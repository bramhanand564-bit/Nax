package com.jarvis.os.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.os.data.ChatMessage
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.ProviderType
import com.jarvis.os.data.ThinkingLevel
import com.jarvis.os.live.LiveTouchService
import com.jarvis.os.ui.components.ActionApprovalCard
import com.jarvis.os.ui.components.ArtifactCard
import com.jarvis.os.ui.components.CapabilitiesCard
import com.jarvis.os.ui.components.FindSkillsResultCard
import com.jarvis.os.ui.components.GitHubSkillInstallCard
import com.jarvis.os.ui.components.ModeBadge
import com.jarvis.os.ui.components.ParallelSubAgentsCard
import com.jarvis.os.ui.components.RoutinePreviewCard
import com.jarvis.os.ui.components.VideoFolderCard
import com.jarvis.os.ui.theme.NaxAmber
import com.jarvis.os.ui.theme.NaxBg
import com.jarvis.os.ui.theme.NaxBorder
import com.jarvis.os.ui.theme.NaxCard
import com.jarvis.os.ui.theme.NaxCardElevated
import com.jarvis.os.ui.theme.NaxCyan
import com.jarvis.os.ui.theme.NaxGreen
import com.jarvis.os.ui.theme.NaxLiveRed
import com.jarvis.os.ui.theme.NaxSandboxPurple
import com.jarvis.os.ui.theme.NaxSurface
import com.jarvis.os.ui.theme.NaxTextPrimary
import com.jarvis.os.ui.theme.NaxTextSecondary
import com.jarvis.os.viewmodel.NaxUiState
import com.jarvis.os.viewmodel.NaxViewModel

@Composable
fun NaxChatScreen(
    state: NaxUiState,
    viewModel: NaxViewModel,
    voiceInputText: String,
    onVoiceConsumed: () -> Unit,
    onVoiceClick: () -> Unit,
    onPickMedia: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showThinkingMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(voiceInputText) {
        if (voiceInputText.isNotBlank()) {
            inputText = voiceInputText
            onVoiceConsumed()
        }
    }

    LaunchedEffect(state.messages.size, state.isThinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaxBg)
    ) {
        // Optional Sandbox PiP Mini-Preview Strip
        if (state.showSandboxPip) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                color = NaxCardElevated,
                border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = NaxSandboxPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!state.activeSandboxHtml.isNullOrBlank())
                                "🎮 Live HTML: ${state.activeSandboxHtmlTitle}"
                            else
                                "Sandbox: ${state.activeSandboxApp?.name ?: "Mini Phone"}",
                            color = NaxTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!state.activeSandboxHtml.isNullOrBlank()) "Tap to play in Sandbox Phone" else state.activeSandboxUrl,
                            color = NaxTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { viewModel.selectTab(1) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open Sandbox", tint = NaxCyan, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { viewModel.toggleSandboxPip(false) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Hide", tint = NaxTextSecondary, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        // Live Accessibility Hint (only when LIVE selected and Accessibility off)
        if (state.executionMode == ExecutionMode.LIVE && !state.liveConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { LiveTouchService.openAccessibilitySettings(context) },
                shape = RoundedCornerShape(12.dp),
                color = NaxLiveRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, NaxLiveRed.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = NaxLiveRed, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Tap to enable NAX Accessibility for full Live search/tap & screenshot (UPI/Bank protected)",
                        color = NaxTextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Main Chat Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                ChatBubbleItem(msg = msg, state = state, viewModel = viewModel)
            }

            if (state.isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NaxCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("NAX soch raha hai (${state.thinkingLevel.badge})...", color = NaxTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick Action Chips Row
        val quickPrompts = listOf(
            "🔍 Find Skills" to "Video editing ke liye koi best skill dhoondho",
            "🔥 Grill Me" to "Grill me: Mere NAX app plan mein kya loopholes hain?",
            "💬 /btw" to "/btw ",
            "📂 Video Folder" to "Check my video folder",
            "✨ Skills?" to "Tumhe kya-kya aata hai?",
            "🔋 Battery" to "Real phone battery status batao"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            quickPrompts.forEach { (chipLabel, promptText) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NaxCard)
                        .border(1.dp, NaxBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            if (promptText == "/btw ") {
                                inputText = "/btw "
                            } else {
                                viewModel.sendMessage(promptText)
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(chipLabel, color = NaxTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Pending Attached Photo / Video / Audio Chip
        state.pendingAttachment?.let { att ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NaxCyan.copy(alpha = 0.16f))
                    .border(1.dp, NaxCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emoji = when (att.type) {
                    "photo" -> "📷"
                    "video" -> "🎬"
                    else -> "🎵"
                }
                Text("$emoji ${att.name}", color = NaxTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = NaxTextSecondary,
                    modifier = Modifier.size(15.dp).clickable { viewModel.setPendingAttachment(null) }
                )
            }
        }

        // Flagship Chat Input Box with [Auto | Sandbox | Live] + Thinking Level + Attach Media + Voice
        val activeAccent = when (state.executionMode) {
            ExecutionMode.AUTO -> NaxCyan
            ExecutionMode.SANDBOX -> NaxSandboxPurple
            ExecutionMode.LIVE -> NaxLiveRed
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = RoundedCornerShape(20.dp),
            color = NaxSurface,
            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = when (state.executionMode) {
                                ExecutionMode.AUTO -> "NAX ko bolo (Auto decides Sandbox/Live, ya /btw likho)..."
                                ExecutionMode.SANDBOX -> "Sandbox mein kya chalana hai / GitHub link paste karo..."
                                ExecutionMode.LIVE -> "Real phone par kya karwana hai (Live Mode)..."
                            },
                            color = NaxTextSecondary.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = NaxTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(NaxCyan),
                        maxLines = 4
                    )
                }

                // Controls Row INSIDE Chat Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3-Way Mode Pill: Auto | Sandbox | Live
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(13.dp))
                            .background(NaxBg)
                            .border(1.dp, NaxBorder, RoundedCornerShape(13.dp))
                            .padding(2.dp)
                    ) {
                        ModeToggleChip("Auto", state.executionMode == ExecutionMode.AUTO, NaxCyan) {
                            viewModel.setExecutionMode(ExecutionMode.AUTO)
                        }
                        ModeToggleChip("SB", state.executionMode == ExecutionMode.SANDBOX, NaxSandboxPurple) {
                            viewModel.setExecutionMode(ExecutionMode.SANDBOX)
                        }
                        ModeToggleChip("Live", state.executionMode == ExecutionMode.LIVE, NaxLiveRed) {
                            viewModel.setExecutionMode(ExecutionMode.LIVE)
                        }
                    }

                    Spacer(Modifier.width(5.dp))

                    // 4 Thinking Levels Dropdown Pill (Low / Med / High / XHigh)
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NaxCard)
                                .border(1.dp, NaxBorder, RoundedCornerShape(10.dp))
                                .clickable { showThinkingMenu = true }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Psychology, contentDescription = null, tint = NaxAmber, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(state.thinkingLevel.badge, color = NaxTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = showThinkingMenu,
                            onDismissRequest = { showThinkingMenu = false },
                            modifier = Modifier.background(NaxCardElevated)
                        ) {
                            ThinkingLevel.entries.forEach { lvl ->
                                DropdownMenuItem(
                                    text = { Text(lvl.label, color = NaxTextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.setThinkingLevel(lvl)
                                        showThinkingMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(5.dp))

                    // Quick AI Model Dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NaxCard)
                                .border(1.dp, NaxBorder, RoundedCornerShape(10.dp))
                                .clickable { showModelMenu = true }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (state.selectedProviderCode == "AUTO") "AI" else state.selectedProviderCode.take(4),
                                color = NaxTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = NaxTextSecondary, modifier = Modifier.size(12.dp))
                        }
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.background(NaxCardElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("⚡ Auto Router", color = NaxTextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.setSelectedProvider("AUTO")
                                    showModelMenu = false
                                }
                            )
                            ProviderType.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName, color = NaxTextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.setSelectedProvider(p.name)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // 📎 Media Attachment Menu (Photo / Video / Audio / Scan Video Folder)
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(NaxCard)
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = "Attach Media", tint = NaxTextPrimary, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                            modifier = Modifier.background(NaxCardElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("📷 Attach Photo (Vision AI)", color = NaxTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    showAttachMenu = false
                                    onPickMedia("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🎬 Attach Video", color = NaxTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    showAttachMenu = false
                                    onPickMedia("video/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🎵 Attach Audio / Voice", color = NaxTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    showAttachMenu = false
                                    onPickMedia("audio/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📂 Check Phone Video Folder", color = NaxCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showAttachMenu = false
                                    viewModel.sendMessage("Check my video folder")
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(5.dp))

                    // Voice Orb Mic Button
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(NaxCard)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Voice", tint = NaxCyan, modifier = Modifier.size(16.dp))
                    }

                    Spacer(Modifier.width(5.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || state.pendingAttachment != null) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(activeAccent)
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggleChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF0A0D16) else NaxTextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

@Composable
private fun ChatBubbleItem(
    msg: ChatMessage,
    state: NaxUiState,
    viewModel: NaxViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleBg = if (msg.isUser) {
            when (msg.mode) {
                ExecutionMode.SANDBOX -> NaxSandboxPurple.copy(alpha = 0.22f)
                ExecutionMode.LIVE -> NaxLiveRed.copy(alpha = 0.22f)
                ExecutionMode.AUTO -> NaxCyan.copy(alpha = 0.2f)
            }
        } else if (msg.isBtw) {
            NaxCardElevated
        } else {
            NaxCard
        }

        val borderColor = if (msg.isBtw) {
            NaxAmber.copy(alpha = 0.6f)
        } else if (msg.isUser) {
            when (msg.mode) {
                ExecutionMode.SANDBOX -> NaxSandboxPurple.copy(alpha = 0.5f)
                ExecutionMode.LIVE -> NaxLiveRed.copy(alpha = 0.5f)
                ExecutionMode.AUTO -> NaxCyan.copy(alpha = 0.5f)
            }
        } else {
            NaxBorder
        }

        Column(
            modifier = Modifier
                .widthIn(max = 335.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (!msg.isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (msg.isBtw) "NAX /btw" else "NAX",
                        color = if (msg.isBtw) NaxAmber else NaxCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                    if (msg.providerBadge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("• ${msg.providerBadge}", color = NaxTextSecondary, fontSize = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    ModeBadge(mode = msg.mode)
                }
                Spacer(Modifier.height(6.dp))
            }

            msg.attachedMedia?.let { att ->
                val icon = when (att.type) {
                    "photo" -> "📷"
                    "video" -> "🎬"
                    else -> "🎵"
                }
                Text(
                    text = "$icon Attached ${att.type}: ${att.name}",
                    color = NaxCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = msg.text.replace("**", ""),
                color = NaxTextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            // All Inline Interactive Cards
            msg.pendingSkill?.let { pending ->
                GitHubSkillInstallCard(
                    pending = pending,
                    onApprove = { viewModel.approveSkillInstall(msg.id) },
                    onReject = { viewModel.rejectSkillInstall(msg.id) }
                )
            }

            if (msg.discoveredSkills.isNotEmpty()) {
                FindSkillsResultCard(
                    discovered = msg.discoveredSkills,
                    onInstallSkill = { viewModel.inspectAndInstallDiscoveredSkill(it) }
                )
            }

            if (msg.subAgents.isNotEmpty()) {
                ParallelSubAgentsCard(agents = msg.subAgents)
            }

            if (msg.deviceVideos.isNotEmpty()) {
                VideoFolderCard(
                    videos = msg.deviceVideos,
                    onSelectVideoPrompt = { viewModel.sendMessage(it) }
                )
            }

            msg.pendingRoutine?.let { pending ->
                RoutinePreviewCard(
                    pending = pending,
                    onSave = { viewModel.approveRoutineFlow(msg.id) },
                    onReject = { viewModel.rejectRoutineFlow(msg.id) }
                )
            }

            if (msg.showCapabilities) {
                CapabilitiesCard(
                    skills = state.skills,
                    onQuickPrompt = { viewModel.sendMessage(it) }
                )
            }

            msg.artifact?.let { art ->
                ArtifactCard(
                    artifact = art,
                    onPlayInSandbox = { viewModel.previewHtmlInSandbox(it) }
                )
            }

            msg.actionApproval?.let { approval ->
                ActionApprovalCard(
                    approval = approval,
                    onApprove = { viewModel.approveDeviceAction(msg.id) },
                    onReject = { viewModel.rejectDeviceAction(msg.id) }
                )
            }
        }
    }
}
