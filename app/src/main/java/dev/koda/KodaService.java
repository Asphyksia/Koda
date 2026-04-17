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

        // Read API key, base URL and model from config
        String apiKey = "";
        String baseUrl = "";
        String model = "";
        try {
            File configFile = new File(HOME + "/.openclaude/openclaude.json");
            if (configFile.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(configFile)))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                org.json.JSONObject config = new org.json.JSONObject(sb.toString());
                org.json.JSONObject models = config.optJSONObject("models");
                if (models != null) {
                    model = models.optString("default", "");
                    org.json.JSONObject providers = models.optJSONObject("providers");
                    if (providers != null && providers.keys().hasNext()) {
                        org.json.JSONObject p = providers.optJSONObject(providers.keys().next());
                        if (p != null) {
                            apiKey = p.optString("apiKey", "");
                            baseUrl = p.optString("baseUrl", "");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Could not read config: " + e.getMessage());
        }

        java.util.List<String> env = new java.util.ArrayList<>();
        env.add("HOME=" + HOME);
        env.add("PREFIX=" + PREFIX);
        env.add("TMPDIR=" + PREFIX + "/tmp");
        env.add("LANG=en_US.UTF-8");
        env.add("PATH=" + BIN + ":" + PREFIX + "/bin/applets");
        env.add("LD_LIBRARY_PATH=" + libDir);
        env.add("LD_PRELOAD=" + libDir + "/libtermux-exec.so");
        env.add("TERM=xterm-256color");
        env.add("COLORTERM=truecolor");
        env.add("SSL_CERT_FILE=" + PREFIX + "/etc/tls/cert.pem");
        env.add("NODE_PATH=" + PREFIX + "/lib/node_modules");
        env.add("ANDROID_DATA=/data");
        env.add("ANDROID_ROOT=/system");

        // OpenClaude / Claude Code auth
        if (!apiKey.isEmpty()) {
            env.add("ANTHROPIC_API_KEY=" + apiKey);
        }
        if (!baseUrl.isEmpty()) {
            env.add("ANTHROPIC_BASE_URL=" + baseUrl);
        }
        if (!model.isEmpty()) {
            env.add("ANTHROPIC_MODEL=" + model);
        }

        return env.toArray(new String[0]);
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
        void onSessionId(String sessionId);
        void onComplete(int exitCode);
        void onError(String error);
    }

    /**
     * Send a message to OpenClaude via CLI with stream-json output.
     * Parses streaming events to deliver tokens in real-time.
     * Supports session continuity via --continue flag.
     */
    public void sendToOpenClaude(String message, String sessionId, StreamCallback callback) {
        new Thread(() -> {
            String openclaude = BIN + "/openclaude";
            if (!new File(openclaude).exists()) {
                mHandler.post(() -> callback.onError("OpenClaude not installed. Run setup again."));
                return;
            }

            String escaped = message.replace("'", "'\\''");

            String distPath = PREFIX + "/lib/node_modules/@gitlawb/openclaude/dist/cli.mjs";
            String binPath = PREFIX + "/lib/node_modules/@gitlawb/openclaude/bin/openclaude";
            String entryPoint = new File(distPath).exists() ? distPath : binPath;

            // Read model from config
            String model = "";
            try {
                File configFile = new File(HOME + "/.openclaude/openclaude.json");
                if (configFile.exists()) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(configFile)))) {
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject config = new org.json.JSONObject(sb.toString());
                    org.json.JSONObject models = config.optJSONObject("models");
                    if (models != null) {
                        model = models.optString("default", "");
                    }
                }
            } catch (Exception ignored) {}

            // Build command
            StringBuilder cmd = new StringBuilder();
            cmd.append("'").append(BIN).append("/node' '").append(entryPoint).append("'");
            cmd.append(" -p '").append(escaped).append("'");
            cmd.append(" --output-format stream-json");
            cmd.append(" --include-partial-messages");
            cmd.append(" --verbose");
            if (!model.isEmpty()) {
                cmd.append(" --model '").append(model.replace("'", "'\\''")).append("'");
            }
            // Resume session for context continuity
            if (sessionId != null && !sessionId.isEmpty()) {
                cmd.append(" --resume '").append(sessionId).append("'");
            }
            cmd.append(" --bare");
            cmd.append(" --thinking disabled");
            cmd.append(" 2>/dev/null");  // stderr to /dev/null, we parse stdout JSON

            String script = cmd.toString();
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
                    if (line.isEmpty() || line.charAt(0) != '{') continue;

                    try {
                        org.json.JSONObject json = new org.json.JSONObject(line);
                        String type = json.optString("type", "");

                        switch (type) {
                            case "system":
                                // Init event — capture session_id
                                String sid = json.optString("session_id", "");
                                if (!sid.isEmpty()) {
                                    mHandler.post(() -> callback.onSessionId(sid));
                                }
                                break;

                            case "stream_event":
                                // Streaming token
                                org.json.JSONObject event = json.optJSONObject("event");
                                if (event != null && "content_block_delta".equals(event.optString("type"))) {
                                    org.json.JSONObject delta = event.optJSONObject("delta");
                                    if (delta != null && "text_delta".equals(delta.optString("type"))) {
                                        String text = delta.optString("text", "");
                                        if (!text.isEmpty()) {
                                            mHandler.post(() -> callback.onToken(text));
                                        }
                                    }
                                }
                                break;

                            case "result":
                                // Final result — if we didn't get streaming tokens,
                                // show the full result text
                                break;

                            case "assistant":
                                // Could extract session_id here too if needed
                                String asid = json.optString("session_id", "");
                                if (!asid.isEmpty()) {
                                    mHandler.post(() -> callback.onSessionId(asid));
                                }
                                break;
                        }
                    } catch (Exception parseErr) {
                        // Non-JSON line or parse error — show as text
                        final String raw = line;
                        mHandler.post(() -> callback.onToken(raw + "\n"));
                    }
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
