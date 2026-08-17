package com.arora.assistant.ui.miniapps

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.core.agent.MacroRecorderEngine
import com.arora.assistant.core.agent.SavedMacro
import com.arora.assistant.core.agent.UiActionExecutor
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FloatingAiAgentView(
    onStartRecording: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Commands & Control, 1: Custom Macros

    var apiKey by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var executionStatus by remember { mutableStateOf<String?>(null) }

    // Macro Recording / Save state
    val savedMacros by MacroRecorderEngine.savedMacrosFlow.collectAsState()
    val recordedSteps = remember { MacroRecorderEngine.getCurrentRecordedSteps() }
    var newMacroName by remember { mutableStateOf("") }
    var isSavingMacro by remember { mutableStateOf(recordedSteps.isNotEmpty()) }

    LaunchedEffect(Unit) {
        apiKey = appPreferences.geminiApiKey.first()
        MacroRecorderEngine.init(context)
    }

    val isAccessibilityActive by com.arora.assistant.core.service.ServiceStateManager.isAccessibilityActive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Tab Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 0) SkyOpal else Color.Transparent)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡ Instant Commands",
                    color = if (selectedTab == 0) Color.Black else TextOffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedTab == 1) PastelRose else Color.Transparent)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔴 Touch Macros (${savedMacros.size})",
                    color = if (selectedTab == 1) Color.White else TextOffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == 0) {
            // TAB 1: Instant Screen & System Commands
            CommandControlTab(
                context = context,
                commandInput = commandInput,
                onCommandChange = { commandInput = it },
                isExecuting = isExecuting,
                executionStatus = executionStatus,
                apiKey = apiKey,
                onExecute = { cmd ->
                    val trimmed = cmd.trim()
                    if (trimmed.isBlank()) {
                        Toast.makeText(context, "Type a command (e.g. 'open wifi')", Toast.LENGTH_SHORT).show()
                        return@CommandControlTab
                    }

                    scope.launch {
                        // 1. Try Instant Intent Command First (<10ms)
                        val instantMsg = UiActionExecutor.tryExecuteInstantCommand(context, trimmed)
                        if (instantMsg != null) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            executionStatus = "⚡ $instantMsg"
                            Toast.makeText(context, instantMsg, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        // 2. Fallback to Screen Node Execution
                        isExecuting = true
                        executionStatus = "Analyzing screen..."
                        val client = if (apiKey.isNotBlank()) GeminiClient(apiKey) else null
                        val res = UiActionExecutor.executeGoalOnScreen(context, client, trimmed) {
                            executionStatus = it
                        }
                        isExecuting = false
                        executionStatus = res.getOrElse { "Error: ${it.message}" }
                    }
                }
            )
        } else {
            // TAB 2: Custom Touch Macros
            CustomMacrosTab(
                context = context,
                savedMacros = savedMacros,
                recordedStepsCount = recordedSteps.size,
                newMacroName = newMacroName,
                onNewMacroNameChange = { newMacroName = it },
                onStartRecording = {
                    onStartRecording()
                },
                onSaveMacro = {
                    if (recordedSteps.isNotEmpty()) {
                        val name = newMacroName.ifBlank { "Macro #${savedMacros.size + 1}" }
                        MacroRecorderEngine.saveMacro(name, recordedSteps)
                        Toast.makeText(context, "Saved macro '$name'", Toast.LENGTH_SHORT).show()
                        newMacroName = ""
                        isSavingMacro = false
                    }
                },
                onPlayMacro = { macro ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Toast.makeText(context, "▶️ Replaying '${macro.name}'...", Toast.LENGTH_SHORT).show()
                    MacroRecorderEngine.replayMacro(macro, scope, onProgress = {
                        executionStatus = it
                    }) {
                        Toast.makeText(context, "Macro completed", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteMacro = { id ->
                    MacroRecorderEngine.deleteMacro(id)
                    Toast.makeText(context, "Macro deleted", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun CommandControlTab(
    context: Context,
    commandInput: String,
    onCommandChange: (String) -> Unit,
    isExecuting: Boolean,
    executionStatus: String?,
    apiKey: String,
    onExecute: (String) -> Unit
) {
    val quickShortcuts = listOf(
        "📶 Open Wi-Fi" to "open wifi",
        "🔵 Bluetooth" to "open bluetooth",
        "⚙️ Settings" to "open settings",
        "📸 Screenshot" to "screenshot",
        "▶️ Open YouTube" to "open youtube",
        "🔒 Lock Screen" to "lock",
        "📱 Installed Apps" to "apps"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Command Input Field
        OutlinedTextField(
            value = commandInput,
            onValueChange = onCommandChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            placeholder = { Text("e.g. 'open wifi', 'open youtube', 'screenshot'", fontSize = 11.5.sp, color = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            trailingIcon = {
                IconButton(
                    onClick = { onExecute(commandInput) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Run", tint = SkyOpal, modifier = Modifier.size(18.dp))
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SoftSurfaceElevated,
                unfocusedContainerColor = SoftSurfaceElevated,
                focusedTextColor = TextPureWhite,
                unfocusedTextColor = TextPureWhite,
                focusedIndicatorColor = SkyOpal,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Execute Button
        NeonButton(
            text = "⚡ Execute Action Now",
            onClick = { onExecute(commandInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Quick System Triggers", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        // Quick Suggestions Horizontal Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickShortcuts) { (label, cmd) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftSurfaceElevated)
                        .border(0.8.dp, SoftCardBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            onCommandChange(cmd)
                            onExecute(cmd)
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(label, color = TextPureWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Output Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SoftDarkBg)
                .border(1.dp, SoftCardBorder, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            if (isExecuting) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(executionStatus ?: "Executing command...", color = TextMuted, fontSize = 11.sp)
                }
            } else if (executionStatus != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Result Log:", color = SkyOpal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = executionStatus,
                        color = TextPureWhite,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Bolt, null, tint = SoftLavender.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Ready for Direct Commands", color = TextPureWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Zero-delay instant execution for settings & apps", color = TextMuted, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun CustomMacrosTab(
    context: Context,
    savedMacros: List<SavedMacro>,
    recordedStepsCount: Int,
    newMacroName: String,
    onNewMacroNameChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    onSaveMacro: () -> Unit,
    onPlayMacro: (SavedMacro) -> Unit,
    onDeleteMacro: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Record Button Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PastelRose.copy(alpha = 0.15f))
                .border(1.dp, PastelRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onStartRecording() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PastelRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("🔴 Record New Touch Macro", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text("Minimizes AuraView & turns the ball into a Stop rectangle", color = TextMuted, fontSize = 10.sp)
            }
        }

        // Save Dialog if fresh steps exist
        if (recordedStepsCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftSurfaceElevated)
                    .border(1.dp, SkyOpal.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉 Recorded $recordedStepsCount Touch Actions", color = SkyOpal, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = newMacroName,
                    onValueChange = onNewMacroNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    placeholder = { Text("Name this macro (e.g. 'Daily Check-in')", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftDarkBg,
                        unfocusedContainerColor = SoftDarkBg,
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedIndicatorColor = SkyOpal,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                NeonButton(
                    text = "💾 Save Macro to Library",
                    onClick = onSaveMacro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("Saved Macros Library (${savedMacros.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        if (savedMacros.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TouchApp, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("No saved macros yet", color = TextMuted, fontSize = 12.sp)
                    Text("Tap 'Record New Touch Macro' to create one", color = TextMuted.copy(alpha = 0.7f), fontSize = 10.5.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(savedMacros, key = { it.id }) { macro ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftSurfaceElevated)
                            .border(0.8.dp, SoftCardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = macro.name,
                                color = TextPureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${macro.steps.size} gestures",
                                color = SkyOpal,
                                fontSize = 10.sp
                            )
                        }

                        // Play Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonEmerald.copy(alpha = 0.2f))
                                .clickable { onPlayMacro(macro) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = NeonEmerald, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Play", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Delete Button
                        IconButton(
                            onClick = { onDeleteMacro(macro.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
