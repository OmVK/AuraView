package com.arora.assistant.ui.miniapps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.NeonRose
import com.arora.assistant.ui.theme.QuantumViolet

@Composable
fun FloatingCalculatorView(onClose: () -> Unit) {
    val context = LocalContext.current
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }

    val buttons = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "=")
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp),
        borderGlow = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Calculate, null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Floating Calculator",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Calc", result))
                        Toast.makeText(context, "Result copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.LightGray)
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassSurfaceHigh)
                    .padding(14.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expression.ifEmpty { " " },
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Keypad Grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { btn ->
                            val isOperator = btn in listOf("÷", "×", "-", "+", "=")
                            val isSpecial = btn in listOf("C", "⌫", "(", ")")
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            btn == "=" -> ElectricCyan
                                            isOperator -> QuantumViolet.copy(alpha = 0.6f)
                                            isSpecial -> GlassSurfaceHigh.copy(alpha = 0.8f)
                                            else -> GlassSurfaceHigh
                                        }
                                    )
                                    .clickable {
                                        when (btn) {
                                            "C" -> {
                                                expression = ""
                                                result = "0"
                                            }
                                            "⌫" -> {
                                                if (expression.isNotEmpty()) {
                                                    expression = expression.dropLast(1)
                                                }
                                            }
                                            "=" -> {
                                                result = evaluateExpression(expression)
                                            }
                                            else -> {
                                                expression += btn
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = btn,
                                    color = if (btn == "=") Color.Black else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = if (isOperator || btn == "=") FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateExpression(expr: String): String {
    return try {
        val sanitized = expr.replace("×", "*").replace("÷", "/")
        val value = eval(sanitized)
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format("%.4f", value).trimEnd('0').trimEnd('.')
        }
    } catch (e: Exception) {
        "Error"
    }
}

// Simple recursive descent parser for basic math expressions
private fun eval(str: String): Double {
    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.code)) x *= parseFactor()
                else if (eat('/'.code)) x /= parseFactor()
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }
            return x
        }
    }.parse()
}
