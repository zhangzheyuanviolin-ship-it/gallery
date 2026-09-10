package com.google.ai.edge.gallery.customtasks.agentchat

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP255 Excel compatibility derived from the exact MCP254 field evidence from 2026-09-06.
 *
 * The observed small model emitted:
 *  - sheets:["销售数据"] instead of sheet objects;
 *  - a valid header row followed by one flattened scalar row;
 *  - create_sheet + insert_rows operations for a brand new workbook;
 *  - one extra array nesting around rows.
 *
 * This normalizer runs before MCP253 nested-operation canonicalization so evidence-bearing fields
 * are not discarded. Existing-workbook requests keep modify semantics.
 */
object AgentExcelMcp255Compat {
  const val META_NORMALIZED = "_mcp255_excel_normalized"
  const val META_PROMOTED_CREATE_SCRIPT = "_mcp255_excel_promoted_create_script"
  const val META_REPAIRED_ROWS = "_mcp255_excel_repaired_rows"

  fun normalizeBeforeRouting(skillName: String, request: JSONObject) {
    if (skillName != EXCEL_WORKBOOK_SKILL_NAME) return

    var changed = false
    if (promoteNewWorkbookConstructionScript(request)) changed = true
    if (normalizeDirectRowsAndSheets(request)) changed = true
    if (normalizeExistingWorkbookConstructionAliases(request)) changed = true
    if (changed) request.put(META_NORMALIZED, true)
  }

  /**
   * A no-input operation script containing only sheet/row construction actions describes creation,
   * not modification. This closes evidence 20260906-204544-431 without guessing destructive edits.
   */
  private fun promoteNewWorkbookConstructionScript(request: JSONObject): Boolean {
    if (request.optString("input_path").isNotBlank()) return false
    val operations = request.optJSONArray("operations") ?: return false
    if (operations.length() == 0) return false

    val parsed = mutableListOf<Pair<String, JSONObject>>()
    for (i in 0 until operations.length()) {
      val raw = operations.optJSONObject(i) ?: return false
      val action = actionOf(raw)
      if (action !in CREATE_SCRIPT_ACTIONS) return false
      parsed += action to raw
    }

    val sheetRows = linkedMapOf<String, JSONArray>()
    var currentSheet = request.optString("sheet_name").ifBlank { "Sheet1" }
    var sawConstruction = false

    for ((action, raw) in parsed) {
      when (action) {
        in SHEET_CREATE_ACTIONS -> {
          currentSheet = firstString(raw, "sheet_name", "sheet", "name", "worksheet").ifBlank { "Sheet${sheetRows.size + 1}" }
          sheetRows.putIfAbsent(currentSheet, JSONArray())
          sawConstruction = true
        }
        in ROWS_INSERT_ACTIONS -> {
          val target = firstString(raw, "sheet_name", "sheet", "worksheet").ifBlank { currentSheet }
          val rowsValue = firstValue(raw, "rows", "data", "values", "row")
          val rows = normalizeRows(rowsValue) ?: return false
          val destination = sheetRows.getOrPut(target) { JSONArray() }
          for (r in 0 until rows.length()) destination.put(rows.opt(r))
          currentSheet = target
          sawConstruction = true
        }
      }
    }
    if (!sawConstruction || sheetRows.isEmpty()) return false

    val sheets = JSONArray()
    sheetRows.forEach { (name, rows) ->
      sheets.put(JSONObject().put("name", name).put("rows", rows))
    }
    request.put("sheets", sheets)
    request.remove("operations")
    request.put("operation", "create")
    request.put(META_PROMOTED_CREATE_SCRIPT, true)
    return true
  }

  /** Canonicalize direct create payloads before the strict XLSX backend sees them. */
  private fun normalizeDirectRowsAndSheets(request: JSONObject): Boolean {
    var changed = false
    val originalRows = request.opt("rows")
    val normalizedRows = normalizeRows(originalRows)
    if (normalizedRows != null && originalRows is JSONArray && normalizedRows.toString() != originalRows.toString()) {
      request.put("rows", normalizedRows)
      request.put(META_REPAIRED_ROWS, true)
      changed = true
    }

    val sheets = request.optJSONArray("sheets") ?: return changed
    if (sheets.length() == 0) return changed
    val rebuilt = JSONArray()
    var sheetsChanged = false
    for (i in 0 until sheets.length()) {
      when (val raw = sheets.opt(i)) {
        is String -> {
          val obj = JSONObject().put("name", raw.ifBlank { "Sheet${i + 1}" })
          if (i == 0 && normalizedRows != null) obj.put("rows", JSONArray(normalizedRows.toString()))
          rebuilt.put(obj)
          sheetsChanged = true
        }
        is JSONObject -> {
          val obj = JSONObject(raw.toString())
          val localRows = normalizeRows(obj.opt("rows"))
          if (localRows != null && (obj.optJSONArray("rows")?.toString() != localRows.toString())) {
            obj.put("rows", localRows)
            sheetsChanged = true
          }
          if (i == 0 && !obj.has("rows") && normalizedRows != null) {
            obj.put("rows", JSONArray(normalizedRows.toString()))
            sheetsChanged = true
          }
          rebuilt.put(obj)
        }
        else -> rebuilt.put(raw)
      }
    }
    if (sheetsChanged) {
      request.put("sheets", rebuilt)
      changed = true
    }
    return changed
  }

  /**
   * The same create_sheet / insert_rows dialect is useful against an existing workbook too. Keep
   * modify routing when input_path exists, but translate it to the backend's add_sheet/add_row API.
   */
  private fun normalizeExistingWorkbookConstructionAliases(request: JSONObject): Boolean {
    if (request.optString("input_path").isBlank()) return false
    val source = request.optJSONArray("operations") ?: return false
    if (source.length() == 0) return false
    val out = JSONArray()
    var changed = false
    var currentSheet = request.optString("sheet_name")

    for (i in 0 until source.length()) {
      val raw = source.optJSONObject(i)
      if (raw == null) {
        out.put(source.opt(i))
        continue
      }
      val action = actionOf(raw)
      when (action) {
        in SHEET_CREATE_ACTIONS -> {
          val name = firstString(raw, "sheet_name", "sheet", "name", "worksheet").ifBlank { "Sheet${i + 1}" }
          currentSheet = name
          out.put(JSONObject().put("action", "add_sheet").put("params", JSONObject().put("name", name)))
          changed = true
        }
        in ROWS_INSERT_ACTIONS -> {
          val target = firstString(raw, "sheet_name", "sheet", "worksheet").ifBlank { currentSheet }
          val rows = normalizeRows(firstValue(raw, "rows", "data", "values", "row"))
          if (rows == null) {
            out.put(raw)
          } else {
            for (r in 0 until rows.length()) {
              val row = rows.optJSONArray(r) ?: continue
              val params = JSONObject().put("values", row)
              if (target.isNotBlank()) params.put("sheet", target)
              out.put(JSONObject().put("action", "add_row").put("params", params))
            }
            changed = true
          }
        }
        else -> out.put(raw)
      }
    }
    if (changed) request.put("operations", out)
    return changed
  }

  /**
   * Normalize a row payload to a two-dimensional array when recovery is unambiguous.
   * - [[ [r1], [r2] ]] -> [[r1], [r2]]
   * - [headerRow, scalar, scalar, ...] -> [headerRow, reconstructed rows] when widths divide evenly
   * - [scalar, scalar] -> [[scalar, scalar]]
   */
  internal fun normalizeRows(value: Any?): JSONArray? {
    val original = value as? JSONArray ?: return null
    var rows = JSONArray(original.toString())

    while (rows.length() == 1) {
      val first = rows.optJSONArray(0) ?: break
      if (first.length() == 0 || first.opt(0) !is JSONArray) break
      rows = JSONArray(first.toString())
    }

    if (rows.length() == 0) return rows
    if ((0 until rows.length()).all { rows.opt(it) is JSONArray }) return rows
    if ((0 until rows.length()).none { rows.opt(it) is JSONArray }) return JSONArray().put(rows)

    val firstRow = rows.optJSONArray(0) ?: return rows
    val width = firstRow.length()
    if (width <= 0) return rows
    val rebuilt = JSONArray().put(JSONArray(firstRow.toString()))
    val scalarTail = mutableListOf<Any?>()
    for (i in 1 until rows.length()) {
      val item = rows.opt(i)
      if (item is JSONArray) {
        if (scalarTail.isNotEmpty()) return rows
        rebuilt.put(JSONArray(item.toString()))
      } else {
        scalarTail += item
      }
    }
    if (scalarTail.isEmpty() || scalarTail.size % width != 0) return rows
    scalarTail.chunked(width).forEach { chunk ->
      val row = JSONArray()
      chunk.forEach { row.put(it) }
      rebuilt.put(row)
    }
    return rebuilt
  }

  private fun actionOf(raw: JSONObject): String =
    firstString(raw, "action", "type", "operation", "op", "command", "mode")
      .trim().lowercase(Locale.US).replace('-', '_').replace(' ', '_')

  private fun firstString(obj: JSONObject, vararg keys: String): String {
    for (key in keys) {
      val value = obj.optString(key).trim()
      if (value.isNotBlank()) return value
    }
    return ""
  }

  private fun firstValue(obj: JSONObject, vararg keys: String): Any? {
    for (key in keys) if (obj.has(key) && !obj.isNull(key)) return obj.opt(key)
    return null
  }

  private val SHEET_CREATE_ACTIONS = setOf("create_sheet", "new_sheet", "add_sheet", "create_worksheet", "add_worksheet")
  private val ROWS_INSERT_ACTIONS = setOf("insert_rows", "add_rows", "append_rows", "write_rows", "insert_row", "add_row", "append_row")
  private val CREATE_SCRIPT_ACTIONS = SHEET_CREATE_ACTIONS + ROWS_INSERT_ACTIONS
}
