# 开发指南

本页介绍 WeKit 的开发环境、构建命令和产物。专题说明请参阅：

- [DexKit 解析器测试](linux-dex-test.md)
- [国际化开发指南](i18n.md)
- [文档站维护](documentation-site.md)
- [翻译贡献](../translations/index.md)

## 克隆仓库

```bash
git clone https://github.com/Ujhhgtg/WeKit.git --recursive
cd WeKit
```

## 环境要求

当前项目使用：

| 依赖 | 版本或要求 |
|------|------------|
| JDK | 21 |
| Android SDK | compile SDK 37、target SDK 37 |
| Android NDK | `30.0.14904198` |
| Rust | 支持 Rust 2024 edition 的 stable 工具链 |
| adb | 在设备上安装 APK 时需要 |

### Arch Linux

```bash
yay -Syu jdk21-openjdk rustup
rustup toolchain install stable
rustup default stable
rustup target add aarch64-linux-android
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;$(sed -n 's/^ndk = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
```

### Debian 系

JDK 21 和 `rustup` 的包名可能随发行版而异。安装 JDK 21 后，还需要 Rust Android
targets：

```bash
sudo apt update
sudo apt install rustup
rustup toolchain install stable
rustup default stable
rustup target add aarch64-linux-android
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;$(sed -n 's/^ndk = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
```

### Windows

建议全文背诵[《停止用 Windows 工作！》](https://zhuanlan.fxzhihu.com/p/2024527609388627701)。

### Android SDK 路径

`./x` 按以下顺序查找 Android SDK：

1. `ANDROID_HOME`
2. `ANDROID_SDK_ROOT`
3. 仓库根目录 `local.properties` 中的 `sdk.dir`

## `./x`

仓库根目录的 `./x` 等价于 `cargo xtask`，以下文档统一使用 `./x`：

```sh
#!/usr/bin/env sh
exec cargo xtask "$@"
```

可用的一级命令：

| 命令 | 用途 |
|------|------|
| `./x configure` | 生成 Rust Android linker 配置 |
| `./x build` | 构建 APK，或仅准备应用 native 库 |
| `./x run` | 构建、安装并启动 APK |
| `./x check` | 对 Rust native 库执行 `cargo check` |
| `./x clippy` | 对 Rust native 库执行 `cargo clippy -- -D warnings` |
| `./x i18n-check` | 校验 Android 国际化资源目录 |
| `./x dex-test` | 在桌面测试 DexKit 解析器 |
| `./x dex-report-diff <report...>` | 离线比较版本报告中的方法和构造函数签名 |
| `./x extensions` | 构建和管理按需下载的扩展包 |

使用 `./x --help` 或 `./x <命令> --help` 查看当前支持的参数。

### Rust Android 配置

```bash
./x configure
```

该命令使用版本目录配置的 NDK，为 ARM64 生成 linker 配置，包括：

```text
app/src/main/rust/wekit-native/.cargo/config.toml
```

完整 APK 模式的 `./x build` 和 `./x run` 会自动执行该步骤。直接运行
`./x check` 或 `./x clippy` 前应执行一次 `./x configure`；`./x build --native-only` 自动准备配置和应用 native 输入。

## APK

### 变体

模块通过 `entrypoint` flavor 提供两个变体：

- **standard**：包含现代 libxposed API 入口
  （`entry/lxp/*` 与 `META-INF/xposed/*`）。大多数用户应使用此变体。
- **legacy**：不包含 libxposed 入口和相关元数据，使框架回退到传统
  `de.robv.android.xposed` API（`Xp51HookEntry` 与 `assets/xposed_init`）。

两个变体使用同一个 `applicationId`，不能同时安装。

### 构建

```bash
# 两个 flavor 的 debug APK
./x build

# 两个 flavor 的 release APK
./x build --release

# 只构建一个 flavor
./x build --flavor standard
./x build --flavor legacy --release

# 只准备应用 native 库，跳过 Gradle
./x build --native-only
./x build --native-only --abi arm64-v8a
```

完整 APK 构建依次执行：

1. `./x configure`
2. 为所选 ABI 准备应用 native 库及所需输入
3. 将 native 库复制到 `app/src/main/jniLibs/<abi>/`
4. 执行对应的 Gradle `assemble` 任务

Rust native 仅支持 `arm64-v8a`。
`--native-only` 会忽略 `--flavor` 和 `--release`，native 库始终使用 Cargo release
profile。

### 字符串与资源保护

LSpeciallyParanoid 在 debug 构建中默认关闭，在 release 构建中默认开启。
`build`、`run` 均可显式覆盖：

```bash
./x run                         # debug，不保护
./x run --protect true          # debug，开启保护
./x run --release               # release，默认保护
./x run --release --protect false
./x build --release --protect false
```

直接调用 Gradle 使用相同的默认值，通过 `-Pprotect=true|false` 覆盖：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:assembleDebug -Pprotect=true
./gradlew :app:assembleRelease -Pprotect=false
```

关闭保护会跳过字符串和资源转换，并生成空操作的加载入口；不会改用 JVM 字符串混淆。
release 原有的 R8 优化不受此开关影响。`--native-only` 不生成 APK，因此忽略此开关。

`dex-test` 和 Gradle 测试任务始终关闭保护，即使传入 `-Pprotect=true`。
请求名称含 `test` 的任务，以及 `check`、`connectedCheck`、`deviceCheck`、`build`、`buildNeeded`、`buildDependents`
等包含测试的聚合任务时，整个 Gradle 调用都不保护，以免主代码和测试共用受保护产物。
同时请求测试与 APK 构建也遵循该规则；需要受保护的发布 APK 时，单独运行
`./x build --release` 或 `./gradlew :app:assembleRelease`。

Gradle 为每个 flavor 输出一个仅包含 ARM64 native 库的 APK：

```text
app/build/outputs/apk/standard/debug/app-standard-debug.apk
app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
```

release 产物位于对应的 `standard/release/` 和 `legacy/release/` 目录。

### 安装

连接 adb 设备后执行：

```bash
# 默认安装 standard debug
./x run
./x run --debug

./x run --flavor standard --release
./x run --flavor legacy
```

当前 `run` 命令执行 `installStandardDebug`、`installStandardRelease` 或对应的 legacy
Gradle 任务。它会先重新构建 ARM64 native 库。

存在多个 adb 设备时，可通过 `ANDROID_SERIAL` 选择设备：

```bash
ANDROID_SERIAL=SERIAL ./x run
```

可选：应用基准配置（Baseline Profile）：

```bash
adb shell cmd package compile -m speed-profile dev.ujhhgtg.wekit
```

### 检查 Rust native 库

```bash
./x configure
./x check
./x clippy

# 显式检查 ARM64 ABI
./x check --abi arm64-v8a
./x clippy --abi arm64-v8a
```

`check` 和 `clippy` 默认检查 `arm64-v8a`，这也是唯一可用 ABI。

## APK 与 native 库

所有 standard / legacy、debug / release APK 都是可直接安装的 Xposed 模块 APK，仅支持
`arm64-v8a`，输出到 `app/build/outputs/apk/<standard|legacy>/<debug|release>/`。

```bash
# 两个 flavor 的 debug APK
./x build

# 两个 flavor 的 release APK
./x build --release

# 只构建 legacy
./x build --flavor legacy

# 仅准备应用 native 输入
./x build --native-only

# 正常 APK 安装；默认 standard debug
./x run
./x run --flavor legacy --release
```

Android 代码优化由 `--release` 控制；native 库始终使用 Cargo release profile。

### 打包

`app/src/main/rust/wekit-native` 由 xtask 编译后复制到 `app/src/main/jniLibs/<abi>/`，
Gradle 只负责消费这些 native 输入并打包签名。签名完成后不得再追加文件或重新打包 APK。

原生调试可直接在 `app/src/main/rust/wekit-native` 中运行
`cargo build --target aarch64-linux-android`；先用 `./x configure` 准备 NDK 链接配置。
正常出包始终使用 `./x build`，确保 native 库已更新——直接运行 Gradle 会静默打包旧的
native 库。

### 验证

CI 对每个产出的 release APK 执行 `.github/scripts/verify-apk.py`，检查签名、zipalign、
DEX 规范性和入口点与 flavor 的一致性。桌面验证和构建通过不代表真机上的微信运行正常。
