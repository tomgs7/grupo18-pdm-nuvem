package pt.ubi.pdm.grupo17.nuvem;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Único ponto de acesso ao Firebase (padrão Repository).
 * As Activities nunca falam diretamente com Auth, Firestore ou Storage.
 */
public class NotesRepository {

    public static final String NOTES = "notes";
    public static final String USERS = "users";
    private static final String INLINE_PREFIX = "firestore:";

    private static NotesRepository instance;

    public static synchronized NotesRepository get() {
        if (instance == null) instance = new NotesRepository();
        return instance;
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean online = true;

    private NotesRepository() { }

    private CollectionReference notes() { return db.collection(NOTES); }

    // ------------------------------------------------------------------ Autenticação

    public boolean isLoggedIn() { return auth.getCurrentUser() != null; }

    public String uid() { return auth.getCurrentUser().getUid(); }

    public String myEmail() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null || u.getEmail() == null ? "?" : u.getEmail();
    }

    public Task<Void> register(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password)
                .onSuccessTask(r -> saveUserDoc(r.getUser()));
    }

    public Task<Void> login(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password)
                .onSuccessTask(r -> saveUserDoc(r.getUser()));
    }

    /** users/{uid} = { email } permite partilhar notas pelo email. */
    private Task<Void> saveUserDoc(FirebaseUser user) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("email", normalize(user.getEmail()));
        return db.collection(USERS).document(user.getUid()).set(doc);
    }

    public void logout() { auth.signOut(); }

    // ------------------------------------------------------------------ Tempo real

    /** Todas as notas em que sou membro. Vem primeiro da cache e depois do servidor. */
    public ListenerRegistration listenMyNotes(EventListener<QuerySnapshot> listener) {
        return notes().whereArrayContains("members", uid())
                .addSnapshotListener(MetadataChanges.INCLUDE, listener);
    }

    public ListenerRegistration listenNote(String noteId, EventListener<DocumentSnapshot> listener) {
        return notes().document(noteId)
                .addSnapshotListener(MetadataChanges.INCLUDE, listener);
    }

    // ------------------------------------------------------------------ Escrita

    /** Cria a nota e devolve o id logo. Offline a escrita fica em fila e sincroniza depois. */
    public String createNote(String title) {
        DocumentReference ref = notes().document();
        Map<String, Object> n = new HashMap<>();
        n.put("title", title);
        n.put("text", "");
        n.put("ownerId", uid());
        n.put("members", Collections.singletonList(uid()));
        n.put("memberNames", Collections.singletonMap(uid(), myEmail()));
        n.put("version", 1);
        n.put("updatedBy", myEmail());
        n.put("updatedAt", FieldValue.serverTimestamp());
        ref.set(n); // não esperamos pelo Task: só termina quando o servidor confirmar
        return ref.getId();
    }

    /** Estratégia 1: last-write-wins. Funciona offline; ganha a última escrita a chegar ao servidor. */
    public Task<Void> saveLww(String noteId, String title, String text) {
        Map<String, Object> u = new HashMap<>();
        u.put("title", title);
        u.put("text", text);
        u.put("updatedBy", myEmail());
        u.put("updatedAt", FieldValue.serverTimestamp());
        u.put("version", FieldValue.increment(1));
        return notes().document(noteId).update(u);
    }

    /**
     * Estratégia 2: transação com número de versão (controlo de concorrência otimista).
     * Só grava se ninguém alterou a nota desde que a carreguei. Precisa de rede.
     */
    public Task<Void> saveChecked(String noteId, long expectedVersion, String title, String text) {
        DocumentReference ref = notes().document(noteId);
        return db.runTransaction(tr -> {
            DocumentSnapshot cur = tr.get(ref);
            Long v = cur.getLong("version");
            if (v == null || v != expectedVersion) {
                throw new ConflictException(cur.toObject(Note.class));
            }
            Map<String, Object> u = new HashMap<>();
            u.put("title", title);
            u.put("text", text);
            u.put("updatedBy", myEmail());
            u.put("updatedAt", FieldValue.serverTimestamp());
            u.put("version", expectedVersion + 1);
            tr.update(ref, u);
            return null;
        });
    }

    /** Conflito detetado na transação; transporta a versão que está no servidor. */
    public static class ConflictException extends RuntimeException {
        public final Note remote;

        ConflictException(Note remote) {
            super("Conflito de versões");
            this.remote = remote;
        }
    }

    @Nullable
    public static ConflictException findConflict(@Nullable Throwable e) {
        while (e != null) {
            if (e instanceof ConflictException) return (ConflictException) e;
            e = e.getCause();
        }
        return null;
    }

    // ------------------------------------------------------------------ Partilha

    public Task<Void> share(String noteId, String email) {
        String e = normalize(email);
        return db.collection(USERS).whereEqualTo("email", e).limit(1).get()
                .onSuccessTask(q -> {
                    if (q.isEmpty()) {
                        throw new IllegalArgumentException("Não existe nenhum utilizador com o email " + e);
                    }
                    String other = q.getDocuments().get(0).getId();
                    return notes().document(noteId).update(
                            "members", FieldValue.arrayUnion(other),
                            "memberNames." + other, e);
                });
    }

    /** Só o dono o pode fazer (garantido pelas Security Rules, não pela app). */
    public Task<Void> removeMember(String noteId, String memberUid) {
        return notes().document(noteId).update(
                "members", FieldValue.arrayRemove(memberUid),
                "memberNames." + memberUid, FieldValue.delete());
    }

    // ------------------------------------------------------------------ Ficheiros

    public Task<Void> uploadPhoto(ContentResolver cr, String noteId, Uri uri) {
        if (Config.USE_CLOUD_STORAGE) {
            String path = "notes/" + noteId + "/foto_" + System.currentTimeMillis();
            String type = cr.getType(uri);
            StorageMetadata meta = new StorageMetadata.Builder()
                    .setContentType(type != null ? type : "image/jpeg")
                    .build();
            return FirebaseStorage.getInstance().getReference(path)
                    .putFile(uri, meta)
                    .onSuccessTask(snap -> notes().document(noteId).update(photoUpdate(path)));
        }
        // Modo sem Storage: miniatura comprimida num documento à parte (máx. 1 MiB por documento)
        return Tasks.call(io, () -> compress(cr, uri))
                .onSuccessTask(bytes -> {
                    Map<String, Object> file = new HashMap<>();
                    file.put("data", Blob.fromBytes(bytes));
                    file.put("contentType", "image/jpeg");
                    DocumentReference note = notes().document(noteId);
                    WriteBatch batch = db.batch();
                    batch.set(note.collection("files").document("foto"), file);
                    batch.update(note, photoUpdate(INLINE_PREFIX + System.currentTimeMillis()));
                    return batch.commit();
                });
    }

    public Task<byte[]> downloadPhoto(String noteId, String imagePath) {
        if (imagePath.startsWith(INLINE_PREFIX)) {
            return notes().document(noteId).collection("files").document("foto").get()
                    .continueWith(t -> {
                        Blob b = t.getResult().getBlob("data");
                        return b == null ? null : b.toBytes();
                    });
        }
        return FirebaseStorage.getInstance().getReference(imagePath).getBytes(Config.MAX_PHOTO_BYTES);
    }

    private Map<String, Object> photoUpdate(String path) {
        Map<String, Object> u = new HashMap<>();
        u.put("imagePath", path);
        u.put("updatedBy", myEmail());
        u.put("updatedAt", FieldValue.serverTimestamp());
        u.put("version", FieldValue.increment(1));
        return u;
    }

    private static byte[] compress(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= 800) {
            opts.inSampleSize *= 2;
        }
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, opts); }
        if (bmp == null) throw new IOException("Não foi possível ler a imagem");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ Rede (para a demonstração)

    public boolean isOnline() { return online; }

    /** Simula falta de rede sem modo avião: o Firestore passa a usar só a cache. */
    public Task<Void> setOnline(boolean value) {
        online = value;
        return value ? db.enableNetwork() : db.disableNetwork();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
