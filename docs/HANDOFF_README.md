# FamilyGames Tablet — pakiet wdrożeniowy (handoff dla agenta AI)

Pakiet zawiera wszystko, czego agent (Claude Opus w Claude Code / Cursor) potrzebuje, żeby od zera zbudować aplikację **FamilyGames**: zamkniętą aplikację na tablet z Android dla małego dziecka (Memorki + Puzzle na rodzinnych zdjęciach, tryb kiosku, aktualizacje z GitHub Releases).

## Zawartość

```text
familygames-handoff/
├── README.md                     ← ten plik (start)
├── CLAUDE.md                     ← zasady pracy dla agenta (skopiuj do roota repo)
├── mockups/
│   ├── index.html                ← makiety 11 ekranów + adnotacje (otwórz w przeglądarce)
│   ├── png/                      ← te same ekrany jako PNG 2560×1600 (2×)
│   └── assets/                   ← fonty (OFL) i grafika-placeholder do puzzli
├── docs/
│   ├── 00_SPECYFIKACJA_ORYGINALNA.md   ← wejściowa specyfikacja produktu
│   ├── 01_ARCHITECTURE.md              ← warstwy, pakiety, DI, nawigacja, rejestr gier
│   ├── 02_DATA_MODEL.md                ← Room, DataStore, pliki zdjęć/audio, seed
│   ├── 03_SCREENS_AND_UX.md            ← każdy ekran: stany, interakcje, zasady UX dla malucha
│   ├── 04_GAME_MEMORY.md               ← maszyna stanów, algorytmy, timingi, gwiazdki
│   ├── 05_GAME_PUZZLE.md               ← tryb Snap (drag&drop) i Swap
│   ├── 06_KIOSK_AND_PARENT_GATE.md     ← Device Owner, Lock Task, bramka rodzica, PIN
│   ├── 07_AUTO_UPDATE.md               ← GitHub Releases, update.json, PackageInstaller
│   ├── 08_DESIGN_SYSTEM.md             ← kolory, typografia, kształty, ruch, dźwięki
│   ├── 09_IMPLEMENTATION_PLAN.md       ← kamienie milowe M0–M9 z kryteriami akceptacji
│   ├── 10_TESTING_QA.md                ← testy automatyczne + checklista na tablecie
│   └── 11_DECISIONS_AND_OPEN_QUESTIONS.md ← ADR-y i pytania do właściciela produktu
└── config/
    ├── gradle/libs.versions.toml       ← proponowany version catalog
    ├── github-workflows/release.yml    ← CI: build + podpis + GitHub Release + update.json
    ├── github-workflows/ci.yml         ← CI: lint + testy + build na każdy PR
    └── android/                        ← fragmenty Manifestu, build.gradle.kts i XML (device admin, FileProvider, backup)
```

## Jak uruchomić pracę agenta

1. Utwórz puste repozytorium (np. `familygames-tablet`) i rozpakuj do niego ten pakiet. Pliki `docs/`, `mockups/`, `config/` zostają w repo jako dokumentacja; `CLAUDE.md` musi leżeć w **roocie** repo.
2. Uruchom Claude Code w katalogu repo i wklej prompt startowy poniżej.
3. Agent pracuje kamieniami milowymi z `docs/09_IMPLEMENTATION_PLAN.md`. Po każdym milestonie odhacza checkboxy, robi commit i zatrzymuje się na Twój przegląd.

### Prompt startowy (do wklejenia)

> Jesteś głównym deweloperem aplikacji Android **FamilyGames Tablet**. Przeczytaj `CLAUDE.md`, a następnie kolejno `docs/01`–`docs/11` oraz obejrzyj makiety w `mockups/png/`. Zanim napiszesz kod, wypisz mi w punktach: (1) jak rozumiesz zakres v1, (2) pytania z `docs/11` sekcja „Otwarte pytania”, na które potrzebujesz odpowiedzi przed M0, (3) ewentualne niespójności w dokumentacji. Potem realizuj **M0** z `docs/09_IMPLEMENTATION_PLAN.md`. Po zakończeniu milestone'u uruchom `./gradlew lint testDebugUnitTest assembleDebug`, odhacz zadania w planie, zrób commit i zatrzymaj się z krótkim raportem.

Kolejne sesje: „Kontynuuj od następnego niezamkniętego milestone'u w `docs/09_IMPLEMENTATION_PLAN.md`”.

## Co jest zmienione/doprecyzowane względem oryginalnej specyfikacji

Oryginał jest w `docs/00_SPECYFIKACJA_ORYGINALNA.md`. Najważniejsze doprecyzowania (uzasadnienia w `docs/11`):

- **Device Owner jest wymagany** do prawdziwej blokady i cichych aktualizacji. Bez niego Android pozwala tylko na „przypinanie ekranu”, które dziecko może zdjąć, a każda aktualizacja wymaga kliknięcia w systemowy dialog.
- **Aktualizacje przez plik `update.json`** dołączany do każdego Release (versionCode + SHA-256), zamiast parsowania nazwy tagu. Dodatkowo weryfikacja certyfikatu podpisu APK.
- **Siatka Memorków bez `LazyVerticalGrid`** — maks. 12 kart, bez przewijania; stała siatka liczona z `BoxWithConstraints` jest prostsza i pewniejsza na tablecie.
- **Zdjęcia kopiowane do pamięci wewnętrznej aplikacji** (z miniaturą), a nie trzymane jako URI galerii — URI tracą uprawnienia.
- **Koin** jako DI (rekomendacja ze specyfikacji przyjęta).
- **Treści startowe**: aplikacja ma wbudowane 3 kategorie ilustracji, żeby działała od pierwszego uruchomienia, zanim rodzic doda zdjęcia.
- **Ekran pierwszego uruchomienia** (ustawienie PIN, sprawdzenie Device Owner) — dodany, nie było go w specyfikacji.

## Makiety

Makiety są w wersji **v2 — stonowanej** (neutralny layout, jeden przygaszony akcent, kolor wnoszą zdjęcia; docs/08). Otwórz `mockups/index.html` w przeglądarce — każdy ekran ma pod spodem adnotacje (zachowania, rozmiary, stany). PNG w `mockups/png/` są do szybkiego podglądu i do wklejenia agentowi.

| ID | Ekran | Plik |
|---|---|---|
| S01 | Menu główne | `S01_menu.png` |
| S02 | Wybór poziomu Memorków + kategoria | `S02_poziomy_memorki.png` |
| S03 | Gra Memorki | `S03_gra_memorki.png` |
| S04 | Ekran sukcesu (wspólny) | `S04_ekran_sukcesu.png` |
| S05 | Puzzle – wybór obrazka i trudności | `S05_puzzle_wybor.png` |
| S06 | Puzzle – Snap (drag & drop) | `S06_puzzle_snap.png` |
| S07 | Puzzle – Swap (zamiana kafelków) | `S07_puzzle_swap.png` |
| S08 | Bramka rodzica | `S08_bramka_rodzica.png` |
| S09 | Panel rodzica – zdjęcia | `S09_panel_zdjecia.png` |
| S10 | Dodawanie zdjęcia (kadr, etykieta, głos) | `S10_dodaj_zdjecie.png` |
| S11 | Ustawienia, kiosk, aktualizacje | `S11_ustawienia_kiosk_update.png` |

Ekrany bez makiety (opisane tekstowo w `docs/03`): S12 Pierwsze uruchomienie, S13 Kategorie (CRUD), S14 Postępy.

### Nowe gry po v1.0 (`docs/12_NEW_GAMES.md`)

| ID | Ekran | Plik |
|---|---|---|
| S15 | Panel rodzica – Sekwencje | `S15_panel_sekwencje.png` |
| S16 | Menu na wiele gier | `S16_menu_wiele_gier.png` |
| S17 | Panel rodzica – Gry (wiek, widoczność) | `S17_panel_gry.png` |
| S18 | Gdzie jest…? | `S18_gdzie_jest.png` |
| S19 | A kuku | `S19_a_kuku.png` |
| S20 | Zdrapka | `S20_zdrapka.png` |
| S21 | Do koszyczków | `S21_do_koszyczkow.png` |
| S22 | Co nie pasuje? | `S22_co_nie_pasuje.png` |
| S23 | Memo dźwiękowe | `S23_memo_dzwiekowe.png` |
| S24 | Bańki | `S24_banki.png` |
| S25 | Cymbałki | `S25_cymbalki.png` |
| S26 | Po kolei | `S26_po_kolei.png` |
| S27 | Ile jest? | `S27_ile_jest.png` |
| S28 | Kolorowanki | `S28_kolorowanki.png` |

Prompt po ukończeniu v1.0: „Przeczytaj `docs/12_NEW_GAMES.md`, obejrzyj makiety S15–S28 i realizuj M10. Potem kolejne milestone'y z tego pliku, z przerwą na przegląd po każdym.”
