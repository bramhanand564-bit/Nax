package com.jarvis.os.brain

import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ApiTestResult
import com.jarvis.os.data.ProviderType
import org.json.JSONObject

object ApiTestBench {

    suspend fun runDiagnostics(config: ApiConfig): ApiTestResult {
        val probePrompt = """Reply ONLY with valid JSON and nothing else: {"ok":true,"agent":"NAX"}"""
        val res = ApiClient.chat(
            config = config,
            systemPrompt = "You are a strict JSON API diagnostic probe. Output raw JSON only.",
            history = emptyList(),
            userMessage = probePrompt
        )

        if (!res.success) {
            return ApiTestResult(
                success = false,
                latencyMs = res.latencyMs,
                visionReady = false,
                jsonAgentReady = false,
                message = "Failed: ${res.error}"
            )
        }

        val jsonReady = isValidAgentJson(res.text)
        val visionReady = supportsVision(config.provider, config.model)

        val statusMsg = buildString {
            append("Connected (${res.latencyMs}ms)")
            append(if (jsonReady) " • Agent JSON OK" else " • Text Only")
            append(if (visionReady) " • Vision Ready" else " • No Vision")
        }

        return ApiTestResult(
            success = true,
            latencyMs = res.latencyMs,
            visionReady = visionReady,
            jsonAgentReady = jsonReady,
            message = statusMsg
        )
    }

    private fun isValidAgentJson(raw: String): Boolean {
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end <= start) return false
        return try {
            val obj = JSONObject(cleaned.substring(start, end + 1))
            obj.optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun supportsVision(provider: ProviderType, model: String): Boolean {
        val m = model.lowercase()
        return when (provider) {
            ProviderType.GEMINI -> true
            ProviderType.CLAUDE -> true
            ProviderType.GROQ -> m.contains("vision") || m.contains("scout") || m.contains("maverick")
            ProviderType.LOCAL, ProviderType.CUSTOM ->
                m.contains("vision") || m.contains("vl") || m.contains("4o") ||
                    m.contains("gemini") || m.contains("claude") || m.contains("pixtral") || m.contains("llava")
        }
    }
}
