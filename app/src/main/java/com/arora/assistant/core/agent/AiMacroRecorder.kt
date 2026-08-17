package com.arora.assistant.core.agent

import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.task.AroraCommand
import com.arora.assistant.core.task.CustomTask
import com.arora.assistant.core.task.TaskStep
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserDemonstrationEvent(
    val eventType: String,
    val targetText: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AiMacroRecorder {

    private val recordedEvents = mutableListOf<UserDemonstrationEvent>()
    private var isRecording = false
    private val gson = Gson()

    const val MACRO_SYNTHESIS_PROMPT = """You observed a user perform a sequence of actions on their Android phone.
Generalize these actions into a reusable automation macro.

Rules:
- Replace hardcoded values with {{parameter}} placeholders where the user would want to change them
- Add reasonable delay_ms between steps (min 200ms)
- If the same action repeats, consolidate into a loop parameter

Output a JSON macro definition:
{
  "macro_name": "[short descriptive name]",
  "description": "[what this macro does]",
  "parameters": [
    {"name": "[param_name]", "description": "[what to substitute]", "example": "[example value]"}
  ],
  "steps": [
    {
      "action": "CLICK" | "INPUT" | "SCROLL_DOWN" | "SCROLL_UP" | "BACK" | "WAIT",
      "target_text": "[node text or {{param_name}} for variable parts]",
      "input_value": "[text or {{param_name}}]",
      "delay_ms": 300
    }
  ]
}"""

    fun startRecording() {
        recordedEvents.clear()
        isRecording = true
    }

    fun logEvent(type: String, text: String) {
        if (isRecording) {
            recordedEvents.add(UserDemonstrationEvent(type, text))
        }
    }

    fun stopRecording(): List<UserDemonstrationEvent> {
        isRecording = false
        return recordedEvents.toList()
    }

    suspend fun synthesizeMacro(
        client: GeminiClient,
        taskName: String
    ): Result<CustomTask> = withContext(Dispatchers.IO) {
        if (recordedEvents.isEmpty()) {
            return@withContext Result.failure(Exception("No recorded actions to compile"))
        }

        val eventLog = gson.toJson(recordedEvents)
        val prompt = """$MACRO_SYNTHESIS_PROMPT

Recorded actions:
$eventLog

User's description of what they were doing: "$taskName""""

        val result = client.generateContent(prompt)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        val steps = recordedEvents.mapIndexed { idx, ev ->
            TaskStep(
                id = idx.toString(),
                command = AroraCommand.LaunchApp(ev.targetText)
            )
        }

        Result.success(
            CustomTask(
                id = java.util.UUID.randomUUID().toString(),
                title = taskName,
                triggerDescription = "Compiled via AI Demonstration",
                steps = steps
            )
        )
    }
}
