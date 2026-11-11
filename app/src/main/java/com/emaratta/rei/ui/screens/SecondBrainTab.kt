package com.emaratta.rei.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emaratta.rei.data.local.CardCategory
import com.emaratta.rei.data.local.EntityContextWeight
import com.emaratta.rei.data.local.EntityType
import com.emaratta.rei.data.local.KnowledgeCard
import com.emaratta.rei.data.local.ReiTask
import com.emaratta.rei.ui.components.EntityPillsRow
import com.emaratta.rei.ui.theme.AmberSteadyRisk
import com.emaratta.rei.ui.theme.CoralQ1Urgent
import com.emaratta.rei.ui.theme.ElectricIndigo
import com.emaratta.rei.ui.theme.EmeraldAiCore
import com.emaratta.rei.ui.theme.TelemetryCyan

@Composable
fun SecondBrainTab(
    allCards: List<KnowledgeCard>,
    searchedCards: List<KnowledgeCard>?,
    allTasks: List<ReiTask>,
    allEntities: List<EntityContextWeight>,
    ftsSearchQuery: String,
    selectedPersonFilter: String?,
    selectedTopicFilter: String?,
    selectedCategoryFilter: CardCategory?,
    onSearchQueryChange: (String) -> Unit,
    onSelectPersonFilter: (String?) -> Unit,
    onSelectTopicFilter: (String?) -> Unit,
    onSelectCategoryFilter: (CardCategory?) -> Unit,
    onTogglePinCard: (KnowledgeCard) -> Unit,
    onEditCard: (KnowledgeCard) -> Unit,
    onDraftItFromCard: (KnowledgeCard) -> Unit,
    onEntityClick: (String) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onExtractBulletsAsTasks: (KnowledgeCard) -> Unit = {}
) {
    // Snapshot Mode defaults to true so cards start compact & collapsible
    var snapshotMode by remember { mutableStateOf(true) }
    val expandedCardIds = remember { mutableStateMapOf<Long, Boolean>() }

    val baseCards = searchedCards ?: allCards
    val filteredCards = remember(
        baseCards,
        selectedPersonFilter,
        selectedTopicFilter,
        selectedCategoryFilter
    ) {
        baseCards.filter { card ->
            val personMatch = selectedPersonFilter == null ||
                card.peopleTags.any { it.equals(selectedPersonFilter, ignoreCase = true) }
            val topicMatch = selectedTopicFilter == null ||
                card.topicTags.any { it.equals(selectedTopicFilter, ignoreCase = true) }
            val categoryMatch = selectedCategoryFilter == null ||
                card.category == selectedCategoryFilter
            personMatch && topicMatch && categoryMatch
        }
    }

    val peopleEntities = remember(allEntities) {
        allEntities.filter { it.entityType == EntityType.PERSON }.map { it.entityName }
    }
    val topicEntities = remember(allEntities) {
        allEntities.filter { it.entityType != EntityType.PERSON }.map { it.entityName }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("second_brain_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Instant SQLite FTS Search Bar + Snapshot Summary Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = ftsSearchQuery,
                    onValueChange = onSearchQueryChange,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "FTS Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (ftsSearchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search"
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TelemetryCyan.copy(alpha = 0.16f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "FTS Index",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Instant FTS search across cards, outlines, @People & #Topics…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("second_brain_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal Filter Pills for @People, #Topics, and Card Categories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedPersonFilter != null || selectedTopicFilter != null || selectedCategoryFilter != null) {
                        FilterChip(
                            selected = true,
                            onClick = {
                                onSelectPersonFilter(null)
                                onSelectTopicFilter(null)
                                onSelectCategoryFilter(null)
                            },
                            label = { Text("Reset Filters", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    peopleEntities.forEach { person ->
                        val selected = selectedPersonFilter.equals(person, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPersonFilter(person) },
                            label = { Text("@$person", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo.copy(alpha = 0.25f)
                            )
                        )
                    }

                    topicEntities.forEach { topic ->
                        val selected = selectedTopicFilter.equals(topic, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTopicFilter(topic) },
                            label = { Text("#$topic", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryCyan.copy(alpha = 0.25f)
                            )
                        )
                    }

                    CardCategory.entries.forEach { cat ->
                        val selected = selectedCategoryFilter == cat
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCategoryFilter(cat) },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (filteredCards.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Knowledge Cards Match Your Filter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clear the search filter or use the Quick Dump Bar below to auto-structure a new thought.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredCards, key = { it.id }) { card ->
                val linkedTasks = remember(allTasks, card.id) {
                    allTasks.filter { it.linkedCardId == card.id }
                }
                val isCardExpanded = expandedCardIds[card.id] ?: !snapshotMode
                KnowledgeCardItem(
                    card = card,
                    linkedTasks = linkedTasks,
                    isCardExpanded = isCardExpanded,
                    onToggleCardExpand = {
                        expandedCardIds[card.id] = !isCardExpanded
                    },
                    onTogglePin = { onTogglePinCard(card) },
                    onEditCard = { onEditCard(card) },
                    onDraftIt = { onDraftItFromCard(card) },
                    onExtractBulletsAsTasks = { onExtractBulletsAsTasks(card) },
                    onEntityClick = onEntityClick,
                    onEditTask = onEditTask
                )
            }
        }
    }
}

@Composable
fun KnowledgeCardItem(
    card: KnowledgeCard,
    linkedTasks: List<ReiTask>,
    isCardExpanded: Boolean = true,
    onToggleCardExpand: () -> Unit = {},
    onTogglePin: () -> Unit,
    onEditCard: () -> Unit,
    onDraftIt: () -> Unit,
    onExtractBulletsAsTasks: () -> Unit = {},
    onEntityClick: (String) -> Unit,
    onEditTask: (ReiTask) -> Unit
) {
    var outlinesExpanded by remember { mutableStateOf(true) }
    var showRawSource by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggleCardExpand() }
            .testTag("knowledge_card_${card.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Category Badge + Pin + Edit + Draft It + Collapse Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElectricIndigo.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${card.category.badgeCode} • ${card.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (card.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📌 PINNED",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberSteadyRisk
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${card.outlineSections.size} sec • ${linkedTasks.size} tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExtractBulletsAsTasks,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("card_extract_tasks_${card.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = "Split Card Bullets into Separate Matrix Tasks",
                            tint = EmeraldAiCore,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDraftIt,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("card_draft_it_${card.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RateReview,
                            contentDescription = "Launch Draft It Studio for Card",
                            tint = ElectricIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onEditCard,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("card_edit_${card.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Tap to Edit Knowledge Card",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("card_pin_${card.id}")
                    ) {
                        Icon(
                            imageVector = if (card.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin or Unpin Knowledge Card",
                            tint = if (card.isPinned) AmberSteadyRisk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand or Collapse Knowledge Card",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // AI-Generated Title
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Compact 2-Line Summary + Max 3 Visual Anchor Tags when collapsed
            if (!isCardExpanded) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = card.tldrSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                val anchorTags = remember(card, linkedTasks) {
                    buildList {
                        linkedTasks.firstOrNull { it.dueDateNote.isNotBlank() }?.let { t ->
                            val cleanDue = t.dueDateNote.removePrefix("📅 ").trim()
                            add("📅 $cleanDue" to CoralQ1Urgent)
                        }
                        if (card.hasRiskOrBlocker) {
                            val waitingPerson = card.peopleTags.firstOrNull()?.let { "@$it" } ?: "Blocker"
                            add("⏳ Waiting On: $waitingPerson" to AmberSteadyRisk)
                        }
                        card.peopleTags.forEach { p ->
                            add("@$p" to ElectricIndigo)
                        }
                        card.topicTags.forEach { t ->
                            add("#$t" to TelemetryCyan)
                        }
                    }
                }
                val visibleTags = anchorTags.take(3)
                val overflowCount = (anchorTags.size - 3).coerceAtLeast(0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTags.forEach { (label, accent) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (label.startsWith("@") || label.startsWith("#")) {
                                        onEntityClick(label)
                                    } else {
                                        onToggleCardExpand()
                                    }
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (overflowCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleCardExpand() }
                        ) {
                            Text(
                                text = "+$overflowCount more",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Full Expanded Card Content
            AnimatedVisibility(visible = isCardExpanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    // 2-Sentence Executive Summary Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "EXECUTIVE SUMMARY (2-SENTENCE BLUF)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = card.tldrSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tappable Bi-Directional Entity Pills (@People & #Topics)
                    EntityPillsRow(
                        peopleTags = card.peopleTags,
                        topicTags = card.topicTags,
                        hasRiskOrBlocker = card.hasRiskOrBlocker,
                        onEntityClick = onEntityClick
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1-Tap Action to Convert Card Bullets into Separate Matrix Tasks
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldAiCore.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldAiCore.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onExtractBulletsAsTasks() }
                            .testTag("card_convert_bullets_to_tasks_banner_${card.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = null,
                                tint = EmeraldAiCore,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔀 Split Card Bullets into Separate Matrix Tasks",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAiCore
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Collapsible Natural Hierarchical Outlines
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { outlinesExpanded = !outlinesExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NATURAL HIERARCHICAL OUTLINE (${card.outlineSections.size} SECTIONS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(
                            imageVector = if (outlinesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand or Collapse Natural Outline",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = outlinesExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            card.outlineSections.forEach { section ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = section.heading,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        section.bullets.forEach { bullet ->
                                            Row(
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 6.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = bullet,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        if (!section.annotation.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AmberSteadyRisk.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, AmberSteadyRisk.copy(alpha = 0.45f))
                                            ) {
                                                Text(
                                                    text = section.annotation,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Linked Auto-Spawned ReiTask Action Items
                    if (linkedTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AUTO-SPAWNED ACTION ITEMS (${linkedTasks.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAiCore
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        linkedTasks.forEach { task ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onEditTask(task) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${task.quadrant.shortCode} • ${task.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${task.status.label} • ${task.dueDateNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = "Edit Linked Task",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Raw Source Dump Reference Toggle
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showRawSource) "Hide Raw Capture Source" else "View Raw Capture Source",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { showRawSource = !showRawSource }
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            text = "Tap-to-Edit Card",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onEditCard() }
                                .padding(vertical = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = showRawSource) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = card.rawSourceText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
