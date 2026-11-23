// Requer Firebase BoM 34+ (as extensões Kotlin passaram para os módulos principais; já não há -ktx).
// Equivalente em Kotlin de NotesRepository.java (excertos principais).
// Dependência extra para usar await():
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
package pt.ubi.pdm.grupo17.nuvem

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ConflictException(val remote: Note?) : RuntimeException("Conflito de versões")

object NotesRepositoryKt {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notes = db.collection("notes")
    private val uid get() = auth.currentUser!!.uid
    private val myEmail get() = auth.currentUser?.email ?: "?"

    // ---------------------------------------------------------------- Autenticação
    suspend fun login(email: String, password: String) {
        val user = auth.signInWithEmailAndPassword(email, password).await().user!!
        db.collection("users").document(user.uid)
            .set(mapOf("email" to user.email!!.trim().lowercase())).await()
    }

    // ---------------------------------------------------------------- Tempo real
    // Java usa addSnapshotListener + ListenerRegistration; em Kotlin é natural expor um Flow.
    fun myNotes(): Flow<QuerySnapshot> = callbackFlow {
        val reg: ListenerRegistration = notes.whereArrayContains("members", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) close(e) else if (snap != null) trySend(snap)
            }
        awaitClose { reg.remove() } // remove o listener quando o Flow deixa de ser recolhido
    }

    // ---------------------------------------------------------------- Last-write-wins
    fun saveLww(id: String, title: String, text: String) =
        notes.document(id).update(
            mapOf(
                "title" to title,
                "text" to text,
                "updatedBy" to myEmail,
                "updatedAt" to FieldValue.serverTimestamp(),
                "version" to FieldValue.increment(1),
            )
        ) // sem await(): offline fica em fila e a interface não bloqueia

    // ---------------------------------------------------------------- Transação com versão
    suspend fun saveChecked(id: String, expected: Long, title: String, text: String) {
        val ref = notes.document(id)
        db.runTransaction { tr ->
            val cur: DocumentSnapshot = tr.get(ref)
            if (cur.getLong("version") != expected) throw ConflictException(cur.toObject(Note::class.java))
            tr.update(
                ref, mapOf(
                    "title" to title, "text" to text, "updatedBy" to myEmail,
                    "updatedAt" to FieldValue.serverTimestamp(), "version" to expected + 1,
                )
            )
            null
        }.await()
    }

    // ---------------------------------------------------------------- Partilha
    suspend fun share(id: String, email: String) {
        val e = email.trim().lowercase()
        val q = db.collection("users").whereEqualTo("email", e).limit(1).get().await()
        val other = q.documents.firstOrNull()?.id
            ?: throw IllegalArgumentException("Não existe nenhum utilizador com o email $e")
        notes.document(id).set(
            mapOf("members" to FieldValue.arrayUnion(other), "memberNames" to mapOf(other to e)),
            SetOptions.merge()
        ).await()
    }

    // ---------------------------------------------------------------- Ficheiros
    suspend fun uploadPhoto(cr: ContentResolver, id: String, uri: Uri) {
        val path = "notes/$id/foto_${System.currentTimeMillis()}"
        val meta = storageMetadata { contentType = cr.getType(uri) ?: "image/jpeg" }
        FirebaseStorage.getInstance().getReference(path).putFile(uri, meta).await()
        notes.document(id).update(
            mapOf(
                "imagePath" to path, "updatedBy" to myEmail,
                "updatedAt" to FieldValue.serverTimestamp(), "version" to FieldValue.increment(1),
            )
        ).await()
    }

    // ---------------------------------------------------------------- Rede (demonstração)
    suspend fun setOnline(on: Boolean) {
        if (on) db.enableNetwork().await() else db.disableNetwork().await()
    }
}
