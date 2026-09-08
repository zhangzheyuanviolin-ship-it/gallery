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

    // MCP258_TRUNCATED_TOOL_OBJECT_CLOSE_REPAIR
    // Phone evidence on 2026-09-08 contained a semantically complete Excel call whose arrays and
    // strings were balanced, but the model omitted exactly the final outer JSON object brace before
    // </tool_call>. Recover only when the canonical wrapper exists and a structural scan proves that
    // strings and arrays are complete, no closing delimiter ever underflows, and only one or two
    // object braces remain open. This turns recoverable tool intent into execution instead of merely
    // hiding the protocol leak.
    val repairedStructureText = repairTruncatedToolObjectClosures(repairedExcelText)
    if (repairedStructureText != rawText) {
      parseCompatToolCall(repairedStructureText)?.let { return it }
      val repairedCanonical = CompatToolCallWireAdapter.normalizeFirstToolCall(repairedStructureText)
      if (!repairedCanonical.isNullOrBlank()) {
        parseCompatToolCall(repairedCanonical)?.let { return it }
        val repairedCanonicalStructure = repairTruncatedToolObjectClosures(repairedCanonical)
        if (repairedCanonicalStructure != repairedCanonical) {
          parseCompatToolCall(repairedCanonicalStructure)?.let { return it }
        }
      }
    }

    // Then normalize Qwen/Gemma/GLM/Mistral/DeepSeek/GPT-OSS and other supported wire dialects.
    val canonical = CompatToolCallWireAdapter.normalizeFirstToolCall(rawText) ?: return null
    if (canonical.isBlank()) return null
    parseCompatToolCall(canonical)?.let { return it }
    val repairedCanonical = repairTruncatedToolObjectClosures(canonical)
    if (repairedCanonical != canonical) {
      parseCompatToolCall(repairedCanonical)?.let { return it }
    }
    return null
  }

  internal fun hasStrongToolSignal(rawText: String): Boolean =
    CompatToolCallWireAdapter.hasStrongToolSignal(rawText)

  private fun repairTruncatedToolObjectClosures(rawText: String): String {
    val openTag = "<tool_call>"
    val closeTag = "</tool_call>"
    val openIndex = rawText.indexOf(openTag, ignoreCase = true)
    if (openIndex < 0) return rawText
    val bodyStart = openIndex + openTag.length
    val closeIndex = rawText.indexOf(closeTag, startIndex = bodyStart, ignoreCase = true)
    if (closeIndex < 0) return rawText

    val body = rawText.substring(bodyStart, closeIndex)
    if (!body.trimStart().startsWith("{")) return rawText

    var objectDepth = 0
    var arrayDepth = 0
    var inString = false
    var escaping = false
    var sawObject = false
    for (char in body) {
      if (escaping) {
        escaping = false
        continue
      }
      if (char == '\\' && inString) {
        escaping = true
        continue
      }
      if (char == '"') {
        inString = !inString
        continue
      }
      if (inString) continue

      when (char) {
        '{' -> {
          objectDepth++
          sawObject = true
        }
        '}' -> {
          objectDepth--
          if (objectDepth < 0) return rawText
        }
        '[' -> arrayDepth++
        ']' -> {
          arrayDepth--
          if (arrayDepth < 0) return rawText
        }
      }
    }

    if (!sawObject || inString || escaping || arrayDepth != 0 || objectDepth !in 1..2) {
      return rawText
    }

    val insertionOffset = body.indexOfLast { !it.isWhitespace() } + 1
    if (insertionOffset <= 0) return rawText
    val repairedBody =
      body.substring(0, insertionOffset) + "}".repeat(objectDepth) + body.substring(insertionOffset)
    return rawText.substring(0, bodyStart) + repairedBody + rawText.substring(closeIndex)
  }

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
