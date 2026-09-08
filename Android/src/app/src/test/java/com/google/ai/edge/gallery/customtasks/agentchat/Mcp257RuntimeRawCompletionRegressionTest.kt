package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Mcp257RuntimeRawCompletionRegressionTest {
  private val exactPhoneFixture =
    """<tool_call>{"tool":"excel_workbook","arguments":{"rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]],"sheets":[{"name":"LLM_Comparison","rows":[["国家","模型名称","开发者","模型特点"],["中国","Qwen (通义千问)","阿里巴巴","多语言能力强，在代码和数学能力上有显著表现，生态完善。",""],["中国","DeepSeek","DeepSeek-AI","极高的推理效率，在开源社区极具影响力，性价比高。",""],["中国","Ernie Bot (文心一言)","百度","国内用户基数大，擅长中文语境下的各种任务，应用场景丰富。",""],["美国","GPT-4o","OpenAI","多模态能力极强，具备极高的逻辑推理和创意写作能力。",""],["美国","Claude 3.5 Sonnet","Anthropic","文字风格更自然，遵循指令能力强，在编程任务中表现优异。",""],["美国","Llama 3.1","Meta","最著名的开源/开放权重模型，推动了开源社区的快速发展。",""]]}]}}</tool_call>"""

  @Test
  fun runtimeRawCompletion_winsWhenUiTailLostOpeningToolMarker() {
    // Reproduce the structural MCP256 blind spot: UI segmentation can leave the last Agent text
    // containing only a tail that no longer includes the opening <tool_call> marker.
    val uiTail = exactPhoneFixture.substringAfter("<tool_call>{\"tool\":")
    assertThat(AgentTextToolCallFallback.hasStrongToolSignal(uiTail)).isFalse()

    val resolved =
      AgentCompletionTextResolver.resolve(
        rawCompletion = exactPhoneFixture,
        uiLastAgentText = uiTail,
      )
    assertThat(resolved).isEqualTo(exactPhoneFixture)
    assertThat(AgentTextToolCallFallback.hasStrongToolSignal(resolved)).isTrue()
    val call = AgentTextToolCallFallback.parse(resolved)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
  }

  @Test
  fun chunkedRuntimeStream_reassemblesExactPhoneToolCallBeforeDispatch() {
    val cut1 = 8
    val cut2 = exactPhoneFixture.indexOf("DeepSeek")
    val cut3 = exactPhoneFixture.indexOf("LLM_Comparison")
    assertThat(cut2).isGreaterThan(cut1)
    assertThat(cut3).isGreaterThan(cut2)

    val chunks =
      listOf(
        exactPhoneFixture.substring(0, cut1),
        exactPhoneFixture.substring(cut1, cut2),
        exactPhoneFixture.substring(cut2, cut3),
        exactPhoneFixture.substring(cut3),
        "", // LiteRT-LM MessageCallback.onDone emits an empty done chunk.
      )
    val runtimeRaw = StringBuilder()
    chunks.forEach(runtimeRaw::append)

    val resolved = AgentCompletionTextResolver.resolve(runtimeRaw.toString(), "irrelevant ui tail")
    assertThat(resolved).isEqualTo(exactPhoneFixture)
    val call = AgentTextToolCallFallback.parse(resolved)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
  }

  @Test
  fun blankRuntimeCapture_fallsBackToUiTextForBackwardCompatibility() {
    val ui = "普通最终回复"
    assertThat(AgentCompletionTextResolver.resolve("", ui)).isEqualTo(ui)
    assertThat(AgentCompletionTextResolver.resolve(null, ui)).isEqualTo(ui)
  }
}
