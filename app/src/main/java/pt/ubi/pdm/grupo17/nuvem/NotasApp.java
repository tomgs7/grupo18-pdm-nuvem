package pt.ubi.pdm.grupo17.nuvem;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

/**
 * Configura a cache local do Firestore antes de qualquer acesso à base de dados.
 * No Android a cache persistente já vem ativa por omissão; aqui fica explícita e com 100 MB.
 */
public class NotasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024)
                        .build())
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }
}
