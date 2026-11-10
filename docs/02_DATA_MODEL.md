# 02 · Model danych

## 1. Room — `FamilyGamesDatabase` (version 1, `exportSchema = true`, schematy w `app/schemas/`)

### CategoryEntity — `categories`
| kolumna | typ | uwagi |
|---|---|---|
| id | Long PK autoGenerate | |
| name | String | max 24 znaki, unikalna (index unique) |
| coverPhotoId | Long? | FK logiczny do photos.id (bez constraintu, żeby usuwanie zdjęcia nie kasowało kategorii) |
| sortOrder | Int | kolejność w S02 i S09 |
| isBuiltIn | Boolean | true dla kategorii z seeda (nieusuwalne, ale można ukryć) |
| isHidden | Boolean | ukryta przed dzieckiem |
| createdAt | Long | epoch ms |

### PhotoEntity — `photos`
| kolumna | typ | uwagi |
|---|---|---|
| id | Long PK autoGenerate | |
| categoryId | Long FK → categories.id | `onDelete = CASCADE`, index |
| filePath | String | względna do `filesDir`, np. `photos/9f1c….jpg`; dla wbudowanych `asset://builtin/dom_kanapa.webp` |
| thumbPath | String | `photos/thumbs/9f1c….jpg` (400 px) |
| aspect | Enum `SQUARE`/`FOUR_THREE` | TypeConverter → String |
| label | String? | podpis karty, max 24 |
| audioPath | String? | `audio/….m4a` |
| useInMemory | Boolean | domyślnie true |
| useInPuzzle | Boolean | domyślnie true |
| sortOrder | Int | |
| createdAt | Long | |

### GameResultEntity — `game_results`
| kolumna | typ | uwagi |
|---|---|---|
| id | Long PK | |
| gameId | String | `MEMORY` / `PUZZLE_SNAP` / `PUZZLE_SWAP` |
| levelKey | String | np. `memory_2x3`, `snap_2x2`, `swap_3x3` |
| categoryId | Long? | dla Memorków |
| photoId | Long? | dla Puzzli |
| stars | Int | 1–3 |
| moves | Int | |
| mistakes | Int | |
| hintsUsed | Int | |
| durationMs | Long | |
| finishedAt | Long | index |

Zapytania kluczowe:
```kotlin
@Query("SELECT levelKey, MAX(stars) AS best FROM game_results WHERE gameId = :gameId GROUP BY levelKey")
fun bestStarsByLevel(gameId: String): Flow<List<LevelBest>>

@Query("SELECT COUNT(*) FROM photos WHERE categoryId = :categoryId AND useInMemory = 1")
fun memoryPhotoCount(categoryId: Long): Flow<Int>
```
Retencja: przy starcie usuwaj wyniki starsze niż 365 dni (`DELETE … WHERE finishedAt < :cutoff`).

## 2. DataStore Preferences — `settings`
| klucz | typ | domyślnie | opis |
|---|---|---|---|
| first_run_completed | Boolean | false | po S12 |
| pin_hash | String? | null | Base64(PBKDF2) |
| pin_salt | String? | null | Base64(16 B) |
| gate_lockout_until | Long | 0 | epoch ms blokady po 3 błędach |
| sound_enabled | Boolean | true | |
| show_card_labels | Boolean | true | |
| level_locks_enabled | Boolean | false | blokada kolejnych poziomów (S02) |
| last_memory_level | String | `memory_2x2` | |
| last_memory_category | Long | -1 | |
| last_puzzle_difficulty | String | `snap_2x2` | |
| update_check_on_start | Boolean | true | |
| update_wifi_only | Boolean | true | |
| update_last_check_at | Long | 0 | |
| update_etag | String? | null | ETag odpowiedzi GitHub API |
| hide_system_bars | Boolean | true | |
| keep_screen_on | Boolean | false | |

## 3. Pliki (`context.filesDir`)
```text
filesDir/
├── photos/{uuid}.jpg          # JPEG q=85, dłuższy bok ≤ 1600 px, EXIF orientacja „wypalona”, EXIF usunięty
├── photos/thumbs/{uuid}.jpg   # JPEG q=80, dłuższy bok 400 px
└── audio/{uuid}.m4a           # AAC 64 kbps mono 44.1 kHz, ≤ 8 s
cacheDir/updates/              # pobrane APK (czyszczone po instalacji lub przy starcie)
```
- `MediaStorage.savePhoto(bitmap, aspect): StoredPhoto` — zapis atomowy (najpierw `.tmp`, potem `renameTo`).
- Usunięcie `PhotoEntity` usuwa pliki (w repozytorium, w jednej operacji z DAO; najpierw DB, potem pliki).
- Zdjęcia **nie** trafiają do galerii ani na zewnętrzną pamięć. Kopia zapasowa: `android:allowBackup="false"` (prywatność; patrz Q5 w docs/11).

## 4. Seed (treści wbudowane)
Przy pierwszym starcie `DefaultContentSeeder` tworzy 3 kategorie `isBuiltIn = true`:
- **Nasz dom** (kanapa, łóżko, lampa, wanna, stół, krzesło, okno, drzwi) — 8 ilustracji
- **Zwierzęta** (kot, pies, krowa, kaczka, koń, świnka, kura, owca) — 8
- **Zabawki** (miś, piłka, klocki, auto, lalka, bębenek, rakieta, kaczuszka) — 8

Ilustracje: proste, płaskie wektory (VectorDrawable → render do bitmapy przy seedzie lub WebP w `assets/builtin/`). Agent może je wygenerować jako proste kształty w stylu z `docs/08`, albo użyć zestawu CC0 (zapisać licencję w `assets/builtin/LICENSE.txt`). Żadnych grafik chronionych prawem autorskim / postaci z bajek.

Seed jest idempotentny (sprawdza `isBuiltIn` przed wstawieniem) i wersjonowany (`seed_version` w DataStore), żeby przyszłe aktualizacje mogły dodać nowe wbudowane kategorie.

## 5. Model domenowy (przykład)
```kotlin
data class Photo(
    val id: Long, val categoryId: Long, val source: PhotoSource,   // File(path) | Asset(path)
    val thumb: PhotoSource, val aspect: Aspect, val label: String?, val audio: String?,
)
enum class MemoryLevel(val rows: Int, val cols: Int, val key: String) {
    L1(2, 2, "memory_2x2"), L2(2, 3, "memory_2x3"), L3(2, 4, "memory_2x4"), L4(3, 4, "memory_3x4");
    val pairs get() = rows * cols / 2
}
```
Coil 3: własny `Fetcher`/`Keyer` nie jest potrzebny — `PhotoSource.File` mapuj na `File`, `PhotoSource.Asset` na `"file:///android_asset/..."`.
