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

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Setup wizard:
 *   Phase 1: Install OpenClaude (if not installed)
 *   Phase 2: Configure API key + provider URL
 *   → Done → ChatActivity
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    // Install phase views
    private View mInstallContainer;
    private ProgressBar mInstallProgress;
    private TextView mInstallStatus;
    private ScrollView mInstallScroll;
    private Button mInstallButton;

    // Config phase views
    private View mConfigContainer;
    private EditText mApiKeyInput;
    private EditText mBaseUrlInput;
    private Button mSaveButton;
    private TextView mConfigStatus;

    private KodaService mService;
    private boolean mBound = false;

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

        mInstallContainer = findViewById(R.id.install_container);
        mInstallProgress = findViewById(R.id.install_progress);
        mInstallStatus = findViewById(R.id.install_status);
        mInstallScroll = findViewById(R.id.install_scroll);
        mInstallButton = findViewById(R.id.install_button);

        mConfigContainer = findViewById(R.id.config_container);
        mApiKeyInput = findViewById(R.id.api_key_input);
        mBaseUrlInput = findViewById(R.id.base_url_input);
        mSaveButton = findViewById(R.id.save_button);
        mConfigStatus = findViewById(R.id.config_status);

        mInstallButton.setOnClickListener(v -> runInstall());
        mSaveButton.setOnClickListener(v -> saveConfig());
        // RelayGPU as default provider (Anthropic endpoint)
        mBaseUrlInput.setText("https://relay.opengpu.network/v2/anthropic/v1");

        // Start and bind to KodaService
        Intent intent = new Intent(this, KodaService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    private void decidePhase() {
        if (KodaService.isOpenclaudeInstalled()) {
            showConfigPhase();
        } else {
            showInstallPhase();
        }
    }

    // ========== Install Phase ==========

    private void showInstallPhase() {
        mInstallContainer.setVisibility(View.VISIBLE);
        mConfigContainer.setVisibility(View.GONE);
        runInstall();
    }

    private void log(String line) {
        mInstallStatus.append(line + "\n");
        // Auto-scroll to bottom
        mInstallScroll.post(() -> mInstallScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void runInstall() {
        if (!mBound || mService == null) {
            log("⏳ Waiting for service...");
            return;
        }

        mInstallButton.setEnabled(false);
        mInstallButton.setVisibility(View.GONE);
        mInstallProgress.setVisibility(View.VISIBLE);
        mInstallStatus.setText(""); // Clear previous output
        log("⏳ Installing OpenClaude...");
        log("   This may take 1-2 minutes on WiFi.\n");

        mService.installOpenclaude(new KodaService.InstallProgressCallback() {
            @Override
            public void onStepStart(int step, String message) {
                log("📦 Step " + step + ": " + message);
            }

            @Override
            public void onStepComplete(int step) {
                log("✅ Step " + step + " complete\n");
            }

            @Override
            public void onOutput(String line) {
                // Show npm output lines (trimmed)
                if (!line.trim().isEmpty()) {
                    log("   " + line.trim());
                }
            }

            @Override
            public void onError(String error) {
                log("\n❌ " + error);
                showRetry();
            }

            @Override
            public void onComplete() {
                mInstallProgress.setVisibility(View.GONE);
                if (KodaService.isOpenclaudeInstalled()) {
                    log("\n✅ OpenClaude installed successfully!");
                    mInstallStatus.postDelayed(() -> showConfigPhase(), 1000);
                } else {
                    log("\n⚠️ Install completed but openclaude not found.");
                    log("   Tap Retry to try again.");
                    showRetry();
                }
            }
        });
    }

    private void showRetry() {
        mInstallProgress.setVisibility(View.GONE);
        mInstallButton.setEnabled(true);
        mInstallButton.setText("Retry");
        mInstallButton.setVisibility(View.VISIBLE);
    }

    // ========== Config Phase ==========

    private void showConfigPhase() {
        mInstallContainer.setVisibility(View.GONE);
        mConfigContainer.setVisibility(View.VISIBLE);

        // Pre-fill from existing config if available
        try {
            File configFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.openclaude/openclaude.json");
            if (configFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                JSONObject config = new JSONObject(content);
                JSONObject providers = config.optJSONObject("models");
                if (providers != null) {
                    providers = providers.optJSONObject("providers");
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
        String apiKey = mApiKeyInput.getText().toString().trim();
        String baseUrl = mBaseUrlInput.getText().toString().trim();

        if (apiKey.isEmpty()) {
            mConfigStatus.setText("API key is required");
            return;
        }
        if (baseUrl.isEmpty()) {
            baseUrl = "https://relay.opengpu.network/v2/anthropic/v1";
        }
        // Strip trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        try {
            // Build config JSON
            JSONObject config = new JSONObject();
            JSONObject models = new JSONObject();
            JSONObject providers = new JSONObject();
            JSONObject provider = new JSONObject();
            provider.put("baseUrl", baseUrl);
            provider.put("apiKey", apiKey);
            provider.put("api", "anthropic-messages");
            providers.put("default", provider);
            models.put("providers", providers);

            // Set default model based on provider
            if (baseUrl.contains("relay.opengpu.network")) {
                models.put("default", "claude-sonnet-4-6");
            }

            config.put("models", models);

            // Write to file
            File configDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.openclaude");
            if (!configDir.exists()) configDir.mkdirs();
            File configFile = new File(configDir, "openclaude.json");

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config.toString(2));
            }
            configFile.setReadable(true, true);
            configFile.setWritable(true, true);

            mConfigStatus.setText("✅ Saved!");
            Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show();

            // Go to chat
            startActivity(new Intent(this, dev.koda.ui.ChatActivity.class));
            finish();

        } catch (Exception e) {
            mConfigStatus.setText("Error: " + e.getMessage());
        }
    }
}
