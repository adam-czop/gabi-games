# 09 · Plan wdrożenia (kamienie milowe)

Zasady: realizuj milestone'y **po kolei**. Po każdym — Definition of Done z `CLAUDE.md`, commit, raport, stop. Odhaczaj `[x]` w tym pliku. Szacunki to dni pracy agenta + przegląd człowieka.

---

## M0 · Bootstrap projektu (~0,5 d)
- [x] Projekt Android `:app`, namespace `com.familygames`, Kotlin DSL, version catalog z `config/gradle/libs.versions.toml` (podbij wersje do najnowszych stabilnych, zapisz w ADR jeśli coś zmieniasz).
- [x] minSdk 28, target 36 / compile 37 (ADR-013), JDK 17, Compose + Material 3, R8 w release.
- [x] `buildConfigField`: `KIOSK_ENABLED`, `UPDATE_REPO_OWNER`, `UPDATE_REPO_NAME`; `versionCode` z `-PversionName` (docs/01 §7).
- [x] Koin (`startKoin` w `FamilyGamesApp`), puste moduły.
- [x] `KidTheme` i `ParentTheme` (kolory, typografia z fontami w `res/font/`, kształty, `Dimens`) — docs/08.
- [x] `FamilyNavHost` z pustymi ekranami-zaślepkami dla wszystkich tras (docs/01 §4).
- [x] `MainActivity`: landscape, edge-to-edge, ukryte paski systemowe.
- [x] Workflow CI `ci.yml` (PR/push: `lint testDebugUnitTest assembleDebug`) + skopiowany `release.yml` (jeszcze nieużywany).
- [x] `.gitignore` (keystore, `keystore.properties`, `local.properties`), `CHANGELOG.md`.
**Akceptacja:** aplikacja startuje na emulatorze tabletu (Pixel Tablet API 35), pokazuje S01-zaślepkę w motywie, CI zielone.
_Stan: S01 na emulatorze Pixel Tablet API 34 ✓; CI do potwierdzenia po pierwszym pushu._

## M1 · Warstwa danych i treści wbudowane (~1 d)
- [x] Room: encje, DAO, `FamilyGamesDatabase` v1, eksport schematu (docs/02 §1).
- [x] `SettingsDataStore` z kluczami z docs/02 §2 + `SettingsRepository`.
- [x] `MediaStorage` (zapis JPEG + miniatura, audio, usuwanie, zapis atomowy).
- [x] Repozytoria: Category, Photo, GameResult, Settings (interfejsy w `domain`).
- [x] `DefaultContentSeeder`: 3 kategorie × 8 ilustracji (proste wektory w stylu docs/08 lub zestaw CC0 + LICENSE).
- [x] Testy: DAO (Robolectric lub instrumented in-memory), seeder idempotentny, MediaStorage na `TemporaryFolder`.
**Akceptacja:** po pierwszym uruchomieniu w bazie są 3 kategorie i 24 zdjęcia; testy zielone.
_Stan: emulator Pixel Tablet API 34 — 3 kategorie, 24 zdjęcia ✓; 30 testów jednostkowych/Robolectric ✓._

## M2 · Menu i Memorki (~1,5 d)
- [x] S01 `MainMenuScreen` z `GameRegistry`, kafel „Wkrótce”, toggle dźwięku, `HoldToActivateButton` (na razie prowadzi do zaślepki bramki).
- [x] `SoundPlayer` (SoundPool) + tymczasowe dźwięki CC0.
- [x] Domain: `DealMemoryCardsUseCase`, `MemoryEngine`, `MemoryScoring` + testy (docs/04 §8).
- [x] S02 `MemoryLevelScreen` (stany kafli, pasek kategorii, najlepsze gwiazdki).
- [x] S03 `MemoryScreen` + `MemoryViewModel` (siatka z `BoxWithConstraints`, flip, blokada wejścia, efekty).
- [x] Zapis `GameResultEntity` po wygranej.
**Akceptacja:** pełna rozgrywka 2×2…3×4 na wbudowanych zdjęciach; brak możliwości odkrycia 3. karty; zachowanie i wygląd jak S02/S03.
_Stan: S02/S03 na emulatorze zgodne z makietą ✓; 46 testów ✓. W M2 jest już podstawowe okno S04 (gwiazdki, przyciski); konfetti i reszta w M3._

## M3 · Ekran sukcesu i dopracowanie gry (~0,5 d)
- [x] `WinOverlay` + `ConfettiCanvas` + `StarsRow` (S04), opóźnienie aktywacji przycisków 800 ms.
- [x] Odtwarzanie nagrania rodzica po dopasowaniu (`MediaPlayer`, kolejka — nie nakładaj dźwięków).
- [x] Obsługa multi-touch (tylko pierwszy wskaźnik), debounce nawigacji.
- [x] Ustawienie „Usuń animacje” (dostępność) respektowane.
**Akceptacja:** S04 jak w makiecie; „klepanie” ekranu całą dłonią nie psuje gry.
_Stan: S04 z konfetti i gwiazdkami na emulatorze ✓; tylko pierwszy palec liczy się w strefie dziecka (`firstPointerOnly`), przyciski z odstępem 250 ms; nagranie rodzica blokuje melodię wygranej do swojego końca (test VM)._

## M4 · Puzzle Snap (~1,5 d)
- [x] S05 `PuzzleSetupScreen` (obrazki + 4 trudności, zapamiętywanie wyboru).
- [x] Ładowanie bitmapy 4:3 (center-crop), rysowanie fragmentów z jednego bitmapu.
- [x] S06 `SnapPuzzleScreen`: drag w warstwie overlay, magnes 40 dp, spring snap, powrót na tackę, żarówka.
- [x] Punktacja + zapis wyniku + `WinOverlay`.
- [x] Testy: `isWithinSnap`, punktacja, ViewModel (umieszczenie wszystkich → won).
**Akceptacja:** 2×2 i 2×3 grywalne palcem na tablecie; fragment nigdy nie „ginie” poza ekranem.
_Stan: S05 i S06 na emulatorze ✓ (przeciąganie, magnes, powrót na tackę, wygrana); tacka dobiera układ do liczby pozostałych fragmentów (ADR-017)._

## M5 · Puzzle Swap (~1 d)
- [x] Domain: `SwapBoard`, `ShuffleSwapBoardUseCase`, `PuzzleScoring` + testy (docs/05 §5).
- [x] S07 `SwapPuzzleScreen`: zaznaczanie, podświetlenie sąsiadów, animacja zamiany, kropki „na miejscu”, podgląd wzorca, scalanie po wygranej.
**Akceptacja:** 2×2 i 3×3 grywalne; tasowanie nigdy nie daje ułożonej planszy.
_Stan: S07 3×3 rozwiązany na emulatorze ✓ (zaznaczenie, kropki, scalenie, wygrana); testy `SwapBoard`/tasowania/punktacji ✓._

## M6 · Strefa rodzica: bramka i zdjęcia (~2 d)
- [x] Domain: `ParentChallengeGenerator`, `PinHasher` (PBKDF2), `GateLockoutPolicy`, kod odzyskiwania + testy.
- [x] S08 `ParentGateDialog` (zadanie / PIN, blokady, auto-zamknięcie 30 s).
- [x] S12 `FirstRunScreen` (PIN ×2, kod odzyskiwania, status kiosku) — start aplikacji kieruje tu, dopóki nie ukończono.
- [x] `ParentScaffold` z boczną nawigacją; timeout sesji rodzica 5 min → S01.
- [x] S09 `PhotoManagerScreen` (chipy kategorii, pasek wystarczalności, siatka, tryb zaznaczania).
- [x] S10 `PhotoEditScreen`: Photo Picker (multi), `TakePicture` + `FileProvider`, `CropView` (pan/zoom/obrót, 1:1/4:3), etykieta, kategoria, `VoiceRecorder` (8 s), przełączniki gier, zapis w tle.
- [x] S13 `CategoriesScreen` (CRUD, widoczność, kolejność, okładka).
**Akceptacja:** rodzic dodaje 5 zdjęć z galerii i 1 z aparatu, nagrywa głos, gra Memorki na nowej kategorii słyszy nagrania.
_Stan: na emulatorze ✓ kreator S12 (PIN ×2, kod odzyskiwania, status kiosku), bramka S08 (zadanie), S09 (nowa kategoria, 2 zdjęcia z Photo Pickera z kadrem 4:3 i etykietami), S13. Blokady bramki i kod odzyskiwania — testy jednostkowe. **Do sprawdzenia na tablecie:** nagrywanie głosu (emulator bez dźwięku), aparat, wygaśnięcie sesji po 5 min._

## M7 · Kiosk (~1,5 d)
- [x] `FamilyDeviceAdminReceiver`, `device_admin.xml`, manifest (HOME, `lockTaskMode`) — `config/android/`.
- [x] `KioskManager` (`applyPolicies`, `enterLockTask`, `exitKiosk`, `decommission`, `begin/endParentSession`) — docs/06.
- [x] Integracja w `MainActivity` (onResume, focus → immersive) pod flagą `KIOSK_ENABLED`.
- [x] S11 karta „Tryb kiosku”: statusy na żywo, instrukcja provisioningu gdy brak DO, wyjście (PIN), zaawansowane: usunięcie DO.
- [x] Testy manualne z checklisty docs/10 §3 na emulatorze z DO.
**Akceptacja:** na emulatorze z DO nie da się opuścić aplikacji (Home, Recents, cień powiadomień, restart); Photo Picker i aparat działają w sesji rodzica.
_Stan: emulator z Device Owner (`-Pkiosk=true`): Lock Task LOCKED, FamilyGames jako HOME, Home/Ostatnie/cień powiadomień/Wstecz nie wyprowadzają z aplikacji ✓; S11 karta kiosku ✓. **Do sprawdzenia na tablecie:** restart urządzenia, Photo Picker i aparat w sesji rodzica przy aktywnym kiosku._

## M8 · Auto-update i wydania (~1,5 d)
- [x] `GitHubReleaseApi` (OkHttp + kotlinx.serialization, ETag), `UpdateInfo` z `update.json`.
- [x] `UpdateManager` (`StateFlow<UpdateState>`), pobieranie do `cacheDir/updates`, `ApkVerifier` (rozmiar, SHA-256, pakiet, wersja, certyfikat).
- [x] `ApkInstaller` (PackageInstaller session, `USER_ACTION_NOT_REQUIRED`), `InstallResultReceiver`, `PackageReplacedReceiver`.
- [x] `UpdateWorker` (12 h, constraints) + polityka „kiedy instalować” + ekran „Za chwilę wracamy!”.
- [x] S11 karta „Aktualizacje”.
- [ ] `release.yml` działający: tag → podpisany APK + `update.json` w Release. Dokumentacja sekretów w `docs/07` §9 i README. _(dokumentacja ✓ w README; pierwsze wydanie czeka na repo wydań i sekrety)_
- [x] Testy (docs/07 §8) z MockWebServer.
**Akceptacja:** wydanie v0.9.0 → v0.9.1 przez tag; tablet/emulator z DO aktualizuje się bez dialogu, wraca do S01; APK z innym podpisem jest odrzucany.
_Stan: logika i UI gotowe, 20 testów (MockWebServer: 200/304/404/500, przerwane pobieranie, zły hash; weryfikator: rozmiar/hash/pakiet/wersja/certyfikat) ✓. **Niezweryfikowane:** prawdziwe wydanie przez tag — wymaga publicznego repo `adam-czop/familygames-releases`, sekretów podpisu w GitHub i pierwszego pushu (instrukcja w README)._

## M9 · Dopracowanie i QA (~1,5 d)
- [x] S14 `ProgressScreen`.
- [x] Docelowe dźwięki, ikony gier (VectorDrawable), ilustracje wbudowane.
- [ ] Baseline Profile, pomiar zimnego startu (< 1,5 s), animacje 60 fps (Macrobenchmark lub ręcznie z profilerem).
- [ ] Pełna checklista docs/10 §3 na docelowym tablecie; poprawki.
- [x] README dla rodzica (`docs/PARENT_GUIDE.md`): provisioning, dodawanie zdjęć, PIN, wydania.
- [ ] Tag `v1.0.0`.
**Akceptacja:** checklista docs/10 w 100% ✓ na docelowym tablecie; v1.0.0 opublikowane.
_Stan: S14, przewodnik dla rodzica, README z procedurą wydań ✓. Do zrobienia na docelowym tablecie: Baseline Profile i pomiar startu, pełna checklista docs/10 §3; tag `v1.0.0` po decyzji właściciela._

---

## Po v1.0 → `docs/12_NEW_GAMES.md` (M10–M29, makiety S15–S38)

## Backlog (po v1)
- Kolorowanki (na konturach zdjęć), Memo dźwiękowe (pary dźwięk–zdjęcie), „Gdzie jest…?” (głos rodzica + wybór zdjęcia).
- Provisioning QR, kopia zapasowa zdjęć (eksport ZIP na pendrive/komputer), wiele profili dzieci, limit czasu gry dziennie.
