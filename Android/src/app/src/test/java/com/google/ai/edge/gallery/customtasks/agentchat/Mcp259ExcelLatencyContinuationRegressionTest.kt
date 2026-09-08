package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
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

    // One final outer object brace is missing. MCP258 can safely repair this envelope and dispatch it.
    // MCP259 requires the runtime coordinator to make the same decision before the tool executes.
    val truncated =
      """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["A","B"],["1","2"]],"sheet_name":"Sheet1"}</tool_call>"""
    val decision =
      AgentCompatRuntimeCoordinator.onGenerationCompleted(
        modelName = modelName,
        generatedText = truncated,
      )
    assertThat(decision.blockedRepeatedToolCall).isFalse()

    val continuation =
      AgentCompatRuntimeCoordinator.prepareInput(
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

    val continuation =
      AgentCompatRuntimeCoordinator.prepareInput(
        modelName = modelName,
        rawInput = "TOOL_RESULT\ntool: excel_workbook\nstatus: succeeded\npayload:\nok",
        historyBudgetChars = 3600,
      )
    assertThat(continuation.requiresFreshConversation).isTrue()
    assertThat(continuation.freshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
  }

  @Test
  fun ordinaryFinalAnswer_doesNotPretendToAwaitToolResult() {
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
}
