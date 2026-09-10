package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class Mcp258ExcelExecutionRecoveryTest {
  // Exact 2026-09-08 phone output. The model omitted one final outer JSON '}' before </tool_call>.
  private val latestPhoneFixture =
    """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]],"sheets":[{"name":"LLM_Comparison","rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]]}]}</tool_call>"""

  @Test
  fun latestPhoneFixture_isRecoveredAndRoutedToXlsxCreateBackend() {
    assertThat(AgentTextToolCallFallback.hasStrongToolSignal(latestPhoneFixture)).isTrue()

    val call = AgentTextToolCallFallback.parse(latestPhoneFixture)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")

    val request =
      AgentOfficeTruthGuard.prepareCompatRequest(
        skillName = EXCEL_WORKBOOK_SKILL_NAME,
        rawArguments = JSONObject(call.arguments.toString()),
      )

    // MCP258 acceptance criterion: this must become an executable XLSX create request, not merely
    // a hidden protocol error and not an unresolved office_auto intermediate state.
    assertThat(request.getString("operation")).isEqualTo("xlsx_create")
    assertThat(request.getString("_mcp252_resolution")).isEqualTo("payload_unambiguous_create")
    assertThat(AgentOfficeDocumentSupport.supports(request.getString("operation"))).isTrue()

    val rows = request.getJSONArray("rows")
    assertThat(rows.length()).isEqualTo(7)
    for (i in 0 until rows.length()) {
      assertThat(rows.getJSONArray(i).length()).isEqualTo(4)
    }

    val sheets = request.getJSONArray("sheets")
    assertThat(sheets.length()).isEqualTo(1)
    assertThat(sheets.getJSONObject(0).getString("name")).isEqualTo("LLM_Comparison")
    val sheetRows = sheets.getJSONObject(0).getJSONArray("rows")
    assertThat(sheetRows.length()).isEqualTo(7)
    for (i in 0 until sheetRows.length()) {
      assertThat(sheetRows.getJSONArray(i).length()).isEqualTo(4)
    }
  }

  @Test
  fun structuralRepair_doesNotGuessWhenArrayIsStillOpen() {
    val broken = """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["A","B"],["1","2"]}</tool_call>"""
    assertThat(AgentTextToolCallFallback.parse(broken)).isNull()
  }

  @Test
  fun structuralRepair_doesNotGuessWhenStringIsStillOpen() {
    val broken = """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["A","B"],["1","unterminated]]}}</tool_call>"""
    assertThat(AgentTextToolCallFallback.parse(broken)).isNull()
  }

  @Test
  fun explicitReadIsNeverReclassifiedAsCreate() {
    val raw = JSONObject("""{"operation":"read","input_path":"file/existing.xlsx"}""")
    val request =
      AgentOfficeTruthGuard.prepareCompatRequest(
        skillName = EXCEL_WORKBOOK_SKILL_NAME,
        rawArguments = raw,
      )
    assertThat(request.getString("operation")).isEqualTo("xlsx_read")
  }
}
