# 08 · Design system (v2 — stonowany)

Źródło wizualne: `mockups/index.html` (tokeny w `:root`). Poniżej odwzorowanie dla Compose.

## 0. Zasada przewodnia
**Interfejs jest cichym tłem, kolor wnoszą zdjęcia.** Layout używa ciepłych neutralnych szarości, jednego przygaszonego akcentu (szałwia) i jednego pomocniczego (mglisty błękit) wyłącznie do odróżnienia gier w menu. Miodowy odcień jest zarezerwowany dla gwiazdek i łagodnej informacji „to nie ta para”. Żadnych nasyconych barw, gradientów ani wielokolorowych napisów w UI. Ikony są liniowe, w jednym kolorze. Emoji nie są elementami interfejsu (w makietach zastępują tylko zdjęcia).

## 1. Dwa motywy, jedna paleta
- **KidTheme** — strefa dziecka (S01–S07): ciepła złamana biel, białe karty z cienką linią, miękkie cienie, czcionka Fredoka w średniej grubości.
- **ParentTheme** — strefa rodzica (S08–S14): Material 3 na tej samej palecie, czcionka Nunito, gęstszy układ. Odróżnienie stref daje typografia i gęstość, nie kolor.

## 2. Kolory
```kotlin
object Palette {
    // neutralne
    val Bg = Color(0xFFF6F4F0)        // tło ekranów
    val Bg2 = Color(0xFFEDE9E3)       // tacki, tył kart, wypełnienia drugiego planu
    val Surface = Color(0xFFFFFFFF)   // karty, kafle, przyciski
    val Line = Color(0xFFE3DED6)      // obrysy 1 dp
    val Line2 = Color(0xFFD6D0C6)     // obrysy przerywane, nieaktywne
    val Ink = Color(0xFF3B3936)       // tekst główny, ikony aktywne
    val Ink2 = Color(0xFF8B867F)      // tekst drugorzędny (strefa dziecka, ≥ 22 sp)
    val Ink3 = Color(0xFFB4AEA5)      // elementy wyłączone, dekoracja
    // akcent
    val Accent = Color(0xFF7E9C8C)    // szałwia: dopasowanie, zaznaczenie, przycisk główny
    val AccentInk = Color(0xFF4E6A5C) // tekst/ikona w kolorze akcentu, przycisk główny w strefie rodzica
    val AccentSoft = Color(0xFFE5ECE7)// tła zaznaczeń, chipy wybrane
    // pomocnicze
    val Mist = Color(0xFF7F95A6); val MistSoft = Color(0xFFE6ECF0)     // tylko kafel „Puzzle”
    val Honey = Color(0xFFD6B47A); val HoneySoft = Color(0xFFF4EBDA)   // gwiazdki, łagodna pomyłka
    // tylko strefa rodzica
    val ParentInk2 = Color(0xFF6F6A63); val Danger = Color(0xFFA9573F)
}
```
Zasady:
- Na jednym ekranie dziecka maksymalnie **jeden** kolor akcentu + neutralne (wyjątek: S01 z rozróżnieniem gier szałwia/błękit).
- Czerwień (`Danger`, przygaszona terakota) **tylko** w strefie rodzica, dla akcji destrukcyjnych.
- Placeholdery zdjęć i tła miniatur: bardzo jasne neutralne odcienie (`#F1EDE7`, `#EDF0F1`, `#EEF1EC` …), żeby zdjęcie było jedynym kolorowym elementem.
- Tryb ciemny: nieobsługiwany w v1 (tablet dziecka, stały jasny motyw; ADR-012).

Kontrast (WCAG, policzony):
| para | kontrast | użycie |
|---|---|---|
| Ink / Bg | 10.5:1 | cały tekst główny |
| Ink2 / Bg | 3.3:1 | tylko duży tekst ≥ 22 sp w strefie dziecka (podpisy kart, liczniki) |
| ParentInk2 / Surface | 5.4:1 | tekst drugorzędny rodzica |
| AccentInk / AccentSoft | 4.9:1 | tekst wybranych chipów / nawigacji |
| biały / AccentInk | 5.9:1 | przyciski główne rodzica |
| biały / Accent | 3.0:1 | tylko ikony ≥ 44 dp (przycisk „zagraj ponownie”), nigdy tekst |

## 3. Typografia
Fonty (SIL OFL; pliki w `mockups/assets/fonts` służą tylko makietom — do aplikacji pobierz statyczne TTF z Google Fonts do `res/font/`):
- `fredoka_medium.ttf` (500), `fredoka_semibold.ttf` (600)
- `nunito_regular/semibold/bold/extrabold.ttf` (400/600/700/800)

| styl | font | rozmiar | kolor | użycie |
|---|---|---|---|---|
| KidDisplay | Fredoka 600 | 60 sp | Ink + „Games” AccentInk | logo |
| KidTitle | Fredoka 600 | 36 sp | Ink | tytuły ekranów dziecka |
| KidTile | Fredoka 600 | 36 sp | Ink | nazwy gier |
| KidLabel | Fredoka 500 | 22–26 sp | Ink2 | podpisy kart, liczniki, kategorie |
| ParentH1 | Nunito 800 | 28 sp | Ink | nagłówek ekranu |
| ParentH2 | Nunito 800 | 18 sp | Ink | nagłówki kart |
| ParentBody | Nunito 600 | 15–16 sp | ParentInk2 / Ink | treść |
| ParentCaption | Nunito 800 | 13 sp, caps, +0.6 sp | ParentInk2 | etykiety pól |

## 4. Kształty, linie, cienie (`Dimens`)
| token | wartość |
|---|---|
| RadiusXL | 32 dp (kafle gier, okno sukcesu 36 dp) |
| RadiusL | 24 dp (karty Memorków, kafle poziomów) |
| RadiusM | 18 dp (plansze puzzli, przyciski trudności) |
| RadiusS | 12 dp (pola, przyciski rodzica) |
| Stroke | 1 dp `Line` na każdej karcie/kafelku; zaznaczenie = 3–4 dp `Accent` |
| TouchPrimary | 96 dp |
| TouchSecondary | 64 dp |
| ScreenPadding | 32 dp poziomo, 28 dp góra |
| GridGap | 28 dp (Memorki), 8 dp (Swap) |

**Cień miękki** (`Modifier.softShadow()`): dwie warstwy — 1 dp/2 dp blur 5% + 6 dp/18 dp blur 5% koloru `Ink`. Wersja podniesiona (okno sukcesu, przeciągany fragment): 16 dp/36 dp blur 8%. Brak „twardych” cieni przesuniętych w dół. Wciśnięcie = skala 0.97, bez zmiany cienia.

## 5. Ruch
Spokojnie i krótko — ruch potwierdza akcję, nie zabawia.
| animacja | spec |
|---|---|
| tap feedback | scale 0.97, `spring(dampingRatio = 0.7, stiffness = 500)` |
| obrót karty | 350 ms `FastOutSlowInEasing` |
| dopasowanie | ramka Accent pojawia się (alpha 0→1, 200 ms) + scale 1 → 1.04 → 1 |
| snap fragmentu | `spring(0.75, 400)` |
| powrót na tackę | `tween(300)` |
| zamiana Swap | `tween(250)` |
| gwiazdki w S04 | stagger 200 ms, alpha + scale 0.8 → 1 (bez „przeskoku”) |
| konfetti | 2 s, ~40 cząstek, opacity 0.75, kolory: Accent, Honey, Mist, `#CFC6BA` |
Szanuj „Usuń animacje” z ustawień dostępności — wtedy czasy × 0.3, bez konfetti.

## 6. Dźwięki (`SoundPool`, `res/raw/`)
Ciche, miękkie, bez jaskrawych „dzwonków arcade”.
| id | kiedy | charakter |
|---|---|---|
| sfx_tap | każdy dotyk przycisku | bardzo krótkie, miękkie „tuk”, 40–60 ms |
| sfx_flip | obrót karty | delikatny szelest papieru |
| sfx_match | para / fragment na miejscu | ciepłe „ding” (dzwonek drewniany / marimba) |
| sfx_soft | pomyłka | neutralny, cichy „bloop” (NIE buczek) |
| sfx_snap | fragment dosunięty | miękkie kliknięcie |
| sfx_star | każda gwiazdka w S04 | wznoszący się ton marimby |
| sfx_win | wygrana | krótka melodyjka (≤ 2 s), bez braw i fanfar |
Źródła: biblioteki CC0 (np. Kenney.nl — CC0) — licencje w `res/raw/LICENSES.md`. OGG, 44.1 kHz, normalizacja do −18 LUFS. Głośność efektów 0.5, nagrania rodzica 1.0.

## 7. Ikony
- Wszystkie ikony liniowe, stroke 2–2.5 dp (na 24 dp), zaokrąglone końce, jeden kolor (`Ink2` domyślnie, `AccentInk` gdy aktywne). Źródło: Material Symbols Rounded (weight 300–400) jako VectorDrawable, tylko potrzebne.
- Ikony gier w menu: liniowe 104 dp w kole 176 dp (`AccentSoft` / `MistSoft`) — jak w S01. Bez emoji, bez wielokolorowych ilustracji.
- Gwiazdki: wypełnione `Honey` / nieaktywne `Line`.

## 8. Komponenty wspólne (`presentation/common`)
`KidTile`, `BigRoundButton(icon, size = 96.dp)` (biały, obrys `Line`, ikona `Ink2`), `HoldToActivateButton(durationMs = 3000)` (pierścień postępu w `Accent`), `StarsRow(count, max = 3)`, `PairsCounter` (kropki 20 dp: `Line` → `Accent`), `WinOverlay` (jasna mgiełka `Bg @ 72%` + blur, nie przyciemnienie), `ConfettiCanvas`, `PhotoImage(source, thumb = true)`, `ParentScaffold(selectedNav)`, `PinPad`, `ToggleRow`, `KeyValueRow`.
Każdy komponent ma `@Preview` na tle właściwego motywu.
