package com.termux.app;

import android.app.Application;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.crash.TermuxCrashUtils;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        TermuxBootstrap.setTermuxPackageManagerAndVariant(com.termux.BuildConfig.TERMUX_PACKAGE_VARIANT);
        TermuxCrashUtils.setCrashHandler(this);
        Logger.setLogLevel(null, Logger.LOG_LEVEL_NORMAL);
        Logger.logInfo(LOG_TAG, "Koda application started");
    }
}
