package com.arora.assistant.ui.miniapps

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.core.agent.AiMacroRecorder
import com.arora.assistant.core.agent.UiActionExecutor
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.bypass.AroraAccessibilityService
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.NeonAmber
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context) }

    var apiKey by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf<String?>(null) }
    var executionResult by remember { mutableStateOf<String?>(null) }
    var isRecordingMacro by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKey = appPreferences.geminiApiKey.first()
    }

    val isAccessibilityActive by com.arora.assistant.core.service.ServiceStateManager.isAccessibilityActive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Status & Security Shield Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = if (isAccessibilityActive) NeonEmerald else PastelRose, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAccessibilityActive) "Accessibility Agent Active" else "Accessibility Required",
                    color = if (isAccessibilityActive) NeonEmerald else PastelRose,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("🔒 Sandbox Safe", color = SkyOpal, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Goal Input Bar
        OutlinedTextField(
            value = goalInput,
            onValueChange = { goalInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            placeholder = { Text("What should the agent do on screen?", fontSize = 12.sp, color = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
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

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Execute Agent Action
            NeonButton(
                text = "⚡ Execute Action",
                onClick = {
                    if (goalInput.isBlank()) {
                        Toast.makeText(context, "Please enter a goal for the agent", Toast.LENGTH_SHORT).show()
                        return@NeonButton
                    }
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "Set Gemini API Key in Settings first", Toast.LENGTH_SHORT).show()
                        return@NeonButton
                    }
                    if (!isAccessibilityActive) {
                        Toast.makeText(context, "Enable AuraView Accessibility Service in Settings", Toast.LENGTH_LONG).show()
                        return@NeonButton
                    }

                    scope.launch {
                        isExecuting = true
                        executionResult = null
                        progressStatus = "Analyzing screen accessibility nodes..."

                        val client = GeminiClient(apiKey)
                        val result = UiActionExecutor.executeGoalOnScreen(
                            client = client,
                            goal = goalInput,
                            onProgress = { progressStatus = it }
                        )
                        isExecuting = false
                        executionResult = result.getOrElse { "Execution error: ${it.message}" }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            )

            // Macro Record / Stop Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isRecordingMacro) PastelRose else SoftSurfaceElevated)
                    .border(1.dp, if (isRecordingMacro) PastelRose else SoftCardBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        if (!isRecordingMacro) {
                            AiMacroRecorder.startRecording()
                            isRecordingMacro = true
                            Toast.makeText(context, "Macro Recording started. Perform actions...", Toast.LENGTH_SHORT).show()
                        } else {
                            val events = AiMacroRecorder.stopRecording()
                            isRecordingMacro = false
                            Toast.makeText(context, "Recorded ${events.size} actions. Synthesizing...", Toast.LENGTH_SHORT).show()
                            if (apiKey.isNotBlank() && events.isNotEmpty()) {
                                scope.launch {
                                    isExecuting = true
                                    progressStatus = "Compiling macro with Gemini..."
                                    val client = GeminiClient(apiKey)
                                    val task = AiMacroRecorder.synthesizeMacro(client, goalInput.ifBlank { "Screen Macro" })
                                    isExecuting = false
                                    executionResult = task.fold(
                                        onSuccess = { compiledTask ->
                                            scope.launch {
                                                com.arora.assistant.core.task.CustomTaskEngine(context).executeTask(compiledTask) { status ->
                                                    progressStatus = status
                                                }
                                            }
                                            "✅ Macro '${compiledTask.title}' compiled with ${compiledTask.steps.size} steps and executed."
                                        },
                                        onFailure = { "Failed to compile macro: ${it.message}" }
                                    )
                                }
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRecordingMacro) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isRecordingMacro) Color.White else PastelRose,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecordingMacro) "Stop" else "Record",
                        color = TextPureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Log / Result Console Card
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
                    CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(progressStatus ?: "Agent executing...", color = TextMuted, fontSize = 11.sp)
                }
            } else if (executionResult != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("🤖 Agent Output Log:", color = SkyOpal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = executionResult!!,
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
                    Icon(Icons.Default.Bolt, null, tint = SoftLavender.copy(alpha = 0.5f), modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("AI Agent Ready", color = TextPureWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enter any task goal to let Gemini interact with the screen autonomously", color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
