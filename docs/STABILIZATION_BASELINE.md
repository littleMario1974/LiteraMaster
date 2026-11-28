# Stabilization baseline — LiteraMaster 1.4

## Cel

Ustalenie powtarzalnego punktu odniesienia przed zmianami wydajnościowymi. W tym etapie nie zmieniamy algorytmów gry, OCR ani interfejsu.

## Stan wejściowy

- Branch bazowy: `main`
- Commit bazowy: `055e05d7edc6d05b35afd82a5ee5b1241ebd1690`
- Android Gradle Plugin: `8.9.3`
- Kotlin: `2.1.0`
- Gradle Wrapper: `8.11.1`
- Compile SDK / Target SDK: `36`
- Min SDK: `24`
- Java / Kotlin JVM target: `17`
- Moduł aplikacji: `:app`
- Testy jednostkowe: 13 plików testowych w `app/src/test`

## Środowisko

Przed uruchomieniem testów zapisać:
- system operacyjny i wersję,
- wersję JDK (`java -version`),
- wersję Android SDK / platform-tools,
- dostępność Android SDK Platform 36,
- model urządzenia i wersję Androida dla testów manualnych.

Nie porównywać czasów z różnych urządzeń lub różnych konfiguracji kompilacji jako równoważnych pomiarów.

## Testy bazowe

Uruchomić z katalogu głównego repozytorium:

```bash
./gradlew --version
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

W Windows użyć `gradlew.bat` zamiast `./gradlew`.

Zapisać pełny wynik każdego polecenia, w tym pierwszą przyczynę błędu, jeżeli zadanie się nie powiedzie. Nie poprawiać przy okazji błędów niezwiązanych z diagnozowanym etapem — najpierw sklasyfikować je jako środowiskowe, zależności, kompilacja albo test.

## Zestaw scenariuszy funkcjonalnych

### Generator ruchów i punktacja
- pusta plansza: pierwszy ruch przechodzi przez pole centralne;
- ruchy poziome i pionowe;
- ruch łączący się z istniejącym słowem;
- kilka słów poprzecznych utworzonych jednym ruchem;
- premia pola jest naliczana wyłącznie dla nowo położonej płytki;
- zajęte wcześniej pole premiowe nie daje premii ponownie;
- blank ma zero punktów, także gdy reprezentuje literę o wysokiej wartości;
- bonus za wykorzystanie siedmiu płytek;
- brak ruchu nielegalnego i brak pominięcia ruchu legalnego w przygotowanych pozycjach kontrolnych.

### Stojak i wymiana
- powtarzające się litery;
- blank na stojaku;
- stojak pusty i stojak niepełny;
- wymiana wszystkich płytek oraz wymiana podzbioru;
- powtarzalność wyników przy ustalonym ziarnie generatora losowego, jeśli API udostępnia seed.

### Słownik DAWG
- słowa obecne i nieobecne;
- prefiks, sufiks i fragment;
- powtarzające się litery;
- blanki i wiele możliwych podstawień;
- graniczne długości słów.

### Analiza zdjęcia
- plansza sfotografowana na wprost;
- plansza pod niewielkim kątem;
- nierówne oświetlenie i cienie;
- pola z pustymi płytkami i blankami;
- możliwość ręcznej korekty błędnego OCR.

## Pomiar wydajności

Na tym etapie nie wpisywać fikcyjnych wartości. Dla każdego scenariusza zanotować:
- urządzenie i wersję Androida,
- rozmiar zdjęcia wejściowego,
- czas wykrycia planszy,
- czas ekstrakcji 225 pól,
- czas OCR,
- całkowity czas analizy zdjęcia,
- czas generowania ruchów dla ustalonej planszy i stojaka,
- czas doradcy wymiany dla ustalonego stojaka i liczby symulacji.

Wykonać co najmniej 5 powtórzeń po rozgrzewce; raportować medianę i zakres. Zachować te same dane wejściowe dla pomiarów przed i po optymalizacji.

## Rejestr wyników

| Zadanie | Wynik | Czas / uwagi |
|---|---|---|
| `./gradlew --version` | do uzupełnienia | |
| `./gradlew clean` | do uzupełnienia | |
| `./gradlew testDebugUnitTest` | do uzupełnienia | |
| `./gradlew assembleDebug` | do uzupełnienia | |
| `./gradlew assembleRelease` | do uzupełnienia | |

## Kryterium zakończenia etapu

Etap bazowy jest zakończony, gdy:
1. wszystkie powyższe zadania zostały uruchomione w zapisanym środowisku;
2. wyniki testów są sklasyfikowane, a błędy mają opis;
3. przygotowano stałe dane wejściowe do pomiarów;
4. zapisano czasy bazowe albo jasno wskazano, dlaczego danego pomiaru nie udało się wykonać.

Dopiero po tym etapie rozpoczynamy optymalizację. Każda optymalizacja musi zachować testy regresyjne i zostać porównana z tym samym zestawem danych.
