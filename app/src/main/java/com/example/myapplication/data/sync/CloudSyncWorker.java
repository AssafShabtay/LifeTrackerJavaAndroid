package com.example.myapplication.data.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.myapplication.data.db.ActivityDatabase;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class CloudSyncWorker extends Worker {
    // Immediate and periodic workers share the database, so only one may sync at a time.
    private static final Object SYNC_LOCK = new Object();
    private String uid;
    private SyncStore store;
    private FirebaseFirestore firestore;
    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    @NonNull @Override public Result doWork() {
        synchronized (SYNC_LOCK) {
            uid = getInputData().getString(CloudSyncManager.UID);
            if (!allowed()) return Result.success();
            try {
                store = new SyncStore(ActivityDatabase.getDatabase(getApplicationContext()));
                if (!uid.equals(store.state("owner"))) {
                    CloudSyncManager.status(getApplicationContext(), "Sign in with the account linked to this device's history.");
                    return Result.failure();
                }
                firestore = FirebaseFirestore.getInstance();
                CloudSyncManager.status(getApplicationContext(), "Syncing saved history…");
                for (String table : SyncSchema.TABLES) upload(table);
                boolean complete = true;
                for (String table : SyncSchema.TABLES) complete &= download(table);
                checkAllowed();
                if (!complete) return Result.retry();
                CloudSyncManager.preferences(getApplicationContext()).edit()
                        .putLong("cloud_last_success", System.currentTimeMillis()).apply();
                CloudSyncManager.status(getApplicationContext(), "Saved history is synced. Ongoing activities sync when they finish.");
                return Result.success();
            } catch (Exception error) {
                if (!allowed()) return Result.success();
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof FirebaseFirestoreException) {
                    FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) cause).getCode();
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED || code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                        CloudSyncManager.status(getApplicationContext(), "Sync needs attention: sign in again and check Firebase access rules/App Check.");
                        return Result.failure();
                    }
                }
                CloudSyncManager.status(getApplicationContext(), "Sync could not finish. Your local data is safe; it will retry automatically.");
                return Result.retry();
            }
        }
    }
    private boolean allowed() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return !isStopped() && uid != null && CloudSyncManager.enabled(getApplicationContext()) && user != null && uid.equals(user.getUid());
    }
    private void checkAllowed() throws InterruptedException {
        if (!allowed()) throw new InterruptedException("Sync paused or account changed");
    }
    private <T> T await(Task<T> task) throws Exception { return Tasks.await(task, 45, TimeUnit.SECONDS); }
    private CollectionReference collection(String table) {
        return firestore.collection("users").document(uid).collection(table);
    }
    private void upload(String table) throws Exception {
        String after = "";
        while (true) {
            checkAllowed();
            List<SyncStore.Pending> page = store.pending(table, after, 100);
            if (page.isEmpty()) return;
            List<SyncStore.Pending> ready = new ArrayList<>();
            for (SyncStore.Pending pending : page) if (pending.document != null) ready.add(pending);
            if (!ready.isEmpty()) {
                // Reads precede writes. A cloud deletion is permanent and wins against stale offline edits.
                List<DocumentSnapshot> previous = await(firestore.runTransaction(transaction -> {
                    List<DocumentSnapshot> snapshots = new ArrayList<>();
                    for (SyncStore.Pending pending : ready) snapshots.add(transaction.get(collection(table).document(pending.id)));
                    for (int i = 0; i < ready.size(); i++) {
                        SyncStore.Pending pending = ready.get(i);
                        DocumentSnapshot remote = snapshots.get(i);
                        if (Boolean.TRUE.equals(remote.getBoolean("deleted")) || pending.document.get("mutation").equals(remote.getString("mutation"))) continue;
                        Map<String, Object> data = new HashMap<>(pending.document);
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        transaction.set(remote.getReference(), data);
                    }
                    return snapshots;
                }));
                checkAllowed();
                for (int i = 0; i < ready.size(); i++) {
                    store.acknowledge(ready.get(i));
                    if (Boolean.TRUE.equals(previous.get(i).getBoolean("deleted"))) {
                        store.apply(table, ready.get(i).id, previous.get(i).getData());
                    }
                }
            }
            after = page.get(page.size() - 1).id;
        }
    }
    private boolean download(String table) throws Exception {
        String checkpoint = store.state("cursor_" + table);
        Query query = collection(table).orderBy("updatedAt").orderBy(FieldPath.documentId()).limit(100);
        if (checkpoint != null) {
            String[] parts = checkpoint.split(":");
            query = query.whereGreaterThanOrEqualTo("updatedAt", new Timestamp(Long.parseLong(parts[0]), Integer.parseInt(parts[1])));
        }
        Timestamp newest = null;
        boolean complete = true;
        while (true) {
            checkAllowed();
            QuerySnapshot page = await(query.get(Source.SERVER));
            if (page.isEmpty()) break;
            for (DocumentSnapshot document : page.getDocuments()) {
                checkAllowed();
                if (!Boolean.TRUE.equals(document.getBoolean("deleted"))) ensureParent(table, document);
                complete &= store.apply(table, document.getId(), document.getData());
                newest = document.getTimestamp("updatedAt");
            }
            query = query.startAfter(page.getDocuments().get(page.size() - 1));
        }
        // Inclusive timestamp cursors intentionally replay ties. Applying a record is idempotent.
        if (complete && newest != null) store.state("cursor_" + table, newest.getSeconds() + ":" + newest.getNanoseconds());
        return complete;
    }
    private void ensureParent(String table, DocumentSnapshot document) throws Exception {
        String parent = SyncStore.parentTable(table);
        if (parent == null) return;
        String id = document.getString("fields." + SyncStore.parentColumn(table));
        if (id == null || store.localId(parent, id) != null) return;
        DocumentSnapshot remote = await(collection(parent).document(id).get(Source.SERVER));
        if (!remote.exists()) throw new IllegalStateException("Parent has not uploaded yet");
        checkAllowed();
        store.apply(parent, id, remote.getData());
    }
}
