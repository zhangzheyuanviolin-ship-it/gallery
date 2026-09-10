package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class Mcp256PhoneToolCallRegressionTest {
  private val fixtureOne =
    """<tool_call>{"tool":"excel_workbook","arguments":{"operation":"create","rows":[["项目 ID","项目名称","负责人","截止日期","当前进度","状态"],"P001","人工智能模型训练","张三","2023-12-31","85%","进行中"],"P002","自动化报表系统","李四","2024-01-15","100%","已完成"],"sheets":[{"name":"项目进度表","rows":[["项目 ID","项目名称","负责人","截止日期","当前进度","状态"],"P001","人工智能模型训练","张三","2023-12-31","85%","进行中"],"P002","自动化报表系统","李四","2024-01-15","100%","已完成"]}]}}</tool_call>"""

  private val fixtureTwo =
    """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]],"sheets":[{"name":"LLM_Comparison","rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]]}]}}</tool_call>"""

  @Test
  fun exactPhoneFixtureOne_isParsedAtCompletionBoundaryAndFlattenedRowsRecover() {
    assertThat(AgentTextToolCallFallback.hasStrongToolSignal(fixtureOne)).isTrue()
    val call = AgentTextToolCallFallback.parse(fixtureOne)
    assertThat(call).isNotNull()
    // Wire tool names use underscores; the built-in skill id uses hyphens.
    assertThat(call!!.toolName).isEqualTo("excel_workbook")

    val args = JSONObject(call.arguments.toString())
    AgentExcelMcp255Compat.normalizeBeforeRouting(EXCEL_WORKBOOK_SKILL_NAME, args)
    val rows = args.getJSONArray("rows")
    assertThat(rows.length()).isEqualTo(3)
    assertThat(rows.getJSONArray(1).length()).isEqualTo(6)
    assertThat(rows.getJSONArray(1).getString(0)).isEqualTo("P001")
    assertThat(rows.getJSONArray(2).getString(0)).isEqualTo("P002")
    val sheetRows = args.getJSONArray("sheets").getJSONObject(0).getJSONArray("rows")
    assertThat(sheetRows.length()).isEqualTo(3)
    assertThat(sheetRows.getJSONArray(2).getString(1)).isEqualTo("自动化报表系统")
  }

  @Test
  fun exactPhoneFixtureTwo_isParsedAndMcp258PromotesUnambiguousCreate() {
    assertThat(AgentTextToolCallFallback.hasStrongToolSignal(fixtureTwo)).isTrue()
    val call = AgentTextToolCallFallback.parse(fixtureTwo)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
    assertThat(call.arguments.has("operation")).isFalse()

    val normalized =
      AgentOfficeTruthGuard.prepareCompatRequest(
        skillName = EXCEL_WORKBOOK_SKILL_NAME,
        rawArguments = JSONObject(call.arguments.toString()),
      )
    // MCP258: rows/sheets with no input workbook are an unambiguous create request and must move
    // directly toward the XLSX writer instead of lingering at the office_auto intermediate state.
    assertThat(normalized.getString("operation")).isEqualTo("xlsx_create")
    val rows = normalized.getJSONArray("rows")
    assertThat(rows.length()).isEqualTo(7)
    for (i in 0 until rows.length()) {
      assertThat(rows.getJSONArray(i).length()).isEqualTo(4)
    }
    val sheetRows = normalized.getJSONArray("sheets").getJSONObject(0).getJSONArray("rows")
    for (i in 0 until sheetRows.length()) {
      assertThat(sheetRows.getJSONArray(i).length()).isEqualTo(4)
    }
  }

  @Test
  fun nonEmptyOverflowColumn_isPreserved() {
    val raw = JSONObject("""{"rows":[["A","B"],["1","2","KEEP"]]}""")
    AgentExcelMcp255Compat.normalizeBeforeRouting(EXCEL_WORKBOOK_SKILL_NAME, raw)
    assertThat(raw.getJSONArray("rows").getJSONArray(1).length()).isEqualTo(3)
    assertThat(raw.getJSONArray("rows").getJSONArray(1).getString(2)).isEqualTo("KEEP")
  }

  @Test
  fun alternateWireDialect_isNormalizedThroughSharedFallback() {
    val raw = "<|tool_call>call:excel_workbook{operation:\"create\",rows:[[\"A\",\"B\"],[\"1\",\"2\"]]}<tool_call|>"
    val call = AgentTextToolCallFallback.parse(raw)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
    assertThat(call.arguments.getString("operation")).isEqualTo("create")
  }
}
