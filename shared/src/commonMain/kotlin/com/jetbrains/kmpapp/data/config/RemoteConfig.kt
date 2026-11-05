package com.jetbrains.kmpapp.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Удалённая конфигурация (config.json на gh-pages).
 * Позволяет менять ссылки/контакты без обновления приложения.
 * Кэшируется локально, обновляется при старте.
 */
@Serializable
data class RemoteConfig(
    @SerialName("social_links")
    val socialLinks: SocialLinks = SocialLinks()
)

@Serializable
data class SocialLinks(
    val github: String = "https://github.com/Vibe-Moments-Technologies/krasava-app",
    val telegram: String = "https://t.me/krasava_app",
    val boosty: String = "https://boosty.to/vibe.moments.technologies"
)
