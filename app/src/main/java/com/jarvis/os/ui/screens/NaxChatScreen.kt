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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import com.jarvis.os.live.LiveTouchService
import com.jarvis.os.ui.components.ActionApprovalCard
import com.jarvis.os.ui.components.ArtifactCard
import com.jarvis.os.ui.components.CapabilitiesCard
import com.jarvis.os.ui.components.GitHubSkillInstallCard
import com.jarvis.os.ui.components.ModeBadge
import com.jarvis.os.ui.components.RoutinePreviewCard
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
    onVoiceClick: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
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
        // Optional Sandbox PiP Mini-Preview Strip inside Chat
        if (state.showSandboxPip) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = NaxCardElevated,
                border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = NaxSandboxPurple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sandbox Active: ${state.activeSandboxApp?.name ?: "Mini Phone"}",
                            color = NaxTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.activeSandboxUrl,
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

        // Live Mode Permission Hint Banner (only if LIVE mode selected & Accessibility off)
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = NaxLiveRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Live Mode ON • Tap to enable NAX Accessibility for full screen control (UPI/Bank protected)",
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
                ChatBubbleItem(
                    msg = msg,
                    state = state,
                    viewModel = viewModel
                )
            }

            if (state.isThinking) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NaxCyan
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("NAX soch raha hai...", color = NaxTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick Starter Action Chips
        val quickPrompts = listOf(
            "Tumhe kya-kya aata hai?",
            "Sandbox mein YouTube kholo",
            "Roz subah 9 baje Sandbox mein YouTube check karna",
            "https://github.com/google/artemis install karo"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NaxCard)
                        .border(1.dp, NaxBorder, RoundedCornerShape(20.dp))
                        .clickable { viewModel.sendMessage(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt.take(36) + if (prompt.length > 36) "…" else "",
                        color = NaxTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Flagship Chat Input Box with BUILT-IN [Sandbox | Live] Toggle + AI Model Pill + Voice
        val activeAccent = if (state.executionMode == ExecutionMode.SANDBOX) NaxSandboxPurple else NaxLiveRed
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = NaxSurface,
            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (state.executionMode == ExecutionMode.SANDBOX)
                                "NAX ko bolo ya GitHub skill link paste karo (Sandbox)..."
                            else
                                "Real phone par kya karwana hai? (Live Mode)...",
                            color = NaxTextSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp
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

                // Bottom Control Bar INSIDE Chat Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Direct [Sandbox | Live] Pill Switch inside Chat Box
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(NaxBg)
                            .border(1.dp, NaxBorder, RoundedCornerShape(14.dp))
                            .padding(2.dp)
                    ) {
                        ModeToggleChip(
                            label = "Sandbox",
                            selected = state.executionMode == ExecutionMode.SANDBOX,
                            color = NaxSandboxPurple,
                            onClick = { viewModel.setExecutionMode(ExecutionMode.SANDBOX) }
                        )
                        ModeToggleChip(
                            label = "Live",
                            selected = state.executionMode == ExecutionMode.LIVE,
                            color = NaxLiveRed,
                            onClick = { viewModel.setExecutionMode(ExecutionMode.LIVE) }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Quick Model Router Dropdown Pill
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NaxCard)
                                .border(1.dp, NaxBorder, RoundedCornerShape(12.dp))
                                .clickable { showModelMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.selectedProviderCode == "AUTO") "Auto" else state.selectedProviderCode.lowercase().replaceFirstChar { it.uppercase() },
                                color = NaxTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = NaxTextSecondary, modifier = Modifier.size(14.dp))
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.background(NaxCardElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("⚡ Auto Smart Router", color = NaxTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    viewModel.setSelectedProvider("AUTO")
                                    showModelMenu = false
                                }
                            )
                            ProviderType.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName, color = NaxTextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.setSelectedProvider(provider.name)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Voice Orb Button
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NaxCard)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Voice", tint = NaxCyan, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(activeAccent)
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
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
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) color else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else NaxTextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
            if (msg.mode == ExecutionMode.SANDBOX) NaxSandboxPurple.copy(alpha = 0.22f)
            else NaxLiveRed.copy(alpha = 0.22f)
        } else {
            NaxCard
        }
        val borderColor = if (msg.isUser) {
            if (msg.mode == ExecutionMode.SANDBOX) NaxSandboxPurple.copy(alpha = 0.5f)
            else NaxLiveRed.copy(alpha = 0.5f)
        } else {
            NaxBorder
        }

        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (!msg.isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NAX", color = NaxCyan, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    if (msg.providerBadge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("• ${msg.providerBadge}", color = NaxTextSecondary, fontSize = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    ModeBadge(mode = msg.mode)
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = msg.text.replace("**", ""),
                color = NaxTextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            // Inline Cards inside Chat
            msg.pendingSkill?.let { pending ->
                GitHubSkillInstallCard(
                    pending = pending,
                    onApprove = { viewModel.approveSkillInstall(msg.id) },
                    onReject = { viewModel.rejectSkillInstall(msg.id) }
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
                ArtifactCard(artifact = art)
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
