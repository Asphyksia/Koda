package dev.koda;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import dev.koda.data.ProviderCatalog;
import dev.koda.data.ProviderDef;
import dev.koda.data.ProviderManager;
import dev.koda.ui.ChatActivity;

import java.util.List;

/**
 * Setup wizard — two phases:
 *
 * Phase 1: Install OpenClaude (if not present)
 * Phase 2: Provider wizard
 *   Step 1 — Choose provider
 *   Step 2 — Configure credentials + model
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    // ─── Views: Install phase ─────────────────────────────────────────────────
    private View            mInstallContainer;
    private ProgressBar     mInstallProgress;
    private TextView        mInstallStatus;
    private Button          mInstallButton;

    // ─── Views: Wizard ────────────────────────────────────────────────────────
    private View            mWizardContainer;
    private TextView        mWizardTitle;
    private View            mStepDot1;
    private View            mStepDot2;

    // Step 1
    private View            mStep1Container;
    private LinearLayout    mProvidersPriority;
    private LinearLayout    mProvidersSecondary;
    private LinearLayout    mProvidersAdvanced;

    // Step 2
    private View            mStep2Container;
    private TextView        mSelectedProviderIcon;
    private TextView        mSelectedProviderName;
    private TextView        mSelectedProviderDesc;
    private TextView        mChangeProviderBtn;
    private TextView        mModeLabel;
    private LinearLayout    mModeChipsContainer;
    private EditText        mApiKeyInput;
    private TextView        mToggleKeyVisibility;
    private TextView        mEndpointLabel;
    private EditText        mEndpointUrlInput;
    private TextView        mModelLabel;
    private LinearLayout    mModelChipsRow1;
    private LinearLayout    mModelChipsRow2;
    private EditText        mModelCustomInput;
    private TextView        mConfigStatus;

    // Bottom bar
    private Button          mBtnBack;
    private Button          mBtnNext;

    // ─── State ────────────────────────────────────────────────────────────────
    private int             mCurrentStep = 1;       // 1 = provider list, 2 = config
    private ProviderDef     mSelectedProvider;
    private int             mSelectedModeIndex = 0;
    private String          mSelectedModel;
    private boolean         mKeyVisible = false;
    private ProviderManager mProviderManager;

    // ─── Service ──────────────────────────────────────────────────────────────
    private KodaService     mService;
    private boolean         mBound = false;

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

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_koda_setup);
        bindViews();
        mProviderManager = new ProviderManager(this);

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

    // ─────────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────────

    private void bindViews() {
        mInstallContainer       = findViewById(R.id.install_container);
        mInstallProgress        = findViewById(R.id.install_progress);
        mInstallStatus          = findViewById(R.id.install_status);
        mInstallButton          = findViewById(R.id.install_button);

        mWizardContainer        = findViewById(R.id.wizard_container);
        mWizardTitle            = findViewById(R.id.wizard_title);
        mStepDot1               = findViewById(R.id.step_dot_1);
        mStepDot2               = findViewById(R.id.step_dot_2);

        mStep1Container         = findViewById(R.id.step1_container);
        mProvidersPriority      = findViewById(R.id.providers_priority);
        mProvidersSecondary     = findViewById(R.id.providers_secondary);
        mProvidersAdvanced      = findViewById(R.id.providers_advanced);

        mStep2Container         = findViewById(R.id.step2_container);
        mSelectedProviderIcon   = findViewById(R.id.selected_provider_icon);
        mSelectedProviderName   = findViewById(R.id.selected_provider_name);
        mSelectedProviderDesc   = findViewById(R.id.selected_provider_desc);
        mChangeProviderBtn      = findViewById(R.id.change_provider_btn);
        mModeLabel              = findViewById(R.id.mode_label);
        mModeChipsContainer     = findViewById(R.id.mode_chips_container);
        mApiKeyInput            = findViewById(R.id.api_key_input);
        mToggleKeyVisibility    = findViewById(R.id.toggle_key_visibility);
        mEndpointLabel          = findViewById(R.id.endpoint_label);
        mEndpointUrlInput       = findViewById(R.id.endpoint_url_input);
        mModelLabel             = findViewById(R.id.model_label);
        mModelChipsRow1         = findViewById(R.id.model_chips_row1);
        mModelChipsRow2         = findViewById(R.id.model_chips_row2);
        mModelCustomInput       = findViewById(R.id.model_custom_input);
        mConfigStatus           = findViewById(R.id.config_status);

        mBtnBack                = findViewById(R.id.btn_back);
        mBtnNext                = findViewById(R.id.btn_next);

        mInstallButton.setOnClickListener(v -> runInstallChecks());
        mChangeProviderBtn.setOnClickListener(v -> goToStep(1));
        mToggleKeyVisibility.setOnClickListener(v -> toggleKeyVisibility());
        mBtnBack.setOnClickListener(v -> goToStep(1));
        mBtnNext.setOnClickListener(v -> onNextClicked());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase selection
    // ─────────────────────────────────────────────────────────────────────────

    private void decidePhase() {
        if (KodaService.isOpenclaudeInstalled()) {
            showWizard();
        } else {
            showInstallPhase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 1 — Install
    // ─────────────────────────────────────────────────────────────────────────

    private void showInstallPhase() {
        mInstallContainer.setVisibility(View.VISIBLE);
        mWizardContainer.setVisibility(View.GONE);

        mInstallButton.setEnabled(false);
        mInstallProgress.setVisibility(View.VISIBLE);
        mInstallStatus.setText("");

        if (!KodaService.isBootstrapInstalled()) {
            log("❌ Bootstrap not installed (no bash binary)");
            mInstallButton.setEnabled(true);
            mInstallButton.setText("Retry");
            mInstallProgress.setVisibility(View.GONE);
            return;
        }
        log("✅ Bootstrap extracted");
        runFixPaths();
    }

    private void log(String line) {
        mInstallStatus.append(line + "\n");
    }

    private void runFixPaths() {
        log("🔧 Verifying bootstrap paths…");
        if (!mBound || mService == null) return;

        mService.executeCommand(
            "chmod +x $PREFIX/bin/* 2>/dev/null\necho DONE",
            result -> {
                log("✅ Bootstrap paths OK");
                runCheckNode();
            });
    }

    private void runCheckNode() {
        mService.executeCommand("node --version 2>&1; echo NODE_OK",
            result -> {
                String out = result.stdout
                    .replaceAll("\\r", "")
                    .replaceAll("\\x1b\\[[0-9;]*[a-zA-Z]", "")
                    .trim();
                if (out.contains("NODE_OK") && out.contains("v")) {
                    log("✅ Node.js: " + out.split("\n")[0].trim());
                } else {
                    log("⚠️  Node.js: " + out.replace("\n", " | "));
                }
                runCheckNpm();
            });
    }

    private void runCheckNpm() {
        mService.executeCommand("npm --version 2>&1; echo NPM_OK",
            result -> {
                String out = result.stdout
                    .replaceAll("\\r", "")
                    .replaceAll("\\x1b\\[[0-9;]*[a-zA-Z]", "")
                    .trim();
                if (out.contains("NPM_OK")) {
                    log("✅ npm: v" + out.split("\n")[0].trim());
                } else {
                    log("⚠️  npm: " + out.replace("\n", " | "));
                }
                runInstallChecks();
            });
    }

    private void runInstallChecks() {
        if (!mBound || mService == null) return;

        mInstallButton.setEnabled(false);
        mInstallProgress.setVisibility(View.VISIBLE);
        log("⏳ Installing @gitlawb/openclaude…");
        log("   (this may take 1-2 minutes on WiFi)\n");

        mService.installOpenclaude(new KodaService.InstallProgressCallback() {
            @Override public void onStepStart(int step, String message)  { log("📦 Step " + step + ": " + message); }
            @Override public void onStepComplete(int step)               { log("✅ Step " + step + " complete"); }
            @Override public void onOutput(String line)                  { log(line); }
            @Override public void onError(String error)                  { log("\n❌ Error: " + error); failInstall(); }
            @Override public void onComplete() {
                mInstallProgress.setVisibility(View.GONE);
                log("\n✅ OpenClaude installed!");
                if (KodaService.isOpenclaudeInstalled()) {
                    log("✅ Module verified");
                    mInstallStatus.postDelayed(SetupActivity.this::showWizard, 1200);
                } else {
                    log("⚠️  Module not found after install — check output above");
                    failInstall();
                }
            }
        });
    }

    private void failInstall() {
        mInstallProgress.setVisibility(View.GONE);
        mInstallButton.setEnabled(true);
        mInstallButton.setText("Retry");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 2 — Wizard
    // ─────────────────────────────────────────────────────────────────────────

    private void showWizard() {
        mInstallContainer.setVisibility(View.GONE);
        mWizardContainer.setVisibility(View.VISIBLE);

        buildProviderLists();
        preSelectFromExistingConfig();
        goToStep(1);
    }

    // ─── Step navigation ──────────────────────────────────────────────────────

    private void goToStep(int step) {
        mCurrentStep = step;

        if (step == 1) {
            mWizardTitle.setText("Choose Provider");
            mStep1Container.setVisibility(View.VISIBLE);
            mStep2Container.setVisibility(View.GONE);
            mBtnBack.setVisibility(View.GONE);
            mBtnNext.setText(mSelectedProvider != null ? "Next →" : "Next →");
            mBtnNext.setEnabled(mSelectedProvider != null);
            updateStepDots(1);
        } else {
            mWizardTitle.setText("Configure");
            mStep1Container.setVisibility(View.GONE);
            mStep2Container.setVisibility(View.VISIBLE);
            mBtnBack.setVisibility(View.VISIBLE);
            mBtnNext.setText("Save & Start");
            mBtnNext.setEnabled(true);
            updateStepDots(2);
            populateStep2();
        }
    }

    private void updateStepDots(int activeStep) {
        mStepDot1.setBackgroundResource(
            activeStep == 1 ? R.drawable.step_dot_active : R.drawable.step_dot_done);
        mStepDot2.setBackgroundResource(
            activeStep == 2 ? R.drawable.step_dot_active : R.drawable.step_dot_inactive);
    }

    private void onNextClicked() {
        if (mCurrentStep == 1) {
            if (mSelectedProvider == null) return;
            goToStep(2);
        } else {
            saveConfig();
        }
    }

    // ─── Step 1: Build provider lists ─────────────────────────────────────────

    private void buildProviderLists() {
        mProvidersPriority.removeAllViews();
        mProvidersSecondary.removeAllViews();
        mProvidersAdvanced.removeAllViews();

        for (ProviderDef p : ProviderCatalog.PRIORITY) {
            mProvidersPriority.addView(buildProviderCard(p));
        }
        for (ProviderDef p : ProviderCatalog.SECONDARY) {
            mProvidersSecondary.addView(buildProviderCard(p));
        }
        for (ProviderDef p : ProviderCatalog.ADVANCED) {
            mProvidersAdvanced.addView(buildProviderCard(p));
        }
    }

    private View buildProviderCard(ProviderDef provider) {
        View card = LayoutInflater.from(this)
            .inflate(R.layout.item_provider_card, null, false);

        ((TextView) card.findViewById(R.id.provider_icon)).setText(provider.iconEmoji);
        ((TextView) card.findViewById(R.id.provider_name)).setText(provider.displayName);
        ((TextView) card.findViewById(R.id.provider_description)).setText(provider.description);

        card.setTag(provider);
        card.setOnClickListener(v -> selectProvider(provider));
        refreshCardSelection(card, provider);
        return card;
    }

    private void selectProvider(ProviderDef provider) {
        mSelectedProvider = provider;
        mSelectedModeIndex = 0;
        mSelectedModel = provider.modes.get(0).defaultModel;

        // Refresh all cards
        refreshAllCards(mProvidersPriority);
        refreshAllCards(mProvidersSecondary);
        refreshAllCards(mProvidersAdvanced);

        mBtnNext.setEnabled(true);
    }

    private void refreshAllCards(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);
            Object tag = card.getTag();
            if (tag instanceof ProviderDef) {
                refreshCardSelection(card, (ProviderDef) tag);
            }
        }
    }

    private void refreshCardSelection(View card, ProviderDef provider) {
        boolean selected = mSelectedProvider != null && mSelectedProvider.id.equals(provider.id);
        card.setBackgroundResource(
            selected ? R.drawable.provider_card_selected_bg : R.drawable.provider_card_ripple);
        View check = card.findViewById(R.id.provider_check);
        if (check != null) {
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ─── Step 2: Configure ────────────────────────────────────────────────────

    private void populateStep2() {
        if (mSelectedProvider == null) return;

        // Summary card
        mSelectedProviderIcon.setText(mSelectedProvider.iconEmoji);
        mSelectedProviderName.setText(mSelectedProvider.displayName);
        mSelectedProviderDesc.setText(mSelectedProvider.description);

        // Mode chips
        List<ProviderDef.AccessMode> modes = mSelectedProvider.modes;
        if (modes.size() > 1) {
            mModeLabel.setVisibility(View.VISIBLE);
            mModeChipsContainer.setVisibility(View.VISIBLE);
            mModeChipsContainer.removeAllViews();
            for (int i = 0; i < modes.size(); i++) {
                final int idx = i;
                TextView chip = new TextView(this);
                chip.setText(modes.get(i).label);
                chip.setTextColor(getColor(i == mSelectedModeIndex
                    ? R.color.koda_primary : R.color.koda_text_secondary));
                chip.setTextSize(13f);
                chip.setPadding(dp(14), dp(8), dp(14), dp(8));
                chip.setBackgroundResource(
                    i == mSelectedModeIndex
                        ? R.drawable.mode_chip_selected_bg
                        : R.drawable.mode_chip_bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(8));
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> {
                    mSelectedModeIndex = idx;
                    mSelectedModel = modes.get(idx).defaultModel;
                    populateStep2(); // re-render
                });
                mModeChipsContainer.addView(chip);
            }
        } else {
            mModeLabel.setVisibility(View.GONE);
            mModeChipsContainer.setVisibility(View.GONE);
        }

        // Endpoint field visibility
        ProviderDef.AccessMode mode = modes.get(mSelectedModeIndex);
        if (mode.requiresCustomUrl) {
            mEndpointLabel.setVisibility(View.VISIBLE);
            mEndpointUrlInput.setVisibility(View.VISIBLE);
        } else {
            mEndpointLabel.setVisibility(View.GONE);
            mEndpointUrlInput.setVisibility(View.GONE);
        }

        // Model chips
        populateModelChips(mode);

        // Custom model input: always shown for custom provider or when no suggested models
        boolean showCustomInput = mSelectedProvider.id.equals("custom")
            || mode.suggestedModels.isEmpty();
        mModelCustomInput.setVisibility(showCustomInput ? View.VISIBLE : View.GONE);

        // Pre-fill from existing config
        prefillFromConfig();

        mConfigStatus.setText("");
    }

    private void populateModelChips(ProviderDef.AccessMode mode) {
        mModelChipsRow1.removeAllViews();
        mModelChipsRow2.removeAllViews();

        List<String> models = mode.suggestedModels;
        if (models.isEmpty()) return;

        // Split into two rows: first 3 in row 1, rest in row 2
        for (int i = 0; i < models.size(); i++) {
            final String modelId = models.get(i);
            String label = shortModelLabel(modelId);

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(11f);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);

            boolean isSelected = modelId.equals(mSelectedModel);
            chip.setBackgroundResource(
                isSelected ? R.drawable.model_chip_selected_bg : R.drawable.model_chip_bg);
            chip.setTextColor(getColor(isSelected ? R.color.koda_on_primary : R.color.koda_text_secondary));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            lp.bottomMargin = dp(4);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                mSelectedModel = modelId;
                // Also fill the custom input if visible (for reference)
                if (mModelCustomInput.getVisibility() == View.VISIBLE) {
                    mModelCustomInput.setText(modelId);
                }
                populateModelChips(mSelectedProvider.modes.get(mSelectedModeIndex));
            });

            if (i < 3) {
                mModelChipsRow1.addView(chip);
            } else {
                mModelChipsRow2.addView(chip);
            }
        }
    }

    /**
     * Pre-fill API key and endpoint from existing config if available.
     */
    private void prefillFromConfig() {
        if (mSelectedProvider == null) return;

        try {
            ProviderManager.Provider active = mProviderManager.getActiveProvider();
            if (active == null) return;

            if (!active.apiKey.isEmpty()) {
                mApiKeyInput.setText(active.apiKey);
            }

            ProviderDef.AccessMode mode = mSelectedProvider.modes.get(mSelectedModeIndex);
            if (mode.requiresCustomUrl && active.baseUrl != null && !active.baseUrl.isEmpty()) {
                mEndpointUrlInput.setText(active.baseUrl);
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "prefillFromConfig failed: " + e.getMessage());
        }
    }

    // ─── Pre-select existing config ───────────────────────────────────────────

    private void preSelectFromExistingConfig() {
        try {
            ProviderManager.Provider active = mProviderManager.getActiveProvider();
            if (active == null) return;

            // Match by baseUrl prefix against known providers
            for (ProviderDef def : ProviderCatalog.all()) {
                for (int i = 0; i < def.modes.size(); i++) {
                    ProviderDef.AccessMode mode = def.modes.get(i);
                    if (!mode.baseUrl.isEmpty() && active.baseUrl != null
                            && active.baseUrl.startsWith(mode.baseUrl.substring(0, Math.min(mode.baseUrl.length(), 30)))) {
                        mSelectedProvider = def;
                        mSelectedModeIndex = i;
                        mSelectedModel = active.defaultModel != null ? active.defaultModel : mode.defaultModel;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "preSelectFromExistingConfig failed: " + e.getMessage());
        }
    }

    // ─── Save & validate ──────────────────────────────────────────────────────

    private void saveConfig() {
        if (mSelectedProvider == null) {
            showError("Please select a provider.");
            return;
        }

        ProviderDef.AccessMode mode = mSelectedProvider.modes.get(mSelectedModeIndex);

        String apiKey = mApiKeyInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            showError("API key is required.");
            mApiKeyInput.requestFocus();
            return;
        }

        // Determine base URL
        String baseUrl;
        if (mode.requiresCustomUrl) {
            baseUrl = mEndpointUrlInput.getText().toString().trim();
            if (baseUrl.isEmpty()) {
                showError("Endpoint URL is required for this provider.");
                mEndpointUrlInput.requestFocus();
                return;
            }
        } else {
            baseUrl = mode.baseUrl;
        }

        // Determine model
        String model = mSelectedModel;
        if (mModelCustomInput.getVisibility() == View.VISIBLE) {
            String customModel = mModelCustomInput.getText().toString().trim();
            if (!customModel.isEmpty()) {
                model = customModel;
            }
        }
        if (model == null || model.isEmpty()) {
            if (!mode.suggestedModels.isEmpty()) {
                model = mode.suggestedModels.get(0);
            } else {
                showError("Please select or enter a model.");
                return;
            }
        }

        mConfigStatus.setTextColor(getColor(R.color.koda_text_secondary));
        mConfigStatus.setText("Saving…");
        mBtnNext.setEnabled(false);

        final String finalModel = model;
        final String finalBaseUrl = baseUrl;
        final String finalApiKey = apiKey;

        // Build ProviderManager.Provider and save
        try {
            ProviderManager.Provider p = new ProviderManager.Provider();
            p.id = mSelectedProvider.id;
            p.name = mSelectedProvider.displayName;
            p.apiKey = finalApiKey;
            p.baseUrl = finalBaseUrl;
            p.defaultModel = finalModel;
            p.type = mode.apiType.equals(ProviderDef.API_TYPE_ANTHROPIC) ? "anthropic" : "openai";
            p.models = mode.suggestedModels.toArray(new String[0]);

            // Add or update in ProviderManager
            ProviderManager.Provider existing = mProviderManager.getProvider(p.id);
            if (existing != null) {
                mProviderManager.updateProvider(p);
            } else {
                mProviderManager.addProvider(p);
            }
            mProviderManager.setActiveProvider(p.id);

        } catch (Exception e) {
            showError("Failed to write config: " + e.getMessage());
            mBtnNext.setEnabled(true);
            return;
        }

        mConfigStatus.setTextColor(getColor(R.color.koda_success));
        mConfigStatus.setText("✅ Configuration saved!");

        Toast.makeText(this, "Setup complete!", Toast.LENGTH_SHORT).show();

        mConfigStatus.postDelayed(() -> {
            startActivity(new Intent(this, ChatActivity.class));
            finish();
        }, 600);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showError(String msg) {
        mConfigStatus.setTextColor(getColor(R.color.koda_error));
        mConfigStatus.setText(msg);
    }

    private void toggleKeyVisibility() {
        mKeyVisible = !mKeyVisible;
        if (mKeyVisible) {
            mApiKeyInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            mToggleKeyVisibility.setText("🙈");
        } else {
            mApiKeyInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mToggleKeyVisibility.setText("👁");
        }
        // Keep cursor at end
        mApiKeyInput.setSelection(mApiKeyInput.getText().length());
    }

    /**
     * Shorten long model IDs for chip display.
     * e.g. "anthropic/claude-sonnet-4-6" → "claude-sonnet-4-6"
     *      "deepseek-ai/DeepSeek-V3.1"   → "DeepSeek-V3.1"
     */
    private String shortModelLabel(String modelId) {
        if (modelId == null) return "";
        int slash = modelId.lastIndexOf('/');
        if (slash >= 0 && slash < modelId.length() - 1) {
            return modelId.substring(slash + 1);
        }
        return modelId;
    }

    private int dp(int px) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(px * density);
    }
}
