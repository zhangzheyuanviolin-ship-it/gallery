# 签名配置说明 (SIGNING)

本文档说明如何配置固定的 Android APK 签名，确保持续集成环境生成的 APK 可以覆盖安装。

## 问题背景

在 v1.0.12-cn 之前，每次 GitHub Actions 构建都使用临时生成的 debug keystore 签名，导致：
- 每个版本的签名完全不同
- 无法覆盖安装，必须卸载旧版本
- 用户数据会丢失

## 解决方案

使用固定的 keystore 文件进行签名，存储在 GitHub Secrets 中。

## 配置步骤

### 1. 生成 Keystore

在本地生成一个固定的 keystore 文件：

```bash
keytool -genkey -v -keystore gallery-release.keystore -alias gallery -keyalg RSA -keysize 2048 -validity 10000
```

按提示输入：
- 密钥库密码
- 姓名、组织、城市等信息
- 密钥密码（可与密钥库密码相同）

### 2. 配置 GitHub Secrets

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加以下 Secrets：

| Secret 名称 | 说明 | 示例值 |
|-----------|------|--------|
| `KEYSTORE_FILE` | Keystore 文件的 Base64 编码 | `base64 gallery-release.keystore` |
| `KEYSTORE_PASSWORD` | 密钥库密码 | `your_password` |
| `KEY_ALIAS` | 密钥别名 | `gallery` |
| `KEY_PASSWORD` | 密钥密码 | `your_key_password` |

### 3. 修改 build.gradle.kts

修改 `Android/src/app/build.gradle.kts` 中的签名配置：

```kotlin
android {
    ...
    
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "debug.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 4. 修改 GitHub Actions 工作流

修改 `.github/workflows/build_android.yaml`：

```yaml
- name: Decode Keystore
  run: |
    echo "${{ secrets.KEYSTORE_FILE }}" | base64 --decode > gallery-release.keystore

- name: Build
  run: ./gradlew assembleRelease
  env:
    KEYSTORE_FILE: ${{ github.workspace }}/gallery-release.keystore
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

## 当前状态

### v1.0.12-cn
- ⚠️ 仍使用 debug 签名配置
- 每个版本签名不同，无法覆盖安装

### 计划（v1.0.13-cn）
- ✅ 配置固定签名
- ✅ 支持覆盖安装
- ✅ 用户数据保留

## 安全注意事项

1. **永远不要**将 keystore 文件提交到 Git 仓库
2. 使用强密码保护密钥库
3. 定期备份 keystore 文件
4. 限制有权访问 GitHub Secrets 的人员

## 故障排查

### 问题：构建失败，提示签名配置错误
**解决**：检查 GitHub Secrets 是否正确配置，密码是否匹配

### 问题：APK 安装失败，提示签名不匹配
**解决**：确认使用了正确的 keystore 和密码，早期版本需要卸载后重新安装

## 相关文档

- [Android 官方签名文档](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)