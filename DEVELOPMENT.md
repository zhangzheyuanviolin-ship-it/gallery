# 开发指南 (DEVELOPMENT)

本文档提供本地构建和开发本项目的详细说明。

## 环境要求

### 必需工具
- JDK 11 或更高版本
- Android SDK 35
- Git

### 推荐工具
- Android Studio Hedgehog 或更高版本
- Gradle 8.0 或更高版本

## 项目结构

```
gallery/
├── Android/src/                    # Android 应用源码
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/google/ai/edge/gallery/
│   │   │   │   ├── data/          # 数据模型和配置
│   │   │   │   ├── ui/            # UI 组件和页面
│   │   │   │   ├── common/        # 通用工具类
│   │   │   │   └── ...
│   │   │   ├── res/               # 资源文件
│   │   │   │   ├── values/        # 字符串、颜色、样式
│   │   │   │   ├── values-zh-rCN/ # 中文翻译
│   │   │   │   ├── layout/        # 布局文件
│   │   │   │   └── ...
│   │   │   ├── AndroidManifest.xml
│   │   │   └── ...
│   │   └── build.gradle.kts
│   └── gradlew                    # Gradle 包装器
├── .github/workflows/             # GitHub Actions 工作流
│   └── build_android.yaml         # APK 构建配置
├── README.md                      # 项目说明
├── CHANGELOG.md                   # 版本更新记录
├── SIGNING.md                     # 签名配置说明
└── DEVELOPMENT.md                 # 开发指南（本文档）
```

## 本地构建

### 1. 克隆仓库

```bash
git clone https://github.com/zhangzheyuanviolin-ship-it/gallery.git
cd gallery
```

### 2. 进入 Android 源码目录

```bash
cd Android/src
```

### 3. 构建 Debug 版本

```bash
./gradlew assembleDebug
```

输出位置：`app/build/outputs/apk/debug/app-debug.apk`

### 4. 构建 Release 版本

```bash
./gradlew assembleRelease
```

输出位置：`app/build/outputs/apk/release/app-release.apk`

## Hugging Face 配置

要成功构建和运行应用程序，需要配置 Hugging Face 开发者应用：

1. 在 [Hugging Face](https://huggingface.co/settings/applications) 创建开发者应用
2. 在 `ProjectConfig.kt` 中替换 `clientId` 和 `redirectUri` 的占位符
3. 在 `app/build.gradle.kts` 中修改 `manifestPlaceholders["appAuthRedirectScheme"]`

## 修改中文翻译

### 添加新的中文翻译

1. 找到需要翻译的英文文本位置
2. 如果是硬编码文本，直接修改对应的 Kotlin 文件
3. 如果是资源文件，修改 `res/values-zh-rCN/strings.xml`

### 示例：修改硬编码文本

```kotlin
// 修改前
Text("Import model", ...)

// 修改后
Text("导入模型", ...)
```

## 修改模型下载源

### 当前配置

模型下载源配置在 `data/ModelAllowlist.kt`：

```kotlin
private const val HF_MIRROR_BASE = "https://hf-mirror.com"
```

### 修改为其他镜像

```kotlin
private const val HF_MIRROR_BASE = "https://your-mirror.com"
```

## 修改应用名称

### AndroidManifest.xml

修改 `Android/src/app/src/main/AndroidManifest.xml`：

```xml
<application
    android:label="离线 AI 展览馆"
    ...>
```

## 配置签名

详见 [SIGNING.md](SIGNING.md)

## 测试

### 单元测试

```bash
./gradlew test
```

### 仪器测试

```bash
./gradlew connectedAndroidTest
```

## 常见问题

### 问题 1：构建失败，提示 SDK 版本不匹配
**解决**：确保安装了 Android SDK 35，并在 `local.properties` 中配置 `sdk.dir`

### 问题 2：Gradle 同步失败
**解决**：删除 `.gradle` 目录，重新运行 `./gradlew build`

### 问题 3：中文显示乱码
**解决**：确保文件编码为 UTF-8

### 问题 4：模型下载失败
**解决**：检查网络连接，确认镜像站可访问，检查 Hugging Face 权限配置

## 提交代码

### 提交规范

```
<type>: <description>

[optional body]

[optional footer]
```

### Type 类型
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建/工具

## 相关资源

- [Android 开发者文档](https://developer.android.com/)
- [Kotlin 官方文档](https://kotlinlang.org/)
- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [LiteRT 文档](https://ai.google.dev/edge/litert)

## 联系方式

- 项目仓库：https://github.com/zhangzheyuanviolin-ship-it/gallery
- 问题反馈：https://github.com/zhangzheyuanviolin-ship-it/gallery/issues