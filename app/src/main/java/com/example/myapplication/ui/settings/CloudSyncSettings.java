package com.example.myapplication.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.myapplication.R;
import com.example.myapplication.LifeTrackerApp;
import com.example.myapplication.data.db.ActivityDatabase;
import com.example.myapplication.data.sync.CloudSyncManager;
import com.example.myapplication.data.sync.SyncStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.DateFormat;
import java.util.Date;

/** View-scoped controller; Firebase never receives a password through app preferences or logs. */
final class CloudSyncSettings implements AutoCloseable {
    private final Fragment fragment;
    private final Context context;
    private final SharedPreferences prefs;
    private final SwitchMaterial toggle;
    private final TextView status;
    private final TextView account;
    private final View syncNow;
    private final View signOut;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean rendering;
    private boolean closed;
    private AlertDialog dialog;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> main.post(this::render);
    private final FirebaseAuth.AuthStateListener authListener = auth -> render();

    CloudSyncSettings(Fragment fragment, View view) {
        this.fragment = fragment;
        context = fragment.requireContext().getApplicationContext();
        prefs = CloudSyncManager.preferences(context);
        toggle = view.findViewById(R.id.switchCloudSync);
        status = view.findViewById(R.id.cloud_sync_status);
        account = view.findViewById(R.id.cloud_account);
        syncNow = view.findViewById(R.id.cloud_sync_now);
        signOut = view.findViewById(R.id.cloud_sign_out);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) return;
            if (!checked) CloudSyncManager.setEnabled(context, false);
            else if (FirebaseAuth.getInstance().getCurrentUser() == null) { render(); showSignIn(); }
            else enable();
        });
        account.setOnClickListener(v -> { if (FirebaseAuth.getInstance().getCurrentUser() == null) showSignIn(); });
        syncNow.setOnClickListener(v -> CloudSyncManager.schedule(context));
        signOut.setOnClickListener(v -> {
            dialog = new MaterialAlertDialogBuilder(fragment.requireContext()).setTitle("Sign out?")
                    .setMessage("Cloud sync will stop. History stays on this phone and remains linked to this account. Sign in with the same account to resume syncing.")
                    .setPositiveButton("Sign out", (d, which) -> {
                        CloudSyncManager.setEnabled(context, false);
                        FirebaseAuth.getInstance().signOut();
                        render();
                    }).setNegativeButton("Cancel", null).show();
        });
        prefs.registerOnSharedPreferenceChangeListener(listener);
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        render();
    }
    private void render() {
        if (closed) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        rendering = true;
        toggle.setChecked(user != null && CloudSyncManager.enabled(context));
        rendering = false;
        account.setText(user == null ? "Sign in or create an account" : "Signed in as " + user.getEmail());
        signOut.setVisibility(user == null ? View.GONE : View.VISIBLE);
        syncNow.setEnabled(user != null && CloudSyncManager.enabled(context));
        String text = prefs.getString("cloud_sync_status", "Sign in to save and restore your history across devices.");
        long success = prefs.getLong("cloud_last_success", 0);
        if (success > 0) text += "\nLast completed sync: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(success));
        status.setText(text);
    }
    private void enable() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        toggle.setEnabled(false);
        ((LifeTrackerApp) context).getBackgroundWriteExecutor().execute(() -> {
            try {
                boolean bound = new SyncStore(ActivityDatabase.getDatabase(context)).bindOwner(user.getUid());
                main.post(() -> {
                    if (closed) return;
                    toggle.setEnabled(true);
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current == null || !current.getUid().equals(user.getUid())) return;
                    if (bound) CloudSyncManager.setEnabled(context, true);
                    else {
                        CloudSyncManager.setEnabled(context, false);
                        FirebaseAuth.getInstance().signOut();
                        CloudSyncManager.status(context, "This phone's history belongs to another account. Sign in with that account to keep its data private.");
                    }
                    render();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (closed) return;
                    toggle.setEnabled(true);
                    CloudSyncManager.status(context, "Could not open local history. Sync has not been enabled.");
                    render();
                });
            }
        });
    }
    private void showSignIn() {
        if (closed || !fragment.isAdded()) return;
        Context ui = fragment.requireContext();
        LinearLayout form = new LinearLayout(ui);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * ui.getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        EditText email = new EditText(ui);
        email.setHint("Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setSingleLine(true);
        EditText password = new EditText(ui);
        password.setHint("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        CheckBox create = new CheckBox(ui);
        create.setText("Create a new account");
        TextView message = new TextView(ui);
        form.addView(email); form.addView(password); form.addView(create); form.addView(message);
        dialog = new MaterialAlertDialogBuilder(ui).setTitle("Save your history")
                .setMessage("Signing in enables cloud sync and merges this phone's saved history with your account. Use the same email on your new phone to restore it.")
                .setView(form).setPositiveButton("Continue", null).setNegativeButton("Cancel", null)
                .setNeutralButton("Reset password", null).create();
        AlertDialog login = dialog;
        login.setOnShowListener(d -> {
            login.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String address = email.getText().toString().trim();
                String secret = password.getText().toString();
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("Enter a valid email"); return; }
                if (secret.isEmpty()) { password.setError("Enter your password"); return; }
                login.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                login.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                login.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                login.setCancelable(false);
                message.setText("Signing in…");
                FirebaseAuth auth = FirebaseAuth.getInstance();
                (create.isChecked() ? auth.createUserWithEmailAndPassword(address, secret) : auth.signInWithEmailAndPassword(address, secret))
                        .addOnCompleteListener(task -> {
                            if (closed) return;
                            if (task.isSuccessful()) { login.dismiss(); enable(); }
                            else {
                                login.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                login.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                                login.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                login.setCancelable(true);
                                message.setText(task.getException() == null ? "Sign-in failed. Try again." : task.getException().getLocalizedMessage());
                            }
                        });
            });
            login.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String address = email.getText().toString().trim();
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("Enter your email first"); return; }
                FirebaseAuth.getInstance().sendPasswordResetEmail(address).addOnCompleteListener(task -> {
                    if (!closed) message.setText(task.isSuccessful() ? "If an account exists, a reset email has been sent." : "Could not request a reset. Check your connection and try again.");
                });
            });
        });
        login.show();
    }
    @Override public void close() {
        closed = true;
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        FirebaseAuth.getInstance().removeAuthStateListener(authListener);
        if (dialog != null) dialog.dismiss();
    }
}
