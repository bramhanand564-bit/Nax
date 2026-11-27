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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.PrivacyShield
import com.jarvis.os.data.ProviderType
import com.jarvis.os.live.DeviceSensorBridge
import com.jarvis.os.live.LiveTouchService
import com.jarvis.os.live.NaxLiveLogger
import com.jarvis.os.live.NaxVoiceEngine
import com.jarvis.os.ui.theme.NaxAmber
import com.jarvis.os.ui.theme.NaxBg
import com.jarvis.os.ui.theme.NaxBorder
import com.jarvis.os.ui.theme.NaxCard
import com.jarvis.os.ui.theme.NaxCardElevated
import com.jarvis.os.ui.theme.NaxCyan
import com.jarvis.os.ui.theme.NaxGreen
import com.jarvis.os.ui.theme.NaxLiveRed
import com.jarvis.os.ui.theme.NaxSandboxPurple
import com.jarvis.os.ui.theme.NaxTextPrimary
import com.jarvis.os.ui.theme.NaxTextSecondary
import com.jarvis.os.viewmodel.NaxUiState
import com.jarvis.os.viewmodel.NaxViewModel

@Composable
fun BrainLabScreen(
    state: NaxUiState,
    viewModel: NaxViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val liveLogs by NaxLiveLogger.logs.collectAsState()
    val liveMetrics by NaxLiveLogger.metrics.collectAsState()
    var newRuleText by remember { mutableStateOf("") }
    var logsCopied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NaxBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. AuraDroid-Style Live API Meter & 24/7 Engine Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NAX Live Telemetry & RPM Meter", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            text = "Reset",
                            color = NaxLiveRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { NaxLiveLogger.resetMetrics() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatMiniBox("1-Min RPM", "${liveMetrics.callsLastMinute}", Modifier.weight(1f))
                        StatMiniBox("OK / 429", "${liveMetrics.sessionOk}/${liveMetrics.session429}", Modifier.weight(1f))
                        StatMiniBox("Avg Speed", "${liveMetrics.avgLatencyMs}ms", Modifier.weight(1f))
                        StatMiniBox("Tokens", "${state.telemetry.totalTokens}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.toggleForegroundEngine() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.engineRunning) NaxGreen else NaxSandboxPurple
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.engineRunning) "24/7 Companion + Floating Orb: ON" else "Start 24/7 Companion + Floating Orb",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Floating NAX Orb, Stealth Mode & Good Morning / Good Night Companion
        item {
            val comp = state.companionSettings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("✨ Floating NAX Orb & Daily Companion", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    SettingToggleRow("Floating Orb Outside App", comp.floatingOrbEnabled) {
                        viewModel.updateCompanionSettings(comp.copy(floatingOrbEnabled = it))
                    }
                    SettingToggleRow("👻 Stealth Mode (Edge Hide)", comp.stealthMode) {
                        viewModel.updateCompanionSettings(comp.copy(stealthMode = it))
                    }

                    Text(
                        text = "Edge Opacity: ${comp.edgeAlphaPercent}%",
                        color = NaxTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Slider(
                        value = comp.edgeAlphaPercent.toFloat(),
                        onValueChange = {
                            viewModel.updateCompanionSettings(comp.copy(edgeAlphaPercent = it.toInt()))
                        },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(thumbColor = NaxCyan, activeTrackColor = NaxSandboxPurple)
                    )

                    SettingToggleRow("☀️ Good Morning / 🌙 Good Night Updates", comp.morningNightEnabled) {
                        viewModel.updateCompanionSettings(comp.copy(morningNightEnabled = it))
                    }
                    SettingToggleRow("🔋 Smart Battery Guardian (<20% & 100%)", comp.batteryGuardianEnabled) {
                        viewModel.updateCompanionSettings(comp.copy(batteryGuardianEnabled = it))
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.triggerCompanionPreview(true) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NaxAmber),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Test ☀️ Morning", color = NaxAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerCompanionPreview(false) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NaxCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Test 🌙 Night", color = NaxCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Offline Indic Voice Organ (12 Indian Languages + English)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Indic Voice Organ (0 Cost)", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("12 Indian Languages + English • Works Offline", color = NaxTextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = state.voiceOutputEnabled,
                            onCheckedChange = { viewModel.toggleVoiceOutput() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaxCyan)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NaxVoiceEngine.supportedIndicLanguages.forEach { lang ->
                            val selected = state.voiceLangCode == lang.code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) NaxCyan.copy(alpha = 0.22f) else NaxCardElevated)
                                    .border(1.dp, if (selected) NaxCyan else NaxBorder, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setVoiceLanguage(lang.code) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(lang.label, color = if (selected) NaxTextPrimary else NaxTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Calibrated Phone Body & 4-Step Permissions Checklist (AuraDroid Style)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🦾 Calibrated Phone Body & Permissions", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    PermissionActionRow(
                        icon = Icons.Rounded.Layers,
                        label = "1. Grant Floating Overlay Permission",
                        granted = state.overlayPermGranted,
                        onClick = { DeviceSensorBridge.openOverlayPermission(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        label = "2. Enable Accessibility Live Control",
                        granted = state.liveConnected,
                        onClick = { LiveTouchService.openAccessibilitySettings(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Rounded.Settings,
                        label = "3. Allow Direct Brightness Control",
                        granted = state.writeSettingsGranted,
                        onClick = { DeviceSensorBridge.openWriteSettingsPermission(context) }
                    )
                    PermissionActionRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        label = "4. Enable 24/7 Unstoppable Battery Mode",
                        granted = state.batteryOptIgnored,
                        onClick = { DeviceSensorBridge.requestUnstoppableBatteryMode(context) }
                    )
                }
            }
        }

        // 5. AI Brain & API Testing Studio
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Brain & API Testing Studio", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text("Gemini, Groq, Claude, Local Model & Custom API 1-tap test bench.", color = NaxTextSecondary, fontSize = 11.sp)
        }

        items(state.apiConfigs, key = { it.provider.name }) { cfg ->
            ProviderApiCard(
                config = cfg,
                isTesting = state.testingProvider == cfg.provider,
                onSave = { key, model, url, enabled -> viewModel.updateApiConfig(cfg.provider, key, model, url, enabled) },
                onTest = { viewModel.testApiProvider(cfg.provider) }
            )
        }

        // 6. AuraDroid-Style Live System & API Call History Logs Terminal
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxGreen.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = NaxGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Live System & API Logs (${liveLogs.size})", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(NaxLiveLogger.exportText()))
                                logsCopied = true
                            },
                            border = BorderStroke(1.dp, NaxBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (logsCopied) "Copied" else "Copy", color = NaxTextPrimary, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { NaxLiveLogger.clearLogs() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear Logs", tint = NaxLiveRed, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 210.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF060911))
                            .padding(10.dp)
                    ) {
                        val content = if (liveLogs.isEmpty()) "No live logs yet — send a command in NAX Chat!" else liveLogs.take(25).joinToString("\n")
                        Text(
                            text = content,
                            color = Color(0xFF4ADE80),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 7. Privacy Shield & Memory Vault
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxGreen.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = NaxGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Privacy Shield (Auto-Protected)", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    PrivacyShield.getDefaultProtectedLabels().forEach { itemLabel ->
                        Text("• $itemLabel", color = NaxTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NAX Memory Vault (${state.memoryRules.size} Rules)", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newRuleText,
                            onValueChange = { newRuleText = it },
                            placeholder = { Text("Add rule (e.g. Always reply short)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newRuleText.isNotBlank()) {
                                    viewModel.addMemoryRule("UserRule", newRuleText)
                                    newRuleText = ""
                                }
                            },
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NaxSandboxPurple)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    state.memoryRules.take(10).forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NaxCardElevated)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.category, color = NaxCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(rule.content, color = NaxTextPrimary, fontSize = 11.sp)
                            }
                            IconButton(onClick = { viewModel.deleteMemoryRule(rule.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = NaxLiveRed.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NaxTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaxSandboxPurple)
        )
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) NaxGreen.copy(alpha = 0.16f) else NaxCardElevated)
            .border(1.dp, if (granted) NaxGreen.copy(alpha = 0.55f) else NaxBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (granted) NaxGreen else NaxCyan, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = NaxTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "ON ✓" else "Enable",
            color = if (granted) NaxGreen else NaxAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatMiniBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NaxCardElevated)
            .padding(vertical = 7.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = NaxCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(label, color = NaxTextSecondary, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun ProviderApiCard(
    config: ApiConfig,
    isTesting: Boolean,
    onSave: (String, String, String, Boolean) -> Unit,
    onTest: () -> Unit
) {
    var expanded by remember { mutableStateOf(config.provider == ProviderType.GEMINI) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var enabled by remember(config.enabled) { mutableStateOf(config.enabled) }
    var showKey by remember { mutableStateOf(false) }

    val configured = config.provider == ProviderType.LOCAL || config.apiKey.isNotBlank()
    val statusColor = when {
        config.lastTest?.success == true -> NaxGreen
        config.lastTest?.success == false -> NaxLiveRed
        configured -> NaxAmber
        else -> NaxTextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCard),
        border = BorderStroke(1.dp, if (config.lastTest?.success == true) NaxGreen.copy(alpha = 0.45f) else NaxBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(config.provider.displayName, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
                    }
                    Text(config.model, color = NaxTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onSave(apiKey, model, baseUrl, it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaxSandboxPurple)
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = NaxTextSecondary
                )
            }

            config.lastTest?.let { res ->
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background((if (res.success) NaxGreen else NaxLiveRed).copy(alpha = 0.12f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (res.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = null,
                        tint = if (res.success) NaxGreen else NaxLiveRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(res.message, color = if (res.success) NaxGreen else NaxLiveRed, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (config.provider == ProviderType.LOCAL) "API Key (Optional)" else "API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Endpoint Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(apiKey, model, baseUrl, enabled) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NaxSandboxPurple),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            onSave(apiKey, model, baseUrl, enabled)
                            onTest()
                        },
                        enabled = !isTesting,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NaxCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NaxCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Testing...", color = NaxCyan, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Rounded.Science, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Test API", color = NaxCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
