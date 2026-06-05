-- 0003_transactions: the canonical event log.
-- Sensitive PII (counterparty, notes) stored as ciphertext bytea — the
-- client encrypts before insert; Postgres never sees plaintext for these.
-- Amount and timestamp are plain so the server can aggregate (§10).

create table transactions (
    id                          uuid primary key default gen_random_uuid(),
    user_id                     uuid not null references auth.users(id) on delete cascade,
    account_id                  uuid references accounts(id) on delete set null,
    ts                          timestamptz not null,
    ingested_at                 timestamptz not null default now(),
    amount_paise                bigint not null check (amount_paise >= 0),
    direction                   text   not null check (direction in ('DEBIT','CREDIT')),
    channel                     text   not null check (channel in ('UPI','CARD','NEFT','IMPS','ATM','POS','OTHER')),
    counterparty_ciphertext     bytea,
    balance_after_paise         bigint,
    source                      text   not null check (source in ('SMS','NOTIF','MANUAL')),
    raw_hash                    text   not null,
    category_id                 uuid,                                       -- FK added in v0.3
    notes_ciphertext            bytea,
    reversed_by                 uuid references transactions(id) on delete set null,
    unique (user_id, raw_hash)
);

create index transactions_user_ts_idx        on transactions (user_id, ts desc);
create index transactions_user_account_ts_idx on transactions (user_id, account_id, ts desc);
create index transactions_user_direction_ts_idx on transactions (user_id, direction, ts desc);

alter table transactions enable row level security;
alter table transactions force  row level security;

create policy transactions_owner on transactions
    for all
    using      (auth.uid() = user_id)
    with check (auth.uid() = user_id);

select assert_rls_enabled('transactions');
