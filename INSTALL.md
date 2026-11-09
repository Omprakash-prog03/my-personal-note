# Installing ARCHIVE on your phone

## Fastest way — the APK is already built

Every push builds a fresh APK and publishes it here, so there is nothing to compile:

**https://github.com/Omprakash-prog03/my-personal-note/releases/download/apk-latest/ARCHIVE-debug.apk**

Open that link **on the phone**, then:

1. Tap **Download**. The file lands in *Downloads* (or the browser's download list).
2. Tap the downloaded `ARCHIVE-debug.apk`. Android will say installs from this app are not allowed —
   tap **Settings**, enable **Allow from this source**, then press back.
3. Tap **Install**, then **Open**.
4. Sign in with `om` / `sahuom123`.

Play Protect may warn that the app is unknown: *More details → Install anyway*. That is expected for
any app that did not come from the Play Store.

The link always points at the newest successful build
([all builds](https://github.com/Omprakash-prog03/my-personal-note/actions/workflows/build-apk.yml) ·
[releases](https://github.com/Omprakash-prog03/my-personal-note/releases/tag/apk-latest)).

## Build it yourself (optional)

### Build the APK

### Option A — Android Studio (easiest)

1. **File → Open…** and select the project folder (the one containing `settings.gradle`).
2. Wait for *Gradle sync* to finish. The first sync downloads Gradle 8.9 and all libraries — expect
   5–15 minutes on a normal connection.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. When the “APK(s) generated successfully” notification appears, click **locate**.

The file you want:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Option B — command line

```bash
cd my-personal-note
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

The Gradle wrapper is committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`), so
no separate Gradle install is needed — only JDK 17 (`JAVA_HOME`). Android Studio's bundled JDK works:

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
```

Wrapper jar provenance, if you want to verify it: taken unmodified from the official
`gradle/gradle` repository at tag `v8.9.0`, SHA-256
`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`.

---

## 2. Get the APK onto the phone

Any of these works:

* **USB cable** – plug in, choose “File transfer / MTP”, copy `app-debug.apk` to `Download/`.
* **Cloud** – upload to Google Drive / Dropbox, or email it to yourself, then download on the phone.
* **ADB** (if USB debugging is enabled):
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  `-r` reinstalls over an existing copy and keeps your data.

---

## 3. Allow the install

Android blocks sideloading by default:

1. Tap the downloaded APK in the **Files** app (or your browser's downloads).
2. When prompted, allow **Install unknown apps** for that app:
   *Settings → Apps → Special app access → Install unknown apps → Files → Allow*.
3. Tap **Install**. Play Protect may warn that the app is unknown — choose **More details → Install
   anyway** (expected for anything not from the Play Store).

---

## 4. First launch

Sign in with the seeded local account:

| Username | Password |
|---|---|
| `om` | `sahuom123` |

Change it immediately in **Settings → Account → Change password**. Leave “Keep me signed in” off if
you want the password required every time the app starts.

---

## 5. Reinstalling without losing data

* Debug builds are signed with the same debug key, so a newer `app-debug.apk` installs **over** the old
  one and keeps your collections (`adb install -r` or just tap the new APK).
* If Android says **“App not installed”** it is usually a signature mismatch (a release build already
  installed) or low storage. Uninstalling removes the archive, so first use
  **Settings → Storage → Back up** (zip) or export important pages as PDF (canvas menu → Export).

---

## 6. Optional: release APK

For a proper signed build: **Build → Generate Signed App Bundle(s) / APK(s) → APK**, create a keystore,
choose `release`. Keep the keystore and its passwords out of git. An unsigned release APK will not
install — Android requires a signature.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Open the project in Android Studio once, or create `local.properties` with `sdk.dir=/path/to/Android/sdk` |
| `Failed to find target with hash string 'android-35'` | SDK Manager → install **Android 15 (API 35)** and **Build Tools 35.0.0** |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| Stuck at `Downloading gradle-8.9-bin.zip` | The computer is offline or behind a proxy; allow `services.gradle.org` and `dl.google.com` |
| `There was a problem parsing the package` | The copy is incomplete (retry the transfer) or the phone is older than Android 8.0 |
| `App not installed` | Signature clash or no space — uninstall the old copy after backing up |
| Play Protect blocks the install | “More details → Install anyway”; normal for sideloaded builds |
| App closes straight after the login screen | Check `logcat`; that is a bug worth reporting with the stack trace |

---

## Honest note about this build

The project has **never been compiled**: it was written in an environment without a JDK or Android
SDK, and verified only by static checks (`python3 tools/check_sources.py` — syntax parse of every Java
file plus resource-reference resolution). Expect a handful of ordinary compile errors on the first
Gradle build. If you paste them back, they are usually quick to fix.
