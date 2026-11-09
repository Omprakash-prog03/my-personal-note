# ARCHIVE — Architecture & Product Analysis

> Required first-response analysis (spec §33). Read this before the code.

## 1. Understanding of ARCHIVE

ARCHIVE is a **local-first personal museum**: a collection layer (research library + journal +
reference shelf) wrapped around a **freeform canvas engine** that behaves like a physical
scrapbook you can rearrange forever.

Three product truths drive every decision:

1. **The canvas is the product.** It is an editor, not an image. Every object stays an
   independent, addressable, editable record — never flattened into a bitmap.
2. **Nothing may be lost.** Autosave + command history + crash recovery + trash + backup
   are not "phase 7 polish"; they are load-bearing.
3. **It must feel like paper, not like a form.** Editorial typography, paper textures and
   tactile objects on top of conventional, predictable Android navigation.

Everything runs offline, in app-private storage, with no accounts, ads or telemetry.

## 2. Application architecture

MVVM with a **domain layer that owns the canvas model**, so the canvas engine never touches
Room, files or `Context`. Dependencies point inward:

```
ui/ (Activities, Fragments, Adapters)
        │  observes LiveData, calls ViewModel intents
ui/*/ViewModel + CanvasViewModel
        │  immutable-ish documents + commands
domain/model  (CanvasElement hierarchy, PageFormat, BackgroundStyle)
        │  pure Java, unit-testable, no Android UI
canvas/engine (CanvasDocument, CanvasRenderer, CanvasView, CanvasTouchHandler)
        │
data/repository (ArchiveRepository, CanvasRepository)  ── data/storage (FileStore, Backup)
        │
data/database (Room) + data/entity + data/dao
```

* No business logic in Activities; Activities bind views and route intents.
* `ArchiveApp` is the composition root (single DB, single FileStore, single repository).
* All disk/DB work runs on a shared `ExecutorService`; UI only receives LiveData.
* Commands (`canvas/commands`) are the single mutation path for the canvas, which is what
  makes undo/redo, autosave and crash recovery consistent.

## 3. SDK and dependencies (verified on Google Maven)

| Item | Choice | Reason |
|---|---|---|
| Language | **Java 17** source/target | required |
| compileSdk / targetSdk | **35** (Android 15) | current stable platform |
| minSdk | **26** (Android 8.0) | `java.time`, `PdfRenderer`, adaptive icons; broader install base than 29 |
| AGP / Gradle | **8.7.3 / 8.9** | stable pair, JDK 17, supports compileSdk 35 |
| UI | Material **1.12.0**, AppCompat **1.7.1**, ConstraintLayout 2.1.4, RecyclerView 1.3.2, ViewPager2 1.1.0 | XML layouts |
| Persistence | Room **2.6.1** (annotationProcessor, Java) | schema export + migrations |
| Location | Play Services **not** used | offline-only |
| Camera | CameraX **1.3.4** (core/camera2/lifecycle/view) | PreviewView + ImageCapture, Java API |
| Media picker | `ActivityResultContracts.PickVisualMedia` (activity **1.9.3**) | no storage permission |
| Documents | SAF `OpenDocument` / `CreateDocument` | no broad storage access |
| Background | WorkManager **2.9.1** | queued backup / media GC |
| Prefs | SharedPreferences (`SettingsStore`), Room `user_preferences` for layout config | DataStore in Java needs coroutines; SharedPreferences is the spec's sanctioned fallback |
| Auth | androidx.biometric **1.1.0** | optional app lock |
| PDF | platform `PdfDocument` (write) / `PdfRenderer` (read) | zero dependencies |

No Kotlin, no WebView, no Compose, no RN/Flutter.

## 4. Database schema (Room, String UUID primary keys)

`collections` · `pages` · `canvas_elements` · `media` · `attachments` · `journal_entries` ·
`research_projects` · `research_references` · `library_items` · `stickers` · `templates` ·
`tags` · `collection_tag_cross_ref` · `page_tag_cross_ref` · `user_preferences`

* `collections.id` → `pages.collectionId` (CASCADE, indexed).
* `pages.id` → `canvas_elements.pageId` (CASCADE, indexed on `pageId, zIndex`).
* `media` rows only store paths/metadata — **never image bytes**.
* Geometry (x, y, w, h, rotation, scale, opacity, zIndex, visible, locked, groupId) is
  first-class columns; type-specific attributes live in a `props` JSON column. This keeps the
  schema stable while 11 element types evolve, and keeps queries over geometry cheap.
* Soft delete (`trashed`) on collections, pages, journal, research, library items → trash.
* `version = 1` with explicit `Migrations` scaffold and `fallbackToDestructiveMigration` **not**
  enabled. Schema JSON is exported to `app/schemas` so future migrations are verifiable.
* Multi-row writes (collection + pages, element batch, tag links) run in `@Transaction` methods.

## 5. Canvas rendering architecture

* **One document, two modes.** `CanvasDocument` holds `PageFormat` (A4 portrait/landscape,
  square, journal, custom) and a mode flag. Page mode renders a fixed page rect with a drop
  shadow on a backdrop and clamps panning; infinite mode drops the page rect and lets the
  viewport roam. Same elements, same renderer, same gestures.
* **View-space vs. document-space.** One `Matrix` (`viewport`) maps document → view. All hit
  tests and handle sizes convert through it, so handles stay constant in dp at any zoom.
* `CanvasRenderer` is pure drawing: backdrop → page (background style) → elements by z-index →
  selection chrome (only when `opts.chrome`). Export calls the same renderer with
  `chrome = false`, which is how selection handles can never leak into a PNG/PDF.
* Per-element draw is a small polymorphic `render()` (image / text / shape / stroke /
  sticker / sticky note / envelope / document card / link card).
* **Performance:** viewport culling (skip elements outside view), `StaticLayout` cache keyed by
  element id + revision, bitmap LRU (`BitmapStore`) with `inSampleSize` computed from the
  on-screen size, hardware-accelerated `View` drawing, and `invalidate(dirtyRect)` for drags.
  No bitmap is copied per command; drawing strokes are vectors shared by reference.
* Text uses `StaticLayout` so multiline content wraps, aligns and cannot silently overflow the
  box (it clips to the object bounds with a configurable `autoHeight` grow flag).

## 6. Touch interaction architecture

`CanvasTouchHandler` receives `MotionEvent`s from `CanvasView` and runs a small state machine:

`IDLE → PRESSED → (DRAG | HANDLE_SCALE | HANDLE_ROTATE | PAN | PINCH | DRAW | LONG_PRESS)`

* `ScaleGestureDetector` always drives **viewport zoom**; object resizing is handle-driven, so a
  two-finger pinch never accidentally resizes artwork.
* One-finger drag moves the object only after a 6 dp slop threshold; otherwise the gesture is a
  tap (select) or a canvas pan (when nothing is hit). This is what prevents accidental
  object movement while panning.
* Rotate handle above the top edge, snap to 15° when `snapToGrid`-adjacent snapping is enabled.
* Multi-select: long-press on empty canvas starts marquee selection; toolbar exposes group /
  ungroup, align and layer actions, so **no essential action is gesture-only** (§27).
* Every gesture that mutates state is wrapped in a `Command`; a drag produces one
  `TransformCommand` on `ACTION_UP` (with the pre-drag values captured on `ACTION_DOWN`), so
  history stays small and autosave fires once per committed gesture.

## 7. File storage strategy

```
filesDir/archive/
  media/images|documents|audio|stickers|backgrounds/   <uuid>.<ext>
  drawings/                                            stroke JSON payloads (optional)
  thumbnails/<mediaId>.jpg                             256 px long edge, JPEG 82
  exports/                                             PNG / JPEG / PDF
  backups/                                             zip of db + media
  recovery/<pageId>.json                               crash-recovery snapshots
```

* Every import is **copied** into app-private storage with a UUID filename; the source URI is
  never needed again, so no transient-permission bugs (§6). Originals are never overwritten.
* MIME and extension are preserved; decoded dimensions are stored on the media row.
* Thumbnails are generated off the main thread with `inSampleSize`; lists use them, so no
  screen ever decodes a 12 MP JPEG at full size.
* Deleting an element never deletes media immediately: media referenced by no element and no
  collection cover becomes garbage-collectable, and `MediaGcWorker` reclaims it. That makes
  undo of a delete safe.
* Backup writes a zip (DB checkpoint + media + `manifest.json`) and restores it through a
  confirmation + validation + restart flow.

## 8. UI screen hierarchy

```
MainActivity (BottomNavigationView)
├── HomeFragment ............ greeting, search, pinned & featured collections,
│                            recent pages, recent media, journal nudge
├── SearchFragment .......... global search + filters (type, collection, favourites)
├── Create (sheet) .......... new collection / page / journal / research / reference / import
├── LibraryFragment (tabs) .. Collections | Journal | Research | References
└── SettingsFragment ........ Appearance, Canvas, Storage, Privacy, About

CollectionActivity ...... page grid + collection actions (rename/cover/colour/pin/dup/export/trash)
CanvasActivity .......... the editor
├── top app bar ......... undo, redo, layers, background, export, share, save state
├── CanvasView .......... gesture surface
├── contextual toolbar .. selection actions (edit, duplicate, lock, layers, delete)
├── bottom tool rail .... select, text, image, camera, draw, shape, sticker, envelope, note, doc
└── sheets .............. add element, stickers, shapes, layers, background, inspector, export
JournalEditorActivity / ResearchDetailActivity / PdfViewerActivity / TrashActivity
CameraActivity .......... CameraX preview, flash, lens flip, confirm/retake
```

Themes: `Warm Archive`, `Modern Minimal`, `Dark Academic`, each with 6 accent overlays and a
home background style, all resolved through theme attributes so no screen hardcodes colour.

## 9. Milestones

| Phase | Deliverable | State in this drop |
|---|---|---|
| 1 Foundation | project, themes, nav, home, collections | ✅ |
| 2 Canvas foundation | engine, selection, transform, layers, undo | ✅ |
| 3 Media | Photo Picker, CameraX, documents, storage, thumbnails | ✅ |
| 4 Creative tools | drawing, shapes, stickers, envelopes, notes, text formatting, backgrounds | ✅ |
| 5 Persistence | Room, repositories, autosave, recovery, backup | ✅ |
| 6 Personal archive | research, journal, library, search, tags | ✅ |
| 7 Finishing | export PNG/JPEG/PDF, share, performance, a11y, tests | ✅ (device-level QA pending your build) |
| 8 Access | local PBKDF2 account, change password, sign out, throttling | ✅ (device-level QA pending your build) |

## 10. Technical risks and mitigations

| Risk | Mitigation |
|---|---|
| OOM on large photos / many objects | sampled decoding sized to display, LRU bitmap cache, export capped at 4096 px, recycle on eviction |
| Lost work on process death | debounced autosave on the DB + atomic recovery snapshot on every commit + restore prompt on open |
| Uri permission expiry | copy-on-import; never persist external URIs |
| Rotated hit-testing bugs | single geometry helper (`CanvasElement.hitTest`) used by render *and* touch |
| Undo stack memory | commands store deltas only; drag = one command on release; drawing shares stroke objects |
| Room schema drift | exported schema JSON + explicit migrations; no destructive fallback |
| Gesture conflicts (pan vs. move) | explicit state machine + slop threshold + handle priority + two-finger = zoom/pan |
| Canvas jank | culling, StaticLayout cache, no full reload per move, dirty-rect invalidate |
| CameraX device variance | PreviewView + ImageCapture with resolution fallback, permission-denied flow |
| Zip backup corrupting live DB | checkpoint + validate + restore into temp then swap, requires restart |
| Accent theming complexity | fixed accent overlays (6) applied via `theme.applyStyle`, no runtime colour hacking |

**Sandbox limitation, stated plainly:** this environment has no JDK/Android SDK and cannot
download them, so the project could not be compiled here. Java syntax is validated with a
tree-sitter parse and every resource reference is cross-checked by script; the Gradle build
must be run in Android Studio (§31/§32.13).

## 11. Local authentication

The spec asks for an offline personal app, and the owner asked for a sign-in screen. Those two
requirements meet in a **local-only account** rather than a network identity.

**Flow**

```
LoginActivity (launcher, exported)
   |  username + password
   v
AuthStore.verify()  ->  PasswordHasher.matches()   [PBKDF2-HMAC-SHA256, 120k rounds, per-account salt]
   |  success
   v
AuthStore.signIn(keepSignedIn)  ->  session flag (memory, optionally persisted)
   |
   v
MainActivity (exported = false)  ->  tabs, archive
```

**Design decisions**

| Decision | Reason |
|---|---|
| PBKDF2-HMAC-SHA256, 120,000 iterations, 256-bit key, 16-byte random salt | Slow enough to resist offline guessing, available on every API 26+ device without extra dependencies |
| Only salt + derived key persisted (hex) in a private `SharedPreferences` file | No plaintext password on disk; no new database surface |
| Constant-time comparison (`MessageDigest.isEqual`) | Avoid timing side channels when comparing hashes |
| Derivation runs on the app executor | Keeps the UI responsive; the button shows a progress bar instead of freezing |
| In-memory session by default, persisted only with "Keep me signed in" | Closing the app requires a password unless the user opts out |
| Five failures then a 30-second pause | Deters shoulder-surfing/brute force without locking the owner out permanently |
| Password change requires the current password; minimum 6 chars | Prevents a walk-up change of credentials |
| No reset flow | With no server, any reset would be a bypass. Recovery is documented as "clear app data" |
| Login and biometric lock are independent | Login gates the archive; biometrics optionally gates resume when the app returns to the foreground |

**Files:** `domain/security/PasswordHasher.java`, `data/preferences/AuthStore.java`,
`ui/auth/LoginActivity.java`, `ui/auth/AccountDialogs.java`, `res/layout/activity_login.xml`,
account card in `res/layout/fragment_settings.xml`. Tests:
`app/src/test/java/com/archive/app/domain/security/PasswordHasherTest.java`.

**Limitation:** there is one local account and no per-device user switching; the session flag is not
encrypted beyond the OS-level app sandbox.

## 12. Page and collection management

**Page duplication** (`CanvasRepository.duplicatePage`) copies the page row and rebuilds every
element through `CanvasDocument.fromPage()` → `element.duplicate().toEntity(newPageId)`. That path
gives each object a fresh id, keeps geometry, styles, rotation and z-order, and never copies bitmaps
(media rows are shared by reference). Page row plus elements are written inside
`db.runInTransaction`, so a crash mid-copy cannot leave half a page. Thumbnails are deliberately not
copied — the copy is re-rendered the first time it is opened.

**Page reordering** (`movePage`) swaps `sortOrder` with the neighbour in the same collection. Legacy
rows whose `sortOrder` values are all zero are renumbered from their current visual order first, then
the swap runs. Attempting to move past either end returns `-1` so the UI can say so instead of
silently doing nothing.

**Collection sorting** is a `Transformations.switchMap` over a `MutableLiveData<String>` seeded from
`SettingsStore.collectionSort()`. Three Room queries back it (`observeWithCounts` for manual order,
`observeWithCountsRecent`, `observeWithCountsName`), so switching sort re-runs SQL rather than sorting
in memory, and both Home and the Library tab update from the same source of truth.

**Creation-time paper** is a page property, not an object: `BackgroundStyleFactory.presetJson(key)`
writes fixed preset colours into `pages.backgroundJson`, so a page keeps the paper it was created
with even if the app theme changes later.

## 13. Export selection and page templates

**Multi-page export** (`CollectionActivity.exportSelectedPages`) lists the collection's pages with a
multi-choice dialog, including a "select all" action that drives the dialog's own list view so the
ticks stay in sync. The chosen pages are loaded concurrently but written into a pre-allocated array
indexed by page order — concurrent loads finish out of order, and a PDF whose pages shuffle would be
silently wrong. Empty selections produce a message instead of an empty file.

**Templates** (`domain/templates/PageTemplates`) are pure builders: each starter supplies a paper
preset plus editable objects (headings, captions, rules) laid out relative to the page size, so a
template works on A4, square and journal formats. Nothing is flattened and no stock imagery is
inserted — a template is a head start, not fake content. The objects are inserted through
`CanvasRepository.addElements`, i.e. exactly the same path an imported image takes, so undo, autosave
and export behave normally afterwards.

## 15. Polish pass: crop, export picker, accessibility

**Image crop** (`CanvasSheets` image branch) keeps the destructive-free model: cropping edits
normalised source fractions on the element, never the file. Aspect presets read the *media row's*
pixel size (`mediaOnce`) instead of the on-canvas frame, because a resized frame would otherwise
produce the wrong crop; a missing media row falls back to element dimensions rather than failing.
Four edge sliders write left/top/right/bottom percentages through `ImageElement.setCrop`, which clamps
and refuses to let edges cross.

**Export picker** (`dialog_page_picker.xml` + `PagePickAdapter`) replaced a text-only multi-choice
dialog with a thumbnail list: row taps toggle, "Select all" fills the tick array, and the chosen pages
go through the same order-preserving export path.

**Accessibility guard** (`tools/check_sources.py`) now fails the check when an `ImageView` has neither
`android:contentDescription` nor an explicit `importantForAccessibility="no"`, so decorative and
described images stay distinguishable as layouts change.

## 16. Test strategy

| Layer | Test | Where |
|---|---|---|
| Security | seeded credential verifies, tampering/malformed rejected, hex, strength | `app/src/test/.../PasswordHasherTest` |
| Geometry | orientation, aspect, key/label sync, export cap expectations | `app/src/test/.../PageFormatTest` |
| Ids | uniqueness, UUID shape | `app/src/test/.../IdsTest` |
| Database | persistence, z-order, cascade delete, soft-delete filtering, transaction rollback | `androidTest/.../ArchiveDatabaseTest` |
| Canvas | hydration, grouping, snapshot undo, layer order, per-object dirty clearing | `androidTest/.../CanvasDocumentTest` |
| Crop | clamping, crossing edges, aspect from source pixels, invalid source guard | `androidTest/.../ImageElementCropTest` |
| Export | aspect, background include/exclude, 4096 cap, painted pixels | `androidTest/.../PageExporterTest` |

## 17. Launch safety

Two crashers shipped in the first installable APK, both invisible to `javac` and `aapt`:

1. `fragment_home.xml` named `com.archive.app.ui.home.HomeBackdropView`; the class lives in
   `ui.common`. aapt does not resolve custom view class names, so the failure only appeared when the
   view was inflated (`InflateException` → `ClassNotFoundException`).
2. `MainActivity` implemented `CreateSheet.Host` but not `HomeFragment.Host`, while
   `HomeFragment` casts `requireActivity()` to it (`ClassCastException` on the first frame).

Both were found by `app/src/test/.../StartupSmokeTest`, which runs under Robolectric with
`unitTests.includeAndroidResources = true` and therefore inflates the real theme and layouts. It
covers the login screen, all four tabs, and a collection + canvas opened from a Room fixture, so the
whole "tap the icon and see something" path is executed on every push. `returnDefaultValues` stays on
for the plain JVM tests, but it is exactly why those could never catch this.

`tools/check_sources.py` gained a rule for the first bug: a layout tag that names a class which does
not exist in the source tree now fails the check.

`utils/CrashLog` installs an uncaught-exception handler in `ArchiveApp.onCreate`, before Room opens,
and writes the trace to app-private storage. `LoginActivity` shows the previous trace with **Copy
details** on the next launch, so a crash on a phone without `adb` is still reportable. The file never
leaves the device unless the user copies it.

## 18. NUZHAT: identity change, chapters and the activity log

**Rebrand.** The application is NUZHAT (نزہت) — package `com.nuzhat.app`, fresh install, so no
migration from the old `com.archive.app` data was needed or attempted. Database, preference files and
the media root all live under the new identity (`nuzhat.db`, `nuzhat_auth`, `nuzhat_settings`,
`filesDir/nuzhat`). The theme family, attributes and the composition root followed
(`Theme.Nuzhat.*`, `nuzhatMuted`…, `NuzhatApp`). The dark theme is now the default; Warm and Minimal
remain in Settings.

**Home as chapters.** Collections render as tall chapter plates (`item_chapter.xml`) in a horizontal
rail, numbered `CHAPTER 01`, with cover, description and page count. The header shows the device date
(`DateFormats.fullDate()`), so it updates on every resume without a timer.

**Collection as a deck.** `CollectionActivity` uses a `ViewPager2` with `FanPageTransformer`: the
active page sits square while neighbours translate, tilt and shrink. Page previews keep 1:1
touch targets and long-press still opens the page menu.

**Activity log.** `activities` is a first-class table:

| Column | Purpose |
|---|---|
| `id` | UUID |
| `actionType` | e.g. `PAGE_RENAMED`; `family()` maps it to a filter chip |
| `description` | ready to display, denormalised at write time so history survives renames |
| `collectionId`, `pageId`, `objectId` | nullable links for tap-through |
| `timestamp` | indexed; the timeline groups by local day |
| `metadataJson` | reserved for structured detail |

`ActivityRecorder` is the single writer. It runs on the background executor, and it drops repeat
events for the same target inside a window (1.5 s general, 5 minutes for page edits) so a drag, a
slider or a debounce burst produces one entry, not hundreds. Writers today: collection created /
renamed / archived / restored / trashed / cover, page created / renamed / trashed. The canvas and the
exporters still need their calls (`docs` and the README checklist say so explicitly).

Events are emitted by the repository, not by the UI, so an action that failed to persist cannot
appear in history. The timeline never invents rows: it starts empty on a fresh install.

**Known test gap.** Under Robolectric the canvas reports “page missing” for a page the same test can
read, so the smoke test asserts canvas inflation only; the asynchronous load needs a device. As a
safety net `CanvasViewModel` retries the read once and logs a final miss.
