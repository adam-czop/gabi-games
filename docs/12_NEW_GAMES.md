# 12 · Nowe gry (po v1.0) — specyfikacja wdrożeniowa

> Dokument dla agenta implementującego. Realizuj **po ukończeniu M0–M9** (v1.0). Obowiązują wszystkie zasady z `CLAUDE.md`, architektura z `docs/01`, model danych z `docs/02`, stonowany design system z `docs/08` (kolor wnoszą zdjęcia, UI neutralne, jeden akcent) i zasady UX dla dziecka z `docs/03 §0`.
> **Makiety:** `mockups/png/S15…S38_*.png` + adnotacje w `mockups/index.html` (sekcja „Nowe gry · makiety v2.1”). Adnotacje są częścią wymagań. Buduj z komponentów wspólnych i tokenów z docs/08, dla każdego ekranu dodaj `@Preview` 1280×800 dp. W raporcie po milestonie dołącz zrzuty ekranu z emulatora do porównania z makietą.

---

## 0. Podsumowanie i kolejność

| Milestone | Gra | `GameId` | Wiek | Typ | Wykorzystuje | Makieta |
|---|---|---|---|---|---|---|
| M10 | *Infrastruktura: menu na wiele gier, widoczność gier, podpowiedzi głosowe* | — | — | — | — | S16, S17 |
| M11 | **Gdzie jest…?** | `WHERE_IS` | 1,5–3 | zadaniowa | zdjęcia, etykiety, głos rodzica | S18 |
| M12 | **A kuku** | `PEEKABOO` | 1–2 | swobodna | zdjęcia, głos rodzica | S19 |
| M12 | **Zdrapka** | `SCRATCH` | 1,5–3 | swobodna | zdjęcia | S20 |
| M13 | **Do koszyczków** | `SORTING` | 2,5–4 | zadaniowa | kategorie, drag & drop z Puzzli | S21 |
| M13 | **Co nie pasuje?** | `ODD_ONE_OUT` | 3–5 | zadaniowa | kategorie | S22 |
| M14 | **Memo dźwiękowe** | `SOUND_MEMO` | 2–4 | zadaniowa | silnik Memorków, dźwięki | S23 |
| M15 | **Bańki** | `BUBBLES` | 1–3 | swobodna | zdjęcia, głos | S24 |
| M15 | **Cymbałki** | `XYLOPHONE` | 1,5–4 | swobodna | zdjęcia | S25 |
| M16 | **Po kolei** | `SEQUENCE` | 3–5 | zadaniowa | nowe sekwencje w panelu rodzica | S26, S15 |
| M17 | **Ile jest?** | `COUNTING` | 3–5 | zadaniowa | zdjęcia | S27 |
| M18 | **Kolorowanki** | `COLORING` | 2,5–5 | swobodna | wbudowane kontury | S28 |
| M19 | **Labirynt** | `MAZE` | 2,5–5 | zadaniowa | zdjęcia na mecie (najpierw „Rodzina”) | S29 |
| M21 | **Cienie** | `SHADOWS` | 2–4,5 | zadaniowa | ilustracje wbudowane, drag & drop z Koszyczków | S30 |
| M22 | **Kulki** | `MARBLES` | 2,5–5 | zadaniowa | brak (kolorowe kulki) | S31 |
| M23 | **Znajdź na obrazku** | `FIND` | 2–5 | zadaniowa + tryb swobodny | 4 wbudowane sceny wektorowe | S32 |
| M24 | **Czego brakuje?** | `MISSING` | 3–5 | zadaniowa | zdjęcia (najpierw „Rodzina”) | S33 |
| M25 | **Połówki** | `HALVES` | 2,5–4,5 | zadaniowa | zdjęcia (najpierw „Rodzina”) | S34 |
| M26 | **Klocki do dziurek** | `SORTER` | 1,5–4 | zadaniowa | brak (kształty) | S35 |
| M27 | **Powtórz za mną** | `ECHO` | 2,5–5 | zadaniowa | zdjęcia na sztabkach, dźwięki Cymbałków | S36 |
| M28 | **Ogródek** | `GARDEN` | 1,5–4 | swobodna | brak (rysunki) | S37 |
| M29 | **Ubierz na pogodę** | `WEATHER` | 2,5–5 | zadaniowa | brak (rysunki) | S38 |

- **Zadaniowa** = ma cel, kończy się `WinOverlay` (S04) i zapisem `GameResultEntity`, punktacja 1–3★ (zawsze min. 1★).
- **Swobodna** = brak wygranej i punktów; dziecko bawi się, ile chce; wyjście przyciskiem Dom. Zapisujemy tylko czas zabawy (`GameResultEntity` z `stars = 0`, `levelKey = "free"`), żeby S14 Postępy mogło to pokazać.
- Kolejność milestone'ów jest rekomendacją — właściciel może ją zmienić; każdy milestone jest niezależny po M10.

---

## M10 · Infrastruktura pod wiele gier

**Makieta:** S16 (menu), S17 (panel „Gry”)

### M10.1 Menu S01 na N gier
Obecne menu mieści 3 kafle. Nowy układ:
- **≤ 3 widoczne gry** → obecny układ (kafle 330×380 dp).
- **4–6 gier** → siatka 3×2, kafle 300×250 dp (ikona 120 dp w kole 150 dp, nazwa 30 sp).
- **> 6 gier** → siatka 3×2 + paginacja poziomym przesunięciem (`HorizontalPager`) i kropki stron w stylu `PairsCounter`. Bez przewijania ciągłego (dziecko gubi się w liście).
- Kafel „Wkrótce” znika, gdy widocznych gier ≥ 3.
- Ikony gier: liniowe, jeden kolor; tło koła ikony na zmianę `AccentSoft` / `MistSoft` (docs/08 §7). **Nie** dodawaj nowych kolorów.

### M10.2 Widoczność gier i profil wieku
- DataStore: `hidden_games: Set<String>` (domyślnie pusty), `child_age_band: String` = `TODDLER` (1–2,5) | `PRESCHOOL` (2,5–5), domyślnie `TODDLER`.
- `GameEntry` dostaje `ageBand: Set<AgeBand>` i `kind: GameKind { TASK, FREE_PLAY }`.
- Menu pokazuje gry: niewidoczne nie, a z niepasującym `ageBand` też nie (chyba że rodzic włączył grę ręcznie — `forced_games: Set<String>`).
- Panel rodzica → nowa pozycja nawigacji **„Gry”** (S17): wybór przedziału wieku (2 przyciski segmentowe) + lista gier z przełącznikami widoczności, nazwą, przedziałem wieku i jednym zdaniem opisu.

### M10.3 Podpowiedzi głosowe (`VoicePromptPlayer`)
> **Zastąpione przez ADR-030 (2026-11-11):** bez nagrań — syntezator czyta 1) własne pytanie zdjęcia (`promptText`), 2) początek zdania z gry + nazwę zdjęcia, 3) brak polskiego głosu → gra bez głosu (wzorzec na górze ekranu). Poniżej pierwotna wersja.

Kilka gier wymaga zdania „Gdzie jest **babcia**?” / „Ile jest **jabłek**?”. Źródła w kolejności priorytetu:
1. **Nagranie pytania** zrobione przez rodzica dla zdjęcia (nowe pole `promptAudioPath`, patrz M10.4).
2. **Nagrany przez rodzica prefiks** („Gdzie jest…”) + **nagranie etykiety** zdjęcia (`audioPath`), odtworzone jedno po drugim z przerwą 150 ms. Prefiksy nagrywane raz w S11 → karta „Głos” (lista zdań-prefiksów, patrz niżej).
3. **Android `TextToSpeech`** z `Locale("pl", "PL")`, tekst = prefiks + `label`. Sprawdź dostępność głosu (`isLanguageAvailable`) przy starcie; brak → pomiń krok.
4. Brak wszystkiego → gra działa bez głosu (wskazanie zdjęcia-wzorca na górze ekranu zamiast pytania; patrz M11).

Prefiksy (klucze w DataStore, ścieżki do plików `audio/prefix_*.m4a`): `prefix_where_is` („Gdzie jest…”), `prefix_how_many` („Ile jest…”), `prefix_bravo` („Brawo!”), `prefix_try_again` („Spróbuj jeszcze raz”).
`VoicePromptPlayer` (singleton) kolejkuje klipy, nie nakłada ich na `SoundPool`, respektuje `sound_enabled` (Q6) i przerywa odtwarzanie przy wyjściu z ekranu.

### M10.4 Migracja bazy v1 → v2
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE photos ADD COLUMN promptAudioPath TEXT")
        db.execSQL("ALTER TABLE photos ADD COLUMN countable INTEGER NOT NULL DEFAULT 0")   // M17
        db.execSQL("CREATE TABLE IF NOT EXISTS sequences (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sequence_steps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sequenceId INTEGER NOT NULL, photoId INTEGER NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(sequenceId) REFERENCES sequences(id) ON DELETE CASCADE, FOREIGN KEY(photoId) REFERENCES photos(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sequence_steps_sequenceId ON sequence_steps(sequenceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sequence_steps_photoId ON sequence_steps(photoId)")
    }
}
```
Wszystkie kolumny/tabele w jednej migracji (nawet jeśli gry powstają później) — mniej migracji na urządzeniach. Test migracji `MigrationTestHelper` obowiązkowy.
S10 Edycja zdjęcia: nowa sekcja „Pytanie głosowe (opcjonalne)” pod nagraniem etykiety — ten sam komponent nagrywania, podpowiedź „np. Gdzie jest babcia?”.

### M10.5 Wspólne elementy gier
- `TaskGameScaffold(onHome, onRestart, progress)` i `FreePlayScaffold(onHome)` — wspólny górny pasek (Dom po lewej, Restart/brak po prawej, kropki postępu na środku).
- `RoundEngine<T>` (domain) — generyczny silnik gier rundowych: N rund, każda z poprawną odpowiedzią i dystraktorami, liczniki `mistakes`, blokada wejścia na czas animacji, `Won` po ostatniej rundzie. Używany przez M11, M13 (Co nie pasuje), M17.
- `RoundScoring`: 3★ `mistakes == 0`, 2★ `mistakes ≤ rounds / 2`, 1★ więcej.
- Losowanie dystraktorów: wyłącznie spośród zdjęć z **tej samej** kategorii (chyba że gra mówi inaczej), bez powtórzeń w rundzie, preferuj zdjęcia z różnymi etykietami (różne `label`).

**Akceptacja M10:** menu z 2 grami wygląda jak dotąd; po zarejestrowaniu 6 testowych wpisów pokazuje siatkę 3×2, a 8 — paginację; ukrycie gry w S11 usuwa ją z menu; migracja v1→v2 przechodzi test; `VoicePromptPlayer` odtwarza „prefiks + etykieta” z nagrań testowych i spada na TTS, gdy ich brak.

---

## M11 · Gdzie jest…? (`WHERE_IS`)

**Makieta:** S18

**Cel rozwojowy:** rozumienie mowy, rozpoznawanie bliskich osób i przedmiotów, wskazywanie.

**Przebieg rundy**
1. Na ekranie 2–4 zdjęcia (karty jak w Memorkach, bez podpisów) rozmieszczone w rzędzie (2–3) lub siatce 2×2 (4).
2. Odtwarza się pytanie (M10.3). Nad zdjęciami duży przycisk głośnika 96 dp — ponowne odtworzenie pytania.
3. Tap w poprawne zdjęcie → ramka `Accent`, karta lekko rośnie (1.06), odtwarza się nagranie etykiety (`audioPath`, „To jest babcia”) lub `sfx_match`; po 1200 ms następna runda.
4. Tap w złe → karta delikatnie „kiwa się” (rotacja ±4°, 300 ms), `sfx_soft`, `mistakes++`, pytanie odtwarza się ponownie. **Po 2 błędach** w rundzie poprawna karta pulsuje (podpowiedź).

**Poziomy** (`levelKey`): `where_2` (2 zdjęcia, 5 rund), `where_3` (3 zdjęcia, 6 rund), `where_4` (4 zdjęcia, 8 rund). Wybór poziomu: ekran jak S02 z 3 kaflami + pasek kategorii.

**Wymagania danych:** zdjęcie może być celem rundy, jeśli ma `label` **lub** `promptAudioPath`. Kategoria potrzebuje ≥ (liczba zdjęć w rundzie) takich zdjęć, inaczej poziom wyszarzony (jak `NotEnoughPhotos` w S02).
**Tryb bez głosu** (brak nagrań i TTS): na górze ekranu mały „wzorzec” — to samo zdjęcie w ramce 160 dp; zadanie zmienia się w „znajdź takie samo”. Działa, ale w S11 przy grze pokaż informację „Nagraj pytania głosowe, aby gra mówiła”.
**Losowanie:** każde zdjęcie-cel max raz w grze; pozycja poprawnej odpowiedzi losowa, ale nie ta sama 3 razy z rzędu.
**Testy:** generator rund (unikalność celów, poprawna odpowiedź zawsze obecna, brak 3× tej samej pozycji, dystraktory z tej samej kategorii), scoring, fallback źródła głosu.

---

## M12a · A kuku (`PEEKABOO`) — swobodna

**Makieta:** S19

**Cel:** stałość obiektu, przyczyna–skutek; dla najmłodszych.
- Ekran: 1 lub 2 duże „okienka” (domyślnie 1; przełącznik w ustawieniach gry) z zamkniętymi drzwiczkami/zasłonką narysowaną w neutralnych odcieniach (`Bg2`, `Line2`, uchwyt `Ink3`).
- Tap → drzwiczki otwierają się (animacja `rotationY` 0→-75° z `cameraDistance`, 500 ms) → pokazuje się losowe zdjęcie z widocznych kategorii → odtwarza się `audioPath` (lub TTS etykiety, lub `sfx_match`) + delikatny „a kuku” (opcjonalnie nagrany prefiks `prefix_peekaboo`).
- Po 3 s drzwiczki same się zamykają; kolejne zdjęcie inne niż ostatnie 5.
- Tap w otwarte okienko → zamyka od razu.
- Brak wygranej, brak punktów, brak licznika.

## M12b · Zdrapka (`SCRATCH`) — swobodna

**Makieta:** S20

**Cel:** motoryka mała, ciekawość.
- Pełnoekranowe zdjęcie (w ramce `Surface`, 4:3) przykryte warstwą „mgiełki” jak srebrna zdrapka: `Line2` z kropkami `Ink3` i kilkoma białymi błyskami — wyraźnie ciemniejsza od tła i ramki (ADR-029).
- Palec ściera mgiełkę: `Canvas` z warstwą maski w offscreen `graphicsLayer(compositingStrategy = Offscreen)`, ścieżka rysowana z `BlendMode.Clear`, pędzel 64 dp z miękką krawędzią.
- Licz odsłonięty procent na siatce 32×24 komórek (nie po pikselach; pędzel okrągły także w pionie, liczone wszystkie punkty szybkiego pociągnięcia). Dopiero przy ≥ 98% (praktycznie wszystko, ADR-029) → reszta mgiełki znika animacją (400 ms), odtwarza się `audioPath`/etykieta, po 2 s przycisk „następne” (strzałka 96 dp) — lub automatycznie po 5 s.
- Multi-touch dozwolony (do 3 palców ściera jednocześnie — tu to zaleta).
- Opcja dla rodzica: „pokazuj kontur pod mgiełką” (ghost alpha 0.15) dla najmłodszych.

---

## M13a · Do koszyczków (`SORTING`)

**Makieta:** S21

**Cel:** kategoryzacja.
- Na dole ekranu 2 (poziom `sort_2`) lub 3 (`sort_3`) koszyki — białe karty 280×200 dp z okładką kategorii (miniatura 96 dp w kole) i podpisem dla rodzica.
- Na górze „tacka” z 6 (`sort_2`) / 9 (`sort_3`) zdjęciami pomieszanymi z wybranych kategorii.
- Przeciąganie: **reużyj** `DragDropBoard` z Puzzli (overlay, skala, cień, `onGloballyPositioned` celów). Upuszczenie, gdy środek zdjęcia jest w prostokącie koszyka.
  - poprawny koszyk → zdjęcie „wpada” (scale 1→0.4 + fade, 300 ms), w koszyku rośnie licznik kropek, `sfx_match`;
  - zły → zdjęcie wraca na tackę (`tween(300)`), `sfx_soft`, `mistakes++`, właściwy koszyk delikatnie pulsuje przez 1 s.
- Koniec, gdy tacka pusta → `WinOverlay`, scoring `RoundScoring` (rundy = liczba zdjęć).
- **Wybór kategorii:** automatycznie 2–3 widoczne kategorie z ≥ 3 zdjęciami, losowo; rodzic może wskazać pary kategorii w ustawieniach gry (opcjonalnie, później).

## M13b · Co nie pasuje? (`ODD_ONE_OUT`)

**Makieta:** S22

- Runda: 4 zdjęcia w rzędzie — 3 z kategorii A, 1 z kategorii B. Tap w „intruza”.
- Pytanie głosowe opcjonalne (prefiks `prefix_odd_one` „Co tu nie pasuje?” lub TTS).
- Poprawnie → intruz odsuwa się w górę i znika, pozostałe 3 przysuwają się do siebie (pokazanie grupy), `sfx_match`. Źle → kiwnięcie + `sfx_soft`.
- Poziomy: `odd_3` (3 zdjęcia: 2+1, 5 rund), `odd_4` (4: 3+1, 6 rund). Silnik: `RoundEngine`.
- Wymaga ≥ 2 widocznych kategorii; kategorie losowane parami tak, żeby nie powtarzać tej samej pary w kolejnych rundach.

---

## M14 · Memo dźwiękowe (`SOUND_MEMO`)

**Makieta:** S23

- **Reużyj** `MemoryEngine` i `MemoryScreen` — różnica tylko w zawartości kart: para = **karta-dźwięk** (tył jak zwykle; przód: ikona głośnika 80 dp w kole `AccentSoft`, tap odtwarza dźwięk) + **karta-zdjęcie**.
- Dźwięk dla zdjęcia: `audioPath` (głos rodzica) **albo** wbudowany dźwięk z mapy `builtinSoundFor(photo)` dla zdjęć wbudowanych (kot → miau, pies → hau, krowa, kaczka, koń, świnka, kura, owca, auto, bębenek…). Pliki OGG CC0 w `res/raw/animal_*.ogg`, licencje w `res/raw/LICENSES.md`.
- Zdjęcia bez dźwięku nie biorą udziału. Poziomy jak Memorki, ale max 3×4 → 6 par; wymagane ≥ `pairs` zdjęć z dźwiękiem.
- Odkrycie karty-dźwięku zawsze odtwarza dźwięk; przy dopasowaniu odtwarza się ponownie + ramka `Accent`.
- Rozszerzenie `MemoryEngine`: `pairKey` bez zmian; `CardUi` dostaje `face: CardFace { Photo(image), Sound(audio) }`. Testy istniejące muszą przejść bez zmian.

---

## M15a · Bańki (`BUBBLES`) — swobodna

**Makieta:** S24

- Z dołu ekranu powoli unoszą się bańki (średnica 140–220 dp, prędkość 40–70 dp/s, lekki ruch sinusoidalny ±20 dp), max 6 na ekranie, nowa co 1,2–2 s.
- Wnętrze bańki: okrągła miniatura zdjęcia (alpha 0.9) + delikatny połysk (biały łuk alpha 0.5) i obrys `Line` — **bez** tęczowych efektów.
- Tap → bańka pęka (8 małych kropel w kolorze `Line2`, 250 ms) + `sfx_pop` + co 3. pęknięcie odtwarza etykietę zdjęcia (żeby nie było kakofonii).
- Animacja w jednym `Canvas` z `withFrameNanos` (nie osobne Composable na bańkę); hit-test po odległości od środka.
- Respektuj „Usuń animacje”: bańki stoją w miejscu, pojawiają się i znikają przez fade.

## M15b · Cymbałki (`XYLOPHONE`) — swobodna

**Makieta:** S25

- 8 pionowych „sztabek” w poprzek ekranu, każda z okrągłym zdjęciem (twarze rodziny — preferuj kategorię „Rodzina”, inaczej dowolne).
- Sztabki w neutralnych odcieniach od `Bg2` do `Line2` (stopniowane wysokością, nie kolorem), zdjęcie w kole 96 dp.
- Tap/przesunięcie palcem po sztabkach → nuty skali **pentatonicznej** C-dur (C D E G A C' D' E') — każda kombinacja brzmi dobrze. Próbki marimby/ksylofonu CC0, `SoundPool` z max 8 strumieniami, multi-touch dozwolony.
- Sztabka przy uderzeniu: przesunięcie w dół o 4 dp + zdjęcie scale 1.08, 120 ms.
- Opcja: „Zagraj melodię” — przycisk odtwarza prostą melodię (np. „Wlazł kotek na płotek” — melodia ludowa, domena publiczna) z podświetlaniem kolejnych sztabek; dziecko może powtarzać.

---

## M16 · Po kolei (`SEQUENCE`)

**Makieta:** S26 (gra), S15 (panel „Sekwencje”)

**Panel rodzica (nowy ekran S15 „Sekwencje”, w bocznej nawigacji pod „Kategorie”). Boczna nawigacja rodzica od M10: Zdjęcia, Kategorie, Sekwencje, Postępy, Gry, Ustawienia.**
- Lista sekwencji (nazwa, 3–5 kroków, miniatury). Tworzenie: nazwa (np. „Poranek”) → wybór 3–5 zdjęć z dowolnych kategorii (siatka jak S09, zaznaczanie z numerkami) → zmiana kolejności przeciąganiem → zapis do `sequences` / `sequence_steps`.
- Wbudowana przykładowa sekwencja w seedzie: brak (zależy od zdjęć rodziny) — pokaż pusty stan z instrukcją.

**Gra:**
- Na górze ponumerowane puste sloty (3–5) połączone cienką linią (`Line2`) — oś czasu. Na dole pomieszane zdjęcia.
- Przeciąganie (reużyj `DragDropBoard`) do slotów: poprawny slot → snap + `sfx_snap`; zły → powrót, `mistakes++`. Tryb łatwy (domyślnie dla `TODDLER`): sloty wypełnia się po kolei — aktywny jest tylko pierwszy wolny, podświetlony.
- Po ułożeniu: kroki odtwarzają się po kolei (każde zdjęcie lekko rośnie + etykieta głosem) → `WinOverlay`.
- Gra widoczna w menu tylko, gdy istnieje ≥ 1 sekwencja.

---

## M17 · Ile jest? (`COUNTING`)

**Makieta:** S27

- Runda: na ekranie N kopii tego samego zdjęcia (małe kafelki 140 dp rozmieszczone losowo bez nakładania — algorytm odrzucania z siatką pomocniczą), N ∈ 1–3 (`count_3`), 1–5 (`count_5`).
- Pytanie: prefiks `prefix_how_many` + etykieta (lub TTS „Ile jest jabłek?” — **bez** odmiany liczebnikowej, użyj formy z etykiety + uwaga w S11, że rodzic może nagrać pytanie sam).
- Na dole cyfry 1…max jako przyciski 112 dp (Fredoka 56 sp) **z kropkami pod cyfrą** (dziecko, które nie zna cyfr, liczy kropki).
- Tap w kafelek zdjęcia → kafelek dostaje kolejny numer (liczenie wspomagane), cichy „tuk” z rosnącą wysokością tonu.
- Poprawna odpowiedź → kafelki podskakują po kolei, głos/TTS liczy „jeden, dwa, trzy” (nagrane prefiksy `num_1`…`num_5` lub TTS). Silnik `RoundEngine`, 5 rund.
- Zdjęcia do liczenia: z flagą `countable = 1` (przełącznik w S10 „Nadaje się do liczenia” — przedmioty, nie osoby); dla wbudowanych: owoce/zabawki ustawione w seedzie (podbij `seed_version`).

---

## M18 · Kolorowanki (`COLORING`) — swobodna

**Makieta:** S28

- **Kontury wbudowane** (nie generujemy ze zdjęć — wynik jest słaby i kosztowny): 12 prostych wektorów (dom, kot, pies, auto, kwiatek, słońce, ryba, motyl, drzewo, miś, balon, rakieta) jako `VectorDrawable`/SVG z zamkniętymi obszarami; grube kontury 6 dp `Ink`.
- **Wypełnianie tapem** (domyślnie dla `TODDLER`): tap w obszar → wypełnienie wybranym kolorem (flood fill na bitmapie maski obszarów 1024 px, wykonywany na `Dispatchers.Default`; obszary można też przygotować jako osobne ścieżki w SVG — wtedy hit-test po ścieżce, bez flood fill, **preferowane**).
- **Malowanie palcem** (opcja dla `PRESCHOOL`): pędzel 24/48 dp pod konturem (kontur rysowany na wierzchu).
- Paleta: 8 kolorów farb — to **jedyne** miejsce w aplikacji z pełniejszymi kolorami (to treść, nie UI); stonowane, „kredkowe” odcienie, nie neony: `#E07A5F #F2CC8F #81B29A #3D85C6 #9C89B8 #F4A6A6 #8D6E63 #3B3936`. Kubeczki farb 72 dp w rzędzie po prawej, wybrany z obrysem `Accent` 4 dp.
- Przyciski: cofnij (ostatnie 10 kroków), wyczyść (przytrzymanie 1,5 s — żeby dziecko nie skasowało przypadkiem), następny obrazek.
- Gotowe rysunki zapisują się automatycznie w `filesDir/drawings/` (max 50, najstarsze usuwane); rodzic widzi je w S14 Postępy → „Rysunki” (podgląd, usuń). Eksport poza tablet — backlog.

---

## M19 · Labirynt (`MAZE`)

**Makieta:** S29 · **Kod:** `domain/game/maze/` (`Track`, `Tracer`, `TrackFactory`), `presentation/games/maze/Maze.kt`

Ćwiczenie grafomotoryczne „prowadź po śladzie”: dziecko kładzie palec na piłeczce i prowadzi ją bez odrywania białym korytarzem do zdjęcia na mecie.

- **Plansza** 1200 × 640 jednostek (≈ dp na tablecie 10"), tło `Bg2`, korytarze białe z krawędzią `Line2`. Korytarz to graf odcinków: ścieżka = łańcuch krótkich odcinków, labirynt = drzewo odcinków między środkami komórek.
- **Ruch:** piłeczkę chwyta dotknięcie w promieniu 0,9 szerokości korytarza od jej środka. Piłeczka idzie za palcem tylko w korytarzu i nigdy nie przeskakuje: przy każdym ruchu szuka punktu najbliższego palcowi wyłącznie w części korytarza osiągalnej w 1,2 szerokości (po korytarzu), więc palec przeciągnięty przez ścianę do sąsiedniego zwoju jej nie przesunie.
- **Ześlizgnięcie:** palec dalej niż pół szerokości + 0,3 szerokości od korytarza → piłeczka zostaje, lekko się kołysze, liczy się ześlizgnięcie. Bez dźwięku i bez czerwieni. Dotknięcie piłeczki (także bez odrywania palca, w promieniu 0,4 szerokości) znów ją podnosi. Dotknięcie obok piłeczki → piłeczka podskakuje, pokazując, gdzie zacząć.
- **Nagrody po drodze:** miękki ślad za piłeczką (`Accent` 35%), co 140 jednostek cichy „tuk” coraz wyżej.
- **Meta:** okrągłe zdjęcie (najpierw kategoria „Rodzina”, inaczej wszystkie widoczne, bez zdjęć — gwiazdka). Po dojściu zdjęcie rośnie, obwódka `Accent`, lektor mówi etykietę; po 1,6 s następna ścieżka. Gra = 3 ścieżki, potem `WinOverlay` i zapis `GameResultEntity` (`moves` = ścieżki, `mistakes` = ześlizgnięcia).
- **Gwiazdki** (`TraceScoring`): ześlizgnięć ≤ liczba ścieżek → 3★, ≤ 3 × ścieżki → 2★, więcej → 1★.
- **Poziomy** (S02, kafel z miniaturą najtrudniejszego kształtu poziomu):

| Poziom | `levelKey` | Kształty (po kolei) | Korytarz |
|---|---|---|---|
| 1 Prosta droga | `maze_1` | linia, łuk, fala | 150 |
| 2 Zakręty | `maze_2` | fala, łuk, zygzak | 130 |
| 3 Ślimak | `maze_3` | zygzak, wąż, ślimak (od środka na zewnątrz) | 115 |
| 4 Labirynt | `maze_4` | 3 losowe labirynty 6 × 3 z rozgałęzieniami (start lewy górny, meta prawy dolny) | 100 |

- Kształty 1–3 losowo odbijane w poziomie i pionie; labirynt kopany losowym przejściem w głąb (DFS) z wstrzykniętym `Random`.
- Testy: `MazeDomainTest` — dojście do mety, brak przeskoku przez ścianę, ześlizgnięcie i ponowny chwyt, labirynt jako drzewo łączące wszystkie komórki, ściany między zwojami (≥ 1,15 szerokości korytarza), zdjęcie mety mieści się na planszy.

---

## M21 · Cienie (`SHADOWS`)

**Makieta:** S30 · **Kod:** `domain/game/shadow/Shadows.kt` (`ShadowDealer`, `ShadowBoard`, `Silhouette`), `presentation/games/shadow/Shadows.kt`

Na górze 2–4 cienie, na dole te same obrazki w innej kolejności; dziecko przeciąga obrazek na jego cień (komponent `DraggableItem` z Koszyczków).

- **Treść:** wyłącznie ilustracje wbudowane z widocznych kategorii (dom, zwierzęta, zabawki, pojazdy) — zdjęcia rodzica nie mają czystego tła, z którego da się wyciąć cień.
- **Cień** wycinany w aplikacji z miniatury 400 px (ADR-031): wypełnianie od krawędzi przez piksele podobne do tła (kolory tła kategorii, jasna szarość tylko w pasie ziemi — droga, cień pod obiektem), z uszczelnieniem szczelin 1–2 px w konturze; prawie biała barwa nigdy nie jest tłem. Kolor cienia `Ink2`. Pamięć podręczna 40 cieni.
- **Trafienie:** obrazek zostaje na cieniu, ramka `Accent`, `Sfx.MATCH`, lektor mówi nazwę. **Pudło:** obrazek wraca na dół (300 ms), `Sfx.SOFT`; po 2 pomyłkach z tym samym obrazkiem właściwy cień pulsuje.
- Tacka nigdy nie powtarza kolejności cieni. Obrazki w grze nie powtarzają się, dopóki są nowe; łatwe poziomy mieszają kategorie (różne kształty).
- **Poziomy:** `shadow_2` (2 cienie), `shadow_3` (3 cienie), `shadow_4` (4 cienie z jednej kategorii — podobne kształty). 3 rundy, potem `WinOverlay`; gwiazdki `RoundScoring` (pomyłki na wszystkie obrazki gry).
- Testy: `ShadowDomainTest` (rozdawanie, plansza, algorytm cienia na danych syntetycznych) i `BuiltinSilhouetteTest` — cień każdej z 32 ilustracji (Robolectric, prawdziwe piksele): nie dotyka krawędzi, zajmuje 8–70 % obrazka, ma sensowny rozmiar; zapisuje arkusz kontrolny `app/build/reports/shadows-sheet.png`.

---

## M22 · Kulki (`MARBLES`)

**Makieta:** S31 · **Kod:** `domain/game/marble/Marbles.kt` (`MarblePath`, `MarbleLevel`, `MarbleEngine`), `presentation/games/marble/Marbles.kt`

Spokojna gra typu „marble shooter” dla 2,5–5 lat, z własnym, stonowanym wyglądem.

- **Plansza** 1200 × 640 jednostek, tło `Bg2`, tor to jasny rowek; na końcu toru domek. Działko na dole pośrodku (koło 124 j.), obok mała kulka „następna”.
- **Strzał:** stuknięcie w planszę — działko obraca się i strzela w tę stronę (1300 j./s); nie ma celowania przeciąganiem. Jednocześnie leci najwyżej jedna kulka. Pudło (kulka wylatuje poza planszę) wraca do działka bez kary. **Stuknięcie w działko** zamienia bieżącą kulkę z następną (odp. właściciela).
- **Łańcuch** wjeżdża szybko do ~22 % toru, potem toczy się wolno (16–22 j./s). Pcha tylko tylny segment; po zbiciu przerwa domyka się szybciej (160 j./s), a segment przed przerwą cofa się, gdy po obu stronach przerwy jest ten sam kolor. Zetknięcie tego samego koloru może zbić znowu — reakcja łańcuchowa z coraz wyższym „pyk”.
- **Nie da się przegrać:** łańcuch zatrzymuje się przed domkiem i czeka; wygrana = zbicie wszystkich kulek → `WinOverlay`.
- **Kolory w działku** tylko spośród kulek obecnych na torze (odp. właściciela); po zbiciu ostatniej kulki koloru działko podmienia go od razu.
- **Startowy łańcuch:** serie o jedną krótsze niż zbicie (na poziomie 1 pojedyncze kulki na przemian), sąsiednie serie w różnych kolorach — każdy celny strzał coś zbija.
- **Kolory:** 4 farby z Kolorowanek (`Paints[0..3]`) + biały znak na kulce (kropka, romb, trójkąt, plus) dla dzieci słabiej rozróżniających barwy (ADR-032).

| Poziom | `levelKey` | Kolory | Kulki | Zbija | Tor | Prędkość |
|---|---|---|---|---|---|---|
| 1 | `marbles_1` | 2 | 12 | 2 | łuk | 16 |
| 2 | `marbles_2` | 3 | 18 | 3 | fala | 18 |
| 3 | `marbles_3` | 3 | 24 | 3 | wąż, 2 rzędy | 20 |
| 4 | `marbles_4` | 4 | 30 | 3 | wąż, 3 rzędy | 22 |

- **Gwiazdki** (`Marbles.stars`): kulki dołożone do łańcucha bez zbicia ≤ ¼ liczby kulek → 3★, ≤ ½ → 2★, więcej → 1★; pudła się nie liczą.
- Testy: `MarbleEngineTest` — startowy łańcuch, zbicie i dołożenie, pudło bez kary, reakcja łańcuchowa (combo 1 → 2), zatrzymanie przed końcem przez 10 min gry, kolory działka, wygrana, tory na planszy i z dala od działka.

---

## M23 · Znajdź na obrazku (`FIND`)

**Makieta:** S32 · **Kod:** `domain/game/find/` (`Scenes`, `FindGame`), `presentation/games/find/` (`FindViewModel`, `SceneRenderer`, `FindTexts`)

Duży obrazek (wieś, miasto, kuchnia, plac zabaw) i polecenie lektora „Gdzie jest krowa?” — dziecko stuka w tę rzecz.

- **Sceny** wbudowane, rysowane wektorowo w kodzie (prostokąty, elipsy, wielokąty, linie) w palecie ilustracji wbudowanych z konturem `Ink` 5 j.; 8–10 rzeczy na scenę, oznaczonych jako duże lub małe. Teksty (nazwa i pytanie każdej rzeczy) w `strings_games.xml`; zwierzęta i pojazdy mają też odgłos.
- **Dotyk:** wygrywa rzecz najbardziej z przodu pod palcem; gdy nic, liczy się rzecz w promieniu 18 j. (małe paluszki). Tło (niebo, trawa, ulica) nic nie robi.
- **Tryb zadaniowy** (`find_big`: 5 pytań o duże rzeczy, `find_all`: 5 pytań, w tym co najmniej 2 małe): u góry głośnik (powtarza pytanie), karta „tego szukamy” z samą rzeczą (gra działa też bez głosu) i kropki postępu. Trafienie: rzecz podskakuje, `Sfx.MATCH`, lektor mówi nazwę (+ odgłos); po 1,5 s następne pytanie. Pudło: dotknięta rzecz kiwa się, `Sfx.SOFT`, pytanie wraca; po 2 pudłach szukana rzecz pulsuje. Gwiazdki `RoundScoring`. Scena losowa, inna niż poprzednia.
- **Tryb odkrywania** (`find_explore`, kafel bez gwiazdek): gra swobodna — dotknij czegokolwiek, rzecz podskakuje, lektor mówi nazwę i odgłos; strzałka w prawym górnym rogu zmienia obrazek; zapisywany tylko czas zabawy.
- Testy: `FindDomainTest` (każda rzecz w każdej scenie da się stuknąć, granice planszy, pudło i podpowiedź, poziomy), `SceneRenderTest` (teksty dla każdej rzeczy, render scen do `app/build/reports/find-scenes.png`).

---

## M24 · Czego brakuje? (`MISSING`)

**Makieta:** S33 · **Kod:** `domain/game/round/MissingGenerator.kt`, `presentation/games/missing/Missing.kt` (na `RoundGameViewModel`)

Propozycja „Kto zniknął?” z listy pomysłów — nazwana „Czego brakuje?”, bo na zdjęciach bywają też przedmioty.

- Runda: na scenie (`Bg2`) 3 lub 4 zdjęcia (najpierw kategoria „Rodzina”), lektor „Popatrz uważnie!”, po 2,6 s zjeżdża zasłonka `Mist` (600 ms), po jej podniesieniu jednego zdjęcia brakuje — w jego miejscu pusta ramka `Line2`. Lektor „Czego brakuje?”; dopiero wtedy pod sceną pojawiają się 3 karty i głośnik.
- Opcje: brakujące zdjęcie + 2 zdjęcia spoza sceny (każda odpowiedź jest „uczciwa” — dziecko musi pamiętać). Puste miejsce i właściwa karta nie trafiają 3 razy z rzędu w to samo miejsce (`PositionPicker`).
- Trafienie: zdjęcie wraca na scenę, lektor mówi jego nazwę. Pudło: karta się kiwa, pytanie wraca; po 2 pudłach właściwa karta pulsuje. 5 rund, gwiazdki `RoundScoring`.
- Poziomy `missing_3`, `missing_4`; potrzeba odpowiednio 5 i 6 zdjęć.
- `RoundUiState` dostał pola sceny (`stage`, `stageGap`, `curtain`) — używa ich tylko ta gra.

---

## M25 · Połówki (`HALVES`)

**Makieta:** S34 · **Kod:** `domain/game/round/HalvesGenerator.kt`, `presentation/games/halves/Halves.kt` (na `RoundGameViewModel`)

- U góry lewa połowa zdjęcia (najpierw kategoria „Rodzina”, kadr kwadratowy) i puste miejsce w ramce `Line2`; obok głośnik. Lektor: „Gdzie jest druga połówka?”.
- Na dole prawe połówki: właściwa i innych zdjęć — najpierw o innych nazwach (dwie „babcie” nie konkurują). Cele nie powtarzają się w grze; właściwa odpowiedź nie stoi 3 razy z rzędu w tym samym miejscu.
- Trafienie: druga połowa wskakuje na miejsce, całe zdjęcie w ramce `Accent`, lektor mówi nazwę. Pudło: połówka się kiwa; po 2 pudłach właściwa pulsuje. 5 rund, gwiazdki `RoundScoring`.
- Poziomy `halves_2`, `halves_3` (2 i 3 połówki do wyboru).

---

## M26 · Klocki do dziurek (`SORTER`)

**Makieta:** S35 · **Kod:** `domain/game/sorter/Sorter.kt` (`SorterDealer`, `SorterBoard`), `presentation/games/sorter/Sorter.kt`

Cyfrowy sortownik kształtów dla najmłodszych (od 1,5 roku); bez zdjęć.

- Pokrywka (`Bg2`, obrys `Line2`) z 2–4 dziurkami (`Ink` 55 %), pod nią kolorowe klocki (farby `Paints[0..3]`, jak kulki — ADR-032) w innej kolejności niż dziurki; każdy klocek ma inny kolor. Kształty: kółko, kwadrat, trójkąt, gwiazdka, serduszko, księżyc (ścieżki Compose).
- Przeciąganie z Koszyczków (`DraggableItem`). Dobra dziurka: klocek „wpada” (`SNAP` + ciche `POP`), dziurka zostaje wypełniona, lektor mówi nazwę kształtu. Zła: klocek wraca (`SOFT`); po 2 pomyłkach z tym klockiem właściwa dziurka pulsuje z obrysem `Accent`. Na start lektor: „Włóż klocki do dziurek!”.
- Poziomy: `sorter_2` (2 z: kółko, kwadrat, trójkąt), `sorter_3` (3 z czterech, + gwiazdka), `sorter_4` (4 z sześciu). 3 rundy; kolejna runda ma inny zestaw, gdy poziom ma zapasowe kształty. Gwiazdki `RoundScoring` (pomyłki na wszystkie klocki).

---

## M27 · Powtórz za mną (`ECHO`)

**Makieta:** S36 · **Kod:** `domain/game/echo/Echo.kt` (`EchoGame`), `presentation/games/echo/Echo.kt` (sztabka `XylophoneBar` z Cymbałków)

- Kilka sztabek Cymbałków ze zdjęciami rodziny; indeks sztabki = ten sam dźwięk i wysokość co w Cymbałkach.
- Lektor „Posłuchaj melodii!”; sztabki podświetlają się i grają po kolei (520 ms + 180 ms przerwy). Potem „Teraz ty!”, a znak u góry zmienia się z głośnika w nutkę (dotknięcie = posłuchaj jeszcze raz).
- Dobrze powtórzona melodia: dźwięk gwiazdki i melodia dłuższa o 1 nutę (ta sama nuta najwyżej 2 razy z rzędu). Pomyłka: `SOFT` i ta sama melodia od początku — nigdy krótsza, bez przegranej, liczy się do gwiazdek (`RoundScoring` na długość celu).
- Poziomy: `echo_3` (sztabki C E G, do 3 nut), `echo_4` (C E G A, do 4), `echo_5` (C E F G A, do 5). Kropki u góry = długość melodii.

---

## M28 · Ogródek (`GARDEN`) — swobodna

**Makieta:** S37 · **Kod:** `domain/game/garden/Garden.kt`, `presentation/games/garden/Garden.kt`

- Plansza 1200 × 640: niebo ze słońcem zasłoniętym chmurką, ziemia z 4 grządkami, konewka po lewej, wazon po prawej (wszystko rysowane na Canvasie, kolory kwiatków = farby).
- Dotknięcie pustej grządki: nasionko (`TAP`). Konewka: kropelki nad wszystkimi grządkami (`SNAP`), po 0,7 s każda zasiana roślinka rośnie o krok — kiełek, listki, pąk (`STAR`). Chmurka: odsuwa się na 4 s, słońce otwiera pąki (`MATCH`, lektor „Kwiatki zakwitły!”). Dotknięcie kwiatka: kwiatek do wazonu (do 9 ostatnich), grządka wolna. Dotknięcie rosnącej roślinki tylko cicho szeleści (`SOFT`).
- Gra swobodna: bez celu i gwiazdek, zapis czasu (`FreePlayClock`). Wiek 1,5–4 lata.

---

## M29 · Ubierz na pogodę (`WEATHER`)

**Makieta:** S38 · **Kod:** `domain/game/weather/Weather.kt` (`WeatherDealer`, `Dresser`), `presentation/games/weather/Weather.kt`

- Po lewej okno z pogodą (słońce / deszcz z chmurką / śnieg z chmurką i białą ziemią) — każda pogoda raz w grze, w losowej kolejności. Po prawej postać dziecka (strefa upuszczenia), na dole rzeczy (karty 150 dp) do przeciągnięcia.
- Lektor: „Świeci słońce. Co założymy?” / „Pada deszcz. Co weźmiemy?” / „Pada śnieg. Co założymy?”; dotknięcie okna powtarza.
- Pasująca rzecz zostaje na postaci (czapka na głowie, okulary na oczach, parasol w ręce, kalosze na nogach, rękawiczki na dłoniach), lektor mówi jej nazwę; po obu pasujących — następna pogoda. Niepasująca wraca, a lektor wyjaśnia „To na słońce.” (bez „nie”); po 2 pomyłkach z rzędu postać pulsuje.
- Słońce: czapka z daszkiem, okulary; deszcz: parasol, kalosze; śnieg: ciepła czapka, rękawiczki. Poziomy `weather_1` (+1 niepasująca rzecz) i `weather_2` (+2). Gwiazdki `RoundScoring` (pomyłki na 6 rzeczy).
- Rysunki rzeczy i postaci w kodzie (Canvas), kolory rzeczy z farb Kolorowanek.

---

## Kolejka pomysłów właściciela (2026-11-11, przed specyfikacją)

Kolejność proponowana; każdy punkt dostanie pełną specyfikację i makietę przed implementacją.

1. ~~**Zdrapka — poprawka**~~ — zrobione (ADR-029).
2. ~~**Nagrania → tekst**~~ — zrobione (ADR-030).
3. ~~**Cienie**~~ — zrobione (M21, ADR-031).
4. ~~**Kulki**~~ — zrobione (M22, ADR-032).
5. ~~**Znajdź na zdjęciu**~~ — zrobione jako „Znajdź na obrazku” (M23, ADR-033). Zostaje na później: sceny z własnych zdjęć rodzica z zaznaczanymi obszarami, sceny „las” i „plaża”, poziomy z cechami („czerwone auto”), liczeniem i położeniem.
6. **Propozycje do wyboru** — robione kolejno po kolejce: ~~Kto zniknął?~~ (M24 „Czego brakuje?”), ~~Połówki~~ (M25), ~~Klocki do dziurek~~ (M26), ~~Powtórz za mną~~ (M27), ~~Ogródek~~ (M28), ~~Ubierz na pogodę~~ (M29) — wszystkie zrobione.

---

## Definition of Done dla każdego milestone'u M10–M29
1. Gra zarejestrowana w `GameRegistry` z `ageBand`, `kind`, ikoną liniową i opisem dla S11.
2. Logika domenowa w `domain/game/<gra>/` z testami jednostkowymi (deterministyczny `Random`, czas przez `TestScope`).
3. Ekrany zgodne z docs/08 (paleta stonowana, miękkie cienie, ikony liniowe, brak czerwieni i „buczków”), cele dotyku ≥ 96/64 dp, obsługa tylko pierwszego wskaźnika (poza Zdrapką i Cymbałkami).
4. Gra działa na samych treściach wbudowanych **i** na zdjęciach rodzica; gdy danych brakuje — czytelny stan pusty dla rodzica, a dziecko nie widzi gry lub widzi wyszarzony poziom.
5. Wpis w `CHANGELOG.md`, checkboxy w tym pliku, zrzuty ekranu w raporcie, `./gradlew lint testDebugUnitTest assembleDebug` zielone.

## Checklista postępu
- [x] M10 Infrastruktura (menu N gier, widoczność, wiek, `VoicePromptPlayer`, migracja v2, `RoundEngine`)
- [x] M11 Gdzie jest…?
- [x] M12 A kuku + Zdrapka
- [x] M13 Do koszyczków + Co nie pasuje?
- [x] M14 Memo dźwiękowe
- [x] M15 Bańki + Cymbałki
- [x] M16 Po kolei (+ S15 Sekwencje)
- [x] M17 Ile jest?
- [x] M18 Kolorowanki
- [x] M19 Labirynt
- [x] M20 Tekst czytany przez syntezator zamiast nagrań (ADR-030)
- [x] M21 Cienie
- [x] M22 Kulki
- [x] M23 Znajdź na obrazku
- [x] M24 Czego brakuje?
- [x] M25 Połówki
- [x] M26 Klocki do dziurek
- [x] M27 Powtórz za mną
- [x] M28 Ogródek
- [x] M29 Ubierz na pogodę

_Stan (2026-11-11):_ wszystkie gry zarejestrowane i sprawdzone na emulatorze 1280×800 dp (menu 3×2 ze stronami, S15, S17, każda gra przeszła co najmniej jedną rundę lub wygraną); `lint testDebugUnitTest assembleDebug` zielone (122 testy, w tym test migracji v1→v2). Odstępstwa: ADR-023…ADR-027. Niesprawdzone: dźwięk (emulator bez audio — kolejność klipów i syntezatora tylko w testach i logice), nagrywanie prefiksów i pytań głosowych mikrofonem, wielodotyk w Zdrapce i Cymbałkach (emulator ma jeden palec).

## Pytania do właściciela (wpisz odpowiedzi przed M10)
| # | Pytanie | Założenie domyślne |
|---|---|---|
| G1 | Czy TTS (syntezator mowy) jest akceptowalny jako zapasowy głos, czy tylko nagrania rodzica? | TTS dozwolony jako zapas |
| G2 | Kolejność milestone'ów M11–M18 — czy zmieniamy? | jak w tabeli |
| G3 | Które gry mają być domyślnie ukryte? | widoczne wszystkie pasujące do przedziału wieku |
| G4 | Melodia w Cymbałkach — jaka piosenka? (tylko melodie z domeny publicznej) | „Wlazł kotek na płotek” |

_2026-11-11: brak odpowiedzi właściciela przed startem M10 — przyjęte założenia domyślne G1–G4 (polecenie „działaj”)._
