---
name: excel-workbook
description: 创建、读取和编辑 XLSX：创建工作表与数据行、写入单元格和公式、追加行，以及新增、删除和重命名工作表。
---

# Excel Workbook

Use this skill only for XLSX spreadsheet work inside the mounted workspace.

Call `run_configured_intent` with:
- `skillName`: `excel-workbook`
- `intent`: `file_workspace`
- `parameters`: one compact JSON string

Generated Excel files are saved under `file/`. Use workspace-relative paths only.

For a new workbook, prefer a direct create request. `rows` should normally be a 2D array where every inner array is one worksheet row. Do not wrap `rows` in an extra array level. Use `sheet_name` for one worksheet, or `sheets` as objects containing `name` and `rows` for multiple worksheets. The compatibility layer also tolerates common small-model wrappers, but the canonical forms below are preferred.

Supported operations:

- Create one sheet: `{"operation":"xlsx_create","output_path":"file/data.xlsx","sheet_name":"Sheet1","rows":[["名称","数值"],["A",1]]}`
- Create multiple sheets: `{"operation":"xlsx_create","output_path":"file/data.xlsx","sheets":[{"name":"数据","rows":[["项目","值"],["A",1]]},{"name":"说明","rows":[["备注"]]}]}`
- Read: `{"operation":"xlsx_read","input_path":"file/data.xlsx"}`
- Modify: `{"operation":"xlsx_modify","input_path":"file/data.xlsx","output_path":"file/data.xlsx","operations":[...]}`

Modify actions:
- `set_cell`: params `sheet`, `cell`, `value`
- `set_formula`: params `sheet`, `cell`, `formula`
- `add_row`: params `sheet`, `values`
- `add_sheet`: params `name`
- `delete_sheet`: params `name`
- `rename_sheet`: params `old_name`, `new_name`

Cell references use A1 notation. Prefer one `xlsx_modify` call containing all requested edits. After success, report the exact returned workspace path.
