# 03 · Ekrany i UX

Makiety: `mockups/png/` (kadr 1280×800 dp). Adnotacje pod każdym ekranem w `mockups/index.html` są częścią wymagań.

## 0. Zasady UX dla strefy dziecka (S01–S07)
1. **Bez czytania.** Każda akcja rozpoznawalna po ikonie/kolorze. Tekst jest tylko dla rodzica.
2. **Duże cele dotyku.** Główne ≥ 96 dp, pomocnicze ≥ 64 dp, odstęp między celami ≥ 24 dp.
3. **Bez porażki.** Brak czerwieni, „buczków”, liczników czasu, przegranych. Błąd = łagodny powrót + miodowe podświetlenie.
4. **Natychmiastowa reakcja.** Każdy dotyk ma odpowiedź < 100 ms: dźwięk „tap” + skala 0.95 (spring).
5. **Multi-touch odporny.** Dziecko kładzie całą dłoń: obsługuj tylko pierwszy wskaźnik w grach (ignoruj kolejne pointery do czasu `up`), debounce 250 ms na przyciskach nawigacji.
6. **Brak zakończeń w ślepy zaułek.** Z każdego ekranu dziecka jest przycisk Dom (lewy górny róg, zawsze w tym samym miejscu).
7. **Spokój.** Stonowana paleta (docs/08 §0) — kolor wnoszą zdjęcia, nie interfejs. Animacje ≤ 600 ms (poza konfetti), brak migających elementów > 3 Hz.

Strefa rodzica (S08–S14) używa `ParentTheme` (ta sama paleta, Nunito, gęstszy układ, standardowe kontrolki M3) — od gry odróżnia ją typografia i gęstość, nie kolor.

## S01 · Menu główne — `MainMenuScreen`
- Układ: logo „Family Games” (Ink + „Games” w kolorze akcentu), rząd kafli gier z `GameRegistry` + kafel „Wkrótce”.
- Kłódka (64 dp, alpha 0.55): `HoldToActivateButton` — przytrzymanie 3000 ms z pierścieniem postępu; puszczenie wcześniej = reset. Po pełnym czasie → `ParentGateDialog`.
- Głośność: toggle `sound_enabled` (ikona zmienia się na przekreśloną).
- Przy wejściu na ekran: jeżeli `UpdateManager` ma pobraną i zweryfikowaną aktualizację → instalacja (patrz docs/07 §5). Dziecko widzi wtedy na 2–3 s pełnoekranowy „Za chwilę wracamy!” z animacją.
- Stany: `Loading` (krótko, splash), `Ready(games)`.

## S02 · Wybór poziomu Memorków — `MemoryLevelScreen`
- 4 kafle poziomów (2×2, 2×3, 2×4, 3×4) z podglądem siatki, licznikiem kart (tekst dla rodzica) i najlepszymi gwiazdkami.
- Stany kafla: `Available`, `Selected` (ostatnio grany, ramka w kolorze akcentu), `Locked` (kłódka; tylko gdy `level_locks_enabled` i poprzedni poziom bez gwiazdki), `NotEnoughPhotos` (wyszarzony, ikona aparatu — kategoria ma < `pairs` zdjęć).
- Pasek kategorii na dole: tylko kategorie widoczne (`isHidden = false`) z ≥ 2 zdjęciami. Domyślnie `last_memory_category`.
- Tap w poziom → natychmiast `MemoryPlayRoute(level, categoryId)`.
- > 4 kategorie → pasek przewijany poziomo (`LazyRow`).

## S03 · Gra Memorki — `MemoryScreen`
Szczegóły logiki: docs/04.
- Góra: Dom (lewo), licznik par jako małe kropki (środek), Restart (prawo).
- Siatka liczona z dostępnej przestrzeni: `cardSize = min((W - (cols-1)*gap)/cols, (H - (rows-1)*gap)/rows)`, gap 28 dp, proporcja karty 0.86 (szer./wys.). Siatka wyśrodkowana.
- Karta: tył (jasny neutralny wzór kropek + konturowa gwiazdka), przód (zdjęcie + opcjonalny podpis). Obrót 350 ms.
- Po dopasowaniu: ramka w kolorze akcentu + ✓, subtelne konfetti, dźwięk + nagranie rodzica (jeśli jest; dźwięk „match” → 300 ms → głos).
- Po pomyłce: miodowa ramka 1000 ms, potem zakrycie.
- Koniec: `WinOverlay` (S04).

## S04 · Ekran sukcesu — `WinOverlay` (wspólny)
- Overlay na ekranie gry: jasna mgiełka (tło 72% + blur), delikatne konfetti (Canvas, ~40 cząstek, 2 s), karta z gwiazdą w miodowym kole, „Brawo!”, 1–3 gwiazdki (stagger 200 ms), 2 przyciski 136 dp: Zagraj ponownie (wypełniony akcentem), Menu (biały z obrysem).
- Zapis `GameResultEntity` następuje **przed** pokazaniem overlay.
- Przyciski aktywne dopiero po 800 ms (dziecko w ekscytacji „klepie” ekran).

## S05 · Puzzle — wybór obrazka i trudności — `PuzzleSetupScreen`
- Siatka 3×2 obrazków (zdjęcia `useInPuzzle = true` ze wszystkich widocznych kategorii, najnowsze najpierw; > 6 → przewijana pionowo `LazyVerticalGrid` — tu przewijanie jest OK).
- 4 przyciski trudności: Snap 2×2, Snap 2×3, Swap 2×2, Swap 3×3. Ostatni wybór zapamiętany.
- Interakcja: tap obrazka → start gry z aktualnie wybraną trudnością.

## S06 · Puzzle Snap — `SnapPuzzleScreen`
Szczegóły: docs/05 §2. Plansza 4:3 z ghost image (alpha 0.22) + obrysy pól; tacka po prawej z fragmentami w skali 0.7. Przeciąganie, magnes < 40 dp, powrót na tackę przy złym miejscu. Przycisk żarówki (podpowiedź).

## S07 · Puzzle Swap — `SwapPuzzleScreen`
Szczegóły: docs/05 §3. Siatka n×n, tap = zaznacz, tap sąsiada = zamiana. Mała kropka w kolorze akcentu na kafelkach na właściwym miejscu. Podgląd wzorca po prawej.

## S08 · Bramka rodzica — `ParentGateDialog`
Szczegóły: docs/06 §4. Dialog pełnoekranowy z przyciemnieniem; zakładki „Zadanie” / „Kod PIN”; mnożenie z 4 odpowiedziami; limit prób i auto-zamknięcie po 30 s.

## S09 · Zdjęcia — `PhotoManagerScreen`
- Boczna nawigacja rodzica (wspólna dla S09, S11, S13, S14): Zdjęcia, Kategorie, Postępy, Ustawienia, separator, Wróć do gier, Wyjdź z kiosku (czerwony, wymaga PIN).
- Chipy kategorii z licznikami + „Nowa kategoria” (dialog z nazwą).
- Pasek stanu kategorii: szałwiowy „wystarczy na wszystkie poziomy” (≥ 6 zdjęć Memorków) / miodowy „dodaj jeszcze N zdjęć, aby odblokować poziom 3×4”.
- Siatka 5 kolumn: miniatura, etykieta, ikona mikrofonu, plakietka „okładka”.
- Akcje: „Z galerii” → `PickMultipleVisualMedia(maxItems = 20)`; „Zrób zdjęcie” → `TakePicture` do pliku w `cacheDir/camera/` przez `FileProvider`. Oba → S10 z listą `pendingUris`.
- Długie przytrzymanie → tryb zaznaczania: Przenieś do…, Ustaw jako okładkę (gdy 1), Usuń (dialog potwierdzenia).
- Wbudowanych zdjęć nie da się usunąć — można je ukryć (toggle `useInMemory/useInPuzzle` = false).

## S10 · Dodawanie / edycja zdjęcia — `PhotoEditScreen`
- Lewa część: `CropView` — obraz pod stałą ramką; pan (1 palec) + pinch-zoom (2 palce), min. zoom = ramka wypełniona, max 5×; siatka 3×3; przełącznik 1:1 / 4:3; obrót o 90°.
- Prawa część: etykieta (max 24), kategoria (dropdown), nagranie głosu (tap start/stop, limit 8 s, licznik, odsłuch, nagraj ponownie, usuń), przełączniki „Memorki”/„Puzzle”, Anuluj / Zapisz.
- Wiele zdjęć: pasek postępu „2 / 5” u góry + „Pomiń”.
- Zapis: crop w tle (`Dispatchers.Default`), zapis plików, insert do Room; w trakcie przycisk „Zapisz” pokazuje spinner, blokada podwójnego kliknięcia.
- Uprawnienia: **nie deklaruj `CAMERA`** w manifeście — `TakePicture` korzysta z systemowej aplikacji aparatu i nie potrzebuje go (a zadeklarowane, a nieprzyznane `CAMERA` blokuje tę intencję). `RECORD_AUDIO` pytane przy pierwszym nagraniu (z wyjaśnieniem w dialogu).
- **Uwaga — kiosk:** Photo Picker i aparat to osobne pakiety systemowe, które Lock Task domyślnie blokuje. Rozwiązanie w docs/06 §5 („Sesja rodzica a Lock Task”).

## S11 · Ustawienia — `SettingsScreen`
Karty: **Aktualizacje** (wersja zainstalowana/dostępna, postęp, changelog, „Sprawdź teraz”, „Zainstaluj”, przełączniki), **Tryb kiosku** (statusy DO / Lock Task / HOME na żywo, przełączniki pasków i ekranu, „Wyjdź z kiosku (PIN)”; gdy brak DO → instrukcja provisioningu z docs/06 §2 i przycisk „Kopiuj komendę”), **Bezpieczeństwo** (Zmień PIN — stary PIN + nowy ×2), **Gra i dźwięk** (efekty, podpisy na kartach, blokady poziomów).
Na dole sekcja „Zaawansowane” (zwinięta): „Usuń uprawnienia Device Owner” (PIN + potwierdzenie tekstowe), „Wyczyść postępy”.

## S12 · Pierwsze uruchomienie — `FirstRunScreen` (bez makiety)
Kreator 3 kroki w stylu rodzica: (1) Powitanie + krótki opis, (2) Ustaw PIN (4 cyfry ×2), (3) Status kiosku: jeśli DO aktywny → „Gotowe ✓”, jeśli nie → instrukcja + „Kontynuuj bez kiosku” (ostrzeżenie). Na końcu opcja „Dodaj pierwsze zdjęcia teraz” (→ S09) lub „Zacznij grać” (→ S01).

## S13 · Kategorie — `CategoriesScreen` (bez makiety)
Lista kategorii: nazwa, liczba zdjęć, okładka, przełącznik „widoczna dla dziecka”, zmiana kolejności (przeciąganie lub strzałki), zmiana nazwy, usunięcie (tylko własne; dialog „usunie N zdjęć”).

## S14 · Postępy — `ProgressScreen` (bez makiety)
Proste podsumowanie z `game_results`: gry dziś / w tym tygodniu, łączny czas, najlepsze gwiazdki na poziom (tabela), ulubione zdjęcia (najczęściej dopasowywane — opcjonalnie). Bez wykresów w v1.

## Tryb immersyjny i orientacja
- Zawsze landscape (`sensorLandscape`). Paski systemowe ukryte (`WindowInsetsControllerCompat.hide(systemBars())`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), ponownie ukrywane w `onWindowFocusChanged(true)`.
- Treść rysowana edge-to-edge, ale przyciski w strefie bezpiecznej (padding z `WindowInsets.safeDrawing`, min. 28 dp).
