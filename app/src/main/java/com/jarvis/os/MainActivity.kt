package com.jarvis.os

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.jarvis.os.data.AttachedMedia
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.ui.screens.BrainLabScreen
import com.jarvis.os.ui.screens.NaxChatScreen
import com.jarvis.os.ui.screens.RoutinesScreen
import com.jarvis.os.ui.screens.SandboxPhoneScreen
import com.jarvis.os.ui.theme.NaxBg
import com.jarvis.os.ui.theme.NaxCard
import com.jarvis.os.ui.theme.NaxCyan
import com.jarvis.os.ui.theme.NaxGreen
import com.jarvis.os.ui.theme.NaxLiveRed
import com.jarvis.os.ui.theme.NaxSandboxPurple
import com.jarvis.os.ui.theme.NaxSurface
import com.jarvis.os.ui.theme.NaxTextPrimary
import com.jarvis.os.ui.theme.NaxTextSecondary
import com.jarvis.os.ui.theme.NaxTheme
import com.jarvis.os.viewmodel.NaxUiState
import com.jarvis.os.viewmodel.NaxViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var naxViewModel: NaxViewModel
    private val capturedVoiceText = mutableStateOf("")
    private var lastPickedMime: String = "image/*"

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) capturedVoiceText.value = spoken
        }
    }

    private val mediaPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedMediaUri(uri)
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        naxViewModel = ViewModelProvider(this)[NaxViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NaxTheme {
                val state by naxViewModel.uiState.collectAsState()
                NaxRootShell(
                    state = state,
                    viewModel = naxViewModel,
                    voiceText = capturedVoiceText.value,
                    onVoiceConsumed = { capturedVoiceText.value = "" },
                    onStartVoice = { startVoiceRecognition() },
                    onPickMedia = { mime ->
                        lastPickedMime = mime
                        mediaPickerLauncher.launch(mime)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::naxViewModel.isInitialized) {
            naxViewModel.refreshPermissionsAndStatus()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "NAX ko bolo (Hindi / Hinglish / English)...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Voice recognition available nahi hai is device par.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePickedMediaUri(uri: Uri) {
        val detectedMime = contentResolver.getType(uri) ?: lastPickedMime
        val fileName = queryDisplayName(uri)
        val type = when {
            detectedMime.startsWith("image") -> "photo"
            detectedMime.startsWith("video") -> "video"
            else -> "audio"
        }

        val base64Image = if (type == "photo") encodeScaledImageBase64(uri) else ""
        naxViewModel.setPendingAttachment(
            AttachedMedia(
                type = type,
                name = fileName,
                uriString = uri.toString(),
                base64Data = base64Image,
                mimeType = if (type == "photo") "image/jpeg" else detectedMime
            )
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "media_file"
        } catch (_: Exception) {
            "media_file"
        }
    }

    private fun encodeScaledImageBase64(uri: Uri): String {
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return ""
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            if (original == null) return ""

            val maxDim = 1024
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt().coerceAtLeast(1),
                    (original.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }
}

@Composable
private fun NaxRootShell(
    state: NaxUiState,
    viewModel: NaxViewModel,
    voiceText: String,
    onVoiceConsumed: () -> Unit,
    onStartVoice: () -> Unit,
    onPickMedia: (String) -> Unit
) {
    LaunchedEffect(state.bannerMessage) {
        if (state.bannerMessage.isNotBlank()) {
            delay(3000L)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        containerColor = NaxBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(NaxSurface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(NaxSandboxPurple.copy(alpha = 0.22f))
                            .border(1.dp, NaxCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("NAX", color = NaxTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        val (modeLabel, modeColor) = when (state.executionMode) {
                            ExecutionMode.AUTO -> "Auto Smart Mode Active" to NaxCyan
                            ExecutionMode.SANDBOX -> "Sandbox Mode Active" to NaxSandboxPurple
                            ExecutionMode.LIVE -> "Live Phone Mode Active" to NaxLiveRed
                        }
                        Text(modeLabel, color = modeColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.weight(1f))

                    // Offline Voice Output Speaker Toggle
                    IconButton(
                        onClick = { viewModel.toggleVoiceOutput() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (state.voiceOutputEnabled) NaxCyan.copy(alpha = 0.18f) else NaxCard)
                    ) {
                        Icon(
                            imageVector = if (state.voiceOutputEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = "Voice Output",
                            tint = if (state.voiceOutputEnabled) NaxCyan else NaxTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // 24/7 Companion + Floating Orb Power Toggle
                    IconButton(
                        onClick = { viewModel.toggleForegroundEngine() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (state.engineRunning) NaxGreen.copy(alpha = 0.2f) else NaxCard)
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = "24/7 Companion",
                            tint = if (state.engineRunning) NaxGreen else NaxTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (state.bannerMessage.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NaxCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(state.bannerMessage, color = NaxCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = NaxSurface, tonalElevation = 0.dp) {
                val navItems = listOf(
                    Triple(0, "NAX", Icons.Rounded.AutoAwesome),
                    Triple(1, "Sandbox", Icons.Rounded.PhoneAndroid),
                    Triple(2, "Routines", Icons.Rounded.Schedule),
                    Triple(3, "Brain", Icons.Rounded.Memory)
                )
                navItems.forEach { (idx, label, icon) ->
                    NavigationBarItem(
                        selected = state.activeTab == idx,
                        onClick = { viewModel.selectTab(idx) },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NaxCyan,
                            selectedTextColor = NaxCyan,
                            indicatorColor = NaxSandboxPurple.copy(alpha = 0.2f),
                            unselectedIconColor = NaxTextSecondary,
                            unselectedTextColor = NaxTextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                0 -> NaxChatScreen(
                    state = state,
                    viewModel = viewModel,
                    voiceText = voiceText,
                    onVoiceConsumed = onVoiceConsumed,
                    onVoiceClick = onStartVoice,
                    onPickMedia = onPickMedia
                )
                1 -> SandboxPhoneScreen(state = state, viewModel = viewModel)
                2 -> RoutinesScreen(state = state, viewModel = viewModel)
                3 -> BrainLabScreen(state = state, viewModel = viewModel)
            }
        }
    }
}
