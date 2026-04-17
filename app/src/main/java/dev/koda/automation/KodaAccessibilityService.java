package dev.koda.automation;

import android.content.pm.PackageManager;

public interface KodaAccessibilityService {
    PackageManager getPackageManager();
    String getActivePackageName();
    String getLastObservedPackageName();
}
