package dev.koda;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
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
    private TextView        mInstallStepIndicator;

    // Step views - each step has pending/current/done views
    private View[]          mStepContainers;
    private View[][]        mStepIndicators; // [step][state: 0=pending, 1=current, 2=done]
    private TextView[]      mStepLabels;

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
        mInstallStepIndicator   = findViewById(R.id.install_step_indicator);
        mInstallSuccess         = findViewById(R.id.install_success);
        mInstallError           = findViewById(R.id.install_error);
        mInstallErrorMsg        = findViewById(R.id.install_error_msg);
        mInstallShowLogs        = findViewById(R.id.install_show_logs);
        mInstallScroll          = findViewById(R.id.install_scroll);
        mInstallStatus          = findViewById(R.id.install_status);
        mInstallButton          = findViewById(R.id.install_button);

        // Step containers
        mStepContainers = new View[5];
        mStepContainers[0] = findViewById(R.id.step_1);
        mStepContainers[1] = findViewById(R.id.step_2);
        mStepContainers[2] = findViewById(R.id.step_3);
        mStepContainers[3] = findViewById(R.id.step_4);
        mStepContainers[4] = findViewById(R.id.step_5);

        // Step indicators [step][state]
        mStepIndicators = new View[5][3];
        for (int i = 0; i < 5; i++) {
            int stepNum = i + 1;
            mStepIndicators[i][0] = findViewById(getResources().getIdentifier("step_" + stepNum + "_pending", "id", getPackageName()));
            mStepIndicators[i][1] = findViewById(getResources().getIdentifier("step_" + stepNum + "_current", "id", getPackageName()));
            mStepIndicators[i][2] = findViewById(getResources().getIdentifier("step_" + stepNum + "_done", "id", getPackageName()));
        }

        // Step labels
        mStepLabels = new TextView[5];
        mStepLabels[0] = findViewById(R.id.step_1_label);
        mStepLabels[1] = findViewById(R.id.step_2_label);
        mStepLabels[2] = findViewById(R.id.step_3_label);
        mStepLabels[3] = findViewById(R.id.step_4_label);
        mStepLabels[4] = findViewById(R.id.step_5_label);

        // Initialize all steps to pending
        for (int i = 0; i < 5; i++) {
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
        // Check if OpenClaude is installed using static method
        if (KodaService.isOpenclaudeInstalled()) {
            // Skip to chat
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

        // Simulate install progress for now
        simulateInstallProgress();
    }

    private void simulateInstallProgress() {
        // Simulate progress through steps
        for (int i = 0; i < 5; i++) {
            final int step = i;
            mInstallContainer.postDelayed(() -> {
                updateStepProgress(step);
            }, i * 1500);
        }
        
        // Complete after all steps
        mInstallContainer.postDelayed(() -> {
            showInstallSuccess();
        }, 8000);
    }

    private void updateStepProgress(int step) {
        // Mark previous steps as completed
        for (int i = 0; i < step; i++) {
            setStepState(i, StepState.COMPLETED);
        }
        // Mark current step as active
        if (step < 5) {
            setStepState(step, StepState.CURRENT);
        }
        // Update step indicator text
        if (mInstallStepIndicator != null) {
            mInstallStepIndicator.setText("Step " + (step + 1) + " of 5");
        }
        // Update hint
        if (step < STEP_HINTS.length) {
            mInstallHint.setText(STEP_HINTS[step]);
        }
    }

    private void resetSteps() {
        for (int i = 0; i < 5; i++) {
            setStepState(i, StepState.PENDING);
        }
        mInstallHint.setText("This may take a few minutes");
        if (mInstallStepIndicator != null) {
            mInstallStepIndicator.setText("Step 1 of 5");
        }
    }

    private void setStepState(int stepIndex, StepState state) {
        if (stepIndex >= mStepIndicators.length) return;

        // Hide all indicators for this step
        for (int i = 0; i < 3; i++) {
            if (mStepIndicators[stepIndex][i] != null) {
                mStepIndicators[stepIndex][i].setVisibility(View.GONE);
            }
        }

        // Show the appropriate indicator
        int stateIndex;
        switch (state) {
            case PENDING: stateIndex = 0; break;
            case CURRENT: stateIndex = 1; break;
            case COMPLETED: stateIndex = 2; break;
            default: stateIndex = 0;
        }

        if (mStepIndicators[stepIndex][stateIndex] != null) {
            mStepIndicators[stepIndex][stateIndex].setVisibility(View.VISIBLE);
        }

        // Update label style
        if (mStepLabels[stepIndex] != null) {
            switch (state) {
                case PENDING:
                    mStepLabels[stepIndex].setTextColor(getColor(R.color.koda_text_tertiary));
                    mStepLabels[stepIndex].setTypeface(null, android.graphics.Typeface.NORMAL);
                    break;
                case CURRENT:
                    mStepLabels[stepIndex].setTextColor(getColor(R.color.koda_text_primary));
                    mStepLabels[stepIndex].setTypeface(null, android.graphics.Typeface.BOLD);
                    break;
                case COMPLETED:
                    mStepLabels[stepIndex].setTextColor(getColor(R.color.koda_text_secondary));
                    mStepLabels[stepIndex].setTypeface(null, android.graphics.Typeface.NORMAL);
                    break;
            }
        }
    }

    private void showInstallSuccess() {
        mInstallProgress.setVisibility(View.GONE);
        mInstallSuccess.setVisibility(View.VISIBLE);
        mInstallButton.setText("Continue");
        mInstallButton.setEnabled(true);
        mInstallButton.setOnClickListener(v -> {
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

    // ─── Step states ──────────────────────────────────────────────────────────
    private enum StepState {
        PENDING,
        CURRENT,
        COMPLETED
    }
}
