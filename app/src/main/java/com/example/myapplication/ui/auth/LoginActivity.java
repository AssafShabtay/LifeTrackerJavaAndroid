package com.example.myapplication.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout layoutEmail, layoutPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnSignUp, btnForgotPassword, btnOffline;
    private LinearProgressIndicator progressIndicator;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        layoutEmail = findViewById(R.id.layout_email);
        layoutPassword = findViewById(R.id.layout_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSignUp = findViewById(R.id.btn_signup);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);
        btnOffline = findViewById(R.id.btn_offline);
        progressIndicator = findViewById(R.id.progress_login);

        setupTextWatchers();

        btnLogin.setOnClickListener(v -> handleLogin());
        btnSignUp.setOnClickListener(v -> handleSignUp());
        btnForgotPassword.setOnClickListener(v -> handleForgotPassword());
        btnOffline.setOnClickListener(v -> handleContinueOffline());
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear field errors as soon as the user starts typing
                layoutEmail.setError(null);
                layoutPassword.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etEmail.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);
    }

    private boolean validateInput(String email, String password, boolean requirePassword) {
        boolean valid = true;
        layoutEmail.setError(null);
        if (requirePassword) layoutPassword.setError(null);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError("Please enter a valid email address");
            valid = false;
        }

        if (requirePassword) {
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                layoutPassword.setError("Password must be at least 6 characters");
                valid = false;
            }
        }

        return valid;
    }

    private void handleLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (!validateInput(email, password, true)) return;

        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, getFriendlyErrorMessage(e), Toast.LENGTH_LONG).show();
                });
    }

    private void handleSignUp() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (!validateInput(email, password, true)) return;

        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, getFriendlyErrorMessage(e), Toast.LENGTH_LONG).show();
                });
    }

    private void handleForgotPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        // Only validate email for password reset
        if (!validateInput(email, "", false)) return;

        setLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, getFriendlyErrorMessage(e), Toast.LENGTH_LONG).show();
                });
    }

    private void handleContinueOffline() {
        finish();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // Clear task stack so pressing back wont return to login
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnSignUp.setEnabled(!loading);
        btnForgotPassword.setEnabled(!loading);
        btnOffline.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private String getFriendlyErrorMessage(Exception e) {
        if (e == null) return "An error occurred. Please try again.";
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account found with this email address.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email address already exists.";
        } else if (e.getMessage() != null && e.getMessage().contains("network")) {
            return "Network error. Please check your connection and try again.";
        }
        return e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.getMessage();
    }
}
