package com.kevin.cryptotrader.live

import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Represents a generic REST request that can be executed against an exchange API. */
data class RestRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val contentType: String? = null,
    val timeout: Duration = Duration.ofSeconds(10)
)

interface RestExecutor {
    suspend fun execute(request: RestRequest): JsonObject
}

class RestException(val statusCode: Int, val payload: String) : Exception("HTTP $statusCode: $payload")

class HttpClientRestExecutor(
    private val baseUrl: String,
    private val client: HttpClient = HttpClient.newBuilder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RestExecutor {
    override suspend fun execute(request: RestRequest): JsonObject {
        val start = System.nanoTime()
        TelemetryCenter.logEvent(
            module = TelemetryModule.LIVE_BROKER,
            level = LogLevel.DEBUG,
            message = "REST request",
            fields = mapOf(
                "method" to request.method,
                "path" to request.path
            )
        )
        return runCatching {
            val uri = buildUri(request)
            val httpRequest = buildRequest(uri, request)
            val response = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
            if (response.statusCode() >= 400) {
                throw RestException(response.statusCode(), response.body())
            }
            val element = json.parseToJsonElement(response.body())
            element.jsonObject
        }.onSuccess {
            val latencyMs = (System.nanoTime() - start) / 1_000_000.0
            TelemetryCenter.logEvent(
                module = TelemetryModule.LIVE_BROKER,
                level = LogLevel.INFO,
                message = "REST response",
                fields = mapOf(
                    "path" to request.path,
                    "latencyMs" to "%.2f".format(latencyMs)
                )
            )
        }.onFailure { throwable ->
            val latencyMs = (System.nanoTime() - start) / 1_000_000.0
            TelemetryCenter.logEvent(
                module = TelemetryModule.LIVE_BROKER,
                level = LogLevel.ERROR,
                message = "REST failure",
                fields = mapOf(
                    "path" to request.path,
                    "latencyMs" to "%.2f".format(latencyMs),
                    "error" to (throwable.message ?: throwable::class.java.simpleName)
                )
            )
        }.getOrThrow()
    }

    private fun buildUri(request: RestRequest): URI {
        val base = if (request.path.startsWith("http")) {
            request.path
        } else {
            baseUrl.trimEnd('/') + request.path
        }
        if (request.query.isEmpty()) {
            return URI.create(base)
        }
        val queryString = request.query.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        return URI.create("$base?$queryString")
    }

    private fun buildRequest(uri: URI, request: RestRequest): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(request.timeout)
        request.headers.forEach { (key, value) -> builder.header(key, value) }
        request.contentType?.let { builder.header("Content-Type", it) }
        val method = request.method.uppercase()
        val bodyPublisher = request.body?.let { HttpRequest.BodyPublishers.ofString(it) }
            ?: HttpRequest.BodyPublishers.noBody()
        return builder.method(method, bodyPublisher).build()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
