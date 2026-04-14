# MegaMini Android (Kotlin)

Порт оригинального C# Console проекта в Android-приложение на Kotlin.

## Что реализовано

- `MegaMiniClient.getNodesFromLink(link)` — получает список файлов из folder share ссылки MEGA.
- `MegaMiniClient.download(megaFile)` — возвращает `InputStream` с расшифрованным содержимым файла.
- Простой `MainActivity` с кнопкой, которая:
  1. Загружает список файлов по тестовой ссылке.
  2. Скачивает первый файл.
  3. Сохраняет его в `cacheDir`.

## Структура

- `app/src/main/java/com/example/megamini/mega/` — порт логики API/криптографии.
- `app/src/main/java/com/example/megamini/MainActivity.kt` — UI для демонстрации.

## Сборка

Откройте проект в Android Studio (Giraffe+ / Hedgehog+) и выполните Sync Gradle.
