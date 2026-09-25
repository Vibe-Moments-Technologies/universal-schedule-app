# GitHub Copilot Instructions for universal-schedule-app

## О проекте
**«Расписание» (universal-schedule)** — кроссплатформенное мобильное приложение расписания для студентов **любого** учебного заведения на базе **Kotlin Multiplatform (KMP)** и **Compose Multiplatform** для Android и iOS. Полностью автономное: сервера расписания нет — пользователь сам собирает семестр в конфигураторе (хоть из PDF-таблички), а приложение разворачивает недельный шаблон в календарь занятий.

## Архитектура и технологии
- **Язык**: Kotlin 2.x
- **UI Framework**: Compose Multiplatform (Material 3)
- **Dependency Injection**: Koin 4.x (`koinViewModel()`, `koinInject()`, `singleOf`, `factoryOf`)
- **Сетевой стек**: Ktor 3.x Client (OkHttp engine на Android, Darwin engine на iOS) — **только** проверка обновлений (gh-pages фид + GitHub API); расписание и задачи полностью локальные
- **Сериализация**: `kotlinx.serialization` (JSON с `ignoreUnknownKeys = true`)
- **Работа с датами**: `kotlinx.datetime` и `kotlin.time.Clock`
- **Асинхронность**: Kotlin Coroutines & StateFlow
- **Хранилище**: `PlatformStorage` (`SharedPreferences` на Android, `NSUserDefaults` на iOS), ключи с префиксом `uschedule_`
- **Аналитика**: AppMetrica; ключ задан в ДВУХ местах, которые обязаны быть синхронизированы: `AppAnalytics.API_KEY` (Kotlin) и `iosApp/iosApp/iOSApp.swift`

## Ключевая модель данных
- `SemesterConfig` (`data/model/SemesterConfig.kt`) — единственный источник истины: вуз, группа, курс, номер семестра, дата начала (понедельник), число недель, звонки (`LessonBells`) и недельный шаблон (`ScheduleEntry`: день, пара, предмет, тип, преподаватель, аудитория, подгруппа, чётность, диапазон недель).
- `generateLessons()` детерминированно разворачивает шаблон в `Lesson` по датам; `weekMarkers()` питает нумерацию недель (`SemesterWeeks`).
- Формат экспорта/импорта `universal-schedule v1` (`ScheduleExport`) описан в `docs/SCHEDULE_FORMAT.md` — там же инструкция для AI-ассистентов по конвертации чужих расписаний в JSON.
- Целевая аудитория расписания одна — локальный семестр (`SemesterConfig.TARGET_ID`); заметки к парам ключуются по нему.

## Структура кодовой базы
- `shared/src/commonMain/kotlin/com/jetbrains/kmpapp/` (пакет намеренно оставлен от шаблона-предка):
  - `data/`: `ScheduleRepository`, `TaskRepository`, хранилища (`ScheduleStorage`, `LessonNotesStorage`, `TaskStorage`), модели, `update/` (проверка обновлений), `notifications/`, `analytics/`.
  - `screens/`:
    - `schedule/`: экран расписания, карточки занятий (`LessonCard`), детали пары (`LessonDetailScreen`), недельная лента (`WeekCalendarStrip`).
    - `configurator/`: `ConfiguratorScreen` — форма семестра, звонки, недельный шаблон, экспорт/импорт JSON (файл и буфер обмена).
    - `tasks/`: трекер учебных задач (вкладка по умолчанию выключена).
    - `other/`: «Другое» — настройки, темы, о программе, команда, лицензии, отладка, социальные ссылки.
    - `components/`: плавающий док (`FloatingDock`), послойная навигация со свайпом назад (`LayeredNavHost`), `PlatformBackHandler`, пикер файлов расписания (expect/actual), оверлей месяца.
  - Часть подсистем намеренно **выключена и скрыта, но не удалена** (договорённость с владельцем): бета-канал обновлений, `PlatformUpdater`, VPN-детектор, `AppIconManager`. Не удалять и не «чинить» их без явного запроса; iOS-движки в Swift регистрируют эти Kotlin-объекты.
- `androidApp/`: точка входа Android (`ScheduleApp.kt`, `MainActivity.kt`), движок уведомлений (`AndroidNotificationsEngine`, `LessonAlarmReceiver`), подпись из секретов CI.
- `iosApp/`: Xcode-проект; Swift-движки (`AppIconEngine.swift`, `iOSApp.swift`) регистрируют платформенные части KMP. AppMetrica SDK запинена на exactVersion в `project.pbxproj` (6.7.x ломает Xcode 16.4).
- `tools/`: CalVer-версионирование (`versioning.py`), публикация релизов и фидов (`publish_release.py`, `update_version_feed.py`).
- `.github/workflows/`: `build-mobile.yml` (тег `v*` → подписанный релиз + фиды gh-pages), `preview-main.yml` (push в main → rolling prerelease `preview`), ревью/триаж issue.

## Протокол AI-агента

Перед заметной правкой агент должен:

1. Проверить доступные плагины и навыки среды; для разработки использовать **Ponytail** (YAGNI, минимальный diff, переиспользование).
2. Использовать **Repowise MCP** до массового чтения: `get_overview`, затем `get_context`/`get_answer`/`search_codebase`; для риска — `get_risk`/`get_change_risk`.
3. Сначала изучить и спланировать изменение, затем менять код; после — выполнить подходящую проверку и сообщить результат. Локальной сборки Android SDK может не быть — тогда проверка через CI (`preview-main.yml`).
4. Не добавлять секреты, временные файлы, артефакты Repowise/агентов и незапланированные изменения. Релизный keystore лежит ВНЕ репо (секреты org в CI).

Если инструмент или навык недоступен, агент должен явно отметить это и сверять Repowise с живым исходником при stale-индексе.

## Правила разработки и решения Issue
1. **Безопасность потоков и старта**:
   - Все сетевые запросы и парсинг файлов ДОЛЖНЫ выполняться строго на `Dispatchers.IO` или `Dispatchers.Default`.
   - В конструкторах и блоках `init` ViewModels ЗАПРЕЩЕНО выполнять блокирующие сетевые запросы.
   - Любые операции с хранилищем и парсингом должны быть защищены `try-catch (_: Throwable)` — повреждённый JSON не должен ронять приложение.
2. **UI и дизайн**:
   - Поддержка тёмного и светлого оформления Material 3 + оверлеи-пасхалки (сакура, матрица).
   - Корректные отступы под системные панели (WindowInsets: `navigationBarsPadding()`, `statusBarsPadding()`).
   - Весь UI работает офлайн; единственные сетевые сценарии — проверка обновлений и аналитика.
3. **Локализация**:
   - Весь интерфейс и текстовые сообщения пользователю составляются на русском языке.
4. **Формат расписания**:
   - Любые изменения `SemesterConfig`/`ScheduleEntry` — только обратно совместимо с `formatVersion: 1` (поля с дефолтами, `ignoreUnknownKeys`); при несовместимом изменении — новая версия формата и миграция импорта.
