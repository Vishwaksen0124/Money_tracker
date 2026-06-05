-- 0002_accounts: bank accounts owned by each user.

create table accounts (
    id                          uuid primary key default gen_random_uuid(),
    user_id                     uuid not null references auth.users(id) on delete cascade,
    bank                        text not null,
    last4                       text not null,
    display_name                text not null,
    current_balance_paise       bigint,
    last_balance_seen_at        timestamptz,
    created_at                  timestamptz not null default now(),
    unique (user_id, bank, last4)
);

create index accounts_user_idx on accounts (user_id);

alter table accounts enable row level security;
alter table accounts force  row level security;

create policy accounts_owner on accounts
    for all
    using      (auth.uid() = user_id)
    with check (auth.uid() = user_id);

select assert_rls_enabled('accounts');
