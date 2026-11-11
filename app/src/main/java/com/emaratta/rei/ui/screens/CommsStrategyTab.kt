package com.emaratta.rei.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emaratta.rei.ai.CommsStrategyOutput
import com.emaratta.rei.data.local.BehavioralTelemetry
import com.emaratta.rei.data.local.CommsStrategyLens
import com.emaratta.rei.data.local.EntityContextWeight
import com.emaratta.rei.ui.theme.AmberSteadyRisk
import com.emaratta.rei.ui.theme.ElectricIndigo
import com.emaratta.rei.ui.theme.EmeraldAiCore
import com.emaratta.rei.ui.theme.TelemetryCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommsStrategyTab(
    selectedLens: CommsStrategyLens,
    inputText: String,
    output: CommsStrategyOutput?,
    entities: List<EntityContextWeight>,
    recentTelemetry: List<BehavioralTelemetry>,
    onSelectLens: (CommsStrategyLens) -> Unit,
    onInputTextChange: (String) -> Unit,
    onRunStudio: () -> Unit,
    onOpenVipContextSheet: () -> Unit,
    onOpenBackupSheet: () -> Unit,
    onCopyFeedback: (String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("comms_strategy_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Launchers for VIP Context Rules & Local Backup Bridge
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenVipContextSheet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comms_open_vip_rules_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VIP Rules (${entities.size})", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onOpenBackupSheet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comms_open_backup_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("JSON / SQLite Backup", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 3 Executive Lenses Selector (Grounded Comms | Pareto 80/20 | Exec Positioning Mode A & Mode B)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SKILL 5 • STRATEGY & GROUNDED COMMS STUDIO ('DRAFT IT')",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CommsStrategyLens.entries.forEach { lens ->
                            val isSelected = lens == selectedLens
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    ElectricIndigo.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ElectricIndigo else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectLens(lens) }
                                    .testTag("lens_selector_${lens.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = lens.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lens.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        label = {
                            Text("Rough Draft, Messy Operational Problem, or Win to Frame")
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comms_studio_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRunStudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_comms_studio_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Run ${selectedLens.title}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Studio Output Card
        if (output != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("comms_studio_output_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, ElectricIndigo.copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "EXECUTIVE LENS OUTPUT • ${output.lens.title.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Reflex Audit / Symptom vs Root Cause Audit
                        if (output.detectedReflexesOrAudit.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AmberSteadyRisk.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, AmberSteadyRisk.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(11.dp)) {
                                    Text(
                                        text = "DIAGNOSTIC & REFLEX AUDIT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberSteadyRisk
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    output.detectedReflexesOrAudit.forEach { line ->
                                        Text(
                                            text = "• $line",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Control & Politics Lane Audit (for Pareto 80/20)
                        if (output.laneBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "CONTROL & POLITICS AUDIT (LANE BREAKDOWN)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            output.laneBreakdown.forEach { (lane, detail) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = lane,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Output Block (Option 1 / 20% High-Leverage Move / ROI Headline / Clinical Note)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutputCopyableBlock(
                            heading = output.primaryHeading,
                            body = output.primaryBody,
                            accentColor = ElectricIndigo,
                            onCopy = {
                                copyToClipboard(context, output.primaryBody)
                                onCopyFeedback("Copied '${output.primaryHeading}' to clipboard")
                            }
                        )

                        // Secondary Output Block (Option 2 BLUF / ROI 4-Pillar Breakdown / Clinical Gate)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutputCopyableBlock(
                            heading = output.secondaryHeading,
                            body = output.secondaryBody,
                            accentColor = TelemetryCyan,
                            onCopy = {
                                copyToClipboard(context, output.secondaryBody)
                                onCopyFeedback("Copied '${output.secondaryHeading}' to clipboard")
                            }
                        )

                        // 1-Sentence Rule of Thumb or What to Stop Doing
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldAiCore.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, EmeraldAiCore.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 ${output.ruleOfThumbOrStopDoing}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Behavioral Telemetry & Learned Rhythm Log
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BEHAVIORAL TELEMETRY (LEARNED RHYTHM & OVERRIDES)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    recentTelemetry.take(6).forEach { event ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${event.eventType.label} • ${event.subjectKey}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = event.detailValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${event.hourOfDay}:00",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputCopyableBlock(
    heading: String,
    body: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Output",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Rei Executive Draft", text))
}
