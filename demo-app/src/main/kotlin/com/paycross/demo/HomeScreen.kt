package com.paycross.demo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.paycross.sdk.PayCrossResult
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: DemoUiState,
    onSelectMerchant: (String) -> Unit,
    onManageMerchants: () -> Unit,
    onAddScenario: () -> Unit,
    onEditScenario: (String) -> Unit,
    onDuplicateScenario: (String) -> Unit,
    onDeleteScenario: (String) -> Unit,
    onRunScenario: (Scenario) -> Unit,
    onRunExternally: (Scenario, Boolean, String, (String) -> Unit) -> Unit,
    onDismissExternalRun: () -> Unit,
    onOpenHistory: () -> Unit,
    onClearResult: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var qrUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PayCross Harness") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Run history")
                    }
                    IconButton(onClick = onManageMerchants) {
                        Icon(Icons.Default.Settings, contentDescription = "Merchants")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedMerchant != null) {
                FloatingActionButton(onClick = onAddScenario) {
                    Icon(Icons.Default.Add, contentDescription = "Add scenario")
                }
            }
        }
    ) { padding ->
        val merchant = uiState.selectedMerchant
        if (merchant == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EmptyState(
                    message = "No merchants configured",
                    actionLabel = "Add merchant",
                    onAction = onManageMerchants
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                MerchantSelector(
                    merchants = uiState.data.merchants,
                    selected = merchant,
                    onSelect = onSelectMerchant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.selectedScenarios, key = { it.id }) { scenario ->
                        ScenarioRow(
                            scenario = scenario,
                            isRunning = uiState.isRunning,
                            onRun = { onRunScenario(scenario) },
                            onOpenInBrowser = {
                                onRunExternally(scenario, true, "Browser") { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            onCopyLink = {
                                onRunExternally(scenario, false, "Link") { url ->
                                    clipboard.setText(AnnotatedString(url))
                                }
                            },
                            onShowQr = {
                                onRunExternally(scenario, false, "QR") { url -> qrUrl = url }
                            },
                            onEdit = { onEditScenario(scenario.id) },
                            onDuplicate = { onDuplicateScenario(scenario.id) },
                            onDelete = { onDeleteScenario(scenario.id) }
                        )
                    }
                    if (uiState.selectedScenarios.isEmpty()) {
                        item {
                            Text(
                                "No scenarios for this merchant yet — add one with +",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                }

                uiState.runError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                uiState.externalRun?.let { run ->
                    ExternalRunCard(
                        run = run,
                        onCopyLink = { clipboard.setText(AnnotatedString(run.checkoutUrl)) },
                        onDismiss = onDismissExternalRun
                    )
                }

                uiState.lastResult?.let { result ->
                    ResultCard(result = result, onClear = onClearResult)
                }
            }
        }
    }

    qrUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { qrUrl = null },
            confirmButton = {
                TextButton(onClick = { qrUrl = null }) { Text("Close") }
            },
            title = { Text("Scan to open checkout") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = remember(url) { qrBitmap(url).asImageBitmap() },
                        contentDescription = "Checkout URL QR code",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(url, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantSelector(
    merchants: List<Merchant>,
    selected: Merchant,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Merchant") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            merchants.forEach { merchant ->
                DropdownMenuItem(
                    text = { Text(merchant.name) },
                    onClick = {
                        onSelect(merchant.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ScenarioRow(
    scenario: Scenario,
    isRunning: Boolean,
    onRun: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onShowQr: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scenario.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    scenario.card.pan.ifBlank { "no card prefill" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                scenario.hint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Button(onClick = onRun, enabled = !isRunning) {
                Text("Run")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open in browser") },
                        enabled = !isRunning,
                        onClick = { menuOpen = false; onOpenInBrowser() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy checkout link") },
                        enabled = !isRunning,
                        onClick = { menuOpen = false; onCopyLink() }
                    )
                    DropdownMenuItem(
                        text = { Text("Show QR code") },
                        enabled = !isRunning,
                        onClick = { menuOpen = false; onShowQr() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ExternalRunCard(
    run: ExternalRun,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (run.phase) {
                ExternalRun.Phase.WAITING -> Color(0xFF1E88E5)
                ExternalRun.Phase.SUCCESS -> Color(0xFF4CAF50)
                ExternalRun.Phase.FAILED -> Color(0xFFF44336)
                ExternalRun.Phase.TIMEOUT -> Color(0xFF9E9E9E)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (run.phase == ExternalRun.Phase.WAITING) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.scenarioName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${run.detail}\n${run.sessionId}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCopyLink) {
                Text("Copy link", color = Color.White)
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color.White)
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: PayCrossResult,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is PayCrossResult.Success -> Color(0xFF4CAF50)
                is PayCrossResult.Failure -> Color(0xFFF44336)
                is PayCrossResult.Cancelled -> Color(0xFF9E9E9E)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (result) {
                        is PayCrossResult.Success -> "Payment Successful"
                        is PayCrossResult.Failure -> "Payment Failed"
                        is PayCrossResult.Cancelled -> "Cancelled"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when (result) {
                        is PayCrossResult.Success ->
                            "${formatMinor(result.amount, result.currency)}\n${result.transactionId}"
                        is PayCrossResult.Failure ->
                            "Recovery: ${result.recovery}\n${result.transactionId ?: "no transaction"}"
                        is PayCrossResult.Cancelled -> "User cancelled"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onClear) {
                Text("Clear", color = Color.White)
            }
        }
    }
}

private fun formatMinor(amount: Long, currency: String): String {
    val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }
        .getOrDefault(2)
        .coerceAtLeast(0)
    var major = amount.toDouble()
    repeat(digits) { major /= 10 }
    return "%.${digits}f %s".format(major, currency)
}
