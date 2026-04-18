package dev.koda;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Setup wizard — two phases:
 *   Phase 1 (install): guided checklist, no raw logs visible by default
 *   Phase 2 (config):  API key + provider URL form
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    // ── Install phase ─────────────────────────────────────────
    private View     mInstallContainer;
    private TextView mInstallSubtitle;
    private ProgressBar mInstallProgress;
    private TextView mInstallHint;

    // Checklist step views (5 steps)
    private View[]   mStepViews;

    // Success state
    private View     mInstallSuccess;

    // Error state
    private View     mInstallError;
    private TextView mInstallErrorMsg;
    private TextView mInstallShowLogs;
    private ScrollView mInstallScroll;
    private TextView mInstallStatus;  // raw log (hidden by default)
    private Button   mInstallButton;

    // ── Config phase ──────────────────────────────────────────
    private View     mConfigContainer;
    private EditText mApiKeyInput;
    private EditText mBaseUrlInput;
    private Button   mSaveButton;
    private TextView mConfigStatus;

    // ── Service ───────────────────────────────────────────────
    private KodaService mService;
    private boolean mBound = false;

    // ── Log accumulator (never shown unless error + user taps) ─
    private final StringBuilder mRawLog = new StringBuilder();

    // ── Step definitions ──────────────────────────────────────
    private static final int STEP_ENVIRONMENT = 0;
    private static final int STEP_NODE        = 1;
    private static final int STEP_OPENCLAUDE  = 2;
    private static final int STEP_VERIFY      = 3;
    private static final int STEP_FINALIZE    = 4;

    private static final String[] STEP_LABELS = {
        "Building environment",
        "Installing Node.js",
        "Installing OpenClaude",
        "Verifying dependencies",
        "Finalizing setup"
    };

    private static final String[] STEP_HINTS = {
        "Setting up the Termux base environment…",
        "This may take a minute",
        "Downloading packages, please wait…",
        "Checking everything is in place…",
        "Almost done!"
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KodaService.LocalBinder binder = (KodaService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            decidePhase();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_koda_setup);

        // Install phase
        mInstallContainer = findViewById(R.id.install_container);
        mInstallSubtitle  = findViewById(R.id.install_subtitle);
        mInstallProgress  = findViewById(R.id.install_progress);
        mInstallHint      = findViewById(R.id.install_hint);
        mInstallSuccess   = findViewById(R.id.install_success);
        mInstallError     = findViewById(R.id.install_error);
        mInstallErrorMsg  = findViewById(R.id.install_error_msg);
        mInstallShowLogs  = findViewById(R.id.install_show_logs);
        mInstallScroll    = findViewById(R.id.install_scroll);
        mInstallStatus    = findViewById(R.id.install_status);
        mInstallButton    = findViewById(R.id.install_button);

        // Step views (5 items matching step_1..step_5 ids)
        int[] stepIds = { R.id.step_1, R.id.step_2, R.id.step_3, R.id.step_4, R.id.step_5 };
        mStepViews = new View[5];
        for (int i = 0; i < 5; i++) {
            mStepViews[i] = findViewById(stepIds[i]);
            setStepLabel(i, STEP_LABELS[i]);
            setStepState(i, StepState.PENDING);
        }

        // Config phase
        mConfigContainer = findViewById(R.id.config_container);
        mApiKeyInput     = findViewById(R.id.api_key_input);
        mBaseUrlInput    = findViewById(R.id.base_url_input);
        mSaveButton      = findViewById(R.id.save_button);
        mConfigStatus    = findViewById(R.id.config_status);

        mBaseUrlInput.setText("https://relay.opengpu.network/v2/anthropic");
        mSaveButton.setOnClickListener(v -> saveConfig());
        mInstallButton.setOnClickListener(v -> {
            mRawLog.setLength(0);
            resetSteps();
            mInstallError.setVisibility(View.GONE);
            runInstall();
        });
        mInstallShowLogs.setOnClickListener(v -> {
            boolean showing = mInstallScroll.getVisibility() == View.VISIBLE;
            mInstallScroll.setVisibility(showing ? View.GONE : View.VISIBLE);
            mInstallShowLogs.setText(showing ? "Show technical details" : "Hide technical details");
        });

        Intent intent = new Intent(this, KodaService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) { unbindService(mConnection); mBound = false; }
    }

    private void decidePhase() {
        if (KodaService.isOpenclaudeInstalled()) {
            showConfigPhase();
        } else {
            showInstallPhase();
        }
    }

    // =========================================================
    // Step state machine
    // =========================================================

    private enum StepState { PENDING, ACTIVE, DONE, ERROR }

    private void setStepState(int stepIndex, StepState state) {
        if (stepIndex < 0 || stepIndex >= mStepViews.length) return;
        View row = mStepViews[stepIndex];
        if (row == null) return;

        View pending = row.findViewById(R.id.step_icon_pending);
        View active  = row.findViewById(R.id.step_icon_active);
        View done    = row.findViewById(R.id.step_icon_done);
        View error   = row.findViewById(R.id.step_icon_error);
        TextView label = row.findViewById(R.id.step_label);

        // Hide all icons first
        pending.setVisibility(View.GONE);
        active.setVisibility(View.GONE);
        done.setVisibility(View.GONE);
        error.setVisibility(View.GONE);

        switch (state) {
            case PENDING:
                pending.setVisibility(View.VISIBLE);
                label.setTextColor(ContextCompat.getColor(this, R.color.koda_text_tertiary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
            case ACTIVE:
                active.setVisibility(View.VISIBLE);
                label.setTextColor(ContextCompat.getColor(this, R.color.koda_text_primary));
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                mInstallHint.setText(STEP_HINTS[stepIndex]);
                break;
            case DONE:
                done.setVisibility(View.VISIBLE);
                // Animate check in
                done.setAlpha(0f);
                done.setScaleX(0.5f);
                done.setScaleY(0.5f);
                done.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                label.setTextColor(ContextCompat.getColor(this, R.color.koda_text_secondary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
            case ERROR:
                error.setVisibility(View.VISIBLE);
                label.setTextColor(ContextCompat.getColor(this, R.color.koda_error));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
        }
    }

    private void setStepLabel(int stepIndex, String label) {
        if (stepIndex < 0 || stepIndex >= mStepViews.length) return;
        View row = mStepViews[stepIndex];
        if (row == null) return;
        TextView tv = row.findViewById(R.id.step_label);
        if (tv != null) tv.setText(label);
    }

    private void resetSteps() {
        for (int i = 0; i < 5; i++) setStepState(i, StepState.PENDING);
        mInstallHint.setText("");
        mInstallSuccess.setVisibility(View.GONE);
        mInstallButton.setVisibility(View.GONE);
    }

    // =========================================================
    // Install phase
    // =========================================================

    private void showInstallPhase() {
        mInstallContainer.setVisibility(View.VISIBLE);
        mConfigContainer.setVisibility(View.GONE);
        runInstall();
    }

    private void appendLog(String line) {
        mRawLog.append(line).append("\n");
        // Keep TextView in sync (it's hidden unless user taps)
        mInstallStatus.setText(mRawLog.toString());
    }

    private void runInstall() {
        if (!mBound || mService == null) {
            mInstallHint.setText("Waiting for service…");
            return;
        }

        mInstallButton.setVisibility(View.GONE);
        mInstallProgress.setVisibility(View.VISIBLE);
        setStepState(STEP_ENVIRONMENT, StepState.ACTIVE);

        mService.installOpenclaude(new KodaService.InstallProgressCallback() {
            @Override
            public void onStepStart(int step, String message) {
                appendLog("STEP " + step + ": " + message);
                // Map internal step numbers (1-based) to our 5 steps
                int idx = mapStep(step, message);
                // Mark previous steps done
                for (int i = 0; i < idx; i++) setStepState(i, StepState.DONE);
                setStepState(idx, StepState.ACTIVE);
            }

            @Override
            public void onStepComplete(int step) {
                appendLog("STEP " + step + " done");
                int idx = Math.min(step - 1, 4);
                setStepState(idx, StepState.DONE);
            }

            @Override
            public void onOutput(String line) {
                if (!line.trim().isEmpty()) appendLog(line.trim());
            }

            @Override
            public void onError(String error) {
                appendLog("ERROR: " + error);
                mInstallProgress.setVisibility(View.GONE);
                showInstallError(error);
            }

            @Override
            public void onComplete() {
                mInstallProgress.setVisibility(View.GONE);
                if (KodaService.isOpenclaudeInstalled()) {
                    // Mark all steps done
                    for (int i = 0; i < 5; i++) setStepState(i, StepState.DONE);
                    mInstallHint.setText("");
                    showInstallSuccess();
                } else {
                    showInstallError("Installation completed but OpenClaude was not found.");
                }
            }
        });
    }

    /**
     * Maps the installer's step number + message to our 5 checklist steps.
     * Installer emits: step 1 = bootstrap/env, step 2 = openclaude/npm
     */
    private int mapStep(int step, String message) {
        String msg = message.toLowerCase();
        if (msg.contains("bootstrap") || msg.contains("environment") || step == 1)
            return STEP_ENVIRONMENT;
        if (msg.contains("node"))
            return STEP_NODE;
        if (msg.contains("openclaude") || msg.contains("npm") || msg.contains("install"))
            return STEP_OPENCLAUDE;
        if (msg.contains("verif") || msg.contains("check"))
            return STEP_VERIFY;
        return STEP_FINALIZE;
    }

    private void showInstallSuccess() {
        mInstallSuccess.setVisibility(View.VISIBLE);
        // Animate all children in
        for (int i = 0; i < ((android.view.ViewGroup) mInstallSuccess).getChildCount(); i++) {
            View child = ((android.view.ViewGroup) mInstallSuccess).getChildAt(i);
            child.animate()
                .alpha(1f)
                .setStartDelay(i * 120L)
                .setDuration(220)
                .start();
        }
        mInstallHint.setText("");
        mInstallSuccess.postDelayed(() -> showConfigPhase(), 1400);
    }

    private void showInstallError(String errorMsg) {
        // Mark the active step as error
        for (int i = 0; i < 5; i++) {
            View row = mStepViews[i];
            if (row != null) {
                View activeIcon = row.findViewById(R.id.step_icon_active);
                if (activeIcon != null && activeIcon.getVisibility() == View.VISIBLE) {
                    setStepState(i, StepState.ERROR);
                    break;
                }
            }
        }
        mInstallError.setVisibility(View.VISIBLE);
        mInstallErrorMsg.setText("Something went wrong: " + errorMsg);
        mInstallButton.setVisibility(View.VISIBLE);
        mInstallButton.setText("Retry");
        mInstallHint.setText("");
    }

    // =========================================================
    // Config phase
    // =========================================================

    private void showConfigPhase() {
        mInstallContainer.setVisibility(View.GONE);
        mConfigContainer.setVisibility(View.VISIBLE);

        try {
            File configFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.openclaude/openclaude.json");
            if (configFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                JSONObject config = new JSONObject(content);
                JSONObject models = config.optJSONObject("models");
                if (models != null) {
                    JSONObject providers = models.optJSONObject("providers");
                    if (providers != null && providers.keys().hasNext()) {
                        JSONObject p = providers.optJSONObject(providers.keys().next());
                        if (p != null) {
                            String key = p.optString("apiKey", "");
                            String url = p.optString("baseUrl", "");
                            if (!key.isEmpty()) mApiKeyInput.setText(key);
                            if (!url.isEmpty()) mBaseUrlInput.setText(url);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Could not pre-fill config: " + e.getMessage());
        }
    }

    private void saveConfig() {
        String apiKey  = mApiKeyInput.getText().toString().trim();
        String baseUrl = mBaseUrlInput.getText().toString().trim();

        if (apiKey.isEmpty()) {
            mConfigStatus.setText("API key is required");
            return;
        }
        if (baseUrl.isEmpty()) baseUrl = "https://relay.opengpu.network/v2/anthropic";
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        try {
            JSONObject config = new JSONObject();
            JSONObject models = new JSONObject();
            JSONObject providers = new JSONObject();
            JSONObject provider = new JSONObject();
            provider.put("baseUrl", baseUrl);
            provider.put("apiKey", apiKey);
            provider.put("api", "anthropic-messages");
            providers.put("default", provider);
            models.put("providers", providers);
            if (baseUrl.contains("relay.opengpu.network")) {
                models.put("default", "anthropic/claude-sonnet-4-6");
            }
            config.put("models", models);

            File configDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.openclaude");
            if (!configDir.exists()) configDir.mkdirs();
            File configFile = new File(configDir, "openclaude.json");
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config.toString(2));
            }
            configFile.setReadable(true, true);
            configFile.setWritable(true, true);

            mConfigStatus.setTextColor(
                ContextCompat.getColor(this, R.color.koda_success));
            mConfigStatus.setText("✓ Saved!");
            Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show();

            startActivity(new Intent(this, dev.koda.ui.ChatActivity.class));
            finish();

        } catch (Exception e) {
            mConfigStatus.setText("Error: " + e.getMessage());
        }
    }
}
