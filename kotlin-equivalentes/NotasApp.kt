// Requer Firebase BoM 34+ (as extensões Kotlin passaram para os módulos principais; já não há -ktx).
// Equivalente em Kotlin de NotasApp.java: cache local persistente de 100 MB.
package pt.ubi.pdm.grupo17.nuvem

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class NotasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(100L * 1024 * 1024) })
        }
    }
}
