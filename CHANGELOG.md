# Changelog

Format: one `## [x.y.z]` section per release; lines starting with `- ` become the release notes and the
`changelog` array in `update.json` (see `.github/workflows/release.yml`).

## [Unreleased]
- Zamiast nagrań głosu: przy zdjęciu wpisujesz nazwę i opcjonalnie własne pytanie, a gry czyta lektor (syntezator mowy). Aplikacja nie używa już mikrofonu; dotychczasowe nagrania zostają usunięte.
- Zdrapka: nazwa zdjęcia pada dopiero po zdrapaniu całej mgiełki, a mgiełka jest wyraźnie odróżniona od tła.
- Nowa gra Labirynt: prowadzenie piłeczki paluszkiem po ścieżce do zdjęcia z rodziny — 4 poziomy, od prostej drogi po prawdziwy labirynt.

## [0.1.0] - 2026-11-11
- Pierwsza wersja Gabi Gry: 13 gier na rodzinnych zdjęciach — Memorki, Puzzle, Gdzie jest?, A kuku, Zdrapka, Do koszyczków, Co nie pasuje?, Memo dźwiękowe, Bańki, Cymbałki, Po kolei, Ile jest?, Kolorowanki.
- 32 wbudowane ilustracje (Nasz dom, Zwierzęta, Zabawki, Pojazdy), żeby gry działały od razu.
- Strefa rodzica za bramką (zadanie lub PIN): zdjęcia z galerii i aparatu z kadrowaniem i nagraniem głosu, kategorie, sekwencje, wybór gier i wieku dziecka, postępy i rysunki.
- Tryb kiosku (Device Owner): aplikacja jako ekran główny tabletu, wyjście tylko PIN-em.
- Automatyczne aktualizacje z GitHub Releases z weryfikacją podpisu.
