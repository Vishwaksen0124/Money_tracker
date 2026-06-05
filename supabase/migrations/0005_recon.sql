-- 0005_recon: balance-gap reconciliation support.
-- account_last4 lets balance math stay per-account; RECON marks transactions
-- the client auto-created from unexplained balance gaps (no SMS, no notif).

alter table transactions add column if not exists account_last4 text;

alter table transactions drop constraint if exists transactions_source_check;
alter table transactions add constraint transactions_source_check
    check (source in ('SMS','NOTIF','MANUAL','RECON'));
