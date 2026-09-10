package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class Mcp259ExcelContinuationLatencyRegressionTest {
  private val modelName = "mcp259-gemma4-12b"

  // Exact structural failure class observed on the MCP258 phone run: the semantic call is complete,
  // but the model omitted one final outer JSON object brace before </tool_call>.
  private val recoveredExcelCall =
    """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""]],"sheets":[{"name":"LLM_Comparison","rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""]]}]}</tool_call>"""

  @Before
  fun setUp() {
    AgentCompatRuntimeCoordinator.clearAllForTest()
  }

  @After
  fun tearDown() {
    AgentCompatRuntimeCoordinator.clearAllForTest()
  }

  @Test
  fun recoveredExcelCall_keepsRuntimeAwaitingToolResult_andForcesFreshContinuation() {
    val initial =
      """COMPAT_AGENT_INSTRUCTIONS
You are running in Qwen-compatible tool mode.
Available compatibility tools:
- excel_workbook

USER_REQUEST
创建一个模型对比表格"""

    val preparedInitial =
      AgentCompatRuntimeCoordinator.prepareInput(
        modelName = modelName,
        rawInput = initial,
        historyBudgetChars = 3600,
      )
    assertThat(preparedInitial.requiresFreshConversation).isTrue()
    assertThat(preparedInitial.freshConversationReason).isEqualTo(COMPAT_FRESH_REASON_TOP_LEVEL)

    // MCP259 acceptance: runtime classification must use the same recovery parser as dispatch.
    // MCP258 proved the UI/Agent layer can execute this malformed-but-recoverable call; runtime must
    // therefore remain in awaitingToolResult state instead of prematurely completing the turn.
    val decision =
      AgentCompatRuntimeCoordinator.onGenerationCompleted(
        modelName = modelName,
        generatedText = recoveredExcelCall,
      )
    assertThat(decision.blockedRepeatedToolCall).isFalse()

    val toolResult =
      """TOOL_RESULT
tool: excel_workbook
status: succeeded
payload:
operation: xlsx_create
sheet_count: 1
file_created: true

You are in compatibility tool mode.
Use this tool result to answer the original user request directly."""

    val continuation =
      AgentCompatRuntimeCoordinator.prepareInput(
        modelName = modelName,
        rawInput = toolResult,
        historyBudgetChars = 3600,
      )

    assertThat(continuation.requiresFreshConversation).isTrue()
    assertThat(continuation.freshConversationReason)
      .isEqualTo(COMPAT_FRESH_REASON_TOOL_CONTINUATION)
    assertThat(continuation.historyStepCount).isEqualTo(1)
    assertThat(continuation.input).contains("TOOL_HISTORY")
    assertThat(continuation.input).contains("NEXT_ACTION")
    // Cross-turn history must not be re-injected into the tool continuation prefill.
    assertThat(continuation.input).doesNotContain("SESSION_HISTORY")
  }

  @Test
  fun compactExcelSchema_prefersSingleRowsPayload_withoutDuplicateSheets() {
    val prompt =
      buildCompatAgentInstructionPayloadForTest(
        baseSystemPrompt = "",
        selectedSkillSummaries = listOf("excel-workbook: create and edit Excel workbooks"),
      )

    val excelLine = prompt.lineSequence().first { it.contains("excel_workbook:") }
    assertThat(excelLine).contains("sheet_name")
    assertThat(excelLine).contains("Omit operation, input_path, and output_path")
    assertThat(excelLine).contains("Never duplicate identical rows")
    assertThat(excelLine).doesNotContain("\"sheets\"")
  }
}
