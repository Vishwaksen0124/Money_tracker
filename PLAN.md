# Money Tracker — Implementation Plan

> Owner: rakesh
> Last updated: 2026-05-26
> Architecture: Android client + Supabase (Postgres + Auth + Storage). Multi-user.
> Status: v0.1 scaffold built and verified; v0.2+ pivoting to cloud.

---

## 1. Goals

- Auto-capture every UPI / bank transaction on Android (SMS + notification).
- Track day / week / month / year **spend, income, savings, and goals**.
- Budgets with multiple **threshold types**: budget %, balance floor, per-category cap, savings progress.
- Manual transactions, recurring entries, bills/subscriptions, lending log.
- Daily summary notification with closing balance and today's spend.
- **Multi-user** with strong per-user isolation. User A cannot see User B's data, ever.
- Sensitive fields (counterparty, notes) encrypted **on the phone** before upload — Supabase stores opaque ciphertext for those columns.

## 2. Non-goals (v1)

- No iOS in v1 (no SMS access).
- No active bank polling, no net-banking credentials, no app-scraping.
- No investment / portfolio / MF tracking.
- No tax filing — only categorization aids.
- No web app in v1 (planned post-v1 against the same Supabase project).

## 3. Why this approach

UPI apps cannot expose APIs (NPCI/RBI rules). The only data signals available to an individual on their phone are:
1. **Bank SMS** (mandatory under RBI transaction-alert rules).
2. **On-device notifications** from PhonePe / GPay / Paytm / SuperMoney.
3. **Manual entry.**

Everything below is built on those three. Supabase replaces "writing a backend"; the Android client talks to Supabase directly via the official SDK.

## 4. Threat model (cloud-aware)

| Threat | Mitigation |
|---|---|
| Network exfiltration via rogue dep | Dependency allowlist + manifest check restricts outbound calls to `*.supabase.co` only (§20.1). |
| Compromised Supabase tenant / employee | **Field-level E2EE**: counterparty + notes stored as `bytea` ciphertext, key derived from user password via Argon2id, never sent to Supabase. |
| RLS misconfig leaks data across users | RLS policies are version-controlled in `supabase/migrations/`; tested via `supabase test db`; CI fails if any table lacks RLS. |
| Stolen JWT | Short-lived access token (1 h), refresh token rotation, server-side revocation on logout, app-side biometric gate before any Supabase call. |
| MITM | TLS 1.3 only, certificate pinning for `*.supabase.co` via OkHttp interceptor. |
| Stolen phone, app-lock bypass | DB cache encrypted (SQLCipher), biometric gate, refresh token wiped on remote logout. |
| Lock-screen notification leak | Default redacted ("Transaction recorded"); opt-in for amount-visible. |
| Anon-key extracted from APK | Anon key is **not** a secret. Security comes from RLS + JWT; service-role key is never in the app. |
| Backup leaks data | Local: `allowBackup=false`, `dataExtractionRules` deny all. Cloud: encrypted export blob (Argon2id) for "leave Supabase" portability. |
| Forensic recovery | `PRAGMA secure_delete = ON` on the SQLCipher cache. |

## 5. Architecture

```
┌─────────────────────────────────────────────────────┐
│  Android phone                                       │
│                                                      │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────┐ │
│  │ SMS recv │   │ Notif    │   │ Manual entry UI  │ │
│  └────┬─────┘   └────┬─────┘   └────┬─────────────┘ │
│       └──────┬───────┴────────┬─────┘               │
│              ▼                                       │
│       ┌─────────────┐                                │
│       │ Parser pack │                                │
│       └──────┬──────┘                                │
│              ▼                                       │
│       ┌─────────────────┐  E2EE on counterparty,    │
│       │ Normalize +     │  notes — key from         │
│       │ encrypt fields  │  Argon2id(user password)  │
│       └──────┬──────────┘                            │
│              ▼                                       │
│       ┌──────────────────┐    ┌──────────────────┐  │
│       │ Local SQLite     │◄──►│ Sync queue       │  │
│       │ cache (SQLCipher)│    │ (offline-first)  │  │
│       └──────┬───────────┘    └────────┬─────────┘  │
│              ▼                          │            │
│       ┌──────────────────┐              │            │
│       │ Compose UI       │              │            │
│       └──────────────────┘              │            │
└─────────────────────────────────────────┼────────────┘
                                          │ TLS 1.3
                                          ▼
                              ┌──────────────────────────┐
                              │  Supabase project        │
                              │                          │
                              │  Auth (email / OAuth)    │
                              │  Postgres + RLS          │
                              │  Storage (encrypted blob │
                              │   exports / attachments) │
                              └──────────────────────────┘
```

The Android client is **offline-first**: every capture writes the local cache immediately; the sync queue uploads when a network is available. The cache exists to keep the UI snappy and to survive Supabase outages.

## 6. Data model

All tables carry `user_id uuid not null references auth.users(id)` and have RLS `using (auth.uid() = user_id)`. Indexes drop `user_id` from the leading position only where RLS already filters by it.

Sensitive fields are stored as `bytea` (ciphertext) and named `*_ciphertext`. The client decrypts after fetching.

```sql
accounts            (id, user_id, bank, last4, display_name, current_balance_paise, last_balance_seen_at)

transactions        (id, user_id, account_id, ts, ingested_at,
                     amount_paise, direction, channel,
                     counterparty_ciphertext, balance_after_paise,
                     source, raw_hash, category_id,
                     notes_ciphertext, reversed_by,
                     unique(user_id, raw_hash))

categories          (id, user_id, name, color, icon, is_income, parent_id)

category_rules      (id, user_id, match_kind, pattern, category_id, priority)

budgets             (id, user_id, period, amount_paise, active, starts_on)

thresholds          (id, user_id, kind, scope_id,
                     value_paise, value_pct, notify_on, active)
                     -- kind: BUDGET_PCT | BALANCE_FLOOR | CATEGORY_CAP | SAVINGS_PROGRESS

income              (id, user_id, ts, amount_paise,
                     source_ciphertext, category_id, notes_ciphertext,
                     recurrence_id)

savings_goals       (id, user_id, name_ciphertext, target_paise, target_date,
                     current_paise, active, created_at)

recurrence_rules    (id, user_id, kind, cadence, day_of_period,
                     amount_paise, label_ciphertext, category_id, active)
                     -- kind: INCOME | EXPENSE
                     -- cadence: DAILY | WEEKLY | MONTHLY | YEARLY

bills               (id, user_id, label_ciphertext, amount_paise,
                     due_day, category_id, reminder_days_before, active)

loans               (id, user_id, direction, counterparty_ciphertext,
                     principal_paise, outstanding_paise, notes_ciphertext, created_at)
                     -- direction: LENT | BORROWED

balance_checkpoints (id, user_id, account_id, ts, balance_paise, source)
                     -- derived from any SMS that carried Avl Bal, plus manual anchors

unreconciled_gaps   (id, user_id, account_id, window_start, window_end, gap_paise, resolved)

parser_misses       (id, user_id, sender, body_hash, received_at)
                     -- diagnostic only; never the body
```

Amounts are **paise as `bigint`**. Timestamps are `timestamptz`. No floats anywhere.

## 7. Parsing strategy

Per-bank and per-app regex packs. Versioned. Fixture-tested with scrubbed samples. New formats surface as `parser_misses` rows (sender + hash only).

## 8. Dedupe and reconciliation

Dedupe key: `(user_id, account_last4, amount_paise, direction, round(ts, 2min))`. SMS wins ties because it carries balance. Refunds linked via `reversed_by`, never deleted.

Reconciliation engine compares consecutive `balance_checkpoints`: if `Δbalance ≠ Σ(captured txns)` between two checkpoints, emit an `unreconciled_gaps` row. User resolves via the Unreconciled tray.

## 9. Budgets, thresholds, alerts

Generalized **thresholds** drive every alert. Four kinds, one engine:

| Kind | Scope | Trigger |
|---|---|---|
| `BUDGET_PCT` | a `budgets.id` | `period_spent ≥ value_pct% × budget.amount` |
| `BALANCE_FLOOR` | an `accounts.id` | `account.current_balance ≤ value_paise` |
| `CATEGORY_CAP` | a `categories.id` | `period_spent_in_category ≥ value_paise` |
| `SAVINGS_PROGRESS` | a `savings_goals.id` | `goal.current ≥ value_pct% × goal.target` |

Rules engine runs after every new transaction. **Rate-limited to one notification per (threshold, day)** so a heavy spend day doesn't spam.

## 10. Aggregations

| View | How |
|---|---|
| Today's spend | `SUM(amount_paise) WHERE direction='DEBIT' AND ts ≥ start_of_day` |
| This week | `WHERE ts ≥ start_of_week (Monday)` |
| This month | `WHERE ts ≥ start_of_month` |
| YTD / FY (Apr–Mar) | `WHERE ts ≥ start_of_fy` |
| By category, month-over-month | `GROUP BY category_id, date_trunc('month', ts)` |
| Net savings, month | `SUM(income.amount) − SUM(expense.amount)` |
| Goal progress | `current_paise / target_paise` |

Computed in Postgres (since amount is plaintext) for speed; cached client-side for offline access. Aggregations refresh on every successful sync.

## 11. Daily / weekly / monthly summary

`WorkManager` periodic job at user-chosen time (default 21:00). On fire:
1. Pull aggregations from Supabase (or compute from local cache if offline).
2. Post a **local** notification: closing balance + today's spend + budget remaining.
3. Once per week (Sunday): weekly digest. Once per month (last day): monthly digest.

No server-side cron — phone-local. Battery-cheap.

## 12. Permissions

| Permission | Why | Asked when |
|---|---|---|
| `INTERNET` | Supabase calls | Implicit, declared in manifest |
| `READ_SMS`, `RECEIVE_SMS` | Parse bank SMS | Onboarding |
| `POST_NOTIFICATIONS` | Local alerts | Onboarding |
| `USE_BIOMETRIC` | App lock | Onboarding |
| `RECEIVE_BOOT_COMPLETED` | Re-arm receivers / WorkManager after reboot | Manifest |
| Notification listener (optional) | Secondary capture | Optional screen |

Not requested: contacts, location, storage, camera, accounts, telephony.

## 13. Reliability concerns

- **Offline-first** — every UI action hits the local cache; sync queue retries.
- **Doze / battery optimization** can kill NotificationListener. SMS receiver and WorkManager are fine.
- **Supabase outage** → app stays usable, syncs catch up when back.
- **JWT expiry** → silent refresh; if refresh fails, biometric re-auth required.
- **Out-of-order txn arrival** — never use latest insert for balance, always latest `ts` per account.
- **Boot** — `BOOT_COMPLETED` re-enables WorkManager and confirms SMS receiver registration.

## 14. UI surfaces (Compose)

1. **Onboarding** — sign-up/login → device-lock check → permission rationale → optional inbox backfill.
2. **Dashboard** — month spend vs budget, today / week / month figures, current balance per account, savings progress, unreconciled tray badge.
3. **Transactions** — list, search, filter, edit category/notes, mark reversal, jump to source.
4. **Manual entry** — add debit/credit, pick account, pick category.
5. **Budgets** — monthly cap, per-category caps, balance floors.
6. **Thresholds** — one screen, four kinds.
7. **Categories** — manage list + auto-tag rules.
8. **Income** — recent credits, recurring rules, sources breakdown.
9. **Savings goals** — list, progress bars, target dates, contribute manually.
10. **Recurring / Bills** — upcoming due, paid history, snooze.
11. **Loans** — lent/borrowed log, mark partial repay.
12. **Reports** — pie by category, line by week/month, bar by counterparty (top 10), FY summary.
13. **Settings** — daily summary time, redacted notifications, parser-miss log, encrypted export, **purge all data**, sign out.

All amount-bearing screens set `FLAG_SECURE`.

## 15. Export / portability

Encrypted blob (Argon2id-derived key, never the same as the E2EE key — separate passphrase). Lives in Supabase Storage *or* local file. Required as the "leave Supabase" escape hatch.

## 16. Testing

- Per-bank parser fixture tests (scrubbed strings).
- RLS policy tests via `supabase test db` — each table verified that one user cannot see/modify another's rows.
- Sync queue property tests (offline → online, conflict handling).
- E2EE round-trip tests with rotating keys.
- Instrumentation tests for app-lock, FLAG_SECURE, encrypted-cache round-trip.

## 17. Build phases

| Phase | Scope | Exit |
|---|---|---|
| **v0.1** ✓ | Project scaffold, Keystore, SQLCipher local cache, app lock, FLAG_SECURE, manifest network-permission check | Empty encrypted DB opens; build refuses network |
| **v0.2** | Supabase setup (migrations, RLS), auth flow, INTERNET + cert pinning, SMS receiver + parser for your banks, sync queue, transactions list | Sign up → SMS arrives → row appears in Supabase under your `user_id` |
| **v0.3** | Manual entry, categories + auto-tag rules, today/week/month aggregations on dashboard | Spend a ₹ manually, see it in today's total |
| **v0.4** | Budgets + thresholds (all four kinds) + rules engine + local notifications + rate-limit | Crossing 80% fires one notification, not ten |
| **v0.5** | Income, savings goals, recurring rules, bills | Set "Salary 50k on 1st" — month auto-creates the income row |
| **v0.6** | Daily / weekly / monthly summary worker | 21:00 notification with the right numbers |
| **v0.7** | Reports, charts, search, filters | Tap "Food" → see Food spend trend over 6 months |
| **v0.8** | Loans, tax buckets (80C, medical, donations), FY (Apr–Mar) reports | Mark a txn as "80C" → FY report shows it |
| **v0.9** | Reconciliation engine, balance anchors, unreconciled tray, NotificationListener secondary capture + dedupe | Missing txn surfaces in the tray; same txn from SMS+notif = 1 row |
| **v1.0** | Release signing, sideload polish, parser packs for more banks, onboarding finish, accessibility | Ship to your own phone |

## 18. Open decisions (resolved)

- **Cloud:** Supabase. ✓
- **Multi-user:** yes, RLS from day one. ✓
- **E2EE scope:** counterparty, notes, all `*_ciphertext` fields. Amounts and timestamps plaintext for queryability. ✓
- **Auth:** email/password to start; Google OAuth in v0.7. ✓
- **Banks for v0.2 parser priority:** **SBI** (confirmed 2026-05-26). Other banks added phase-by-phase as needed.
- **Daily summary time default:** 21:00 IST.
- **Threshold defaults:** 80% warn, 95% alert, balance floor user-set.
- **Supabase project:** user-created (Project URL + anon key to be dropped into `local.properties`).

## 19. Risks

| Risk | Mitigation |
|---|---|
| Google Play won't accept `READ_SMS` | Distribute as **sideloaded APK**; signed by you. Already the plan. |
| Supabase pricing scales with usage | Free tier covers ~50k MAU; personal/family use is comfortably in. Monitor. |
| New SMS format breaks parser silently | `parser_misses` table; Settings surfaces miss count. |
| User loses phone | Sign in on new phone → data restored from Supabase. Encrypted fields decrypt with same password. |
| User forgets password | E2EE key is unrecoverable. Encrypted exports require their own passphrase too. Onboarding must warn loudly. |
| Battery optimizer kills listener | Onboarding screen guides whitelist. |
| Supabase outage | Offline cache keeps UI usable; sync resumes. |

## 20. Coverage & failure modes (capture)

(unchanged — bank SMS primary, notif secondary, manual entry, reconciliation tray surfaces gaps.)

## 21. Hardening checklist (release-blocking for v1.0)

### 21.1 Network surface
- [ ] App has `INTERNET` permission. **No other network permissions** (no `ACCESS_NETWORK_STATE`, no `CHANGE_NETWORK_STATE`).
- [ ] OkHttp interceptor pins `*.supabase.co` certificates.
- [ ] Build-time dependency allowlist: only `io.github.jan-tennert.supabase:*`, `ktor-client-*`, AndroidX, Compose, Room, SQLCipher, biometric. CI greps for `okhttp` outside the allowlist and `retrofit`, `firebase`, `crashlytics`, `sentry`, `analytics`, `ads`.
- [ ] Network calls go through one client; no app code uses `URL().openConnection()` directly (lint rule).

### 21.2 Storage at rest
- [ ] `allowBackup="false"` + `dataExtractionRules` deny every domain.
- [ ] Local SQLCipher cache, key from Keystore (hardware-backed required).
- [ ] `PRAGMA secure_delete = ON`.
- [ ] No sensitive data in `SharedPreferences` or `WorkManager` `Data`.
- [ ] Purge-all flow: `DELETE … ; VACUUM ;` then delete file; also calls Supabase `delete_user` RPC.

### 21.3 Crypto
- [ ] E2EE: each user has a "data key" wrapped by an Argon2id-derived key from their password. Argon2id mem ≥ 64 MiB, iters ≥ 3.
- [ ] Data key cached in Keystore-wrapped form after first unlock; never sent to Supabase.
- [ ] Password change re-wraps the data key with the new password's KDF output.
- [ ] All randomness via `SecureRandom`.
- [ ] No `EncryptedSharedPreferences` (Tink + Keystore directly).

### 21.4 RLS
- [ ] Every table has RLS enabled; CI `supabase db lint` fails otherwise.
- [ ] Every table tested with two seeded users; each cannot read/write the other's rows.
- [ ] No usage of the `service_role` key in any shipped artifact.

### 21.5 Memory hygiene
- [ ] Passwords / passphrases as `CharArray`, zeroed in `finally`.
- [ ] Local DB closed when app-lock fires.
- [ ] `android:debuggable="false"`, `extractNativeLibs="false"`.
- [ ] No LeakCanary in release.

### 21.6 Logging
- [ ] `SecureLogger` no-op in release.
- [ ] `toString()` on every model returns only id.
- [ ] No third-party crash reporter.

### 21.7 UI
- [ ] `FLAG_SECURE` on host Activity.
- [ ] Passphrase / PIN fields disable autofill, IME suggestions.
- [ ] Counterparty/notes fields disable IME personalization.
- [ ] Toasts never include amounts/counterparties.

### 21.8 Notifications
- [ ] Default visibility = redacted.
- [ ] `setLocalOnly(true)` on every builder.
- [ ] `PendingIntent` immutable, payload-free.

### 21.9 IPC
- [ ] All components `exported="false"` unless system requires.
- [ ] SMS receiver guarded with `BROADCAST_SMS`.
- [ ] No deep links, no `ContentProvider`, no exported intent filters.

### 21.10 Build & repo
- [ ] Release keystore offline, encrypted at rest.
- [ ] R8 / ProGuard enabled in release.
- [ ] `hasFragileUserData="true"`.
- [ ] APK SHA-256 recorded per release.
- [ ] `.gitignore`: `*.keystore`, `*.jks`, `local.properties`, `.env*`, `parser_samples_real/`.
- [ ] Pre-commit hook greps for `₹\s*\d`, `Rs\.?\s*\d`, `\b\d{10,16}\b`.
- [ ] All parser fixtures in `parser_samples_scrubbed/`.

### 21.11 Residual risks (disclosed in app)
- [ ] About → Security screen lists: rooted device, malicious IME, other apps with `READ_SMS`, Supabase tenant breach scope (timestamps + amounts visible; counterparties/notes encrypted).

## 22. Supabase project layout

```
supabase/
├── config.toml
├── migrations/
│   ├── 0001_init.sql          -- enable extensions, auth schema
│   ├── 0002_accounts.sql
│   ├── 0003_transactions.sql
│   ├── 0004_categories.sql
│   ├── 0005_budgets_thresholds.sql
│   ├── 0006_income_savings.sql
│   ├── 0007_recurring_bills.sql
│   ├── 0008_loans.sql
│   ├── 0009_checkpoints_gaps.sql
│   └── 0010_views_aggregations.sql
├── tests/
│   ├── rls_accounts.test.sql
│   ├── rls_transactions.test.sql
│   └── …                       -- one RLS test file per table
└── seed.sql                    -- empty by default; test seeds in tests/
```

Applied via `supabase db push` from your laptop. CI (later) runs `supabase db lint` + `supabase test db` against a temporary project.

## 23. Deployment

| Component | Where | How |
|---|---|---|
| Supabase project | supabase.com (free tier; Pro when needed) | One-time create via dashboard; schema via `supabase db push` |
| Android APK | Your phone(s), each user's phone | Sideloaded signed APK; Play Store rejects `READ_SMS` regardless |
| (Future) Web app | Vercel | Optional Next.js client against the same Supabase project |
