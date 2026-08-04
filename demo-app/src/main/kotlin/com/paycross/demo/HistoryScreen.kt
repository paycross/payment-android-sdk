package com.paycross.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun outcomeColor(outcome: String): Color = when {
    outcome == "succeeded" -> Color(0xFF4CAF50)
    outcome == "pending" -> Color(0xFF1E88E5)
    outcome == "timeout" -> Color(0xFF9E9E9E)
    outcome == "cancelled" -> Color(0xFF9E9E9E)
    else -> Color(0xFFF44336)
}

private val timeFormat = SimpleDateFormat("d MMM HH:mm:ss", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    runs: List<RunRecord>,
    onOpenRun: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (runs.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No runs yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(runs, key = { it.id }) { run ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenRun(run.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(outcomeColor(run.outcome), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(run.scenarioName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${run.outcome} · ${run.surface} · ${timeFormat.format(Date(run.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunDetailScreen(
    run: RunRecord,
    merchantName: String?,
    inspectedJson: String?,
    inspectLoading: Boolean,
    curl: String,
    onInspectSession: () -> Unit,
    onDismissInspected: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    // Inspect state is global, so leaving must clear it — otherwise another run's
    // JSON arrives under this screen's title.
    BackHandler { onDismissInspected(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(run.scenarioName) },
                navigationIcon = {
                    IconButton(onClick = { onDismissInspected(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DetailRow("Outcome", run.outcome, clipboardText = null)
            DetailRow("Surface", run.surface, clipboardText = null)
            DetailRow("Time", timeFormat.format(Date(run.timestamp)), clipboardText = null)
            merchantName?.let { DetailRow("Merchant", it, clipboardText = null) }
            run.amount?.let { amount ->
                DetailRow("Amount", "$amount ${run.currency.orEmpty()}", clipboardText = null)
            }
            DetailRow("Session ID", run.sessionId) { clipboard.setText(AnnotatedString(run.sessionId)) }
            run.transactionId?.let { txn ->
                DetailRow("Transaction ID", txn) { clipboard.setText(AnnotatedString(txn)) }
            }
            DetailRow("Checkout URL", run.checkoutUrl.take(60) + "…") {
                clipboard.setText(AnnotatedString(run.checkoutUrl))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(curl)) }) {
                    Text("Copy as curl")
                }
                TextButton(onClick = onInspectSession, enabled = !inspectLoading) {
                    Text(if (inspectLoading) "Fetching…" else "Fetch session JSON")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Request body", style = MaterialTheme.typography.titleSmall)
            Text(
                run.requestBody,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    inspectedJson?.let { json ->
        AlertDialog(
            onDismissRequest = onDismissInspected,
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(json)) }) { Text("Copy") }
                TextButton(onClick = onDismissInspected) { Text("Close") }
            },
            title = { Text("Session ${run.sessionId.take(13)}…") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }

    if (inspectLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, clipboardText: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (clipboardText != null) {
            TextButton(onClick = clipboardText) { Text("Copy") }
        }
    }
}
