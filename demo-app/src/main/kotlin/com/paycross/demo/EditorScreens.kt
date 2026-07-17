package com.paycross.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MerchantsScreen(
    merchants: List<Merchant>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Merchant?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchants") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add merchant")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(merchants, key = { it.id }) { merchant ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = merchant.id == selectedId,
                            onClick = { onSelect(merchant.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(merchant.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${merchant.environment} · ${merchant.clientId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEdit(merchant.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { pendingDelete = merchant }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            if (merchants.isEmpty()) {
                item {
                    Text(
                        "No merchants — add one with +",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }

    pendingDelete?.let { merchant ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${merchant.name}?") },
            text = { Text("Its scenarios will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(merchant.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MerchantEditScreen(
    merchant: Merchant?,
    onSave: (Merchant, ScenarioPreset) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(merchant?.name ?: "") }
    var preset by rememberSaveable { mutableStateOf(ScenarioPreset.SANDBOX) }
    var environment by rememberSaveable {
        mutableStateOf(merchant?.environment ?: Merchant.ENV_STAGING)
    }
    var tokenUrl by rememberSaveable {
        mutableStateOf(merchant?.tokenUrl ?: DEFAULT_TOKEN_URL)
    }
    var clientId by rememberSaveable { mutableStateOf(merchant?.clientId ?: "") }
    var clientSecret by rememberSaveable { mutableStateOf(merchant?.clientSecret ?: "") }
    var paymentApiUrl by rememberSaveable {
        mutableStateOf(merchant?.paymentApiUrl ?: DEFAULT_PAYMENT_API_URL)
    }
    var version by rememberSaveable {
        mutableStateOf(merchant?.paycrossVersion ?: DEFAULT_VERSION)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (merchant == null) "New Merchant" else "Edit Merchant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = environment == Merchant.ENV_STAGING,
                    onClick = { environment = Merchant.ENV_STAGING },
                    label = { Text("Staging") }
                )
                FilterChip(
                    selected = environment == Merchant.ENV_PRODUCTION,
                    onClick = { environment = Merchant.ENV_PRODUCTION },
                    label = { Text("Production") }
                )
            }
            OutlinedTextField(
                value = tokenUrl,
                onValueChange = { tokenUrl = it },
                label = { Text("OAuth token URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text("Client secret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = paymentApiUrl,
                onValueChange = { paymentApiUrl = it },
                label = { Text("Payment sessions URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("PayCross-Version") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (merchant == null) {
                PresetSelector(preset = preset, onSelect = { preset = it })
            }
            Button(
                enabled = name.isNotBlank() && clientId.isNotBlank() &&
                    tokenUrl.isNotBlank() && paymentApiUrl.isNotBlank(),
                onClick = {
                    onSave(
                        Merchant(
                            id = merchant?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            environment = environment,
                            tokenUrl = tokenUrl.trim(),
                            clientId = clientId.trim(),
                            clientSecret = clientSecret.trim(),
                            paymentApiUrl = paymentApiUrl.trim(),
                            paycrossVersion = version.trim()
                        ),
                        preset
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSelector(
    preset: ScenarioPreset,
    onSelect: (ScenarioPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = preset.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Scenario preset") },
            supportingText = { Text("Test scenarios seeded for this merchant") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ScenarioPreset.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScenarioEditScreen(
    scenario: Scenario?,
    merchantId: String,
    onSave: (Scenario) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(scenario?.name ?: "") }
    var cardholderName by rememberSaveable {
        mutableStateOf(scenario?.card?.cardholderName ?: "John Doe")
    }
    var pan by rememberSaveable { mutableStateOf(scenario?.card?.pan ?: "") }
    var month by rememberSaveable { mutableStateOf(scenario?.card?.expireMonth ?: "12") }
    var year by rememberSaveable { mutableStateOf(scenario?.card?.expireYear ?: "2028") }
    var cvv by rememberSaveable { mutableStateOf(scenario?.card?.cvv ?: "123") }
    var saveCard by rememberSaveable { mutableStateOf(scenario?.card?.saveCard ?: false) }
    var body by rememberSaveable {
        mutableStateOf(scenario?.requestBody ?: DemoSeeds.DEFAULT_BODY)
    }
    var bodyError by remember { mutableStateOf<String?>(null) }
    var bodyTab by rememberSaveable { mutableStateOf(0) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scenario == null) "New Scenario" else "Edit Scenario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Card prefill (blank PAN = no prefill)",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = cardholderName,
                onValueChange = { cardholderName = it },
                label = { Text("Cardholder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pan,
                onValueChange = { pan = it },
                label = { Text("PAN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("MM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("YYYY") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { cvv = it },
                    label = { Text("CVV") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveCard, onCheckedChange = { saveCard = it })
                Text("Pre-tick \"save card\"", style = MaterialTheme.typography.bodyMedium)
            }

            Text("Session request body", style = MaterialTheme.typography.titleSmall)
            TabRow(selectedTabIndex = bodyTab) {
                Tab(
                    selected = bodyTab == 0,
                    onClick = { bodyTab = 0 },
                    text = { Text("Builder") }
                )
                Tab(
                    selected = bodyTab == 1,
                    onClick = { bodyTab = 1 },
                    text = { Text("JSON") }
                )
            }

            if (bodyTab == 0) {
                BodyBuilder(
                    body = body,
                    onBodyChange = {
                        body = it
                        bodyError = null
                    }
                )
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        bodyError = null
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    isError = bodyError != null,
                    supportingText = {
                        Text(bodyError ?: "Placeholders: {{timestamp}}, {{uuid}}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bodyTab == 1) {
                    OutlinedButton(onClick = {
                        runCatching {
                            GsonBuilder().setPrettyPrinting().create()
                                .toJson(JsonParser.parseString(body))
                        }.onSuccess {
                            body = it
                            bodyError = null
                        }.onFailure {
                            bodyError = "Invalid JSON"
                        }
                    }) { Text("Format") }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (runCatching { JsonParser.parseString(body) }.isFailure) {
                            bodyError = "Invalid JSON"
                            return@Button
                        }
                        onSave(
                            Scenario(
                                id = scenario?.id ?: UUID.randomUUID().toString(),
                                merchantId = scenario?.merchantId ?: merchantId,
                                name = name.trim(),
                                card = CardPrefill(
                                    cardholderName = cardholderName.trim(),
                                    pan = pan.trim(),
                                    expireMonth = month.trim(),
                                    expireYear = year.trim(),
                                    cvv = cvv.trim(),
                                    saveCard = saveCard
                                ),
                                requestBody = body
                            )
                        )
                    }
                ) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val DEFAULT_TOKEN_URL =
    "https://api.test-pay-cross.com/oauth2/token?scope=paycross/payments"
private const val DEFAULT_PAYMENT_API_URL =
    "https://api.test-pay-cross.com/payment-sessions"
private const val DEFAULT_VERSION = "2026-06-16"
