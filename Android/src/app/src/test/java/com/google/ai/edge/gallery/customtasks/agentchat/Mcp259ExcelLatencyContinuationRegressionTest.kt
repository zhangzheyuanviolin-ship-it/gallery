package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class Mcp259ExcelLatencyContinuationRegressionTest {
  private val modelName = "mcp259-test-model"
  private val initialInput =
    """COMPAT_AGENT_INSTRUCTIONS
You are running in Qwen-compatible tool mode.

USER_REQUEST
创建一个两列表格。"""

  @Before
  fun setUp() {
    AgentCompatRuntimeCoordinator.clearAllForTest()
  }

  @After
  fun tearDown() {
    AgentCompatRuntimeCoordinator.clearAllForTest()
  }

  @Test
  fun recoveredTruncatedExcelCall_marksAwaitingAndForcesFreshToolContinuation() {
    val topLevel =
      AgentCompatRuntimeCoordinator.prepareInput(
        modelName = modelName,
        rawInput = initialInput,
        historyBudgetChars = 3600,
      )
    assertThat(topLevel.requiresFreshConversation).isTrue()
    assertThat(topLevel.freshConversationReason).isEqualTo(COMPAT_FRESH_REASON_TOP_LEVEL)

    val truncated =
      """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["A","B"],["1","2"]],"sheet_name":"Sheet1"}</tool_call>"""
    val decision =
      AgentCompatRuntimeCoordinator.onGenerationCompleted(
        modelName = modelName,
        generatedText = truncated,
      )
    assertThat(decision.blockedRepeatedToolCall).isFalse()

    val continuation = prepareSuccessfulExcelToolResult()
    assertFreshToolContinuationAndRecordMetrics(continuation)
  }

  @Test
  fun alternateExcelWireDialect_usesSameContinuationStateAsDispatchParser() {
    AgentCompatRuntimeCoordinator.prepareInput(
      modelName = modelName,
      rawInput = initialInput,
      historyBudgetChars = 3600,
    )

    val alternate =
      """<|tool_call>call:excel_workbook{rows:[["A","B"],["1","2"]],sheet_name:"Sheet1"}<tool_call|>"""
    assertThat(AgentTextToolCallFallback.parse(alternate)).isNotNull()
    AgentCompatRuntimeCoordinator.onGenerationCompleted(
      modelName = modelName,
      generatedText = alternate,
    )

    val continuation = prepareSuccessfulExcelToolResult()
    assertThat(continuation.requiresFreshConversation).isTrue()
    assertThat(continuation.freshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
    assertThat(continuation.historyStepCount).isEqualTo(1)
  }

  @Test
  fun hostAcceptedDispatch_recoversCoordinatorThatPrematurelyCompletedTurn() {
    AgentCompatRuntimeCoordinator.prepareInput(
      modelName = modelName,
      rawInput = initialInput,
      historyBudgetChars = 3600,
    )

    // Simulate the exact architectural disagreement MCP259 must make impossible: runtime saw a
    // representation it did not recognize and provisionally completed the user turn, while the host
    // parser later obtained an executable Excel call from its authoritative completion text.
    AgentCompatRuntimeCoordinator.onGenerationCompleted(
      modelName = modelName,
      generatedText = "unrecognized runtime representation",
    )
    val beforeRecovery = AgentCompatRuntimeCoordinator.snapshot(modelName)
    assertThat(beforeRecovery).isNotNull()
    assertThat(beforeRecovery!!.sessionCompletedTurnCount).isEqualTo(1)

    val acceptedArguments =
      JSONObject()
        .put(
          "rows",
          JSONArray()
            .put(JSONArray().put("A").put("B"))
            .put(JSONArray().put("1").put("2")),
        )
        .put("sheet_name", "Sheet1")
    AgentCompatRuntimeCoordinator.onHostToolDispatchAccepted(
      modelName = modelName,
      toolName = "excel_workbook",
      arguments = acceptedArguments,
    )

    val afterRecovery = AgentCompatRuntimeCoordinator.snapshot(modelName)
    assertThat(afterRecovery).isNotNull()
    // The provisional final turn must be rolled back because the host is actually executing a tool.
    assertThat(afterRecovery!!.sessionCompletedTurnCount).isEqualTo(0)

    val continuation = prepareSuccessfulExcelToolResult()
    assertThat(continuation.requiresFreshConversation).isTrue()
    assertThat(continuation.freshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
    assertThat(continuation.historyStepCount).isEqualTo(1)
  }

  @Test
  fun ordinaryFinalAnswer_withoutHostDispatch_doesNotPretendToAwaitToolResult() {
    AgentCompatRuntimeCoordinator.prepareInput(
      modelName = modelName,
      rawInput = initialInput,
      historyBudgetChars = 3600,
    )
    AgentCompatRuntimeCoordinator.onGenerationCompleted(
      modelName = modelName,
      generatedText = "表格已经准备好了。",
    )

    val next =
      AgentCompatRuntimeCoordinator.prepareInput(
        modelName = modelName,
        rawInput = "TOOL_RESULT\ntool: excel_workbook\nstatus: succeeded\npayload:\nok",
        historyBudgetChars = 3600,
      )
    assertThat(next.requiresFreshConversation).isFalse()
    assertThat(next.freshConversationReason).isNull()
  }

  @Test
  fun excelCompatSchema_prefersCompactCreateAndForbidsDuplicateRows() {
    val prompt =
      buildCompatAgentInstructionPayloadForTest(
        baseSystemPrompt = "",
        selectedSkillSummaries = listOf("excel-workbook: spreadsheet creation and editing"),
      )

    assertThat(prompt).contains("sheet_name")
    assertThat(prompt).contains("Omit operation, input_path, and output_path when not needed")
    assertThat(prompt).contains("Never duplicate identical rows in both root rows and sheets")
    assertThat(prompt).doesNotContain("\\\"rows\\\":[[...]],\\\"sheets\\\":[...]")
  }

  private fun prepareSuccessfulExcelToolResult(): CompatPreparedInput {
    return AgentCompatRuntimeCoordinator.prepareInput(
      modelName = modelName,
      rawInput =
        """TOOL_RESULT
tool: excel_workbook
status: succeeded
payload:
created workbook.xlsx

You are in compatibility tool mode.
Use this tool result to answer the original user request directly.""",
      historyBudgetChars = 3600,
    )
  }

  private fun assertFreshToolContinuationAndRecordMetrics(continuation: CompatPreparedInput) {
    assertThat(continuation.requiresFreshConversation).isTrue()
    assertThat(continuation.freshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
    assertThat(continuation.historyStepCount).isEqualTo(1)

    AgentCompatRuntimeCoordinator.recordContinuationPreparation(
      modelName = modelName,
      prepareMs = 8.0,
      resetMs = 7.0,
      rawInputChars = continuation.rawInputChars,
      effectiveInputChars = continuation.effectiveInputChars,
      historyStepCount = continuation.historyStepCount,
      historyChars = continuation.historyChars,
    )
    val snapshot = AgentCompatRuntimeCoordinator.snapshot(modelName)
    assertThat(snapshot).isNotNull()
    assertThat(snapshot!!.lastFreshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
    assertThat(snapshot.continuationPrepareMsTotal).isGreaterThan(0.0)
    assertThat(snapshot.lastContinuationResetMs).isGreaterThan(0.0)
  }
}
