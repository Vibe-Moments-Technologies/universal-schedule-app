package com.jetbrains.kmpapp.screens.other

import org.jetbrains.compose.resources.DrawableResource
import kmp_app_template.shared.generated.resources.Res
import kmp_app_template.shared.generated.resources.team_kirasunshine
import kmp_app_template.shared.generated.resources.team_l1ratch
import kmp_app_template.shared.generated.resources.team_prosto_max

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
    val links: List<TeamLink> = emptyList()
)

/** Отделы для группировки; карточки выводятся по этому порядку. */
val teamDepartments = listOf("Разработка", "Карты")

val projectTeam = listOf(
    TeamMember(
        name = "Линк (l1ratch)",
        role = "Разработчик",
        department = "Разработка",
        email = "connect@l1ratch.ru",
        avatar = Res.drawable.team_l1ratch,
        links = listOf(TeamLink(TeamLinkType.GITHUB, "https://github.com/l1ratch"))
    ),
    TeamMember(
        name = "prosto-max",
        role = "Разработчик",
        department = "Разработка",
        avatar = Res.drawable.team_prosto_max,
        links = listOf(TeamLink(TeamLinkType.GITHUB, "https://github.com/prosto-max"))
    ),
    TeamMember(
        name = "KiraSunshine",
        role = "Картограф",
        department = "Карты",
        avatar = Res.drawable.team_kirasunshine,
        links = listOf(TeamLink(TeamLinkType.GITHUB, "https://github.com/KiraSunshine"))
    )
)
