package com.jarvis.os.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.os.sandbox.SandboxWebEngine
import com.jarvis.os.ui.theme.NaxAmber
import com.jarvis.os.ui.theme.NaxBg
import com.jarvis.os.ui.theme.NaxBorder
import com.jarvis.os.ui.theme.NaxCard
import com.jarvis.os.ui.theme.NaxCardElevated
import com.jarvis.os.ui.theme.NaxCyan
import com.jarvis.os.ui.theme.NaxGreen
import com.jarvis.os.ui.theme.NaxSandboxPurple
import com.jarvis.os.ui.theme.NaxSurface
import com.jarvis.os.ui.theme.NaxTextPrimary
import com.jarvis.os.ui.theme.NaxTextSecondary
import com.jarvis.os.viewmodel.NaxUiState
import com.jarvis.os.viewmodel.NaxViewModel

@Composable
fun SandboxPhoneScreen(
    state: NaxUiState,
    viewModel: NaxViewModel
) {
    var showInstallDialog by remember { mutableStateOf(false) }
    var lastLoadedUrl by remember { mutableStateOf("") }

    val engine = remember {
        SandboxWebEngine(
            onUrlChanged = { url -> viewModel.updateSandboxUrl(url) },
            onControlsScanned = { nodes -> viewModel.updateSandboxControls(nodes) },
            onTeachElementCaptured = { desc -> viewModel.recordTaughtControl(desc) }
        )
    }

    LaunchedEffect(state.activeSandboxUrl) {
        if (state.activeSandboxUrl.isNotBlank() && state.activeSandboxUrl != lastLoadedUrl) {
            lastLoadedUrl = state.activeSandboxUrl
            engine.navigateTo(state.activeSandboxUrl)
        }
    }

    LaunchedEffect(state.xrayEnabled) {
        engine.setXrayOverlay(state.xrayEnabled)
    }

    LaunchedEffect(state.teachModeEnabled) {
        engine.setTeachMode(state.teachModeEnabled)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaxBg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Installed Sandbox Apps Launcher Strip (Phone inside Phone)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.sandboxApps.forEach { app ->
                val isSelected = state.activeSandboxApp?.id == app.id
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) NaxSandboxPurple.copy(alpha = 0.25f) else NaxCard)
                        .border(
                            1.dp,
                            if (isSelected) NaxSandboxPurple else NaxBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            viewModel.openSandboxApp(app)
                            lastLoadedUrl = app.url
                            engine.navigateTo(app.url)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.emoji, fontSize = 14.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = app.name,
                        color = if (isSelected) NaxTextPrimary else NaxTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // + Install New App inside Sandbox Phone
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NaxCyan.copy(alpha = 0.14f))
                    .border(1.dp, NaxCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { showInstallDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Install App", tint = NaxCyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("Install App", color = NaxCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Virtual Mini-Phone Frame
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = NaxSurface,
            border = BorderStroke(1.5.dp, NaxSandboxPurple.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Virtual Phone Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NaxCardElevated)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { engine.goBack() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = NaxTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { engine.reload() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = NaxTextPrimary, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = state.activeSandboxApp?.name ?: "Sandbox Device",
                        color = NaxTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        maxLines = 1
                    )

                    // X-Ray Control Map Toggle
                    SandboxToolPill(
                        icon = Icons.Rounded.Visibility,
                        label = "X-Ray",
                        active = state.xrayEnabled,
                        activeColor = NaxSandboxPurple,
                        onClick = { viewModel.toggleXrayOverlay() }
                    )
                    Spacer(Modifier.width(4.dp))

                    // Teach AI Toggle
                    SandboxToolPill(
                        icon = Icons.Rounded.School,
                        label = "Teach",
                        active = state.teachModeEnabled,
                        activeColor = NaxAmber,
                        onClick = { viewModel.toggleTeachMode() }
                    )

                    IconButton(
                        onClick = { engine.clearSandboxSessionData() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Reset Sandbox", tint = NaxTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                // Isolated Sandbox WebView Display
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                lastLoadedUrl = state.activeSandboxUrl
                                engine.attach(this, state.activeSandboxUrl)
                            }
                        }
                    )
                }

                // AI Control Blueprint Bar (Shows NAX how to control everything inside this Sandbox app)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NaxCard)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "AI Control Map (${state.sandboxControls.size} elements) • Tap any tag to test",
                            color = NaxCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Rescan",
                            color = NaxSandboxPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { engine.scanControlBlueprint(state.xrayEnabled) }
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    if (state.sandboxControls.isEmpty()) {
                        Text(
                            text = "Page load hone par saare clickable controls automatically map ho jayenge.",
                            color = NaxTextSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.sandboxControls.take(18).forEach { node ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NaxCardElevated)
                                        .border(1.dp, NaxBorder, RoundedCornerShape(8.dp))
                                        .clickable { engine.clickControlIndex(node.index) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "[${node.index}] ${node.text.take(20)}",
                                        color = NaxTextPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    val taught = state.activeSandboxApp?.learnedControls.orEmpty()
                    if (taught.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Learned Controls: ${taught.takeLast(3).joinToString(" • ")}",
                            color = NaxGreen,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Dialog to Install a New App into Sandbox Phone
    if (showInstallDialog) {
        var appName by remember { mutableStateOf("") }
        var appUrl by remember { mutableStateOf("") }
        var appEmoji by remember { mutableStateOf("📱") }

        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            containerColor = NaxCardElevated,
            title = { Text("Install App in NAX Sandbox", color = NaxTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Isolated Virtual Phone mein koi bhi app/web-app add karo — NAX iske saare controls auto-map kar lega.",
                        color = NaxTextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App Name (e.g. X, Notion, Studio)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = appUrl,
                        onValueChange = { appUrl = it },
                        label = { Text("App URL (e.g. studio.youtube.com)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = appEmoji,
                        onValueChange = { appEmoji = it },
                        label = { Text("Icon Emoji") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomSandboxApp(appName, appUrl, appEmoji)
                        showInstallDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NaxSandboxPurple)
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) {
                    Text("Cancel", color = NaxTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SandboxToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeColor.copy(alpha = 0.25f) else NaxCard)
            .border(1.dp, if (active) activeColor else NaxBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (active) activeColor else NaxTextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            color = if (active) Color.White else NaxTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
