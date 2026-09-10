package com.google.ai.edge.gallery.customtasks.agentchat

/**
 * MCP257: selects the authoritative completion text for textual tool dispatch.
 *
 * Runtime-captured raw output is preferred because UI chat text can be split by progress panels or
 * other message insertions during streaming. The UI message is retained only as a compatibility
 * fallback for runtimes/callers that have not supplied a raw completion snapshot.
 */
internal object AgentCompletionTextResolver {
  internal fun resolve(rawCompletion: String?, uiLastAgentText: String?): String {
    return rawCompletion?.takeIf { it.isNotBlank() } ?: uiLastAgentText.orEmpty()
  }
}
