package com.canteen.student.ui.owner.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.canteen.student.data.model.MenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMenuItemDialog(
    itemToEdit: MenuItem?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (MenuItem) -> Unit
) {
    val isEditing = itemToEdit != null
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var priceText by remember { mutableStateOf(itemToEdit?.price?.toString() ?: "") }
    var description by remember { mutableStateOf(itemToEdit?.description ?: "") }
    var selectedCategory by remember {
        mutableStateOf(itemToEdit?.category ?: categories.firstOrNull { it != "All" } ?: "General")
    }
    var isAvailable by remember { mutableStateOf(itemToEdit?.isAvailable ?: true) }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var isValid = true
        if (name.isBlank()) {
            nameError = "Name cannot be empty"
            isValid = false
        } else {
            nameError = null
        }

        val price = priceText.toDoubleOrNull()
        if (price == null || price <= 0) {
            priceError = "Enter a valid positive price"
            isValid = false
        } else {
            priceError = null
        }

        return isValid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Menu Item" else "Add New Menu Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Item Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        if (priceError != null) priceError = null
                    },
                    label = { Text("Price (₹) *") },
                    isError = priceError != null,
                    supportingText = priceError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                val selectableCategories = categories.filter { it != "All" }

                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = !expandedCategoryDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false }
                    ) {
                        selectableCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available in Stock",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { isAvailable = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        val newItem = MenuItem(
                            itemId = itemToEdit?.itemId ?: 0,
                            name = name.trim(),
                            price = priceText.toDoubleOrNull() ?: 0.0,
                            description = description.trim(),
                            category = selectedCategory,
                            isAvailable = isAvailable
                        )
                        onSave(newItem)
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isEditing) "Save Changes" else "Add Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
