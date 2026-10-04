package com.example.nutricart.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Shows a digits-only value with thousands separators: "12000" is displayed as "12,000".
// The stored value keeps no separators.
object ThousandsVisualTransformation : VisualTransformation {

    fun format(digits: String): String = buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(char)
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = format(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val separators = (1..minOf(offset, digits.length - 1))
                    .count { (digits.length - it) % 3 == 0 }
                return offset + separators
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != ',' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
