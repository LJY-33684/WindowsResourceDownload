# Windows Resource Download

这是 [AndroidResourceDownload](https://github.com/zhuzhuzihan/AndroidResourceDownload) 的 Windows 桌面移植版，基于 Compose Multiplatform 构建，提供与安卓端一致的 WebDAV 文件浏览、下载和上传体验。

## 当前状态

**v2.3.1 已发布，功能与安卓端 v2.3.1 对齐。**

支持 GitHub OAuth / 邮箱认证、加密会话恢复、内存 WebDAV 凭据、目录浏览、文件预览、下载队列、断点续传、仓库版本检查，以及管理员专属的上传队列、递归文件夹上传、重命名、移动、复制和删除操作。

## 目标技术栈

- Kotlin 1.9.24
- Compose Multiplatform 1.6.11（Desktop）
- Material 3
- MVVM + Repository
- Coroutines + Flow
- OkHttp 4.12.0
- kotlinx-serialization-json 1.6.3
- JNA 5.14.0（Windows 原生 API 调用）

## Windows 支持范围

- Windows 10 及以上（64 位）
- 高 DPI 屏幕适配（2.5K / 4K）
- 深色 / 浅色主题实时跟随系统
- 下载目录默认指向用户 `Downloads` 文件夹
- 单实例运行，GitHub 登录通过系统浏览器 + 本地套接字回调

## 与安卓端的差异

- **取色功能**：Windows 端不支持动态取色，已隐藏
- **加载动画**：Compose Multiplatform 1.6.11 不支持安卓端部分 Material 3 新动画 API，使用等效替代
- **文件打开**：已下载文件点击后打开文件所在文件夹，而非直接调用系统打开
- **拖拽上传**：支持文件 / 文件夹拖入应用窗口上传（仅管理员模式）

## 构建

### 环境要求

- JDK 17
- 网络连接（首次构建需下载 Gradle 和依赖）

### 构建命令

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:GRADLE_OPTS = "-Xmx2048m -XX:MaxMetaspaceSize=512m"
.\gradlew.bat --no-daemon createDistributable
```

构建产物位于 `build\compose\binaries\main\app\WindowsResourceDownload\`。

> **注意**：构建路径请勿包含中文字符，否则 jpackage 可能因编码问题失败。建议在纯英文路径下构建。

构建完成后，需手动将 JDK 的 `java.exe` 和 `javaw.exe` 复制到 `runtime\bin\` 目录。

## 仓库结构

```text
.
├── src/                     # 应用源码
│   └── main/kotlin/link/mczihan/androidResourceDownload/
├── gradle/                  # Gradle Wrapper
├── app_icon.ico             # 应用图标（ICO）
├── app_icon.png             # 应用图标（PNG）
├── app_icon.svg             # 应用图标（SVG）
├── build.gradle.kts         # 构建配置
├── settings.gradle.kts      # 项目设置
├── gradle.properties        # Gradle 属性
├── latest_version.txt       # 最新版本信息（用于应用内更新检查）
└── README.md                # 项目说明
```

## 更新检查

应用启动时会读取 `latest_version.txt` 中的版本号和下载地址，与当前版本比对，提示用户更新。

当前更新源：`https://github.com/LJY-33684/WindowsResourceDownload/releases/`

## 致谢

- 安卓端原项目：[zhuzhuzihan/AndroidResourceDownload](https://github.com/zhuzhuzihan/AndroidResourceDownload)
- 后端 API：由项目组提供
