# HearLink

Natywna aplikacja Android (Kotlin + Jetpack Compose + C++ Oboe/AAudio) wspomagająca słyszenie podczas rozmów terapeutycznych poprzez lokalną transmisję mowy w czasie rzeczywistym z mikrofonu smartfona na słuchawki Bluetooth z przewodnictwem kostnym pacjenta.

## Najważniejsze funkcje
- **Niskoopóźnieniowy silnik C++ (Oboe / AAudio)** z bezblokującym buforem pierścieniowym SPSC.
- **Lokalny łańcuch DSP**: filtr górnoprzepustowy, redukcja szumów stacjonarnych, korekcja wyraźności mowy, AGC, łagodny rozruch i bezpieczny limiter szczytowy.
- **Inteligentne trasowanie Bluetooth**: priorytet dla LE Audio i HFP (`setCommunicationDevice`), tryb awaryjny A2DP z ostrzeżeniem oraz blokada wbudowanego głośnika telefonu.
- **Diagnostyka i test akustyczny (*Loopback Ping*)**: oddzielny pomiar czasu DSP, buforów potoku Oboe i rzeczywistego opóźnienia *end-to-end*.
- **Prywatność i praca w tle**: zero nagrywania i zero chmury; usługa pierwszoplanowa typu `microphone` z przyciskiem natychmiastowego zatrzymania.

## Wydania i historia wersji (GitHub Releases + CI/CD)

Repozytorium korzysta z semantycznego wersjonowania (**SemVer**), pliku [`CHANGELOG.md`](CHANGELOG.md) oraz dwóch automatycznych potoków GitHub Actions:
- **Continuous Integration** (`.github/workflows/ci.yml`): uruchamia testy jednostkowe/Robolectric oraz kompiluje pakiety Debug i Release APK przy każdym `push` i `pull_request` do gałęzi `main`/`master`.
- **Release Pipeline** (`.github/workflows/release.yml`): buduje przetestowane pliki `HearLink-vX.Y.Z-release.apk`, `HearLink-vX.Y.Z-debug.apk` oraz sumy `SHA-256`, generuje opis wydania z `CHANGELOG.md` + automatycznych notatek GitHuba i dołącza je bezpośrednio jako zasoby (**Assets**) w **GitHub Releases**.

### Jak utworzyć nowe wydanie
1. **Przez tag Git (zalecane):**
   ```bash
   # Zaktualizuj CHANGELOG.md sekcją [1.0.1], zatwierdź zmiany i wypchnij tag:
   git tag -a v1.0.1 -m "HearLink v1.0.1"
   git push origin main --tags
   ```
2. **Przez interfejs GitHub (Workflow Dispatch):**
   Wejdź w **Actions → Release HearLink (Debug & Release APKs) → Run workflow**, wpisz numer wersji (np. `1.0.1`) i uruchom potok.

### Konfiguracja podpisu produkcyjnego Release APK (opcjonalna)
Domyślnie potok CI generuje tymczasowy klucz podpisu, aby kompilacje Release zawsze kończyły się sukcesem. Aby podpisywać wydania własnym kluczem produkcyjnym (`.jks` z aliasem `upload`), dodaj w **Settings → Secrets and variables → Actions** trzy sekrety:
- `RELEASE_KEYSTORE_BASE64`: plik `.jks` zakodowany w Base64 (`base64 -w 0 my-upload-key.jks`)
- `RELEASE_STORE_PASSWORD`: hasło magazynu kluczy
- `RELEASE_KEY_PASSWORD`: hasło klucza `upload`

## Budowanie lokalne
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
