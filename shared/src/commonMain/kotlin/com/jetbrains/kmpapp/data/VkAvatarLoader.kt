package com.jetbrains.kmpapp.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * Достаёт аватар сообщества ВКонтакте из HTML страницы (meta og:image).
 * Лучший-effort: капча, блокировка или отсутствие сети вернут null,
 * и UI в этом случае покажет символ-заглушку вместо картинки.
 * Результат (включая неудачу) кешируется в памяти на время жизни процесса.
 */
class VkAvatarLoader(private val client: HttpClient) {

    private val ogImage = Regex(
        """<meta(?=[^>]*?["']og:image["'])[^>]*?\bcontent\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val cache = mutableMapOf<String, String?>()

    suspend fun resolveAvatar(pageUrl: String): String? {
        val key = pageUrl.trim()
        if (cache.containsKey(key)) return cache[key]

        val avatar = try {
            val html = client.get(key) {
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                header(HttpHeaders.Accept, "text/html")
            }.bodyAsText()
            ogImage.find(html)?.groupValues?.get(1)
        } catch (t: Throwable) {
            null
        }

        cache[key] = avatar
        return avatar
    }
}