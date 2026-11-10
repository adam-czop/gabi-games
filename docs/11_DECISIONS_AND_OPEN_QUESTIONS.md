# 11 · Decyzje (ADR) i otwarte pytania

Format ADR: kontekst → decyzja → konsekwencje. Agent dopisuje nowe ADR-y na końcu listy (kolejny numer), gdy odchodzi od dokumentacji lub dodaje zależność.

## Decyzje

**ADR-001 · Koin zamiast Hilt.** Mała aplikacja, jeden moduł, szybki build bez kapt/KSP dla DI. Konsekwencja: błędy grafu wychodzą w runtime → test `koinApplication { modules(...) }.checkModules()` / `verify()` w unit testach.

**ADR-002 · Jeden moduł Gradle.** Podział na pakiety wystarcza przy 2 grach. Rewizja przy > 4 grach (wtedy `:feature:game-*`, `:core:*`).

**ADR-003 · Device Owner jako docelowy tryb kiosku.** Tylko DO daje pewną blokadę, start po restarcie i cichą instalację aktualizacji. Konsekwencja: wymagany reset tabletu i adb przy pierwszej konfiguracji; tryb bez DO jest „degraded” i jasno komunikowany.

**ADR-004 · Aktualizacje przez `update.json` + weryfikacja SHA-256 i certyfikatu.** Parsowanie tagów jest kruche; sam hash z tego samego źródła co APK nie chroni przed podmianą Release, dlatego dodatkowo porównujemy certyfikat podpisu z zainstalowaną aplikacją (to i tak wymusza Android, ale weryfikacja przed sesją daje czytelny błąd i oszczędza próby).

**ADR-005 · Stała siatka Memorków zamiast `LazyVerticalGrid`** (odstępstwo od promptu w specyfikacji §6). Maks. 12 kart, bez przewijania; `BoxWithConstraints` pozwala dopasować rozmiar karty do ekranu, Lazy nie daje tu korzyści, a utrudnia mierzenie.

**ADR-006 · Zdjęcia kopiowane do `filesDir`.** URI z galerii/Pickera tracą uprawnienia i mogą zniknąć; kopia + miniatura gwarantuje działanie offline i szybkie ładowanie. Konsekwencja: zajętość miejsca (~300 KB/zdjęcie) — akceptowalne.

**ADR-007 · Własny `CropView` w Compose zamiast biblioteki.** Potrzebne tylko 2 proporcje, pan/zoom/obrót; biblioteki kadrowania są oparte na View/Activity, co komplikuje Lock Task. Jeśli implementacja przekroczy ~1 dzień — rozważyć bibliotekę i dopisać ADR.

**ADR-008 · Photo Picker zamiast uprawnień do pamięci.** Brak `READ_MEDIA_IMAGES`; prywatność i prostsza obsługa. Konsekwencja: biała lista pakietów w Lock Task na czas sesji rodzica (docs/06 §5).

**ADR-009 · `versionCode = major*10000 + minor*100 + patch`.** Deterministyczny z tagu, czytelny, monotoniczny. Ograniczenie: minor/patch ≤ 99.

**ADR-010 · Brak przywracania stanu gry po śmierci procesu.** Rozgrywka trwa 1–3 minuty; po restarcie dziecko wraca do wyboru poziomu. Upraszcza ViewModele.

**ADR-011 · Brak sieci poza aktualizacjami, brak analityki i crash-reportingu zewnętrznego.** Aplikacja dla dziecka, prywatne zdjęcia. Błędy zbierane lokalnie do pliku logu (rotacja 1 MB), podgląd w S11 → „Zaawansowane” (opcjonalnie M9).

**ADR-012 · Stonowana paleta (v2 makiet).** Na życzenie właściciela layout jest delikatny: neutralne tło, jeden przygaszony akcent, ikony liniowe, miękkie cienie — kolor wnoszą zdjęcia. Jeden jasny motyw (bez trybu ciemnego w v1). Szczegóły: docs/08.

**ADR-013 · Wersje narzędzi z listopada 2026 (M0).** Katalog z `config/gradle` był z połowy 2026; zgodnie z M0 podbity do najnowszych stabilnych: AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Navigation 2.10.2, Room 2.8.5, Koin 4.2.2, Coil 3.6.3, OkHttp 5.5.1 (MockWebServer to teraz artefakt `mockwebserver3`). Konsekwencje: (1) AGP 9 kompiluje Kotlina sam (built-in Kotlin), więc plugin `org.jetbrains.kotlin.android` usunięty z katalogu; (2) aktualne AndroidX wymagają `compileSdk 37`, dlatego kompilujemy na 37, a `targetSdk` zostaje 36 (zachowanie w runtime jak w dokumentacji); (3) dodany `koin-test` (bez JUnit-owego wrappera), bo `Module.verify()` z ADR-001 jest w tym artefakcie. Emulator testowy: Pixel Tablet API 34 (obraz dostępny lokalnie) zamiast API 35 z kryterium M0.

**ADR-014 · Fonty i ikony z makiet.** Fonty: statyczne instancje (Fredoka 500/600, Nunito 400/600/700/800) wycięte z plików zmiennych repozytorium `google/fonts` narzędziem fontTools — pliki `woff2` z makiet nie działają w `res/font`. Licencje OFL w `assets/licenses/`. Ikony liniowe: wszystkie symbole z `mockups/index.html` skonwertowane 1:1 do VectorDrawable (`ic_*`), przerywany okrąg kafla „Wkrótce” rozbity na łuki (VectorDrawable nie ma `stroke-dasharray`). Kolor ikon ustawiany w Compose (`tint`), więc jeden plik służy w stanie zwykłym i aktywnym.

**ADR-015 · Warstwa danych M1.** (1) Ilustracje wbudowane: 24 płaskie rysunki w palecie v2 (wypełnienia + kontur `Ink`; to zastępstwo zdjęć, więc nie liniowe jak ikony UI) zdefiniowane w `tools/builtin_art.py`, renderowane headless Chrome do WebP 800 px + miniatura 400 px w `assets/builtin/` (razem ~0,5 MB) — ścieżki `asset://builtin/…` jak w docs/02 §4, bez renderowania przy starcie. Obiekty mieszczą się w środkowym pasie, więc kadr 4:3 Puzzli ich nie ucina. (2) Seeder rozpoznaje wbudowaną kategorię po ścieżkach jej ilustracji, nie po nazwie — zmiana nazwy przez rodzica nie tworzy duplikatu. (3) Dodatkowy indeks `photos.filePath` (wyszukiwanie przy seedzie); nazwy kategorii porównywane bez wielkości liter. (4) Testy: Robolectric na API 35 (API 36 wymaga Javy 21, projekt jest na JDK 17), zwykłe `Application` w testach; DataStore w testach na `OkioStorage`, bo domyślny `FileStorage` nie nadpisuje pliku na Windowsie (na Androidzie działa). Nowa zależność testowa: `androidx.test:core-ktx`.

**ADR-016 · Memorki (M2).** (1) Dźwięki syntezowane własnym skryptem `tools/make_sfx.py` (tony „marimby” i szum) zamiast paczki CC0 — brak licencji zewnętrznych; opis w `assets/licenses/SOUNDS.md` (`res/raw` nie przyjmuje plików z wielkimi literami, więc `LICENSES.md` nie mógł tam leżeć). Na prośbę właściciela dźwięki nagradzające (para, gwiazdka, wygrana) są głośniejsze (0,7–0,8) od neutralnych (0,4–0,5), żeby motywowały dziecko. (2) Aplikacja wymusza język polski (`MainActivity.attachBaseContext`) — przy tablecie ustawionym na angielski Android stosował angielskie reguły liczby mnogiej („6 karty”). (3) Zdjęcia w kartach ładowane jako zwykłe bitmapy (Coil `allowHardware(false)`) — sprzętowe dawały uszkodzone krawędzie przy obrocie 3D karty. (4) Podstawowe okno S04 powstało już w M2, żeby gra miała zakończenie.

**ADR-017 · Puzzle (M4–M5).** (1) Przeciąganie w S06 obsługuje jeden detektor gestów na całym ekranie, który trafia we fragment po jego aktualnym położeniu i skali — przesunięcie palca zawsze odpowiada ruchowi fragmentu (detektor na samym fragmencie liczyłby ruch w jego pomniejszonym układzie). (2) Tacka dobiera liczbę kolumn do liczby pozostałych fragmentów tak, żeby były jak największe (maks. 70% pola) — przy 4–6 fragmentach na wąskiej tacce stała siatka dawała bardzo małe kawałki. (3) W S07 zamiast żarówki jest przycisk „od nowa” (podgląd wzorca jest stale widoczny). (4) Dźwięki: przyłożenie fragmentu = „snap”, kafelek trafia na miejsce = „match”, zwykła zamiana = szelest.

**ADR-018 · Strefa rodzica (M6).** (1) Bramka S08 jest nakładką w tym samym oknie, nie `Dialog` — osobne okno dialogu pokazuje paski systemowe i zabiera fokus trybowi immersyjnemu. (2) Nowe klucze ustawień: `gate_failures`, `gate_lockout_level` (eskalacja blokady przetrwa restart), `recovery_hash`/`recovery_salt` (kod odzyskiwania tylko jako hash), `always_require_pin`. (3) Po „Nie pamiętam PIN” bramka od razu prowadzi przez nowy PIN ×2 i pokazuje nowy kod odzyskiwania. (4) Zdjęcia z Photo Pickera i aparatu dekodowane `ImageDecoder` (orientacja EXIF uwzględniona, do 2048 px na czas edycji); kadr i obrót w aplikacji (ADR-007). Wynik z pickera nawiguje bez `navigateOnce`, bo przychodzi przed powrotem aktywności do stanu RESUMED. (5) S11 (Ustawienia) i S14 (Postępy) są w M6 tylko nagłówkami — wypełniają je M7–M9. „Wyjdź z kiosku” pojawi się w menu w M7.

**ADR-019 · Kiosk (M7).** (1) Wpis HOME jest osobnym `activity-alias` włączanym placeholderem manifestu tylko w buildzie z kioskiem (release albo debug z `-Pkiosk=true`) — telefon dewelopera nie dostaje FamilyGames jako launchera. (2) Zgodnie z domyślnymi odpowiedziami Q3/Q4: ekran blokady wyłączony (`setKeyguardDisabled`), brak menu zasilania (`LOCK_TASK_FEATURE_NONE`). (3) Biała lista Lock Task dla Photo Pickera i aparatu włącza się, gdy na ekranie jest dowolny ekran strefy rodzica, i wyłącza przy wyjściu oraz przy każdym starcie aplikacji. (4) Akcje krytyczne (wyjście z kiosku, usunięcie Device Owner z potwierdzeniem słowem „USUŃ”, czyszczenie postępów, zmiana PIN) zawsze przez PIN.

**ADR-020 · Aktualizacje (M8).** (1) ETag zapisywany tylko przy odpowiedzi „aktualna” — gdy jest nowsza wersja, następne sprawdzenie pobiera wydanie w całości (inaczej 304 po restarcie ukryłby nieukończoną aktualizację). (2) `UpdateWorker` korzysta z `KoinComponent`, bez `koin-androidx-workmanager` (mniej zależności). (3) Instalacja z menu dopiero po 10 s bez dotyku i za ekranem „Za chwilę wracamy!”; nigdy w trakcie gry. (4) Nowe zależności: OkHttp 5, WorkManager, MockWebServer (testy).

**ADR-021 · Kategoria „Pojazdy”.** Na prośbę właściciela czwarta wbudowana kategoria (koparka, traktor, kombajn, samochód, autobus, wóz strażacki, wywrotka, pociąg); `BuiltInContent.VERSION = 2`, więc istniejące instalacje dostaną ją przy starcie bez duplikatów.

**ADR-022 · Nazwa „Gabi Gry”.** Na prośbę właściciela nazwa widoczna dla użytkownika (ikona, logo S01, strefa rodzica, kreator) to „Gabi Gry”. Identyfikatory techniczne zostają: `applicationId`/pakiet `com.familygames` (Q1), nazwy plików wydań `familygames-*.apk`, repozytorium wydań `familygames-releases` — zmiana pakietu po pierwszej instalacji w kiosku wymagałaby ponownego provisioningu tabletu.

**ADR-023 · Nowe gry M10–M18 — odstępstwa od docs/12.** (1) Domyślny przedział wieku to 2,5–5 lat (`PRESCHOOL`), nie 1–2,5 — dziecko ma 2,5–4 lata (Q8). (2) `GameEntry` ma zakres wieku od–do (`AgeRange`) zamiast zbioru `AgeBand`; gra jest widoczna, gdy jej zakres styka się z wybranym przedziałem lub na niego nachodzi. (3) Nagrania początków pytań („Gdzie jest…”, „Ile jest…”, „Co tu nie pasuje?”, „A kuku!”) są w S17 „Gry” w karcie „Głos w grach”, nie w S11 — rodzic ma je obok gier, których dotyczą. (4) Memo dźwiękowe: dźwięki ilustracji wbudowanych mówi syntezator mowy (onomatopeje „miau miau”, „brum brum”), zamiast plików OGG CC0 — bez zewnętrznych licencji i pobierania; nagranie rodzica ma zawsze pierwszeństwo, a bez polskiego syntezatora grają tylko zdjęcia z nagraniem. (5) „Gdzie jest…?”: 4 zdjęcia w jednym rzędzie zamiast siatki 2×2 — karty są większe. (6) „A kuku”: jedno okienko, bez przełącznika 1/2. (7) „Zdrapka”: delikatny kontur pod mgiełką włącza się sam dla przedziału 1–2,5 roku zamiast osobnej opcji. (8) S15: kolejność kroków zmienia się strzałkami „wcześniej/później”, nie przeciąganiem. (9) „Ile jest?”: liczenie słowami tylko syntezatorem (bez nagrań `num_1…5`), bez syntezatora — stuknięcia o rosnącym tonie. (10) „Kolorowanki”: 12 obrazków to wielokąty w kodzie (`ColoringPages`), dotknięcie wypełnia obszar wskazany testem punktu w wielokącie (wariant preferowany w docs/12), rysowanie warstwowe (obszar po obszarze), pędzel jednej grubości; tryb pędzla przełącznikiem dla 2,5–5 lat. (11) „Do koszyczków”: kategorie zawsze losowane — wybór par przez rodzica zostaje na później.

**ADR-024 · Cymbałki bez pentatoniki.** Sztabki strojone C D E F G A C' D' zamiast pentatoniki C D E G A C' D' E': melodia „Wlazł kotek na płotek” (G4) wymaga dźwięku F. Brak H, więc w losowych uderzeniach nie ma trytonu; nuty syntezowane jak pozostałe dźwięki (`tools/make_sfx.py`).

**ADR-025 · Jedno ziarno losowości.** Wszystkie generatory (rozdania, rundy, bramka) dostają jedną instancję `Random` z ziarnem z `SecureRandom` zamiast `Random.Default`, którego sekwencja w procesach uruchamianych z zygote potrafi się powtarzać po każdym starcie aplikacji.

**ADR-026 · Test migracji bazy.** `MigrationTestHelper` pod Robolectric czyta zasoby wariantu aplikacji, więc schematy z `app/schemas` są podpięte do zasobów wariantu `debug` (nie trafiają do wydania), a nazwa bazy w teście to pełna ścieżka (Room 2.8 porównuje ją z rozwiązaną ścieżką). Migracja v1→v2 dodaje od razu wszystkie kolumny i tabele nowych gier (`promptAudioPath`, `countable`, `sequences`, `sequence_steps`).

**ADR-027 · Bramka rodzica w menu — klucz na każde otwarcie.** ViewModel bramki ma klucz unikalny dla każdego otwarcia (czas otwarcia). Wcześniej licznik wracał do 0 po przejściu bramki, więc drugie otwarcie w tym samym wejściu do menu dostawało „zużyty” ViewModel i bramka się nie pokazywała.

## Otwarte pytania (do właściciela produktu — odpowiedzi wpisz tutaj)

| # | Pytanie | Domyślne założenie, jeśli brak odpowiedzi | Blokuje |
|---|---|---|---|
| Q1 | Docelowy `applicationId`? (`com.familygames` ze specyfikacji czy np. własna domena odwrotnie `pl.<firma>.familygames`) Po pierwszej instalacji z DO zmiana = reinstalacja i ponowny provisioning. | **Odp. 2026-11-10:** `com.familygames` (bez zmian) | M0 |
| Q2 | Właściciel/nazwa repozytorium GitHub. Kod publiczny czy prywatny? (prywatny → osobne publiczne repo na Releases) | **Odp. 2026-11-10:** OK — prywatne repo kodu + publiczne `adam-czop/familygames-releases` | M8 |
| Q3 | Wyłączyć ekran blokady (keyguard) na tablecie? | tak, wyłączony (tablet tylko dla dziecka, w domu) | M7 |
| Q4 | Menu zasilania w kiosku: ukryte czy dostępne? | ukryte (`LOCK_TASK_FEATURE_NONE`) | M7 |
| Q5 | Kopia zapasowa zdjęć (Google backup wyłączony z powodu prywatności)? | brak w v1; eksport ZIP w backlogu | — |
| Q6 | Wyciszenie w S01 ma wyciszać też nagrania głosu rodzica? | tak, wycisza wszystko | M3 |
| Q7 | Model docelowego tabletu i wersja Androida? | 10–11", Android 13+ | M9 |
| Q8 | Wiek dziecka? (wpływa na domyślny poziom, podpisy na kartach, czas pokazu pomyłki) | **Odp. 2026-11-10:** OK — 2,5–4 lata: start 2×2, podpisy włączone | M2 |
| Q9 | Ilustracje wbudowane: generuje agent (proste wektory) czy dostarczacie własne? | **Odp. 2026-11-10:** agent rysuje; właściciel potem dogrywa własne zdjęcia do Memorków i Puzzli | M1 |
| Q10 | Tylko język polski? | tak, ale wszystkie teksty w `strings.xml` (gotowe pod tłumaczenia) | — |
| Q11 | Czy kafel „Wkrótce” ma być widoczny dla dziecka? (może frustrować, bo nic nie robi) | widoczny, ale bez reakcji poza lekkim potrząśnięciem | M2 |
