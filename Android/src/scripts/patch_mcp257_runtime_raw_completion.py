#!/usr/bin/env python3
"""MCP257: route textual tool dispatch from runtime-complete raw text, not fragile UI tail text.

Run after MCP251-256 materialization. The patch is fail-closed on every structural anchor.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/google/ai/edge/gallery"
AGENT = JAVA / "customtasks/agentchat"
VIEWMODEL = JAVA / "ui/llmchat/LlmChatViewModel.kt"
SCREEN = JAVA / "ui/llmchat/LlmChatScreen.kt"
AGENT_SCREEN = AGENT / "AgentChatScreen.kt"


def require_count(text: str, needle: str, expected: int, label: str) -> None:
    count = text.count(needle)
    if count != expected:
        raise SystemExit(f"MCP257 fail-closed: {label} count={count}, expected={expected}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    require_count(text, old, 1, label)
    return text.replace(old, new, 1)


def patch_viewmodel(text: str) -> str:
    marker = "MCP257_RUNTIME_RAW_COMPLETION"
    if marker in text:
        return text

    old_sig = """    onDone: () -> Unit = {},
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
"""
    new_sig = """    onDone: () -> Unit = {},
    onError: (String) -> Unit,
    onCompletedText: (String) -> Unit = {},
    allowThinking: Boolean = false,
"""
    text = replace_once(text, old_sig, new_sig, "generateResponse completed-text callback signature")

    old_state = """      val generationFinished = AtomicBoolean(false)
      val finishWithError: (String) -> Unit = { message ->
"""
    new_state = """      val generationFinished = AtomicBoolean(false)
      // MCP257_RUNTIME_RAW_COMPLETION
      // Keep the runtime stream independently from chat UI messages. UI progress panels and other
      // insertions may split one assistant turn into multiple ChatMessageText objects.
      val rawVisibleResult = StringBuilder()
      val finishWithError: (String) -> Unit = { message ->
"""
    text = replace_once(text, old_state, new_state, "raw completion accumulator")

    old_stream = """              if (partialResult.startsWith("<ctrl")) {
                // Do nothing. Ignore control tokens.
              } else {
                val intercepted =
"""
    new_stream = """              if (partialResult.startsWith("<ctrl")) {
                // Do nothing. Ignore control tokens.
              } else {
                rawVisibleResult.append(partialResult)
                val intercepted =
"""
    text = replace_once(text, old_stream, new_stream, "raw stream accumulation")

    done_block = """                    setInProgress(false)
                    onDone()
"""
    done_block_new = """                    setInProgress(false)
                    setPreparing(false)
                    onCompletedText(rawVisibleResult.toString())
                    onDone()
"""
    require_count(text, done_block, 2, "success completion callbacks")
    text = text.replace(done_block, done_block_new)

    old_run_again_sig = """  fun runAgain(
    model: Model,
    message: ChatMessageText,
    onError: (String) -> Unit,
    allowThinking: Boolean = false,
    extraContextOverride: Map<String, String>? = null,
  ) {
"""
    new_run_again_sig = """  fun runAgain(
    model: Model,
    message: ChatMessageText,
    onError: (String) -> Unit,
    onDone: () -> Unit = {},
    onCompletedText: (String) -> Unit = {},
    onInterceptPartialResult: (Model, String, Boolean, String?) -> Boolean = { _, _, _, _ -> false },
    allowThinking: Boolean = false,
    extraContextOverride: Map<String, String>? = null,
  ) {
"""
    text = replace_once(text, old_run_again_sig, new_run_again_sig, "runAgain callback signature")

    old_run_again_call = """        onError = onError,
        allowThinking = allowThinking,
        extraContextOverride = extraContextOverride,
"""
    new_run_again_call = """        onError = onError,
        onDone = onDone,
        onCompletedText = onCompletedText,
        onInterceptPartialResult = onInterceptPartialResult,
        allowThinking = allowThinking,
        extraContextOverride = extraContextOverride,
"""
    text = replace_once(text, old_run_again_call, new_run_again_call, "runAgain generateResponse plumbing")
    return text


def patch_llm_screen(text: str) -> str:
    marker = "MCP257_RAW_COMPLETION_CALLBACK"
    if marker in text:
        return text

    sig = """  onGenerateResponseDone: (Model) -> Unit = {},
  onBeforeSendMessage: (Model, List<ChatMessage>) -> Unit = { _, _ -> },
"""
    sig_new = """  onGenerateResponseDone: (Model) -> Unit = {},
  // MCP257_RAW_COMPLETION_CALLBACK
  onGenerateResponseTextCompleted: (Model, String) -> Unit = { _, _ -> },
  onBeforeSendMessage: (Model, List<ChatMessage>) -> Unit = { _, _ -> },
"""
    require_count(text, sig, 2, "LlmChatScreen/ChatViewWrapper callback signatures")
    text = text.replace(sig, sig_new)

    old_forward = """    onGenerateResponseDone = onGenerateResponseDone,
    onBeforeSendMessage = onBeforeSendMessage,
"""
    new_forward = """    onGenerateResponseDone = onGenerateResponseDone,
    onGenerateResponseTextCompleted = onGenerateResponseTextCompleted,
    onBeforeSendMessage = onBeforeSendMessage,
"""
    text = replace_once(text, old_forward, new_forward, "LlmChatScreen callback forwarding")

    old_generate = """          onDone = { onGenerateResponseDone(model) },
          onError = { errorMessage ->
"""
    new_generate = """          onDone = { onGenerateResponseDone(model) },
          onCompletedText = { rawText -> onGenerateResponseTextCompleted(model, rawText) },
          onError = { errorMessage ->
"""
    text = replace_once(text, old_generate, new_generate, "normal send raw completion callback")

    old_run_again = """        viewModel.runAgain(
          model = model,
          message = runtimeMessage,
          onError = { errorMessage ->
"""
    new_run_again = """        viewModel.runAgain(
          model = model,
          message = runtimeMessage,
          onDone = { onGenerateResponseDone(model) },
          onCompletedText = { rawText -> onGenerateResponseTextCompleted(model, rawText) },
          onInterceptPartialResult = onInterceptPartialResult,
          onError = { errorMessage ->
"""
    text = replace_once(text, old_run_again, new_run_again, "run again completion plumbing")
    return text


def patch_agent_screen(text: str) -> str:
    marker = "MCP257_RUNTIME_RAW_TEXT_AUTHORITY"
    if marker in text:
        return text

    old_maps = """  val compatToolStepsByModel = remember { mutableStateMapOf<String, Int>() }
  LaunchedEffect(task) { viewModel.loadSystemPrompt(task) }
"""
    new_maps = """  val compatToolStepsByModel = remember { mutableStateMapOf<String, Int>() }
  // MCP257_RUNTIME_RAW_TEXT_AUTHORITY
  val rawCompletionTextByModel =
    remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
  LaunchedEffect(task) { viewModel.loadSystemPrompt(task) }
"""
    text = replace_once(text, old_maps, new_maps, "raw completion map")

    old_error = """    compatToolStepsByModel.remove(model.name)
    viewModel.handleError(
"""
    new_error = """    compatToolStepsByModel.remove(model.name)
    rawCompletionTextByModel.remove(model.name)
    viewModel.handleError(
"""
    text = replace_once(text, old_error, new_error, "error cleanup raw completion")

    old_last = """      ) as? ChatMessageText
    val pendingWriteResult =
"""
    new_last = """      ) as? ChatMessageText
    val capturedRawCompletion = rawCompletionTextByModel.remove(model.name)
    val completionText =
      AgentCompletionTextResolver.resolve(
        rawCompletion = capturedRawCompletion,
        uiLastAgentText = lastAgentText?.content,
      )
    val pendingWriteResult =
"""
    text = replace_once(text, old_last, new_last, "completion text resolution")

    old_dispatch_entry = """    val textualToolSignal =
      lastAgentText?.let { AgentTextToolCallFallback.hasStrongToolSignal(it.content) } == true
    if (
      lastAgentText != null &&
        (resolveAgentToolMode(model) == ResolvedAgentToolMode.COMPAT || textualToolSignal)
    ) {
      // MCP256_MODE_AGNOSTIC_TEXT_TOOL_FALLBACK
      // Session mode describes the requested generation path; the emitted wire text is authoritative.
      val parsedToolCall = AgentTextToolCallFallback.parse(lastAgentText.content)
      val visibleCompatText = stripCompatThinkingText(lastAgentText.content)
      if (parsedToolCall == null && visibleCompatText != lastAgentText.content) {
"""
    new_dispatch_entry = """    val textualToolSignal = AgentTextToolCallFallback.hasStrongToolSignal(completionText)
    AgentDiagnosticsLogger.log(
      context = context,
      category = "chat.runtime_completion_dispatch",
      message = "Inspecting runtime completion for model ${model.name}",
      detail =
        "raw_chars=${capturedRawCompletion?.length ?: 0} | ui_last_chars=${lastAgentText?.content?.length ?: 0} | resolved_chars=${completionText.length} | tool_signal=$textualToolSignal",
    )
    if (
      completionText.isNotBlank() &&
        (resolveAgentToolMode(model) == ResolvedAgentToolMode.COMPAT || textualToolSignal)
    ) {
      // MCP256_MODE_AGNOSTIC_TEXT_TOOL_FALLBACK
      // MCP257 uses the runtime-complete stream as authority; UI message segmentation is irrelevant.
      val parsedToolCall = AgentTextToolCallFallback.parse(completionText)
      val visibleCompatText = stripCompatThinkingText(completionText)
      if (
        parsedToolCall == null &&
          lastAgentText != null &&
          visibleCompatText != completionText
      ) {
"""
    text = replace_once(text, old_dispatch_entry, new_dispatch_entry, "runtime-authoritative dispatch entry")

    # Remove current-turn agent text segments precisely. Progress/info panels must survive.
    old_visible_remove = """        viewModel.removeLastMessage(model = model)
        if (visibleCompatText.isNotBlank()) {
"""
    new_visible_remove = """        removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)
        if (visibleCompatText.isNotBlank()) {
"""
    text = replace_once(text, old_visible_remove, new_visible_remove, "visible compat text targeted removal")

    old_leak_remove = """        // A strong machine-protocol marker must never survive as an ordinary assistant answer.
        viewModel.removeLastMessage(model = model)
        viewModel.addMessage(
"""
    new_leak_remove = """        // A strong machine-protocol marker must never survive as an ordinary assistant answer.
        removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)
        viewModel.addMessage(
"""
    text = replace_once(text, old_leak_remove, new_leak_remove, "protocol leak targeted removal")

    old_max_steps = """        if (currentSteps >= MAX_COMPAT_TOOL_STEPS) {
          viewModel.removeLastMessage(model = model)
          viewModel.addMessage(
"""
    new_max_steps = """        if (currentSteps >= MAX_COMPAT_TOOL_STEPS) {
          removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)
          viewModel.addMessage(
"""
    text = replace_once(text, old_max_steps, new_max_steps, "max steps targeted removal")

    old_exec_remove = """        compatToolStepsByModel[model.name] = currentSteps + 1
        viewModel.removeLastMessage(model = model)
        viewModel.addMessage(
"""
    new_exec_remove = """        compatToolStepsByModel[model.name] = currentSteps + 1
        removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)
        viewModel.addMessage(
"""
    text = replace_once(text, old_exec_remove, new_exec_remove, "tool execution targeted removal")

    old_continue = """      onDone = { handleGenerationDone(model) },
      onError = { errorMessage -> handleCompatError(model, errorMessage) },
"""
    new_continue = """      onDone = { handleGenerationDone(model) },
      onCompletedText = { rawText -> rawCompletionTextByModel[model.name] = rawText },
      onError = { errorMessage -> handleCompatError(model, errorMessage) },
"""
    text = replace_once(text, old_continue, new_continue, "compat continuation raw capture")

    old_llm_head = """  LlmChatScreen(
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_AGENT_CHAT,
    navigateUp = navigateUp,
"""
    new_llm_head = """  LlmChatScreen(
    modelManagerViewModel = modelManagerViewModel,
    viewModel = viewModel,
    taskId = BuiltInTaskId.LLM_AGENT_CHAT,
    navigateUp = navigateUp,
"""
    text = replace_once(text, old_llm_head, new_llm_head, "explicit shared viewmodel")

    old_before = """    onBeforeSendMessage = { model, _ -> compatToolStepsByModel.remove(model.name) },
"""
    new_before = """    onBeforeSendMessage = { model, _ ->
      compatToolStepsByModel.remove(model.name)
      rawCompletionTextByModel.remove(model.name)
    },
"""
    text = replace_once(text, old_before, new_before, "top-level request raw reset")

    old_done = """    onGenerateResponseDone = handleGenerationDone,
    onResetSessionClickedOverride = { task, _, initialMessages ->
"""
    new_done = """    onGenerateResponseDone = handleGenerationDone,
    onGenerateResponseTextCompleted = { model, rawText ->
      rawCompletionTextByModel[model.name] = rawText
    },
    onResetSessionClickedOverride = { task, _, initialMessages ->
"""
    text = replace_once(text, old_done, new_done, "top-level runtime raw capture")

    old_stop = """    onStopButtonClickedOverride = { model ->
      compatToolStepsByModel.remove(model.name)
      viewModel.stopResponse(model = model)
"""
    new_stop = """    onStopButtonClickedOverride = { model ->
      compatToolStepsByModel.remove(model.name)
      rawCompletionTextByModel.remove(model.name)
      viewModel.stopResponse(model = model)
"""
    text = replace_once(text, old_stop, new_stop, "stop raw cleanup")

    helper_anchor = """private fun updateProgressPanel(viewModel: LlmChatViewModel, model: Model, agentTools: AgentTools) {
"""
    helper = """private fun removeCurrentTurnAgentTextMessages(viewModel: LlmChatViewModel, model: Model) {
  val messages = viewModel.uiState.value.messagesByModel[model.name].orEmpty()
  val lastUserIndex =
    messages.indexOfLast { message -> message is ChatMessageText && message.side == ChatSide.USER }
  if (lastUserIndex < 0) return
  val indices =
    (lastUserIndex + 1 until messages.size).filter { index ->
      val message = messages[index]
      message is ChatMessageText && message.side == ChatSide.AGENT
    }
  for (index in indices.asReversed()) {
    viewModel.removeMessageAt(model = model, index = index)
  }
}

"""
    text = replace_once(text, helper_anchor, helper + helper_anchor, "current-turn text removal helper")
    return text


for path, patcher, marker in [
    (VIEWMODEL, patch_viewmodel, "MCP257_RUNTIME_RAW_COMPLETION"),
    (SCREEN, patch_llm_screen, "MCP257_RAW_COMPLETION_CALLBACK"),
    (AGENT_SCREEN, patch_agent_screen, "MCP257_RUNTIME_RAW_TEXT_AUTHORITY"),
]:
    original = path.read_text(encoding="utf-8")
    updated = patcher(original)
    if marker not in updated:
        raise SystemExit(f"MCP257 fail-closed: marker missing in {path.name}")
    path.write_text(updated, encoding="utf-8")
    print(f"MCP257 patched: {path}")

resolver = AGENT / "AgentCompletionTextResolver.kt"
if not resolver.exists():
    raise SystemExit("MCP257 fail-closed: AgentCompletionTextResolver.kt missing")
print("MCP257_RUNTIME_RAW_COMPLETION_PATCH_PASS")
