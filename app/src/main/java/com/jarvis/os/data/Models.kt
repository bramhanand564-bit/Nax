package com.jarvis.os.data

import java.util.UUID

enum class ExecutionMode {
    SANDBOX,
    LIVE
}

enum class ProviderType(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String
) {
    GEMINI(
        displayName = "Gemini API",
        defaultModel = "gemini-3.8-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com"
    ),
    GROQ(
        displayName = "Groq API",
        defaultModel = "llama-3.3-70b-versatile",
        defaultBaseUrl = "https://api.groq.com/openai/v1"
    ),
    CLAUDE(
        displayName = "Claude (Anthropic)",
        defaultModel = "claude-sonnet-5-5",
        defaultBaseUrl = "https://api.anthropic.com/v1"
    ),
    LOCAL(
        displayName = "Local Model",
        defaultModel = "llama3.2",
        defaultBaseUrl = "http://127.0.0.1:11434/v1"
    ),
    CUSTOM(
        displayName = "Custom API",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://openrouter.ai/api/v1"
    )
}

data class ApiTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val visionReady: Boolean,
    val jsonAgentReady: Boolean,
    val message: String,
    val testedAt: Long = System.currentTimeMillis()
)

data class ApiConfig(
    val provider: ProviderType,
    val apiKey: String = "",
    val model: String = provider.defaultModel,
    val baseUrl: String = provider.defaultBaseUrl,
    val enabled: Boolean = true,
    val lastTest: ApiTestResult? = null
)

data class SkillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val repoUrl: String = "",
    val mode: ExecutionMode = ExecutionMode.SANDBOX,
    val isCore: Boolean = false,
    val isNew: Boolean = false,
    val commandsHint: String = "",
    val instructions: String = "",
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)

enum class InstallStatus {
    SCANNING,
    WAITING_APPROVAL,
    INSTALLING,
    INSTALLED,
    REJECTED,
    FAILED
}

data class PendingSkillInstall(
    val id: String = UUID.randomUUID().toString(),
    val repoUrl: String,
    val repoName: String,
    val summary: String,
    val instructions: String,
    val securitySafe: Boolean,
    val securityReport: String,
    val targetMode: ExecutionMode = ExecutionMode.SANDBOX,
    val status: InstallStatus = InstallStatus.WAITING_APPROVAL
)

data class RoutineStep(
    val mode: ExecutionMode,
    val label: String,
    val command: String
)

data class RoutineFlow(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val triggerTime: String,
    val frequency: String = "Daily",
    val steps: List<RoutineStep>,
    val enabled: Boolean = true,
    val lastRunStatus: String = "Auto-saved • Active",
    val createdAt: Long = System.currentTimeMillis()
)

data class PendingRoutineApproval(
    val flow: RoutineFlow,
    val saved: Boolean = false,
    val rejected: Boolean = false
)

data class ArtifactPreview(
    val title: String,
    val type: String,
    val content: String
)

data class ActionApproval(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val details: String,
    val mode: ExecutionMode,
    val commandPayload: String,
    val status: String = "PENDING"
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val mode: ExecutionMode,
    val providerBadge: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val pendingSkill: PendingSkillInstall? = null,
    val pendingRoutine: PendingRoutineApproval? = null,
    val artifact: ArtifactPreview? = null,
    val actionApproval: ActionApproval? = null,
    val showCapabilities: Boolean = false
)

data class SandboxApp(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val emoji: String,
    val description: String,
    val learnedControls: List<String> = emptyList()
)

data class SandboxControlNode(
    val index: Int,
    val tag: String,
    val text: String,
    val selector: String
)

data class MemoryRule(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class TelemetryStats(
    val totalCalls: Int = 0,
    val totalTokens: Long = 0,
    val lastLatencyMs: Long = 0,
    val activeProvider: String = "Auto"
)
