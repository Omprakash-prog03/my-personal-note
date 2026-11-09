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
