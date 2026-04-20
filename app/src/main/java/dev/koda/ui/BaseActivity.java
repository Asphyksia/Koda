package dev.koda.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Base activity with Material 3 edge-to-edge support.
 * Handles system bars (status bar, navigation bar) as transparent
 * and manages window insets for proper content padding.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
    }

    /**
     * Configures edge-to-edge display with transparent system bars.
     * Call this after setContentView() if you need to apply insets to specific views.
     */
    protected void setupEdgeToEdge() {
        Window window = getWindow();

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Make status bar and navigation bar transparent
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Configure system bars appearance
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            // Light icons on dark background
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }

        // Handle navigation bar contrast on older devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Apply system bar insets as padding to a view.
     * Useful for top-level containers that need to avoid system bars.
     *
     * @param view The view to apply insets to
     * @param applyTop Apply top inset (status bar)
     * @param applyBottom Apply bottom inset (navigation bar)
     */
    protected void applySystemBarInsets(@NonNull View view, boolean applyTop, boolean applyBottom) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            int paddingTop = applyTop ? systemBars.top : v.getPaddingTop();
            int paddingBottom = applyBottom ? systemBars.bottom : v.getPaddingBottom();

            v.setPadding(v.getPaddingLeft(), paddingTop, v.getPaddingRight(), paddingBottom);

            return insets;
        });
    }

    /**
     * Apply system bar insets as margins to a view.
     *
     * @param view The view to apply insets to
     * @param applyTop Apply top margin (status bar)
     * @param applyBottom Apply bottom margin (navigation bar)
     */
    protected void applySystemBarMargins(@NonNull View view, boolean applyTop, boolean applyBottom) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

                if (applyTop) {
                    params.topMargin = systemBars.top;
                }
                if (applyBottom) {
                    params.bottomMargin = systemBars.bottom;
                }

                v.setLayoutParams(params);
            }

            return insets;
        });
    }

    /**
     * Get the height of the status bar.
     *
     * @return Status bar height in pixels, or 0 if not available
     */
    protected int getStatusBarHeight() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (insets != null) {
            return insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        }
        return 0;
    }

    /**
     * Get the height of the navigation bar.
     *
     * @return Navigation bar height in pixels, or 0 if not available
     */
    protected int getNavigationBarHeight() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (insets != null) {
            return insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        }
        return 0;
    }
}
