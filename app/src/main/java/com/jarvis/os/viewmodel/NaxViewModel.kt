package com.jarvis.os.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.brain.ApiTestBench
import com.jarvis.os.brain.NaxBrain
import com.jarvis.os.data.ActionApproval
import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.ArtifactPreview
import com.jarvis.os.data.AttachedMedia
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
import com.jarvis.os.data.SubAgentProgress
import com.jarvis.os.data.TelemetryStats
import com.jarvis.os.data.ThinkingLevel
import com.jarvis.os.data.VaultStorage
import com.jarvis.os.live.CompanionSettings
import com.jarvis.os.live.DeviceSensorBridge
import com.jarvis.os.live.FloatingNaxOverlay
import com.jarvis.os.live.JarvisForegroundService
import com.jarvis.os.live.LiveTouchService
import com.jarvis.os.live.NaxCompanionPrefs
import com.jarvis.os.live.NaxLiveLogger
import com.jarvis.os.live.NaxMood
import com.jarvis.os.live.NaxVoiceEngine
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
    val executionMode: ExecutionMode = ExecutionMode.AUTO,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
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
    val activeSandboxHtml: String? = null,
    val activeSandboxHtmlTitle: String = "",
    val sandboxControls: List<SandboxControlNode> = emptyList(),
    val xrayEnabled: Boolean = false,
    val teachModeEnabled: Boolean = false,
    val showSandboxPip: Boolean = false,
    val memoryRules: List<MemoryRule> = emptyList(),
    val telemetry: TelemetryStats = TelemetryStats(),
    val companionSettings: CompanionSettings = CompanionSettings(),
    val voiceOutputEnabled: Boolean = true,
    val voiceLangCode: String = "hi-IN",
    val pendingAttachment: AttachedMedia? = null,
    val engineRunning: Boolean = false,
    val liveConnected: Boolean = false,
    val overlayPermGranted: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val batteryOptIgnored: Boolean = false,
    val bannerMessage: String = ""
)

class NaxViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val vault = VaultStorage(ctx)
    private val brain = NaxBrain(vault)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<NaxUiState> = _uiState.asStateFlow()

    init {
        NaxVoiceEngine.init(ctx)
        NaxLiveLogger.log("✨", "NAX v2.0 All-in-One Body + Mind + Skills ready")
    }

    private fun loadInitialState(): NaxUiState {
        val apps = vault.loadSandboxApps()
        val welcome = ChatMessage(
            isUser = false,
            text = "Hello Boss! Main **NAX v2.0** hoon — **Auto** mode active hai (aap bolo *private/sandbox* ya *live*, warna main khud smartly decide karunga!).\n\nTry karo:\n• *\"Battery status batao\"* ya *\"Brightness 25% kar do\"*\n• *\"Open Instagram and search bhojpuri song and play 1st video\"*\n• *\"Video editing ke liye koi skill dhoondho\"* (Find-Skills!)\n• *\"Tic Tac Toe game banao\"* → direct Sandbox mein play karo!\n• **`/btw`** side question ya **`Grill me`** loophole check!",
            mode = ExecutionMode.AUTO,
            providerBadge = "NAX v2.0 Core"
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
            companionSettings = NaxCompanionPrefs.load(ctx),
            voiceOutputEnabled = NaxVoiceEngine.isVoiceEnabled(ctx),
            voiceLangCode = NaxVoiceEngine.getSavedLang(ctx),
            engineRunning = JarvisForegroundService.isRunning,
            liveConnected = LiveTouchService.isConnected(),
            overlayPermGranted = DeviceSensorBridge.canDrawOverlays(ctx),
            writeSettingsGranted = DeviceSensorBridge.canModifySystemSettings(ctx),
            batteryOptIgnored = DeviceSensorBridge.isBatteryOptimizationIgnored(ctx)
        )
    }

    fun selectTab(index: Int) {
        refreshPermissionsAndStatus()
        _uiState.update { it.copy(activeTab = index) }
    }

    fun setExecutionMode(mode: ExecutionMode) {
        _uiState.update { it.copy(executionMode = mode) }
        refreshPermissionsAndStatus()
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        _uiState.update { it.copy(thinkingLevel = level, bannerMessage = "Thinking: ${level.label}") }
    }

    fun setSelectedProvider(code: String) {
        vault.saveSelectedProvider(code)
        _uiState.update { it.copy(selectedProviderCode = code) }
    }

    fun setPendingAttachment(media: AttachedMedia?) {
        _uiState.update { it.copy(pendingAttachment = media) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = "") }
    }

    fun refreshPermissionsAndStatus() {
        _uiState.update {
            it.copy(
                liveConnected = LiveTouchService.isConnected(),
                engineRunning = JarvisForegroundService.isRunning,
                overlayPermGranted = DeviceSensorBridge.canDrawOverlays(ctx),
                writeSettingsGranted = DeviceSensorBridge.canModifySystemSettings(ctx),
                batteryOptIgnored = DeviceSensorBridge.isBatteryOptimizationIgnored(ctx)
            )
        }
    }

    fun toggleVoiceOutput() {
        val next = !_uiState.value.voiceOutputEnabled
        NaxVoiceEngine.setVoiceEnabled(ctx, next)
        _uiState.update {
            it.copy(
                voiceOutputEnabled = next,
                bannerMessage = if (next) "🔊 Offline Voice ON" else "🔇 Voice Muted"
            )
        }
    }

    fun setVoiceLanguage(langTag: String) {
        NaxVoiceEngine.setLanguage(ctx, langTag)
        _uiState.update { it.copy(voiceLangCode = langTag, bannerMessage = "Voice language: $langTag") }
    }

    fun updateCompanionSettings(updated: CompanionSettings) {
        NaxCompanionPrefs.save(ctx, updated)
        FloatingNaxOverlay.applyConfig(ctx, updated)
        _uiState.update { it.copy(companionSettings = updated) }
    }

    fun triggerCompanionPreview(isMorning: Boolean) {
        val bat = DeviceSensorBridge.getBatteryPercent(ctx)
        if (isMorning) {
            val msg = "Good Morning Boss! Battery $bat% hai aur ${_uiState.value.routines.count { it.enabled }} routines ready hain!"
            FloatingNaxOverlay.peekStatus(ctx, NaxMood.MORNING, "Good Morning Boss ☀️", msg, 6000L)
            NaxVoiceEngine.speak(ctx, msg)
        } else {
            val msg = "Good Night Boss! Aaj NAX ne ${_uiState.value.telemetry.totalCalls} calls kiye. Battery $bat% hai!"
            FloatingNaxOverlay.peekStatus(ctx, NaxMood.NIGHT, "Good Night Boss 🌙", msg, 6000L)
            NaxVoiceEngine.speak(ctx, msg)
        }
    }

    fun toggleForegroundEngine() {
        if (JarvisForegroundService.isRunning) {
            JarvisForegroundService.stop(ctx)
            _uiState.update { it.copy(engineRunning = false, bannerMessage = "NAX 24/7 Companion Stopped") }
        } else {
            JarvisForegroundService.start(ctx)
            _uiState.update { it.copy(engineRunning = true, bannerMessage = "NAX 24/7 Companion + Floating Orb ON") }
        }
    }

    // ---------------- CHAT-FIRST COMMAND CENTER ----------------

    fun sendMessage(rawText: String) {
        val attachment = _uiState.value.pendingAttachment
        val text = rawText.trim().ifBlank {
            if (attachment != null) "Is ${attachment.type} (${attachment.name}) ko analyze karke batao." else ""
        }
        if (text.isBlank()) return

        val stateSnap = _uiState.value
        val isBtw = brain.isBtwQuestion(text)
        val resolvedMode = brain.resolveSmartMode(text, stateSnap.executionMode)

        val userMsg = ChatMessage(
            isUser = true,
            text = text,
            mode = resolvedMode,
            isBtw = isBtw,
            attachedMedia = attachment
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                pendingAttachment = null,
                isThinking = if (isBtw) it.isThinking else true
            )
        }

        // 1. /btw Side Question runs in parallel WITHOUT interrupting main work!
        if (isBtw) {
            viewModelScope.launch {
                val cleanQ = text.removePrefix("/btw").removePrefix("/BTW").removePrefix("btw").trim()
                val reply = brain.generateReply(
                    userMessage = cleanQ.ifBlank { text },
                    mode = resolvedMode,
                    thinkingLevel = ThinkingLevel.LOW,
                    selectedProviderCode = stateSnap.selectedProviderCode,
                    configs = stateSnap.apiConfigs,
                    skills = stateSnap.skills,
                    sandboxApps = stateSnap.sandboxApps,
                    memoryRules = stateSnap.memoryRules,
                    liveDeviceSnapshot = DeviceSensorBridge.buildLiveDeviceSnapshot(ctx),
                    liveScreenSummary = LiveTouchService.instance?.getScreenSummaryForBrain() ?: "Idle",
                    history = emptyList(),
                    btwMode = true
                )
                val msg = ChatMessage(
                    isUser = false,
                    text = reply.cleanText,
                    mode = resolvedMode,
                    providerBadge = "Side /btw • ${reply.providerUsed}",
                    isBtw = true
                )
                _uiState.update { it.copy(messages = it.messages + msg, telemetry = vault.loadTelemetry()) }
                NaxVoiceEngine.speak(ctx, reply.cleanText)
            }
            return
        }

        viewModelScope.launch {
            FloatingNaxOverlay.peekStatus(ctx, NaxMood.THINKING, "NAX Thinking...", text.take(65), 3500L)

            // 2. Direct GitHub URL -> Security Audit & Inline Install Card
            val githubUrl = brain.extractGitHubRepoUrl(text)
            if (githubUrl != null) {
                val pending = GitHubSkillEngine.inspectRepoForChatApproval(githubUrl, resolvedMode)
                val reply = ChatMessage(
                    isUser = false,
                    text = "Boss, GitHub skill scan + Snyk-style 3-Level Security Audit complete hai. Yahin se approve karke install karo:",
                    mode = resolvedMode,
                    providerBadge = "Skill Security Auditor",
                    pendingSkill = pending
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 3. #1 FIND-SKILLS (Search GitHub Skills without URL!)
            if (brain.isFindSkillsRequest(text)) {
                val discovered = GitHubSkillEngine.searchSkillsOnGitHub(text)
                val replyText = if (discovered.isNotEmpty()) {
                    "Boss, maine GitHub skills ecosystem search karke top **${discovered.size} audited skills** nikaale hain (har ek par Security Audit verdict ke saath):"
                } else {
                    "Abhi koi matching GitHub skill nahi mila — thoda alag keyword try karo ya seedha GitHub URL paste kar do!"
                }
                val reply = ChatMessage(
                    isUser = false,
                    text = replyText,
                    mode = ExecutionMode.SANDBOX,
                    providerBadge = "Find-Skills Engine",
                    discoveredSkills = discovered
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                NaxVoiceEngine.speak(ctx, replyText)
                return@launch
            }

            // 4. Device Video Folder Scanner ("Check your video folder")
            if (brain.isVideoFolderRequest(text)) {
                val videos = DeviceSensorBridge.scanDeviceVideos(ctx, 8)
                val replyText = if (videos.isNotEmpty()) {
                    "Boss, aapke phone ke Video Folder mein **${videos.size} latest videos** mile hain! Kisi par bhi tap karke metadata/title banwao:"
                } else {
                    "Video folder mein koi video nahi mila (ya Media permission abhi allow nahi hui — Brain tab ya Settings se Media permission allow karein)."
                }
                val reply = ChatMessage(
                    isUser = false,
                    text = replyText,
                    mode = ExecutionMode.SANDBOX,
                    providerBadge = "Video Folder Scanner",
                    deviceVideos = videos
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 5. Playable HTML Preview Trigger ("preview kar ke dikhao" / "nahibdikha")
            if (brain.isPreviewHtmlRequest(text)) {
                val latestHtml = _uiState.value.messages.asReversed()
                    .mapNotNull { it.artifact }
                    .firstOrNull { it.type.equals("html", ignoreCase = true) || it.content.lowercase().contains("<html") }
                if (latestHtml != null) {
                    previewHtmlInSandbox(latestHtml)
                    val reply = ChatMessage(
                        isUser = false,
                        text = "🎮 Done Boss! **${latestHtml.title}** seedha aapke **Sandbox Phone** mein LIVE open kar diya hai—abhi khelo aur test karo!",
                        mode = ExecutionMode.SANDBOX,
                        providerBadge = "Sandbox HTML Runner"
                    )
                    _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                    NaxVoiceEngine.speak(ctx, "Boss, ${latestHtml.title} sandbox phone mein live open ho gaya hai.")
                    return@launch
                }
            }

            // 6. Self-Awareness ("Tumhe kya-kya aata hai?")
            if (brain.isAskingCapabilities(text)) {
                val count = _uiState.value.skills.count { it.enabled }
                val newCount = _uiState.value.skills.count { it.isNew && it.enabled }
                val intro = if (newCount > 0) {
                    "Boss, ab mujhe total **$count skills** aate hain (jismein **$newCount naye seekhe hue skills** bhi shamil hain!). Neeche dekho:"
                } else {
                    "Boss, mujhe ye **$count active skills** aate hain — aur aap bolo *'koi skill dhoondho'* ya GitHub link do, naye skills turant seekh lunga:"
                }
                val reply = ChatMessage(
                    isUser = false,
                    text = intro,
                    mode = resolvedMode,
                    providerBadge = "NAX Self-Awareness",
                    showCapabilities = true
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                NaxVoiceEngine.speak(ctx, intro)
                return@launch
            }

            // 7. Strict Routine / Always-On Flow Creation (Fixed Instagram/Kam bug!)
            if (brain.isRoutineRequest(text)) {
                val flow = RoutineManager.parseFlowFromChat(text, resolvedMode)
                val reply = ChatMessage(
                    isUser = false,
                    text = "Boss, aapka **${flow.frequency}** flow ready hai! Approve karte hi **Routines tab** mein auto-save ho jayega:",
                    mode = resolvedMode,
                    providerBadge = "NAX Routine Builder",
                    pendingRoutine = PendingRoutineApproval(flow = flow)
                )
                _uiState.update { it.copy(messages = it.messages + reply, isThinking = false) }
                return@launch
            }

            // 8. Instant Real Hardware & Multi-Step Live Execution (Zero fake answers!)
            val directHardware = brain.detectDirectHardwareAction(text)
            if (directHardware != null && resolvedMode == ExecutionMode.LIVE) {
                executeSuggestedActionImmediately(directHardware)
                _uiState.update { it.copy(isThinking = false) }
                return@launch
            }

            // 9. XHigh / Parallel Sub-Agents Orchestrator Progress Card
            val currentThinking = _uiState.value.thinkingLevel
            val isGrill = brain.isGrillMeRequest(text)
            var subAgentMsgId: String? = null

            if (currentThinking == ThinkingLevel.XHIGH && !isGrill) {
                val initialAgents = listOf(
                    SubAgentProgress("Agent 1", "Requirement Planner", 0.25f, "Planning..."),
                    SubAgentProgress("Agent 2", "Code & UI Builder", 0.15f, "Drafting..."),
                    SubAgentProgress("Agent 3", "Security & Loopholes", 0.20f, "Checking..."),
                    SubAgentProgress("Agent 4", "QA Verifier", 0.10f, "Standing by...")
                )
                val progressMsg = ChatMessage(
                    isUser = false,
                    text = "⚡ **Orchestrator Mode (XHigh):** 4 Parallel Sub-Agents ek saath kaam kar rahe hain...",
                    mode = resolvedMode,
                    providerBadge = "4 Parallel Sub-Agents",
                    subAgents = initialAgents
                )
                subAgentMsgId = progressMsg.id
                _uiState.update { it.copy(messages = it.messages + progressMsg) }
                launch {
                    delay(500L)
                    updateSubAgents(progressMsg.id, 0.72f, "Executing...")
                }
            }

            // 10. Full NAX Mind (API) Call with 100% Real Hardware Sensors + Vision Support
            val snapNow = _uiState.value
            val history = snapNow.messages
                .filterNot { it.id == subAgentMsgId }
                .dropLast(1)
                .takeLast(8)
                .map { it.isUser to it.text }

            val promptWithAttachmentNote = if (attachment != null && attachment.type != "photo") {
                "$text\n[User attached ${attachment.type} file: ${attachment.name}]"
            } else {
                text
            }

            val brainReply = brain.generateReply(
                userMessage = promptWithAttachmentNote,
                mode = resolvedMode,
                thinkingLevel = currentThinking,
                selectedProviderCode = snapNow.selectedProviderCode,
                configs = snapNow.apiConfigs,
                skills = snapNow.skills,
                sandboxApps = snapNow.sandboxApps,
                memoryRules = snapNow.memoryRules,
                liveDeviceSnapshot = DeviceSensorBridge.buildLiveDeviceSnapshot(ctx),
                liveScreenSummary = LiveTouchService.instance?.getScreenSummaryForBrain() ?: "NAX Active",
                history = history,
                grillMeMode = isGrill,
                imageBase64 = if (attachment?.type == "photo") attachment.base64Data else null,
                imageMimeType = attachment?.mimeType?.ifBlank { "image/jpeg" } ?: "image/jpeg"
            )

            subAgentMsgId?.let { updateSubAgents(it, 1.0f, "Done ✓") }

            // Handle Mind-Created "Shirt" Skill (Self-Upgrade without APK rebuild!)
            brainReply.selfCreatedSkill?.let { spec ->
                val created = GitHubSkillEngine.createMindShirtSkill(
                    name = spec.name,
                    description = spec.description,
                    instructions = spec.instructions,
                    mode = spec.mode
                )
                val updatedSkills = listOf(created) + _uiState.value.skills.map { it.copy(isNew = false) }
                vault.saveSkills(updatedSkills)
                _uiState.update { it.copy(skills = updatedSkills, bannerMessage = "🆕 NAX created & wore new skill: ${created.name}") }
                NaxLiveLogger.log("👕", "Mind created new Shirt Skill: ${created.name}")
            }

            // If AI suggested immediate HTML preview
            if (brainReply.suggestedAction?.type == "PREVIEW_HTML") {
                val art = brainReply.artifact ?: _uiState.value.messages.asReversed().mapNotNull { it.artifact }.firstOrNull()
                if (art != null) previewHtmlInSandbox(art)
            }

            val approval = brainReply.suggestedAction
                ?.takeIf { it.type != "PREVIEW_HTML" }
                ?.let { act ->
                    ActionApproval(
                        title = "${act.mode.name} • ${act.type}: ${act.target}",
                        details = act.command,
                        mode = act.mode,
                        commandPayload = "${act.type}||${act.target}||${act.command}"
                    )
                }

            val badge = if (isGrill) "🔥 Grill-Me • ${brainReply.providerUsed}" else brainReply.providerUsed
            val assistantMsg = ChatMessage(
                isUser = false,
                text = brainReply.cleanText,
                mode = resolvedMode,
                providerBadge = badge,
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

            FloatingNaxOverlay.peekStatus(ctx, NaxMood.HAPPY, "NAX Replied", brainReply.cleanText.replace("**", "").take(90), 4000L)
            NaxVoiceEngine.speak(ctx, brainReply.cleanText)
        }
    }

    private fun updateSubAgents(msgId: String, progress: Float, status: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id == msgId) {
                        m.copy(subAgents = m.subAgents.map { it.copy(progress = progress, status = status) })
                    } else m
                }
            )
        }
    }

    // ---------------- REAL HARDWARE & MULTI-STEP LIVE ENGINE ----------------

    private suspend fun executeSuggestedActionImmediately(act: com.jarvis.os.brain.SuggestedDeviceAction) {
        when (act.type) {
            "BRIGHTNESS" -> {
                val pct = act.target.toIntOrNull() ?: 25
                val (ok, msg) = DeviceSensorBridge.setRealBrightness(ctx, pct)
                FloatingNaxOverlay.peekStatus(ctx, if (ok) NaxMood.HAPPY else NaxMood.WARNING, "Brightness ($pct%)", msg.replace("**", ""), 4500L)
                postSystemReply(msg, ExecutionMode.LIVE, "Hardware Control")
            }
            "VOLUME" -> {
                val pct = act.target.toIntOrNull() ?: 50
                val (ok, msg) = DeviceSensorBridge.setRealMediaVolume(ctx, pct)
                FloatingNaxOverlay.peekStatus(ctx, if (ok) NaxMood.HAPPY else NaxMood.WARNING, "Volume ($pct%)", msg.replace("**", ""), 4500L)
                postSystemReply(msg, ExecutionMode.LIVE, "Hardware Control")
            }
            "SCREENSHOT" -> {
                val svc = LiveTouchService.instance
                val (ok, msg) = if (svc != null) {
                    delay(400L)
                    svc.takeScreenshotAction()
                } else {
                    LiveTouchService.openAccessibilitySettings(ctx)
                    false to "Screenshot ke liye pehle NAX Accessibility Service ON karein (Settings khol diya hai)."
                }
                FloatingNaxOverlay.peekStatus(ctx, if (ok) NaxMood.HAPPY else NaxMood.WARNING, "Screenshot", msg, 4000L)
                postSystemReply(msg, ExecutionMode.LIVE, "Live Capture")
            }
            "MULTI_STEP" -> {
                runMultiStepLiveAutomation(act.target, act.command)
            }
            else -> {
                val (_, info) = LiveTouchService.launchRealAppOrSetting(ctx, act.target)
                postSystemReply("🔴 $info", ExecutionMode.LIVE, "Live Executor")
            }
        }
    }

    private suspend fun runMultiStepLiveAutomation(appName: String, fullCommand: String) {
        LiveTouchService.resetStopFlag()
        val stepsLog = mutableListOf<String>()

        FloatingNaxOverlay.peekStatus(ctx, NaxMood.WORKING, "Live Loop: $appName", "Step 1: Launching $appName...", 5000L)
        val (opened, openMsg) = LiveTouchService.launchRealAppOrSetting(ctx, appName)
        stepsLog.add("1. $openMsg")

        if (!opened) {
            postSystemReply("⚠️ $openMsg", ExecutionMode.LIVE, "Live Multi-Step")
            return
        }

        val svc = LiveTouchService.instance
        if (svc == null) {
            postSystemReply(
                "📱 **$appName** khol diya hai! Aage automatic Search & Play ke liye **Brain tab → Enable Accessibility Service** ON karein.",
                ExecutionMode.LIVE,
                "Live Multi-Step"
            )
            return
        }

        val searchQuery = extractSearchQuery(fullCommand, appName)
        val wantsPlay = listOf("play", "1st", "first", "video", "reel", "song", "chalao").any { fullCommand.lowercase().contains(it) }

        if (searchQuery.isNotBlank()) {
            delay(2600L)
            if (LiveTouchService.stopRequested) return

            FloatingNaxOverlay.peekStatus(ctx, NaxMood.WORKING, "Live Loop: $appName", "Step 2: Tapping Search...", 4000L)
            val clickedSearch = svc.findAndClickByHints(
                listOf("search and explore", "search", "explore", "search_tab", "action_bar_search_edit_text", "menu_search")
            )
            stepsLog.add(if (clickedSearch) "2. Opened Search bar ✓" else "2. Search icon scan attempted")

            delay(1500L)
            if (LiveTouchService.stopRequested) return

            // Instagram needs 2nd tap on top search box after opening Explore tab
            if (appName.equals("Instagram", ignoreCase = true)) {
                svc.findAndClickByHints(listOf("search", "action_bar_search_edit_text", "search_edit_text"))
                delay(1000L)
            }

            FloatingNaxOverlay.peekStatus(ctx, NaxMood.WORKING, "Live Loop: $appName", "Step 3: Typing \"$searchQuery\"...", 4000L)
            val typed = svc.inputTextIntoSearchOrEditable(searchQuery)
            stepsLog.add(if (typed) "3. Typed \"$searchQuery\" ✓" else "3. Search box focus retry")

            delay(1100L)
            if (LiveTouchService.stopRequested) return
            svc.tapBottomRightKeyboardSearch()

            if (wantsPlay) {
                delay(2400L)
                if (LiveTouchService.stopRequested) return
                FloatingNaxOverlay.peekStatus(ctx, NaxMood.WORKING, "Live Loop: $appName", "Step 4: Playing 1st video...", 4000L)
                svc.tapCenterAreaForFirstMediaResult()
                stepsLog.add("4. Tapped 1st result/video ✓")
            }
        }

        val summary = "✅ **Live Multi-Step Complete ($appName):**\n" + stepsLog.joinToString("\n")
        FloatingNaxOverlay.peekStatus(ctx, NaxMood.HAPPY, "Task Done ✓", "$appName: $searchQuery", 5000L)
        postSystemReply(summary, ExecutionMode.LIVE, "Live Autonomous Loop")
    }

    private fun extractSearchQuery(command: String, appName: String): String {
        val lower = command.lowercase()
        val afterSearch = Regex("""(?:search|dhundho|dhundo|find)\s+(.+)""").find(lower)?.groupValues?.get(1) ?: return ""
        return afterSearch
            .replace(Regex("""\b(and|aur|then|phir|play|chalao|1st|first|video|reel|karein|karo|do)\b"""), "")
            .replace(appName.lowercase(), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun postSystemReply(text: String, mode: ExecutionMode, badge: String) {
        val msg = ChatMessage(isUser = false, text = text, mode = mode, providerBadge = badge)
        _uiState.update { it.copy(messages = it.messages + msg) }
        NaxVoiceEngine.speak(ctx, text)
    }

    // ---------------- INLINE CHAT APPROVALS ----------------

    fun inspectAndInstallDiscoveredSkill(repoUrl: String) {
        sendMessage("Install skill $repoUrl")
    }

    fun approveSkillInstall(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val pending = targetMsg.pendingSkill ?: return

        updatePendingSkillStatus(messageId, InstallStatus.INSTALLING)

        viewModelScope.launch {
            delay(700L)
            val newSkill = GitHubSkillEngine.toInstalledSkill(pending)
            val currentSkills = _uiState.value.skills.map { it.copy(isNew = false) }
                .filterNot { it.repoUrl.equals(newSkill.repoUrl, ignoreCase = true) && it.repoUrl.isNotBlank() }
            val updatedSkills = listOf(newSkill) + currentSkills
            vault.saveSkills(updatedSkills)

            updatePendingSkillStatus(messageId, InstallStatus.INSTALLED)
            NaxLiveLogger.log("👕", "Installed GitHub Skill: ${newSkill.name}")

            val confirmText = "✅ **${newSkill.name}** NAX mein install ho gaya! Ab poocho *\"Tumhe kya-kya aata hai?\"* ya seedha kaam bolo!"
            val confirmMsg = ChatMessage(isUser = false, text = confirmText, mode = newSkill.mode, providerBadge = "Skill Installed")

            _uiState.update {
                it.copy(
                    skills = updatedSkills,
                    messages = it.messages + confirmMsg,
                    bannerMessage = "Installed skill: ${newSkill.name}"
                )
            }
            NaxVoiceEngine.speak(ctx, "${newSkill.name} skill install ho gaya hai boss.")
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
                    } else msg
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
            text = "✅ **${pending.flow.title}** active ho gaya hai aur **Routines tab** mein auto-save ho gaya!",
            mode = _uiState.value.executionMode,
            providerBadge = "Routine Auto-Saved"
        )

        _uiState.update { state ->
            state.copy(
                routines = updatedRoutines,
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingRoutine != null) {
                        msg.copy(pendingRoutine = msg.pendingRoutine.copy(saved = true))
                    } else msg
                } + confirmMsg,
                bannerMessage = "Saved: ${pending.flow.triggerTime}"
            )
        }
    }

    fun rejectRoutineFlow(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingRoutine != null) {
                        msg.copy(pendingRoutine = msg.pendingRoutine.copy(rejected = true))
                    } else msg
                }
            )
        }
    }

    fun approveDeviceAction(messageId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val approval = targetMsg.actionApproval ?: return
        val parts = approval.commandPayload.split("||")
        val type = parts.getOrElse(0) { "OPEN" }
        val target = parts.getOrElse(1) { approval.title }
        val cmd = parts.getOrElse(2) { approval.details }

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.actionApproval != null) {
                        msg.copy(actionApproval = msg.actionApproval.copy(status = "APPROVED"))
                    } else msg
                }
            )
        }

        if (approval.mode == ExecutionMode.SANDBOX) {
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
                    activeSandboxHtml = null,
                    activeSandboxHtmlTitle = "",
                    activeSandboxApp = matchedApp ?: it.activeSandboxApp,
                    activeSandboxUrl = urlToLoad,
                    showSandboxPip = true
                )
            }
            postSystemReply("🟣 Sandbox Phone mein **$target** open ho gaya! Upar Mini-Preview ya Sandbox tab dekho.", ExecutionMode.SANDBOX, "Sandbox Executor")
        } else {
            viewModelScope.launch {
                executeSuggestedActionImmediately(
                    com.jarvis.os.brain.SuggestedDeviceAction(ExecutionMode.LIVE, type, target, cmd)
                )
            }
        }
    }

    fun rejectDeviceAction(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId && msg.actionApproval != null) {
                        msg.copy(actionApproval = msg.actionApproval.copy(status = "DENIED"))
                    } else msg
                }
            )
        }
    }

    // ---------------- SANDBOX ("PHONE-IN-PHONE" + PLAYABLE HTML) ----------------

    fun previewHtmlInSandbox(artifact: ArtifactPreview) {
        _uiState.update {
            it.copy(
                activeSandboxHtml = artifact.content,
                activeSandboxHtmlTitle = artifact.title,
                showSandboxPip = true,
                activeTab = 1,
                bannerMessage = "🎮 Playing ${artifact.title} in Sandbox!"
            )
        }
        NaxLiveLogger.log("🎮", "Loaded live playable HTML artifact in Sandbox: ${artifact.title}")
    }

    fun openSandboxApp(app: SandboxApp) {
        _uiState.update {
            it.copy(
                activeSandboxHtml = null,
                activeSandboxHtmlTitle = "",
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
        val newApp = SandboxApp(name = cleanName, url = cleanUrl, emoji = emoji.ifBlank { "📱" }, description = "Installed inside NAX Sandbox")
        val updated = _uiState.value.sandboxApps + newApp
        vault.saveSandboxApps(updated)
        _uiState.update {
            it.copy(
                sandboxApps = updated,
                activeSandboxHtml = null,
                activeSandboxHtmlTitle = "",
                activeSandboxApp = newApp,
                activeSandboxUrl = newApp.url,
                bannerMessage = "$cleanName installed in Sandbox!"
            )
        }
    }

    fun recordTaughtControl(elementDesc: String) {
        val activeApp = _uiState.value.activeSandboxApp ?: return
        val updatedApps = _uiState.value.sandboxApps.map { app ->
            if (app.id == activeApp.id) {
                app.copy(learnedControls = (app.learnedControls + elementDesc).distinct().takeLast(12))
            } else app
        }
        vault.saveSandboxApps(updatedApps)

        val rule = MemoryRule(category = "Sandbox:${activeApp.name}", content = "User taught → $elementDesc")
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

    // ---------------- ROUTINES, SKILLS & BRAIN LAB ----------------

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
            } else flow
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
        val updated = _uiState.value.skills.map { if (it.id == skillId) it.copy(enabled = !it.enabled) else it }
        vault.saveSkills(updated)
        _uiState.update { it.copy(skills = updated) }
    }

    fun deleteSkill(skillId: String) {
        val updated = _uiState.value.skills.filterNot { it.id == skillId && !it.isCore }
        vault.saveSkills(updated)
        _uiState.update { it.copy(skills = updated) }
    }

    fun updateApiConfig(provider: ProviderType, apiKey: String, model: String, baseUrl: String, enabled: Boolean) {
        val updated = _uiState.value.apiConfigs.map { cfg ->
            if (cfg.provider == provider) {
                cfg.copy(
                    apiKey = apiKey.trim(),
                    model = model.trim().ifBlank { provider.defaultModel },
                    baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
                    enabled = enabled
                )
            } else cfg
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
