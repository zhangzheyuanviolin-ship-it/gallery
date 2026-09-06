package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Mcp255WireEvidenceTest {
  @Test fun chatRecord_compactCallExcelWorkbook_isRecognized() {
    val raw = "<|tool_call>call:excel_workbook{operation:\"create\",rows:[[\"国家\", \"模型名称\", \"开发者\", \"模型特点\"],[\"中国\", \"Qwen (通义千问)\", \"阿里巴巴\", \"强大的中文理解能力，支持多语言，在代码和数学任务上表现优异\"]]}<tool_call|>"
    val call = parseCompatToolCall(raw)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
    assertThat(call.arguments.getString("operation")).isEqualTo("create")
  }

  @Test fun chatRecord_standardXmlNestedExcelCall_isRecognized() {
    val raw = "<tool_call>{\"tool\":\"excel_workbook\",\"arguments\":{\"operation\":\"create\",\"rows\":[[[\"项目名称\",\"预算\",\"进度\",\"负责人\"],[\"市场推广\",\"50000\",\"80%\",\"张三\"]]],\"sheets\":[{\"name\":\"项目进度表\"}]}}</tool_call>"
    val call = parseCompatToolCall(raw)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
  }

  @Test fun defensiveZeroWidthVariant_isRecognized() {
    val raw = "\u200B<tool_call>\u200B{\"tool\":\"excel_workbook\",\"arguments\":{\"operation\":\"create\",\"rows\":[[\"A\",\"B\"]]}}\u200B</tool_call>\u200B"
    val call = parseCompatToolCall(raw)
    assertThat(call).isNotNull()
    assertThat(call!!.toolName).isEqualTo("excel_workbook")
  }
}
