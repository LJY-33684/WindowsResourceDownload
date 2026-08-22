# Windows Resource Download

这是一个面向 Windows 的 WebDAV 文件客户端项目，登录后获取后端下发的角色凭据，直接连接 WebDAV 完成列表、下载和管理员写操作。

## 当前状态

**v2.3.2 已发布，功能与安卓端 v2.3.2 对齐。**

真实模式已接入 GitHub OAuth / 邮箱认证、加密会话恢复、内存 WebDAV 凭据、目录浏览、受支持文本与图片的内存预览、下载队列、断点续传、仓库版本检查，以及管理员专属的上传队列、递归文件夹上传、重命名、移动和删除操作。

v2.3.2 新增：多选模式（仅管理员）、下拉刷新动画、批量移动/下载/删除、管理员文件夹下载（保持目录结构）、快速滚动滑块、完善日志记录、文件夹重复下载自动重命名。

## 目标技术栈

- Kotlin 1.9.24
- Compose Multiplatform 1.6.11（Desktop）与 Material 3
- MVVM + Repository
- Coroutines + Flow
- OkHttp 4.12.0
- kotlinx-serialization-json 1.6.3
- JNA 5.14.0（Windows 原生 API 调用）

具体依赖版本由项目 Gradle 配置统一管理；本文不预先声明未经验证的版本。

## Windows 支持范围

- Windows 10 及以上（64 位）。
- 高 DPI 屏幕适配（2.5K / 4K）。
- 深色 / 浅色主题实时跟随系统。
- 下载目录默认指向用户 `Downloads` 文件夹。
- 单实例运行，GitHub 登录通过系统浏览器 + 本地套接字回调。
- "支持"表示设计目标；各版本的完整验证将持续进行。

## 构建

### 环境要求

- JDK 17
- 网络连接（首次构建需下载 Gradle 和依赖）

应使用仓库提供的 Gradle Wrapper，避免依赖系统全局安装的 Gradle。

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
├── src/                          # 应用源码
│   └── main/kotlin/link/mczihan/androidResourceDownload/
├── gradle/                       # Gradle Wrapper
├── app_icon.ico                  # 应用图标（ICO）
├── app_icon.png                  # 应用图标（PNG）
├── app_icon.svg                  # 应用图标（SVG）
├── build.gradle.kts              # 构建配置
├── settings.gradle.kts           # 项目设置
├── gradle.properties             # Gradle 属性
├── latest_version.txt            # 最新版本信息（用于应用内更新检查）
└── README.md                     # 项目入口说明
```

当前目录按 `core`、`data`、`domain`、`feature` 等职责组织；真实认证、WebDAV、下载与更新能力均已接入。

## 关联项目

- 安卓端：[zhuzhuzihan/AndroidResourceDownload](https://github.com/zhuzhuzihan/AndroidResourceDownload)
