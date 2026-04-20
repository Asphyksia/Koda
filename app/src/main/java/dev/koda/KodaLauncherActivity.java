package dev.koda;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxInstaller;

import dev.koda.ui.BaseActivity;
import dev.koda.ui.ChatActivity;

/**
 * Entry point for Koda.
 * Handles permissions → bootstrap → routing to Setup or Chat.
 * Shows a hero card with animated code preview on first launch.
 */
public class KodaLauncherActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int BATTERY_REQUEST_CODE    = 1002;

    // Card + animation views
    private View     mHeroCard;
    private View     mCodeLine1, mCodeLine2, mCodeLine3, mCodeLine4, mCodeLine5;
    private TextView mCodeCursor;
    private TextView mLauncherStatus;

    // CTAs
    private Button   mContinueButton;
    private TextView mSkipButton;
    private View     mBatteryBlock;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mPermissionsRequested = false;
    private ValueAnimator mCursorBlink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mHeroCard       = findViewById(R.id.hero_card);
        mCodeLine1      = findViewById(R.id.code_line_1);
        mCodeLine2      = findViewById(R.id.code_line_2);
        mCodeLine3      = findViewById(R.id.code_line_3);
        mCodeLine4      = findViewById(R.id.code_line_4);
        mCodeLine5      = findViewById(R.id.code_line_5);
        mCodeCursor     = findViewById(R.id.code_cursor);
        mLauncherStatus = findViewById(R.id.launcher_status);
        mContinueButton = findViewById(R.id.launcher_continue);
        mSkipButton     = findViewById(R.id.launcher_skip);
        mBatteryBlock   = findViewById(R.id.battery_block);

        mContinueButton.setOnClickListener(v -> requestBatteryOptimization());
        mSkipButton.setOnClickListener(v -> proceedToRoute());

        // Animate card in, then start code animation, then check permissions
        animateCardIn(() -> {
            animateCodeLines(() ->
                mHandler.postDelayed(this::checkPermissions, 300));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (mCursorBlink != null) mCursorBlink.cancel();
    }

    // =========================================================
    // Animations
    // =========================================================

    private void animateCardIn(Runnable onDone) {
        mHeroCard.setAlpha(0f);
        mHeroCard.setTranslationY(40f);
        mHeroCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(new DecelerateInterpolator(1.6f))
            .setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { if (onDone != null) onDone.run(); }
            })
            .start();
    }

    /** Fades in code lines one by one, then starts cursor blink loop. */
    private void animateCodeLines(Runnable onDone) {
        View[] lines = { mCodeLine1, mCodeLine2, mCodeLine3, mCodeLine4, mCodeLine5 };
        int delay = 0;
        for (View line : lines) {
            final int d = delay;
            mHandler.postDelayed(() ->
                line.animate().alpha(1f).setDuration(160)
                    .setInterpolator(new DecelerateInterpolator()).start(), d);
            delay += 120;
        }
        // Show cursor after last line
        mHandler.postDelayed(() -> {
            mCodeCursor.setAlpha(1f);
            startCursorBlink();
            if (onDone != null) onDone.run();
        }, delay + 80);
    }

    private void startCursorBlink() {
        mCursorBlink = ValueAnimator.ofFloat(1f, 0f, 1f);
        mCursorBlink.setDuration(900);
        mCursorBlink.setRepeatCount(ValueAnimator.INFINITE);
        mCursorBlink.setInterpolator(new AccelerateDecelerateInterpolator());
        mCursorBlink.addUpdateListener(a ->
            mCodeCursor.setAlpha((float) a.getAnimatedValue()));
        mCursorBlink.start();
    }

    private void showCTAs(boolean needsBattery) {
        if (needsBattery) {
            mContinueButton.setVisibility(View.VISIBLE);
            mSkipButton.setVisibility(View.VISIBLE);
            mContinueButton.setAlpha(0f);
            mSkipButton.setAlpha(0f);
            mContinueButton.animate().alpha(1f).setDuration(220).start();
            mSkipButton.animate().alpha(1f).setDuration(220).setStartDelay(60).start();
        } else {
            // Battery already granted — hide the block and proceed
            mBatteryBlock.setVisibility(View.GONE);
            proceedToRoute();
        }
    }

    // =========================================================
    // Permission + routing logic
    // =========================================================

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (!mPermissionsRequested) {
                    mPermissionsRequested = true;
                    ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                        PERMISSION_REQUEST_CODE);
                    return;
                }
            }
        }
        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean needsBattery = pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName());
        showCTAs(needsBattery);
    }

    private void requestBatteryOptimization() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, BATTERY_REQUEST_CODE);
    }

    private void proceedToRoute() {
        // Stop cursor animation
        if (mCursorBlink != null) mCursorBlink.cancel();

        // 1. Bootstrap installed?
        if (!KodaService.isBootstrapInstalled()) {
            mLauncherStatus.setText("Setting up environment… this may take a minute on first launch.");
            mContinueButton.setVisibility(View.GONE);
            mSkipButton.setVisibility(View.GONE);
            TermuxInstaller.setupBootstrapIfNeeded(this, this::proceedToRoute);
            return;
        }

        // 2. OpenClaude installed + configured?
        if (!KodaService.isOpenclaudeInstalled() || !KodaService.isOpenclaudeConfigured()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        // 3. All ready → Chat
        startActivity(new Intent(this, ChatActivity.class));
        finish();
    }

    // =========================================================
    // Activity result callbacks
    // =========================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            checkBatteryOptimization();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BATTERY_REQUEST_CODE) {
            proceedToRoute();
        }
    }
}
