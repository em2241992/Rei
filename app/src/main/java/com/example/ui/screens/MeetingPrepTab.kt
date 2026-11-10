package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ai.MeetingGlanceSynthesis
import com.example.data.local.EntityContextWeight
import com.example.data.local.EntityType
import com.example.data.local.KnowledgeCard
import com.example.data.local.ReiTask
import com.example.ui.theme.AmberSteadyRisk
import com.example.ui.theme.CoralQ1Urgent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldAiCore
import com.example.ui.theme.TelemetryCyan

@Composable
fun MeetingPrepTab(
    allEntities: List<EntityContextWeight>,
    selectedEntity: String,
    synthesis: MeetingGlanceSynthesis?,
    checkedPrepItems: Set<String>,
    onSelectEntity: (String) -> Unit,
    onTogglePrepChecklistItem: (String) -> Unit,
    onOpenCard: (KnowledgeCard) -> Unit,
    onOpenTask: (ReiTask) -> Unit,
    onOpenEntityHub: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("meeting_prep_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1-Tap Selector for @Person or #Project
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SELECT STAKEHOLDER (@PERSON) OR INITIATIVE (#TOPIC)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allEntities.forEach { entity ->
                        val isSelected = entity.entityName.equals(selectedEntity, ignoreCase = true)
                        val isPerson = entity.entityType == EntityType.PERSON
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectEntity(entity.entityName) },
                            label = {
                                Text(
                                    text = "${entity.entityType.prefix}${entity.entityName}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isPerson) {
                                    ElectricIndigo.copy(alpha = 0.25f)
                                } else {
                                    TelemetryCyan.copy(alpha = 0.25f)
                                }
                            ),
                            modifier = Modifier.testTag("prep_selector_${entity.entityName.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        if (synthesis != null) {
            // 30-SECOND GLANCE CARD (Goal, Your Move, Watch Out Trap + TL;DR Brief)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("thirty_second_glance_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.8.dp,
                        brush = Brush.horizontalGradient(listOf(ElectricIndigo, TelemetryCyan))
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚡ 30-SECOND EXECUTIVE GLANCE CARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = synthesis.entityLabel,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = synthesis.roleSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ElectricIndigo.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, ElectricIndigo),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenEntityHub(synthesis.entityLabel) }
                            ) {
                                Text(
                                    text = "Entity Hub",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        GlanceRowBlock(
                            icon = Icons.Default.Flag,
                            accent = ElectricIndigo,
                            label = "THE GOAL",
                            body = synthesis.thirtySecondGoal
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GlanceRowBlock(
                            icon = Icons.Default.Bolt,
                            accent = EmeraldAiCore,
                            label = "YOUR MOVE (30s OPENER)",
                            body = synthesis.yourMoveAction
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GlanceRowBlock(
                            icon = Icons.Default.WarningAmber,
                            accent = CoralQ1Urgent,
                            label = "WATCH OUT TRAP",
                            body = synthesis.watchOutTrap
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "TL;DR CONTEXTUAL MEETING BRIEF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = synthesis.tldrMeetingBrief,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // LIVE PRE-MEETING CHECKLIST
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
                        Text(
                            text = "LIVE PRE-MEETING TALKING POINTS CHECKLIST",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAiCore
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        synthesis.checklistItems.forEach { item ->
                            val isChecked = checkedPrepItems.contains(item)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTogglePrepChecklistItem(item) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onTogglePrepChecklistItem(item) },
                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore)
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4-PART CONTEXTUAL SQLITE BRIEF
            val bundle = synthesis.sqlBundle

            // Part 1: 🔴 MUST DO BEFORE
            item {
                PrepSqlSectionCard(
                    badgeTitle = "1. 🔴 MUST DO BEFORE (OPEN COMMITMENTS: ${bundle.mustDoBefore.size})",
                    accentColor = CoralQ1Urgent,
                    emptyText = "Zero open task commitments waiting on you for ${synthesis.entityLabel}."
                ) {
                    bundle.mustDoBefore.forEach { task ->
                        PrepTaskClickableRow(task = task, onClick = { onOpenTask(task) })
                    }
                }
            }

            // Part 2: ⚡ YOUR MOVE (Unblockers)
            item {
                PrepSqlSectionCard(
                    badgeTitle = "2. ⚡ YOUR MOVE • BREADCRUMB UNBLOCKERS (${bundle.yourMoveUnblockers.size})",
                    accentColor = TelemetryCyan,
                    emptyText = "No paused breadcrumb tasks blocked on ${synthesis.entityLabel}."
                ) {
                    bundle.yourMoveUnblockers.forEach { task ->
                        PrepTaskClickableRow(task = task, onClick = { onOpenTask(task) })
                    }
                }
            }

            // Part 3: 🧠 MUST KNOW (Last 14 Days KnowledgeCards)
            item {
                PrepSqlSectionCard(
                    badgeTitle = "3. 🧠 MUST KNOW • LAST 14 DAYS DECISIONS (${bundle.mustKnowRecentCards.size})",
                    accentColor = ElectricIndigo,
                    emptyText = "No recent Knowledge Cards tagged with ${synthesis.entityLabel} in the last 14 days."
                ) {
                    bundle.mustKnowRecentCards.forEach { card ->
                        PrepCardClickableRow(card = card, onClick = { onOpenCard(card) })
                    }
                }
            }

            // Part 4: ⚠️ WATCH OUT (Risks & High Deferrals)
            item {
                val totalRisks = bundle.watchOutRiskCards.size + bundle.watchOutStalledTasks.size
                PrepSqlSectionCard(
                    badgeTitle = "4. ⚠️ WATCH OUT • ESCALATIONS & STALLED ITEMS ($totalRisks)",
                    accentColor = AmberSteadyRisk,
                    emptyText = "No active SLA risks or stalled tasks flagged for ${synthesis.entityLabel}."
                ) {
                    bundle.watchOutRiskCards.forEach { card ->
                        PrepCardClickableRow(card = card, onClick = { onOpenCard(card) })
                    }
                    bundle.watchOutStalledTasks.forEach { task ->
                        PrepTaskClickableRow(task = task, onClick = { onOpenTask(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceRowBlock(
    icon: ImageVector,
    accent: Color,
    label: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PrepSqlSectionCard(
    badgeTitle: String,
    accentColor: Color,
    emptyText: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = badgeTitle,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
            if (emptyText.isNotBlank()) {
                // Only shown by caller if empty, handled cleanly below
            }
        }
    }
}

@Composable
private fun PrepTaskClickableRow(
    task: ReiTask,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.quadrant.shortCode} • ${task.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (task.deferralCount > 0) {
                    Text(
                        text = "Deferred ${task.deferralCount}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberSteadyRisk
                    )
                }
            }
            if (task.breadcrumbBlocker.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Blocker: ${task.breadcrumbBlocker} → Resume: ${task.breadcrumbResumeStep}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TelemetryCyan
                )
            } else {
                Text(
                    text = task.dueDateNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrepCardClickableRow(
    card: KnowledgeCard,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (card.hasRiskOrBlocker) {
                    Text(
                        text = "⚠️ SLA Risk",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralQ1Urgent
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = card.tldrSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
