package com.example.lifetracker.data.backup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.example.lifetracker.LifeTrackerApp;
import com.example.lifetracker.data.db.ActivityDatabase;
import com.example.lifetracker.tracking.location.LocationService;
import com.example.lifetracker.util.ErrorLogger;
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

public class BackupManager {

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    public interface BackupInfoCallback {
        void onInfo(boolean ok, long updatedTimeMillis, long sizeBytes, String message);
    }

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

        File copyDbFile = new File(appContext.getCacheDir(), "backup_temp_" + System.currentTimeMillis() + ".db");

        try {
            // Merge WAL changes into the main database before backup
            ActivityDatabase db = ActivityDatabase.getDatabase(appContext);
            Cursor cursor = db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(TRUNCATE);");
            try {
                if (cursor.moveToFirst()) {
                    int busy = cursor.getInt(0);
                    int log = cursor.getInt(1);
                    int checkpointed = cursor.getInt(2);
                    if (busy != 0 || log == -1 || checkpointed == -1) {
                        throw new Exception();
                    }
                } else {
                    throw new Exception();
                }

            } catch (Exception e) {
                ErrorLogger.logError(appContext, TAG, "WAL checkpoint failed", e);
                return false;
            } finally {
                cursor.close();
            }

            // Copy database file
            copyFile(dbFile, copyDbFile);

            // Upload to Firebase Storage
            StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                    .child("users").child(uid).child("backup.db");
            Tasks.await(storageRef.putFile(Uri.fromFile(copyDbFile)));

            // Record last backup time
            long now = System.currentTimeMillis();
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply();

            return true;

        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Backup failed: " + e.getMessage(), e);
            return false;
        } finally {
            if (copyDbFile.exists()) {
                copyDbFile.delete();
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

        File RestoredDbFile = new File(appContext.getCacheDir(), "restore_temp_" + System.currentTimeMillis() + ".db");

        storageRef.getFile(RestoredDbFile)
                .addOnSuccessListener(taskSnapshot -> {
                    app.getBackgroundWriteExecutor().execute(() -> {
                        processRestore(appContext, RestoredDbFile, callback);
                    });
                })
                .addOnFailureListener(e -> {
                    if (RestoredDbFile.exists()) {
                        RestoredDbFile.delete();
                    }
                    ErrorLogger.logError(appContext, TAG, "Download backup failed", e);
                    if (e instanceof StorageException && ((StorageException) e).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        callback.onDone(false, "No backup found for this account");
                    } else {
                        callback.onDone(false, "Failed to download backup: " + e.getMessage());
                    }
                });
    }

    private static void processRestore(Context appContext, File RestoredDbFile, Callback callback) {
        SQLiteDatabase db = null;
        int backupVersion = -1;
        try {
            db = SQLiteDatabase.openDatabase(RestoredDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            backupVersion = db.getVersion();
        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Failed to open downloaded database for version check", e);
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }

        if (backupVersion != CURRENT_SCHEMA_VERSION) {
            if (RestoredDbFile.exists()) {
                RestoredDbFile.delete();
            }
            callback.onDone(false, "Backup is incompatible with the current database version.");
            return;
        }

        try {
            // stop location service, so it doesn't write while you change the database
            appContext.stopService(new Intent(appContext, LocationService.class));

            // close room database
            ActivityDatabase.closeInstance();

            // Delete database, WAL, and SHM files
            File dbFile = appContext.getDatabasePath("activity_database");
            File walFile = new File(dbFile.getPath() + "-wal");
            File shmFile = new File(dbFile.getPath() + "-shm");

            if (dbFile.exists())
                dbFile.delete();
            if (walFile.exists())
                walFile.delete();
            if (shmFile.exists())
                shmFile.delete();

            // copy restored file to the database file
            copyFile(RestoredDbFile, dbFile);

            callback.onDone(true, "Restore successful");
        } catch (Exception e) {
            ErrorLogger.logError(appContext, TAG, "Restore replacement failed", e);
            callback.onDone(false, "Restore failed: " + e.getMessage());
        } finally {
            if (RestoredDbFile.exists()) {
                RestoredDbFile.delete();
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
        /// copy file(1st parameter) to destination(2nd parameter)
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
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
