package pt.ubi.pdm.grupo17.nuvem;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SnapshotMetadata;

import java.util.ArrayList;
import java.util.List;

/** Ecrã 2: as minhas notas, atualizadas em tempo real. */
public class NotesActivity extends AppCompatActivity {

    private final NotesRepository repo = NotesRepository.get();
    private NotesAdapter adapter;
    private TextView status;
    private View empty;
    private RecyclerView list;
    private boolean animated;
    private Menu menu;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        setSupportActionBar((MaterialToolbar) findViewById(R.id.toolbar));
        setTitle(getString(R.string.title_notes));
        if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(repo.myEmail());

        status = findViewById(R.id.status);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotesAdapter(this::openNote);
        list.setAdapter(adapter);

        ExtendedFloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> askNewNote());
        // encolhe ao descer na lista e expande ao subir ou no topo
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) fab.shrink();
                else if (dy < 0 || !rv.canScrollVertically(-1)) fab.extend();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registration = repo.listenMyNotes(this::onNotes); // começa a ouvir
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) registration.remove(); // erro frequente: esquecer isto
        registration = null;
    }

    private void onNotes(@Nullable QuerySnapshot snap, @Nullable FirebaseFirestoreException e) {
        if (e != null) {
            setStatus("Erro: " + e.getMessage(), OFFLINE, R.drawable.ic_cloud_off);
            return;
        }
        if (snap == null) return;
        List<Note> notes = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            Note n = d.toObject(Note.class);
            if (n == null) continue;
            n.pending = d.getMetadata().hasPendingWrites();
            notes.add(n);
        }
        // mais recentes primeiro; as que ainda não têm hora do servidor ficam no topo
        notes.sort((a, b) -> {
            if (a.updatedAt == null) return b.updatedAt == null ? 0 : -1;
            if (b.updatedAt == null) return 1;
            return b.updatedAt.compareTo(a.updatedAt);
        });
        adapter.submit(notes);
        empty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        if (!animated && !notes.isEmpty()) { animated = true; list.scheduleLayoutAnimation(); } // fade-in só à primeira
        showStatus(snap.getMetadata());
    }

    private void showStatus(SnapshotMetadata m) {
        if (!repo.isOnline()) {
            setStatus(getString(R.string.status_offline_sim), OFFLINE, R.drawable.ic_cloud_off);
        } else if (m.hasPendingWrites()) {
            setStatus(getString(R.string.status_syncing), SYNC, R.drawable.ic_sync);
        } else if (m.isFromCache()) {
            setStatus(getString(R.string.status_cache), CACHE, R.drawable.ic_cloud);
        } else {
            setStatus(getString(R.string.status_online), ONLINE, R.drawable.ic_cloud_done);
        }
    }

    // cores (fundo, texto) de cada estado da barra de ligação
    private static final int ONLINE = 0, SYNC = 1, CACHE = 2, OFFLINE = 3;
    private static final int[][] STATUS_COLORS = {
            {R.color.g17_st_ok_bg, R.color.g17_st_ok_fg},
            {R.color.g17_st_sync_bg, R.color.g17_st_sync_fg},
            {R.color.g17_st_cache_bg, R.color.g17_st_cache_fg},
            {R.color.g17_st_off_bg, R.color.g17_st_off_fg}};

    private void setStatus(CharSequence text, int kind, @DrawableRes int icon) {
        int fg = getColor(STATUS_COLORS[kind][1]);
        status.setText(text);
        status.setBackgroundTintList(ColorStateList.valueOf(getColor(STATUS_COLORS[kind][0])));
        status.setTextColor(fg);
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        status.setCompoundDrawableTintList(ColorStateList.valueOf(fg));
    }

    private void askNewNote() {
        Dialogs.input(this, R.string.new_note, R.drawable.ic_note_empty, R.string.hint_note_title,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0, R.string.create,
                title -> openNoteId(repo.createNote(title)));
    }

    private void openNote(Note note) { openNoteId(note.id); }

    private void openNoteId(String id) {
        startActivity(new Intent(this, NoteActivity.class).putExtra(NoteActivity.EXTRA_ID, id));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notes, menu);
        this.menu = menu;
        applyOffline();
        return true;
    }

    /** Sincroniza o item do menu (checkbox) e o ícone da toolbar com o estado atual. */
    private void applyOffline() {
        if (menu == null) return;
        boolean offline = !repo.isOnline();
        menu.findItem(R.id.action_offline).setChecked(offline);
        MenuItem icon = menu.findItem(R.id.action_offline_icon);
        icon.setIcon(offline ? R.drawable.ic_cloud_off : R.drawable.ic_cloud);
        String d = getString(offline ? R.string.offline_back : R.string.offline_go);
        icon.setTitle(d);
        MenuItemCompat.setContentDescription(icon, d);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_offline || item.getItemId() == R.id.action_offline_icon) {
            boolean goOffline = repo.isOnline(); // o mesmo código para o item do menu e para o ícone
            repo.setOnline(!goOffline);
            applyOffline();
            if (goOffline) setStatus(getString(R.string.status_offline_sim), OFFLINE, R.drawable.ic_cloud_off);
            else setStatus(getString(R.string.status_syncing), SYNC, R.drawable.ic_sync);
            return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            repo.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
