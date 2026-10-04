package com.example.nutricart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// C-04 Input field. Single line; the error message is shown below the field.
@Composable
fun NutriTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    prefix: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    height: Dp = Sizes.input,
    shape: Shape = NutriCartShapes.input,
    containerColor: Color = NutriCartTheme.colors.surfaceCard,
    textStyle: TextStyle = NutriCartTheme.typography.title,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = NutriCartTheme.colors
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> colors.danger
        focused -> colors.primary
        else -> colors.outline
    }
    val borderWidth = if (focused) Sizes.focusedOutline else Sizes.outline

    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(color = colors.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .height(height)
                        .background(containerColor, shape)
                        .border(borderWidth, borderColor, shape)
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    leadingIcon?.invoke()
                    if (prefix != null) {
                        Text(
                            text = prefix,
                            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurfaceMuted
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.onSurfaceFaint,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.invoke()
                }
            }
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = NutriCartTheme.typography.caption,
                color = colors.danger,
                modifier = Modifier.padding(top = Spacing.xxs)
            )
        }
    }
}

// C-04 pill variant: filters as you type, no submit action
@Composable
fun NutriSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    NutriTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        height = Sizes.searchField,
        shape = NutriCartShapes.pill,
        textStyle = NutriCartTheme.typography.body,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconInline),
                tint = NutriCartTheme.colors.onSurfaceFaint
            )
        }
    )
}

// C-05 Dropdown. When searchable, typing in the field filters the options;
// otherwise a tap opens the list without raising the keyboard.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutriDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    searchable: Boolean = true
) {
    val colors = NutriCartTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val filtered = if (!searchable || query == value) {
        options
    } else {
        options.filter { it.contains(query, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        NutriTextField(
            value = if (searchable) query else value,
            onValueChange = {
                query = it
                expanded = true
            },
            fieldModifier = Modifier.menuAnchor(
                if (searchable) {
                    ExposedDropdownMenuAnchorType.PrimaryEditable
                } else {
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable
                }
            ),
            placeholder = placeholder,
            readOnly = !searchable,
            isError = isError,
            errorMessage = errorMessage,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconInline),
                    tint = colors.onSurfaceMuted
                )
            }
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = {
                expanded = false
                query = value
            },
            containerColor = colors.surfaceCard
        ) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = NutriCartTheme.typography.body,
                            color = colors.onSurface
                        )
                    },
                    onClick = {
                        onSelect(option)
                        query = option
                        expanded = false
                    }
                )
            }
        }
    }
}
