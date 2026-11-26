package com.jarvis.os.brain

import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApiCallResponse(
    val success: Boolean,
    val text: String = "",
    val latencyMs: Long = 0L,
    val tokensUsed: Long = 0L,
    val providerName: String = "",
    val error: String = ""
)

object ApiClient {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        config: ApiConfig,
        systemPrompt: String,
        history: List<Pair<Boolean, String>>,
        userMessage: String
    ): ApiCallResponse = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            if (config.provider != ProviderType.LOCAL && config.apiKey.isBlank()) {
                return@withContext ApiCallResponse(
                    success = false,
                    providerName = config.provider.displayName,
                    error = "${config.provider.displayName} key missing. Brain Lab mein API key daalein."
                )
            }

            when (config.provider) {
                ProviderType.GEMINI -> callGemini(config, systemPrompt, history, userMessage, start)
                ProviderType.CLAUDE -> callClaude(config, systemPrompt, history, userMessage, start)
                ProviderType.GROQ,
                ProviderType.LOCAL,
                ProviderType.CUSTOM -> callOpenAiCompatible(config, systemPrompt, history, userMessage, start)
            }
        } catch (e: Exception) {
            ApiCallResponse(
                success = false,
                latencyMs = System.currentTimeMillis() - start,
                providerName = config.provider.displayName,
                error = e.message ?: "Network error"
            )
        }
    }

    private fun callGemini(
        config: ApiConfig,
        systemPrompt: String,
        history: List<Pair<Boolean, String>>,
        userMessage: String,
        start: Long
    ): ApiCallResponse {
        val base = config.baseUrl.trimEnd('/')
        val url = "$base/v1beta/models/${config.model.trim()}:generateContent?key=${config.apiKey.trim()}"

        val contents = JSONArray()
        history.takeLast(8).forEach { (isUser, msg) ->
            contents.put(JSONObject().apply {
                put("role", if (isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg)))
            })
        }
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        })

        val payload = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                put("maxOutputTokens", 1500)
            })
        }

        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            val latency = System.currentTimeMillis() - start
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return ApiCallResponse(
                    success = false,
                    latencyMs = latency,
                    providerName = config.provider.displayName,
                    error = "HTTP ${resp.code}: ${extractErrorMessage(raw)}"
                )
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
                .trim()
            val tokens = json.optJSONObject("usageMetadata")?.optLong("totalTokenCount", 0L)
                ?: ((userMessage.length + text.length) / 4L)
            return ApiCallResponse(
                success = text.isNotBlank(),
                text = text,
                latencyMs = latency,
                tokensUsed = tokens,
                providerName = config.provider.displayName,
                error = if (text.isBlank()) "Empty Gemini response" else ""
            )
        }
    }

    private fun callClaude(
        config: ApiConfig,
        systemPrompt: String,
        history: List<Pair<Boolean, String>>,
        userMessage: String,
        start: Long
    ): ApiCallResponse {
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/messages")) base else "$base/messages"

        val messages = JSONArray()
        history.takeLast(8).forEach { (isUser, msg) ->
            messages.put(JSONObject().apply {
                put("role", if (isUser) "user" else "assistant")
                put("content", msg)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val payload = JSONObject().apply {
            put("model", config.model.trim())
            put("max_tokens", 1500)
            put("system", systemPrompt)
            put("messages", messages)
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { resp ->
            val latency = System.currentTimeMillis() - start
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return ApiCallResponse(
                    success = false,
                    latencyMs = latency,
                    providerName = config.provider.displayName,
                    error = "HTTP ${resp.code}: ${extractErrorMessage(raw)}"
                )
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
                .trim()
            val usage = json.optJSONObject("usage")
            val tokens = (usage?.optLong("input_tokens", 0L) ?: 0L) +
                (usage?.optLong("output_tokens", 0L) ?: 0L)
            return ApiCallResponse(
                success = text.isNotBlank(),
                text = text,
                latencyMs = latency,
                tokensUsed = if (tokens > 0) tokens else ((userMessage.length + text.length) / 4L),
                providerName = config.provider.displayName,
                error = if (text.isBlank()) "Empty Claude response" else ""
            )
        }
    }

    private fun callOpenAiCompatible(
        config: ApiConfig,
        systemPrompt: String,
        history: List<Pair<Boolean, String>>,
        userMessage: String,
        start: Long
    ): ApiCallResponse {
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            history.takeLast(8).forEach { (isUser, msg) ->
                put(JSONObject().apply {
                    put("role", if (isUser) "user" else "assistant")
                    put("content", msg)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", config.model.trim())
            put("messages", messages)
            put("temperature", 0.6)
            put("max_tokens", 1500)
        }

        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))

        if (config.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        client.newCall(builder.build()).execute().use { resp ->
            val latency = System.currentTimeMillis() - start
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return ApiCallResponse(
                    success = false,
                    latencyMs = latency,
                    providerName = config.provider.displayName,
                    error = "HTTP ${resp.code}: ${extractErrorMessage(raw)}"
                )
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            val tokens = json.optJSONObject("usage")?.optLong("total_tokens", 0L)
                ?: ((userMessage.length + text.length) / 4L)
            return ApiCallResponse(
                success = text.isNotBlank(),
                text = text,
                latencyMs = latency,
                tokensUsed = tokens,
                providerName = config.provider.displayName,
                error = if (text.isBlank()) "Empty model response" else ""
            )
        }
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            val j = JSONObject(raw)
            j.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: j.optString("message").takeIf { it.isNotBlank() }
                ?: raw.take(140)
        } catch (_: Exception) {
            raw.take(140)
        }
    }
}
