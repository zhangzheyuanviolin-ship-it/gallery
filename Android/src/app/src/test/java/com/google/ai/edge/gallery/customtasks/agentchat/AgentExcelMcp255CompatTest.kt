package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class AgentExcelMcp255CompatTest {
  @Test fun evidence204608_stringSheetAndFlattenedTail_areRecovered() {
    val raw = JSONObject("""{"rows":[["产品名称","单价","库存数量","销售目标"],"智能手机","5999","100","50"],"sheets":["销售数据"],"operations":[]}""")
    val normalized = AgentOfficeTruthGuard.prepareCompatRequest(EXCEL_WORKBOOK_SKILL_NAME, raw)
    // MCP258: rows/sheets with no input workbook are unambiguously a creation request. The
    // compatibility layer must resolve directly to xlsx_create so the call proceeds to the XLSX
    // writer instead of stopping at the old office_auto intermediate state.
    assertThat(normalized.getString("operation")).isEqualTo("xlsx_create")
    val sheet = normalized.getJSONArray("sheets").getJSONObject(0)
    assertThat(sheet.getString("name")).isEqualTo("销售数据")
    assertThat(sheet.getJSONArray("rows").length()).isEqualTo(2)
    assertThat(sheet.getJSONArray("rows").getJSONArray(1).getString(0)).isEqualTo("智能手机")
  }

  @Test fun evidence204544_createSheetInsertRows_isPromotedToCreate() {
    val raw = JSONObject("""{"operations":[{"operation":"create_sheet","sheet_name":"演示表格"},{"operation":"insert_rows","rows":[[["项目名称","描述","状态","优先级"],["数据分析","自动化报表生成","已完成","高"],["模型训练","模型微调与测试","进行中","中"],["文档编写","技术文档更新","待开始","低"]]]}]}""")
    val normalized = AgentOfficeTruthGuard.prepareCompatRequest(EXCEL_WORKBOOK_SKILL_NAME, raw)
    assertThat(normalized.getString("operation")).isEqualTo("xlsx_create")
    assertThat(normalized.has("operations")).isFalse()
    val sheet = normalized.getJSONArray("sheets").getJSONObject(0)
    assertThat(sheet.getString("name")).isEqualTo("演示表格")
    assertThat(sheet.getJSONArray("rows").length()).isEqualTo(4)
  }

  @Test fun chatRecord_threeDimensionalRowsAttachToSingleSheet() {
    val raw = JSONObject("""{"operation":"create","rows":[[["项目名称","预算","进度","负责人"],["市场推广","50000","80%","张三"],["产品研发","200000","40%","李四"],["人力资源","30000","100%","王五"],["行政办公","10000","90%","赵六"]]],"sheets":[{"name":"项目进度表"}]}""")
    val normalized = AgentOfficeTruthGuard.prepareCompatRequest(EXCEL_WORKBOOK_SKILL_NAME, raw)
    assertThat(normalized.getString("operation")).isEqualTo("xlsx_create")
    val rows = normalized.getJSONArray("sheets").getJSONObject(0).getJSONArray("rows")
    assertThat(rows.length()).isEqualTo(5)
    assertThat(rows.getJSONArray(4).getString(3)).isEqualTo("赵六")
  }

  @Test fun existingWorkbook_sameDialectRemainsModifyAndExpandsRows() {
    val raw = JSONObject("""{"input_path":"file/demo.xlsx","operations":[{"operation":"create_sheet","sheet_name":"新增表"},{"operation":"insert_rows","rows":[[["A","B"],["1","2"]]]}]}""")
    val normalized = AgentOfficeTruthGuard.prepareCompatRequest(EXCEL_WORKBOOK_SKILL_NAME, raw)
    assertThat(normalized.getString("operation")).isEqualTo("office_auto")
    val ops = normalized.getJSONArray("operations")
    assertThat(ops.length()).isEqualTo(3)
    assertThat(ops.getJSONObject(0).getString("action")).isEqualTo("add_sheet")
    assertThat(ops.getJSONObject(1).getString("action")).isEqualTo("add_row")
    assertThat(ops.getJSONObject(2).getJSONObject("params").getJSONArray("values").getString(1)).isEqualTo("2")
  }

  @Test fun normalTwoDimensionalRows_areNotChanged() {
    val rows = JSONArray("""[["A","B"],["1","2"]]""")
    assertThat(AgentExcelMcp255Compat.normalizeRows(rows).toString()).isEqualTo(rows.toString())
  }
}
