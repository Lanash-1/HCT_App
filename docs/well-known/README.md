# App Links verification

Android only opens `https://belay.codigitech.com/pair/…` invite links directly in Belay (no
"open with" chooser) once it can fetch a matching Digital Asset Links file. Until then the links
still work — Android just asks the user which app should handle them.

To enable verification:

1. Confirm the domain (see [OPEN_DECISIONS.md](../OPEN_DECISIONS.md) — `belay.codigitech.com` is
   provisional and is currently hardcoded in `PairingDeepLink.kt` and `AndroidManifest.xml`).
2. Copy `assetlinks.json.example` to `assetlinks.json` and replace the fingerprint placeholder
   with the release signing certificate's SHA-256:
   ```bash
   keytool -list -v -keystore <release.keystore> -alias <alias> | grep 'SHA256:'
   ```
   If Play App Signing is enabled, use the fingerprint Play Console shows under
   *Setup → App integrity*, not the upload key's — they differ, and using the upload key's
   silently fails verification on installed builds.
3. Host it at `https://belay.codigitech.com/.well-known/assetlinks.json`, served as
   `application/json` over HTTPS with no redirects.
4. Verify with:
   ```bash
   adb shell pm verify-app-links --re-verify com.codigitech.belay
   ```
