# ARCHIVE

A personal digital museum for Android: collect photographs, notes, research and drawings in
structured collections, then arrange them freely on a zoomable scrapbook canvas.

Offline-first. No account server, no cloud, no analytics. Everything lives in app-private storage
on the device.

---

## Build & install

**Download the APK:** [ARCHIVE-debug.apk](https://github.com/Omprakash-prog03/my-personal-note/releases/download/apk-latest/ARCHIVE-debug.apk)
— every push to this repository builds it on GitHub Actions, so no local toolchain is needed to try
the app. Open the link on the phone, allow installs from the browser, and tap the file.


See **[INSTALL.md](INSTALL.md)** for phone installation (APK build, sideloading, troubleshooting).

## Build

```
Android Studio (Ladybug or newer)  •  JDK 17  •  Gradle 8.9  •  AGP 8.7.3
compileSdk / targetSdk 35           •  minSdk 26 (Android 8.0)
```

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (hashing, ids, page geometry)
./gradlew connectedDebugAndroidTest  # Room + canvas + export tests (device required)
./gradlew installDebug           # install on a connected device / emulator
python3 tools/check_sources.py   # offline syntax + resource-reference check (no SDK needed)
```

Room schemas are exported to `app/schemas/`. Never enable a destructive migration fallback: the
archive is the user's data.

### What has actually been verified

| Check | Where | Result |
|---|---|---|
| `assembleDebug` (APK builds and is signed) | GitHub Actions, every push | ✅ passes |
| `testDebugUnitTest` | GitHub Actions, every push | ✅ 17 tests, 3 classes, 0 failures |
| `tools/check_sources.py` (syntax, resources, a11y) | local, no SDK needed | ✅ 0 problems |
| `connectedDebugAndroidTest` (23 instrumented tests) | needs a device | ⏳ not run yet |
| §26 acceptance walkthrough on hardware | manual | ⏳ not run yet |

CI publishes the APK it just built to
[the `apk-latest` release](https://github.com/Omprakash-prog03/my-personal-note/releases/tag/apk-latest),
so the build and the download link can never drift apart.

---

## Signing in

The app opens on a local sign-in screen. The default account is seeded on first launch:

| Field | Value |
|---|---|
| Username | `om` |
| Password | `sahuom123` |

* Only a **salted PBKDF2-HMAC-SHA256 hash** (120,000 iterations, 256-bit key) is stored, in a
  private `SharedPreferences` file. The plaintext password is never written to disk, logged, or sent
  anywhere.
* "Keep me signed in" persists the session; otherwise the session ends when the process dies.
* Change the password in **Settings → Account → Change password** (requires the current password;
  minimum 6 characters).
* There is no in-app password recovery, because there is no server that could verify identity.
  Forgetting the password means clearing ARCHIVE's data in Android settings, which erases the
  archive. Back up first (Settings → Storage → Back up).
* Repeated failed attempts are throttled: five misses, then a 30-second pause.
* Optional biometric / device-credential app lock (Settings → Privacy) adds a second gate on
  resume.

---

## Architecture

```
com.archive.app
├── data
│   ├── database      AppDatabase (v1), 15 entities, 10 DAOs, no destructive fallback
│   ├── repository    ArchiveRepository, CanvasRepository (all Room access lives here)
│   ├── storage       FileStore (media, thumbnails, exports, recovery), BackupManager
│   └── preferences   SettingsStore, AuthStore
├── domain
│   ├── model         CanvasElement + 10 element types, PageFormat, BackgroundStyle
│   └── security      PasswordHasher (PBKDF2)
├── canvas
│   ├── engine        CanvasDocument, CanvasViewport, CanvasView, CanvasRenderer, BitmapStore
│   ├── paint         StickerArt, BackgroundRenderer
│   ├── gestures      CanvasTouchHandler (select / text / draw state machine)
│   ├── commands      CommandHistory + 7 command types (delta-based undo/redo)
│   └── selection     SelectionState
├── media
│   ├── importer      MediaImporter (copy-on-import, EXIF-aware thumbnails)
│   ├── camera        CameraActivity (CameraX)
│   └── exporter      PageExporter (PNG / JPEG / PDF)
├── ui
│   ├── home, collections, library, search, settings, trash, pdf, auth
│   ├── canvas        CanvasActivity, CanvasViewModel, CanvasSheets, ElementDialogs
│   ├── journal, research
│   └── common        BaseActivity, UiKit, ItemDialogs, CreateSheet, CollectionCardBinder
├── workers           BackupWorker (WorkManager)
└── utils             Ids, DateFormats, UiUtils, ThemeController, SettingsStore
```

**Data flow:** View → ViewModel → Repository → (Room ∪ FileStore) → LiveData → View. Activities
never touch DAOs. File I/O and PDF rendering always run on a background executor.

**One renderer:** the same `CanvasRenderer` paints the interactive view, the page thumbnail and the
exported bitmap/PDF. Selection chrome is a separate pass that exports never enable.

**Undo/redo** stores deltas (transform snapshots, property JSON, id lists) — never bitmaps — and is
bounded to 60 entries.

**Autosave** debounces 600 ms, forces a write on pause/stop/exit/export, and writes a recovery
snapshot alongside every commit; recovery is offered only when it is newer than the page row.

---

## Database (schema v1)

| Table | Purpose |
|---|---|
| `collections` | named shelves, cover, colour, icon, pin/favourite, soft delete |
| `pages` | page geometry, format, mode, background JSON, thumbnail, ordering |
| `canvas_elements` | one row per object: geometry columns + type-specific JSON props |
| `media` | metadata only (path, MIME, size, dimensions, checksum); bytes live in files |
| `attachments` | page ↔ media links |
| `journal_entries` | dated entries with mood |
| `research_projects` / `research_references` | notebooks and manually typed references |
| `library_items` | books / films / music / articles / sites / quotes |
| `stickers`, `templates` | reusable assets |
| `tags`, `collection_tag_cross_ref`, `page_tag_cross_ref` | tagging + search |
| `user_preferences` | non-critical preference mirror |

All foreign keys cascade, soft-deleted rows are filtered by index-backed `trashed` columns, and
large binaries are never stored in SQLite.

---

## Feature checklist

Legend: ✅ implemented (statically verified here) · 🧪 implemented, needs on-device testing ·
⛔ not implemented

| Area | State |
|---|---|
| Gradle project, Java 17, Material 3 themes (Warm / Minimal / Dark) | ✅ |
| Room v1 entities, DAOs, indexes, cascades, soft delete | ✅ |
| Home dashboard, collections, recent pages, empty states | ✅ |
| Collection detail: rename, cover, favourite, pin, duplicate, delete, PDF export | 🧪 |
| Multi-page PDF export with page picker (select all, ordered output) | 🧪 |
| Page templates: blank, journal, research notes, quote card, index | ✅ |
| Image crop: aspect presets from source pixels + per-edge crop sliders | ✅ |
| Export picker with page thumbnails and select-all | 🧪 |
| Page creation: format + paper preset picker + page/infinite modes | ✅ |
| Page duplicate (fresh ids, transactional) and reorder within a collection | ✅ |
| Collection sorting: recently edited / alphabetical / custom order | ✅ |
| Canvas engine: zoom, pan, cull, selection, move, resize, rotate, marquee | ✅ |
| 13 element types (text, image, sticky, envelope, sticker, shape, draw, document, audio, link…) | ✅ |
| Layers: front / back / forward / backward, lock, hide, groups | ✅ |
| Undo / redo with bounded delta history | ✅ |
| Photo Picker (single + multiple), CameraX capture with flash/flip/review | 🧪 |
| SAF document import + `PdfRenderer` viewer | 🧪 |
| Text formatting (font, size, colour, bold/italic/underline/strike, align, spacing) | ✅ |
| Handwriting (pen / pencil / highlighter / eraser, colour, width) | ✅ |
| Sticker and shape libraries, background selector | ✅ |
| Autosave with debounce, forced saves, crash-recovery prompt | 🧪 |
| PNG / JPEG / PDF export with remembered defaults, share via FileProvider | 🧪 |
| Journal, research notebooks, personal library, global search, tags | ✅ |
| Settings: appearance, canvas defaults, export defaults, storage, privacy, about, trash | ✅ |
| Local account: salted PBKDF2 login, change password, sign out, throttling | 🧪 |
| Biometric / device-credential app lock | 🧪 |
| Backup (WorkManager zip) and staged restore | 🧪 |
| JVM unit tests (password hashing, page geometry, id generation) | ✅ |
| Instrumented tests: Room persistence/cascade/transactions, canvas document, crop maths, export render | 🧪 (device required) |
| Cloud sync, accounts, subscriptions, analytics, ads | ⛔ (deliberately out of scope) |

"Statically verified" means the code parses and every resource reference resolves. This sandbox has
no JDK or Android SDK, so **the project has not been compiled or run here**; the 🧪 rows need a real
device pass.

---

## Privacy

* App-private storage only; no external storage permissions are requested.
* Photo Picker and SAF deliver content URIs; files are copied into managed storage immediately, so
  no long-lived URI permissions are relied upon.
* No network code, no ads, no analytics, no third-party trackers.
* `allowBackup="false"` — backups are explicit, user-initiated zips.
* Passwords: salted PBKDF2 hash; never logged or persisted in plaintext.
* Delete flows are soft by default; "empty trash" is the only permanent deletion path and it asks
  for confirmation.

---

## Known limitations

1. **Not compiled in this environment.** No JDK/Android SDK is available; validation is a
   tree-sitter syntax pass plus full resource-reference checks (`tools/check_sources.py`, 0
   problems across 113 Java files and 117 XML files). The APK does build: GitHub Actions compiles it
   and the 17 JVM unit tests pass on every push. The UI itself has never been exercised on hardware.
2. Autosave distinguishes success from failure: a failed batch keeps the affected changes dirty,
   shows "Unable to save — Retry", and retries after two seconds. Only ids actually written (and not
   edited mid-write) are un-dirtied. This retry path is statically verified, not device-tested.
3. Restore intentionally restarts the process; that path is untested on-device.
4. Instrumented tests live in `app/src/androidTest` — 23 tests over Room persistence, cascade delete,
   soft-delete filtering, transactions, canvas hydration/grouping/undo, image crop maths and export
   rendering. CI compiles them (`compileDebugAndroidTestJavaWithJavac`) so they stay syntactically
   valid, but they only *run* on a device via `connectedDebugAndroidTest`, which has not happened.
5. Password recovery is impossible by design (local-only account). Back up before changing
   credentials.

---

## License

Personal project. Third-party licences are listed in-app under Settings → About → Licences.
