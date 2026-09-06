#!/usr/bin/env python3
"""MCP255 Excel evidence repair. Run after MCP251-254."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat"


def require_count(text, needle, expected, label):
    count = text.count(needle)
    if count != expected:
        raise SystemExit(f"MCP255 fail-closed: {label} count={count}, expected={expected}")


def patch_truth_guard(text: str) -> str:
    marker = "// MCP255_EXCEL_EVIDENCE_NORMALIZATION"
    if marker in text:
        return text
    hook = "    AgentOfficeMcp253Compat.normalizeBeforeRouting(skillName = skillName, request = request)\n"
    require_count(text, hook, 2, "MCP253 normalization hook")
    replacement = (
        "    // MCP255_EXCEL_EVIDENCE_NORMALIZATION\n"
        "    // Normalize observed Excel create dialects before MCP253 can discard their direct fields.\n"
        "    AgentExcelMcp255Compat.normalizeBeforeRouting(skillName = skillName, request = request)\n"
        + hook
    )
    return text.replace(hook, replacement)


def patch_tooling(text: str) -> str:
    marker = "// MCP255_EXCEL_SCHEMA_GUIDANCE"
    if marker in text:
        return text
    old = "XLSX tool. create uses rows/sheets, read uses input_path, modify uses input_path plus operations. output_path is optional for create; the app allocates a unique path. Reuse the exact returned path for follow-up edits. Nested action aliases are normalized."
    new = "XLSX tool. create: use operation=create with rows as a 2D array and optional sheet_name, or sheets:[{name,rows}]. read uses input_path. modify uses input_path plus operations. Common small-model sheet/row wrappers are normalized safely. output_path is optional and collision-safe."
    require_count(text, old, 1, "Excel schema guidance")
    return text.replace(old, new, 1).replace("// MCP253_OFFICE_EVIDENCE_SCHEMA_GUIDANCE", "// MCP253_OFFICE_EVIDENCE_SCHEMA_GUIDANCE\n  // MCP255_EXCEL_SCHEMA_GUIDANCE", 1)


for path, patcher, marker in [
    (AGENT / "AgentOfficeTruthGuard.kt", patch_truth_guard, "MCP255_EXCEL_EVIDENCE_NORMALIZATION"),
    (AGENT / "AgentTooling.kt", patch_tooling, "MCP255_EXCEL_SCHEMA_GUIDANCE"),
]:
    text = path.read_text(encoding="utf-8")
    updated = patcher(text)
    if marker not in updated:
        raise SystemExit(f"MCP255 fail-closed: marker missing in {path.name}")
    path.write_text(updated, encoding="utf-8")
    print(f"MCP255 patched: {path}")

helper = AGENT / "AgentExcelMcp255Compat.kt"
if not helper.exists():
    raise SystemExit("MCP255 fail-closed: AgentExcelMcp255Compat.kt missing")
print("MCP255_EXCEL_EVIDENCE_PATCH_PASS")
