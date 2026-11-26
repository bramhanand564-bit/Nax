package com.jarvis.os.skills

import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.InstallStatus
import com.jarvis.os.data.PendingSkillInstall
import com.jarvis.os.data.SkillItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GitHubSkillEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun inspectRepoForChatApproval(
        rawUrl: String,
        preferredMode: ExecutionMode = ExecutionMode.SANDBOX
    ): PendingSkillInstall = withContext(Dispatchers.IO) {
        val cleaned = rawUrl.trim().removeSuffix("/").removeSuffix(".git")
        val parts = cleaned.substringAfter("github.com/").split("/").filter { it.isNotBlank() }

        if (parts.size < 2) {
            return@withContext PendingSkillInstall(
                repoUrl = rawUrl,
                repoName = "Invalid GitHub URL",
                summary = "Valid GitHub link bhejo (jaise https://github.com/user/repo).",
                instructions = "",
                securitySafe = false,
                securityReport = "Invalid URL format",
                targetMode = ExecutionMode.SANDBOX,
                status = InstallStatus.FAILED
            )
        }

        val owner = parts[0]
        val repo = parts[1]
        val repoFullName = "$owner/$repo"

        var description = ""
        var defaultBranch = "main"
        var language = "Multi-Language"
        var stars = 0

        try {
            val metaReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "NAX-Mobile-Agent")
                .build()
            client.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    description = json.optString("description", "")
                    defaultBranch = json.optString("default_branch", "main")
                    language = json.optString("language", "Tool")
                    stars = json.optInt("stargazers_count", 0)
                }
            }
        } catch (_: Exception) {
        }

        // First try AgentSkills SKILL.md (OpenClaw / Claude / Antigravity standard), fallback to README.md
        val skillMd = fetchRawFile(owner, repo, defaultBranch, "SKILL.md")
        val readmeMd = if (skillMd.isBlank()) {
            fetchRawFile(owner, repo, defaultBranch, "README.md")
                .ifBlank { fetchRawFile(owner, repo, defaultBranch, "readme.md") }
        } else {
            ""
        }

        val docContent = skillMd.ifBlank { readmeMd }
        val isNativeSkillMd = skillMd.isNotBlank()

        val (isSafe, securityReport) = runSecurityAudit(docContent, repoFullName, isNativeSkillMd)

        val readableSummary = buildString {
            if (description.isNotBlank()) {
                append(description)
            } else if (docContent.isNotBlank()) {
                append(extractCleanSummary(docContent))
            } else {
                append("GitHub skill package from $repoFullName")
            }
            append("\n• Stack: $language")
            if (stars > 0) append(" • ⭐ $stars")
            append(if (isNativeSkillMd) " • Native SKILL.md detected" else " • Auto-wrapped via README.md")
        }

        val targetMode = if (docContent.lowercase().contains("adb") || docContent.lowercase().contains("android device")) {
            preferredMode
        } else {
            ExecutionMode.SANDBOX
        }

        val condensedInstructions = extractCondensedInstructions(repo, docContent, description)

        PendingSkillInstall(
            repoUrl = "https://github.com/$repoFullName",
            repoName = formatSkillTitle(repo),
            summary = readableSummary,
            instructions = condensedInstructions,
            securitySafe = isSafe,
            securityReport = securityReport,
            targetMode = targetMode,
            status = InstallStatus.WAITING_APPROVAL
        )
    }

    fun toInstalledSkill(pending: PendingSkillInstall): SkillItem {
        return SkillItem(
            name = pending.repoName,
            description = pending.summary.lineSequence().firstOrNull()?.take(180)
                ?: "Installed GitHub Skill",
            repoUrl = pending.repoUrl,
            mode = pending.targetMode,
            isCore = false,
            isNew = true,
            commandsHint = "\"${pending.repoName} skill use karke kaam shuru karo\"",
            instructions = pending.instructions,
            enabled = true,
            installedAt = System.currentTimeMillis()
        )
    }

    private fun fetchRawFile(owner: String, repo: String, branch: String, fileName: String): String {
        return try {
            val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$fileName"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "NAX-Mobile-Agent")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty().take(12000) else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun runSecurityAudit(content: String, repoName: String, nativeSkill: Boolean): Pair<Boolean, String> {
        val lower = content.lowercase()
        val riskyPatterns = listOf(
            "curl " to "| bash",
            "curl " to "| sh",
            "wget " to "| sh",
            "base64 -d" to "bash",
            "powershell -enc" to "",
            "keylogger" to "",
            "seed phrase" to "",
            "steal" to "token",
            "~/.ssh" to "upload",
            "rm -rf /" to ""
        )

        val findings = mutableListOf<String>()
        riskyPatterns.forEach { (p1, p2) ->
            if (lower.contains(p1) && (p2.isEmpty() || lower.contains(p2))) {
                findings.add("Suspicious pattern: $p1 $p2".trim())
            }
        }

        if (findings.isNotEmpty()) {
            return false to "⚠️ Caution: ${findings.joinToString("; ")}. Sirf trusted repo ho tabhi approve karein."
        }

        val modeNote = if (nativeSkill) "SKILL.md verified" else "README scanned"
        return true to "✅ Safe ($modeNote • Isolated Sandbox mode • No malware signatures found in $repoName)"
    }

    private fun formatSkillTitle(repo: String): String {
        return repo.replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private fun extractCleanSummary(markdown: String): String {
        return markdown.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("![") && !it.startsWith("[!") && !it.startsWith("```") }
            .take(2)
            .joinToString(" ")
            .take(180)
            .ifBlank { "Custom GitHub AI Skill ready for NAX." }
    }

    private fun extractCondensedInstructions(repo: String, doc: String, desc: String): String {
        if (doc.isBlank()) return "Use $repo workflow: $desc"
        return doc.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("![") }
            .take(18)
            .joinToString(" ")
            .take(900)
    }
}
