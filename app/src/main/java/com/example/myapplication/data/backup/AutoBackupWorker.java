package com.example.myapplication.data.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success();
        }

        boolean success = BackupManager.backupSync(getApplicationContext());
        return success ? Result.success() : Result.retry();
    }
}
