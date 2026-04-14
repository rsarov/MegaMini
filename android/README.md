# Проект MegaMini Android

Это портированная версия C# Console приложения MegaMini на Kotlin для Android.

## Описание

Приложение позволяет скачивать файлы из общих папок Mega.nz по прямой ссылке.

## Структура проекта

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/megamini/android/
│   │   │   ├── MainActivity.kt          # UI и точка входа
│   │   │   ├── MegaMini.kt              # Основной API клиент
│   │   │   ├── Utils.kt                 # Утилиты (криптография, base64, HTTP)
│   │   │   ├── Const.kt                 # Константы
│   │   │   ├── BufferedStream.kt        # Буферизированный поток
│   │   │   └── MegaAesCtrStream.kt      # AES-CTR расшифровка
│   │   ├── res/                         # Ресурсы Android
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Основные изменения при портировании

1. **HTTP клиент**: System.Net.Http → OkHttp3
2. **JSON парсинг**: Newtonsoft.Json → Gson / ручная парсинг
3. **Криптография**: System.Security.Cryptography → javax.crypto
4. **Потоки**: .NET Stream → Java InputStream
5. **Base64**: Convert.FromBase64String → android.util.Base64
6. **Асинхронность**: async/await → Kotlin Coroutines

## Как использовать

1. Откройте проект в Android Studio
2. Соберите и запустите на устройстве или эмуляторе
3. Вставьте ссылку на папку Mega.nz (формат: https://mega.nz/folder/...)
4. Нажмите "Скачать первый файл"
5. Файл сохранится в папке приложения

## Требования

- Android 7.0+ (API 24)
- Разрешение на доступ к интернету
- Разрешение на запись файлов (для Android ≤ 9)

## Технические детали

### Криптография
- AES-CBC для расшифровки ключей
- AES-CTR для расшифровки потока данных
- Проверка MetaMac для целостности

### API Mega.nz
- Используется публичный API: https://g.api.mega.co.nz/cs
- Поддержка TLS 1.2

## Примечания

- Проект использует OkHttp для HTTP запросов вместо HttpClient
- JSON парсится вручную для уменьшения зависимостей
- Все сетевые операции выполняются в фоновом потоке через coroutines
