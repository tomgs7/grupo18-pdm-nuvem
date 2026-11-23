# Grupo 17 – PDM 2026/27 – Tema 7: Serviços na nuvem e sincronização de dados

App de demonstração **Notas Partilhadas** (Java) + excertos equivalentes em Kotlin.
Utilizadores autenticados criam notas, partilham-nas por email e editam-nas **em tempo real**,
também **sem rede**. Liga-se aos laboratórios de escrita colaborativa.

Plataforma: **Firebase** (Authentication, Cloud Firestore, Cloud Storage, Security Rules).
A pasta `supabase-comparacao/` mostra o equivalente em Supabase (PostgreSQL + Row Level Security).

## O que demonstra
| Na app | Mecanismo |
|---|---|
| Criar conta / Entrar | Firebase Authentication (email e palavra-passe) + documento `users/{uid}` |
| Lista "As minhas notas" | Query `whereArrayContains("members", uid)` + `addSnapshotListener` (tempo real) |
| Linha de estado (online / a sincronizar / cache) | `SnapshotMetadata.hasPendingWrites()` e `isFromCache()` |
| Menu "Simular offline" | `disableNetwork()` / `enableNetwork()`: cache persistente e fila de escritas |
| Botão **Guardar** | `update()` com `serverTimestamp()` e `increment()`: *last-write-wins*, funciona offline |
| Botão **Guardar com verificação** | `runTransaction()` com número de versão: deteção de conflitos (precisa de rede) |
| Partilhar / Remover membro | `arrayUnion` / `arrayRemove` em `members`, protegido por Security Rules |
| Fotografia | Photo Picker + `putFile()` no Cloud Storage, caminho guardado no Firestore |
| Regras | `firebase/firestore.rules` e `firebase/storage.rules` (o Storage consulta o Firestore) |

## Estrutura
```
app/src/main/...           código-fonte da app (a copiar para um projeto criado no Android Studio)
app/DEPENDENCIES.txt       plugin e dependências Firebase a acrescentar ao Gradle
app/google-services.example.json   exemplo SEM valores reais
firebase/                  Security Rules do Firestore e do Storage
kotlin-equivalentes/       versões Kotlin (repositório, Application, excerto de Activity)
supabase-comparacao/       o mesmo modelo em SQL com Row Level Security
docs/                      protocolo experimental (PDF)
```

## Como executar (resumo – ver `docs/Protocolo_Experimental_Grupo17.pdf`)
1. Criar um projeto em <https://console.firebase.google.com>, registar uma app Android com o
   package **pt.ubi.pdm.grupo17.nuvem** e descarregar o `google-services.json`.
2. Na consola: ativar **Authentication > Email/Password**, criar a base de dados **Firestore**
   e, se usarem fotografias no Storage, o **Storage** (exige plano Blaze, ver abaixo).
3. Colar `firebase/firestore.rules` e `firebase/storage.rules` nos separadores *Rules* e publicar.
4. Android Studio > New Project > **Empty Views Activity**, Language **Java**, Minimum SDK **API 26**,
   Package name **pt.ubi.pdm.grupo17.nuvem**.
5. Apagar `MainActivity.java` e `activity_main.xml` gerados; copiar `app/src/main/` deste repositório
   (manifesto e `strings.xml` incluídos – substituir).
6. Seguir `app/DEPENDENCIES.txt`, pôr o `google-services.json` em `app/` e fazer *Sync*.
7. Executar em **dois** emuladores (ou dispositivos) com contas diferentes.

## Cloud Storage e o plano Blaze
Desde 03/02/2026 o Cloud Storage for Firebase só funciona em projetos no plano **Blaze**
(pago por utilização, com o mesmo uso gratuito; exige uma conta de faturação).
Sem Blaze, pôr `USE_CLOUD_STORAGE = false` em `Config.java`: a fotografia passa a ser guardada
como miniatura comprimida no próprio Firestore (`notes/{id}/files/foto`), protegida pelas mesmas regras.

## Segurança do repositório
Sem palavras-passe, tokens, chaves ou dados pessoais. O `google-services.json` está no `.gitignore`;
cada grupo usa o seu próprio projeto Firebase.
