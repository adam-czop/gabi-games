# CLAUDE.md — zasady pracy w repozytorium FamilyGames Tablet

## Kontekst w jednym akapicie
Aplikacja Android (tablet, landscape) dla dziecka 2–5 lat. Gry: Memorki i Puzzle na zdjęciach rodzinnych. Działa jako kiosk (Device Owner + Lock Task), aktualizuje się sama z GitHub Releases. Rodzic zarządza zdjęciami w panelu za bramką rodzica. Brak reklam, brak internetu poza sprawdzaniem aktualizacji, brak kont, brak analityki.

## Gdzie co jest
- Plan pracy i checkboxy: `docs/09_IMPLEMENTATION_PLAN.md` — **źródło prawdy o postępie**.
- Wymagania szczegółowe: `docs/01`–`docs/08`, testy: `docs/10`, decyzje: `docs/11`.
- Makiety: `mockups/png/Sxx_*.png` + adnotacje w `mockups/index.html`. Identyfikatory ekranów (S01…S14) używaj w commitach i komentarzach.
- Gotowe fragmenty konfiguracji: `config/` (version catalog, workflow CI, XML-e Androida).

## Stack (nie zmieniaj bez ADR w docs/11)
Kotlin · Jetpack Compose (Material 3) · Navigation Compose z typowanymi trasami (`@Serializable`) · Koin · Room (KSP) · DataStore Preferences · Coil 3 · SoundPool · OkHttp + kotlinx.serialization · WorkManager · Coroutines/Flow.
minSdk 28, target/compileSdk 36. JDK 17. Wersje bibliotek w `gradle/libs.versions.toml` — przy M0 podbij do najnowszych stabilnych.

## Zasady kodu
- Kod, identyfikatory, komentarze i commity **po angielsku**. Teksty UI **po polsku**, wyłącznie w `res/values/strings.xml` (żadnych literałów w Composable).
- Architektura: `presentation` → `domain` ← `data`. `domain` nie importuje Androida. ViewModel wystawia jeden `StateFlow<XxxUiState>` i przyjmuje zdarzenia przez `fun onEvent(event: XxxEvent)`. Efekty jednorazowe (dźwięk, nawigacja) przez `Channel`/`SharedFlow`.
- Logika gier = czyste klasy w `domain` (testowalne bez Androida, losowość przez wstrzykiwany `Random`, czas przez `TestScope`/wstrzykiwany dispatcher).
- Composable bezstanowe (state hoisting). Każdy ekran ma `@Preview` w rozdzielczości tabletu (`device = "spec:width=1280dp,height=800dp,dpi=240"`).
- Rozmiary dotyku w strefie dziecka: główne ≥ 96 dp, pomocnicze ≥ 64 dp. Nigdy czerwonych komunikatów błędu ani „negatywnych” dźwięków w strefie dziecka.
- Żadnych nowych zależności bez wpisu w `docs/11`. Żadnych SDK analitycznych, reklamowych, crash-reportingu z siecią.
- Nie loguj ścieżek zdjęć ani nagrań w release (`Timber` tylko w debug).

## Kiosk w trakcie developmentu
- Flaga `BuildConfig.KIOSK_ENABLED` = `false` w `debug`, `true` w `release`. Nie blokuj emulatora/telefonu dewelopera.
- Do testów kiosku używaj osobnego emulatora i komendy z `docs/06` (provisioning Device Owner).

## Definition of Done (każdy milestone)
1. `./gradlew lint testDebugUnitTest assembleDebug` przechodzi bez błędów (ostrzeżenia lint nowe = do poprawy lub uzasadnienia).
2. Nowa logika domenowa ma testy jednostkowe (wymagania w `docs/10`).
3. Ekrany zgodne z makietą (układ, kolory z `docs/08`, zachowania z adnotacji).
4. Checkboxy w `docs/09` odhaczone, ewentualne odstępstwa dopisane jako ADR w `docs/11`.
5. Commit `feat(Mx): …` i krótki raport: co zrobione, co odbiega od dokumentacji, co dalej. Potem **zatrzymaj się** i czekaj na przegląd.

## Bezpieczeństwo
- Nigdy nie commituj keystore'a, haseł ani tokenów. Podpisywanie release tylko w CI (sekrety GitHub) lub lokalnie przez `keystore.properties` w `.gitignore`.
- Updater instaluje wyłącznie APK z poprawnym SHA-256 **i** tym samym certyfikatem podpisu co zainstalowana aplikacja.
- PIN rodzica przechowywany tylko jako hash PBKDF2 + sól.
