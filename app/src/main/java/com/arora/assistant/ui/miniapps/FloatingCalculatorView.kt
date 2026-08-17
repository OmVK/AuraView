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
    var selectedMode by remember { mutableStateOf(0) } // 0 = Calculator, 1 = Unit Converter
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }

    // Converter States
    var convCategory by remember { mutableStateOf("Length") }
    var inputVal by remember { mutableStateOf("1") }
    var fromUnit by remember { mutableStateOf("Meters (m)") }
    var toUnit by remember { mutableStateOf("Kilometers (km)") }

    val buttons = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Sub-Header (Mode Switcher & Copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher Tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassSurfaceHigh)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedMode == 0) ElectricCyan else Color.Transparent)
                        .clickable { selectedMode = 0 }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Calculator", color = if (selectedMode == 0) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedMode == 1) ElectricCyan else Color.Transparent)
                        .clickable { selectedMode = 1 }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Converter", color = if (selectedMode == 1) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Calc", if (selectedMode == 0) result else calculateConversion(inputVal, convCategory, fromUnit, toUnit)))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, "Copy", tint = ElectricCyan, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

            if (selectedMode == 0) {
                // Calculator Display
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

                Spacer(modifier = Modifier.height(12.dp))

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
            } else {
                // Unit Converter View
                val categories = listOf("Length", "Weight", "Temp", "Digital")
                val unitMap = mapOf(
                    "Length" to listOf("Meters (m)", "Kilometers (km)", "Miles (mi)", "Feet (ft)", "Inches (in)"),
                    "Weight" to listOf("Kilograms (kg)", "Grams (g)", "Pounds (lb)", "Ounces (oz)"),
                    "Temp" to listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)"),
                    "Digital" to listOf("Megabytes (MB)", "Gigabytes (GB)", "Terabytes (TB)", "Kilobytes (KB)")
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (convCategory == cat) QuantumViolet else GlassSurfaceHigh)
                                    .clickable {
                                        convCategory = cat
                                        fromUnit = unitMap[cat]?.firstOrNull() ?: ""
                                        toUnit = unitMap[cat]?.getOrNull(1) ?: ""
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cat, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Value Input
                    androidx.compose.material3.OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Input Value", fontSize = 11.sp, color = Color.Gray) },
                        singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = GlassSurfaceHigh,
                            unfocusedContainerColor = GlassSurfaceHigh,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Unit Selectors
                    val currentUnits = unitMap[convCategory] ?: listOf()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("From:", color = Color.Gray, fontSize = 10.sp)
                            currentUnits.forEach { unit ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (fromUnit == unit) ElectricCyan.copy(alpha = 0.3f) else Color.Transparent)
                                        .clickable { fromUnit = unit }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(unit, color = if (fromUnit == unit) ElectricCyan else Color.LightGray, fontSize = 11.sp)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("To:", color = Color.Gray, fontSize = 10.sp)
                            currentUnits.forEach { unit ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (toUnit == unit) NeonEmerald.copy(alpha = 0.3f) else Color.Transparent)
                                        .clickable { toUnit = unit }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(unit, color = if (toUnit == unit) NeonEmerald else Color.LightGray, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Result Box
                    val convertedOutput = calculateConversion(inputVal, convCategory, fromUnit, toUnit)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurfaceHigh)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Converted Result:", color = Color.Gray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(convertedOutput, color = NeonEmerald, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

private fun calculateConversion(inputStr: String, category: String, from: String, to: String): String {
    val value = inputStr.toDoubleOrNull() ?: return "Invalid Input"
    return try {
        val resultVal = when (category) {
            "Length" -> {
                // Base: Meters
                val inMeters = when (from) {
                    "Meters (m)" -> value
                    "Kilometers (km)" -> value * 1000.0
                    "Miles (mi)" -> value * 1609.34
                    "Feet (ft)" -> value * 0.3048
                    "Inches (in)" -> value * 0.0254
                    else -> value
                }
                when (to) {
                    "Meters (m)" -> inMeters
                    "Kilometers (km)" -> inMeters / 1000.0
                    "Miles (mi)" -> inMeters / 1609.34
                    "Feet (ft)" -> inMeters / 0.3048
                    "Inches (in)" -> inMeters / 0.0254
                    else -> inMeters
                }
            }
            "Weight" -> {
                // Base: Kilograms
                val inKg = when (from) {
                    "Kilograms (kg)" -> value
                    "Grams (g)" -> value / 1000.0
                    "Pounds (lb)" -> value * 0.453592
                    "Ounces (oz)" -> value * 0.0283495
                    else -> value
                }
                when (to) {
                    "Kilograms (kg)" -> inKg
                    "Grams (g)" -> inKg * 1000.0
                    "Pounds (lb)" -> inKg / 0.453592
                    "Ounces (oz)" -> inKg / 0.0283495
                    else -> inKg
                }
            }
            "Temp" -> {
                // Base: Celsius
                val inCelsius = when (from) {
                    "Celsius (°C)" -> value
                    "Fahrenheit (°F)" -> (value - 32) * 5.0 / 9.0
                    "Kelvin (K)" -> value - 273.15
                    else -> value
                }
                when (to) {
                    "Celsius (°C)" -> inCelsius
                    "Fahrenheit (°F)" -> (inCelsius * 9.0 / 5.0) + 32
                    "Kelvin (K)" -> inCelsius + 273.15
                    else -> inCelsius
                }
            }
            "Digital" -> {
                // Base: Megabytes
                val inMb = when (from) {
                    "Megabytes (MB)" -> value
                    "Gigabytes (GB)" -> value * 1024.0
                    "Terabytes (TB)" -> value * 1024.0 * 1024.0
                    "Kilobytes (KB)" -> value / 1024.0
                    else -> value
                }
                when (to) {
                    "Megabytes (MB)" -> inMb
                    "Gigabytes (GB)" -> inMb / 1024.0
                    "Terabytes (TB)" -> inMb / (1024.0 * 1024.0)
                    "Kilobytes (KB)" -> inMb * 1024.0
                    else -> inMb
                }
            }
            else -> value
        }

        if (resultVal % 1.0 == 0.0) {
            resultVal.toLong().toString()
        } else {
            String.format("%.4f", resultVal).trimEnd('0').trimEnd('.')
        }
    } catch (e: Exception) {
        "Error"
    }
}
