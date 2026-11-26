package com.anilili.ui.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
internal fun FormattedNoteEditor(value: String, onChange: (String) -> Unit, modifier: Modifier, colors: TextFieldColors, shape: Shape) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    var preview by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (field.text != value) field = TextFieldValue(value) }
    fun format(prefix: String, suffix: String = "") {
        val start = minOf(field.selection.start, field.selection.end)
        val end = maxOf(field.selection.start, field.selection.end)
        val next = field.text.substring(0, start) + prefix + field.text.substring(start, end) + suffix + field.text.substring(end)
        if (next.length > 8000) return
        field = TextFieldValue(next, TextRange(start + prefix.length, end + prefix.length))
        onChange(next)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            @Composable
            fun RowScope.Tool(label: String, selected: Boolean = false, weight: Float = 1f, action: () -> Unit) {
                Surface(
                    modifier = Modifier.weight(weight),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    TextButton(
                        onClick = action,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    ) {
                        Text(
                            label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Tool("B") { format("**", "**") }
            Tool("I") { format("*", "*") }
            Tool("H") { format("\n# ") }
            Tool("•") { format("\n- ") }
            Tool(if (preview) "Edit" else "Preview", selected = preview, weight = 1.7f) { preview = !preview }
        }
        if (preview) {
            Text(buildAnnotatedString {
                field.text.lines().forEachIndexed { i, line ->
                    if (i > 0) append("\n")
                    val heading = line.startsWith("# ")
                    val body = if (heading) line.removePrefix("# ") else if (line.startsWith("- ")) "• " + line.removePrefix("- ") else line
                    val pattern = Regex("\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*")
                    var cursor = 0
                    val base = if (heading) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
                    withStyle(base) {
                        pattern.findAll(body).forEach { m ->
                            append(body.substring(cursor, m.range.first))
                            withStyle(if (m.groups[1] != null) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(m.groups[1]?.value ?: m.groups[2]?.value.orEmpty())
                            }
                            cursor = m.range.last + 1
                        }
                        append(body.substring(cursor))
                    }
                }
            }, modifier = modifier.padding(12.dp))
        } else OutlinedTextField(field, { next ->
            if (next.text.length <= 8000) { field = next; onChange(next.text) }
        }, label = { Text("Your thoughts") }, minLines = 3, modifier = modifier, colors = colors, shape = shape)
    }
}
