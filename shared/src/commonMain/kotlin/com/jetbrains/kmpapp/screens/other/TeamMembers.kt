package com.jetbrains.kmpapp.screens.other

import org.jetbrains.compose.resources.DrawableResource
import kmp_app_template.shared.generated.resources.Res
import kmp_app_template.shared.generated.resources.team_l1ratch

/**
 * Команда проекта — декларативный список в коде (не «конструктор»):
 * новый участник = строка в [projectTeam]. Аватарки захардкожены
 * ресурсами, чтобы не тянуть их с сети при каждом открытии экрана.
 */

/** Типы личных ссылок участника: определяют иконку кнопки в карточке. */
enum class TeamLinkType {
    GITHUB, TELEGRAM, OTHER
}

data class TeamLink(
    val type: TeamLinkType,
    val url: String
)

data class TeamMember(
    val name: String,
    val role: String,
    val department: String,
    val email: String? = null,
    val avatar: DrawableResource? = null,
    val links: List<TeamLink> = emptyList(),
    /** Текстовое описание вместо ссылок/почты — для собирательных участников. */
    val description: String? = null
)

/** Отделы для группировки; карточки выводятся по этому порядку. */
val teamDepartments = listOf("Разработка")

val projectTeam = listOf(
    TeamMember(
        name = "Линк (l1ratch)",
        role = "Автор и разработчик",
        department = "Разработка",
        email = "connect@l1ratch.ru",
        avatar = Res.drawable.team_l1ratch,
        links = listOf(TeamLink(TeamLinkType.GITHUB, "https://github.com/l1ratch"))
    ),
    TeamMember(
        name = "Контрибьютер",
        role = "Сообщество проекта",
        department = "Разработка",
        description = "Все, кто предлагал идеи и функции, находил и сообщал о проблемах, " +
            "тестировал сборки и помогал делать приложение лучше. Спасибо каждому из вас! 🙌"
    )
)
