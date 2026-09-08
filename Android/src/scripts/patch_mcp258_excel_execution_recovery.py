#!/usr/bin/env python3
"""MCP258: recover truncated textual Excel calls and force unambiguous create execution.

Runs after MCP251-MCP257 materialization. The parser-side structural repair lives in
AgentTextToolCallFallback.kt; this patch hardens the Office routing boundary so a payload containing
new workbook rows/sheets, with no input workbook and no modification operations, resolves directly
to xlsx_create instead of remaining at office_auto.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AGENT = ROOT / "app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat"
TRUTH = AGENT / "AgentOfficeTruthGuard.kt"
FALLBACK = AGENT / "AgentTextToolCallFallback.kt"


def require_count(text: str, needle: str, expected: int, label: str) -> None:
    count = text.count(needle)
    if count != expected:
        raise SystemExit(f"MCP258 fail-closed: {label} count={count}, expected={expected}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    require_count(text, old, 1, label)
    return text.replace(old, new, 1)


fallback_text = FALLBACK.read_text(encoding="utf-8")
for marker in (
    "MCP256_EXCEL_PREMATURE_ROWS_CLOSE_REPAIR",
    "MCP258_TRUNCATED_TOOL_OBJECT_CLOSE_REPAIR",
    "repairTruncatedToolObjectClosures",
):
    if marker not in fallback_text:
        raise SystemExit(f"MCP258 fail-closed: fallback structural repair missing: {marker}")

truth = TRUTH.read_text(encoding="utf-8")
marker = "MCP258_UNAMBIGUOUS_EXCEL_CREATE"
if marker not in truth:
    old_resolve = '''    val resolved = resolveExplicitOperation(skillName = skillName, rawOperation = rawOperation)
    request.put(META_SKILL, skillName)
    request.put(META_RAW_OPERATION, rawOperation)
    if (resolved != null) {
      request.put("operation", resolved)
      request.put(META_RESOLUTION, if (forcedOperation.isNotBlank()) "forced" else "explicit")
      materializeSimpleModifyOperations(skillName, request, resolved)
    } else {
      request.put("operation", AUTO_OPERATION)
      request.put(META_RESOLUTION, "deferred_safe_inference")
    }
'''
    new_resolve = '''    val explicitResolved = resolveExplicitOperation(skillName = skillName, rawOperation = rawOperation)
    // MCP258_UNAMBIGUOUS_EXCEL_CREATE
    // A no-input Excel payload carrying rows or sheets already describes a new workbook. Resolve
    // it here so a recoverable textual call proceeds directly toward the XLSX writer instead of
    // stopping at an intermediate office_auto state.
    val payloadResolved =
      if (explicitResolved == null) inferUnambiguousPayloadOperation(skillName = skillName, request = request)
      else null
    val resolved = explicitResolved ?: payloadResolved
    request.put(META_SKILL, skillName)
    request.put(META_RAW_OPERATION, rawOperation)
    if (resolved != null) {
      request.put("operation", resolved)
      request.put(
        META_RESOLUTION,
        when {
          forcedOperation.isNotBlank() -> "forced"
          explicitResolved != null -> "explicit"
          else -> "payload_unambiguous_create"
        },
      )
      materializeSimpleModifyOperations(skillName, request, resolved)
    } else {
      request.put("operation", AUTO_OPERATION)
      request.put(META_RESOLUTION, "deferred_safe_inference")
    }
'''
    truth = replace_once(truth, old_resolve, new_resolve, "prepareCompatRequest resolution block")

    helper_anchor = '''  fun prepareConfiguredParameters(skillName: String, parameters: String): String {
'''
    helper = '''  private fun inferUnambiguousPayloadOperation(skillName: String, request: JSONObject): String? {
    if (skillName != EXCEL_WORKBOOK_SKILL_NAME) return null
    if (request.optString("input_path").isNotBlank() || request.optString("path").isNotBlank()) return null
    if (request.optJSONArray("operations")?.length()?.let { it > 0 } == true) return null

    val hasRows = request.optJSONArray("rows")?.length()?.let { it > 0 } == true
    val hasSheets = request.optJSONArray("sheets")?.length()?.let { it > 0 } == true
    return if (hasRows || hasSheets) "xlsx_create" else null
  }

  fun prepareConfiguredParameters(skillName: String, parameters: String): String {
'''
    truth = replace_once(truth, helper_anchor, helper, "unambiguous Excel create helper")
    TRUTH.write_text(truth, encoding="utf-8")
    print(f"MCP258 patched: {TRUTH}")
else:
    print(f"MCP258 already applied: {TRUTH}")

final_truth = TRUTH.read_text(encoding="utf-8")
for required in (
    "MCP258_UNAMBIGUOUS_EXCEL_CREATE",
    "inferUnambiguousPayloadOperation",
    'return if (hasRows || hasSheets) "xlsx_create" else null',
    '"payload_unambiguous_create"',
    "executeVerified",
):
    if required not in final_truth:
        raise SystemExit(f"MCP258 fail-closed: final routing marker missing: {required}")

support = (AGENT / "AgentOfficeDocumentSupport.kt").read_text(encoding="utf-8")
for required in (
    '"xlsx_create" -> createXlsx(context, root, request)',
    "private fun createXlsx",
    "writeBytes(",
):
    if required not in support:
        raise SystemExit(f"MCP258 fail-closed: XLSX backend path missing: {required}")

agent_tools = (AGENT / "AgentTools.kt").read_text(encoding="utf-8")
if '"excel_workbook" -> runOfficeCompatTool(EXCEL_WORKBOOK_SKILL_NAME, arguments)' not in agent_tools:
    raise SystemExit("MCP258 fail-closed: excel_workbook compat execution route missing")

print("MCP258_EXCEL_EXECUTION_RECOVERY_PATCH_PASS")
