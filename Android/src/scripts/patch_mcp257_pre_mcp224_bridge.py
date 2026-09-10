#!/usr/bin/env python3
"""MCP257 build-order bridge for the established MCP224 Gradle patch.

MCP224 removes the legacy fixed COMPAT step-limit block during Gradle configuration. MCP257's
current-turn text cleanup originally touched that soon-to-be-deleted block first, which changed the
exact fail-closed anchor expected by MCP224. Restore only that obsolete anchor before Gradle runs;
MCP224 then deletes the whole block. All live MCP257 runtime-raw-completion changes remain intact.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatScreen.kt"

text = SCREEN.read_text(encoding="utf-8")
old = """        if (currentSteps >= MAX_COMPAT_TOOL_STEPS) {
          removeCurrentTurnAgentTextMessages(viewModel = viewModel, model = model)
          viewModel.addMessage(
"""
new = """        if (currentSteps >= MAX_COMPAT_TOOL_STEPS) {
          viewModel.removeLastMessage(model = model)
          viewModel.addMessage(
"""

if old in text:
    if text.count(old) != 1:
        raise SystemExit(f"MCP257 pre-MCP224 bridge fail-closed: targeted legacy block count={text.count(old)}")
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("MCP257 pre-MCP224 bridge fail-closed: legacy step-limit anchor missing")

SCREEN.write_text(text, encoding="utf-8")
print("MCP257_PRE_MCP224_BRIDGE_PASS")
