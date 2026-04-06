# 更新日志 (CHANGELOG)

所有重要的项目更改都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [未发布]

### 计划功能
- 完成所有 UI 页面的中文翻译
- 添加应用名称自定义（离线 AI 展览馆）
- 配置固定签名，支持覆盖安装

---

## [1.0.12-cn] - 2026-04-06

### 新增功能
- ✅ **中国镜像站支持**：使用 hf-mirror.com 替代 huggingface.co
  - 中国大陆用户可直接下载模型，无需 VPN
  - 支持所有 Hugging Face 公开模型（Gemma、Qwen、DeepSeek 等）
  - 模型信息页面也使用镜像站

### 优化改进
- ✅ **中文界面优化**：模型管理器界面完全汉化
  - "Import model" → "导入模型"
  - "From local model file" → "从本地模型文件"
  - "Unsupported file type" → "不支持的文件类型"
  - "Only .task or .litertlm file type is supported." → "仅支持 .task 或 .litertlm 文件格式。"
  - "Unsupported model type" → "不支持的模型类型"
  - "Looks like the model is a web-only model and is not supported by the app." → "此模型仅支持 Web 版本，应用暂不支持。"
  - "Model imported successfully" → "模型导入成功"

### 技术改动
- 修改 `ModelAllowlist.kt`：添加 `HF_MIRROR_BASE` 常量，修改下载 URL 逻辑
- 修改 `GlobalModelManager.kt`：硬编码中文文本替换英文

### 已知问题
- 部分页面仍有英文硬编码文本（待后续版本完善）
  - PromoScreenGm4.kt 和 PromoBannerGm4.kt 的推广页面
  - HomeScreen.kt 中的任务标签（AI Chat、Ask Image 等）
  - SettingsDialog.kt、ConfigDialog.kt 等其他 UI 组件

---

## [1.0.11-cn] - 2026-04-06

### 新增功能
- ✅ **复制和重新生成按钮**：在 AI 消息输出区域添加两个功能按钮
  - 复制按钮：将 AI 回复内容复制到剪贴板
  - 重新生成按钮：重新生成当前消息

### 优化改进
- ✅ **基础中文界面支持**：创建中文翻译资源文件
  - 系统语言为中文时自动切换界面
  - 翻译约 200 个字符串项

### 技术改动
- 修改 `ChatPanel.kt`：添加 MessageActionButton
- 创建 `values-zh-rCN/strings.xml`：中文翻译资源

---

## [1.0.10] - 2026-04-02

### 原始版本
- Google 官方发布的初始版本
- 纯英文界面
- 使用 huggingface.co 下载模型

---

## 版本说明

### 版本号规则
- `x.y.z-cn` 表示中文无障碍改造版本
- `x.y.z` 表示 Google 官方版本

### 签名说明
- v1.0.12-cn 起使用固定签名配置
- 早期版本签名不固定，无法覆盖安装

### 下载地址
- GitHub Releases: https://github.com/zhangzheyuanviolin-ship-it/gallery/releases