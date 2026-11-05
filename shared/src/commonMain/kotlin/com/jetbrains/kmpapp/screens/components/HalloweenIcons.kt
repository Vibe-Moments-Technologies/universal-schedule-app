package com.jetbrains.kmpapp.screens.components

/**
 * Тематические иконки (эмодзи) для хэллоуинской темы: показываются в доке и
 * на карточках сервисов, пока активна тема (сезон 28 октября — 3 ноября).
 * Обычные Material-иконки остаются нетронутыми вне сезона.
 */
val AppTab.halloweenEmoji: String
    get() = when (this) {
        AppTab.SCHEDULE -> "\uD83C\uDF83"   // 🎃 тыква
        AppTab.FREE_ROOMS -> "\uD83D\uDEAA" // 🚪 дверь аудитории
        AppTab.TASKS -> "\u26B0\uFE0F"      // ⚰️ гроб (дедлайны)
        AppTab.MAP -> "\uD83D\uDD78\uFE0F"  // 🕸️ паутина
        AppTab.NOTES -> "\uD83D\uDCD6"      // 📖 гримуар конспектов
        AppTab.COMPARE -> "\uD83D\uDD2E"    // 🔮 хрустальный шар
        AppTab.GAMES -> "\uD83D\uDC7B"      // 👻 призрак
        AppTab.SERVICES -> "\uD83E\uDDDF"   // 🧟 зомби
        AppTab.OTHER -> "\uD83D\uDC80"      // 💀 череп
    }
