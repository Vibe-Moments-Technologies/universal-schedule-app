# Расписание

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-3DDC84.svg?logo=android&logoColor=white)](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.0-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)

Универсальное приложение-расписание для студентов любых вузов и колледжей. Работает полностью офлайн: семестр собирается прямо на устройстве в конфигураторе — вуз, группа, звонки и недельный шаблон занятий — и превращается в полноценный календарь пар.

> [!NOTE]
> Приложение не привязано ни к какому университету и не требует сервера. Расписание живёт на устройстве; перенос между устройствами — экспорт/импорт JSON.

**Скачать:** [APK (Android, стабильная версия)](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest/download/Raspisanie.apk) · [IPA (iOS, без подписи)](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest/download/Raspisanie.ipa) · [Все релизы](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases)

**Сообщество:** [Telegram](https://t.me/l1ratch) · [GitHub](https://github.com/Vibe-Moments-Technologies/universal-schedule-app)

---

## Возможности

**Расписание**
- Конфигуратор семестра: вуз, группа, курс, семестр, дата начала, число недель, звонки.
- Недельный шаблон занятий: предмет, тип (лекция/практика/лабораторная/…), преподаватель, аудитория, подгруппа.
- Чётность недель (каждую неделю / нечётные / чётные) и диапазоны недель (курсовые, модули).
- Нумерация недель семестра, автоматическая прокрутка к текущей паре, прогресс до конца пары.
- Локальные напоминания о занятиях с настраиваемым временем.
- Экспорт/импорт семестра в JSON (перенос между устройствами).

**Задачи**
- Задачи по предметам: категории (лабораторные, практики, домашние задания, курсовые и другие), приоритеты, статусы, чеклисты подзадач.

**Игры**
- «Сапёр» с настраиваемым размером поля и «Тетрис» с вариантами управления.

**Интерфейс**
- Темы: светлая, тёмная, системная.
- Настраиваемая нижняя панель страниц: порядок и видимость разделов.
- Виджеты расписания на iOS (WidgetKit).
- Иконка приложения подстраивается под тему системы.
- Встроенная проверка обновлений.

---

## Формат данных

Локальный формат `universal-schedule v1`: `SemesterConfig` (заголовок + звонки + недельный шаблон) разворачивается в конкретные занятия по датам (`generateLessons()`). Один активный семестр на устройстве.

---

## Установка

### Android
1. Скачайте [Raspisanie.apk](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest/download/Raspisanie.apk) из последнего стабильного релиза.
2. Установите приложение, разрешив установку из неизвестных источников.
3. Дальше приложение само проверяет обновления и предлагает установить новую версию.

### iOS
IPA собирается без подписи, поэтому для установки его нужно переподписать любым инструментом для sideload.

---

## Сборка из исходников

Требования: JDK 21, Android SDK (compileSdk 37, minSdk 24, targetSdk 37), Xcode 26+ для iOS. Gradle 9.6.1 подключается через wrapper (`gradlew`).

```bash
git clone https://github.com/Vibe-Moments-Technologies/universal-schedule-app.git
cd universal-schedule-app

# Android (debug APK)
./gradlew assembleDebug
# результат: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# iOS (unsigned, на macOS)
cd iosApp
xcodebuild -scheme iosApp -configuration Release -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
```

Тесты общего кода: `./gradlew :shared:allTests`.

Стек: Kotlin Multiplatform, Compose Multiplatform, kotlinx.serialization, Multiplatform Settings, Koin, Яндекс AppMetrica.

---

## Приватность

- Все данные хранятся локально на устройстве и никуда не отправляются.
- Сетевые запросы — только чтение: проверка обновлений с GitHub.
- Анонимная статистика (AppMetrica) передаёт только события использования: разделы, платформу и версию приложения. Без имён, групп и других персональных данных. Отключается в настройках.

## Лицензия

Код проекта распространяется по [GNU GPL v3](LICENSE).
