package pt.ubi.pdm.grupo17.nuvem;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

/** Ecrã 1: registo e login com email e palavra-passe (Firebase Authentication). */
public class LoginActivity extends AppCompatActivity {

    private final NotesRepository repo = NotesRepository.get();
    private EditText email;
    private EditText password;
    private Button btnLogin;
    private Button btnRegister;
    private ProgressBar progress;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (repo.isLoggedIn()) { // a sessão fica guardada no dispositivo
            openNotes();
            return;
        }
        setContentView(R.layout.activity_login);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);

        btnLogin.setOnClickListener(v -> submit(false));
        btnRegister.setOnClickListener(v -> submit(true));
    }

    private void submit(boolean register) {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        String e = email.getText().toString().trim();
        String p = password.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            emailLayout.setError(getString(R.string.err_email));
            return;
        }
        if (p.length() < 6) {
            passwordLayout.setError(getString(R.string.err_password));
            return;
        }
        setBusy(true);
        if (register) {
            repo.register(e, p)
                    .addOnSuccessListener(this, r -> openNotes())
                    .addOnFailureListener(this, this::showError);
        } else {
            repo.login(e, p)
                    .addOnSuccessListener(this, r -> openNotes())
                    .addOnFailureListener(this, this::showError);
        }
    }

    private void showError(Exception ex) {
        setBusy(false);
        Snackbar.make(findViewById(android.R.id.content), "Erro: " + ex.getMessage(), Snackbar.LENGTH_LONG).show();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
    }

    private void openNotes() {
        startActivity(new Intent(this, NotesActivity.class));
        finish();
    }
}
