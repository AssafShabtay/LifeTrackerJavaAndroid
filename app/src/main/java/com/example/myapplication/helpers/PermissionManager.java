package com.example.myapplication.helpers;

import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class PermissionManager {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final String[] requiredPermissions;
    private static final String KEY_PERMISSION_REQUESTED_PREFIX = "requested_";

    public PermissionManager(Activity activity, String[] requiredPermissions) {
        this.activity = activity;
        this.requiredPermissions = requiredPermissions;
        this.prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE);
    }

    public static String[] buildRequiredPermissions() {
        ArrayList<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return perms.toArray(new String[0]);
    }


    public boolean hasAllPermissions() {
        if (requiredPermissions == null) return false;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private SharedPreferences getPermissionPrefs() {
        return prefs;
    }

    public void markPermissionRequested(String permission) {
        getPermissionPrefs().edit().putBoolean(KEY_PERMISSION_REQUESTED_PREFIX + permission, true).apply();
    }

    private boolean wasPermissionRequested(String permission) {
        return getPermissionPrefs().getBoolean(KEY_PERMISSION_REQUESTED_PREFIX + permission, false);
    }

    public boolean shouldShowAnyPermissionRationale() {
        if (requiredPermissions == null) return false;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
                    && shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPermanentlyDenied(String permission) {
        boolean denied = ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED;
        boolean noRationale = !shouldShowRequestPermissionRationale(activity, permission);
        return denied && wasPermissionRequested(permission) && noRationale;
    }

    public boolean isAnyPermissionPermanentlyDenied() {
        if (requiredPermissions == null) return false;
        for (String permission : requiredPermissions) {
            if (isPermanentlyDenied(permission)) {
                return true;
            }
        }
        return false;
    }

    public void showPermissionRationaleDialog(Runnable onRetry, Runnable onCancel) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission required")
                .setMessage("We need these permissions to use the app.")
                .setPositiveButton("Allow", (d, w) -> onRetry.run())
                .setNegativeButton("Not now", (d, w) -> onCancel.run())
                .show();
    }

    public void showGoToSettingsDialog(Runnable onOpenSettings, Runnable onCancel) {
        new AlertDialog.Builder(activity)
                .setTitle("Enable permissions in Settings")
                .setMessage("Permissions were denied. Please enable them in Settings to continue.")
                .setPositiveButton("Open Settings", (d, w) -> onOpenSettings.run())
                .setNegativeButton("Cancel", (d, w) -> onCancel.run())
                .show();
    }

    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}
