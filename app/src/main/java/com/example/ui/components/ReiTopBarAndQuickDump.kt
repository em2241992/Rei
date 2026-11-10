package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AiEngineDiagnostics
import com.example.ai.EngineRuntimeStatus
import com.example.ai.WhereWasIBannerState
import com.example.data.local.EnergyBandwidthState
import com.example.data.local.ReiTask
import com.example.ui.theme.AmberSteadyRisk
import com.example.ui.theme.CoralQ1Urgent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldAiCore
import com.example.ui.theme.TelemetryCyan

/**
 * 1. SINGLE-ROW CONSOLIDATED TOP APP BAR
 * - Left: `Rei` + compact engine status pill (`🟢 Nano` / `🔵 Sim`).
 * - Right: `"↺ Refocus"` pill button, `📅` Calendar Check icon, Theme toggle, and Overflow menu.
 */
@Composable
fun ReiExecutiveTopBar(
    engineStatus: EngineRuntimeStatus,
    diagnostics: AiEngineDiagnostics,
    aiCoreDownloadProgress: String? = null,
    isDarkTheme: Boolean,
    showWhereWasI: Boolean,
    onToggleEngineMode: () -> Unit,
    onToggleWhereWasI: () -> Unit,
    onOpenCalendarCheck: () -> Unit = {},
    onOpenVipRules: () -> Unit,
    onOpenBackupBridge: () -> Unit,
    onOpenSelfTestInspector: () -> Unit = {},
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var showAiStatusDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onOpenCalendarCheck()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT: App Icon + "Rei" Title + Compact Status Pill (🟢 Nano or 🔵 Sim)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricIndigo, TelemetryCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Rei Neural Emblem",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Text(
                        text = "Rei",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val pillColor = if (engineStatus.isHardwareNano) EmeraldAiCore else TelemetryCyan
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillColor.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showAiStatusDialog = true }
                            .testTag("engine_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (engineStatus.isHardwareNano) "🟢 Nano" else "🔵 Sim",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // RIGHT: "↺ Refocus" Pill + 📅 Calendar Check Icon + Theme Toggle + Overflow Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (showWhereWasI) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onToggleWhereWasI() }
                            .testTag("where_was_i_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "↺ Refocus",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showWhereWasI) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1
                            )
                        }
                    }

                    // 📅 Calendar Check Action (Step 2 Conditional Calendar Review)
                    IconButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CALENDAR
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                onOpenCalendarCheck()
                            } else {
                                runCatching {
                                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }.onFailure {
                                    onOpenCalendarCheck()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("calendar_check_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendar Check (Northwell / Device Meetings)",
                            tint = TelemetryCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Light or Dark Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("top_bar_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Settings, Learned Rules & Backup Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📅 Calendar Check (Northwell)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = TelemetryCyan
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenCalendarCheck()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Learned & VIP Rules") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = ElectricIndigo
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenVipRules()
                                },
                                modifier = Modifier.testTag("open_vip_rules_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Backup, Restore & Start Fresh") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Backup,
                                        contentDescription = null,
                                        tint = TelemetryCyan
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenBackupBridge()
                                },
                                modifier = Modifier.testTag("open_backup_bridge_button")
                            )
                            DropdownMenuItem(
                                text = { Text("AI Status & S25U Self-Test") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = null,
                                        tint = EmeraldAiCore
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenSelfTestInspector()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (engineStatus.isHardwareNano) {
                                            "Switch to Sim Engine"
                                        } else {
                                            "Switch to S25U Nano Mode"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleEngineMode()
                                }
                            )
                        }
                    }
                }
            }

            if (!aiCoreDownloadProgress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberSteadyRisk.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⬇️ $aiCoreDownloadProgress",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberSteadyRisk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showAiStatusDialog) {
        AlertDialog(
            onDismissRequest = { showAiStatusDialog = false },
            title = {
                Column {
                    Text(
                        text = "ON-DEVICE AI ENGINE & LEARNED RULES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (engineStatus.isHardwareNano) "🟢 S25U Gemini Nano Active" else "🔵 Deterministic Sim Engine Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Status: ${diagnostics.rawFeatureStatusCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Last Skill: ${diagnostics.lastSkillExecuted} • ~${diagnostics.lastPromptTokenEstimate} tokens • ${diagnostics.lastLatencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onToggleEngineMode()
                                showAiStatusDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⇄ Toggle Mode", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                showAiStatusDialog = false
                                onOpenVipRules()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚙️ Learned Rules", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showAiStatusDialog = false
                            onOpenSelfTestInspector()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_s25u_selftest_inspector_button")
                    ) {
                        Text("🔬 Open S25U AICore Self-Test", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiStatusDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Step 6 — `↺ Refocus` Banner:
 * Restates the current Top Task, reminds the user of recent context/breadcrumbs,
 * and points directly to the current 2-minute micro-step to restore working memory after an interruption.
 */
@Composable
fun WhereWasICollapsibleBanner(
    visible: Boolean,
    state: WhereWasIBannerState,
    onResumeTask: (ReiTask) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("where_was_i_banner"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "↺ REFOCUS • WORKING MEMORY RESTORED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Refocus Banner",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                state.lastActiveTask?.let { active ->
                    Text(
                        text = "🎯 Top Task: ${active.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (state.pausedBreadcrumbs.isNotEmpty()) {
                    val bc = state.pausedBreadcrumbs.first()
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "🍞 Stuck Because: ${bc.breadcrumbBlocker} (Stopped at: ${bc.breadcrumbStoppedAt})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.thirtySecondResumePrompt,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        val targetTask = state.pausedBreadcrumbs.firstOrNull() ?: state.lastActiveTask
                        if (targetTask != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onResumeTask(targetTask) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("resume_where_was_i_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. SLEEK SINGLE-ROW BANDWIDTH BAR:
 * `[ ⚡ Peak ]  [ ● Standard ]  [ 🟢 Quick Win ]`
 */
@Composable
fun EnergyBandwidthSelectorBar(
    selectedBandwidth: EnergyBandwidthState,
    learnedRhythmHint: String,
    onSelectBandwidth: (EnergyBandwidthState) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EnergyBandwidthState.entries.forEach { state ->
                val isSelected = state == selectedBandwidth
                val accent = when (state) {
                    EnergyBandwidthState.SHARP -> ElectricIndigo
                    EnergyBandwidthState.STEADY -> TelemetryCyan
                    EnergyBandwidthState.FRIED -> EmeraldAiCore
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectBandwidth(state) }
                        .testTag("bandwidth_selector_${state.name.lowercase()}"),
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.65f)) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${state.emoji} ${state.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. BOTTOM INPUT BAR:
 * - When empty, show ONLY `[ 🎤 ]` and a full-width pill (`"Unload thoughts, rough notes, or tasks..."`).
 * - When text is entered, reveal `[ 🅿️ Stash ]` (logs without disrupting focus) and `[ ✨ Organize ]` (runs the 6-step Rei pipeline).
 */
@Composable
fun PersistentQuickDumpBar(
    text: String,
    isProcessing: Boolean,
    onTextChange: (String) -> Unit,
    onOrganizeClick: () -> Unit,
    onParkItClick: () -> Unit,
    onAskMoreDetailClick: () -> Unit = {},
    onOpenVoicePresets: () -> Unit,
    onOpenTypeComposer: () -> Unit,
    onVoiceResult: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val hasText = text.isNotBlank()
    val showHelperTray = isFocused || hasText

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onVoiceResult(spoken)
            } else {
                onOpenVoicePresets()
            }
        } else {
            onOpenVoicePresets()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Inline slide-up helper tray when focused or typing
            AnimatedVisibility(
                visible = showHelperTray,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AmberSteadyRisk.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onAskMoreDetailClick() }
                            .testTag("quick_dump_ask_detail_button")
                    ) {
                        Text(
                            text = "❓ Clarify",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberSteadyRisk,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenVoicePresets() }
                            .testTag("voice_preset_link")
                    ) {
                        Text(
                            text = "🎙️ Presets",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenTypeComposer() }
                            .testTag("open_type_composer_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Full Editor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    listOf("📅 Due: Today", "⏳ Waiting On: @Corrie", "@Debbie", "@Wayne", "#Epic SBO").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val spacer = if (text.isEmpty() || text.endsWith(" ")) "" else " "
                                    onTextChange("$text$spacer$tag ")
                                }
                        ) {
                            Text(
                                text = "+ $tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // SINGLE-ROW CAPTURE BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // [ 🎤 ] Mic Button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Unload thoughts, rough notes, or tasks…"
                                )
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            onOpenVoicePresets()
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("quick_dump_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Dictation or Sample Transcripts",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Full-width pill when empty: "Unload thoughts, rough notes, or tasks..."
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isFocused) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Unload thoughts, rough notes, or tasks...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .testTag("quick_dump_input")
                            )
                        }
                        if (hasText) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear typed text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onTextChange("") }
                            )
                        }
                    }
                }

                // Reveal `[ 🅿️ Stash ]` and `[ ✨ Organize ]` ONLY when text is entered (or processing)
                AnimatedVisibility(
                    visible = hasText || isProcessing,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onParkItClick() }
                                .testTag("quick_dump_park_button")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🅿️ Stash",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = onOrganizeClick,
                            enabled = !isProcessing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("quick_dump_organize_button")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "✨ Organize",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityPillsRow(
    peopleTags: List<String>,
    topicTags: List<String>,
    hasRiskOrBlocker: Boolean = false,
    onEntityClick: (String) -> Unit
) {
    if (peopleTags.isEmpty() && topicTags.isEmpty() && !hasRiskOrBlocker) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (hasRiskOrBlocker) {
            Surface(
                shape = RoundedCornerShape(50),
                color = CoralQ1Urgent.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "SLA Risk or Blocker Flagged",
                        tint = CoralQ1Urgent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SLA / Blocker",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CoralQ1Urgent
                    )
                }
            }
        }

        peopleTags.forEach { person ->
            val clean = person.removePrefix("@")
            Surface(
                shape = RoundedCornerShape(50),
                color = ElectricIndigo.copy(alpha = 0.14f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onEntityClick("@$clean") }
                    .testTag("entity_pill_person_${clean.lowercase()}")
            ) {
                Text(
                    text = "@$clean",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        topicTags.forEach { topic ->
            val clean = topic.removePrefix("#")
            Surface(
                shape = RoundedCornerShape(50),
                color = TelemetryCyan.copy(alpha = 0.14f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onEntityClick("#$clean") }
                    .testTag("entity_pill_topic_${clean.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = "#$clean",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
