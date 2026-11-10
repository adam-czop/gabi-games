# 05 · Gra Puzzle

Dwa tryby: **Snap** (przeciągnij fragment na właściwe pole, dla najmłodszych) i **Swap** (zamieniaj sąsiednie kafelki). Wspólne: wybór obrazka (S05), `WinOverlay` (S04), zapis wyniku.

## 1. Przygotowanie obrazu
- Źródło: pełne zdjęcie (≤ 1600 px). Docelowa proporcja planszy **4:3**. Zdjęcie 1:1 → center-crop do 4:3 (pas z góry i dołu).
- Wczytanie jednego `ImageBitmap` przez Coil (`ImageLoader.execute(...)`, `allowHardware(false)` jeśli rysujemy na Canvas) w ViewModel/Use case → przekazane do UI.
- Fragmenty rysowane z jednego bitmapu: `drawImage(image, srcOffset = IntOffset(c*pw, r*ph), srcSize = IntSize(pw, ph), dstSize = …)`. Nie twórz osobnych bitmap.

## 2. Tryb Snap — `SnapPuzzleScreen`
Konfiguracje: 2×2 (4 el.), 2×3 (6 el.).

### Model
```kotlin
data class SnapPiece(val id: Int, val row: Int, val col: Int, val placed: Boolean)
data class SnapUiState(
    val rows: Int, val cols: Int, val image: ImageBitmap?,
    val pieces: List<SnapPiece>,          // kolejność = kolejność na tacce (losowa)
    val draggingId: Int? = null,
    val hintTargetId: Int? = null,        // pole pulsujące po użyciu żarówki
    val ghostAlpha: Float = 0.22f,
    val hintsUsed: Int = 0, val wrongDrops: Int = 0,
    val won: Boolean = false, val stars: Int? = null,
)
```

### Drag & drop (Compose)
- Każde pole planszy raportuje swoje granice: `Modifier.onGloballyPositioned { slotBounds[id] = it.boundsInRoot() }`.
- Fragment na tacce: `Modifier.pointerInput(piece.id) { detectDragGestures(onDragStart, onDrag, onDragEnd, onDragCancel) }`; pozycja w `Animatable<Offset>`; podczas przeciągania rysowany w warstwie overlay nad planszą (`Box` na całym ekranie z `zIndex`), żeby nie był przycinany przez tackę.
- Podczas przeciągania: skala 0.7 → 1.0 (fragment osiąga rozmiar pola), rotacja −3°, miękki cień podniesiony (docs/08 §4).
- **Magnes**: `distance(center(piece), center(slot[piece.id])) < SNAP_DISTANCE` gdzie `SNAP_DISTANCE = 40.dp` (na planszach 2×2 dopuszczalne 56 dp — stała w `PuzzleTuning`). Gdy warunek spełniony w trakcie przeciągania → pole podświetla się (`Accent`, obrys 3 dp + wypełnienie 14%).
- `onDragEnd`: jeśli w zasięgu → `animateTo(slotTopLeft, spring(dampingRatio = 0.6f, stiffness = 400f))` + dźwięk „click” + `placed = true`; inaczej → `animateTo(trayPosition, tween(300))`, `wrongDrops++` (bez dźwięku błędu).
- Fragmenty umieszczone nie są już przeciągalne.
- Tylko jeden aktywny drag (ignoruj kolejne wskaźniki).

### Podpowiedź (żarówka)
Ghost alpha 0.18 → 0.5 na 2 s, losowy nieumieszczony fragment z tacki „podskakuje”, a jego pole pulsuje. `hintsUsed++`. Cooldown 3 s.

### Punktacja Snap
3★: `hintsUsed == 0`; 2★: `hintsUsed == 1`; 1★: więcej. (`wrongDrops` nie odejmuje gwiazdek — maluch uczy się metodą prób.)

## 3. Tryb Swap — `SwapPuzzleScreen`
Konfiguracje: 2×2, 3×3.

### Model (domain: `SwapBoard`)
```kotlin
/** tiles[pos] = indeks kafelka, który powinien leżeć na pozycji tiles[pos]; rozwiązane gdy tiles[i] == i */
data class SwapBoard(val size: Int, val tiles: List<Int>) {
    fun isSolved() = tiles.withIndex().all { (i, t) -> i == t }
    fun areAdjacent(a: Int, b: Int): Boolean { val (ra, ca) = a / size to a % size; val (rb, cb) = b / size to b % size; return abs(ra - rb) + abs(ca - cb) == 1 }
    fun swap(a: Int, b: Int): SwapBoard { require(areAdjacent(a, b)); return copy(tiles = tiles.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }) }
    fun correctCount() = tiles.withIndex().count { (i, t) -> i == t }
}
```

### Tasowanie — `ShuffleSwapBoardUseCase`
- Fisher–Yates na `0 until n*n` z wstrzykniętym `Random`.
- Warunki akceptacji: plansza nierozwiązana **i** co najmniej `ceil(0.6 × n²)` kafelków na złych miejscach (2×2: min. 3 z 4). Powtarzaj do spełnienia (dla 2×2 i 3×3 zbiega natychmiast).
- Każda permutacja jest rozwiązywalna zamianami sąsiadów (transpozycje sąsiednie generują grupę symetryczną) — nie potrzeba sprawdzania parzystości jak w „piętnastce”.

### Interakcja
- Tap kafelka → `selected = pos` (ramka `Accent` 4 dp), sąsiedzi dostają jasną obwódkę `AccentSoft`.
- Tap sąsiada zaznaczonego → zamiana (animacja 250 ms przesunięcia obu kafelków, `animateIntOffsetAsState`), `moves++`, odznaczenie.
- Tap niesąsiada → zmiana zaznaczenia. Tap zaznaczonego → odznaczenie.
- (Opcja) przeciągnięcie kafelka w stronę sąsiada (`detectDragGestures`, próg 48 dp) = zamiana.
- Kafelek na właściwym miejscu: mała kropka `Accent` w rogu + delikatny dźwięk „ding” w momencie, gdy trafia na miejsce.
- Wygrana gdy `isSolved()` → 400 ms → kafelki „zlewają się” (gap 8 dp → 0, 300 ms) → `WinOverlay`.

### Punktacja Swap
`misplacedAtStart = n² − correctCount(start)`:
3★: `moves ≤ 2 × misplacedAtStart`; 2★: `moves ≤ 4 × misplacedAtStart`; 1★: więcej.

## 4. Zapis wyniku
`GameResultEntity(gameId = PUZZLE_SNAP | PUZZLE_SWAP, levelKey = "snap_2x3" | "swap_3x3", photoId, stars, moves, mistakes = wrongDrops, hintsUsed, durationMs)`.

## 5. Testy wymagane (min.)
`SwapBoard` (sąsiedztwo na krawędziach, swap, isSolved), tasowanie (nigdy rozwiązane, próg 60%, deterministyczność), punktacja obu trybów, logika magnesu jako czysta funkcja `isWithinSnap(pieceCenter, slotCenter, thresholdPx)`.
