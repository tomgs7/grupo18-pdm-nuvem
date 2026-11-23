# Comparação com Supabase

A demonstração usa Firebase. Esta pasta mostra o equivalente em Supabase para o slide de comparação.

| | Firebase (Firestore) | Supabase |
|---|---|---|
| Base de dados | NoSQL de documentos | PostgreSQL (relacional, SQL) |
| Controlo de acesso | Security Rules (linguagem própria) | Row Level Security (políticas SQL) |
| Tempo real | Listeners em documentos e queries | Realtime (alterações na tabela via WebSocket) |
| Offline | Cache local e fila de escritas incluídas no SDK | Não incluído: é preciso implementar (ex.: Room + sincronização) |
| Ficheiros | Cloud Storage (plano Blaze) | Supabase Storage (incluído no plano gratuito) |
| SDK Android | Java e Kotlin | `supabase-kt`, só Kotlin |

**Alternativa para Java no Supabase:** o SDK oficial é Kotlin. Em Java usa-se a API REST que o
Supabase gera automaticamente (PostgREST), por exemplo com Retrofit/OkHttp, enviando
`apikey` e `Authorization: Bearer <token do utilizador>`. As políticas RLS aplicam-se na mesma.

`politicas_rls.sql` tem o modelo de dados e as políticas equivalentes às `firestore.rules`.
