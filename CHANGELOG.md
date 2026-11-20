# Historia zmian (Changelog)

Wszystkie istotne zmiany w aplikacji **HearLink** są dokumentowane w tym pliku.
Format jest oparty na [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), a wersjonowanie jest zgodne z [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planowane / W przygotowaniu
- Walidacja terenowa opóźnień dla kolejnych modeli słuchawek z przewodnictwem kostnym.

## [1.0.0] - 2026-11-20

### Dodano
- **Natywny silnik audio C++ (Oboe + AAudio)**: niskoopóźnieniowe przechwytywanie głosu z wbudowanego mikrofonu (`48 kHz` mono, `Float32`) połączone bezblokującą kolejką pierścieniową SPSC i adaptacyjną kontrolą dryftu zegara.
- **Lokalny łańcuch DSP**: filtr górnoprzepustowy (HPF), łagodna redukcja szumów stacjonarnych z zachowaniem spółgłosek, korekcja wyraźności mowy, bramkowany AGC, łagodny rozruch (*Soft-Start Ramp*) oraz twardy limiter chroniący słuch pacjenta.
- **Inteligentne zarządzanie Bluetooth**: priorytet dla Bluetooth LE Audio i HFP (`setCommunicationDevice()` na Androidzie 12+), tryb awaryjny A2DP z wyraźnym ostrzeżeniem, blokada wbudowanego głośnika telefonu oraz automatyczne wznawianie trasy po chwilowym rozłączeniu słuchawek.
- **Kliniczny interfejs w języku polskim (Jetpack Compose / Material 3)**: ekran powitalny z oświadczeniem o bezpieczeństwie i listą kontrolną uprawnień, główny panel terapeuty (obsługa jedną ręką, presety *Standardowy* / *Wyraźna mowa* / *Cichy pokój*, miernik poziomu dBFS), ustawienia zaawansowane oraz motywy jasny i ciemny.
- **Praca w tle i prywatność**: `ForegroundService` typu `microphone` ze stałym powiadomieniem i przyciskiem natychmiastowego zatrzymania (`ZATRZYMAJ`); brak nagrywania, chmury i transkrypcji.
- **Diagnostyka i pomiar opóźnień**: raport wydajności strumieni Oboe/AAudio, liczniki XRun, użycie CPU i baterii, wbudowany 12-punktowy plan testów klinicznych oraz kierowany test impulsu akustycznego (*Loopback Ping*) mierzący rzeczywiste opóźnienie *end-to-end*.
- **Automatyzacja wydań (GitHub Actions)**: potoki CI/CD kompilujące pakiety **Debug APK** oraz **Release APK** wraz z sumami kontrolnymi SHA-256 jako zasoby wydań GitHub Releases.
