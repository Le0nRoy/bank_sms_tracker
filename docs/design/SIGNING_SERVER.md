# Signing Server Plan

## Problem

Android's default debug keystore lives at `~/.android/debug.keystore` and is
machine-generated on first use.  When two machines (dev laptop + CI runner)
build the same app, they produce APKs with different signatures.  Consequently
`adb install -r` refuses to upgrade an APK already on the device and the
fallback backup/restore dance in `make update-adb` is triggered.

**Goal:** one canonical debug keystore shared by every build environment so
`adb install -r` always works without backup/restore.

---

## Current state (phase 0 — already done)

`keystore/debug.keystore` is generated once with `make keystore-init` and
referenced in `app/build.gradle.kts`.  It is `.gitignore`d.  On a single
machine this is already sufficient; new machines need the file distributed
out-of-band.

---

## Phase 1 — Simplest viable multi-machine solution (recommended start)

Store the keystore as an **encrypted secret in your CI provider** and as a
**local file fetched from a shared location** (e.g. a private object-storage
bucket or a team password manager like 1Password / Bitwarden).

### CI side (GitHub Actions / GitLab CI / etc.)

```yaml
# .github/workflows/build.yml (example)
- name: Write debug keystore
  run: |
    echo "${{ secrets.DEBUG_KEYSTORE_B64 }}" | base64 -d > keystore/debug.keystore
```

Store the base64-encoded keystore as a CI secret:
```
base64 -w0 keystore/debug.keystore   # copy output → CI secret DEBUG_KEYSTORE_B64
```

### Local side (new developer or new machine)

Option A — shared private bucket:
```
aws s3 cp s3://your-private-bucket/banksmstracker/debug.keystore keystore/debug.keystore
# or: gsutil cp gs://your-bucket/...  keystore/debug.keystore
```

Option B — team password manager:
- Store the base64 string as a secure note in 1Password / Bitwarden / pass.
- On new machine: decode and write to `keystore/debug.keystore`.

Add a Makefile helper:
```makefile
keystore-pull: ## Download shared debug keystore from S3 (requires AWS credentials)
    aws s3 cp s3://your-bucket/banksmstracker/debug.keystore keystore/debug.keystore
    @echo "keystore/debug.keystore fetched."
```

**Pros:** zero infrastructure, works today, fits any CI.
**Cons:** distributing the file is still manual for the first team member.

---

## Phase 2 — Lightweight HTTP signing server

A small HTTP service that stores the keystore and signs APKs (or just serves
the keystore over an authenticated endpoint).  Deployable as a Docker container
on the same server that already runs the Appium + Gradle cache cluster.

### API (minimal)

| Method | Path | Description |
|--------|------|-------------|
| `GET /keystore/debug` | — | Download `debug.keystore` (auth required) |
| `POST /sign` | body: unsigned APK | Returns signed APK |

### Stack choice

- **Language:** Python (Flask) or Go — both fit in a ~20 MB container.
- **Auth:** Bearer token stored as CI secret and in `~/.config/signing-server/token`.
- **Storage:** keystore file on a volume-mounted path (not baked into the image).

### Docker service (added to `docker-compose.yml`)

```yaml
signing-server:
  image: banksmstracker/signing-server:latest
  ports:
    - "5072:5072"
  volumes:
    - ./keystore:/keystore:ro   # read-only; keystore lives on host
  environment:
    - SIGNING_TOKEN=${SIGNING_TOKEN}
  restart: unless-stopped
```

### Makefile integration

```makefile
keystore-pull-server: ## Fetch debug keystore from local signing server
    @curl -fsSL -H "Authorization: Bearer $${SIGNING_TOKEN}" \
        http://localhost:5072/keystore/debug -o keystore/debug.keystore
    @echo "keystore/debug.keystore fetched from signing server."
```

### CI integration

```yaml
- name: Fetch keystore from signing server
  run: |
    curl -fsSL -H "Authorization: Bearer ${{ secrets.SIGNING_TOKEN }}" \
        https://signing.your-domain.com/keystore/debug \
        -o keystore/debug.keystore
```

**Pros:** single source of truth, revocable tokens, works on LAN or over HTTPS.
**Cons:** requires a reachable server; adds one more service to operate.

---

## Phase 3 — Full signing-as-a-service (future, if team grows)

The signing server accepts **unsigned APKs** via `POST /sign` and returns signed
APKs.  The keystore never leaves the server.  Builds upload an unsigned APK and
download a signed one.

```
POST /sign
Content-Type: application/octet-stream
Authorization: Bearer <token>

[unsigned APK bytes]

→ 200 OK  [signed APK bytes]
```

Gradle task variant:
```kotlin
// app/build.gradle.kts (sketch)
tasks.register("signViaServer") {
    dependsOn("assembleDebugUnsigned")
    doLast {
        val unsigned = file("build/outputs/apk/debug/app-debug-unsigned.apk")
        val signed   = file("build/outputs/apk/debug/app-debug.apk")
        val token    = System.getenv("SIGNING_TOKEN") ?: error("SIGNING_TOKEN not set")
        // HTTP POST unsigned → receive signed
        // ... implementation using HttpURLConnection or OkHttp
        signed.writeBytes(/* response body */)
    }
}
```

**Pros:** keystore is never distributed; maximum key security.
**Cons:** network round-trip in every build; significantly more code to maintain.

---

## Recommended path

| When | Action |
|------|--------|
| Now (solo dev) | Phase 0 already done — `make keystore-init` once, back up the file manually |
| Adding CI | Phase 1 — base64 keystore as CI secret, 1 env var |
| Adding second dev | Phase 1 — store in shared bucket or password manager |
| Team ≥ 3 or compliance req | Phase 2 — signing server as a Docker service alongside Appium |
| Key must never leave server | Phase 3 — signing-as-a-service |

---

## Security notes

- The **debug keystore** is low-risk: it only signs development builds and
  grants no production privileges.  Sharing it via a team password manager or
  CI secret is acceptable.
- The **release keystore** must never be committed to git and should go
  directly to Phase 2 or 3 when CI release builds are needed.
- Rotate the token (Phase 2/3) if a CI runner or developer machine is
  compromised.
