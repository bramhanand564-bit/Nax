package com.jarvis.os.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.brain.ApiTestBench
import com.jarvis.os.brain.NaxBrain
import com.jarvis.os.data.ActionApproval
import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ChatMessage
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.InstallStatus
import com.jarvis.os.data.MemoryRule
import com.jarvis.os.data.PendingRoutineApproval
import com.jarvis.os.data.ProviderType
import com.jarvis.os.data.RoutineFlow
import com.jarvis.os.data.SandboxApp
import com.jarvis.os.data.SandboxControlNode
import com.jarvis.os.data.SkillItem
import com.jarvis.os.data.TelemetryStats
import com.jarvis.os.data.VaultStorage
import com.jarvis.os.live.JarvisForegroundService
import com.jarvis.os.live.LiveTouchService
import com.jarvis.os.routines.RoutineManager
import com.jarvis.os.skills.GitHubSkillEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NaxUiState(
    val activeTab: Int = 0,
    val executionMode: ExecutionMode = ExecutionMode.SANDBOX,
    val selectedProviderCode: String = "AUTO",
    val isThinking: Boolean = false,
    val testingProvider: ProviderType? = null,
    val messages: List<ChatMessage> = emptyList(),
    val skills: List<SkillItem> = emptyList(),
    val routines: List<RoutineFlow> = emptyList(),
    val apiConfigs: List<ApiConfig> = emptyList(),
    val sandboxApps: List<SandboxApp> = emptyList(),
    val activeSandboxApp: SandboxApp? = null,
    val activeSandboxUrl: String = "https://m.youtube.com",
    val sandboxControls: List<SandboxControlNode> = emptyList(),
    val xrayEnabled: Boolean = false,
    val teachModeEnabled: Boolean = false,
    val showSandboxPip: Boolean = false,
    val memoryRules: List<MemoryRule> = emptyList(),
    val telemetry: TelemetryStats = TelemetryStats(),
    val engineRunning: Boolean = false,
    val liveConnected: Boolean = false,
    val bannerMessage: String = ""
)

class NaxViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val vault = VaultStorage(ctx)
    private val brain = NaxBrain(vault)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<NaxUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): NaxUiState {
        val apps = vault.loadSandboxApps()
        val welcome = ChatMessage(
            isUser = false,
            text = "Hello Boss! Main **NAX** hoon — aapka all-in-one Sandbox + Live AI Assistant.\n\nChat box se seedha **Sandbox** ya **Live** chuno, koi bhi **GitHub skill link** paste karo, ya bolo *\"Roz subah 9 baje...\"* daily routine banane ke liye!",
            mode = ExecutionMode.SANDBOX,
            providerBadge = "NAX Core"
        )
        return NaxUiState(
            selectedProviderCode = vault.loadSelectedProvider(),
            messages = listOf(welcome),
            skills = vault.loadSkills(),
            routines = vault.loadRoutines(),
            apiConfigs = vault.loadApiConfigs(),
            sandboxApps = apps,
            activeSandboxApp = apps.firstOrNull(),
            activeSandboxUrl = apps.firstOrNull()?.url ?: "https://m.youtube.com",
            memoryRules = vault.loadMemoryRules(),
            telemetry = vault.loadTelemetry(),
            engineRunning = JarvisForegroundService.isRunning,
            liveConnected = LiveTouchService.isConnected()
        )
    }

    fun selectTab(index: Int) {
        _uiState.update {
            it.copy(
                activeTab = index,
                liveConnected = LiveTouchService.isConnected(),
                engineRunning = JarvisForegroundService.isRunning
            )
        }
    }

    fun setExecutionMode(mode: ExecutionMode) {
        _uiState.update {
            it.copy(
                executionMode = mode,
                liveConnected = LiveTouchService.isConnected()
            )
        }
    }

    fun setSelectedProvider(code: String) {
        vault.saveSelectedProvider(code)
        _uiState.update { it.copy(selectedProviderCode = code) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = "") }
    }

    fun refreshLiveStatus() {
        _uiState.update {
            it.copy(
                liveConnected = LiveTouchService.isConnected(),
                engineRunning = JarvisForegroundService.isRunning
            )
        }
    }

    fun toggleForegroundEngine() {
        if (JarvisForegroundService.isRunning) {
            JarvisForegroundService.stop(ctx)
            _uiState.update { it.copy(engineRunning = false, bannerMessage = "NAX 24/7 Engine stopped") }
        } else {
            JarvisForegroundService.start(ctx)
            _uiState.update { it.copy(engineRunning = true, bannerMessage = "NAX 24/7 Engine active") }
        }
    }

    // ---------------- CHAT-FIRST COMMAND CENTER ----------------

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return

        val current = _uiState.value
        val resolvedMode = brain.detectModeHint(text, current.executionMode)

        val userMsg = ChatMessage(
            isUser = true,
            text = text,
            mode = resolvedMode
        )

        _uiState.update {
            it.copy(
                executionMode = resolvedMode,
                messages = it.messages + userMsg,
                isThinking = true
            )
        }

        viewModelScope.launch {
            // 1. Check if user pasted a GitHub Repo Skill link in Chat
            val githubUrl = brain.extractGitHubRepoUrl(text)
            if (githubUrl != null) {
                val pending = GitHubSkillEngine.inspectRepoForChatApproval(githubUrl, resolvedMode)
                val reply = ChatMessage(
                    isUser = false,
                    text = "Boss, maine is GitHub skill repo ko scan kar liya hai. Details aur Security Audit neeche hai — yahin se approve karke install kar lo:",
                    mode = resolvedMode,
                    providerBadge = "GitHub Skill Scanner",
                    pendingSkill = pending
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 2. Check Self-Awareness ("Tumhe kya-kya aata hai?")
            if (brain.isAskingCapabilities(text)) {
                val installedCount = _uiState.value.skills.count { it.enabled }
                val newCount = _uiState.value.skills.count { it.isNew && it.enabled }
                val intro = if (newCount > 0) {
                    "Boss, ab mujhe total **$installedCount skills** aate hain (jismein **$newCount naye seekhe hue GitHub skills** bhi shamil hain!). Neeche dekho:"
                } else {
                    "Boss, abhi mujhe ye **$installedCount core skills** aate hain — aur koi bhi GitHub link bhejoge toh naye skills turant seekh lunga:"
                }
                val reply = ChatMessage(
                    isUser = false,
                    text = intro,
                    mode = resolvedMode,
                    providerBadge = "NAX Self-Awareness",
                    showCapabilities = true
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 3. Check n8n-Style Timed Routine Creation directly from Chat
            if (brain.isRoutineRequest(text)) {
                val flow = RoutineManager.parseFlowFromChat(text, resolvedMode)
                val reply = ChatMessage(
                    isUser = false,
                    text = "Boss, aapka n8n-style daily flow ready hai! Approve karte hi ye **Routines** tab mein auto-save ho kar schedule ho jayega:",
                    mode = resolvedMode,
                    providerBadge = "NAX Routine Builder",
                    pendingRoutine = PendingRoutineApproval(flow = flow)
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 4. General Jarvis Multi-Model Brain Call
            val stateSnap = _uiState.value
            val history = stateSnap.messages
                .dropLast(1)
                .takeLast(8)
                .map { it.isUser to it.text }

            val brainReply = brain.generateReply(
                userMessage = text,
                mode = resolvedMode,
                selectedProviderCode = stateSnap.selectedProviderCode,
                configs = stateSnap.apiConfigs,
                skills = stateSnap.skills,
                sandboxApps = stateSnap.sandboxApps,
                memoryRules = stateSnap.memoryRules,
                history = history
            )

            val approval = brainReply.suggestedAction?.let { act ->
                ActionApproval(
                    title = "${act.mode.name} Action: ${act.target}",
                    details = act.command,
                    mode = act.mode,
                    commandPayload = "${act.target}||${act.command}"
                )
            }

            val assistantMsg = ChatMessage(
                isUser = false,
                text = brainReply.cleanText,
                mode = resolvedMode,
                providerBadge = brainReply.providerUsed,
                artifact = brainReply.artifact,
                actionApproval = approval
            )

            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMsg,
                    telemetry = vault.loadTelemetry(),
                    isThinking = false
                )
            }
        }
    }

    // ---------------- INLINE CHAT APPROVALS ----------------

    fun approveSkillInstall(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val pending = targetMsg.pendingSkill ?: return

        updatePendingSkillStatus(messageId, InstallStatus.INSTALLING)

        viewModelScope.launch {
            delay(750L)
            val newSkill = GitHubSkillEngine.toInstalledSkill(pending)
            val currentSkills = _uiState.value.skills.map { it.copy(isNew = false) }
                .filterNot { it.repoUrl.equals(newSkill.repoUrl, ignoreCase = true) && it.repoUrl.isNotBlank() }
            val updatedSkills = listOf(newSkill) + currentSkills
            vault.saveSkills(updatedSkills)

            updatePendingSkillStatus(messageId, InstallStatus.INSTALLED)

            val confirmMsg = ChatMessage(
                isUser = false,
                text = "✅ **${newSkill.name}** successfully NAX mein install ho gaya!\nAb jab bhi poocho *\"Tumhe kya-kya aata hai?\"*, mujhe ye naya skill bhi yaad rahega. Bolo, isse kya kaam shuru karoon?",
                mode = newSkill.mode,
                providerBadge = "Skill Installed"
            )

            _uiState.update {
                it.copy(
                    skills = updatedSkills,
                    messages = it.messages + confirmMsg,
                    bannerMessage = "Installed skill: ${newSkill.name}"
                )
            }
        }
    }

    fun rejectSkillInstall(messageId: String) {
        updatePendingSkillStatus(messageId, InstallStatus.REJECTED)
    }

    private fun updatePendingSkillStatus(messageId: String, status: InstallStatus) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingSkill != null) {
                        msg.copy(pendingSkill = msg.pendingSkill.copy(status = status))
                    } else {
                        msg
                    }
                }
            )
        }
    }

    fun approveRoutineFlow(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val pending = targetMsg.pendingRoutine ?: return

        val updatedRoutines = listOf(pending.flow) + _uiState.value.routines
        vault.saveRoutines(updatedRoutines)
        RoutineManager.scheduleRoutine(ctx, pending.flow)

        val confirmMsg = ChatMessage(
            isUser = false,
            text = "✅ **${pending.flow.title}** active ho gaya hai aur **Routines tab** mein auto-save ho gaya! Ye ab aapke **${pending.flow.frequency}** routine par khud chalega.",
            mode = _uiState.value.executionMode,
            providerBadge = "Routine Auto-Saved"
        )

        _uiState.update { state ->
            state.copy(
                routines = updatedRoutines,
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingRoutine != null) {
                        msg.copy(pendingRoutine = msg.pendingRoutine.copy(saved = true))
                    } else {
                        msg
                    }
                } + confirmMsg,
                bannerMessage = "Routine saved: ${pending.flow.triggerTime}"
            )
        }
    }

    fun rejectRoutineFlow(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingRoutine != null) {
                        msg.copy(pendingRoutine = msg.pendingRoutine.copy(rejected = true))
                    } else {
                        msg
                    }
                }
            )
        }
    }

    fun approveDeviceAction(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val approval = targetMsg.actionApproval ?: return
        val parts = approval.commandPayload.split("||")
        val target = parts.getOrElse(0) { approval.title }

        val resultText = if (approval.mode == ExecutionMode.SANDBOX) {
            val matchedApp = _uiState.value.sandboxApps.firstOrNull {
                it.name.equals(target, ignoreCase = true) || target.lowercase().contains(it.name.lowercase())
            }
            val urlToLoad = when {
                matchedApp != null -> matchedApp.url
                target.startsWith("http") -> target
                else -> "https://www.google.com/search?q=${target.replace(" ", "+")}"
            }
            _uiState.update {
                it.copy(
                    activeSandboxApp = matchedApp ?: it.activeSandboxApp,
                    activeSandboxUrl = urlToLoad,
                    showSandboxPip = true
                )
            }
            "🟣 Sandbox Phone mein **$target** open ho gaya! Upar Mini-Preview ya Sandbox tab mein live control dekho."
        } else {
            val (ok, info) = LiveTouchService.launchRealAppOrSetting(ctx, target)
            if (ok) "🔴 $info" else "⚠️ $info"
        }

        val followUp = ChatMessage(
            isUser = false,
            text = resultText,
            mode = approval.mode,
            providerBadge = "${approval.mode.name} Executor"
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.actionApproval != null) {
                        msg.copy(actionApproval = msg.actionApproval.copy(status = "APPROVED"))
                    } else {
                        msg
                    }
                } + followUp
            )
        }
    }

    fun rejectDeviceAction(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.actionApproval != null) {
                        msg.copy(actionApproval = msg.actionApproval.copy(status = "DENIED"))
                    } else {
                        msg
                    }
                }
            )
        }
    }

    // ---------------- SANDBOX ("PHONE-IN-PHONE") ----------------

    fun openSandboxApp(app: SandboxApp) {
        _uiState.update {
            it.copy(
                activeSandboxApp = app,
                activeSandboxUrl = app.url
            )
        }
    }

    fun updateSandboxUrl(url: String) {
        _uiState.update { it.copy(activeSandboxUrl = url) }
    }

    fun updateSandboxControls(nodes: List<SandboxControlNode>) {
        _uiState.update { it.copy(sandboxControls = nodes) }
    }

    fun toggleXrayOverlay() {
        _uiState.update { it.copy(xrayEnabled = !it.xrayEnabled) }
    }

    fun toggleTeachMode() {
        _uiState.update { it.copy(teachModeEnabled = !it.teachModeEnabled) }
    }

    fun toggleSandboxPip(show: Boolean) {
        _uiState.update { it.copy(showSandboxPip = show) }
    }

    fun addCustomSandboxApp(name: String, url: String, emoji: String = "📱") {
        val cleanName = name.trim()
        val cleanUrl = url.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        if (cleanName.isBlank() || cleanUrl.length < 10) return
        val newApp = SandboxApp(
            name = cleanName,
            url = cleanUrl,
            emoji = emoji.ifBlank { "📱" },
            description = "Installed inside NAX Sandbox Phone"
        )
        val updated = _uiState.value.sandboxApps + newApp
        vault.saveSandboxApps(updated)
        _uiState.update {
            it.copy(
                sandboxApps = updated,
                activeSandboxApp = newApp,
                activeSandboxUrl = newApp.url,
                bannerMessage = "$cleanName installed inside Sandbox!"
            )
        }
    }

    fun recordTaughtControl(elementDesc: String) {
        val activeApp = _uiState.value.activeSandboxApp ?: return
        val updatedApps = _uiState.value.sandboxApps.map { app ->
            if (app.id == activeApp.id) {
                val merged = (app.learnedControls + elementDesc).distinct().takeLast(12)
                app.copy(learnedControls = merged)
            } else {
                app
            }
        }
        vault.saveSandboxApps(updatedApps)

        val rule = MemoryRule(
            category = "Sandbox:${activeApp.name}",
            content = "User taught control → $elementDesc"
        )
        val updatedRules = (listOf(rule) + _uiState.value.memoryRules).take(30)
        vault.saveMemoryRules(updatedRules)

        _uiState.update {
            it.copy(
                sandboxApps = updatedApps,
                activeSandboxApp = updatedApps.firstOrNull { a -> a.id == activeApp.id },
                memoryRules = updatedRules,
                bannerMessage = "NAX learned: $elementDesc"
            )
        }
    }

    // ---------------- ROUTINES & SKILLS MANAGEMENT ----------------

    fun toggleRoutineEnabled(flowId: String) {
        val updated = _uiState.value.routines.map { flow ->
            if (flow.id == flowId) {
                val toggled = flow.copy(
                    enabled = !flow.enabled,
                    lastRunStatus = if (!flow.enabled) "Active • Scheduled" else "Paused"
                )
                if (toggled.enabled) RoutineManager.scheduleRoutine(ctx, toggled)
                else RoutineManager.cancelRoutine(ctx, toggled.id)
                toggled
            } else {
                flow
            }
        }
        vault.saveRoutines(updated)
        _uiState.update { it.copy(routines = updated) }
    }

    fun deleteRoutine(flowId: String) {
        RoutineManager.cancelRoutine(ctx, flowId)
        val updated = _uiState.value.routines.filterNot { it.id == flowId }
        vault.saveRoutines(updated)
        _uiState.update { it.copy(routines = updated) }
    }

    fun toggleSkillEnabled(skillId: String) {
        val updated = _uiState.value.skills.map {
            if (it.id == skillId) it.copy(enabled = !it.enabled) else it
        }
        vault.saveSkills(updated)
        _uiState.update { it.copy(skills = updated) }
    }

    fun deleteSkill(skillId: String) {
        val updated = _uiState.value.skills.filterNot { it.id == skillId && !it.isCore }
        vault.saveSkills(updated)
        _uiState.update { it.copy(skills = updated) }
    }

    // ---------------- BRAIN & API TESTING LAB ----------------

    fun updateApiConfig(
        provider: ProviderType,
        apiKey: String,
        model: String,
        baseUrl: String,
        enabled: Boolean
    ) {
        val updated = _uiState.value.apiConfigs.map { cfg ->
            if (cfg.provider == provider) {
                cfg.copy(
                    apiKey = apiKey.trim(),
                    model = model.trim().ifBlank { provider.defaultModel },
                    baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                    enabled = enabled
                )
            } else {
                cfg
            }
        }
        vault.saveApiConfigs(updated)
        _uiState.update { it.copy(apiConfigs = updated, bannerMessage = "${provider.displayName} saved") }
    }

    fun testApiProvider(provider: ProviderType) {
        val cfg = _uiState.value.apiConfigs.firstOrNull { it.provider == provider } ?: return
        _uiState.update { it.copy(testingProvider = provider) }

        viewModelScope.launch {
            val result = ApiTestBench.runDiagnostics(cfg)
            val updated = _uiState.value.apiConfigs.map {
                if (it.provider == provider) it.copy(lastTest = result) else it
            }
            vault.saveApiConfigs(updated)
            _uiState.update {
                it.copy(
                    apiConfigs = updated,
                    testingProvider = null,
                    bannerMessage = "${provider.displayName}: ${result.message}"
                )
            }
        }
    }

    fun addMemoryRule(category: String, content: String) {
        if (content.isBlank()) return
        val updated = listOf(MemoryRule(category = category.ifBlank { "General" }, content = content.trim())) +
            _uiState.value.memoryRules
        vault.saveMemoryRules(updated)
        _uiState.update { it.copy(memoryRules = updated) }
    }

    fun deleteMemoryRule(id: String) {
        val updated = _uiState.value.memoryRules.filterNot { it.id == id }
        vault.saveMemoryRules(updated)
        _uiState.update { it.copy(memoryRules = updated) }
    }
}
