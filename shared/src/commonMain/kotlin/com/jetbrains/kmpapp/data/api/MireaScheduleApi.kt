package com.jetbrains.kmpapp.data.api

import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

import com.jetbrains.kmpapp.data.DebugConfig
import io.ktor.utils.io.errors.IOException

@Serializable
private data class SearchResponse(
    val data: List<ScheduleTarget> = emptyList()
)

class MireaScheduleApi(private val client: HttpClient) {

    private val baseUrl = "https://schedule-of.mirea.ru"

    suspend fun search(query: String, limit: Int = 20): List<ScheduleTarget> {
        if (DebugConfig.isOfflineSimulated.value) {
            throw IOException("Simulated network offline")
        }
        val trimmed = query.trim()
        val response = client.get("$baseUrl/schedule/api/search") {
            header(HttpHeaders.UserAgent, "university-app-schedule-fetcher/0.1")
            header(HttpHeaders.Accept, "application/json")
            if (trimmed.isNotEmpty()) {
                parameter("match", trimmed)
            }
            parameter("limit", limit)
        }
        // Сервер может отдать HTML-заглушку (техобслуживание) со статусом 5xx —
        // без проверки статуса JSON-парсинг молча вернул бы пустой результат.
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value} от сервера расписания")
        }
        val parsed: SearchResponse = response.body()
        return parsed.data
    }

    suspend fun getIcal(targetType: ScheduleTargetType, id: Int): String {
        if (DebugConfig.isOfflineSimulated.value) {
            throw IOException("Simulated network offline")
        }
        val response = client.get("$baseUrl/schedule/api/ical/${targetType.pathName}/$id") {
            header(HttpHeaders.UserAgent, "university-app-schedule-fetcher/0.1")
            parameter("includeMeta", "true")
        }
        // Аналогично search: HTML-заглушка вместо iCal затирала бы
        // сохранённое расписание пустым результатом парсинга.
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value} от сервера расписания")
        }
        return response.bodyAsText()
    }
}

