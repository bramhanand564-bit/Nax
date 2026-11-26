package com.jarvis.os.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.os.data.ApiConfig
import com.jarvis.os.data.PrivacyShield
import com.jarvis.os.data.ProviderType
import com.jarvis.os.live.LiveTouchService
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
    var newRuleText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NaxBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Live System Health & 24/7 Engine Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NAX Telemetry & System Controls", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatMiniBox("API Calls", "${state.telemetry.totalCalls}", Modifier.weight(1f))
                        StatMiniBox("Tokens", "${state.telemetry.totalTokens}", Modifier.weight(1f))
                        StatMiniBox("Latency", "${state.telemetry.lastLatencyMs}ms", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.toggleForegroundEngine() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.engineRunning) NaxGreen else NaxCardElevated
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (state.engineRunning) "24/7 ON" else "Start 24/7", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { LiveTouchService.openAccessibilitySettings(context) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, if (state.liveConnected) NaxGreen else NaxLiveRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = if (state.liveConnected) NaxGreen else NaxLiveRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (state.liveConnected) "Live Ready ✓" else "Enable Live",
                                color = if (state.liveConnected) NaxGreen else NaxTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. API Testing Studio Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.Science, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Brain & API Testing Studio", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(
                text = "Gemini, Groq, Claude, Local Model aur Custom API configure karke 1-tap health test karein.",
                color = NaxTextSecondary,
                fontSize = 11.sp
            )
        }

        // Provider Config + 1-Tap Test Cards
        items(state.apiConfigs, key = { it.provider.name }) { cfg ->
            ProviderApiCard(
                config = cfg,
                isTesting = state.testingProvider == cfg.provider,
                onSave = { key, model, url, enabled ->
                    viewModel.updateApiConfig(cfg.provider, key, model, url, enabled)
                },
                onTest = { viewModel.testApiProvider(cfg.provider) }
            )
        }

        // 3. Privacy Shield Vault
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NaxCard),
                border = BorderStroke(1.dp, NaxGreen.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = NaxGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Privacy Shield (No-AI Protected Zones)", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    PrivacyShield.getDefaultProtectedLabels().forEach { itemLabel ->
                        Text("• $itemLabel", color = NaxTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        // 4. Self-Learning Memory Vault Rules
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
                            placeholder = { Text("Add rule (e.g. Always reply in Hinglish)", fontSize = 12.sp) },
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
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NaxSandboxPurple)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add Rule", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (state.memoryRules.isEmpty()) {
                        Text("Sandbox Teach Mode aur user rules yahan save honge.", color = NaxTextSecondary, fontSize = 11.sp)
                    } else {
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
}

@Composable
private fun StatMiniBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NaxCardElevated)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = NaxCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = NaxTextSecondary, fontSize = 10.sp)
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
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor)
                        )
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

            // Live API Diagnostic Result Pill
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
                    Text(
                        text = res.message,
                        color = if (res.success) NaxGreen else NaxLiveRed,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(if (config.provider == ProviderType.LOCAL) "API Key (Optional for Local)" else "API Key")
                    },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle Key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Endpoint Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
