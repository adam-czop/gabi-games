# 10 · Testy i QA

## 1. Narzędzia
JUnit4 · kotlinx-coroutines-test (`runTest`, `StandardTestDispatcher`, `advanceTimeBy`) · Turbine (Flow) · MockK (tylko tam, gdzie fake jest za drogi — preferuj ręczne fake'i) · Robolectric (DAO, DataStore, MediaStorage) · OkHttp MockWebServer · Compose UI Test (`createAndroidComposeRule`) na emulatorze tabletu.

## 2. Wymagane testy automatyczne
| Obszar | Co | Typ |
|---|---|---|
| Memorki | rozdanie, silnik (match/mismatch/blokada/won), punktacja | unit |
| Memorki VM | sekwencja zdarzeń → UiState, efekty dźwięku, opóźnienia czasowe | unit + Turbine |
| Puzzle | `SwapBoard`, tasowanie, `isWithinSnap`, punktacja Snap/Swap | unit |
| Bramka | generator zadań (poprawna odpowiedź zawsze w opcjach, 4 unikalne > 0), `PinHasher` (ten sam PIN ≠ ten sam hash dla różnych soli, weryfikacja), `GateLockoutPolicy` (eskalacja, reset) | unit |
| Dane | DAO (zapytania z docs/02), kaskadowe usuwanie, seeder idempotentny, `MediaStorage` (atomowość, usuwanie plików) | Robolectric |
| Update | parse `update.json`, `VersionComparator`, `ApkVerifier`, `UpdateManager` (200/304/404/timeout/przerwane), polityka instalacji | unit + MockWebServer |
| UI | Memorki 2×2: dotknij 4 karty w znanym układzie (Random(seed)) → widoczny `WinOverlay`; bramka: zła odpowiedź ×3 → blokada | Compose UI (instrumented) |

Pokrycie: `domain/` ≥ 85% linii (Kover lub JaCoCo — raport w CI, bez twardego progu w v1).

## 3. Checklista manualna na tablecie (przed każdym wydaniem minor/major)

### Kiosk (tablet z Device Owner, build release)
- [ ] Po restarcie tabletu startuje FamilyGames (bez launchera systemowego).
- [ ] Gest Home / przycisk Home — nic się nie dzieje.
- [ ] Gest Recents / przegląd aplikacji — nic się nie dzieje.
- [ ] Przeciągnięcie z góry — brak cienia powiadomień i szybkich ustawień.
- [ ] Długie przytrzymanie zasilania — brak menu zasilania (lub zgodnie z decyzją Q4).
- [ ] Wstecz w grze → menu; Wstecz w menu → nic.
- [ ] Kłódka: krótki tap nic nie robi; 3 s → bramka.
- [ ] Bramka: 3 złe odpowiedzi → blokada 60 s, przetrwa restart aplikacji.
- [ ] W strefie rodzica: Photo Picker i aparat otwierają się i wracają do aplikacji; po wyjściu ze strefy nie da się ich już otworzyć.
- [ ] 5 min bezczynności w strefie rodzica → powrót do S01.
- [ ] „Wyjdź z kiosku” wymaga PIN, otwiera Ustawienia systemu; ponowne uruchomienie aplikacji przywraca kiosk.
- [ ] Kod odzyskiwania pozwala ustawić nowy PIN.

### Gry
- [ ] Wszystkie 4 poziomy Memorków na kategorii wbudowanej i własnej.
- [ ] Szybkie tapanie wielu kart naraz / całą dłonią — nigdy 3 odkryte karty.
- [ ] Kategoria z 3 zdjęciami: poziomy 2×4 i 3×4 wyszarzone z ikoną aparatu.
- [ ] Nagranie rodzica odtwarzane po parze, nie nakłada się z melodyjką wygranej.
- [ ] Puzzle Snap 2×2/2×3: upuszczenie poza planszą wraca na tackę; przeciąganie dwoma palcami nie duplikuje fragmentu.
- [ ] Puzzle Swap 3×3: rozwiązanie → scalenie → S04.
- [ ] Wyłączony dźwięk w S01 wycisza wszystkie efekty (nagrania rodzica też — decyzja Q6).

### Zdjęcia
- [ ] Wybór 10 zdjęć z galerii → kreator 1/10…10/10, „Pomiń” działa.
- [ ] Zdjęcie pionowe z aparatu ma poprawną orientację po kadrowaniu (EXIF).
- [ ] Usunięcie zdjęcia usuwa pliki (sprawdź `adb shell run-as com.familygames ls files/photos`).
- [ ] 200 zdjęć w kategorii — siatka S09 płynna, start gry < 1 s.

### Aktualizacje
- [ ] Release wyższej wersji → pobranie, weryfikacja, cicha instalacja na S01, powrót do aplikacji.
- [ ] W trakcie gry aktualizacja **nie** instaluje się.
- [ ] APK podpisany innym kluczem → odrzucony (S11 pokazuje błąd).
- [ ] Brak sieci / tryb samolotowy → brak crasha, `Error` z ponowieniem.
- [ ] „Tylko Wi-Fi” respektowane (test z hotspotem oznaczonym jako taryfowy).

### Ogólne
- [ ] Wygaszenie ekranu i wybudzenie w trakcie gry — gra trwa, dźwięki działają.
- [ ] Niski poziom pamięci (zapełniony dysk) — dodawanie zdjęcia pokazuje czytelny błąd rodzicowi.
- [ ] TalkBack w strefie rodzica — przyciski mają opisy.

## 4. Urządzenia referencyjne
- Emulator: Pixel Tablet, API 35 (bez Google Play — do DO) + API 28 (min).
- Fizyczny: docelowy tablet rodziny (Q7 w docs/11).
