# Google AI Edge Gallery 中文无障碍版

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/google-ai-edge/gallery)](https://github.com/google-ai-edge/gallery/releases)

**Google AI Edge Gallery 中文无障碍版** 是基于 Google 官方开源的 Edge Gallery 项目改造的 Android 应用，专为中国大陆地区用户优化。支持在本地运行大型语言模型（LLM），无需联网即可体验 AI 对话、图片问答、音频转写等功能。

## 主要特性

### 核心功能
- **本地运行大模型**：支持 Gemma、Qwen、DeepSeek 等主流开源模型
- **AI 智能对话**：离线状态下与 AI 进行自然语言交流
- **图片问答**：上传图片并询问相关问题
- **音频转写**：将语音转换为文字或翻译
- **提示词实验室**：创建和测试自定义提示词

### 中国地区优化（v1.0.12-cn 新增）
- ✅ **中国镜像站支持**：使用 hf-mirror.com 替代 huggingface.co，无需 VPN 即可下载模型
- ✅ **完整中文界面**：所有 UI 文本已翻译为中文
- ✅ **无障碍优化**：针对视障用户优化读屏支持

### 支持的模型
- Gemma 3n E2B/E4B（Google 最新开源模型）
- Gemma3 1B
- Qwen2.5 1.5B（阿里巴巴通义千问）
- DeepSeek-R1-Distill-Qwen 1.5B（深度求索）
- 更多 LiteRT 社区模型

## 安装说明

### 系统要求
- Android 12 或更高版本
- 至少 8GB 运行内存（推荐 12GB+）
- 至少 5GB 可用存储空间

### 安装步骤
1. 从 [Releases](https://github.com/zhangzheyuanviolin-ship-it/gallery/releases) 下载最新 APK
2. 在 Android 设置中允许"安装未知来源应用"
3. 安装下载的 APK 文件
4. 首次启动需接受服务条款

### 模型下载
1. 打开应用后进入"模型"页面
2. 选择需要的模型点击下载
3. 模型将自动从中国镜像站下载
4. 下载完成后即可离线使用

## 版本历史

详见 [CHANGELOG.md](CHANGELOG.md)

## 项目文档

- [CHANGELOG.md](CHANGELOG.md) - 版本更新记录
- [SIGNING.md](SIGNING.md) - 签名配置说明
- [DEVELOPMENT.md](DEVELOPMENT.md) - 开发指南

## 构建说明

### 本地构建
```bash
cd Android/src
./gradlew assembleRelease
```

### GitHub Actions 构建
每次推送到 main 分支会自动触发构建，APK 作为 Artifact 上传。

## 签名配置

Release 版本使用固定签名配置，确保持续集成环境生成的 APK 可以覆盖安装。

详见 [SIGNING.md](SIGNING.md)

## 已知问题

- 部分页面仍有英文硬编码文本（待后续版本完善）
- 某些最新模型可能需要先在 Hugging Face 官网申请许可

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

本项目基于 Apache License 2.0 开源。

原项目：https://github.com/google-ai-edge/gallery

## 联系方式

- 项目仓库：https://github.com/zhangzheyuanviolin-ship-it/gallery
- 问题反馈：https://github.com/zhangzheyuanviolin-ship-it/gallery/issues