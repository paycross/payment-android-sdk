package com.paycross.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().create()

/**
 * Structured editor for the create-session request body, mirroring the
 * create-payment-session request schema the backend publishes.
 * Optional fields toggle the key in and out of the JSON; unknown keys
 * (metadata, account_funding, hand-added extras) pass through untouched
 * because edits mutate the parsed tree.
 */
@Composable
internal fun BodyBuilder(
    body: String,
    onBodyChange: (String) -> Unit
) {
    val parsed = remember(body) {
        runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    }

    if (parsed == null) {
        Text(
            "Body is not valid JSON — fix it in the JSON tab.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    fun mutate(block: (JsonObject) -> Unit) {
        val obj = parsed.deepCopy()
        block(obj)
        onBodyChange(PRETTY_GSON.toJson(obj))
    }

    val customer = parsed.getAsJsonObject("customer")
    val billing = customer?.getAsJsonObject("address")?.getAsJsonObject("billing")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BuilderField(
            label = "merchant_reference",
            value = parsed.str("merchant_reference"),
            onChange = { v -> mutate { it.addProperty("merchant_reference", v) } }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuilderField(
                label = "amount (minor)",
                value = parsed.str("amount"),
                numeric = true,
                modifier = Modifier.weight(1f),
                onChange = { v -> mutate { it.addProperty("amount", v.toLongOrNull() ?: 0L) } }
            )
            BuilderField(
                label = "currency",
                value = parsed.str("currency"),
                modifier = Modifier.weight(1f),
                onChange = { v -> mutate { it.addProperty("currency", v.uppercase()) } }
            )
        }
        EnumDropdown(
            label = "transaction_type",
            value = parsed.str("transaction_type"),
            options = listOf("sale", "auth", "auth_capture"),
            onSelect = { v -> mutate { it.addProperty("transaction_type", v) } }
        )
        BuilderField(
            label = "return_url",
            value = parsed.str("return_url"),
            onChange = { v -> mutate { it.addProperty("return_url", v) } }
        )
        BuilderField(
            label = "success_url",
            value = parsed.str("success_url"),
            onChange = { v -> mutate { it.addProperty("success_url", v) } }
        )

        ToggleSection(
            label = "locale",
            enabled = parsed.has("locale"),
            onToggle = { on ->
                mutate { if (on) it.addProperty("locale", "en") else it.remove("locale") }
            }
        ) {
            BuilderField(
                label = "locale",
                value = parsed.str("locale"),
                onChange = { v -> mutate { it.addProperty("locale", v) } }
            )
        }

        Text("customer", style = MaterialTheme.typography.titleSmall)
        BuilderField(
            label = "first_name",
            value = customer?.str("first_name").orEmpty(),
            onChange = { v -> mutate { it.obj("customer").addProperty("first_name", v) } }
        )
        BuilderField(
            label = "last_name",
            value = customer?.str("last_name").orEmpty(),
            onChange = { v -> mutate { it.obj("customer").addProperty("last_name", v) } }
        )
        BuilderField(
            label = "email",
            value = customer?.str("email").orEmpty(),
            onChange = { v -> mutate { it.obj("customer").addProperty("email", v) } }
        )

        ToggleSection(
            label = "phone",
            enabled = customer?.has("phone") == true,
            onToggle = { on ->
                mutate {
                    if (on) it.obj("customer").addProperty("phone", "+12025551234")
                    else it.getAsJsonObject("customer")?.remove("phone")
                }
            }
        ) {
            BuilderField(
                label = "phone",
                value = customer?.str("phone").orEmpty(),
                onChange = { v -> mutate { it.obj("customer").addProperty("phone", v) } }
            )
        }

        ToggleSection(
            label = "customer merchant_reference",
            enabled = customer?.has("merchant_reference") == true,
            onToggle = { on ->
                mutate {
                    if (on) it.obj("customer").addProperty("merchant_reference", "CUST-{{timestamp}}")
                    else it.getAsJsonObject("customer")?.remove("merchant_reference")
                }
            }
        ) {
            BuilderField(
                label = "customer merchant_reference",
                value = customer?.str("merchant_reference").orEmpty(),
                onChange = { v -> mutate { it.obj("customer").addProperty("merchant_reference", v) } }
            )
        }

        ToggleSection(
            label = "billing address",
            enabled = billing != null,
            onToggle = { on ->
                mutate {
                    if (on) {
                        it.obj("customer").obj("address").add("billing", defaultBilling())
                    } else {
                        it.getAsJsonObject("customer")?.remove("address")
                    }
                }
            }
        ) {
            listOf("line1", "line2", "city", "state", "postal_code", "country").forEach { key ->
                BuilderField(
                    label = key,
                    value = billing?.str(key).orEmpty(),
                    onChange = { v ->
                        mutate {
                            it.obj("customer").obj("address").obj("billing").addProperty(key, v)
                        }
                    }
                )
            }
        }

        ToggleSection(
            label = "save_card_config",
            enabled = parsed.has("save_card_config"),
            onToggle = { on ->
                mutate {
                    if (on) {
                        it.add("save_card_config", JsonObject().apply {
                            addProperty("usage", "card_on_file")
                        })
                    } else {
                        it.remove("save_card_config")
                    }
                }
            }
        ) {
            EnumDropdown(
                label = "usage",
                value = parsed.getAsJsonObject("save_card_config")?.str("usage").orEmpty(),
                options = listOf("recurring", "card_on_file", "unscheduled"),
                onSelect = { v ->
                    mutate { it.obj("save_card_config").addProperty("usage", v) }
                }
            )
        }

        ToggleSection(
            label = "saved_cards (show: all)",
            enabled = parsed.has("saved_cards"),
            onToggle = { on ->
                mutate {
                    if (on) {
                        it.add("saved_cards", JsonObject().apply { addProperty("show", "all") })
                    } else {
                        it.remove("saved_cards")
                    }
                }
            }
        )
    }
}

@Composable
private fun BuilderField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleSection(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit = {}
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = onToggle)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (enabled) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun JsonObject.str(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.obj(key: String): JsonObject =
    getAsJsonObject(key) ?: JsonObject().also { add(key, it) }

private fun defaultBilling() = JsonObject().apply {
    addProperty("line1", "123 Main Street")
    addProperty("line2", "Apt 4B")
    addProperty("city", "New York")
    addProperty("state", "NY")
    addProperty("postal_code", "10001")
    addProperty("country", "US")
}
