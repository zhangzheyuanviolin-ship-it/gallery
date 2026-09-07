package com.google.ai.edge.gallery.customtasks.agentchat

/**
 * MCP256 completion-boundary fallback for models that emit textual tool-call wire formats even
 * when the session was configured for native tool calling.
 *
 * Model capability/mode selection is a generation hint, not proof of the syntax actually emitted.
 * The completion boundary therefore inspects the observed text and normalizes it through the
 * family-aware wire adapter before giving up.
 */
internal object AgentTextToolCallFallback {
  internal fun parse(rawText: String): ParsedCompatToolCall? {
    if (rawText.isBlank()) return null

    // Keep the established strict parser as the fastest path for canonical <tool_call> JSON.
    parseCompatToolCall(rawText)?.let { return it }

    // MCP256_EXCEL_PREMATURE_ROWS_CLOSE_REPAIR
    // Exact phone evidence showed a model closing a rows array after the first flattened data row,
    // then continuing to emit additional scalar cells before the next object key. Repair only this
    // structurally provable Excel corruption: after a candidate rows-array close, a comma must be
    // followed by an object key (a quoted string plus ':'). If it is followed by another scalar
    // value instead, that close bracket cannot be valid at that object position and is removed.
    val repairedExcelText = repairPrematureExcelRowsArrayClosures(rawText)
    if (repairedExcelText != rawText) {
      parseCompatToolCall(repairedExcelText)?.let { return it }
      val repairedCanonical = CompatToolCallWireAdapter.normalizeFirstToolCall(repairedExcelText)
      if (!repairedCanonical.isNullOrBlank()) {
        parseCompatToolCall(repairedCanonical)?.let { return it }
      }
    }

    // Then normalize Qwen/Gemma/GLM/Mistral/DeepSeek/GPT-OSS and other supported wire dialects.
    val canonical = CompatToolCallWireAdapter.normalizeFirstToolCall(rawText) ?: return null
    if (canonical.isBlank()) return null
    return parseCompatToolCall(canonical)
  }

  internal fun hasStrongToolSignal(rawText: String): Boolean =
    CompatToolCallWireAdapter.hasStrongToolSignal(rawText)

  private fun repairPrematureExcelRowsArrayClosures(rawText: String): String {
    if (!rawText.contains("excel_workbook", ignoreCase = true) || !rawText.contains("\"rows\"")) {
      return rawText
    }

    var text = rawText
    repeat(8) {
      val badClose = findPrematureRowsArrayClose(text) ?: return text
      text = text.removeRange(badClose, badClose + 1)
    }
    return text
  }

  private fun findPrematureRowsArrayClose(text: String): Int? {
    var searchFrom = 0
    while (searchFrom < text.length) {
      val keyIndex = text.indexOf("\"rows\"", startIndex = searchFrom)
      if (keyIndex < 0) return null
      val colonIndex = text.indexOf(':', startIndex = keyIndex + 6)
      if (colonIndex < 0) return null
      val arrayStart = text.indexOf('[', startIndex = colonIndex + 1)
      if (arrayStart < 0) return null

      var depth = 0
      var inString = false
      var escaping = false
      var index = arrayStart
      while (index < text.length) {
        val char = text[index]
        if (escaping) {
          escaping = false
          index++
          continue
        }
        if (char == '\\' && inString) {
          escaping = true
          index++
          continue
        }
        if (char == '"') {
          inString = !inString
          index++
          continue
        }
        if (inString) {
          index++
          continue
        }

        when (char) {
          '[' -> depth++
          ']' -> {
            depth--
            if (depth == 0) {
              if (isScalarContinuationAfterArrayClose(text, index)) return index
              searchFrom = index + 1
              break
            }
          }
        }
        index++
      }
      if (index >= text.length) return null
    }
    return null
  }

  private fun isScalarContinuationAfterArrayClose(text: String, closeIndex: Int): Boolean {
    var index = skipWhitespace(text, closeIndex + 1)
    if (index >= text.length || text[index] != ',') return false
    index = skipWhitespace(text, index + 1)
    if (index >= text.length) return false

    // A valid rows value inside a JSON object is followed by another property key or object close.
    if (text[index] == '"') {
      val stringEnd = findStringEnd(text, index) ?: return false
      val afterString = skipWhitespace(text, stringEnd + 1)
      return afterString >= text.length || text[afterString] != ':'
    }
    if (text[index] == '}' || text[index] == ']') return false

    // Numbers, booleans, null, arrays, or objects after the candidate close are array continuations,
    // not legal object keys, so the close is structurally premature.
    return true
  }

  private fun findStringEnd(text: String, startQuote: Int): Int? {
    var escaping = false
    for (index in startQuote + 1 until text.length) {
      val char = text[index]
      if (escaping) {
        escaping = false
        continue
      }
      if (char == '\\') {
        escaping = true
        continue
      }
      if (char == '"') return index
    }
    return null
  }

  private fun skipWhitespace(text: String, start: Int): Int {
    var index = start
    while (index < text.length && text[index].isWhitespace()) index++
    return index
  }
}
