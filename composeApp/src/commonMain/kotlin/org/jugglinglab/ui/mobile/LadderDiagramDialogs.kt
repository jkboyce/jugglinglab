//
// LadderDiagramDialogs.kt
//
// Compose dialogs for the ladder diagram interactions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.jml.JmlProp
import org.jugglinglab.path.Path
import org.jugglinglab.prop.Prop
import org.jugglinglab.ui.common.*
import org.jugglinglab.util.ParameterDescriptor
import org.jugglinglab.util.jlParseFiniteDouble
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource

@Composable
fun LadderDiagramDialogs(
    controller: LadderDiagramController,
    onDismissRequest: () -> Unit
) {
    val uiState = controller.uiState

    when (val dialog = uiState.activeDialog) {
        LadderDiagramDialog.Menu -> {
            LadderDiagramMenuDialog(
                controller = controller,
                onDismissRequest = onDismissRequest
            )
        }

        is LadderDiagramDialog.DefineProp -> {
            DefinePropDialog(
                initialType = dialog.currentType,
                initialMod = dialog.currentMod,
                onConfirm = { type, mod ->
                    controller.applyPropDefinition(dialog.pathNum, type, mod)
                },
                onDismissRequest = onDismissRequest
            )
        }

        is LadderDiagramDialog.DefineThrow -> {
            DefineThrowDialog(
                initialType = dialog.currentType,
                initialMod = dialog.currentMod,
                onConfirm = { type, mod ->
                    controller.applyThrowDefinition(dialog.eventItem, type, mod)
                },
                onDismissRequest = onDismissRequest
            )
        }

        null -> {}
    }
}

@Composable
fun LadderDiagramMenuDialog(
    controller: LadderDiagramController,
    onDismissRequest: () -> Unit
) {
    val resources = LadderDiagramController.popupItemsStringResources
    val commands = LadderDiagramController.popupCommands

    val displayIndices = mutableListOf<Int>()
    var hasEnabledItemBefore = false
    var pendingDividerIndex: Int? = null

    for ((i, popupResource) in resources.withIndex()) {
        if (popupResource == null) {
            if (hasEnabledItemBefore) {
                pendingDividerIndex = i
            }
        } else {
            val command = commands[i] ?: ""
            val enabled = controller.isCommandEnabled(controller.popupItem, command)
            if (enabled) {
                if (pendingDividerIndex != null) {
                    displayIndices.add(pendingDividerIndex)
                    pendingDividerIndex = null
                }
                displayIndices.add(i)
                hasEnabledItemBefore = true
            }
        }
    }

    if (displayIndices.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismissRequest()
        }
        return
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(IntrinsicSize.Max)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                displayIndices.forEach { index ->
                    if (resources[index] == null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        val command = commands[index] ?: ""
                        val text = resources[index]?.let { stringResource(it) } ?: "MISSING"

                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { controller.onMenuAction(command) }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefinePropDialog(
    initialType: String,
    initialMod: String?,
    onConfirm: (String, String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val propTypes = remember { Prop.builtinProps }
    var selectedType by remember { mutableStateOf(initialType) }

    // if we (re)select the initial prop type then use the initialMod set of
    // original modifiers. Otherwise initialize the prop with defaults.
    val descriptors = remember(selectedType) {
        val prop = if (selectedType.equals(initialType, ignoreCase = true)) {
            JmlProp(selectedType.lowercase(), initialMod).prop
        } else {
            Prop.newProp(selectedType)
        }
        prop.parameterDescriptors
    }

    ParameterEditDialog(
        title = stringResource(Res.string.gui_define_prop),
        typeLabel = stringResource(Res.string.gui_prop_type),
        availableTypes = propTypes,
        selectedType = selectedType,
        onTypeChange = { selectedType = it },
        descriptors = descriptors,
        onConfirm = { mod -> onConfirm(selectedType, mod) },
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun DefineThrowDialog(
    initialType: String,
    initialMod: String?,
    onConfirm: (String, String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val pathTypes = remember { Path.builtinPaths }
    var selectedType by remember { mutableStateOf(initialType) }

    val descriptors = remember(selectedType) {
        val path = Path.newPath(selectedType)
        if (selectedType.equals(initialType, ignoreCase = true)) {
            path.initPath(initialMod)
        }
        path.parameterDescriptors
    }

    ParameterEditDialog(
        title = stringResource(Res.string.gui_define_throw),
        typeLabel = stringResource(Res.string.gui_throw_type),
        availableTypes = pathTypes,
        selectedType = selectedType,
        onTypeChange = { selectedType = it },
        descriptors = descriptors,
        onConfirm = { mod -> onConfirm(selectedType, mod) },
        onDismissRequest = onDismissRequest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterEditDialog(
    title: String,
    typeLabel: String,
    availableTypes: List<String>,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    descriptors: List<ParameterDescriptor>,
    onConfirm: (String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    // State for parameter values. Key is param name.
    val paramValues = remember(descriptors) {
        mutableStateMapOf<String, Any?>().apply {
            descriptors.forEach { pd ->
                this[pd.name] = pd.value ?: pd.defaultValue
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(400.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                // Type Selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = typeLabel, modifier = Modifier.width(140.dp))
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        TextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            availableTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        onTypeChange(type)
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Parameters
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    descriptors.forEach { pd ->
                        ParameterRow(
                            descriptor = pd,
                            currentValue = paramValues[pd.name],
                            onValueChange = { newValue -> paramValues[pd.name] = newValue }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.gui_cancel))
                    }
                    Button(onClick = {
                        val result = buildParameterString(descriptors, paramValues)
                        onConfirm(result)
                    }) {
                        Text(stringResource(Res.string.gui_ok))
                    }
                }
            }
        }
    }
}

@Composable
fun ParameterRow(
    descriptor: ParameterDescriptor,
    currentValue: Any?,
    onValueChange: (Any?) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(text = descriptor.name, modifier = Modifier.width(140.dp))

        when (descriptor.type) {
            ParameterDescriptor.TYPE_BOOLEAN -> {
                Checkbox(
                    checked = currentValue as? Boolean ?: false,
                    onCheckedChange = { onValueChange(it) }
                )
            }

            ParameterDescriptor.TYPE_FLOAT -> {
                val strVal = currentValue?.toString() ?: ""
                var textState by remember(currentValue) { mutableStateOf(strVal) }

                TextField(
                    value = textState,
                    onValueChange = {
                        textState = it
                        try {
                            val d = jlParseFiniteDouble(it)
                            onValueChange(d)
                        } catch (_: Exception) {
                            // ignore invalid parse
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParameterDescriptor.TYPE_INT -> {
                val strVal = currentValue?.toString() ?: ""
                var textState by remember(currentValue) { mutableStateOf(strVal) }

                TextField(
                    value = textState,
                    onValueChange = {
                        textState = it
                        it.toIntOrNull()?.let { intVal ->
                            onValueChange(intVal)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParameterDescriptor.TYPE_CHOICE -> {
                val choices = descriptor.range ?: emptyList()
                val selectedChoice = currentValue as? String ?: ""
                var expanded by remember { mutableStateOf(false) }

                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(selectedChoice.ifEmpty { "Select..." })
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        choices.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice) },
                                leadingIcon = if (descriptor.name == "color") {
                                    val colorIndex = Prop.colorNames.indexOf(choice)
                                    if (colorIndex >= 0) {
                                        {
                                            val outlineColor =
                                                if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        Prop.colorValues[colorIndex],
                                                        CircleShape
                                                    )
                                                    .border(1.dp, outlineColor, CircleShape)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    onValueChange(choice)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            ParameterDescriptor.TYPE_ICON -> {
                // TODO: make this an icon instead of a placeholder string
                // How should selection work when there isn't a file chooser?
                val strVal = currentValue as? String ?: ""
                TextField(
                    value = strVal,
                    onValueChange = { onValueChange(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Image URL/Path") }
                )
            }
        }
    }
}

private fun buildParameterString(
    descriptors: List<ParameterDescriptor>,
    paramValues: Map<String, Any?>
): String? {
    val result = buildString {
        descriptors.forEach { pd ->
            var term: String? = null
            val value = paramValues[pd.name]
            val defaultValue = pd.defaultValue

            when (pd.type) {
                ParameterDescriptor.TYPE_BOOLEAN -> {
                    if (value != defaultValue) term = value.toString()
                }

                ParameterDescriptor.TYPE_FLOAT -> {
                    val dVal = value as? Double
                    val dDef = defaultValue as? Double
                    // Simple comparison for now, might need epsilon
                    if (dVal != dDef) term = dVal.toString()
                }

                ParameterDescriptor.TYPE_INT -> {
                    if (value != defaultValue) term = value.toString()
                }

                ParameterDescriptor.TYPE_CHOICE -> {
                    if (value is String && !value.equals(
                            defaultValue as? String,
                            ignoreCase = true
                        )
                    ) {
                        term = if (pd.name == "color" && value == "custom") {
                            pd.customData as? String
                        } else {
                            value
                        }
                    }
                }

                ParameterDescriptor.TYPE_ICON -> {
                    val str = value as? String ?: ""
                    val def = defaultValue.toString()
                    if (str != def) term = str
                }
            }

            if (term != null) {
                if (isNotEmpty()) append(";")
                append("${pd.name}=$term")
            }
        }
    }
    return result.ifEmpty { null }
}


