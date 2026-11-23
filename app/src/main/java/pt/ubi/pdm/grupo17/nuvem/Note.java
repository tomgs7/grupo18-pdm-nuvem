package pt.ubi.pdm.grupo17.nuvem;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Documento notes/{id} do Firestore. Os nomes dos campos são os nomes no Firestore. */
public class Note {

    @DocumentId public String id;
    public String title = "";
    public String text = "";
    public String ownerId;
    public List<String> members = new ArrayList<>();        // uids com acesso (usado nas regras)
    public Map<String, String> memberNames = new HashMap<>(); // uid -> email, só para mostrar
    public String updatedBy;
    public Date updatedAt;        // serverTimestamp(): fica null enquanto a escrita está pendente
    public long version;          // incrementado em cada gravação (deteção de conflitos)
    public String imagePath;      // caminho no Storage ou "firestore:<id>" no modo sem Storage

    /** Não vai para o Firestore: indica que esta versão ainda não chegou ao servidor. */
    @Exclude public boolean pending;

    public Note() { } // necessário para toObject()
}
