-- Comparação: o mesmo modelo de dados em Supabase (PostgreSQL + Row Level Security).
-- Não é usado pela app; serve para o slide "Security Rules vs RLS".

create table notes (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid not null references auth.users(id) default auth.uid(),
  title       text not null default '',
  body        text not null default '',
  version     bigint not null default 1,
  updated_at  timestamptz not null default now()
);

create table note_members (
  note_id  uuid references notes(id) on delete cascade,
  user_id  uuid references auth.users(id) on delete cascade,
  primary key (note_id, user_id)
);

alter table notes        enable row level security;
alter table note_members enable row level security;

-- Função auxiliar (security definer evita recursão entre políticas)
create or replace function is_member(n uuid) returns boolean
language sql security definer stable as $$
  select exists (select 1 from note_members where note_id = n and user_id = auth.uid())
$$;

-- Ler e alterar: só membros
create policy "membros leem"    on notes for select using (is_member(id));
create policy "membros alteram" on notes for update using (is_member(id));

-- Criar: o criador tem de ser o dono
create policy "dono cria" on notes for insert with check (owner_id = auth.uid());

-- Apagar: só o dono
create policy "dono apaga" on notes for delete using (owner_id = auth.uid());

-- Membros: o dono gere; cada um vê as suas linhas
create policy "ver membros" on note_members for select
  using (user_id = auth.uid() or exists (select 1 from notes where id = note_id and owner_id = auth.uid()));
create policy "dono gere membros" on note_members for all
  using (exists (select 1 from notes where id = note_id and owner_id = auth.uid()));
