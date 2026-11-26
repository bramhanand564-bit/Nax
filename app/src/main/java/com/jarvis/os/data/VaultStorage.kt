package com.jarvis.os.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class VaultStorage(context: Context) {

    private val prefs = context.getSharedPreferences("nax_vault_v1", Context.MODE_PRIVATE)

    // ---------------- API CONFIGS ----------------

    fun loadApiConfigs(): List<ApiConfig> {
        val raw = prefs.getString("api_configs", null)
        val savedMap = mutableMapOf<ProviderType, ApiConfig>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val provider = ProviderType.valueOf(o.getString("provider"))
                    val testObj = o.optJSONObject("lastTest")
                    val testRes = testObj?.let {
                        ApiTestResult(
                            success = it.optBoolean("success", false),
                            latencyMs = it.optLong("latencyMs", 0L),
                            visionReady = it.optBoolean("visionReady", false),
                            jsonAgentReady = it.optBoolean("jsonAgentReady", false),
                            message = it.optString("message", ""),
                            testedAt = it.optLong("testedAt", 0L)
                        )
                    }
                    savedMap[provider] = ApiConfig(
                        provider = provider,
                        apiKey = o.optString("apiKey", ""),
                        model = o.optString("model", provider.defaultModel),
                        baseUrl = o.optString("baseUrl", provider.defaultBaseUrl),
                        enabled = o.optBoolean("enabled", true),
                        lastTest = testRes
                    )
                }
            } catch (_: Exception) {
            }
        }
        return ProviderType.entries.map { savedMap[it] ?: ApiConfig(provider = it) }
    }

    fun saveApiConfigs(configs: List<ApiConfig>) {
        val arr = JSONArray()
        configs.forEach { cfg ->
            val o = JSONObject().apply {
                put("provider", cfg.provider.name)
                put("apiKey", cfg.apiKey)
                put("model", cfg.model)
                put("baseUrl", cfg.baseUrl)
                put("enabled", cfg.enabled)
                cfg.lastTest?.let { t ->
                    put("lastTest", JSONObject().apply {
                        put("success", t.success)
                        put("latencyMs", t.latencyMs)
                        put("visionReady", t.visionReady)
                        put("jsonAgentReady", t.jsonAgentReady)
                        put("message", t.message)
                        put("testedAt", t.testedAt)
                    })
                }
            }
            arr.put(o)
        }
        prefs.edit().putString("api_configs", arr.toString()).apply()
    }

    // ---------------- SKILLS REGISTRY ----------------

    fun loadSkills(): List<SkillItem> {
        val raw = prefs.getString("skills_list", null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultCoreSkills()
            saveSkills(defaults)
            return defaults
        }
        val list = mutableListOf<SkillItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SkillItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        description = o.optString("description"),
                        repoUrl = o.optString("repoUrl"),
                        mode = runCatching { ExecutionMode.valueOf(o.optString("mode", "SANDBOX")) }
                            .getOrDefault(ExecutionMode.SANDBOX),
                        isCore = o.optBoolean("isCore", false),
                        isNew = o.optBoolean("isNew", false),
                        commandsHint = o.optString("commandsHint"),
                        instructions = o.optString("instructions"),
                        enabled = o.optBoolean("enabled", true),
                        installedAt = o.optLong("installedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            return defaultCoreSkills()
        }
        return if (list.isEmpty()) defaultCoreSkills() else list
    }

    fun saveSkills(skills: List<SkillItem>) {
        val arr = JSONArray()
        skills.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("description", s.description)
                put("repoUrl", s.repoUrl)
                put("mode", s.mode.name)
                put("isCore", s.isCore)
                put("isNew", s.isNew)
                put("commandsHint", s.commandsHint)
                put("instructions", s.instructions)
                put("enabled", s.enabled)
                put("installedAt", s.installedAt)
            })
        }
        prefs.edit().putString("skills_list", arr.toString()).apply()
    }

    private fun defaultCoreSkills(): List<SkillItem> = listOf(
        SkillItem(
            id = "core_sandbox",
            name = "Sandbox Phone Control",
            description = "Phone ke andar isolated apps (YouTube, Instagram, Flow, Web) X-Ray DOM map ke saath control karna.",
            mode = ExecutionMode.SANDBOX,
            isCore = true,
            commandsHint = "Sandbox mein YouTube kholo aur AI reels search karo"
        ),
        SkillItem(
            id = "core_live",
            name = "Live Real Phone Control",
            description = "Real Android apps launch karna, tap/swipe/type karna aur Settings control karna (Privacy Shield ke saath).",
            mode = ExecutionMode.LIVE,
            isCore = true,
            commandsHint = "Live mode mein Settings kholo aur battery check karo"
        ),
        SkillItem(
            id = "core_github_installer",
            name = "GitHub Skill Auto-Installer",
            description = "Chat mein koi bhi GitHub repo link do — NAX security scan karke apne andar naya skill install kar lega.",
            mode = ExecutionMode.SANDBOX,
            isCore = true,
            commandsHint = "Is GitHub repo ko install karo: https://github.com/..."
        ),
        SkillItem(
            id = "core_routines",
            name = "n8n-Style Daily Routine Builder",
            description = "Chat se hi daily/timed workflows banana aur Routines tab mein auto-save karke background mein chalana.",
            mode = ExecutionMode.SANDBOX,
            isCore = true,
            commandsHint = "Roz subah 9 baje Sandbox mein trending tech news check karna"
        )
    )

    // ---------------- SAVED N8N ROUTINES ----------------

    fun loadRoutines(): List<RoutineFlow> {
        val raw = prefs.getString("routines_list", null) ?: return emptyList()
        val list = mutableListOf<RoutineFlow>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val stepsArr = o.optJSONArray("steps") ?: JSONArray()
                val steps = mutableListOf<RoutineStep>()
                for (j in 0 until stepsArr.length()) {
                    val st = stepsArr.getJSONObject(j)
                    steps.add(
                        RoutineStep(
                            mode = runCatching { ExecutionMode.valueOf(st.optString("mode", "SANDBOX")) }
                                .getOrDefault(ExecutionMode.SANDBOX),
                            label = st.optString("label"),
                            command = st.optString("command")
                        )
                    )
                }
                list.add(
                    RoutineFlow(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        triggerTime = o.optString("triggerTime"),
                        frequency = o.optString("frequency", "Daily"),
                        steps = steps,
                        enabled = o.optBoolean("enabled", true),
                        lastRunStatus = o.optString("lastRunStatus", "Scheduled"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveRoutines(routines: List<RoutineFlow>) {
        val arr = JSONArray()
        routines.forEach { r ->
            val stepsArr = JSONArray()
            r.steps.forEach { st ->
                stepsArr.put(JSONObject().apply {
                    put("mode", st.mode.name)
                    put("label", st.label)
                    put("command", st.command)
                })
            }
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("triggerTime", r.triggerTime)
                put("frequency", r.frequency)
                put("steps", stepsArr)
                put("enabled", r.enabled)
                put("lastRunStatus", r.lastRunStatus)
                put("createdAt", r.createdAt)
            })
        }
        prefs.edit().putString("routines_list", arr.toString()).apply()
    }

    // ---------------- SANDBOX APPS ----------------

    fun loadSandboxApps(): List<SandboxApp> {
        val raw = prefs.getString("sandbox_apps", null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultSandboxApps()
            saveSandboxApps(defaults)
            return defaults
        }
        val list = mutableListOf<SandboxApp>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val controls = mutableListOf<String>()
                val cArr = o.optJSONArray("learnedControls") ?: JSONArray()
                for (j in 0 until cArr.length()) controls.add(cArr.optString(j))
                list.add(
                    SandboxApp(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        url = o.optString("url"),
                        emoji = o.optString("emoji", "📱"),
                        description = o.optString("description"),
                        learnedControls = controls
                    )
                )
            }
        } catch (_: Exception) {
            return defaultSandboxApps()
        }
        return if (list.isEmpty()) defaultSandboxApps() else list
    }

    fun saveSandboxApps(apps: List<SandboxApp>) {
        val arr = JSONArray()
        apps.forEach { a ->
            val cArr = JSONArray()
            a.learnedControls.forEach { cArr.put(it) }
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("url", a.url)
                put("emoji", a.emoji)
                put("description", a.description)
                put("learnedControls", cArr)
            })
        }
        prefs.edit().putString("sandbox_apps", arr.toString()).apply()
    }

    private fun defaultSandboxApps(): List<SandboxApp> = listOf(
        SandboxApp("sb_yt", "YouTube", "https://m.youtube.com", "▶️", "Isolated YouTube Sandbox with X-Ray DOM Control"),
        SandboxApp("sb_ig", "Instagram", "https://www.instagram.com", "📸", "Isolated Instagram Reels & Messages Sandbox"),
        SandboxApp("sb_flow", "Google Flow", "https://labs.google/fx/tools/flow", "🎬", "AI Video Generation Workspace inside Sandbox"),
        SandboxApp("sb_gh", "GitHub", "https://github.com", "🐙", "Browse & inspect GitHub AI skills inside Sandbox"),
        SandboxApp("sb_search", "Google", "https://www.google.com", "🌐", "Fast Sandbox Browser & Research Engine")
    )

    // ---------------- MEMORY RULES & TELEMETRY ----------------

    fun loadMemoryRules(): List<MemoryRule> {
        val raw = prefs.getString("memory_rules", null) ?: return emptyList()
        val list = mutableListOf<MemoryRule>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MemoryRule(
                        id = o.optString("id"),
                        category = o.optString("category"),
                        content = o.optString("content"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveMemoryRules(rules: List<MemoryRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("category", r.category)
                put("content", r.content)
                put("createdAt", r.createdAt)
            })
        }
        prefs.edit().putString("memory_rules", arr.toString()).apply()
    }

    fun loadTelemetry(): TelemetryStats = TelemetryStats(
        totalCalls = prefs.getInt("tel_calls", 0),
        totalTokens = prefs.getLong("tel_tokens", 0L),
        lastLatencyMs = prefs.getLong("tel_latency", 0L),
        activeProvider = prefs.getString("tel_provider", "Auto") ?: "Auto"
    )

    fun recordCall(tokensEstimate: Long, latencyMs: Long, providerName: String) {
        val cur = loadTelemetry()
        prefs.edit()
            .putInt("tel_calls", cur.totalCalls + 1)
            .putLong("tel_tokens", cur.totalTokens + tokensEstimate)
            .putLong("tel_latency", latencyMs)
            .putString("tel_provider", providerName)
            .apply()
    }

    fun loadSelectedProvider(): String = prefs.getString("selected_provider", "AUTO") ?: "AUTO"

    fun saveSelectedProvider(code: String) {
        prefs.edit().putString("selected_provider", code).apply()
    }
}
