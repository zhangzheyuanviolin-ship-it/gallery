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

    // Then normalize Qwen/Gemma/GLM/Mistral/DeepSeek/GPT-OSS and other supported wire dialects.
    val canonical = CompatToolCallWireAdapter.normalizeFirstToolCall(rawText) ?: return null
    if (canonical.isBlank()) return null
    return parseCompatToolCall(canonical)
  }

  internal fun hasStrongToolSignal(rawText: String): Boolean =
    CompatToolCallWireAdapter.hasStrongToolSignal(rawText)
}
