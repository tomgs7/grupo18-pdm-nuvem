package pt.ubi.pdm.grupo17.nuvem;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Ecrã 3: editar uma nota, gravar (LWW ou com verificação), foto e partilha. */
public class NoteActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "note_id";
    private static final String TAG = "G17";

    private final NotesRepository repo = NotesRepository.get();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private String noteId;
    private Note current;          // última versão recebida
    private long baseVersion = -1; // versão em que comecei a editar
    private boolean dirty;         // há texto por gravar?
    private boolean filling;       // a preencher os campos por código (ignorar no TextWatcher)
    private String shownImage;

    private EditText title;
    private EditText text;
    private TextView info;
    private TextView banner;
    private ChipGroup membersView;
    private ImageView photo;
    private ProgressBar progress;
    private Button btnShare;
    private Button btnMembers;
    private ListenerRegistration registration;

    // Seletor de ficheiros do sistema: não precisa de permissões e funciona em qualquer versão
    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                progress.setVisibility(View.VISIBLE);
                repo.uploadPhoto(getContentResolver(), noteId, uri)
                        .addOnSuccessListener(this, v -> {
                            Log.d(TAG, "foto enviada t=" + System.currentTimeMillis());
                            toast("Fotografia enviada");
                        })
                        .addOnFailureListener(this, e -> toast("Falhou o envio: " + e.getMessage()))
                        .addOnCompleteListener(this, t -> progress.setVisibility(View.GONE));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);
        setSupportActionBar((MaterialToolbar) findViewById(R.id.toolbar));
        noteId = getIntent().getStringExtra(EXTRA_ID);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        title = findViewById(R.id.noteTitle);
        text = findViewById(R.id.noteText);
        info = findViewById(R.id.info);
        banner = findViewById(R.id.banner);
        membersView = findViewById(R.id.members);
        photo = findViewById(R.id.photo);
        progress = findViewById(R.id.progress);
        btnShare = findViewById(R.id.btnShare);
        btnMembers = findViewById(R.id.btnMembers);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { if (!filling) dirty = true; }
        };
        title.addTextChangedListener(watcher);
        text.addTextChangedListener(watcher);

        findViewById(R.id.btnSave).setOnClickListener(v -> saveLww());
        findViewById(R.id.btnSaveChecked).setOnClickListener(v -> saveChecked());
        findViewById(R.id.btnPhoto).setOnClickListener(v -> pickPhoto.launch("image/*"));
        btnShare.setOnClickListener(v -> askShare());
        btnMembers.setOnClickListener(v -> askRemoveMember());
        banner.setOnClickListener(v -> fill(current));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registration = repo.listenNote(noteId, this::onNote);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove();
        registration = null;
    }

    // Ao rodar o ecrã a Activity é recriada: sem isto baseVersion volta a -1 e dirty fica true
    // (o TextWatcher dispara ao restaurar o texto), dando um aviso falso e um conflito falso.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("baseVersion", baseVersion);
        out.putBoolean("dirty", dirty);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle saved) {
        super.onRestoreInstanceState(saved); // restaura o texto e dispara o TextWatcher
        baseVersion = saved.getLong("baseVersion", -1);
        dirty = saved.getBoolean("dirty");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ------------------------------------------------------------------ Receber alterações

    private void onNote(@Nullable DocumentSnapshot d, @Nullable FirebaseFirestoreException e) {
        if (e != null) {
            if (e.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                closeWith("Deixaste de ter acesso a esta nota");
            } else {
                toast("Erro: " + e.getMessage());
            }
            return;
        }
        if (d == null) return;
        if (!d.exists()) {
            // offline, uma nota acabada de criar pode ainda não estar na cache: esperar
            if (!d.getMetadata().isFromCache()) { closeWith("A nota já não existe"); }
            return;
        }
        Note n = d.toObject(Note.class);
        if (n == null) return;
        n.pending = d.getMetadata().hasPendingWrites();
        current = n;

        String when = n.updatedAt == null ? "a sincronizar…" : fmt.format(n.updatedAt);
        String origin = n.pending ? "local, por enviar" : (d.getMetadata().isFromCache() ? "cache" : "servidor");
        Log.d(TAG, "snapshot v=" + n.version + " origem=" + origin + " t=" + System.currentTimeMillis());
        info.setText("versão " + n.version + " · " + n.updatedBy + " · " + when + " · " + origin);

        if (!dirty) {
            fill(n);                       // não estou a editar: mostrar a versão nova
        } else if (n.version != baseVersion && !n.pending) {
            banner.setText("Alterada por " + n.updatedBy + " enquanto editavas. Toca para ver a versão remota.");
            showBanner();
        }

        boolean owner = Objects.equals(n.ownerId, repo.uid());
        btnShare.setVisibility(owner ? View.VISIBLE : View.GONE);
        btnMembers.setVisibility(owner && n.members.size() > 1 ? View.VISIBLE : View.GONE);
        membersView.removeAllViews();
        for (String name : n.memberNames.values()) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setChipIconResource(R.drawable.ic_person);
            chip.setClickable(false);
            chip.setCheckable(false);
            membersView.addView(chip);
        }

        if (n.imagePath != null && !n.imagePath.equals(shownImage)) loadPhoto(n.imagePath);
    }

    /** Aviso laranja: entra com fade + deslize curtos. */
    private void showBanner() {
        if (banner.getVisibility() == View.VISIBLE) return;
        banner.setAlpha(0f);
        banner.setTranslationY(-24 * getResources().getDisplayMetrics().density);
        banner.setVisibility(View.VISIBLE);
        banner.animate().alpha(1f).translationY(0f).setDuration(250).start();
    }

    private void fill(@Nullable Note n) {
        if (n == null) return;
        filling = true;
        if (!title.getText().toString().equals(n.title)) title.setText(n.title);
        if (!text.getText().toString().equals(n.text)) {
            text.setText(n.text);
            text.setSelection(text.length());
        }
        filling = false;
        baseVersion = n.version;
        dirty = false;
        banner.setVisibility(View.GONE);
    }

    private void loadPhoto(String path) {
        shownImage = path;
        repo.downloadPhoto(noteId, path)
                .addOnSuccessListener(this, bytes -> {
                    if (bytes == null) return;
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    photo.setImageBitmap(bmp);
                    Log.d(TAG, "foto recebida bytes=" + bytes.length + " t=" + System.currentTimeMillis());
                    photo.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(this, e -> {
                    shownImage = null; // tentar outra vez no próximo snapshot
                    toast("Não foi possível obter a fotografia: " + e.getMessage());
                });
    }

    // ------------------------------------------------------------------ Gravar

    /** Last-write-wins: grava logo, mesmo offline (fica em fila). */
    private void saveLww() {
        Log.d(TAG, "guardar LWW t=" + System.currentTimeMillis());
        repo.saveLww(noteId, title.getText().toString(), text.getText().toString())
                .addOnFailureListener(this, e -> toast("Recusado: " + e.getMessage()));
        dirty = false;
        banner.setVisibility(View.GONE);
        toast(repo.isOnline() ? "Guardado" : "Guardado localmente; será enviado quando houver rede");
    }

    /** Transação com versão: deteta se alguém gravou desde que comecei a editar. */
    private void saveChecked() {
        if (!repo.isOnline()) {
            toast("A gravação com verificação usa uma transação e precisa de rede");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        repo.saveChecked(noteId, baseVersion, title.getText().toString(), text.getText().toString())
                .addOnSuccessListener(this, v -> {
                    dirty = false;
                    toast("Guardado sem conflitos");
                })
                .addOnFailureListener(this, e -> {
                    NotesRepository.ConflictException c = NotesRepository.findConflict(e);
                    if (c != null && c.remote == null) {
                        toast("A nota foi apagada no servidor");
                    } else if (c != null) {
                        Log.d(TAG, "conflito detetado: local v=" + baseVersion + " servidor v=" + c.remote.version);
                        showConflict(c.remote);
                    }
                    else toast("Erro: " + e.getMessage());
                })
                .addOnCompleteListener(this, t -> progress.setVisibility(View.GONE));
    }

    private void showConflict(Note remote) {
        String msg = "A nota foi alterada por " + remote.updatedBy + " (versão " + remote.version
                + ") depois de começares a editar.\n\nTexto no servidor:\n" + remote.text;
        Dialogs.builder(this, "Conflito", R.drawable.ic_warning)
                .setMessage(msg)
                .setPositiveButton("Manter a minha", (d, w) -> saveLww())
                .setNegativeButton("Usar a do servidor", (d, w) -> fill(remote))
                .setNeutralButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ Partilha

    private void askShare() {
        Dialogs.input(this, R.string.share, R.drawable.ic_person, R.string.hint_colleague,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                R.string.helper_account, R.string.share, email ->
                        repo.share(noteId, email)
                                .addOnSuccessListener(this, v -> toast("Partilhada"))
                                .addOnFailureListener(this, e -> toast(e.getMessage())));
    }

    private void askRemoveMember() {
        if (current == null) return;
        List<String> uids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> m : current.memberNames.entrySet()) {
            if (m.getKey().equals(current.ownerId)) continue;
            uids.add(m.getKey());
            names.add(m.getValue());
        }
        Dialogs.builder(this, "Remover membro", R.drawable.ic_person_remove)
                .setItems(names.toArray(new String[0]), (d, which) ->
                        repo.removeMember(noteId, uids.get(which))
                                .addOnSuccessListener(this, v -> toast("Removido"))
                                .addOnFailureListener(this, e -> toast(e.getMessage())))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean closing;

    /** Diálogo curto com OK e só depois fecha o ecrã (uma Snackbar desapareceria com o finish). */
    private void closeWith(String msg) {
        if (closing) return;
        closing = true;
        Dialogs.builder(this, getString(R.string.app_name), R.drawable.ic_warning)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .show();
    }

    private void toast(String s) {
        Snackbar.make(findViewById(android.R.id.content), s == null ? "Erro" : s, Snackbar.LENGTH_LONG).show();
    }
}
