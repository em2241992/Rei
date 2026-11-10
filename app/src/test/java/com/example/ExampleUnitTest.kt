package com.example

import com.example.data.local.CardCategory
import com.example.data.local.EntityContextWeight
import com.example.data.local.EntityType
import com.example.data.local.KnowledgeCard
import com.example.data.local.ReiTask
import com.example.data.local.TaskQuadrant
import com.example.data.local.TaskStatus
import com.example.ui.screens.DeliverableHorizon
import com.example.ui.screens.DeliverableRagStatus
import com.example.ui.screens.DeliverableRunwayBuilder
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun deliverableRunwayBuilder_buildsSwimlanesRagStatusAndHorizons() {
        val cards = listOf(
            KnowledgeCard(
                id = 1L,
                title = "Epic SBO Cutover & Guarantor Split Escalation",
                category = CardCategory.MEETING_RECORD,
                tldrSummary = "SLA risk on unmapped guarantor splits.",
                outlineSectionsJson = "[]",
                peopleTags = listOf("Corrie", "Wayne"),
                topicTags = listOf("Epic SBO"),
                hasRiskOrBlocker = true,
                rawSourceText = "Cutover risk"
            )
        )
        val tasks = listOf(
            ReiTask(
                id = 10L,
                title = "Freeze legacy statement batching for 48 hours",
                quadrant = TaskQuadrant.Q1_DO_NOW,
                status = TaskStatus.ACTIVE,
                microStepsJson = "[]",
                peopleTags = listOf("Corrie", "Debbie"),
                topicTags = listOf("Epic SBO"),
                dueDateNote = "Today • Cutover"
            ),
            ReiTask(
                id = 11L,
                title = "Configure warm-transfer queue rule",
                quadrant = TaskQuadrant.Q2_PROTECT_BUILD,
                status = TaskStatus.PAUSED_BREADCRUMB,
                breadcrumbBlocker = "Waiting on @Debbie sign-off",
                microStepsJson = "[]",
                peopleTags = listOf("Wayne"),
                topicTags = listOf("Resolution Room"),
                dueDateNote = "This Week • Thursday"
            )
        )
        val entities = listOf(
            EntityContextWeight(
                entityName = "Epic SBO",
                entityType = EntityType.PROJECT,
                roleOrContext = "Single Billing Office transition"
            )
        )

        val swimlanes = DeliverableRunwayBuilder.buildInitiativeSwimlanes(
            tasks = tasks,
            cards = cards,
            entities = entities
        )

        assertEquals(2, swimlanes.size)
        val sboLane = swimlanes.first { it.topicName == "Epic SBO" }
        assertEquals(DeliverableRagStatus.RED_SLA_RISK, sboLane.ragStatus)
        assertEquals(DeliverableHorizon.NOW_TODAY, sboLane.nodes.first().horizon)
        assertTrue(sboLane.peopleInvolved.contains("Corrie"))

        val resRoomLane = swimlanes.first { it.topicName == "Resolution Room" }
        assertEquals(DeliverableRagStatus.AMBER_BLOCKED, resRoomLane.ragStatus)
        assertEquals(DeliverableHorizon.THIS_WEEK, resRoomLane.nodes.first().horizon)
    }
}


