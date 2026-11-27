package com.jarvis.os.brain

import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ArtifactPreview
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.MemoryRule
import com.jarvis.os.data.ProviderType
import com.jarvis.os.data.SandboxApp
import com.jarvis.os.data.SkillItem
import com.jarvis.os.data.ThinkingLevel
import com.jarvis.os.data.VaultStorage

data class SuggestedDeviceAction(
    val mode: ExecutionMode,
    val type: String,
    val target: String,
    val command: String
)

data class SelfCreatedSkillSpec(
    val name: String,
    val description: String,
    val mode: ExecutionMode,
    val instructions: String
)

data class BrainReply(
    val cleanText: String,
    val providerUsed: String,
    val artifact: ArtifactPreview? = null,
    val suggestedAction: SuggestedDeviceAction? = null,
    val selfCreatedSkill: SelfCreatedSkillSpec? = null
)

class NaxBrain(private val vault: VaultStorage) {

    fun extractGitHubRepoUrl(message: String): String? {
        val regex = Regex("""https?://(www\.)?github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+""")
        return regex.find(message)?.value?.trimEnd('/', '.')
    }

    fun isBtwQuestion(message: String): Boolean {
        val m = message.trim().lowercase()
        return m.startsWith("/btw") || m.startsWith("btw ")
    }

    fun isGrillMeRequest(message: String): Boolean {
        val m = message.lowercase()
        return m.startsWith("/grill") || m.contains("grill me") || m.contains("grill karo") || m.contains("loophole")
    }

    fun isFindSkillsRequest(message: String): Boolean {
        if (extractGitHubRepoUrl(message) != null) return false
        val m = message.lowercase()
        val triggers = listOf(
            "find skill", "find-skill", "search skill", "koi skill", "skill dhoondh",
            "skill dhund", "skills dhoondh", "best skill", "github skill batao"
        )
        return triggers.any { m.contains(it) }
    }

    fun isVideoFolderRequest(message: String): Boolean {
        val m = message.lowercase()
        return listOf("video folder", "check video", "mere videos", "download videos", "gallery videos")
            .any { m.contains(it) }
    }

    fun isPreviewHtmlRequest(message: String): Boolean {
        val m = message.lowercase()
        return listOf("preview", "play kar", "khelna", "chala ke dikhao", "run kar ke dikhao", "run karke dikhao", "nahi dikha", "nahibdikha")
            .any { m.contains(it) }
    }

    fun isAskingCapabilities(message: String): Boolean {
        val m = message.lowercase()
        return listOf(
            "kya kya aata", "kya-kya aata", "kya aata hai", "tumhe kya aata",
            "tujhe kya aata", "what can you do", "your skills", "apni skills",
            "installed skills", "list skills", "capabilities"
        ).any { m.contains(it) }
    }

    // FIXED: Strict word boundaries so "instagram" or "kam" NEVER falsely triggers Routine Builder!
    fun isRoutineRequest(message: String): Boolean {
        val m = message.lowercase()
        if (listOf("mat bana", "jab bolu", "abhi run", "cancel", "delete").any { m.contains(it) }) {
            return false
        }
        val explicitSchedule = Regex("""\b(roz|roj|daily|every\s+day|har\s+roz|har\s+din|schedule\s+kar|routine\s+set|routine\s+bana|always[\s-]on)\b""").containsMatchIn(m)
        val explicitClock = Regex("""\b\d{1,2}(:\d{2})?\s*(am|pm|baje)\b""").containsMatchIn(m)
        val eventRoutine = (m.contains("routine") || m.contains("jaise hi")) &&
            (m.contains("battery") || m.contains("charg") || m.contains("new video"))
        return explicitSchedule || explicitClock || eventRoutine
    }

    // SMART AUTO MODE: Respects explicit user words ("private/sandbox/live") or intelligently guesses!
    fun resolveSmartMode(message: String, selectedMode: ExecutionMode): ExecutionMode {
        val m = message.lowercase()
        if (listOf("sandbox", "private mein", "private me", "private karo", "in sandbox").any { m.contains(it) }) {
            return ExecutionMode.SANDBOX
        }
        if (listOf("live mein", "live me", "live karo", "real phone", "asli phone").any { m.contains(it) }) {
            return ExecutionMode.LIVE
        }
        if (selectedMode != ExecutionMode.AUTO) return selectedMode

        val liveKeywords = listOf(
            "battery", "brightness", "brithness", "volume", "screenshot", "settings",
            "wifi", "bluetooth", "instagram", "whatsapp", "call", "alarm", "real app"
        )
        return if (liveKeywords.any { m.contains(it) }) ExecutionMode.LIVE else ExecutionMode.SANDBOX
    }

    // Fast Instant Local Hardware Detection (Zero Hallucination!)
    fun detectDirectHardwareAction(message: String): SuggestedDeviceAction? {
        val m = message.lowercase()

        // Brightness control
        if (m.contains("brightness") || m.contains("brithness") || m.contains("roshni")) {
            val num = Regex("""\b(\d{1,3})\s*%?""").find(m)?.groupValues?.get(1)?.toIntOrNull()
            val targetPct = when {
                num != null -> num.coerceIn(5, 100)
                listOf("kam", "low", "dim", "min", "ghata", "down").any { m.contains(it) } -> 20
                listOf("full", "max", "zyada", "jyada", "badha", "high", "up").any { m.contains(it) } -> 90
                else -> 50
            }
            return SuggestedDeviceAction(ExecutionMode.LIVE, "BRIGHTNESS", targetPct.toString(), "Set real screen brightness to $targetPct%")
        }

        // Volume control
        if (m.contains("volume") || m.contains("awaaz") || m.contains("sound")) {
            val num = Regex("""\b(\d{1,3})\s*%?""").find(m)?.groupValues?.get(1)?.toIntOrNull()
            val targetPct = when {
                num != null -> num.coerceIn(0, 100)
                listOf("mute", "band", "silent", "zero").any { m.contains(it) } -> 0
                listOf("kam", "low", "slow", "down").any { m.contains(it) } -> 25
                listOf("full", "max", "zyada", "high", "badha").any { m.contains(it) } -> 95
                else -> 55
            }
            return SuggestedDeviceAction(ExecutionMode.LIVE, "VOLUME", targetPct.toString(), "Set media volume to $targetPct%")
        }

        // Real Screenshot
        if (m.contains("screenshot") && listOf("lo", "le", "take", "capture", "khinch", "bhej").any { m.contains(it) }) {
            return SuggestedDeviceAction(ExecutionMode.LIVE, "SCREENSHOT", "screen", "Capture real phone screenshot")
        }

        // Multi-step Live App Search & Play (e.g. "open instagram and search bhojpuri song and play 1st video")
        val appTargets = listOf("instagram", "youtube", "whatsapp", "chrome", "spotify", "settings")
        val matchedApp = appTargets.firstOrNull { m.contains(it) }
        val hasMultiStepIntent = listOf("search", "play", "dhund", "chalao", "video", "song", "reel").any { m.contains(it) }
        if (matchedApp != null && hasMultiStepIntent && !m.contains("sandbox") && !m.contains("private")) {
            return SuggestedDeviceAction(
                mode = ExecutionMode.LIVE,
                type = "MULTI_STEP",
                target = matchedApp.replaceFirstChar { it.uppercase() },
                command = message.trim()
            )
        }

        return null
    }

    suspend fun generateReply(
        userMessage: String,
        mode: ExecutionMode,
        thinkingLevel: ThinkingLevel,
        selectedProviderCode: String,
        configs: List<ApiConfig>,
        skills: List<SkillItem>,
        sandboxApps: List<SandboxApp>,
        memoryRules: List<MemoryRule>,
        liveDeviceSnapshot: String,
        liveScreenSummary: String,
        history: List<Pair<Boolean, String>>,
        grillMeMode: Boolean = false,
        btwMode: Boolean = false,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ): BrainReply {
        val orderedProviders = buildProviderChain(selectedProviderCode, configs, userMessage, !imageBase64.isNullOrBlank())
        val systemPrompt = buildSystemPrompt(
            mode = mode,
            thinkingLevel = thinkingLevel,
            skills = skills,
            sandboxApps = sandboxApps,
            memoryRules = memoryRules,
            liveDeviceSnapshot = liveDeviceSnapshot,
            liveScreenSummary = liveScreenSummary,
            grillMeMode = grillMeMode,
            btwMode = btwMode
        )

        for (cfg in orderedProviders) {
            val res = ApiClient.chat(cfg, systemPrompt, history, userMessage, imageBase64, imageMimeType)
            if (res.success) {
                vault.recordCall(res.tokensUsed, res.latencyMs, cfg.provider.displayName)
                return parseStructuredReply(res.text, cfg.provider.displayName, mode)
            }
        }

        return offlineFallbackReply(userMessage, mode, sandboxApps)
    }

    private fun buildProviderChain(
        selectedCode: String,
        configs: List<ApiConfig>,
        userMessage: String,
        hasImage: Boolean
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
        val isCoding = listOf("code", "kotlin", "python", "html", "script", "app bana", "game").any { lower.contains(it) }
        val priority = when {
            hasImage -> listOf(ProviderType.GEMINI, ProviderType.CLAUDE, ProviderType.CUSTOM, ProviderType.GROQ, ProviderType.LOCAL)
            isCoding -> listOf(ProviderType.CLAUDE, ProviderType.GEMINI, ProviderType.GROQ, ProviderType.CUSTOM, ProviderType.LOCAL)
            else -> listOf(ProviderType.GEMINI, ProviderType.GROQ, ProviderType.CLAUDE, ProviderType.CUSTOM, ProviderType.LOCAL)
        }
        return priority.mapNotNull { type -> ready.firstOrNull { it.provider == type } }
    }

    private fun buildSystemPrompt(
        mode: ExecutionMode,
        thinkingLevel: ThinkingLevel,
        skills: List<SkillItem>,
        sandboxApps: List<SandboxApp>,
        memoryRules: List<MemoryRule>,
        liveDeviceSnapshot: String,
        liveScreenSummary: String,
        grillMeMode: Boolean,
        btwMode: Boolean
    ): String {
        val activeSkills = skills.filter { it.enabled }.joinToString("\n") {
            val tag = if (it.isNew) "[NEW SKILL]" else if (it.isCore) "[CORE]" else "[INSTALLED]"
            "- $tag ${it.name} (${it.mode.name}): ${it.description}"
        }
        val appsList = sandboxApps.joinToString(", ") { it.name }
        val rules = if (memoryRules.isEmpty()) "None" else memoryRules.takeLast(8).joinToString("\n") { "- [${it.category}] ${it.content}" }

        val specialModeDirective = when {
            btwMode -> "SIDE QUESTION (/btw): Answer super briefly and directly in 2-3 lines without disturbing any main ongoing work."
            grillMeMode -> "GRILL-ME LOOPHOLE FINDER MODE: Critically interrogate the user's plan/idea. Ask 3-4 sharp, practical cross-questions exposing hidden loopholes (offline edge cases, failure modes, security, cost, scaling) until 0 loopholes remain!"
            thinkingLevel == ThinkingLevel.XHIGH -> "ORCHESTRATOR MODE (XHigh): Think deeply, break into clear plan + build + verify steps, and produce complete production-quality output."
            thinkingLevel == ThinkingLevel.LOW -> "FAST MODE (Low): Be super concise, direct, 2-4 lines max."
            else -> "BALANCED MODE: Be concise, natural Hinglish/Hindi like Jarvis ('Boss'), helpful and practical."
        }

        return """
You are NAX — a sleek Jarvis-like Mobile AI Assistant (Body = NAX App, Mind = API, Shirts = Skills).
$specialModeDirective

CRITICAL HONESTY RULE:
- NEVER hallucinate or guess Battery %, Brightness %, Volume %, RAM, or Screen contents!
- ALWAYS read exact real numbers from the REAL-TIME ANDROID DEVICE SENSORS section below.

$liveDeviceSnapshot
LIVE ACCESSIBILITY SCREEN SNAPSHOT: $liveScreenSummary

ACTIVE MODE: ${mode.name}
SANDBOX INSTALLED APPS: $appsList

YOUR INSTALLED SKILLS (Dynamic Self-Awareness):
$activeSkills

USER MEMORY RULES:
$rules

STRUCTURED TAGS (use ONLY when genuinely needed):
1. Whenever building a playable game (e.g. Tic Tac Toe), calculator, HyperFrames animation, HTML mini-app, code, or plan, output COMPLETE self-contained code inside:
<artifact title="Short Title" type="html|code|plan|notes">
...complete standalone code...
</artifact>
2. If you want to create and wear a brand-new reusable skill ("Shirt") inside NAX without APK rebuild:
<new_skill name="Skill Title" desc="1-line summary" mode="SANDBOX">step-by-step instructions</new_skill>
3. If the user asks to execute a real device or sandbox action right now, add ONE tag at the end:
<action mode="${mode.name}" type="OPEN|MULTI_STEP|BRIGHTNESS|VOLUME|SCREENSHOT|PREVIEW_HTML" target="AppOrValue" command="short command" />
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
        var createdSkill: SelfCreatedSkillSpec? = null

        val artRegex = Regex("""<artifact\s+title="([^"]+)"\s+type="([^"]+)">([\s\S]*?)</artifact>""")
        artRegex.find(text)?.let { match ->
            artifact = ArtifactPreview(
                title = match.groupValues[1].trim(),
                type = match.groupValues[2].trim(),
                content = match.groupValues[3].trim()
            )
            text = text.replace(match.value, "").trim()
        }

        val skillRegex = Regex("""<new_skill\s+name="([^"]+)"\s+desc="([^"]+)"\s+mode="([^"]+)">([\s\S]*?)</new_skill>""")
        skillRegex.find(text)?.let { match ->
            val m = runCatching { ExecutionMode.valueOf(match.groupValues[3].uppercase()) }.getOrDefault(ExecutionMode.SANDBOX)
            createdSkill = SelfCreatedSkillSpec(
                name = match.groupValues[1].trim(),
                description = match.groupValues[2].trim(),
                mode = m,
                instructions = match.groupValues[4].trim()
            )
            text = text.replace(match.value, "").trim()
        }

        val actRegex = Regex("""<action\s+mode="([^"]+)"(?:\s+type="([^"]+)")?\s+target="([^"]+)"\s+command="([^"]+)"\s*/>""")
        actRegex.find(text)?.let { match ->
            val parsedMode = runCatching { ExecutionMode.valueOf(match.groupValues[1].uppercase()) }
                .getOrDefault(defaultMode)
            val parsedType = match.groupValues[2].ifBlank { "OPEN" }.uppercase()
            action = SuggestedDeviceAction(
                mode = parsedMode,
                type = parsedType,
                target = match.groupValues[3].trim(),
                command = match.groupValues[4].trim()
            )
            text = text.replace(match.value, "").trim()
        }

        return BrainReply(
            cleanText = if (text.isBlank()) "Done Boss! Neeche card check karo." else text,
            providerUsed = providerName,
            artifact = artifact,
            suggestedAction = action,
            selfCreatedSkill = createdSkill
        )
    }

    private fun offlineFallbackReply(
        userMessage: String,
        mode: ExecutionMode,
        sandboxApps: List<SandboxApp>
    ): BrainReply {
        val lower = userMessage.lowercase()
        val matchedApp = sandboxApps.firstOrNull { lower.contains(it.name.lowercase()) }
        if (matchedApp != null && listOf("kholo", "open", "chalao").any { lower.contains(it) }) {
            return BrainReply(
                cleanText = "Boss, ${mode.name} mode mein **${matchedApp.name}** open karne ke liye ready hoon!",
                providerUsed = "NAX Local Core",
                suggestedAction = SuggestedDeviceAction(mode, "OPEN", matchedApp.name, userMessage)
            )
        }
        return BrainReply(
            cleanText = "Hi Boss! NAX online hai. Full AI chat ke liye **Brain tab** mein Gemini, Groq, Claude ya Local API set kar lo — bina API key ke bhi aap real Battery, Brightness, Volume, Screenshot, Find-Skills, Video Folder aur Routines test kar sakte ho!",
            providerUsed = "NAX Offline Core"
        )
    }
}
