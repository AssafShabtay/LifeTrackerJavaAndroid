package com.example.myapplication.data.backup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.tracking.location.LocationService;
import com.example.myapplication.util.ErrorLogger;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles database backup to and restore from Firebase Storage.
 */
public class BackupManager {

    /**
     * Callback for asynchronous operations.
     */
    public interface Callback {
        void onDone(boolean ok, String message);
    }

    /**
     * Callback for backup info metadata query.
     */
    public interface BackupInfoCallback {
        void onInfo(boolean ok, long updatedTimeMillis, long sizeBytes, String message);
    }

    /**
     * Current Room schema version. Must match @Database(version = 4) in ActivityDatabase.
     */
    public static final int CURRENT_SCHEMA_VERSION = 4;

    private static final String TAG = "BackupManager";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_LAST_BACKUP = "last_backup_time";

    public static void backup(Context context, Callback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onDone(false, "User is not signed in.");
            return;
        }

        Context appContext = context.getApplicationContext();
        LifeTrackerApp app = (LifeTrackerApp) appContext;

        app.getBackgroundWriteExecutor().execute(() -> {
            boolean result = backupSync(appContext);
            if (result) {
                callback.onDone(true, "Backup completed successfully.");
            } else {
                callback.onDone(false, "Backup failed. See logs for details.");
            }
        });
    }

    public static boolean backupSync(Context appContext) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return false;
        }

        String uid = currentUser.getUid();
        File dbFile = appContext.getDatabasePath("activity_database");
        if (!dbFile.exists()) {
            ErrorLogger.logError(appContext, TAG, "Database file does not exist", new Exception("Missing activity_database"));
            return false;
        }

        File tempFile = new File(appContext.getCacheDir(), "backup_temp_" + System.currentTimeMillis() + ".db");

        try {
            // 1. Force WAL checkpoint
            ActivityDatabase db = ActivityDatabase.getDatabase(appContext);
            Cursor cursor = db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(TRUNCATE);");
            if (cursor != null) {
                cursor.moveToFirst();
                cursor.close();
            }

            // 2. Copy database file to cache temp file
            copyFile(dbFile, tempFile);

            // 3. Upload to Firebase Storage
            StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                    .child("users").child(uid).child("backup.db");

            Tasks.await(storageRef.putFile(Uri.fromFile(tempFile)));

            // 4. Record last backup time
            long now = System.currentTimeMillis();
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply();

            return true;

        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Backup failed: " + e.getMessage(), e);
            return false;
        } finally {
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    public static void restore(Context context, Callback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onDone(false, "User is not signed in.");
            return;
        }

        Context appContext = context.getApplicationContext();
        LifeTrackerApp app = (LifeTrackerApp) appContext;

        String uid = currentUser.getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("users").child(uid).child("backup.db");

        File tempFile = new File(appContext.getCacheDir(), "restore_temp_" + System.currentTimeMillis() + ".db");

        storageRef.getFile(tempFile)
                .addOnSuccessListener(taskSnapshot -> {
                    app.getBackgroundWriteExecutor().execute(() -> {
                        processRestore(appContext, tempFile, callback);
                    });
                })
                .addOnFailureListener(e -> {
                    if (tempFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        tempFile.delete();
                    }
                    ErrorLogger.logError(appContext, TAG, "Download backup failed", e);
                    if (e instanceof StorageException &&
                            ((StorageException) e).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        callback.onDone(false, "No backup found for this account");
                    } else {
                        callback.onDone(false, "Failed to download backup: " + e.getMessage());
                    }
                });
    }

    private static void processRestore(Context appContext, File tempFile, Callback callback) {
        SQLiteDatabase checkDb = null;
        int backupVersion = -1;
        try {
            checkDb = SQLiteDatabase.openDatabase(tempFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            backupVersion = checkDb.getVersion();
        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Failed to open downloaded database for version check", e);
        } finally {
            if (checkDb != null && checkDb.isOpen()) {
                checkDb.close();
            }
        }

        if (backupVersion != CURRENT_SCHEMA_VERSION) {
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
            callback.onDone(false, "Backup was made by a different app version");
            return;
        }

        try {
            // 1. Stop LocationService
            appContext.stopService(new Intent(appContext, LocationService.class));

            // 2. Close Room database instance
            ActivityDatabase.closeInstance();

            // 3. Delete existing database, WAL, and SHM files
            File dbFile = appContext.getDatabasePath("activity_database");
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");

            if (dbFile.exists()) //noinspection ResultOfMethodCallIgnored
                dbFile.delete();
            if (walFile.exists()) //noinspection ResultOfMethodCallIgnored
                walFile.delete();
            if (shmFile.exists()) //noinspection ResultOfMethodCallIgnored
                shmFile.delete();

            // 4. Copy downloaded file over the database file
            copyFile(tempFile, dbFile);

            callback.onDone(true, "Restore successful");
        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Restore replacement failed", e);
            callback.onDone(false, "Restore failed: " + e.getMessage());
        } finally {
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    public static void getBackupInfo(Context context, BackupInfoCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onInfo(false, 0, 0, "User is not signed in.");
            return;
        }

        String uid = currentUser.getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("users").child(uid).child("backup.db");

        storageRef.getMetadata()
                .addOnSuccessListener(metadata -> {
                    long updatedTime = metadata.getUpdatedTimeMillis();
                    long size = metadata.getSizeBytes();
                    callback.onInfo(true, updatedTime, size, null);
                })
                .addOnFailureListener(e -> {
                    Context appContext = context.getApplicationContext();
                    if (e instanceof StorageException &&
                            ((StorageException) e).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        callback.onInfo(false, 0, 0, "No backup found for this account");
                    } else {
                        ErrorLogger.logError(appContext, TAG, "getBackupInfo failed", e);
                        callback.onInfo(false, 0, 0, "Failed to get backup info: " + e.getMessage());
                    }
                });
    }

    public static void deleteCloudBackup(Context context, Callback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onDone(false, "User is not signed in.");
            return;
        }

        Context appContext = context.getApplicationContext();
        String uid = currentUser.getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("users").child(uid).child("backup.db");

        storageRef.delete()
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().remove(KEY_LAST_BACKUP).apply();
                    callback.onDone(true, "Cloud backup deleted successfully.");
                })
                .addOnFailureListener(e -> {
                    if (e instanceof StorageException &&
                            ((StorageException) e).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().remove(KEY_LAST_BACKUP).apply();
                        callback.onDone(true, "No backup found on cloud.");
                    } else {
                        ErrorLogger.logError(appContext, TAG, "Delete cloud backup failed", e);
                        callback.onDone(false, "Delete cloud backup failed: " + e.getMessage());
                    }
                });
    }

    private static void copyFile(File src, File dst) throws IOException {
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dst.getParentFile().mkdirs();
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
