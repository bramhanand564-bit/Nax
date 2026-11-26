package com.jarvis.os.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.os.data.RoutineFlow
import com.jarvis.os.data.SkillItem
import com.jarvis.os.ui.components.ModeBadge
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
fun RoutinesScreen(
    state: NaxUiState,
    viewModel: NaxViewModel
) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NaxBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Segmented Switch: Saved n8n Routines | Learned Skills Shelf
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NaxCard)
                .border(1.dp, NaxBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SubTabChip(
                label = "Daily Routines (${state.routines.size})",
                selected = subTab == 0,
                accent = NaxCyan,
                modifier = Modifier.weight(1f),
                onClick = { subTab = 0 }
            )
            SubTabChip(
                label = "Learned Skills (${state.skills.size})",
                selected = subTab == 1,
                accent = NaxSandboxPurple,
                modifier = Modifier.weight(1f),
                onClick = { subTab = 1 }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (subTab == 0) {
            if (state.routines.isEmpty()) {
                EmptyRoutinesState(onGoToChat = { viewModel.selectTab(0) })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.routines, key = { it.id }) { flow ->
                        SavedRoutineCard(
                            flow = flow,
                            onToggle = { viewModel.toggleRoutineEnabled(flow.id) },
                            onDelete = { viewModel.deleteRoutine(flow.id) }
                        )
                    }
                }
            }
        } else {
            // Note that new GitHub skills are installed right inside NAX Chat
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NaxSandboxPurple.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, NaxSandboxPurple.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NaxSandboxPurple, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Naya GitHub skill install karne ke liye seedha NAX Chat mein GitHub repo link paste karein.",
                        color = NaxTextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.skills, key = { it.id }) { skill ->
                    InstalledSkillRow(
                        skill = skill,
                        onToggle = { viewModel.toggleSkillEnabled(skill.id) },
                        onDelete = { viewModel.deleteSkill(skill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubTabChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) NaxTextPrimary else NaxTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyRoutinesState(onGoToChat: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCard),
        border = BorderStroke(1.dp, NaxBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("No Daily Routines Yet", color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "NAX Chat mein bas bol do:\n\"Roz subah 9 baje Sandbox mein YouTube check karna\"\nWo automatically n8n-style daily routine ban kar yahan auto-save ho jayega!",
                color = NaxTextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onGoToChat,
                colors = ButtonDefaults.buttonColors(containerColor = NaxCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Routine in NAX Chat", color = Color(0xFF0A0D16), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SavedRoutineCard(
    flow: RoutineFlow,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCard),
        border = BorderStroke(1.dp, if (flow.enabled) NaxCyan.copy(alpha = 0.5f) else NaxBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = NaxCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(flow.title, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${flow.frequency} • ${flow.triggerTime}", color = NaxCyan, fontSize = 11.sp)
                }
                Switch(
                    checked = flow.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaxCyan)
                )
            }

            Spacer(Modifier.height(10.dp))

            // n8n-Style Visual Step Chain
            flow.steps.forEachIndexed { idx, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NaxCardElevated)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeBadge(mode = step.mode)
                    Spacer(Modifier.width(8.dp))
                    Text(step.command, color = NaxTextPrimary, fontSize = 12.sp, maxLines = 2)
                }
                if (idx < flow.steps.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = NaxTextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = flow.lastRunStatus,
                    color = if (flow.enabled) NaxGreen else NaxTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = NaxLiveRed.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun InstalledSkillRow(
    skill: SkillItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NaxCard),
        border = BorderStroke(1.dp, if (skill.isNew) NaxGreen.copy(alpha = 0.55f) else NaxBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = NaxSandboxPurple, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(skill.name, color = NaxTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (skill.isNew) {
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
                    Spacer(Modifier.width(6.dp))
                }
                ModeBadge(mode = skill.mode)
            }
            Spacer(Modifier.height(4.dp))
            Text(skill.description, color = NaxTextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            if (skill.repoUrl.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(skill.repoUrl, color = NaxCyan, fontSize = 10.sp, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (skill.isCore) "Core NAX Skill" else "GitHub Installed Skill",
                    color = NaxTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!skill.isCore) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove Skill", tint = NaxLiveRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NaxSandboxPurple)
                )
            }
        }
    }
}
