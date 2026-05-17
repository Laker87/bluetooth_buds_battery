# BT Fast Pair Battery

Android-приложение для мониторинга заряда Bluetooth-наушников (включая TWS с кейсом) в фоне, с кастомным уведомлением в стиле Fast Pair и экраном текущего статуса.

## Возможности

- Фоновый мониторинг подключенных Bluetooth-наушников.
- Определение заряда:
- основной батареи (`Battery`).
- раздельно `L / R / Case` для TWS, если устройство отдает эти данные.
- Кастомное уведомление:
- свернутое и развернутое представление.
- круги заряда с процентом внутри.
- Автоматическое скрытие уведомления при отключении наушников.
- Экран статуса в приложении:
- текущий статус подключения.
- последний известный заряд после отключения.
- время с момента отключения.
- Настройки:
- `Monitoring enabled`
- `Show Fast Pair card when app is open`
- тема (`Light` / `Dark`)
- язык (`English` / `Русский`)
- акцентный цвет.

## Технические требования

- Android SDK:
- `minSdk = 31` (Android 12+)
- `targetSdk = 36`
- JDK `17`
- Gradle Wrapper (в репозитории уже есть `gradlew` / `gradlew.bat`).

## Разрешения

Приложение использует:

- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `ACCESS_FINE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+)
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `RECEIVE_BOOT_COMPLETED`

Без Bluetooth/Location/Notification разрешений фоновый мониторинг и уведомления работать не будут.

## Сборка

Windows:

```powershell
.\gradlew.bat assembleDebug
```

APK после сборки:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Запуск и проверка

1. Установите debug APK на устройство.
2. Откройте приложение и выдайте все разрешения.
3. Включите `Monitoring enabled` в `Settings`.
4. Подключите наушники и проверьте уведомление с кругами заряда.

## Архитектура (кратко)

- `MainActivity`:
- UI на Jetpack Compose.
- Экран статуса и экран настроек.
- `MainViewModel`:
- состояние экрана и настройки.
- логика «последний известный заряд» и таймер после отключения.
- `BluetoothBatteryService`:
- фоновый сервис мониторинга.
- построение и обновление кастомных уведомлений (`RemoteViews`).
- `BluetoothBatteryRepositoryImpl`:
- сбор Bluetooth-событий и чтение уровней заряда из доступных источников.
- `BootCompletedReceiver`:
- перезапуск мониторинга после перезагрузки устройства (если включен мониторинг).

## Ограничения

- Поведение отображения в шторке/статус-баре частично зависит от оболочки (One UI, MIUI и т.д.).
- Некоторые устройства отдают только «грубый» уровень (например, шаг 10%), если это ограничение источника данных.
- Для отдельных моделей наушников раздельные `L/R/Case` могут быть недоступны.

## Troubleshooting

- Уведомление не появляется:
- проверьте разрешения (`Bluetooth`, `Location`, `Notifications`).
- убедитесь, что `Monitoring enabled` включен.
- Залип старый вид уведомления после обновления:
- смахните текущее уведомление.
- переподключите наушники, чтобы система пересоздала `RemoteViews`.
- Нет данных по батарее:
- проверьте, поддерживает ли устройство передачу battery data по профилю/metadata/BLE.

## Структура проекта

- `app/src/main/java/com/example/btbattery/MainActivity.kt`
- `app/src/main/java/com/example/btbattery/presentation/MainViewModel.kt`
- `app/src/main/java/com/example/btbattery/service/BluetoothBatteryService.kt`
- `app/src/main/java/com/example/btbattery/data/BluetoothBatteryRepositoryImpl.kt`
- `app/src/main/res/layout/layout_fast_pair_notification.xml`

## Лицензия

Проект распространяется под лицензией **GNU General Public License v3.0**.