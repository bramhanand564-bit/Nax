package com.jarvis.os.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.os.data.ActionApproval
import com.jarvis.os.data.ArtifactPreview
import com.jarvis.os.data.DeviceVideoItem
import com.jarvis.os.data.DiscoveredSkill
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.InstallStatus
import com.jarvis.os.data.PendingRoutineApproval
import com.jarvis.os.data.PendingSkillInstall
import com.jarvis.os.data.SecurityVerdict
import com.jarvis.os.data.SkillItem
import com.jarvis.os.data.SubAgentProgress
import com.jarvis.os.ui.theme.NaxAmber
import com.jarvis.os.ui.theme.NaxBorder
import com.jarvis.os.ui.theme.NaxCard
import com.jarvis.os.ui.theme.NaxCardElevated
import com.jarvis.os.ui.theme.NaxCyan
import com.jarvis.os.ui.theme.NaxGreen
import com.jarvis.os.ui.theme.NaxLiveRed
import com.jarvis.os.ui.theme.NaxSandboxPurple
import com.jarvis.os.ui.theme.NaxTextPrimary
import com.jarvis.os.ui.theme.NaxTextSecondary

@Composable
fun GitHubSkillInstallCard(
    pending: PendingSkillInstall,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val accent = when (pending.verdict) {
        SecurityVerdict.PASS -> NaxGreen
        SecurityVerdict.WARN -> NaxAmber
        SecurityVerdict.BLOCKED -> NaxLiveRed
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (pending.verdict) {
                        SecurityVerdict.PASS -> Icons.Rounded.Security
                        SecurityVerdict.WARN -> Icons.Rounded.Warning
                        SecurityVerdict.BLOCKED -> Icons.Rounded.Block
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(pending.repoName, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                VerdictBadge(pending.verdict)
            }

            Spacer(Modifier.height(6.dp))
            Text(pending.summary, color = NaxTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Text(pending.securityReport, color = accent, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.height(10.dp))
            when (pending.status) {
                InstallStatus.WAITING_APPROVAL -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApprove,
                            enabled = pending.verdict != SecurityVerdict.BLOCKED,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NaxSandboxPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (pending.verdict == SecurityVerdict.BLOCKED) "Blocked by Audit" else "Approve & Install",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = onReject,
                            border = BorderStroke(1.dp, NaxBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = NaxTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                InstallStatus.INSTALLING -> {
                    Text("Installing skill into NAX...", color = NaxCyan, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)), color = NaxSandboxPurple)
                }
                InstallStatus.INSTALLED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NaxGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Installed & Active in NAX Brain", color = NaxGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                InstallStatus.REJECTED -> Text("Installation cancelled.", color = NaxTextSecondary, fontSize = 12.sp)
                else -> Text("Could not inspect repository.", color = NaxLiveRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FindSkillsResultCard(
    discovered: List<DiscoveredSkill>,
    onInstallSkill: (String) -> Unit
) {
    if (discovered.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, NaxCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Find-Skills • Audited Results", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            discovered.forEachIndexed { idx, item ->
                if (idx > 0) HorizontalDivider(color = NaxBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = NaxAmber, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${item.stars}", color = NaxAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        VerdictBadge(item.verdict)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(item.description, color = NaxTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(item.auditSummary, color = NaxCyan, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { onInstallSkill(item.repoUrl) },
                        enabled = item.verdict != SecurityVerdict.BLOCKED,
                        colors = ButtonDefaults.buttonColors(containerColor = NaxSandboxPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Inspect & Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ParallelSubAgentsCard(agents: List<SubAgentProgress>) {
    if (agents.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GroupWork, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dynamic Parallel Sub-Agents (${agents.size})", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            agents.forEach { a ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${a.name} • ${a.role}", color = NaxTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(a.status, color = if (a.progress >= 1f) NaxGreen else NaxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { a.progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (a.progress >= 1f) NaxGreen else NaxSandboxPurple,
                        trackColor = NaxCard
                    )
                }
            }
        }
    }
}

@Composable
fun VideoFolderCard(
    videos: List<DeviceVideoItem>,
    onSelectVideoPrompt: (String) -> Unit
) {
    if (videos.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, NaxCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Phone Video Folder (${videos.size})", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            videos.forEachIndexed { idx, vid ->
                if (idx > 0) HorizontalDivider(color = NaxBorder.copy(alpha = 0.45f), modifier = Modifier.padding(vertical = 5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectVideoPrompt("Is video '${vid.name}' (${vid.sizeMb}) ke liye viral YouTube Shorts title, description aur hashtags likho.") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vid.name, color = NaxTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(vid.sizeMb, color = NaxTextSecondary, fontSize = 10.sp)
                    }
                    Text("Write Title →", color = NaxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RoutinePreviewCard(
    pending: PendingRoutineApproval,
    onSave: () -> Unit,
    onReject: () -> Unit
) {
    val flow = pending.flow
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, NaxCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(flow.title, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("Trigger: ${flow.frequency} • ${flow.triggerTime}", color = NaxCyan, fontSize = 12.sp)

            Spacer(Modifier.height(8.dp))
            flow.steps.forEachIndexed { idx, step ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeBadge(mode = step.mode)
                    Spacer(Modifier.width(8.dp))
                    Text("${idx + 1}. ${step.command}", color = NaxTextPrimary, fontSize = 12.sp, maxLines = 2)
                }
            }

            Spacer(Modifier.height(10.dp))
            when {
                pending.saved -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NaxGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Auto-Saved in Routines Tab ✓", color = NaxGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                pending.rejected -> Text("Routine discarded.", color = NaxTextSecondary, fontSize = 12.sp)
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NaxCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Activate & Save", color = Color(0xFF0A0D16), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onReject,
                            border = BorderStroke(1.dp, NaxBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = NaxTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilitiesCard(
    skills: List<SkillItem>,
    onQuickPrompt: (String) -> Unit
) {
    val activeSkills = skills.filter { it.enabled }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NaxSandboxPurple, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("NAX Live Capabilities (${activeSkills.size})", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            activeSkills.forEachIndexed { idx, skill ->
                if (idx > 0) HorizontalDivider(color = NaxBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (skill.commandsHint.isNotBlank()) onQuickPrompt(skill.commandsHint.replace("\"", ""))
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skill.name, color = NaxTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (skill.isNew) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "NEW",
                                    color = NaxGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NaxGreen.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(skill.description, color = NaxTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    ModeBadge(mode = skill.mode)
                }
            }
        }
    }
}

@Composable
fun ArtifactCard(
    artifact: ArtifactPreview,
    onPlayInSandbox: (ArtifactPreview) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val isPlayableHtml = artifact.type.equals("html", ignoreCase = true) ||
        artifact.content.lowercase().contains("<html") ||
        artifact.content.lowercase().contains("<canvas") ||
        artifact.content.lowercase().contains("<div")

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCard),
        border = BorderStroke(1.dp, if (isPlayableHtml) NaxSandboxPurple.copy(alpha = 0.65f) else NaxBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(artifact.title, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Artifact • ${artifact.type.uppercase()} • Tap to ${if (expanded) "hide" else "view"} code", color = NaxTextSecondary, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPlayableHtml) {
                    Button(
                        onClick = { onPlayInSandbox(artifact) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NaxSandboxPurple),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play in Sandbox", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(artifact.content))
                        copied = true
                    },
                    border = BorderStroke(1.dp, NaxBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (copied) "Copied ✓" else "Copy", color = NaxTextPrimary, fontSize = 11.sp)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF070A12))
                        .padding(10.dp)
                ) {
                    Text(
                        text = artifact.content,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActionApprovalCard(
    approval: ActionApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val accent = if (approval.mode == ExecutionMode.SANDBOX) NaxSandboxPurple else NaxLiveRed
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCardElevated),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(approval.title, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ModeBadge(mode = approval.mode)
            }
            Spacer(Modifier.height(4.dp))
            Text(approval.details, color = NaxTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            when (approval.status) {
                "PENDING" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Allow & Run", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onReject,
                            border = BorderStroke(1.dp, NaxBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Deny", color = NaxTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                "APPROVED" -> Text("Executed ✓", color = NaxGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                else -> Text("Action denied.", color = NaxTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun VerdictBadge(verdict: SecurityVerdict) {
    val color = when (verdict) {
        SecurityVerdict.PASS -> NaxGreen
        SecurityVerdict.WARN -> NaxAmber
        SecurityVerdict.BLOCKED -> NaxLiveRed
    }
    Text(
        text = verdict.label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
fun ModeBadge(mode: ExecutionMode) {
    val color = when (mode) {
        ExecutionMode.AUTO -> NaxCyan
        ExecutionMode.SANDBOX -> NaxSandboxPurple
        ExecutionMode.LIVE -> NaxLiveRed
    }
    Text(
        text = mode.name,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
