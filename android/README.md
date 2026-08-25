# ATR Trailing Bot — Android App

Native Kotlin Android app (dark IBM Carbon theme) for monitoring and configuring the
Go ATR Trailing Bot backend. Zero paid/proprietary dependencies — builds entirely via
Gradle + AndroidX + Material + OkHttp.

## What it does

- **Dashboard** (`MainActivity`) — same data as the web dashboard: PnL, wallet balance,
  open position, open orders, bot activity log. Polls your backend's `/api/*` endpoints
  every 1.5s. Pull-to-refresh supported.
- **Settings** (`SettingsActivity`) — fill in:
  - Backend URL (your Go bot's local address, e.g. `http://192.168.1.5:8787`)
  - Delta API Base URL (defaults to `https://api.india.delta.exchange`)
  - API Key / API Secret (with show/hide toggle)
  - Product Symbol + **Resolve Symbol** button (calls Delta's public product endpoint
    directly from the phone to fetch the numeric product_id)
  - Lot (order size in contracts)
  - Leverage + **Apply Leverage on Delta** button (calls Delta's
    `POST /v2/products/{id}/orders/leverage` directly from the phone, signed with your
    keys — takes effect immediately, independent of the backend)
  - **Test API Keys** button (signed call to `/v2/wallet/balances` to confirm your keys
    work before trusting anything else)
  - **Save** — stores everything locally, then best-effort pushes lot/leverage to the
    backend's `/api/settings` endpoint so the running bot picks it up without a restart

## Where each setting takes effect

| Setting | Where it applies | Live or needs restart? |
|---|---|---|
| Lot (order size) | Backend, via `POST {backendUrl}/api/settings` | **Live** — next signal uses new size |
| Leverage | Delta directly, via `POST /v2/products/{id}/orders/leverage` | **Live** — immediate |
| API Key / Secret | Stored on phone; used for direct Delta calls (Resolve/Test/Leverage) | N/A (phone-side only) |
| Product Symbol / Product ID | Compared against the backend's running symbol | **Rejected if different** — the backend intentionally refuses to hot-swap the traded symbol (re-priming ATR/volume history safely needs a restart). Edit `config.json`'s `product_symbol` and restart the Go bot instead. |
| Backend URL, Delta Base URL | Phone-side only | N/A |

This matches the backend's `/api/settings` endpoint (added alongside this app) — see
`internal/api/server.go` in the Go project for the exact contract.

## Build via GitHub Actions (no local Android Studio needed)

1. Drop this `android/` folder **and** the `.github/workflows/android-build.yml` file
   into the root of your GitHub repo (same level, so the workflow lands at
   `.github/workflows/android-build.yml` and the project at `android/`).
2. Push to `main`/`master`, or trigger manually from the **Actions** tab
   ("Run workflow").
3. Once the run finishes, download the APK from the run's **Artifacts** section
   (`delta-atr-bot-debug-apk`).
4. Transfer the APK to your phone and install it (you'll need to allow "install from
   unknown sources" the first time).

The workflow needs no secrets and no local setup — it installs JDK 17, the Android SDK,
and Gradle 8.7 fresh each run, then runs `gradle assembleDebug`.

## Build locally (optional, if you have Android Studio / SDK installed)

```
cd android
gradle assembleDebug
```
APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Security notes

- API key/secret are stored in plain (app-private, not world-readable) SharedPreferences
  — not encrypted at rest. Fine for personal use on your own device; if you want
  encryption-at-rest, swap `Prefs.kt` for `androidx.security`'s
  `EncryptedSharedPreferences` (not included here to keep the dependency surface small
  and the CI build as reliable as possible).
- The app allows cleartext (plain HTTP) traffic so it can reach your backend on your
  local network without TLS. This does **not** affect calls to Delta Exchange itself,
  which are always HTTPS.
- Because this app calls Delta directly with your API key/secret (for Resolve/Test
  Keys/Apply Leverage), make sure your phone's network is one you trust, and that your
  Delta API key's IP whitelist (if you use one) permits your phone's public IP, or
  disable IP whitelisting for that key if you'll be calling from changing networks.

## Known limitations / things you may want to extend

- No push notifications on signals/orders (would need Firebase Cloud Messaging wired
  into the Go backend, which isn't included).
- No authentication on the backend's `/api/*` endpoints — anyone on your local network
  who can reach the backend's port can read your PnL/positions/wallet and hot-swap the
  lot/leverage. Fine on a trusted home network; add a shared-secret header if you'll
  expose the backend more broadly.
- Settings screen doesn't yet support changing ATR period / key value / volume filter /
  session filter live — those still require editing `config.json` and restarting the
  bot. Happy to add a `/api/strategy-settings` endpoint + matching UI section if useful.
