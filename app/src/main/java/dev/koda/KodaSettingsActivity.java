package dev.koda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.AnalyticsManager;
import com.termux.shared.logger.Logger;

import java.util.List;

public class KodaSettingsActivity extends Activity {

    private static final String LOG_TAG = "KodaSettingsActivity";
    private static final int OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS = 180;
    private static final String KODA_WEBSITE_URL = "https://koda.app/";
    private static final String KODA_X_URL = "https://x.com/kodaapp";
    private static final String KODA_DISCORD_URL = "https://discord.gg/w8wdnMM6Vy";
    private static final String KODA_DOCS_URL = "https://docs.koda.app/";

    private TextView mKodaVersionText;
    private TextView mOpenclaudeVersionText;
    private Button mCheckKodaUpdateButton;
    private Button mChangeVersionButton;
    private KodaService mKodaService;
    private boolean mBound = false;
    private AlertDialog mOpenclaudeVersionManagerDialog;
    private boolean mOpenclaudeVersionActionInProgress;
    private final Handler mHandler = new Handler();

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KodaService.LocalBinder binder = (KodaService.LocalBinder) service;
            mKodaService = binder.getService();
            mBound = true;
            refreshCurrentVersion();
            Logger.logDebug(LOG_TAG, "KodaService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mKodaService = null;
            Logger.logDebug(LOG_TAG, "KodaService disconnected");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_koda_settings);

        mKodaVersionText = findViewById(R.id.settings_koda_version_text);
        mOpenclaudeVersionText = findViewById(R.id.settings_openclaude_version_text);
        mCheckKodaUpdateButton = findViewById(R.id.btn_check_koda_update);
        mChangeVersionButton = findViewById(R.id.btn_change_openclaude_version);
        ImageButton backButton = findViewById(R.id.btn_settings_back);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        refreshKodaVersion();

        if (mCheckKodaUpdateButton != null) {
            mCheckKodaUpdateButton.setOnClickListener(v -> {
                AnalyticsManager.logEvent(this, "settings_koda_update_tap");
                checkKodaUpdate();
            });
        }

        if (mChangeVersionButton != null) {
            mChangeVersionButton.setOnClickListener(v -> {
                AnalyticsManager.logEvent(this, "settings_change_version_tap");
                showOpenclaudeVersionManagerDialog();
            });
        }

        bindExternalLink(R.id.settings_website_row, "settings_website_tap", KODA_WEBSITE_URL);
        bindExternalLink(R.id.settings_x_row, "settings_x_tap", KODA_X_URL);
        bindExternalLink(R.id.settings_discord_row, "settings_discord_tap", KODA_DISCORD_URL);
        bindExternalLink(R.id.settings_docs_row, "settings_docs_tap", KODA_DOCS_URL);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, KodaService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentVersion();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (mOpenclaudeVersionManagerDialog != null && mOpenclaudeVersionManagerDialog.isShowing()) {
            mOpenclaudeVersionManagerDialog.dismiss();
        }
        mOpenclaudeVersionManagerDialog = null;
    }

    private void refreshCurrentVersion() {
        if (mOpenclaudeVersionText == null) {
            return;
        }
        String currentVersion = KodaService.getOpenclaudeVersion();
        if (TextUtils.isEmpty(currentVersion)) {
            mOpenclaudeVersionText.setText(getString(R.string.koda_unknown));
            return;
        }
        mOpenclaudeVersionText.setText(currentVersion);
    }

    private void refreshKodaVersion() {
        if (mKodaVersionText == null) {
            return;
        }
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mKodaVersionText.setText(
                TextUtils.isEmpty(packageInfo.versionName) ? getString(R.string.koda_unknown) : packageInfo.versionName
            );
        } catch (Exception e) {
            mKodaVersionText.setText(getString(R.string.koda_unknown));
        }
    }

    private void checkKodaUpdate() {
        if (mCheckKodaUpdateButton != null) {
            mCheckKodaUpdateButton.setEnabled(false);
        }
        UpdateChecker.forceCheck(this, (version, url, notes) -> {
            if (mCheckKodaUpdateButton != null) {
                mCheckKodaUpdateButton.setEnabled(true);
            }
            if (version != null && !version.isEmpty()) {
                new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.koda_update_update_available))
                    .setMessage(getString(R.string.koda_update_update_message, version))
                    .setPositiveButton(getString(R.string.koda_open_browser), (d, w) -> openKodaUpdatePage())
                    .setNegativeButton(getString(R.string.koda_cancel), null)
                    .show();
            } else {
                Toast.makeText(this, getString(R.string.koda_no_update_available), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openKodaUpdatePage() {
        openExternalUrl(KODA_WEBSITE_URL);
    }

    private void bindExternalLink(int viewId, String analyticsEvent, String url) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setOnClickListener(v -> {
            AnalyticsManager.logEvent(this, analyticsEvent);
            openExternalUrl(url);
        });
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to open url: " + url + " - " + e.getMessage());
            Toast.makeText(this, getString(R.string.koda_failed_to_open_link), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean canShowDialog() {
        return !isFinishing() && !isDestroyed();
    }

    private void showOpenclaudeVersionManagerDialog() {
        if (mOpenclaudeVersionActionInProgress) {
            return;
        }
        if (!mBound || mKodaService == null) {
            Toast.makeText(this, getString(R.string.koda_service_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }

        setOpenclaudeVersionManagerBusy(true);
        dismissVersionDialog();

        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(this)
            .setTitle(getString(R.string.koda_openclaude_versions))
            .setMessage(getString(R.string.koda_loading_versions))
            .setCancelable(false)
            .setNegativeButton(R.string.koda_cancel, (d, w) -> setOpenclaudeVersionManagerBusy(false))
            .create();
        mOpenclaudeVersionManagerDialog.show();

        fetchOpenclaudeVersions((versions, errorMessage) -> {
            if (!canShowDialog()) {
                setOpenclaudeVersionManagerBusy(false);
                return;
            }
            dismissVersionDialog();

            if (versions == null || versions.isEmpty()) {
                showOpenclaudeVersionManagerErrorDialog(
                    TextUtils.isEmpty(errorMessage) ? getString(R.string.koda_no_versions_available) : errorMessage
                );
                return;
            }

            showOpenclaudeVersionListDialog(versions);
        });
    }

    private void showOpenclaudeVersionManagerErrorDialog(String message) {
        if (!canShowDialog()) {
            return;
        }
        if (TextUtils.isEmpty(message)) {
            message = getString(R.string.koda_failed_to_load_version_list);
        }

        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(this)
            .setTitle(getString(R.string.koda_openclaude_versions))
            .setMessage(message)
            .setNegativeButton(R.string.koda_close, (d, w) -> setOpenclaudeVersionManagerBusy(false))
            .setPositiveButton(R.string.koda_retry, (d, w) -> showOpenclaudeVersionManagerDialog())
            .setOnDismissListener(d -> setOpenclaudeVersionManagerBusy(false))
            .create();
        mOpenclaudeVersionManagerDialog.show();
    }

    private void showOpenclaudeVersionListDialog(List<String> versions) {
        if (!canShowDialog()) {
            return;
        }
        final List<String> normalized = OpenclaudeVersionUtils.normalizeVersionList(versions);
        if (normalized.isEmpty()) {
            showOpenclaudeVersionManagerErrorDialog(getString(R.string.koda_no_valid_versions_found));
            return;
        }

        String currentVersion = OpenclaudeVersionUtils.normalizeForSort(KodaService.getOpenclaudeVersion());
        String[] labels = new String[normalized.size()];
        for (int i = 0; i < normalized.size(); i++) {
            String v = normalized.get(i);
            labels[i] = !TextUtils.isEmpty(currentVersion) && TextUtils.equals(currentVersion, v)
                ? getString(R.string.koda_openclaude_current_version, v)
                : getString(R.string.koda_openclaude_version, v);
        }

        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(this)
            .setTitle(getString(R.string.koda_openclaude_versions))
            .setItems(labels, (d, which) -> {
                if (which < 0 || which >= normalized.size()) {
                    setOpenclaudeVersionManagerBusy(false);
                    return;
                }
                showOpenclaudeVersionInstallConfirm(normalized.get(which));
            })
            .setNegativeButton(R.string.koda_close, (d, w) -> setOpenclaudeVersionManagerBusy(false))
            .create();
        mOpenclaudeVersionManagerDialog.show();
    }

    private void showOpenclaudeVersionInstallConfirm(String version) {
        if (!canShowDialog()) {
            return;
        }
        String installVersion = OpenclaudeVersionUtils.normalizeInstallVersion(version);
        if (TextUtils.isEmpty(installVersion)) {
            setOpenclaudeVersionManagerBusy(false);
            Toast.makeText(this, getString(R.string.koda_invalid_version_format), Toast.LENGTH_SHORT).show();
            return;
        }

        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(this)
            .setTitle(getString(R.string.koda_install) + " " + getString(R.string.koda_openclaude))
            .setMessage(getString(R.string.koda_install_openclaude_confirm, installVersion))
            .setCancelable(false)
            .setPositiveButton(R.string.koda_install, (d, w) -> {
                setOpenclaudeVersionManagerBusy(true);
                startOpenclaudeUpdate(installVersion);
            })
            .setNegativeButton(R.string.koda_cancel, (d, w) -> setOpenclaudeVersionManagerBusy(false))
            .setOnDismissListener(d -> setOpenclaudeVersionManagerBusy(false))
            .create();
        mOpenclaudeVersionManagerDialog.show();
    }

    private void fetchOpenclaudeVersions(OpenclaudeVersionUtils.VersionListCallback cb) {
        if (cb == null) {
            return;
        }
        String currentVersion = KodaService.getOpenclaudeVersion();

        mKodaService.executeCommand(
            OpenclaudeVersionUtils.VERSIONS_COMMAND,
            OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS,
            result -> {
                if (result == null || !result.success) {
                    String fallbackError = result == null
                        ? getString(R.string.koda_failed_to_fetch_versions)
                        : getString(R.string.koda_failed_to_fetch_versions_exit, String.valueOf(result.exitCode));
                    cb.onResult(OpenclaudeVersionUtils.buildFallback(currentVersion), fallbackError);
                    return;
                }

                List<String> versions = OpenclaudeVersionUtils.parseVersions(result.stdout);
                if (versions.isEmpty()) {
                    cb.onResult(OpenclaudeVersionUtils.buildFallback(currentVersion), getString(R.string.koda_no_versions_found));
                    return;
                }
                cb.onResult(versions, null);
            }
        );
    }

    private void setOpenclaudeVersionManagerBusy(boolean isBusy) {
        mOpenclaudeVersionActionInProgress = isBusy;
        if (mChangeVersionButton != null) {
            mChangeVersionButton.setEnabled(!isBusy);
        }
    }

    private void startOpenclaudeUpdate(String targetVersion) {
        if (TextUtils.isEmpty(targetVersion)) {
            Toast.makeText(this, getString(R.string.koda_no_update_target_version), Toast.LENGTH_SHORT).show();
            setOpenclaudeVersionManagerBusy(false);
            return;
        }

        dismissVersionDialog();
        setOpenclaudeVersionManagerBusy(true);
        if (!mBound || mKodaService == null) {
            setOpenclaudeVersionManagerBusy(false);
            return;
        }

        AnalyticsManager.logEvent(this, "settings_version_update_started");

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_openclaude_update, null);
        TextView[] stepIcons = {
            dialogView.findViewById(R.id.update_step_0_icon),
            dialogView.findViewById(R.id.update_step_1_icon),
            dialogView.findViewById(R.id.update_step_2_icon),
            dialogView.findViewById(R.id.update_step_3_icon),
            dialogView.findViewById(R.id.update_step_4_icon),
        };
        TextView[] stepPercents = {
            dialogView.findViewById(R.id.update_step_0_percent),
            dialogView.findViewById(R.id.update_step_1_percent),
            dialogView.findViewById(R.id.update_step_2_percent),
            dialogView.findViewById(R.id.update_step_3_percent),
            dialogView.findViewById(R.id.update_step_4_percent),
        };
        TextView statusMessage = dialogView.findViewById(R.id.update_status_message);

        AlertDialog progressDialog = KodaDialogStyler.createBuilder(this)
            .setTitle(R.string.koda_updating_openclaude)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        progressDialog.show();
        KodaDialogStyler.applyTransparentCardWindow(progressDialog);

        mKodaService.updateOpenclaude(targetVersion, new KodaService.UpdateProgressCallback() {
            private int currentStep = -1;

            private void advanceToStep(int nextStep) {
                if (nextStep < 0) {
                    return;
                }
                for (int i = 0; i < nextStep && i < stepIcons.length; i++) {
                    stepIcons[i].setText("\u2713");
                    stepPercents[i].setText(StepPercentUtils.formatPercent(100));
                }
                if (nextStep < stepIcons.length) {
                    stepIcons[nextStep].setText("\u25CF");
                    if (nextStep > currentStep) {
                        stepPercents[nextStep].setText(StepPercentUtils.formatPercent(0));
                    }
                }
                currentStep = nextStep;
            }

            @Override
            public void onStepStart(String message) {
                int nextStep = OpenclaudeUpdateProgress.resolveStepFromMessage(message);
                advanceToStep(nextStep);
                if (nextStep >= 0 && nextStep < stepPercents.length) {
                    stepPercents[nextStep].setText(
                        StepPercentUtils.formatPercent(
                            StepPercentUtils.extractPercent(message, 0)
                        )
                    );
                }
            }

            @Override
            public void onError(String error) {
                AnalyticsManager.logEvent(KodaSettingsActivity.this, "settings_version_update_failed");
                progressDialog.dismiss();
                setOpenclaudeVersionManagerBusy(false);
                if (canShowDialog()) {
                    KodaDialogStyler.createBuilder(KodaSettingsActivity.this)
                        .setTitle(R.string.koda_update_failed)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
                refreshCurrentVersion();
            }

            @Override
            public void onComplete(String newVersion) {
                advanceToStep(OpenclaudeUpdateProgress.STEP_REFRESHING_MODELS);
                for (int i = 0; i < stepPercents.length; i++) {
                    stepPercents[i].setText(StepPercentUtils.formatPercent(100));
                }
                statusMessage.setText(getString(R.string.koda_updated_to_version, newVersion));
                AnalyticsManager.logEvent(KodaSettingsActivity.this, "settings_version_update_completed");

                mHandler.postDelayed(() -> {
                    if (canShowDialog()) {
                        progressDialog.dismiss();
                    }
                    setOpenclaudeVersionManagerBusy(false);
                    refreshCurrentVersion();
                }, 1500);
            }
        });
    }

    private void dismissVersionDialog() {
        if (mOpenclaudeVersionManagerDialog != null && mOpenclaudeVersionManagerDialog.isShowing()) {
            mOpenclaudeVersionManagerDialog.dismiss();
        }
        mOpenclaudeVersionManagerDialog = null;
    }
}
