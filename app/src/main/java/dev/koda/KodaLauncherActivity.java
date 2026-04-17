package dev.koda;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxInstaller;

import dev.koda.ui.ChatActivity;

/**
 * Entry point for Koda.
 * Handles permissions → bootstrap → routing to Setup or Chat.
 */
public class KodaLauncherActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int BATTERY_REQUEST_CODE = 1002;

    private TextView mStatusText;
    private Button mContinueButton;
    private boolean mPermissionsRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mStatusText = findViewById(R.id.launcher_status);
        mContinueButton = findViewById(R.id.launcher_continue);

        mContinueButton.setOnClickListener(v -> checkPermissions());
        checkPermissions();
    }

    private void checkPermissions() {
        // Check notification permission (Android 13+) — only ask once
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (!mPermissionsRequested) {
                    mPermissionsRequested = true;
                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
                    return;
                }
                // User denied — continue anyway, notifications just won't work
            }
        }

        checkBatteryAndRoute();
    }

    private void checkBatteryAndRoute() {
        // Check battery optimization — show button, don't auto-launch
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            mStatusText.setText("Koda needs unrestricted battery access to run in the background.\n\nYou can skip this and configure it later in Settings.");
            mContinueButton.setText("Grant Battery Access");
            mContinueButton.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, BATTERY_REQUEST_CODE);
            });
            mContinueButton.setVisibility(android.view.View.VISIBLE);

            // Add a skip button via the existing continue button's behavior:
            // After 2 seconds, allow skipping
            mStatusText.postDelayed(() -> {
                if (!isFinishing()) {
                    Button skipBtn = new Button(this);
                    skipBtn.setText("Skip for now");
                    skipBtn.setOnClickListener(sv -> proceedToRoute());
                    android.widget.LinearLayout root = findViewById(R.id.launcher_root);
                    if (root != null) {
                        root.addView(skipBtn);
                    }
                }
            }, 1500);
            return;
        }

        proceedToRoute();
    }

    private void proceedToRoute() {
        checkAndRoute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Whether granted or denied, move on
            checkBatteryAndRoute();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BATTERY_REQUEST_CODE) {
            // Re-check — if granted, proceed; if not, user can still skip
            proceedToRoute();
        }
    }

    private void checkAndRoute() {
        // 1. Bootstrap installed?
        if (!KodaService.isBootstrapInstalled()) {
            mStatusText.setText("Setting up environment...\nThis may take a minute on first launch.");
            mContinueButton.setVisibility(android.view.View.GONE);
            TermuxInstaller.setupBootstrapIfNeeded(this, this::checkAndRoute);
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
}
