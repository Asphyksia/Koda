package dev.koda;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.AnalyticsManager;
import com.termux.app.TermuxInstaller;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Step 1 of setup: Choose which agent to install.
 *
 * Currently offers:
 * - OpenClaude (available, triggers install)
 * - OwliaBot (a distinct AI agent product, not a rename leftover - coming soon, disabled)
 */
public class AgentSelectionFragment extends Fragment {

    private static final String LOG_TAG = "AgentSelectionFragment";

    public static final String PREFS_NAME = "koda_settings";
    public static final String KEY_OPENCLAUDE_VERSION = "openclaude_install_version";
    private static final String PINNED_VERSION = "openclaude@2026.2.6";
    private static final int TAP_COUNT_THRESHOLD = 10;
    private static final long TAP_WINDOW_MS = 5000;
    private static final long OPENCLAUDE_VERSION_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);
    private static final int OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS = 180;
    private static final int OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS_RETRY = 300;
    private static final String KEY_OPENCLAUDE_VERSION_CACHE = "openclaude_versions_cache";
    private static final String KEY_OPENCLAUDE_VERSION_CACHE_TIME = "openclaude_versions_cache_time";

    private KodaService mKodaService;
    private boolean mServiceBound = false;
    private AlertDialog mOpenclaudeVersionManagerDialog;
    private StepProgressDialog mProgressDialog;
    private boolean mOpenclaudeVersionActionInProgress;
    private long mOpenclaudeVersionRequestId;
    private boolean mOpenclaudeVersionManagementDisabled;
    private int mTapCount = 0;
    private long mFirstTapTime = 0;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            KodaService.LocalBinder binder = (KodaService.LocalBinder) service;
            mKodaService = binder.getService();
            mServiceBound = true;
            Logger.logDebug(LOG_TAG, "KodaService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            mKodaService = null;
            Logger.logDebug(LOG_TAG, "KodaService disconnected");
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_koda_agent_select, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button installButton = view.findViewById(R.id.agent_openclaude_install);
        mOpenclaudeVersionManagementDisabled = BundledOpenclaudeUtils.shouldDisableVersionManagement(
            BundledOpenclaudeUtils.loadManifest(requireContext())
        );
        final boolean isOpenclaudeInstalled = KodaService.isOpenclaudeInstalled();
        installButton.setText(isOpenclaudeInstalled ? R.string.koda_open : R.string.koda_install);
        installButton.setOnClickListener(v -> {
            if (isOpenclaudeInstalled) {
                AnalyticsManager.logEvent(requireContext(), "agent_open_dashboard_tap");
                Logger.logInfo(LOG_TAG, "OpenClaude already installed, opening dashboard");
                openDashboard();
            } else {
                AnalyticsManager.logEvent(requireContext(), "agent_install_tap");
                Logger.logInfo(LOG_TAG, "OpenClaude selected for installation");
                SetupActivity activity = (SetupActivity) getActivity();
                if (activity != null && !activity.isFinishing()) {
                    activity.goToNextStep();
                }
            }
        });

        // URL click handlers
        view.findViewById(R.id.agent_openclaude_url).setOnClickListener(v -> {
            AnalyticsManager.logEvent(requireContext(), "agent_openclaude_link_tap");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://openclaude.ai")));
        });

        // Easter egg: tap OpenClaude icon 10 times to pin install version
        view.findViewById(R.id.agent_openclaude_icon).setOnClickListener(v -> {
            if (mOpenclaudeVersionManagementDisabled) {
                return;
            }
            long now = System.currentTimeMillis();
            if (mTapCount == 0 || now - mFirstTapTime > TAP_WINDOW_MS) {
                mTapCount = 1;
                mFirstTapTime = now;
            } else {
                mTapCount++;
            }

            if (mTapCount >= TAP_COUNT_THRESHOLD) {
                mTapCount = 0;
                showVersionPinDialog();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() == null) {
            return;
        }
        Intent intent = new Intent(getActivity(), KodaService.class);
        boolean bound = getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (bound) {
            mServiceBound = true;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissOpenclaudeVersionManagerDialog();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        if (mServiceBound && getActivity() != null) {
            try {
                getActivity().unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
                // Service was not bound or already unbound.
            }
            mServiceBound = false;
            mKodaService = null;
        }
    }

    private void showOpenclaudeVersionListDialog() {
        if (mOpenclaudeVersionManagementDisabled) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        if (!mServiceBound || mKodaService == null) {
            KodaDialogStyler.createBuilder(ctx)
                .setTitle(R.string.koda_openclaude_versions)
                .setMessage(R.string.koda_service_not_connected_try_later)
                .setNegativeButton(R.string.koda_close, null)
                .show();
            return;
        }

        if (mOpenclaudeVersionActionInProgress) {
            return;
        }

        mOpenclaudeVersionActionInProgress = true;
        final long requestId = ++mOpenclaudeVersionRequestId;
        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(ctx)
            .setTitle(R.string.koda_openclaude_versions)
            .setMessage(R.string.koda_loading_versions)
            .setCancelable(false)
            .setNegativeButton(R.string.koda_cancel, (d, w) -> {
                mOpenclaudeVersionActionInProgress = false;
                if (requestId == mOpenclaudeVersionRequestId) {
                    ++mOpenclaudeVersionRequestId;
                }
            })
            .create();
        mOpenclaudeVersionManagerDialog.show();

        fetchOpenclaudeVersions((versions, errorMessage) -> {
            if (requestId != mOpenclaudeVersionRequestId || getActivity() == null || !isAdded()) {
                mOpenclaudeVersionActionInProgress = false;
                return;
            }
            dismissOpenclaudeVersionManagerDialog();
            if (versions == null || versions.isEmpty()) {
                showOpenclaudeVersionManagerError(TextUtils.isEmpty(errorMessage)
                    ? getString(R.string.koda_no_versions_available)
                    : errorMessage);
                return;
            }
            showOpenclaudeVersions(versions);
        });
    }

    private void dismissOpenclaudeVersionManagerDialog() {
        if (mOpenclaudeVersionManagerDialog != null) {
            mOpenclaudeVersionManagerDialog.dismiss();
            mOpenclaudeVersionManagerDialog = null;
        }
        mOpenclaudeVersionActionInProgress = false;
    }

    private void showOpenclaudeVersionManagerError(String message) {
        Context ctx = getContext();
        if (ctx == null) {
            mOpenclaudeVersionActionInProgress = false;
            return;
        }
        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(ctx)
            .setTitle(R.string.koda_openclaude_versions)
            .setMessage(message)
            .setNegativeButton(R.string.koda_close, (d, w) -> mOpenclaudeVersionActionInProgress = false)
            .setPositiveButton(R.string.koda_retry, (d, w) -> showOpenclaudeVersionListDialog())
            .setCancelable(false)
            .setOnDismissListener(d -> mOpenclaudeVersionActionInProgress = false)
            .show();
    }

    private void showOpenclaudeVersions(List<String> versions) {
        Context ctx = getContext();
        if (ctx == null) {
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        List<String> normalized = OpenclaudeVersionUtils.normalizeVersionList(versions);
        if (normalized.isEmpty()) {
            mOpenclaudeVersionActionInProgress = false;
            showOpenclaudeVersionManagerError(getString(R.string.koda_no_valid_versions_found));
            return;
        }

        String currentVersion = OpenclaudeVersionUtils.normalizeForSort(KodaService.getOpenclaudeVersion());

        // Ensure current version is in the list
        if (!TextUtils.isEmpty(currentVersion) && !normalized.contains(currentVersion)) {
            normalized.add(currentVersion);
            normalized = new ArrayList<>(OpenclaudeVersionUtils.sortAndLimit(normalized));
        }
        final List<String> finalNormalized = normalized;

        String[] labels = new String[normalized.size()];
        for (int i = 0; i < normalized.size(); i++) {
            String v = normalized.get(i);
            if (!TextUtils.isEmpty(currentVersion) && TextUtils.equals(currentVersion, v)) {
                labels[i] = getString(R.string.koda_openclaude_current_version, v);
            } else {
                labels[i] = getString(R.string.koda_openclaude_version, v);
            }
        }

        mOpenclaudeVersionActionInProgress = true;
        mOpenclaudeVersionManagerDialog = KodaDialogStyler.createBuilder(ctx)
            .setTitle(R.string.koda_openclaude_versions)
            .setItems(labels, (d, which) -> {
                if (which < 0 || which >= finalNormalized.size()) {
                    mOpenclaudeVersionActionInProgress = false;
                    return;
                }
                handleOpenclaudeVersionPick(finalNormalized.get(which));
            })
            .setNegativeButton(R.string.koda_close, (d, w) -> mOpenclaudeVersionActionInProgress = false)
            .create();
        mOpenclaudeVersionManagerDialog.show();
    }

    private void handleOpenclaudeVersionPick(String version) {
        String picked = OpenclaudeVersionUtils.normalizeForSort(version);
        if (TextUtils.isEmpty(picked)) {
            Context ctx = getContext();
        if (ctx != null) {
                Toast.makeText(ctx, getString(R.string.koda_invalid_version_format), Toast.LENGTH_SHORT).show();
            }
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        String currentVersion = OpenclaudeVersionUtils.normalizeForSort(KodaService.getOpenclaudeVersion());
        if (!TextUtils.isEmpty(currentVersion) && TextUtils.equals(currentVersion, picked)) {
            openDashboard();
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        final String installVersion = OpenclaudeVersionUtils.normalizeInstallVersion(picked);
        if (TextUtils.isEmpty(installVersion)) {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, getString(R.string.koda_invalid_install_version), Toast.LENGTH_SHORT).show();
            }
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        Context ctx = getContext();
        if (ctx == null) {
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        if (KodaService.isOpenclaudeInstalled()) {
            KodaDialogStyler.createBuilder(ctx)
                .setTitle(R.string.koda_install)
                .setMessage(getString(
                    R.string.koda_install_openclaude_installed_title,
                    TextUtils.isEmpty(currentVersion) ? getString(R.string.koda_unknown) : currentVersion,
                    installVersion
                ))
                .setNegativeButton(R.string.koda_cancel, (d, w) -> mOpenclaudeVersionActionInProgress = false)
                .setPositiveButton(R.string.koda_install, (d, w) -> installOpenclaudeInPlace(installVersion))
                .setCancelable(false)
                .setOnDismissListener(d -> mOpenclaudeVersionActionInProgress = false)
                .show();
        } else {
            KodaDialogStyler.createBuilder(ctx)
                .setTitle(R.string.koda_install)
                .setMessage(getString(R.string.koda_install_openclaude_not_installed_title, installVersion))
                .setNegativeButton(R.string.koda_cancel, (d, w) -> mOpenclaudeVersionActionInProgress = false)
                .setPositiveButton(R.string.koda_install, (d, w) -> installOpenclaudeWithSetup(installVersion))
                .setCancelable(false)
                .setOnDismissListener(d -> mOpenclaudeVersionActionInProgress = false)
                .show();
        }
    }

    private void installOpenclaudeInPlace(String installVersion) {
        if (mKodaService == null) {
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        Context ctx = getContext();
        if (ctx == null) {
            mOpenclaudeVersionActionInProgress = false;
            return;
        }

        mProgressDialog = StepProgressDialog.create(
            ctx,
            R.string.koda_install,
            Arrays.asList(
                getString(R.string.koda_stopping_gateway),
                getString(R.string.koda_installing_update),
                getString(R.string.koda_finalizing),
                getString(R.string.koda_starting_gateway),
                getString(R.string.koda_refreshing_model_list)
            ),
            getString(R.string.koda_may_take_a_few_minutes)
        );
        mProgressDialog.show();
        AnalyticsManager.logEvent(ctx, "agent_version_install_started");

        mKodaService.updateOpenclaude(installVersion, new KodaService.UpdateProgressCallback() {
            @Override
            public void onStepStart(String message) {
                if (mProgressDialog == null) {
                    return;
                }
                int nextStep = OpenclaudeUpdateProgress.resolveStepFromMessage(message);
                if (nextStep < 0) {
                    mProgressDialog.setStatus(message);
                    return;
                }
                mProgressDialog.setStep(nextStep, message);
                mProgressDialog.setStatus(message);
            }

            @Override
            public void onError(String error) {
                AnalyticsManager.logEvent(requireContext(), "agent_version_install_failed");
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.showError(
                        getString(R.string.koda_install_failed, error),
                        () -> {
                            mProgressDialog = null;
                            mOpenclaudeVersionActionInProgress = false;
                        }
                    );
                } else {
                    mOpenclaudeVersionActionInProgress = false;
                }
            }

            @Override
            public void onComplete(String version) {
                if (mProgressDialog == null) {
                    mOpenclaudeVersionActionInProgress = false;
                    return;
                }
                AnalyticsManager.logEvent(requireContext(), "agent_version_install_completed");
                mProgressDialog.complete(getString(
                    R.string.koda_installation_complete_with_version,
                    TextUtils.isEmpty(version) ? getString(R.string.koda_unknown) : version
                ));

                if (getActivity() != null && isAdded()) {
                    getActivity().getWindow().getDecorView().postDelayed(() -> {
                        if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                            mProgressDialog = null;
                        }
                        mOpenclaudeVersionActionInProgress = false;
                        openDashboard();
                    }, 1500);
                    return;
                }
                mOpenclaudeVersionActionInProgress = false;
            }
        });
    }

    private void installOpenclaudeWithSetup(String installVersion) {
        mOpenclaudeVersionActionInProgress = false;
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_OPENCLAUDE_VERSION, installVersion).apply();
        TermuxInstaller.createKodaScripts(ctx, installVersion);

        SetupActivity activity = (SetupActivity) getActivity();
        if (activity != null && !activity.isFinishing()) {
            activity.goToNextStep();
        }
    }

    private void fetchOpenclaudeVersions(OpenclaudeVersionUtils.VersionListCallback cb) {
        fetchOpenclaudeVersionsWithTimeout(cb, OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS, false);
    }

    private void fetchOpenclaudeVersionsWithTimeout(
        OpenclaudeVersionUtils.VersionListCallback cb,
        int timeoutSeconds,
        boolean retried
    ) {
        if (cb == null) {
            return;
        }

        if (mKodaService == null || !mServiceBound) {
            String currentVersion = KodaService.getOpenclaudeVersion();
            cb.onResult(
                OpenclaudeVersionUtils.buildFallback(currentVersion),
                getString(R.string.koda_service_not_connected)
            );
            return;
        }

        List<String> cachedVersions = loadOpenclaudeVersionCache();
        if (cachedVersions != null && !cachedVersions.isEmpty() && isOpenclaudeVersionCacheFresh()) {
            Logger.logInfo(LOG_TAG, "OpenClaude versions loaded from cache");
            cb.onResult(cachedVersions, null);
            return;
        }

        String currentVersion = KodaService.getOpenclaudeVersion();
        mKodaService.executeCommand(
            OpenclaudeVersionUtils.VERSIONS_COMMAND,
            timeoutSeconds,
            result -> {
            if (result == null || !result.success) {
                if (!retried) {
                    Logger.logWarn(
                        LOG_TAG,
                        "OpenClaude versions fetch failed, retrying with longer timeout: " +
                        OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS_RETRY + "s"
                    );
                    fetchOpenclaudeVersionsWithTimeout(cb, OPENCLAUDE_VERSION_FETCH_TIMEOUT_SECONDS_RETRY, true);
                    return;
                }
                if (cachedVersions != null && !cachedVersions.isEmpty()) {
                    cb.onResult(cachedVersions,
                        result == null
                            ? getString(R.string.koda_failed_to_fetch_versions)
                            : getString(R.string.koda_failed_to_fetch_versions_exit, String.valueOf(result.exitCode))
                    );
                    return;
                }
                cb.onResult(OpenclaudeVersionUtils.buildFallback(currentVersion),
                    result == null
                        ? getString(R.string.koda_failed_to_fetch_versions)
                        : getString(R.string.koda_failed_to_fetch_versions_exit, String.valueOf(result.exitCode))
                );
                return;
            }

            List<String> versions = OpenclaudeVersionUtils.parseVersions(result.stdout);
            if (versions.isEmpty()) {
                if (cachedVersions != null && !cachedVersions.isEmpty()) {
                    cb.onResult(cachedVersions, getString(R.string.koda_no_versions_found));
                    return;
                }
                cb.onResult(OpenclaudeVersionUtils.buildFallback(currentVersion), getString(R.string.koda_no_versions_found));
                return;
            }

            persistOpenclaudeVersionCache(versions);
            cb.onResult(versions, null);
            });
    }

    private boolean isOpenclaudeVersionCacheFresh() {
        Context ctx = getContext();
        if (ctx == null) {
            return false;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long cacheTime = prefs.getLong(KEY_OPENCLAUDE_VERSION_CACHE_TIME, 0L);
        if (cacheTime <= 0) {
            return false;
        }
        return System.currentTimeMillis() - cacheTime <= OPENCLAUDE_VERSION_CACHE_TTL_MS;
    }

    private List<String> loadOpenclaudeVersionCache() {
        Context ctx = getContext();
        if (ctx == null) {
            return null;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rawCache = prefs.getString(KEY_OPENCLAUDE_VERSION_CACHE, null);
        if (TextUtils.isEmpty(rawCache)) {
            return null;
        }

        List<String> versions = new ArrayList<>();
        try {
            JSONArray cacheArray = new JSONArray(rawCache);
            for (int i = 0; i < cacheArray.length(); i++) {
                String token = cacheArray.optString(i, null);
                String normalized = OpenclaudeVersionUtils.normalizeForSort(token);
                if (OpenclaudeVersionUtils.isStableVersion(normalized)) {
                    versions.add(normalized);
                }
            }
        } catch (Exception e) {
            return null;
        }

        return OpenclaudeVersionUtils.sortAndLimit(versions);
    }

    private void persistOpenclaudeVersionCache(List<String> versions) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (versions == null || versions.isEmpty()) {
            prefs.edit().remove(KEY_OPENCLAUDE_VERSION_CACHE).remove(KEY_OPENCLAUDE_VERSION_CACHE_TIME).apply();
            return;
        }

        List<String> stableSorted = OpenclaudeVersionUtils.sortAndLimit(versions);
        JSONArray cacheArray = new JSONArray();
        for (String version : stableSorted) {
            String normalized = OpenclaudeVersionUtils.normalizeForSort(version);
            if (!TextUtils.isEmpty(normalized)) {
                cacheArray.put(normalized);
            }
        }

        prefs.edit()
            .putString(KEY_OPENCLAUDE_VERSION_CACHE, cacheArray.toString())
            .putLong(KEY_OPENCLAUDE_VERSION_CACHE_TIME, System.currentTimeMillis())
            .apply();
    }

    private void showVersionPinDialog() {
        if (mOpenclaudeVersionManagementDisabled) {
            return;
        }
        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String current = prefs.getString(KEY_OPENCLAUDE_VERSION, null);
        boolean isPinned = PINNED_VERSION.equals(current);

        if (isPinned) {
            KodaDialogStyler.createBuilder(ctx)
                .setTitle(R.string.koda_openclaude_version_manager_title)
                .setMessage(getString(R.string.koda_current_install_version, PINNED_VERSION) + "\n\n"
                    + getString(R.string.koda_reset_to_latest) + "?")
                .setPositiveButton(R.string.koda_reset_to_latest, (d, w) -> {
                    prefs.edit().remove(KEY_OPENCLAUDE_VERSION).apply();
                    TermuxInstaller.createKodaScripts(ctx, "openclaude@latest");
                    Toast.makeText(ctx, getString(R.string.koda_set_to_latest, "openclaude@latest"), Toast.LENGTH_SHORT).show();
                    Logger.logInfo(LOG_TAG, "OpenClaude version reset to latest");
                })
                .setNegativeButton(R.string.koda_cancel, null)
                .show();
        } else {
            KodaDialogStyler.createBuilder(ctx)
                .setTitle(R.string.koda_openclaude_version_manager_title)
                .setMessage(getString(R.string.koda_pin_install_version, PINNED_VERSION))
                .setPositiveButton(R.string.koda_pin, (d, w) -> {
                    prefs.edit().putString(KEY_OPENCLAUDE_VERSION, PINNED_VERSION).apply();
                    TermuxInstaller.createKodaScripts(ctx, PINNED_VERSION);
                    Toast.makeText(ctx, getString(R.string.koda_set_to_latest, PINNED_VERSION), Toast.LENGTH_SHORT).show();
                    Logger.logInfo(LOG_TAG, "OpenClaude version pinned to " + PINNED_VERSION);
                })
                .setNegativeButton(R.string.koda_cancel, null)
                .show();
        }
    }

    private void openDashboard() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        Intent dashboardIntent = new Intent(ctx, DashboardActivity.class);
        startActivity(dashboardIntent);
        android.app.Activity activity = getActivity();
        if (activity instanceof SetupActivity && !activity.isFinishing()) {
            activity.finish();
        }
    }
}
