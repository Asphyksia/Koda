package com.termux.app;

import android.app.Application;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // Set package variant from BuildConfig
        TermuxBootstrap.setPackageVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Set crash handler
        TermuxCrashUtils.setCrashHandler(this);

        // Set log level
        Logger.setDefaultLogLevel(this, Logger.LOG_LEVEL_NORMAL);

        Logger.logInfo(LOG_TAG, "Koda application started");
    }
}
