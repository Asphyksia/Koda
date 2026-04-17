package dev.koda;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

/**
 * Background service that manages:
 * - Running shell commands via JNI (pipe mode)
 * - Running the install script
 * - Starting/stopping OpenClaude (PTY mode)
 */
public class KodaService extends Service {

    private static final String LOG_TAG = "KodaService";

    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String HOME = TermuxConstants.TERMUX_HOME_DIR_PATH;
    private static final String BIN = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;

    private final IBinder mBinder = new LocalBinder();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // OpenClaude process state
    private int mOpenClaudePid = -1;
    private int mOpenClaudeFd = -1;

    public class LocalBinder extends Binder {
        public KodaService getService() { return KodaService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // ========== Static Checks ==========

    public static boolean isBootstrapInstalled() {
        return new File(BIN + "/bash").exists();
    }

    public static boolean isOpenclaudeInstalled() {
        return new File(BIN + "/openclaude").exists();
    }

    public static boolean isOpenclaudeConfigured() {
        File config = new File(HOME + "/.openclaude/openclaude.json");
        return config.exists() && config.length() > 10;
    }

    // ========== Environment ==========

    /**
     * Build the environment array for Termux subprocesses.
     * This is THE critical piece — env must be set at C level
     * before the dynamic linker runs.
     */
    public String[] buildTermuxEnv() {
        String libDir = PREFIX + "/lib";
        return new String[] {
            "HOME=" + HOME,
            "PREFIX=" + PREFIX,
            "TMPDIR=" + PREFIX + "/tmp",
            "LANG=en_US.UTF-8",
            "PATH=" + BIN + ":" + PREFIX + "/bin/applets",
            "LD_LIBRARY_PATH=" + libDir,
            "LD_PRELOAD=" + libDir + "/libtermux-exec.so",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "SSL_CERT_FILE=" + PREFIX + "/etc/tls/cert.pem",
            "NODE_PATH=" + PREFIX + "/lib/node_modules",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
        };
    }

    // ========== Command Execution (Pipe Mode) ==========

    public interface CommandCallback {
        void onResult(CommandResult result);
    }

    public static class CommandResult {
        public final String stdout;
        public final int exitCode;

        public CommandResult(String stdout, int exitCode) {
            this.stdout = stdout;
            this.exitCode = exitCode;
        }
    }

    /**
     * Execute a shell command via JNI pipe mode.
     * Runs on a background thread, callback on main thread.
     */
    public void executeCommand(String script, CommandCallback callback) {
        new Thread(() -> {
            CommandResult result = executeCommandSync(script);
            mHandler.post(() -> callback.onResult(result));
        }).start();
    }

    public CommandResult executeCommandSync(String script) {
        String bash = BIN + "/bash";
        String[] args = { bash, "-c", script };
        String[] env = buildTermuxEnv();
        int[] pidOut = new int[1];

        int fd = KodaProcess.createSubprocessPipe(bash, args, env, pidOut);
        if (fd < 0) {
            return new CommandResult("Failed to create subprocess", -1);
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/fd/" + fd)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Error reading command output: " + e.getMessage());
        }

        int exitCode = KodaProcess.waitFor(pidOut[0]);
        KodaProcess.close(fd);

        return new CommandResult(output.toString(), exitCode);
    }

    // ========== Install OpenClaude ==========

    public interface InstallProgressCallback {
        void onStepStart(int step, String message);
        void onStepComplete(int step);
        void onOutput(String line);
        void onError(String error);
        void onComplete();
    }

    /**
     * Run the install script that sets up the environment and installs OpenClaude.
     * Runs on background thread, callbacks on main thread.
     */
    public void installOpenclaude(InstallProgressCallback callback) {
        new Thread(() -> {
            String installScript = PREFIX + "/share/koda/install.sh";

            // Check if install script exists
            if (!new File(installScript).exists()) {
                mHandler.post(() -> callback.onError("Install script not found: " + installScript));
                return;
            }

            String bash = BIN + "/bash";
            String[] args = { bash, installScript };
            String[] env = buildTermuxEnv();
            int[] pidOut = new int[1];

            int fd = KodaProcess.createSubprocessPipe(bash, args, env, pidOut);
            if (fd < 0) {
                mHandler.post(() -> callback.onError("Failed to start install process"));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream("/proc/self/fd/" + fd)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String l = line;
                    if (l.startsWith("KODA_STEP:")) {
                        // Format: KODA_STEP:<n>:START:<message> or KODA_STEP:<n>:DONE
                        String[] parts = l.split(":", 4);
                        if (parts.length >= 3) {
                            int step = Integer.parseInt(parts[1]);
                            if ("START".equals(parts[2]) && parts.length >= 4) {
                                mHandler.post(() -> callback.onStepStart(step, parts[3]));
                            } else if ("DONE".equals(parts[2])) {
                                mHandler.post(() -> callback.onStepComplete(step));
                            }
                        }
                    } else if (l.startsWith("KODA_ERROR:")) {
                        String error = l.substring(11);
                        mHandler.post(() -> callback.onError(error));
                    } else if (l.startsWith("KODA_COMPLETE")) {
                        // Success
                    } else {
                        mHandler.post(() -> callback.onOutput(l));
                    }
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                mHandler.post(() -> callback.onError("Install error: " + msg));
            }

            int exitCode = KodaProcess.waitFor(pidOut[0]);
            KodaProcess.close(fd);

            if (exitCode == 0) {
                mHandler.post(callback::onComplete);
            } else {
                mHandler.post(() -> callback.onError("Install exited with code " + exitCode));
            }
        }).start();
    }

    // ========== OpenClaude Chat (Pipe Mode) ==========

    public interface StreamCallback {
        void onToken(String token);
        void onComplete(int exitCode);
        void onError(String error);
    }

    /**
     * Send a message to OpenClaude via CLI pipe mode.
     * Uses: openclaude -p "message" --output-format text
     * Streams output line by line to the callback.
     */
    public void sendToOpenClaude(String message, StreamCallback callback) {
        new Thread(() -> {
            String openclaude = BIN + "/openclaude";

            // Check if openclaude binary exists
            if (!new File(openclaude).exists()) {
                mHandler.post(() -> callback.onError("OpenClaude not installed. Run setup again."));
                return;
            }

            // Use -p (print/prompt) flag for single-shot mode
            // Escape the message for shell safety
            String escaped = message.replace("'", "'\\''");

            // Try dist/cli.mjs first (built package), fallback to bin/openclaude
            String distPath = PREFIX + "/lib/node_modules/@gitlawb/openclaude/dist/cli.mjs";
            String binPath = PREFIX + "/lib/node_modules/@gitlawb/openclaude/bin/openclaude";
            String entryPoint = new File(distPath).exists() ? distPath : binPath;

            String script = "'" + BIN + "/node' '" + entryPoint + "' -p '" + escaped + "' --output-format text 2>&1";

            String bash = BIN + "/bash";
            String[] args = { bash, "-c", script };
            String[] env = buildTermuxEnv();
            int[] pidOut = new int[1];

            int fd = KodaProcess.createSubprocessPipe(bash, args, env, pidOut);
            if (fd < 0) {
                mHandler.post(() -> callback.onError("Failed to start OpenClaude process"));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream("/proc/self/fd/" + fd)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String l = line + "\n";
                    mHandler.post(() -> callback.onToken(l));
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                mHandler.post(() -> callback.onError("Read error: " + msg));
            }

            int exitCode = KodaProcess.waitFor(pidOut[0]);
            KodaProcess.close(fd);
            mHandler.post(() -> callback.onComplete(exitCode));
        }).start();
    }

    // ========== OpenClaude Process (PTY Mode) ==========

    public boolean isOpenClaudeRunning() {
        return mOpenClaudePid > 0;
    }

    public int startOpenClaude() {
        if (mOpenClaudePid > 0) {
            Logger.logWarn(LOG_TAG, "OpenClaude already running (pid=" + mOpenClaudePid + ")");
            return mOpenClaudeFd;
        }

        String openclaude = BIN + "/openclaude";
        String[] args = { openclaude };
        String[] env = buildTermuxEnv();
        int[] pidOut = new int[1];

        mOpenClaudeFd = KodaProcess.createSubprocess(openclaude, args, env, pidOut);
        if (mOpenClaudeFd < 0) {
            Logger.logError(LOG_TAG, "Failed to start OpenClaude");
            return -1;
        }

        mOpenClaudePid = pidOut[0];
        Logger.logInfo(LOG_TAG, "OpenClaude started: pid=" + mOpenClaudePid + ", fd=" + mOpenClaudeFd);
        return mOpenClaudeFd;
    }

    public void stopOpenClaude() {
        if (mOpenClaudePid > 0) {
            try {
                android.system.Os.kill(mOpenClaudePid, 15); // SIGTERM
            } catch (Exception e) {
                Logger.logWarn(LOG_TAG, "Failed to kill OpenClaude: " + e.getMessage());
            }
            KodaProcess.close(mOpenClaudeFd);
            mOpenClaudePid = -1;
            mOpenClaudeFd = -1;
        }
    }

    @Override
    public void onDestroy() {
        stopOpenClaude();
        super.onDestroy();
    }
}
