# Specyfikacja Techniczna Projektu: FamilyGames (Tablet App)

## 1. Przegląd Projektu (Executive Summary)
* **Nazwa robocza**: FamilyGames Tablet
* **Przeznaczenie**: Dedykowana, zamknięta aplikacja na tablet dla małego dziecka z interaktywnymi grami edukacyjnymi opartymi na rodzinnych zdjęciach i otoczeniu domowym.
* **Główne cechy**:
  * Pełna blokada przed wyjściem dziecka (Kiosk / Guided Access).
  * Gry początkowe: **Memorki (Memory Match)** oraz **Puzzle z kafelków (Jigsaw/Grid Slide/Snap)**.
  * System bezprzewodowych aktualizacji z GitHuba (OTA / Self-Updater).
  * Panel rodzica chroniony prostym testem matematycznym / kodem PIN do dodawania nowych pakietów zdjęć.
  * Skalowalna architektura modularna pod przyszłe mini-gry (np. kolorowanki, memo dźwiękowe).

---

## 2. Stack Technologiczny i Trendy Android (2025/2026)

* **Język główny**: **Kotlin** (100% Kotlin First).
* **UI Toolkit**: **Jetpack Compose** (Nowoczesny, deklaratywny UI, zoptymalizowany pod kątem responsywności na tabletach).
* **Architektura**: Clean Architecture + MVI/MVVM z `StateFlow` i Coroutines.
* **Baza lokalna i stan**: **Room Database** (zarządzanie pakietami zdjęć, poziomami, statystykami) + DataStore.
* **Ładowanie i obróbka multimediów**: **Coil Compose** (lekki, nowoczesny silnik renderowania grafik w Compose).
* **Dźwięk / Efekty Audio**: Android `SoundPool` (błyskawiczne odtwarzanie reakcji na kliknięcia, sukcesy i animacje).
* **Wstrzykiwanie zależności**: **Koin** lub **Hilt** (rekomendowany Koin dla prostoty i szybkości wdrażania).
* **Dystrybucja aktualizacji**: Własny moduł auto-update sprawdzający **GitHub Releases API** + cicha instalacja APK (w trybie Device Owner / Kiosk).

---

## 3. Makiety UI / Rzuty Ekranów (Wireframes UX)

### 3.1. Ekran Główny (Menu Wyboru Gier)
```text
+--------------------------------------------------------------+
| [Ikona Kłódki: PIN Rodzica]                    [Głośność: ON]|
|                                                              |
|                     FAMILY GAMES 🎉                          |
|                                                              |
|        +---------------------+    +---------------------+    |
|        |      [ IKONA ]      |    |      [ IKONA ]      |    |
|        |                     |    |                     |    |
|        |     MEMORKI         |    |     PUZZLE          |    |
|        |   (Odkryj pary)     |    |  (Ułóż zdjęcia)     |    |
|        +---------------------+    +---------------------+    |
|                                                              |
|        +---------------------+                               |
|        |     [ + WIĘCEJ ]    |                               |
|        |     Wkrótce nowe!   |                               |
|        +---------------------+                               |
+--------------------------------------------------------------+
```

### 3.2. Ekran Wyboru Poziomu (Level Select)
```text
+--------------------------------------------------------------+
| [<-- Wróć]               WYBIERZ POZIOM                      |
|                                                              |
|    [ Poziom 1 ]             [ Poziom 2 ]        [ Poziom 3 ] |
|      2x2 (4 karty)            2x3 (6 kart)        3x4 (12)   |
|       ⭐ ⭐ ⭐                   ⭐ ⭐ ☆             (Zablok.)|
|                                                              |
|    Kategoria: [ Nasz Dom / Pokój / Zwierzęta ]               |
+--------------------------------------------------------------+
```

### 3.3. Ekran Gry: Memorki (Memory)
```text
+--------------------------------------------------------------+
| [<-- Menu]                 MEMORKI                   [Restart]|
|                                                              |
|        +-------+   +-------+   +-------+   +-------+         |
|        |  ? ?  |   | [Kot] |   |  ? ?  |   |  ? ?  |         |
|        +-------+   +-------+   +-------+   +-------+         |
|        +-------+   +-------+   +-------+   +-------+         |
|        | [Pies]|   |  ? ?  |   | [Kot] |   |  ? ?  |         |
|        +-------+   +-------+   +-------+   +-------+         |
|                                                              |
|   Konfetti & Dźwięk braw przy dopasowaniu pary! 🎉            |
+--------------------------------------------------------------+
```

### 3.4. Ekran Gry: Puzzle Klockowe (Snap Puzzle dla malucha)
```text
+--------------------------------------------------------------+
| [<-- Menu]                  PUZZLE                   [Pomoc] |
|                                                              |
|     +-------------------------+      +----------+            |
|     | PUSTA RAMKA             |      | Fragment |            |
|     | (Półprzezroczysty zarys |      |   #1     |            |
|     |  np. salonu / zabawki)  |      +----------+            |
|     |                         |                              |
|     |         [UPUŚĆ TU]      |      +----------+            |
|     |                         |      | Fragment |            |
|     +-------------------------+      |   #2     |            |
|                                      +----------+            |
+--------------------------------------------------------------+
```

---

## 4. Wymagania Funkcjonalne

### 4.1. Moduł Bezpieczeństwa i Blokady (Kiosk Mode)
* **Pinning aplikacji (Lock Task Mode)**: Aplikacja powinna korzystać z Android Lock Task API, aby uniemożliwić dziecku wyjście gestem lub paskiem nawigacji.
* **Parental Gate**: Wejście w ustawienia, dodawanie zdjęć lub wyjście z aplikacji wymaga rozwiązania zadania (np. *"Przesuń 3 gwiazdki"* lub kod 4-cyfrowy).
* **Brak reklam i linków zewnętrznych**.

### 4.2. Moduł: Memorki (Memory Game)
* **Logika**:
  * Siatki dopasowane do wieku: 2x2 (4 karty), 2x3 (6 kart), 2x4 (8 kart), 3x4 (12 kart).
  * Losowanie par z puli zdjęć przypisanych do danej kategorii (np. `zdjęcia_domownicy`, `przedmioty`).
  * Wyraźne animacje obrotu karty (Flip Card Animation w Jetpack Compose).
  * Blokada klikania kolejnych kart podczas animacji sprawdzania pary.

### 4.3. Moduł: Puzzle (Jigsaw / Tile Match)
* **Logika**:
  * Dwa tryby trudności:
    1. **Shape Match / Snap Grid** (dla najmłodszych): Dzielenie zdjęcia na 4 lub 6 dużych prostokątów zarysowanych na podłożu; przeciąganie palcem (Drag & Drop) na właściwe miejsce.
    2. **Grid Tap/Swap** (starszy maluch): Zamiana sąsiednich kafelków miejscami aż do ułożenia całego obrazka.
  * Wizualna podpowiedź w tle (Ghost image z niskim `alpha`).

### 4.4. Panel Rodzica (CMS Zdjęć)
* Możliwość wyboru zdjęć z galerii tabletu lub zrobienia nowego zdjęcia bezpośrednio aparatem.
* Kadrowanie zdjęcia do kwadratu (1:1) lub proporcji 4:3.
* Nadawanie etykiety słownej (opcjonalnie: nagranie własnego głosu np. *"To jest kanapa w salonie"*).

### 4.5. Mechanizm Auto-Update (GitHub Releases)
* Aplikacja w tle podczas startu sprawdza endpoint API:
  `https://api.github.com/repos/{owner}/{repo}/releases/latest`
* Porównanie `versionCode` z `build.gradle.kts`.
* Jeśli dostępna nowsza wersja:
  * Pobranie pliku `.apk` do cache aplikacji.
  * Uruchomienie `PackageInstaller` z uprawnieniami zarządzania aplikacją.

---

## 5. Architektura Kodu i Struktura Projektu

```text
app/
 ├── src/main/java/com/familygames/
 │    ├── data/
 │    │    ├── local/         # Room DB, Baza zdjęć, DataStore
 │    │    ├── repository/    # PhotoRepository, GameProgressRepository
 │    │    └── updater/       # GitHubUpdateManager, DownloadReceiver
 │    ├── domain/
 │    │    ├── model/         # CardItem, PuzzlePiece, GameLevel
 │    │    └── usecase/       # CheckMatchUseCase, GenerateGridUseCase
 │    ├── presentation/
 │    │    ├── common/        # Komponenty: BigButton, Dialogs, AudioPlayer
 │    │    ├── navigation/    # Compose Navigation Graph
 │    │    ├── menu/          # MainMenuScreen, MainViewModel
 │    │    ├── games/
 │    │    │    ├── memory/   # MemoryScreen, MemoryViewModel, CardComponent
 │    │    │    └── puzzle/   # PuzzleScreen, PuzzleViewModel, DragDropCanvas
 │    │    └── parent_panel/  # ParentGateScreen, PhotoManagerScreen
 │    └── ui/theme/           # Kolorystyka dla dzieci (duże kontrasty, pastele)
```

---

## 6. Prompting Guide dla Dewelopera & Narzędzi AI (Cursor / Claude / Copilot)

Przy implementacji poszczególnych modułów, przekazuj modelom AI precyzyjne polecenia:

1. **Dla Silnika Memorki**:
   > *"Napisz w Jetpack Compose widok `MemoryGameScreen` w architekturze MVVM. Siatka `LazyVerticalGrid`, animacja obrotu karty wykorzystująca `graphicsLayer { rotationY }`. Stan gry trzymany w `MemoryUiState` jako `StateFlow`. Zadbaj o blokadę interakcji na czas pokazywania błędnej pary."*

2. **Dla Drag & Drop w Puzzlach**:
   > *"Stwórz komponent Jetpack Compose umożliwiający przeciąganie elementów (PointerInput Drag) i upuszczanie ich na właściwe pole siatki (Snap to target). Wykorzystaj współrzędne z `onGloballyPositioned` i animację płynnego dopasowania po zbliżeniu na odległość mniejszą niż 40dp."*

3. **Dla Trybu Kiosk**:
   > *"Przygotuj klasę `KioskManager` dla Android 14+ korzystającą z `startLockTask()` oraz obsługującą ukrywanie systemowych pasków statusu i nawigacji (`WindowInsetsControllerCompat`)."*