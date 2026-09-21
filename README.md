# Universal Schedule App

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-3DDC84.svg?logo=android&logoColor=white)](https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.0-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-GPL_v3-blue.svg)](LICENSE)

Кроссплатформенное мобильное приложение-расписание для студентов и преподавателей РТУ МИРЭА. Основано на [krasava-app](https://github.com/Vibe-Moments-Technologies/krasava-app), из которого удалены все сервисы — остались только расписание и задачи.

> [!NOTE]
> Приложение неофициальное: сделано студентами для студентов РТУ МИРЭА и не является продуктом университета. Расписание загружается из открытых источников.

---

## Возможности

**Расписание**
- Поиск и просмотр расписания групп, преподавателей и аудиторий.
- Нумерация недель семестра, чётные и нечётные недели.
- Текущая пара и прогресс до её конца.
- Несколько сохранённых расписаний с быстрым переключением.
- Заметки к парам и предметам.
- Подсветка изменений при обновлении расписания.
- Локальный кэш: расписание доступно без интернета.
- Напоминания о занятиях: локальные уведомления с настраиваемым временем.

**Задачи**
- Задачи по предметам: категории (лабораторные, практики, домашние задания, курсовые и другие), приоритеты, статусы, чеклисты подзадач.

**Интерфейс**
- Темы: светлая, тёмная, системная (+ оверлеи).
- Настраиваемая нижняя панель страниц: порядок и видимость разделов.
- Иконка приложения подстраивается под тему системы; на iOS можно выбрать между новым и старым дизайном.
- Встроенная проверка обновлений.

---

## Структура проекта

Kotlin Multiplatform + Compose Multiplatform:

- `shared/` — общий код: данные, репозитории, экраны (расписание, задачи, «Другое» с настройками).
- `androidApp/` — Android-обёртка.
- `iosApp/` — iOS-обёртка.

## Сборка

- Android: `./gradlew :androidApp:assembleDebug` (нужен Android SDK).
- iOS: открыть `iosApp/iosApp.xcodeproj` в Xcode на macOS.

Лицензия: [GPL-3.0](LICENSE). Права на данные расписания принадлежат РТУ МИРЭА.
