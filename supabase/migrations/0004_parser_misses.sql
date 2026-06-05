-- 0004_parser_misses: diagnostic-only log for SMS the parser couldn't handle.
-- Stores sender + content hash, never the body. Lets the user surface
-- "you have 3 unparseable messages from VK-SBIINB" without exposing text.

create table parser_misses (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references auth.users(id) on delete cascade,
    sender          text not null,
    body_hash       text not null,
    received_at     timestamptz not null default now(),
    unique (user_id, body_hash)
);

create index parser_misses_user_idx on parser_misses (user_id, received_at desc);

alter table parser_misses enable row level security;
alter table parser_misses force  row level security;

create policy parser_misses_owner on parser_misses
    for all
    using      (auth.uid() = user_id)
    with check (auth.uid() = user_id);

select assert_rls_enabled('parser_misses');
