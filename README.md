# Bluetooth Buds Battery

Android-приложение для мониторинга заряда Bluetooth-наушников (включая TWS) с кастомным уведомлением в стиле Fast Pair.

## Актуальные данные проекта

- Название приложения: `Bluetooth Buds Battery`
- Package / Application ID: `com.laker.btbudsbattery`
- Минимальная версия Android: `minSdk = 31` (Android 12+)
- Целевая версия Android: `targetSdk = 36`
- JDK: `17`

## Возможности

- Фоновый мониторинг подключенных Bluetooth-наушников.
- Источники заряда:
  - общий заряд (`Battery`),
  - раздельный `L / R / Case` (если устройство передает эти данные).
- Кастомное уведомление:
  - компактный и развернутый вид,
  - круговые индикаторы заряда.
- Главный экран:
  - текущий статус подключенного устройства,
  - история ранее подключенных наушников,
  - для истории: если доступны `L/R/Case`, показываются раздельно; иначе одиночный заряд.
- Экран первоначальной настройки:
  - запрос необходимых разрешений при первом запуске.
- Мониторинг включен по умолчанию для новых установок.

## Разрешения

Приложение использует:

- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `RECEIVE_BOOT_COMPLETED`

Без Bluetooth/Location/Notification разрешений фоновые обновления и уведомления не будут работать.

## Источники батареи и архитектура парсинга

Приложение собирает данные из нескольких источников (профили Bluetooth, BLE scan, metadata, vendor events).

Для BLE TWS-парсинга используется модульная схема:

- общий контракт парсера:  
  `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/TwsBatteryAdvertisementParser.kt`
- реестр парсеров:  
  `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/TwsBatteryParserRegistry.kt`
- отдельный модуль для Realme T310:  
  `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/realme/RealmeT310FastPairSpec.kt`  
  `app/src/main/java/com/laker/btbudsbattery/data/parser/tws/realme/RealmeT310FastPairParser.kt`

Это позволяет добавлять поддержку новых моделей как отдельные парсеры без изменения основной логики репозитория.

## Сборка

Windows:

```powershell
.\gradlew.bat assembleDebug
```

APK после сборки:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Линт и проверка

```powershell
.\gradlew.bat lintDebug
```

HTML-отчет:

```text
app/build/reports/lint-results-debug.html
```

## Структура проекта (основное)

- `app/src/main/java/com/laker/btbudsbattery/MainActivity.kt`
- `app/src/main/java/com/laker/btbudsbattery/presentation/MainViewModel.kt`
- `app/src/main/java/com/laker/btbudsbattery/service/BluetoothBatteryService.kt`
- `app/src/main/java/com/laker/btbudsbattery/data/BluetoothBatteryRepositoryImpl.kt`
- `app/src/main/res/layout/layout_fast_pair_notification.xml`

## Ограничения

- Поведение уведомлений может отличаться в разных оболочках Android.
- Не все модели наушников отдают `L/R/Case`; в этом случае доступен только общий уровень.
- Для части устройств значения могут приходить с задержкой или в «грубом» шаге (например, по 10%).

## Лицензия

Проект распространяется по лицензии **GNU General Public License v3.0**.
