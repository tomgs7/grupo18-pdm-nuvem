// Requer Firebase BoM 34+ (as extensões Kotlin passaram para os módulos principais; já não há -ktx).
// Excerto em Kotlin equivalente a partes de NoteActivity.java:
// ouvir a nota com repeatOnLifecycle (o listener é removido automaticamente em onStop)
// e tratar o conflito devolvido pela transação.
package pt.ubi.pdm.grupo17.nuvem

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.launch

class NoteActivityExcerto : AppCompatActivity() {

    private lateinit var noteId: String
    private var baseVersion = -1L

    private fun listen() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                FirebaseFirestore.getInstance().collection("notes").document(noteId)
                    .snapshots(MetadataChanges.INCLUDE)          // Flow<DocumentSnapshot>
                    .collect { d ->
                        val n = d.toObject(Note::class.java) ?: return@collect
                        val origem = when {
                            d.metadata.hasPendingWrites() -> "local, por enviar"
                            d.metadata.isFromCache -> "cache"
                            else -> "servidor"
                        }
                        title = "v${n.version} · ${n.updatedBy} · $origem"
                    }
            }
        }
    }

    private fun saveChecked(title: String, text: String) {
        lifecycleScope.launch {
            try {
                NotesRepositoryKt.saveChecked(noteId, baseVersion, title, text)
            } catch (e: Exception) {
                val conflict = generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<ConflictException>().firstOrNull()
                when {
                    conflict != null -> AlertDialog.Builder(this@NoteActivityExcerto)
                        .setTitle("Conflito")
                        .setMessage("Alterada por ${conflict.remote?.updatedBy}")
                        .setPositiveButton("Manter a minha") { _, _ ->
                            NotesRepositoryKt.saveLww(noteId, title, text)
                        }
                        .setNegativeButton("Usar a do servidor", null)
                        .show()
                    e is FirebaseFirestoreException &&
                        e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> { /* sem rede */ }
                    else -> throw e
                }
            }
        }
    }
}
