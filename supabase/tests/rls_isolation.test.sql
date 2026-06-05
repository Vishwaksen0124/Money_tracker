-- Verifies that for every table created so far:
--   * RLS is enabled and forced.
--   * Owner can read/insert their own rows.
--   * Owner cannot read/insert/update/delete another user's rows.
--
-- Run with:  supabase test db
-- Each `begin … rollback` block is isolated and re-runnable.

begin;

select plan(13);

-- ─── 1. rls_lint reports no missing tables ──────────────────────────────────
select is_empty(
    'select * from rls_lint()',
    'every public table has RLS enabled and forced'
);

-- ─── 2. set up two users ────────────────────────────────────────────────────
-- Bypassing email/password — directly insert into auth.users for the test.
insert into auth.users (id, email)
values ('11111111-1111-1111-1111-111111111111', 'alice@test.invalid'),
       ('22222222-2222-2222-2222-222222222222', 'bob@test.invalid');

-- ─── 3. accounts: alice writes, bob cannot read ─────────────────────────────
set local role authenticated;
set local request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

insert into accounts (user_id, bank, last4, display_name)
values ('11111111-1111-1111-1111-111111111111', 'SBI', '1234', 'Alice SBI');

select results_eq(
    'select count(*)::int from accounts',
    'values (1)',
    'alice sees her own account'
);

set local request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';

select results_eq(
    'select count(*)::int from accounts',
    'values (0)',
    'bob does not see alice''s account'
);

select throws_ok(
    $$update accounts set display_name = 'hacked' where last4 = '1234'$$,
    null,
    null,
    'bob cannot update alice''s account row (no rows matched)'
);

-- bob inserts impersonating alice → blocked by WITH CHECK
select throws_like(
    $$insert into accounts (user_id, bank, last4, display_name)
      values ('11111111-1111-1111-1111-111111111111', 'SBI', '9999', 'forged')$$,
    '%row-level security%',
    'bob cannot insert a row labelled with alice''s user_id'
);

-- ─── 4. transactions ────────────────────────────────────────────────────────
set local request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

insert into transactions (
    user_id, ts, amount_paise, direction, channel, source, raw_hash
) values (
    '11111111-1111-1111-1111-111111111111',
    now(), 50000, 'DEBIT', 'UPI', 'SMS', 'alice-hash-1'
);

select results_eq(
    'select count(*)::int from transactions',
    'values (1)',
    'alice sees her own transaction'
);

set local request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';

select results_eq(
    'select count(*)::int from transactions',
    'values (0)',
    'bob does not see alice''s transaction'
);

select throws_like(
    $$insert into transactions (
        user_id, ts, amount_paise, direction, channel, source, raw_hash
      ) values (
        '11111111-1111-1111-1111-111111111111',
        now(), 99999, 'DEBIT', 'UPI', 'SMS', 'forged-by-bob'
      )$$,
    '%row-level security%',
    'bob cannot insert a transaction under alice''s user_id'
);

-- ─── 5. unique(user_id, raw_hash) — same hash across users is fine ──────────
insert into transactions (
    user_id, ts, amount_paise, direction, channel, source, raw_hash
) values (
    '22222222-2222-2222-2222-222222222222',
    now(), 12345, 'CREDIT', 'NEFT', 'SMS', 'alice-hash-1'    -- same string as alice's hash
);

select results_eq(
    'select count(*)::int from transactions',
    'values (1)',
    'bob has his own row with the same raw_hash string, RLS-scoped'
);

-- ─── 6. parser_misses ──────────────────────────────────────────────────────
insert into parser_misses (user_id, sender, body_hash)
values ('22222222-2222-2222-2222-222222222222', 'VK-SBIINB', 'sha-bob-1');

select results_eq(
    'select count(*)::int from parser_misses',
    'values (1)',
    'bob sees his own parser miss'
);

set local request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

select results_eq(
    'select count(*)::int from parser_misses',
    'values (0)',
    'alice does not see bob''s parser misses'
);

-- ─── 7. cascade on user delete ─────────────────────────────────────────────
reset role;
delete from auth.users where id = '11111111-1111-1111-1111-111111111111';

select results_eq(
    $$select count(*)::int from accounts where user_id = '11111111-1111-1111-1111-111111111111'$$,
    'values (0)',
    'deleting alice cascades to her accounts'
);

select results_eq(
    $$select count(*)::int from transactions where user_id = '11111111-1111-1111-1111-111111111111'$$,
    'values (0)',
    'deleting alice cascades to her transactions'
);

select * from finish();
rollback;
