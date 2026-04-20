package dev.koda;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;
import dev.koda.ui.ChatActivity;

/**
 * SetupActivity - Premium installation screen
 * Phase 1: Install OpenClaude (if not present)
 */
public class SetupActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SetupActivity";

    // ─── Views: Install phase ─────────────────────────────────────────────────
    private View            mInstallContainer;
    private ProgressBar     mInstallProgress;
    private TextView        mInstallHint;

    // Checklist step views (5 steps)
    private View[]          mStepViews;

    // Success / error states
    private View            mInstallSuccess;
    private View            mInstallError;
    private TextView        mInstallErrorMsg;
    private TextView        mInstallShowLogs;
    private ScrollView      mInstallScroll;
    private TextView        mInstallStatus;
    private Button          mInstallButton;

    // Log accumulator
    private final StringBuilder mRawLog = new StringBuilder();

    // ─── Step definitions ─────────────────────────────────────────────────────
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
        mInstallHint            = findViewById(R.id.install_hint);
        mInstallSuccess         = findViewById(R.id.install_success);
        mInstallError           = findViewById(R.id.install_error);
        mInstallErrorMsg        = findViewById(R.id.install_error_msg);
        mInstallShowLogs        = findViewById(R.id.install_show_logs);
        mInstallScroll          = findViewById(R.id.install_scroll);
        mInstallStatus          = findViewById(R.id.install_status);
        mInstallButton          = findViewById(R.id.install_button);

        // Step checklist views
        int[] stepIds = { R.id.step_1, R.id.step_2, R.id.step_3, R.id.step_4, R.id.step_5 };
        mStepViews = new View[5];
        for (int i = 0; i < 5; i++) {
            mStepViews[i] = findViewById(stepIds[i]);
            setStepState(i, StepState.PENDING);
        }

        mInstallButton.setOnClickListener(v -> {
            mRawLog.setLength(0);
            resetSteps();
            mInstallError.setVisibility(View.GONE);
            runInstall();
        });

        mInstallShowLogs.setOnClickListener(v -> {
            boolean showing = mInstallScroll.getVisibility() == View.VISIBLE;
            mInstallScroll.setVisibility(showing ? View.GONE : View.VISIBLE);
            mInstallShowLogs.setText(showing ? "View details" : "Hide details");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase selection
    // ─────────────────────────────────────────────────────────────────────────

    private void decidePhase() {
        if (mService == null) return;

        // Check if OpenClaude is installed
        if (mService.isOpenClaudeInstalled()) {
            // Skip to chat - wizard temporarily disabled for redesign
            startActivity(new Intent(this, ChatActivity.class));
            finish();
        } else {
            // Show install phase
            mInstallContainer.setVisibility(View.VISIBLE);
            mInstallSuccess.setVisibility(View.GONE);
            mInstallError.setVisibility(View.GONE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Install phase
    // ─────────────────────────────────────────────────────────────────────────

    private void runInstall() {
        mInstallButton.setEnabled(false);
        mInstallButton.setText("Installing...");
        mInstallProgress.setVisibility(View.VISIBLE);

        // Run installation via service
        if (mService != null) {
            mService.installOpenClaude(new KodaService.InstallCallback() {
                @Override
                public void onProgress(int step, String message) {
                    runOnUiThread(() -> updateStepProgress(step, message));
                }

                @Override
                public void onComplete(boolean success, String error) {
                    runOnUiThread(() -> {
                        if (success) {
                            showInstallSuccess();
                        } else {
                            showInstallError(error);
                        }
                    });
                }
            });
        }
    }

    private void updateStepProgress(int step, String message) {
        // Mark previous steps as completed
        for (int i = 0; i < step; i++) {
            setStepState(i, StepState.COMPLETED);
        }
        // Mark current step as active
        if (step < 5) {
            setStepState(step, StepState.CURRENT);
        }
        // Update hint
        if (step < STEP_HINTS.length) {
            mInstallHint.setText(STEP_HINTS[step]);
        }
        // Log
        mRawLog.append(message).append("\n");
        mInstallStatus.setText(mRawLog.toString());
    }

    private void resetSteps() {
        for (int i = 0; i < 5; i++) {
            setStepState(i, StepState.PENDING);
        }
        mInstallHint.setText("This may take a few minutes");
    }

    private void setStepState(int stepIndex, StepState state) {
        if (mStepViews[stepIndex] == null) return;

        View stepView = mStepViews[stepIndex];
        View indicator = stepView.findViewById(R.id.step_indicator);
        TextView label = stepView.findViewById(R.id.step_label);

        switch (state) {
            case PENDING:
                indicator.setBackgroundResource(R.drawable.step_pending);
                label.setTextColor(getColor(R.color.koda_text_tertiary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
            case CURRENT:
                indicator.setBackgroundResource(R.drawable.step_current);
                label.setTextColor(getColor(R.color.koda_text_primary));
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            case COMPLETED:
                indicator.setBackgroundResource(R.drawable.step_completed);
                label.setTextColor(getColor(R.color.koda_text_secondary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
                break;
        }
    }

    private void showInstallSuccess() {
        mInstallProgress.setVisibility(View.GONE);
        mInstallSuccess.setVisibility(View.VISIBLE);
        mInstallButton.setText("Continue");
        mInstallButton.setEnabled(true);
        mInstallButton.setOnClickListener(v -> {
            // Go to chat - wizard temporarily disabled
            startActivity(new Intent(this, ChatActivity.class));
            finish();
        });
    }

    private void showInstallError(String error) {
        mInstallProgress.setVisibility(View.GONE);
        mInstallError.setVisibility(View.VISIBLE);
        mInstallErrorMsg.setText(error);
        mInstallButton.setText("Retry");
        mInstallButton.setEnabled(true);
        mRawLog.append("ERROR: ").append(error).append("\n");
        mInstallStatus.setText(mRawLog.toString());
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density);
    }

    // ─── Step states ──────────────────────────────────────────────────────────
    private enum StepState {
        PENDING,
        CURRENT,
        COMPLETED
    }
}
