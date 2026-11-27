package com.jarvis.os.skills

import com.jarvis.os.data.DiscoveredSkill
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.InstallStatus
import com.jarvis.os.data.PendingSkillInstall
import com.jarvis.os.data.SecurityVerdict
import com.jarvis.os.data.SkillItem
import com.jarvis.os.live.NaxLiveLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GitHubSkillEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // #1 FIND-SKILLS: Search GitHub Ecosystem directly from Chat without needing a URL!
    suspend fun searchSkillsOnGitHub(rawQuery: String): List<DiscoveredSkill> = withContext(Dispatchers.IO) {
        val topic = rawQuery
            .replace(Regex("""\b(find|search|dhoondho|dhundo|skills?|koi|liye|ke|for|agent|claude|github|mujhe|batao)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "ai agent skill" }

        val encoded = URLEncoder.encode("$topic skill OR mcp OR agent", "UTF-8")
        val url = "https://api.github.com/search/repositories?q=$encoded&sort=stars&order=desc&per_page=4"
        val results = mutableListOf<DiscoveredSkill>()

        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "NAX-FindSkills-Engine")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val items = json.optJSONArray("items") ?: return@withContext emptyList()

                for (i in 0 until minOf(items.length(), 3)) {
                    val item = items.getJSONObject(i)
                    val fullName = item.optString("full_name")
                    val repoName = item.optString("name")
                    val htmlUrl = item.optString("html_url")
                    val desc = item.optString("description", "Open-source AI skill repository")
                    val stars = item.optInt("stargazers_count", 0)
                    val branch = item.optString("default_branch", "main")
                    val owner = fullName.substringBefore("/")

                    val doc = fetchRawFile(owner, repoName, branch, "SKILL.md")
                        .ifBlank { fetchRawFile(owner, repoName, branch, "README.md") }
                    val (verdict, auditText) = runThreeLevelAudit(doc, fullName, stars, doc.contains("SKILL"))

                    results.add(
                        DiscoveredSkill(
                            repoFullName = fullName,
                            repoUrl = htmlUrl,
                            title = formatSkillTitle(repoName),
                            description = desc.take(120),
                            stars = stars,
                            verdict = verdict,
                            auditSummary = auditText
                        )
                    )
                }
            }
            NaxLiveLogger.log("🔍", "Find-Skills found ${results.size} audited repos for '$topic'")
        } catch (e: Exception) {
            NaxLiveLogger.log("⚠️", "Find-Skills search failed: ${e.message}")
        }
        results
    }

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
                status = InstallStatus.FAILED,
                verdict = SecurityVerdict.BLOCKED
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

        val skillMd = fetchRawFile(owner, repo, defaultBranch, "SKILL.md")
        val readmeMd = if (skillMd.isBlank()) {
            fetchRawFile(owner, repo, defaultBranch, "README.md")
                .ifBlank { fetchRawFile(owner, repo, defaultBranch, "readme.md") }
        } else {
            ""
        }

        val docContent = skillMd.ifBlank { readmeMd }
        val isNativeSkillMd = skillMd.isNotBlank()
        val (verdict, securityReport) = runThreeLevelAudit(docContent, repoFullName, stars, isNativeSkillMd)

        val readableSummary = buildString {
            if (description.isNotBlank()) append(description)
            else if (docContent.isNotBlank()) append(extractCleanSummary(docContent))
            else append("GitHub skill package from $repoFullName")
            append("\n• Stack: $language")
            if (stars > 0) append(" • ⭐ $stars")
            append(if (isNativeSkillMd) " • Native SKILL.md" else " • Auto-wrapped README")
        }

        val targetMode = if (docContent.lowercase().contains("android") && preferredMode == ExecutionMode.LIVE) {
            ExecutionMode.LIVE
        } else {
            ExecutionMode.SANDBOX
        }

        PendingSkillInstall(
            repoUrl = "https://github.com/$repoFullName",
            repoName = formatSkillTitle(repo),
            summary = readableSummary,
            instructions = extractCondensedInstructions(repo, docContent, description),
            securitySafe = verdict == SecurityVerdict.PASS,
            securityReport = securityReport,
            targetMode = targetMode,
            status = InstallStatus.WAITING_APPROVAL,
            verdict = verdict
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

    // Mind-Created "Shirt" Skill (Self-Made without APK rebuild!)
    fun createMindShirtSkill(
        name: String,
        description: String,
        instructions: String,
        mode: ExecutionMode = ExecutionMode.SANDBOX
    ): SkillItem {
        return SkillItem(
            name = name.trim(),
            description = description.trim(),
            repoUrl = "nax://mind-created-skill",
            mode = mode,
            isCore = false,
            isNew = true,
            commandsHint = "\"$name chala ke dikhao\"",
            instructions = instructions.take(1000),
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

    // Snyk-Style 3-Level Security Audit: PASS / WARN / BLOCKED
    private fun runThreeLevelAudit(
        content: String,
        repoName: String,
        stars: Int,
        nativeSkill: Boolean
    ): Pair<SecurityVerdict, String> {
        val lower = content.lowercase()

        val criticalMalware = listOf(
            "~/.aws/credentials", "wallet.dat", "seed phrase", "keylogger",
            "rm -rf /", "powershell -enc", "exfiltrate", "attacker.com"
        )
        val blockedHits = criticalMalware.filter { lower.contains(it) }
        if (blockedHits.isNotEmpty()) {
            return SecurityVerdict.BLOCKED to "🔴 BLOCKED (Malicious signature: ${blockedHits.joinToString(", ")})"
        }

        val warnPatterns = listOf(
            "curl " to "| bash",
            "curl " to "| sh",
            "wget " to "| sh",
            "eval(" to "",
            "base64 -d" to ""
        )
        val warnHits = warnPatterns.filter { (p1, p2) ->
            lower.contains(p1) && (p2.isEmpty() || lower.contains(p2))
        }.map { it.first.trim() }

        if (warnHits.isNotEmpty() || stars < 5) {
            val reason = if (warnHits.isNotEmpty()) "Contains shell/eval (${warnHits.joinToString()})" else "Low stars ($stars)"
            return SecurityVerdict.WARN to "🟡 WARN: $reason • Run in isolated Sandbox only."
        }

        val tag = if (nativeSkill) "SKILL.md verified" else "README audited"
        return SecurityVerdict.PASS to "🟢 PASS ($tag • ⭐ $stars • No curl/eval/key-stealer found in $repoName)"
    }

    private fun formatSkillTitle(repo: String): String {
        return repo.replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
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
