# Gabi Gry

Zamknięta aplikacja na tablet z Androidem dla małego dziecka (2,5–4 lata): 19 gier na rodzinnych zdjęciach i ilustracjach (m.in. **Memorki**, **Puzzle**, **Gdzie jest?**, **Po kolei**, **Kolorowanki**, **Labirynt**, **Cienie**, **Kulki**, **Znajdź na obrazku**, **Czego brakuje?**, **Połówki**), tryb kiosku (Device Owner) i aktualizacje z GitHub Releases. Rodzic zarządza zdjęciami w strefie chronionej zadaniem lub PIN-em.

Pakiet techniczny: `com.familygames` (debug: `com.familygames.debug`).

## Dokumentacja

- Specyfikacja i plan: [docs/](docs/) — plan z postępem w [docs/09_IMPLEMENTATION_PLAN.md](docs/09_IMPLEMENTATION_PLAN.md), decyzje w [docs/11_DECISIONS_AND_OPEN_QUESTIONS.md](docs/11_DECISIONS_AND_OPEN_QUESTIONS.md).
- Makiety: [mockups/index.html](mockups/index.html).
- Instrukcja dla rodzica: [docs/PARENT_GUIDE.md](docs/PARENT_GUIDE.md).
- Zasady pracy w repo: [CLAUDE.md](CLAUDE.md).

## Budowanie

Wymagania: JDK 17, Android SDK z platformą 37 (compileSdk) — `targetSdk` to 36.

```bash
./gradlew lint testDebugUnitTest assembleDebug     # Definition of Done każdego etapu
./gradlew assembleDebug -Pkiosk=true               # debug z włączonym kioskiem (tylko emulator/tablet testowy)
./gradlew assembleRelease -PversionName=1.0.0      # release; podpis z keystore.properties lub zmiennych CI
```

Build `debug` ma kiosk wyłączony, żeby nie blokować telefonu dewelopera. Treści wbudowane generują skrypty: [tools/builtin_art.py](tools/builtin_art.py) (ilustracje, wymaga Chrome i Pillow) i [tools/make_sfx.py](tools/make_sfx.py) (dźwięki, wymaga ffmpeg).

## Tryb kiosku (Device Owner)

Na tablecie po resecie fabrycznym, **bez konta Google**, z włączonym debugowaniem USB:

```bash
adb install gabi-gry.apk
adb shell dpm set-device-owner com.familygames/com.familygames.kiosk.FamilyDeviceAdminReceiver
```

Szczegóły: [docs/06_KIOSK_AND_PARENT_GATE.md](docs/06_KIOSK_AND_PARENT_GATE.md) §2. Wyjście z kiosku: strefa rodzica → Ustawienia → „Wyjdź z kiosku (PIN)”.

## Wydania i aktualizacje

Tablety pobierają aktualizacje z **publicznego** repozytorium wydań `adam-czop/familygames-releases` (kod może zostać prywatny). Właściciel/nazwę zmienisz parametrami `-PupdateRepoOwner` / `-PupdateRepoName`.

Jednorazowa konfiguracja:

1. Utwórz publiczne repozytorium `familygames-releases` z jednym commitem (np. README).
2. Wygeneruj klucz podpisu i **zachowaj go na zawsze** — każda aktualizacja musi być podpisana tym samym kluczem:
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias gabigry -keyalg RSA -keysize 4096 -validity 36500
   ```
3. W repozytorium z kodem (Settings → Secrets and variables → Actions) dodaj sekrety `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `GH_RELEASES_TOKEN` (fine-grained PAT z uprawnieniem *Contents: write* tylko do repozytorium wydań) oraz zmienną `RELEASES_REPO` = `adam-czop/familygames-releases`.

Wydanie: uzupełnij sekcję `## [x.y.z]` w [CHANGELOG.md](CHANGELOG.md), potem

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Workflow [.github/workflows/release.yml](.github/workflows/release.yml) zbuduje i podpisze APK, policzy SHA-256, utworzy `update.json` i opublikuje Release. Tablety sprawdzają aktualizacje przy starcie (co najmniej co 6 h) i co 12 h w tle; instalacja odbywa się tylko na menu po 10 s bezczynności, na prośbę rodzica albo w nocy — nigdy w trakcie gry. Szczegóły: [docs/07_AUTO_UPDATE.md](docs/07_AUTO_UPDATE.md).
