package com.emaratta.rei.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.emaratta.rei.ai.AiEngineDiagnostics
import com.emaratta.rei.data.local.AiBackendConfig
import com.emaratta.rei.data.local.AiBackendMode
import com.emaratta.rei.ui.theme.AmberSteadyRisk
import com.emaratta.rei.ui.theme.CoralQ1Urgent
import com.emaratta.rei.ui.theme.ElectricIndigo
import com.emaratta.rei.ui.theme.EmeraldAiCore
import com.emaratta.rei.ui.theme.TelemetryCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReiProductionSettingsDialog(
    backendConfig: AiBackendConfig,
    diagnostics: AiEngineDiagnostics,
    debugSandboxMode: Boolean,
    isProcessing: Boolean,
    cardCount: Int,
    taskCount: Int,
    entityCount: Int,
    onDismiss: () -> Unit,
    onSaveBackendConfig: (AiBackendConfig, Boolean) -> Unit,
    onToggleDebugSandboxMode: (Boolean) -> Unit,
    onExportJsonUri: (Uri) -> Unit,
    onImportJsonUri: (Uri) -> Unit,
    onExportSqliteUri: (Uri) -> Unit,
    onRestoreSqliteUri: (Uri) -> Unit,
    onPurgeAllData: () -> Unit,
    onOpenSelfTestInspector: () -> Unit
) {
    val context = LocalContext.current

    var selectedMode by remember(backendConfig) { mutableStateOf(backendConfig.mode) }
    var litertUri by remember(backendConfig) { mutableStateOf(backendConfig.litertModelUri) }
    var litertName by remember(backendConfig) { mutableStateOf(backendConfig.litertModelName) }
    var localUrl by remember(backendConfig) { mutableStateOf(backendConfig.localEndpointUrl) }
    var localModel by remember(backendConfig) { mutableStateOf(backendConfig.localModelName) }
    var cloudApiKey by remember(backendConfig) { mutableStateOf(backendConfig.cloudGeminiApiKey) }
    var cloudModel by remember(backendConfig) { mutableStateOf(backendConfig.cloudGeminiModel) }
    var showApiKey by remember { mutableStateOf(false) }

    val litertPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            var resolvedName = uri.lastPathSegment?.substringAfterLast('/') ?: "model.litertlm"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        resolvedName = cursor.getString(nameIdx) ?: resolvedName
                    }
                }
            }
            litertUri = uri.toString()
            litertName = resolvedName
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportJsonUri(uri) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportJsonUri(uri) }

    val exportSqliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) onExportSqliteUri(uri) }

    val importSqliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onRestoreSqliteUri(uri) }

    fun currentEditedConfig(): AiBackendConfig = AiBackendConfig(
        mode = selectedMode,
        litertModelUri = litertUri.trim(),
        litertModelName = litertName.trim(),
        localEndpointUrl = localUrl.trim().ifBlank { "http://127.0.0.1:11434" },
        localModelName = localModel.trim().ifBlank { "llama3.2" },
        cloudGeminiApiKey = cloudApiKey.trim(),
        cloudGeminiModel = cloudModel.trim().ifBlank { "gemini-2.5-flash" }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "REI PRODUCTION SETTINGS • S25 ULTRA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElectricIndigo
                )
                Text(
                    text = "AI Backends, Backup & Sandbox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. LIVE RUNTIME STATUS SUMMARY
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Active Runtime: ${diagnostics.status.badgeText}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = diagnostics.rawFeatureStatusCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "SQLite DB (rei_cognitive_hub.db): $cardCount Cards • $taskCount Tasks • $entityCount VIP Weights",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    }
                }

                // 2. 3-BACKEND AI EXECUTION SELECTOR (+ OFFLINE HEURISTIC)
                Text(
                    text = "1. SELECT AI EXECUTION BACKEND",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                AiBackendMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val icon: ImageVector = when (mode) {
                        AiBackendMode.AICORE_NANO -> Icons.Default.Memory
                        AiBackendMode.LOCAL_LOOPBACK_LAN -> Icons.Default.Lan
                        AiBackendMode.CLOUD_GEMINI_REST -> Icons.Default.Cloud
                        AiBackendMode.OFFLINE_HEURISTIC -> Icons.Default.Rule
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ElectricIndigo.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricIndigo else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedMode = mode }
                            .testTag("ai_backend_option_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) ElectricIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected Backend",
                                    tint = EmeraldAiCore,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // BACKEND-SPECIFIC CONFIGURATION FIELDS
                when (selectedMode) {
                    AiBackendMode.AICORE_NANO -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldAiCore.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, EmeraldAiCore.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ON-DEVICE AICORE NANO & OPTIONAL .LITERTLM BUNDLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAiCore
                                )
                                Text(
                                    text = if (litertName.isNotBlank()) {
                                        "Mounted .litertlm bundle: $litertName"
                                    } else {
                                        "Uses Android AICore (ML Kit GenAI Prompt API) automatically, or mount a local .litertlm bundle via SAF:"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { litertPickerLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pick_litertlm_bundle_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick .litertlm File", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (litertUri.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                litertUri = ""
                                                litertName = ""
                                            }
                                        ) {
                                            Text("Clear", color = CoralQ1Urgent)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AiBackendMode.LOCAL_LOOPBACK_LAN -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TelemetryCyan.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "LOCAL LOOPBACK / LAN (OLLAMA OR OPENAI HTTP)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TelemetryCyan
                                )
                                OutlinedTextField(
                                    value = localUrl,
                                    onValueChange = { localUrl = it },
                                    label = { Text("Endpoint URL") },
                                    placeholder = { Text("http://127.0.0.1:11434") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_lan_url_input")
                                )
                                OutlinedTextField(
                                    value = localModel,
                                    onValueChange = { localModel = it },
                                    label = { Text("Model Name") },
                                    placeholder = { Text("llama3.2") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_lan_model_input")
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("llama3.2", "qwen2.5:3b", "gemma2:2b", "phi3.5").forEach { m ->
                                        FilterChip(
                                            selected = localModel == m,
                                            onClick = { localModel = m },
                                            label = { Text(m, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AiBackendMode.CLOUD_GEMINI_REST -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricIndigo.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CLOUD GEMINI REST API (GENERATIVELANGUAGE.GOOGLEAPIS.COM)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricIndigo
                                )
                                OutlinedTextField(
                                    value = cloudApiKey,
                                    onValueChange = { cloudApiKey = it },
                                    label = { Text("Gemini API Key") },
                                    placeholder = { Text("AIza...") },
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showApiKey = !showApiKey }) {
                                            Icon(
                                                imageVector = if (showApiKey) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                                contentDescription = "Show or Hide API Key"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cloud_gemini_api_key_input")
                                )
                                OutlinedTextField(
                                    value = cloudModel,
                                    onValueChange = { cloudModel = it },
                                    label = { Text("Gemini Model ID") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("cloud_gemini_model_input")
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash").forEach { m ->
                                        FilterChip(
                                            selected = cloudModel == m,
                                            onClick = { cloudModel = m },
                                            label = { Text(m, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AiBackendMode.OFFLINE_HEURISTIC -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "100% air-gapped LocalSkillRouter decomposition + adaptive SQLite entity weights. Zero network or NPU overhead.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSaveBackendConfig(currentEditedConfig(), true) },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_and_probe_backend_button")
                    ) {
                        Text("Save & Probe Backend", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveBackendConfig(currentEditedConfig(), false)
                            onDismiss()
                            onOpenSelfTestInspector()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Diagnostics", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // 3. SAF BACKUP & RESTORE (rei_backup.json & rei_cognitive_hub.db)
                Text(
                    text = "2. SAF BACKUP & RESTORE (ZERO DATA LOSS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TelemetryCyan
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportJsonLauncher.launch("rei_backup.json") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_export_json_button")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_restore_json_button")
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore JSON", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportSqliteLauncher.launch("rei_cognitive_hub.db") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export .db", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { importSqliteLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Restore .db", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // 4. DEBUG SANDBOX MODE (DEFAULT: OFF) & WORKSPACE WIPE
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberSteadyRisk.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, AmberSteadyRisk.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Debug Sandbox Mode (Default: OFF)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "When OFF, Rei boots completely clean with zero demo tasks or mock meetings. Toggle ON only to test sample datasets.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = debugSandboxMode,
                                onCheckedChange = { enabled -> onToggleDebugSandboxMode(enabled) },
                                modifier = Modifier.testTag("debug_sandbox_switch")
                            )
                        }

                        OutlinedButton(
                            onClick = onPurgeAllData,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralQ1Urgent),
                            border = BorderStroke(1.dp, CoralQ1Urgent.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purge_all_workspace_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe All Workspace Data", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBackendConfig(currentEditedConfig(), false)
                    onDismiss()
                },
                modifier = Modifier.testTag("settings_save_close_button")
            ) {
                Text("Save & Done")
            }
        }
    )
}
