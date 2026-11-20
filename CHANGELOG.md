# Historia zmian (Changelog)

Wszystkie istotne zmiany w aplikacji **HearLink** są dokumentowane w tym pliku.
Format jest oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), a wersjonowanie jest zgodne z [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Dodano
- **Tryb Ultra-Niski (cel 10–20 ms po stronie telefonu) i Auto-XRun Guard**: start wyjścia Oboe od `1× burst`, natychmiastowa synchronizacja pierścienia SPSC przy pierwszym wywołaniu zwrotnym (*zero-lag startup sync*), otwieranie strumieni w natywnym próbkowaniu sprzętu (bez wymuszonego resamplingu AudioFlinger) z lekkim resamplerem liniowym C++ tylko przy różnicy `inSR != outSR` oraz automatyczne podbijanie bufora (`+1×` do `4×`), jeśli wystąpią ≥ 3 underruny w oknie 2 s.
- **Wybór fizycznego mikrofonu i presetu HAL (`InputPreset`)**: lista mikrofonów telefonu (dolny, górny, tylny, USB, BT) z metadanymi `MicrophoneInfo` oraz przełącznik trybu wejścia (`Unprocessed` domyślnie bez DSP producenta, `Generic`, `VoiceRecognition`, `VoiceCommunication`) i profilu A2DP (`Game Low-Latency` / `Media`), stosowane na żywo bez rozłączania słuchawek.
- **Karta „Specyfikacja sprzętu i standardów” w Ustawieniach**: pełny odczyt SoC, Android/ABI, flag `FEATURE_AUDIO_LOW_LATENCY`/`PRO`/`UNPROCESSED`, wykrytych mikrofonów, wsparcia LE Audio / BLE 2M PHY / Coded PHY, wynegocjowanego kodeka A2DP i formatów HAL oraz przycisk jednoklikowego kopiowania raportu Markdown do schowka.
- **Rozdzielona telemetria etapów opóźnienia**: osobny podgląd opóźnień bufora wejścia Oboe, kolejki SPSC, DSP, bufora wyjścia Oboe i pełnej pętli Bluetooth.

### Zmieniono
- Skompensowano filtry korekcji mowy oraz `LoopbackDetector` pod różne natywne częstotliwości próbkowania wejścia i wyjścia (np. 48 kHz mikrofon → 16 kHz HFP lub 44,1 kHz A2DP).

## [1.0.0] - 2026-11-20

### Dodano
- **Natywny silnik audio C++ (Oboe + AAudio)**: niskoopóźnieniowe przechwytywanie głosu z wbudowanego mikrofonu (`48 kHz` mono, `Float32`) połączone bezblokującą kolejką pierścieniową SPSC i adaptacyjną kontrolą dryftu zegara.
- **Lokalny łańcuch DSP**: filtr górnoprzepustowy (HPF), łagodna redukcja szumów stacjonarnych z zachowaniem spółgłosek, korekcja wyraźności mowy, bramkowany AGC, łagodny rozruch (*Soft-Start Ramp*) oraz twardy limiter chroniący słuch pacjenta.
- **Inteligentne zarządzanie Bluetooth**: priorytet dla Bluetooth LE Audio i HFP (`setCommunicationDevice()` na Androidzie 12+), tryb awaryjny A2DP z wyraźnym ostrzeżeniem, blokada wbudowanego głośnika telefonu oraz automatyczne wznawianie trasy po chwilowym rozłączeniu słuchawek.
- **Kliniczny interfejs w języku polskim (Jetpack Compose / Material 3)**: ekran powitalny z oświadczeniem o bezpieczeństwie i listą kontrolną uprawnień, główny panel terapeuty (obsługa jedną ręką, presety *Standardowy* / *Wyraźna mowa* / *Cichy pokój*, miernik poziomu dBFS), ustawienia zaawansowane oraz motywy jasny i ciemny.
- **Praca w tle i prywatność**: `ForegroundService` typu `microphone` ze stałym powiadomieniem i przyciskiem natychmiastowego zatrzymania (`ZATRZYMAJ`); brak nagrywania, chmury i transkrypcji.
- **Diagnostyka i pomiar opóźnień**: raport wydajności strumieni Oboe/AAudio, liczniki XRun, użycie CPU i baterii, wbudowany 12-punktowy plan testów klinicznych oraz kierowany test impulsu akustycznego (*Loopback Ping*) mierzący rzeczywiste opóźnienie *end-to-end*.
- **Automatyzacja wydań (GitHub Actions)**: potoki CI/CD kompilujące pakiety **Debug APK** oraz **Release APK** wraz z sumami kontrolnymi SHA-256 jako zasoby wydań GitHub Releases.
