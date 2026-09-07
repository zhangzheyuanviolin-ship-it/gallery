# Local Agent Plaza 项目交接文档

更新时间：2026-09-07

当前交接节点：MCP255 已完成 CI、签名、永久 GitHub Release 发布和结果标记写入。

本文用于新的 ChatGPT 对话窗口直接接手项目。接手后应优先阅读本文，再读取本文引用的 result JSON、现场证据 ZIP 和相关源码。不要依赖旧会话记忆推测项目状态。

---

## 1. 接手后的第一原则

本项目当前最重要的工程纪律是：**所有修复必须由真实日志、真实工具调用、真实输出文件或可复现测试证据驱动。禁止仅根据用户口头描述或模型行为猜测代码问题，然后连续试错构建。**

出现新问题时，先做只读诊断：

1. 找到用户最新上传并已经进入 `experimental/logs/` 的证据 ZIP。
2. 精确解压并枚举全部条目。
3. 对每个工具日志比较：
   - `model_tool_arguments`
   - `normalized_tool_arguments`
   - `resolved_operation`
   - `raw_result_json`
   - 外层 flatten 后的工具结果
   - 最终真实 Office 文件内容
4. 只有在证据明确指向某个共同接口或共享源码问题时，才扩展修复范围。
5. 修复必须附带回归测试，并保留 MCP250 保护边界。
6. 大 APK 只通过永久 GitHub Release 提供，不用临时 sandbox 链接。

用户已经明确多次强调：不允许根据推测和口头反馈直接改代码。

---

## 2. 仓库、分支、基线和参考项目

主项目仓库：

`zhangzheyuanviolin-ship-it/local-agent-plaza`

当前开发分支：

`experimental`

MCP250 黄金稳定分支：

`golden/mcp-250-product-stable`

黄金 SHA：

`4a4346a199344ac0912984f1f383abedf3b93819`

Office 改造从这个 SHA 直接向前演进。

参考项目：

`zhangzheyuanviolin-ship-it/rastacoder`

此前认可的 V17 迁移参考提交：

`4da9cd51cf488fbd6164d01a41de076f95e8d050`

目标产品是 Android 端完整本地 AI Agent。真实设备测试主要围绕 Redmi K70 Pro、24GB RAM、1TB 存储、Snapdragon 8 Gen 3 展开。项目非常重视端侧小模型在工具调用时产生的不标准参数，因此 Office 兼容层需要做到“宽容接收已观察到的小模型方言，同时保持失败真实、验证严格、禁止静默覆盖”。

---

## 3. MCP250 以后必须保护的区域

除非新日志明确证明问题来自这些区域，否则不要修改：

- `customtasks/musicgeneration`
- `customtasks/visualcreation`
- `app/src/main/jniLibs/arm64-v8a`
- 模型下载、加载、推理主链
- LiteRT / JNI
- Agent 模型适配器
- 工具调用 wire adapter
- AUTO / NATIVE / COMPAT 模式状态机
- search-required 逻辑
- post-tool continuation
- Media Toolbox
- long-text / workspace 主链

MCP255 的正式 workflow 会对前三个最敏感运行时目录做 `git diff --exit-code`，并对 Office Truth Guard 与 wire adapter 实体做存在性/入口检查。

MCP255 确实对 wire adapter 有一个极窄、证据驱动的 FunctionGemma 修复，见后文。未来仍然要把 wire adapter 当作保护区，不应随意继续扩写。

---

## 4. 构建与补丁物化架构

一个非常容易踩坑的事实：

`Android/src/app/build.gradle.kts` 目前只自动执行 MCP251 patch。

MCP252、MCP253、MCP254、MCP255 都由各自的专用 GitHub Actions workflow 在 Gradle 之前显式物化。

因此不要随意把后续 patch 接进 `build.gradle.kts`。原因是仓库中仍有旧版本 workflow；如果把 MCP252+ 自动挂进 Gradle，旧 MCP252/MCP253 workflow 可能在未来被触发后，用旧版本号构建出混入新逻辑的 APK，破坏版本血缘。

当前 MCP255 权威发布 workflow：

`.github/workflows/mcp255_excel_evidence_build_v2.yaml`

不要把旧文件：

`.github/workflows/mcp255_excel_evidence_build.yaml`

当作权威 MCP255 发布链。旧版本存在已知证据复现脚本问题。

MCP255 v2 的物化顺序：

1. `patch_mcp251_office_skills.py`
2. `patch_mcp252_office_truth.py`
3. `patch_mcp253_office_contract.py`
4. `patch_mcp254_document_conversion.py`
5. `patch_mcp255_excel_evidence.py`

随后运行 Office / Excel / wire 回归，再构建 Release APK。

stable-diffusion.cpp 在发布链固定到：

`19bdfe22d255d5b4dff39d449318b9bc5ea2317f`

Java 使用 21。

---

## 5. Office 能力总览

MCP251 起引入五个可选 Office Skill：

1. Word
2. PDF
3. Excel
4. PowerPoint
5. 文档转换

核心后端：

`Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentOfficeDocumentSupport.kt`

Skill 资产：

- `assets/skills/word-document/SKILL.md`
- `assets/skills/pdf-document/SKILL.md`
- `assets/skills/excel-workbook/SKILL.md`
- `assets/skills/powerpoint-presentation/SKILL.md`
- `assets/skills/document-convert/SKILL.md`

主要 operation：

Word：
- `word_create`
- `word_read`
- `word_modify`

PDF：
- `pdf_create`
- `pdf_read`
- `pdf_merge`
- `pdf_extract_pages`
- `pdf_reorder_pages`
- `pdf_delete_pages`
- `pdf_rotate_pages`

Excel：
- `xlsx_create`
- `xlsx_read`
- `xlsx_modify`

PowerPoint：
- `pptx_create`
- `pptx_read`
- `pptx_modify`

转换：
- `document_convert`

---

## 6. MCP250：当前 Office 改造的黄金起点

Release tag：

`mcp250-target-agent-family-state-machine-v1`

黄金源码 SHA：

`4a4346a199344ac0912984f1f383abedf3b93819`

MCP250 是 Office 改造前最后确认的产品稳定基线。它保留 MCP247 以来的本地媒体/native 运行时，并隔离处理多种目标 Agent 模型族兼容逻辑。

当前 MCP250 Release APK SHA256：

`fc2d2c55141e286d9f8118f302fba99ecd9b3f59b90c0faeb5e5312ea421dd2e`

Release：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/tag/mcp250-target-agent-family-state-machine-v1`

Office 251–255 的所有专用 workflow 都应持续以黄金 SHA 做边界参照。

---

## 7. MCP251：首次引入五个 Office Skill

核心后端提交：

`284f7df84fc7d2395bb31d5a447ad30a4801c576`

提交说明：

`MCP251: add workspace Office document backend`

该提交直接以 MCP250 黄金 SHA 为 parent。

Release tag：

`mcp251-office-skills-v1`

Release 当前记录的 target commitish：

`07f64634184ff69a4f1d76801419ddc0cc360b5d`

版本：

`1.0.14-mcp.251`

当前 Release APK 链接：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp251-office-skills-v1/local-agent-plaza-1.0.14-mcp.251.apk`

注意：这个 Release 的 APK 后来被旧 workflow 使用 `--clobber` 重传过。当前 GitHub Release 资产 SHA256 为：

`386ebba79e2b5714880663b7e476d3c3a365169cbdbcdeb699708e20643dcc30`

因此不要把当前 `docs/mcp251_office_skills_result.json` 里的 `commit_sha` 直接当作原始 MCP251 发布提交；该 result marker 后来被旧 workflow 重跑刷新过。

MCP251 现场证据：

`logs/mcp-251_测试日志与文档_20260903.zip`

现场证明了首次 Office 实现存在真实的“假成功/错误路由”问题：

- 首次 Word create 曾因缺少 title/content 失败。
- append/read 类请求可能静默路由为 `word_create`。
- 最终 `self_introduction.docx` 实际只包含标题和空段落，模型却可能给出成功式回答。

这些问题直接推动 MCP252。

---

## 8. MCP252：Truth Guard 与假成功修复

核心 Truth Guard 源码提交：

`80f06991e2bde4901ae379aaeaca8c3925936d2c`

提交说明：

`MCP252: add Office request normalization and truth verification guard`

Release tag：

`mcp252-office-truth-v1`

Release target：

`9b573618c7121c25ebfdf88053d33374431b1323`

版本：

`1.0.14-mcp.252`

核心文件：

`AgentOfficeTruthGuard.kt`

`patch_mcp252_office_truth.py`

`AgentOfficeTruthGuardTest.kt`

主要设计：

- 缺失 operation 时不再无条件默认 create。
- 接收一批常见小模型参数别名。
- 做 workspace-aware 的安全 operation 推断。
- create 不允许静默覆盖既有文件。
- 工具结果附 recovery hint。
- Office 输出做语义 read-back 验证。
- 验证失败支持 rollback。
- audit 记录 raw args、normalized args、resolved op 等证据。

MCP252 最初是根据 MCP251 真实 ZIP 修复。

MCP252 Release 后，用户再次测试 Word，口头反馈 append 失败、文件路径衔接异常等。但后续工程处理没有直接依据口头描述，而是读取：

`logs/mcp-252_测试日志与文档_20260904.zip`

这份 ZIP 共 9 个条目，后续 MCP253 完全由它驱动。

当前 MCP252 Release APK 也在 2026-09-06 被旧 workflow `--clobber` 重传过。当前 Release 资产 SHA256：

`f8e4b3ff1525053d36d79b22e30b2b001498a342d1a0659992ed2f033da26bc6`

同样不要把后续被刷新的 `docs/mcp252_office_truth_result.json` 的 `commit_sha` 当成原始发布血缘；Release tag target `9b573...` 更可靠。

---

## 9. MCP253：根据九份现场证据修复 Office 契约

证据 ZIP：

`logs/mcp-252_测试日志与文档_20260904.zip`

条目数量：9。

其中 7 个 Word JSON 日志和 2 个实际 DOCX。

关键真实证据：

### 9.1 wrapped root create 被误判 modify

日志：

`20260904-135415-166-word_document.json`

模型参数包含：

`operations:["create"]`

MCP252 因看到 `operations`，把请求推成 `word_modify`，随后报：

`Input path is required.`

另一个日志：

`20260904-135510-771-word_document.json`

模型参数：

`operations:[{"operation":"create"}]`

产生相同误路由。

结论：单个 root create 指令如果被小模型包进 `operations`，必须在“operations=modify”推断之前提升到根 operation。

### 9.2 Word append 方言与后端 envelope 不匹配

日志：

`20260904-135740-951-word_document.json`

模型参数：

`{"input_path":"file/self_intro.docx","operations":[{"type":"append","content":"..."}]}`

后端真实要求 nested operation 使用 `action` + `params`，所以报：

`Unsupported Word modify action:`

日志：

`20260904-135032-586-word_document.json`

同样使用：

`{"operation":"append","content":"..."}`

再次失败。

结论：nested operation 需要 canonicalization，例如 append -> `add_paragraph`，直接 content -> `params.text`。

### 9.3 固定默认文件名产生跨任务碰撞

日志：

`20260904-134741-262-word_document.json`

create 成功，默认落到：

`file/document.docx`

日志：

`20260904-135443-445-word_document.json`

下一次 create 再次想用同一路径，被 Truth Guard 拒绝：

`Refusing to overwrite existing file file/document.docx with a create operation.`

源代码审计进一步确认，MCP251 后端默认名同时存在于：

- Word `document.docx`
- PDF `document.pdf`
- Excel `workbook.xlsx`
- PPT `presentation.pptx`

因此碰撞风险属于共享源码事实。

### 9.4 精确 backend recovery hint 被外层 flatten 丢失

MCP252 内层返回了针对 create/read/edit 的具体提示，但 `AgentTools.kt` 失败 flatten 时统一改成泛化提示。

MCP253 修复为优先保留 payload 自带 `recovery_hint`。

### 9.5 两个实际 DOCX 的初始 create 内容被二进制检查确认

workflow 直接打开 DOCX 的 `word/document.xml`，确认两个成功 create 文件确实含有模型生成的 title 和完整初始 content。

因此 MCP253 没有猜测性加入“全局最后一个 Office 文件”状态。

### 9.6 关于用户口头反馈“模型找不到上一个文件”

九份证据里没有出现缺失 `input_path` 的真实工具调用；两个 append 工具调用都带了正确的前一个路径。

因此 MCP253 **没有** 引入 conversation last-file memory。

如果以后出现新日志证明 follow-up tool call 真的缺失 `input_path`，应该先调查 conversation/session architecture。正确方向必须是会话隔离的状态或有证据的确定性解析；严禁使用全局 static last path，因为它可能跨聊天窗口串文件。

### 9.7 MCP253 关键实现

Helper：

`AgentOfficeMcp253Compat.kt`

关键 helper 提交：

`5d810e84e012cde3043fed6f6b9d5d519ca90838`

Patch：

`patch_mcp253_office_contract.py`

关键 patch 提交：

`229d808d47fca04d1172fc8aa0a98243f9ad087b`

Tests：

`AgentOfficeMcp253CompatTest.kt`

关键 tests 提交：

`5eef87602683f12289369342f2d4e252e204b0b1`

Release workflow / 发布目标提交：

`976ecba0a9dd69430381d7fad070585e26b0eb71`

Release tag：

`mcp253-office-contract-v1`

版本：

`1.0.14-mcp.253`

APK SHA256：

`5ed8924157cd65a94b09b224fed3828abc989d018719302c16f08253102cd198`

永久 APK：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp253-office-contract-v1/local-agent-plaza-1.0.14-mcp.253.apk`

Result marker：

`docs/mcp253_office_contract_result.json`

MCP253 还把同样由源码确认的 `action + params` envelope 兼容应用到 Excel/PPT，但没有把 Word 特有行为盲目复制给 PDF。

---

## 10. MCP254：文档转换矩阵与验证修复

证据 ZIP：

`logs/mcp-253_测试日志与文档_20260904.zip`

条目数量：6。

核心实现：

`AgentDocumentConversionMcp254.kt`

关键实现提交：

`a12f41affd637cf08287746290c2a209ec7b44fd`

说明：

`MCP254: add conversion matrix and semantic verification policy`

后续严格 source extension 修复提交：

`0df650bebbb6693a475451192763053655c32bc9`

说明：

`MCP254: keep source extension validation strict`

矩阵回归测试提交：

`945faa645b1f6236e9ff816916ccc78ea61544f3`

说明：

`MCP254: add conversion evidence and full matrix regressions`

Release target：

`79a4bd42d72b2faa7d5fbeb79fd70cd4840ce1d4`

Release tag：

`mcp254-document-conversion-v1`

版本：

`1.0.14-mcp.254`

APK SHA256：

`41de7ec205b00e1b60df483e7463e9afc0eff080968eef721e2c0b9476b42e59`

永久 APK：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp254-document-conversion-v1/local-agent-plaza-1.0.14-mcp.254.apk`

Result marker：

`docs/mcp254_document_conversion_result.json`

其记录的转换矩阵：

- 总路由 16
- 同格式二进制复制 4
- 跨格式文本路由 12

MCP254 的主要策略：

- bare document input filename 自动规范到 `file/` workspace。
- 修复合法 TXT/DOCX -> PDF 被布局敏感语义验证误判的问题。
- 同格式转换使用精确二进制 copy。
- 禁止 silent text truncation。
- 禁止 source 与 output 同路径静默覆盖。
- 对 scanned/image-only PDF 的限制诚实返回，不伪造成功。
- 保留 MCP252 Truth Guard 与 MCP253 Office compatibility。

MCP254 发布后用户测试 Excel，新的现场证据推动 MCP255。

---

## 11. MCP255：Excel 现场证据修复

### 11.1 证据来源

证据 ZIP：

`logs/mcp-254_测试日志与文档_20260906.zip`

证据入库提交：

`f293fd4ccef4bb3958947bde29e2e3c94fc9cc41`

ZIP 必须精确包含 3 个条目：

1. `20260906-204608-287-excel_workbook.json`
2. `20260906-204544-431-excel_workbook.json`
3. `用户提示词与模型错误工具调用记录_mcp254.txt`

### 11.2 第一份 Excel 日志证明的问题

模型 create 参数中：

`sheets == ["销售数据"]`

但后端要求 sheet object，因此真实错误：

`Each sheet must be an object.`

同时 rows 结构是：

- 第一行是合法 4 列数组
- 后续数据被模型摊平成连续 scalar：`智能手机`, `5999`, `100`, `50`

所以需要在恢复无歧义时，把 string sheet 包装成 object，并按 header 宽度重组 scalar tail。

### 11.3 第二份 Excel 日志证明的问题

模型把“新建 workbook”描述成 operation script：

- `create_sheet`
- `insert_rows`

但没有 `input_path`。

MCP253 看见 operations 后按 modify 路由，最终报：

`Input path is required.`

rows 还多了一层数组嵌套。

结论：**当请求没有 input_path，并且 operations 全部只包含新建 sheet / 插入 row 这一类 construction action 时，可以安全提升为 create。**

如果已有 `input_path`，则必须继续保留 modify 语义，不能把现有 workbook 编辑误改为新建。

### 11.4 chat record 证明 wire 文本本身

现场聊天记录同时保留了：

- Gemma compact tool call：`<|tool_call>call:excel_workbook...<tool_call|>`
- standard `<tool_call>{"tool":"excel_workbook"...}`
- standard 调用里真实出现 `"rows":[[[` 的额外嵌套
- 记录中没有零宽字符污染

### 11.5 MCP255 实现

Helper：

`AgentExcelMcp255Compat.kt`

核心提交：

`0b10d013f54eb229c82cc0924658cbe4d5cdb43f`

说明：

`MCP255: add evidence-driven Excel request normalizer`

Patch wiring：

`patch_mcp255_excel_evidence.py`

关键提交：

`f39c82acab0ba545644299efdcd25b8746ba3ccf`

说明：

`MCP255: wire Excel evidence normalizer into Office routing`

Excel exact evidence tests：

`AgentExcelMcp255CompatTest.kt`

提交：

`1852e7574faded9bfbed38a22852d8badf3558be`

说明：

`MCP255: add exact Excel evidence regressions`

Wire evidence test：

`Mcp255WireEvidenceTest.kt`

提交：

`e57e3a976ff5a5f9f7a8eb48b44b942bd0e766a2`

说明：

`MCP255: preserve exact Excel wire evidence probes`

当前 helper 的重要安全行为：

- 只对 Excel Skill 生效。
- 无 `input_path` 且 operation script 全部属于 sheet/row construction actions 时，才提升 create。
- string sheet 可转为 `{name, rows}` object。
- 多一层 rows nesting 在结构明确时去掉。
- `[headerRow, scalar, scalar, ...]` 只有在 scalar 数量能被 header 宽度整除时才重组；无法无歧义恢复时保持原值，不强猜。
- 全 scalar rows 可规范成单一 row。
- 已有 `input_path` 时保留 modify，并把 create_sheet/insert_rows 方言转换到 backend 的 add_sheet/add_row envelope。

---

## 12. MCP255 构建过程中额外发现并修复的两个 CI/兼容问题

### 12.1 FunctionGemma `<escape>` 解析回归

第一轮 MCP255 v2 正式构建：

Run：

`34035382732`

Job：

`101492368947`

45 个测试中 44 个通过，唯一失败：

`CompatToolCallWireAdapterTest.normalizesOfficialGemmaAndFunctionGemmaArguments`

测试使用的真实 FunctionGemma 形态：

`<start_function_call>call:query_weather{location:<escape>昆明<escape>,mode:<escape>week<escape>}<end_function_call>`

源码对照证明根因是：`flexibleObject()` 先尝试 JVM `JSONObject(...)`。`org.json` 对未引号值过于宽松，可能直接把 `<escape>昆明<escape>` 当普通字符串吃掉，于是已有的 `LooseObjectParser` 没机会去除 `<escape>` delimiter。

修复严格限制为：当 raw object 含 `<escape>` 或 `<|"|>` 这种 Gemma 专用 delimiter 时，优先走已有 `LooseObjectParser`；普通 JSON 和其他模型格式不变。

提交：

`671b51372f279adb538996892298590411a3832b`

说明：

`MCP255: preserve FunctionGemma escape delimiters`

该修改属于 wire adapter 保护区，但有明确失败测试证据，而且后续完整 wire regressions 已通过。

### 12.2 MCP255 v2 的边界门禁本身有一个不可能成立的断言

修复 FunctionGemma 后的 Run：

`34073137088`

第 9 步完整 Office + Excel + wire tests 和 APK build 已经成功。

第 10 步失败在：

`grep -F 'MCP250_TOOL_CALL_WIRE_ADAPTER' AgentTooling.kt`

只读核对 MCP250 黄金 SHA 的 `AgentTooling.kt` 后确认：黄金基线本身就没有这个 marker，因此这是 workflow 自己的错误门禁，并非运行时代码越界。

同一第 10 步中真正的保护 diff：

- musicgeneration
- visualcreation
- jniLibs/arm64-v8a

已经全部通过。

最终 workflow 修复为直接检查真实存在的：

`CompatToolCallWireAdapter.kt`

并验证：

- 文件存在
- `internal object CompatToolCallWireAdapter`
- `normalizeFirstToolCall`

同时保留保护目录 `git diff --exit-code`。

workflow-only 提交：

`f77e3645c40e2b9ed2dbbb37cf3abb35dd2929e2`

说明：

`MCP255: fix impossible wire-adapter boundary assertion`

这也是最终 MCP255 Release 的 target commit。

---

## 13. MCP255 最终成功状态

权威 workflow：

`.github/workflows/mcp255_excel_evidence_build_v2.yaml`

最终 Run：

`34079284002`

最终 release target：

`f77e3645c40e2b9ed2dbbb37cf3abb35dd2929e2`

结果标记写入后的 bot commit：

`11da66d5ce12c373d6d7f5eaea55f262f6eb87de`

版本：

`1.0.14-mcp.255`

versionCode：

`355`

package：

`com.localagent.plaza.mcp`

Release tag：

`mcp255-excel-evidence-v1`

Release ID：

`383813120`

APK asset ID：

`548093606`

APK 大小：

`239127549` bytes

APK SHA256：

`39dc65b5c25364804b3e0f25deca8344b58cedeb3c86fd3b0992ad4f922f2586`

永久 APK：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp255-excel-evidence-v1/local-agent-plaza-1.0.14-mcp.255.apk`

SHA 文件：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/download/mcp255-excel-evidence-v1/local-agent-plaza-1.0.14-mcp.255.apk.sha256`

Release 页面：

`https://github.com/zhangzheyuanviolin-ship-it/local-agent-plaza/releases/tag/mcp255-excel-evidence-v1`

Result marker：

`docs/mcp255_excel_evidence_result.json`

最终构建已确认成功的关键阶段：

- MCP250 黄金边界校验
- 三项 MCP254 Excel 现场证据精确复现
- MCP251→MCP255 依次物化
- Office Truth Guard regressions
- MCP253 Office regressions
- MCP254 conversion regressions
- MCP255 Excel regressions
- MCP255 wire evidence regressions
- 现有 `*ToolCall*Test` regressions
- Release APK assemble
- 保护 runtime diff
- APK signing 验证
- package/version 验证
- 五个 Office skill assets 验证
- Actions artifact 上传
- 永久 GitHub Release 上传
- result marker 写入

---

## 14. 当前仍未被证明彻底解决的问题

以下项目必须继续保持“未决/待现场验证”状态，不能因为 CI 绿灯就宣布彻底解决。

### 14.1 MCP255 Excel 需要真实设备端到端复测

CI 已经证明：现场两种 malformed Excel 参数可以被 normalization 正确处理，旧 Office 和 wire regressions 没被打破，APK 也能成功构建并签名。

CI 不能替代用户在真实本地模型、真实 Android 设备上的端到端测试。

下一轮最重要任务是让用户安装 MCP255，用产生 MCP254 问题的同类模型和提示词重新创建 Excel，并检查真实 XLSX 内容。

建议导出：

- 所有 Excel tool audit JSON
- 最终 XLSX
- 如果模型在对话文本里输出了原始 tool-call 文本，也一起保存

新证据建议命名：

`logs/mcp-255_测试日志与文档_YYYYMMDD.zip`

### 14.2 “同一聊天中自动识别上一个 Office 文件”仍没有现场证据支持

目前没有任何已读日志证明 follow-up 工具调用真的缺少 `input_path`。

因此没有 conversation last-file state。

如果未来出现此问题，必须从新日志证明：模型 tool call 确实没有 path，再调查会话 ID、workspace 状态和 tool context。

严禁用 global static last-file path。

### 14.3 Word / PDF / PowerPoint 仍可能存在尚未暴露的边角方言

MCP253 对共享 envelope 做了硬化，但没有完整的每个 Skill 现场覆盖。

如果用户后续在 Word/PDF/PPT 再遇到失败，仍按具体 Skill 的日志证据处理，不能因为 Excel 的方言类似就盲目复制兼容逻辑。

### 14.4 文档转换还需要更多真实输入类型覆盖

MCP254 已覆盖 16 条转换 route 的回归矩阵，但 scanned/image-only PDF、复杂布局、极大文本、特殊 DOCX/PDF 内容等仍然可能暴露真实边界。

保持“如实失败、禁止假成功”的原则。

### 14.5 旧 workflow 会污染历史 Release 资产

MCP251 和 MCP252 的 Release asset 已经被后续旧 workflow `--clobber` 重传过。

因此未来不要无目的触发旧 MCP251/MCP252 build workflow。

审计历史版本时：

1. 看 tag target / Release target。
2. 看当前 GitHub asset digest。
3. 再看 result marker。
4. 如果三者时间不一致，优先按实际 Release 资产与明确历史 Run 解释。

### 14.6 Gradle / Kotlin / Node deprecation warning 当前是非阻塞项

构建日志存在若干 NDK/C++ warning、Kotlin context receiver warning、旧 actions Node 20/24 warning、部分 deprecated API warning。

这些在 MCP255 最终成功构建中均为 warning，当前没有证据表明它们造成 Office 功能错误。不要为“清 warning”在本轮项目里扩大修改面。

---

## 15. 下一轮推荐的现场测试顺序

优先测试 MCP255 Excel：

1. 新建一个简单 Excel，包含工作表名、表头和至少一行数据。
2. 记录模型首次 tool call 是否仍输出 string `sheets`、flattened rows 或 create_sheet/insert_rows script。
3. 确认工具返回成功后，实际打开/读取 XLSX 内容，验证 sheet、header、数据行都存在。
4. 在同一聊天继续要求向刚创建的 workbook 增加一行，验证 returned path 被正确复用。
5. 再要求读取刚刚修改后的表格，验证数据完整。
6. 导出 tool audit JSON + XLSX。

如果这一轮完全成功，再逐步测试：

- Word create -> append -> read
- PDF create/read/页操作
- PowerPoint create -> modify -> read
- document conversion 跨格式与同格式

每个失败都单独形成证据 ZIP，不要把多个模糊现象混成一个口头描述后一起修。

---

## 16. 新会话接手时建议读取的文件顺序

1. 本文件：
   `docs/PROJECT_HANDOFF_20260907_MCP255.md`
2. `docs/mcp255_excel_evidence_result.json`
3. `logs/mcp-254_测试日志与文档_20260906.zip`
4. `AgentExcelMcp255Compat.kt`
5. `patch_mcp255_excel_evidence.py`
6. `AgentExcelMcp255CompatTest.kt`
7. `Mcp255WireEvidenceTest.kt`
8. `.github/workflows/mcp255_excel_evidence_build_v2.yaml`
9. 必要时向前读：
   - `docs/mcp254_document_conversion_result.json`
   - `docs/mcp253_office_contract_result.json`
   - `AgentDocumentConversionMcp254.kt`
   - `AgentOfficeMcp253Compat.kt`
   - `AgentOfficeTruthGuard.kt`
   - `AgentOfficeDocumentSupport.kt`

如果用户在新会话直接发来 MCP255 新日志，第一动作应该是读取新 ZIP，不要先改代码。

---

## 17. 当前交接结论

截至 2026-09-07，MCP255 已经成为当前 Office/Excel 修复线的最新可测试版本。

它建立在 MCP250 黄金产品基线之上，继承 MCP251 Office 五 Skill、MCP252 Truth Guard、MCP253 Office 契约兼容、MCP254 conversion matrix，并针对 MCP254 真实 Excel 现场日志加入最小范围的 Excel request normalization。

MCP255 的 CI、旧 Office 回归、wire 回归、保护边界、Release 签名、包名版本、Office assets、永久 Release 都已通过。

当前下一步重点是：**真实设备安装 MCP255 做 Excel 端到端复测，并以新现场日志决定是否需要 MCP256。**

除非新证据出现，不继续扩大兼容层，也不主动改 protected runtime。
