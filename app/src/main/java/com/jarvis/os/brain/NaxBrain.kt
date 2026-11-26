package com.jarvis.os.brain

import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ArtifactPreview
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.MemoryRule
import com.jarvis.os.data.ProviderType
import com.jarvis.os.data.SandboxApp
import com.jarvis.os.data.SkillItem
import com.jarvis.os.data.VaultStorage

data class BrainReply(
    val cleanText: String,
    val providerUsed: String,
    val artifact: ArtifactPreview? = null,
    val suggestedAction: SuggestedDeviceAction? = null
)

data class SuggestedDeviceAction(
    val mode: ExecutionMode,
    val target: String,
    val command: String
)

class NaxBrain(private val vault: VaultStorage) {

    fun extractGitHubRepoUrl(message: String): String? {
        val regex = Regex("""https?://(www\.)?github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""")
        return regex.find(message)?.value?.trimEnd('/', '.')
    }

    fun isAskingCapabilities(message: String): Boolean {
        val m = message.lowercase()
        val triggers = listOf(
            "kya kya aata", "kya-kya aata", "kya aata hai", "tumhe kya aata",
            "tujhe kya aata", "what can you do", "your skills", "apni skills",
            "installed skills", "list skills", "capabilities"
        )
        return triggers.any { m.contains(it) }
    }

    fun isRoutineRequest(message: String): Boolean {
        val m = message.lowercase()
        val hasScheduleWord = listOf(
            "roz ", "roj ", "daily", "every day", "har roz", "har din",
            "schedule", "routine", "subah ", "shaam ", "raat ", "am ", "pm ", "baje"
        ).any { m.contains(it) }
        val hasActionWord = listOf(
            "banao", "chalao", "upload", "kholo", "check", "bhej", "run",
            "open", "post", "send", "search", "flow", "karna", "kardo", "kar dena"
        ).any { m.contains(it) }
        return hasScheduleWord && hasActionWord
    }

    fun detectModeHint(message: String, currentMode: ExecutionMode): ExecutionMode {
        val m = message.lowercase()
        return when {
            m.contains("sandbox mein") || m.contains("sandbox me") || m.contains("in sandbox") -> ExecutionMode.SANDBOX
            m.contains("live mein") || m.contains("live me") || m.contains("real phone") || m.contains("asli phone") -> ExecutionMode.LIVE
            else -> currentMode
        }
    }

    suspend fun generateReply(
        userMessage: String,
        mode: ExecutionMode,
        selectedProviderCode: String,
        configs: List<ApiConfig>,
        skills: List<SkillItem>,
        sandboxApps: List<SandboxApp>,
        memoryRules: List<MemoryRule>,
        history: List<Pair<Boolean, String>>
    ): BrainReply {
        val orderedProviders = buildProviderChain(selectedProviderCode, configs, userMessage)
        val systemPrompt = buildSystemPrompt(mode, skills, sandboxApps, memoryRules)

        for (cfg in orderedProviders) {
            val res = ApiClient.chat(cfg, systemPrompt, history, userMessage)
            if (res.success) {
                vault.recordCall(res.tokensUsed, res.latencyMs, cfg.provider.displayName)
                return parseStructuredReply(res.text, cfg.provider.displayName, mode)
            }
        }

        // Offline / No API Key graceful Jarvis fallback
        return offlineFallbackReply(userMessage, mode, sandboxApps)
    }

    private fun buildProviderChain(
        selectedCode: String,
        configs: List<ApiConfig>,
        userMessage: String
    ): List<ApiConfig> {
        val ready = configs.filter {
            it.enabled && (it.provider == ProviderType.LOCAL || it.apiKey.isNotBlank())
        }
        if (ready.isEmpty()) return emptyList()

        if (selectedCode != "AUTO") {
            val preferred = ready.firstOrNull { it.provider.name == selectedCode }
            val others = ready.filter { it.provider.name != selectedCode }
            return listOfNotNull(preferred) + others
        }

        val lower = userMessage.lowercase()
        val isCoding = listOf("code", "kotlin", "python", "html", "script", "bug", "debug", "app bana")
            .any { lower.contains(it) }

        val priority = if (isCoding) {
            listOf(ProviderType.CLAUDE, ProviderType.GEMINI, ProviderType.GROQ, ProviderType.CUSTOM, ProviderType.LOCAL)
        } else {
            listOf(ProviderType.GEMINI, ProviderType.GROQ, ProviderType.CLAUDE, ProviderType.CUSTOM, ProviderType.LOCAL)
        }

        return priority.mapNotNull { type -> ready.firstOrNull { it.provider == type } }
    }

    private fun buildSystemPrompt(
        mode: ExecutionMode,
        skills: List<SkillItem>,
        sandboxApps: List<SandboxApp>,
        memoryRules: List<MemoryRule>
    ): String {
        val activeSkills = skills.filter { it.enabled }.joinToString("\n") {
            val tag = if (it.isNew) "[NEWLY LEARNED GITHUB SKILL]" else if (it.isCore) "[CORE]" else "[INSTALLED]"
            "- $tag ${it.name} (${it.mode.name}): ${it.description}" +
                if (it.instructions.isNotBlank()) " | Guide: ${it.instructions.take(180)}" else ""
        }
        val appsList = sandboxApps.joinToString(", ") { "${it.name} (${it.url})" }
        val rules = if (memoryRules.isEmpty()) "None yet." else memoryRules.takeLast(8).joinToString("\n") { "- [${it.category}] ${it.content}" }

        return """
You are NAX — a sleek, ultra-helpful mobile AI Assistant inspired by Jarvis, combining OpenClaw skills, Antigravity autonomous planning, and Claude reasoning.
Speak naturally in crisp Hinglish/Hindi matching the user's style. Be concise, practical, and action-oriented.

ACTIVE EXECUTION MODE: ${mode.name}
- SANDBOX = Phone-inside-Phone isolated virtual workspace (safe, zero disturbance to real phone).
- LIVE = Direct control of the user's real Android phone (protected by Privacy Shield for UPI/Banking).

SANDBOX INSTALLED APPS: $appsList

YOUR CURRENT INSTALLED SKILLS (Dynamic Self-Awareness):
$activeSkills

USER MEMORY RULES:
$rules

SPECIAL OUTPUT TAGS (use ONLY when genuinely useful):
1. If you create a plan, script, code, mini HTML app, or structured report, wrap it cleanly inside:
<artifact title="Short Title" type="code|plan|html|notes">
...content...
</artifact>
2. If the user asks you to open/control an app right now in ${mode.name} mode, append ONE tag at the end:
<action mode="${mode.name}" target="AppNameOrUrl" command="short instruction" />
        """.trimIndent()
    }

    private fun parseStructuredReply(
        raw: String,
        providerName: String,
        defaultMode: ExecutionMode
    ): BrainReply {
        var text = raw
        var artifact: ArtifactPreview? = null
        var action: SuggestedDeviceAction? = null

        val artRegex = Regex("""<artifact\s+title="([^"]+)"\s+type="([^"]+)">([\s\S]*?)</artifact>""")
        artRegex.find(text)?.let { match ->
            artifact = ArtifactPreview(
                title = match.groupValues[1].trim(),
                type = match.groupValues[2].trim(),
                content = match.groupValues[3].trim()
            )
            text = text.replace(match.value, "").trim()
        }

        val actRegex = Regex("""<action\s+mode="([^"]+)"\s+target="([^"]+)"\s+command="([^"]+)"\s*/>""")
        actRegex.find(text)?.let { match ->
            val parsedMode = runCatching { ExecutionMode.valueOf(match.groupValues[1].uppercase()) }
                .getOrDefault(defaultMode)
            action = SuggestedDeviceAction(
                mode = parsedMode,
                target = match.groupValues[2].trim(),
                command = match.groupValues[3].trim()
            )
            text = text.replace(match.value, "").trim()
        }

        return BrainReply(
            cleanText = if (text.isBlank()) "Done boss! Neeche card check karo." else text,
            providerUsed = providerName,
            artifact = artifact,
            suggestedAction = action
        )
    }

    private fun offlineFallbackReply(
        userMessage: String,
        mode: ExecutionMode,
        sandboxApps: List<SandboxApp>
    ): BrainReply {
        val lower = userMessage.lowercase()
        val matchedSandboxApp = sandboxApps.firstOrNull { lower.contains(it.name.lowercase()) }
        if (matchedSandboxApp != null && listOf("kholo", "open", "chalao", "launch", "search").any { lower.contains(it) }) {
            return BrainReply(
                cleanText = "Boss, ${mode.name} mode mein **${matchedSandboxApp.name}** open karne ke liye ready hoon! Neeche approve ya run karo.",
                providerUsed = "NAX Local Router",
                suggestedAction = SuggestedDeviceAction(
                    mode = mode,
                    target = matchedSandboxApp.name,
                    command = userMessage
                )
            )
        }

        if (mode == ExecutionMode.LIVE && listOf("settings", "wifi", "battery", "bluetooth", "whatsapp", "youtube", "instagram").any { lower.contains(it) }) {
            val target = listOf("Settings", "Wi-Fi", "Battery", "Bluetooth", "WhatsApp", "YouTube", "Instagram")
                .firstOrNull { lower.contains(it.lowercase().replace("-", "")) || lower.contains(it.lowercase()) }
                ?: "Settings"
            return BrainReply(
                cleanText = "Live Mode ready hai! **$target** par action chalane ke liye neeche confirm karo.",
                providerUsed = "NAX Local Router",
                suggestedAction = SuggestedDeviceAction(
                    mode = ExecutionMode.LIVE,
                    target = target,
                    command = userMessage
                )
            )
        }

        return BrainReply(
            cleanText = "Hi Boss! Main **NAX** hoon (${mode.name} mode active). Abhi koi cloud API key set nahi hai — full Jarvis AI chat ke liye **Brain tab** mein Gemini, Groq, Claude, ya Local API key daal kar 1-tap Test kar lo!\n\nBina API key ke bhi aap abhi try kar sakte ho:\n• Chat mein koi bhi **GitHub repo link** bhejo install karne ke liye\n• Poocho: *\"Tumhe kya-kya aata hai?\"*\n• Bolo: *\"Roz subah 9 baje Sandbox mein YouTube check karna\"*\n• Ya bolo: *\"Sandbox mein YouTube kholo\"*",
            providerUsed = "NAX Offline Core"
        )
    }
}
