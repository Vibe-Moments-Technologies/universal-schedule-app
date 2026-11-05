# Красава!

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-3DDC84.svg?logo=android&logoColor=white)](https://github.com/Vibe-Moments-Technologies/krasava-app/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.0-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)

Кроссплатформенное мобильное приложение для студентов и преподавателей РТУ МИРЭА: расписание занятий, свободные аудитории, схемы корпусов, задачи и конспекты.

> [!NOTE]
> Приложение неофициальное: сделано студентами для студентов РТУ МИРЭА и не является продуктом университета. Расписание и схемы корпусов загружаются из открытых источников.

**Скачать:** [APK (Android, стабильная версия)](https://github.com/Vibe-Moments-Technologies/krasava-app/releases/latest/download/Krasava.apk) · [IPA (iOS, без подписи)](https://github.com/Vibe-Moments-Technologies/krasava-app/releases/latest/download/Krasava.ipa) · [Все релизы](https://github.com/Vibe-Moments-Technologies/krasava-app/releases)

**Сообщество:** [Telegram](https://t.me/krasava_app) · [GitHub](https://github.com/Vibe-Moments-Technologies/krasava-app)

---

## Возможности

**Расписание**
- Поиск и просмотр расписания групп, преподавателей и аудиторий.
- Нумерация недель семестра, чётные и нечётные недели.
- Текущая пара и прогресс до её конца.
- Несколько сохранённых расписаний с быстрым переключением.
- Подсветка изменений при обновлении расписания.
- Локальный кэш: расписание доступно без интернета.
- Напоминания о занятиях: локальные уведомления с настраиваемым временем.

**Сравнение расписаний**
- Несколько групп рядом, подсветка различий, поиск по сохранённым расписаниям.

**Свободные аудитории**
- Аудитории, свободные на выбранной паре или в заданном интервале.
- Фильтрация по кампусам. ([API](FREE_ROOMS_API.md))

**Карты корпусов**
- Векторные схемы этажей (SVG, WebView, масштабирование и прокрутка): В-78, В-86, С-20, МП-1.
- Схемы построены по официальным источникам и могут содержать неточности. Не используйте их как единственный ориентир при эвакуации.

**Задачи**
- Задачи по предметам: категории (лабораторные, практики, домашние задания, курсовые и другие), приоритеты, статусы, чеклисты подзадач.

**Конспекты**
- Страницы заметок с цветными полями, поиском, переименованием и подтверждением удаления.
- Хранятся локально на устройстве и никуда не отправляются.

**Интерфейс**
- Темы: светлая, тёмная, системная.
- Настраиваемая нижняя панель страниц: порядок и видимость разделов.
- Раздел «Сервисы»: доступ к страницам, не добавленным на панель.
- Иконка приложения подстраивается под тему системы; на iOS можно выбрать между новым и старым дизайном.
- Встроенная проверка обновлений.

---

## Установка

### Android
1. Скачайте [Krasava.apk](https://github.com/Vibe-Moments-Technologies/krasava-app/releases/latest/download/Krasava.apk) из последнего стабильного релиза.
2. Установите приложение, разрешив установку из неизвестных источников.
3. Дальше приложение само проверяет обновления и предлагает установить новую версию.

### iOS
IPA собирается без подписи, поэтому для установки его нужно переподписать любым инструментом для sideload.

Источник приложений (AltStore-совместимый формат):

```
https://raw.githubusercontent.com/Vibe-Moments-Technologies/krasava-app/gh-pages/apps.json
```

---

## Сборка из исходников

Требования: JDK 21, Android SDK (compileSdk 37, minSdk 24, targetSdk 37), Xcode 16+ для iOS. Gradle 9.6.1 подключается через wrapper (`gradlew`).

```bash
git clone https://github.com/Vibe-Moments-Technologies/krasava-app.git
cd krasava-app

# Android (debug APK)
./gradlew assembleDebug
# результат: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# iOS (unsigned, на macOS)
cd iosApp
xcodebuild -scheme iosApp -configuration Release -sdk iphoneos \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
```

Тесты общего кода: `./gradlew :shared:allTests`.

Стек: Kotlin Multiplatform, Compose Multiplatform, Ktor Client, kotlinx.serialization, Multiplatform Settings, Koin, Яндекс AppMetrica.

---

## Источники данных

- **Расписание:** публичный API расписания РТУ МИРЭА.
- **Свободные аудитории:** собственная база, которую CI собирает из расписания ([FREE_ROOMS_API.md](FREE_ROOMS_API.md)).
- **Карты корпусов:** официальные схемы [pulse.mirea.ru](https://pulse.mirea.ru/services/maps) и векторные схемы проекта [university-app](https://github.com/0niel/university-app) ([0niel](https://github.com/0niel)).
- Приложение не отправляет данные пользователей: сетевые запросы только на чтение (API расписания и GitHub — проверка обновлений, база свободных аудиторий, список контрибьюторов).
- Анонимная статистика (AppMetrica) передаёт только события использования: разделы, ошибки загрузки, платформу и версию приложения. Без имён, групп, текстов конспектов и других персональных данных. Отключается в настройках.

## Лицензия

Код проекта распространяется по [GNU GPL v3](LICENSE).
Права на использованные данные и материалы остаются за их правообладателями: расписание и сведения о занятиях — РТУ МИРЭА; векторные схемы корпусов — [pulse.mirea.ru](https://pulse.mirea.ru/services/maps) и проект [university-app](https://github.com/0niel/university-app) ([0niel](https://github.com/0niel)).
