# 漫听 · Wander Radio

> 把下一站，交给声音。

漫听是一款使用 Java 开发的原生 Android 网络电台客户端。通过可交互的地球视图、流派星云和随机换台，探索来自不同国家与地区的实时广播。

项目从 [Radio Browser](https://www.radio-browser.info/) 获取电台目录，音频直接连接电台服务器播放。界面使用 Android 原生控件和 Canvas 绘制，播放器使用 AndroidX Media3 ExoPlayer。

**Android 8.0+ · Java 17 · Media3 ExoPlayer · MIT**

[更新记录](CHANGELOG.md) · [参与贡献](CONTRIBUTING.md) · [第三方说明](THIRD_PARTY_NOTICES.md) · [许可证](LICENSE)

本项目的交互灵感来自 [RoamDial](https://roamdial.com/)，为独立实现，与 RoamDial 无隶属关系。

## 项目状态

当前版本：`0.1.5`，处于早期开发阶段，仅支持 Android。

已完成 APK 编译和静态检查，以及目录解析、搜索、缓存数据序列化等核心数据逻辑检查。已在 OPPO PKV110 上验证 MP3/HLS 直播、连续 3 次暂停后恢复播放，以及一次全量目录加载（69,408 台，其中中国 2,441 台；数量为测试时快照，包含失效标记）；锁屏控制、长时间后台运行和不同厂商策略仍需进一步验证。

## 功能

- **随机探索**：从当前列表随机选择电台，换台时尽量避免重复当前电台。
- **地球视图**：优先根据目录经纬度展示电台，缺失时回退到有精度标注的地区或国家范围，支持拖动旋转、双指缩放和点选播放。切换地区时自动定位该地区的电台坐标群，选择频道时平滑旋转并放大至目录坐标或带精度说明的近似位置。
- **统一面板**：地区、流派/码率、睡眠定时、收听工具、分享入口和关于页面共用深色圆角面板；流派与码率使用可预览、统一确认的选项卡。
- **调谐界面**：深绿调谐面板、带清除按钮的搜索框和底部地区选择面板；选中频道显示暖色光晕、刻度环、播放状态、台名及码率。
- **流派星云**：依据电台标签进行粗略分组，以星云布局探索不同类型的广播。
- **地区选择性能**：国家、省州选项在目录更新后由后台线程建立索引并排序；打开面板读取缓存，切换地区直接查询对应电台，目录进度通知不触发地图重建。
- **国家与地区**：按国家/地区筛选，并可继续选择省、州或地区；选择器支持名称搜索。国家选项、电台列表和播放卡片显示对应国旗。
- **搜索与筛选**：支持电台名称、国家、国家代码、地区和流派标签搜索，以及流派、最低码率筛选。
- **大尺寸频道列表**：底部常驻 50dp 展开入口，探索、收藏、最近也可直接打开大列表；支持面板内搜索，频道条目至少 72dp 高，整行可点击。
- **本地收藏**：收藏电台并保存在设备上，支持长按列表条目切换收藏状态。
- **最近收听**：保存最近 50 个电台，便于再次访问。
- **后台播放**：通过前台媒体服务实现后台播放，并提供通知栏和系统媒体会话控制。
- **睡眠定时**：支持 15、30、60 分钟后暂停播放。
- **电台分享**：通过 Android 分享面板分享电台名称、地区和 Radio Browser 详情链接。
- **目录缓存**：先读取本地缓存，再分页拉取全球全量目录；显示加载进度，以电台 UUID 去重，全部完成后原子替换完整缓存。更新失败时保留已有目录，可点击顶部状态重试。
- **线路回退**：优先尝试解析地址及原始地址的 HTTPS 版本，并保留原始 HTTP 地址回退；去除重复线路，按顺序尝试候选地址，失败后停止并显示 HTTP、超时、DNS 或播放器错误。

## 实现方式

```text
Radio Browser API
        │
        ▼
StationRepository ─── 本地目录缓存
        │
        ▼
MainActivity ──────── Library
        │             收藏与最近收听
        ├── AtlasView
        │   地球投影与流派星云
        │
        ▼
RadioService
        ├── Media3 ExoPlayer + HLS
        ├── MediaSession
        ├── 前台播放通知
        └── 电台音频服务器
```

目录请求在独立线程执行，结果回到主线程更新界面。播放由独立的前台服务管理，避免将播放器生命周期绑定到页面生命周期。服务处理音频焦点变化和耳机断开事件，并在暂停或销毁时释放相应资源。

Radio Browser 节点通过 DNS 发现、打乱顺序后依次尝试，并配置备用节点。全量目录使用同一节点按名称分页读取，偏移量按原始记录数递增；发现重复整页则停止该节点并尝试下一节点，避免无限循环。用户选择电台时，客户端按 Radio Browser 的 API 约定提交点击请求。

换台后立即同步显示连接状态，连接与重连期间禁用主播放按钮，避免把播放操作误判为取消连接。播放器使用异步网络加载，并在播放器所属的主线程停止和释放；暂停时保留已准备的播放器与暂停通知，恢复时重新建立媒体会话并使旧回调失效；暂时丢失音频焦点时等待恢复，用户暂停、耳机断开或永久丢失焦点时不自动抢占播放。

### 技术栈

| 项目 | 配置 |
| --- | --- |
| 开发语言 | Java 17 |
| 用户界面 | Android 原生控件 |
| 星图绘制 | Android Canvas |
| 音频播放 | AndroidX Media3 ExoPlayer 1.8.0 |
| 系统媒体控制 | MediaSession、Notification.MediaStyle |
| 网络请求 | HttpURLConnection |
| JSON 处理 | Android 内置 org.json |
| 本地数据 | SharedPreferences、应用内部文件 |
| 构建工具 | Gradle 8.7、Android Gradle Plugin 8.5.2 |
| 最低系统 | Android 8.0 / API 26 |
| 编译与目标 SDK | API 35 |
| 应用包名 | `com.wanderadio.app` |

应用使用 AndroidX Media3 ExoPlayer 与 HLS 模块（1.8.0，Apache-2.0）；该版本与当前编译 SDK 35 保持兼容。国家参考经纬度来自 mledoze/countries，相关归属和 ODbL 数据授权见 `THIRD_PARTY_NOTICES.md`。可用的音频编码和流协议取决于 AndroidX Media3 ExoPlayer、设备解码器及电台服务端。

## 快速开始

### 环境要求

- Android Studio。
- JDK 17：将 Android Studio 的 Gradle JDK 配置为 17。
- Android SDK Platform 35。
- Android SDK Build-Tools 34.0.0。
- Android 8.0 或更高版本的手机或模拟器。

### 在 Android Studio 中运行

1. 获取项目源码，在 Android Studio 中选择 **Open**。
2. 打开包含 `settings.gradle` 和 `gradlew` 的项目根目录。
3. 等待 Gradle 同步完成；如果缺少 SDK 组件，按提示安装。
4. 连接手机并开启 USB 调试，在手机上允许电脑调试。
5. 选择 `app` 运行配置和目标设备，点击 **Run**。

本项目直接以仓库根目录作为 Android 工程目录。

### 命令行构建

macOS / Linux：

```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew lintDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接已授权的设备后，可执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

调试构建使用 Android 默认调试签名。如需指定已有的调试密钥库，可通过项目提供的参数传入：

```bash
./gradlew assembleDebug -PdebugKeystore=/absolute/path/to/debug.keystore
```

该参数仅覆盖调试密钥库路径；密钥别名和密码仍使用 Android 默认调试签名配置。正式发布版本需要单独配置发布签名。

## 项目结构

```text
.
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/wanderadio/app/
│       │   ├── MainActivity.java       # 页面、搜索、筛选与交互
│       │   ├── LocationResolver.java   # 目录、地区、国家三级位置解析
│       │   ├── GlobeCamera.java        # 经纬度投影、地区取景与最短旋转
│       │   ├── AtlasView.java          # 地球投影与流派星云绘制
│       │   ├── RadioService.java       # 后台播放与系统媒体控制
│       │   ├── StationRepository.java  # 目录获取、节点选择与缓存
│       │   ├── GeographyIndex.java     # 后台建立国家、地区选项及电台索引
│       │   ├── CataloguePages.java     # 全量分页、去重与推进检查
│       │   ├── StreamRoutes.java       # HTTPS 优先与候选线路
│       │   ├── Station.java            # 电台数据模型
│       │   └── Library.java            # 收藏与最近收听
│       └── res/                       # 主题与应用图标
├── gradle/wrapper/                     # Gradle Wrapper
├── tests/                              # 数据、位置、相机、线路与分页检查
├── CHANGELOG.md
├── CONTRIBUTING.md
├── THIRD_PARTY_NOTICES.md
├── LICENSE
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
└── gradlew.bat
```

## 验证

当前版本已完成以下检查：

- `assembleDebug` 编译成功。
- 地区索引检查覆盖国家/地区归属、选项索引一致性、不可变快照与空结果；在一次 69,408 条缓存快照上核对索引结果。
- 线路检查覆盖 HTTPS 优先、原始地址回退、去重、HLS 元数据持久化；目录分页检查覆盖原始偏移、UUID 去重、空末页、完整目录参数与重复整页保护。
- `lintDebug` 通过，结果为 0 个错误。警告涉及依赖版本提示、备份配置和文本国际化。
- 核心数据检查共通过 20 项，其中包含对一次真实 3,000 条目录响应的解析检查。另有 9 项位置回退检查，以及 7 项地球相机检查，覆盖经纬度居中、背面遮挡、日期变更线、地区聚焦与缺失坐标。

`tests/` 下的检查使用独立 JVM 入口，不会由 `./gradlew test` 自动执行。需要 JDK 17、Android SDK 的 `android.jar`，以及 JVM 环境使用的 [org.json:json:20240303](https://repo.maven.apache.org/maven2/org/json/json/20240303/) JAR。该 JAR 仅供检查使用，应用使用 Android 内置的 `org.json`。

macOS / Linux：

```bash
export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk
export JSON_JAR=/absolute/path/to/json-20240303.jar

mkdir -p build/core-tests

javac -encoding UTF-8 \
  -cp "$JSON_JAR:$ANDROID_SDK_ROOT/platforms/android-35/android.jar" \
  -d build/core-tests \
  app/src/main/java/com/wanderadio/app/Station.java \
  app/src/main/java/com/wanderadio/app/StationRepository.java \
  app/src/main/java/com/wanderadio/app/CataloguePages.java \
  app/src/main/java/com/wanderadio/app/StreamRoutes.java \
  app/src/main/java/com/wanderadio/app/GeographyIndex.java \
  app/src/main/java/com/wanderadio/app/GlobeCamera.java \
  app/src/main/java/com/wanderadio/app/LocationResolver.java \
  tests/*.java

export TEST_CLASSPATH="build/core-tests:$JSON_JAR:$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
java -cp "$TEST_CLASSPATH" com.wanderadio.app.CoreTest
java -cp "$TEST_CLASSPATH" com.wanderadio.app.GlobeCameraTest
java -cp "$TEST_CLASSPATH" com.wanderadio.app.LocationResolverTest
java -cp "$TEST_CLASSPATH" com.wanderadio.app.StreamRoutesTest
java -cp "$TEST_CLASSPATH" com.wanderadio.app.CataloguePagesTest
java -cp "$TEST_CLASSPATH" com.wanderadio.app.GeographyIndexTest
```

核心检查默认运行 18 项；如果已有公开的 Radio Browser 电台数组样本，可额外传入文件路径执行两项目录解析检查：

```bash
java -cp "$TEST_CLASSPATH" com.wanderadio.app.CoreTest /absolute/path/to/catalog.json
```

数据与相机检查不替代音频验收。0.1.4 已在 OPPO PKV110 上验证直播和连续 3 次暂停后恢复播放；耳机切换、锁屏、长时间后台持续运行和睡眠定时仍需分别验收。

## 数据与隐私

- 无需注册应用账号。
- 收藏、最近收听和目录缓存保存在设备上。
- 应用未接入广告 SDK 或分析埋点；电台直播本身可能包含广告。
- 不申请定位、麦克风或通讯录权限。
- 电台地理位置来自目录数据，不读取用户位置。
- 目录查询和电台点击记录发送至 Radio Browser，音频请求发送至对应电台服务器；这些服务可获得请求 IP 和连接信息。
- 为兼容部分电台，应用允许 HTTP 明文音频连接，不能保证所有电台连接均经过加密。

## 当前限制

- 按每页 5,000 条拉取 Radio Browser 的全球目录，直至末页，不设热门台数上限；保留失效标记的电台，只排除缺失 ID 或没有 HTTP/HTTPS 播放地址的记录。完整加载前，搜索仅覆盖已加载的数据；数量会随上游更新，与其他网站的快照或筛选口径可能不同。
- 地球视图优先使用目录坐标；缺失时使用同国家、同地区电台的坐标群作为地区参考，或使用离线国家参考位置，并标注“近似位置”。国家代码也缺失或无效时，仍只能从列表收听。
- 流派根据目录标签粗略分类，不属于专业音乐分类结果。
- 码率、编码和地理信息依赖电台目录提供的数据；未知码率不会显示为推算值。
- 完整目录包含上游标记失效的电台；部分电台可能失效、限制地域访问，或使用设备不支持的编码。
- 全球地图为保证交互流畅限制同时绘制的点数；频道列表、地区筛选和搜索使用全部已加载记录。
- 本地目录缓存仅用于浏览目录，不提供离线音频收听。
- 当前没有歌曲识别、歌词、音频频谱、跨设备同步、桌面组件或电台闹钟。
- 本项目没有接入 RoamDial 的私有服务，也不承诺与原站所有功能或界面完全一致。

## 参与贡献

欢迎通过 Issue 提交错误反馈或功能建议，也欢迎提交 Pull Request。

反馈播放问题时，请提供系统版本、设备型号、电台名称、复现步骤和相关错误信息。提交修改时，请说明变更目的、验证方法，以及是否涉及播放服务或本地数据兼容性。

提交源码时，请排除 `local.properties`、`.gradle/`、构建产物、个人 IDE 配置和签名密钥。

## 致谢

- [Radio Browser](https://www.radio-browser.info/)：社区维护的电台目录。
- [Radio Browser API](https://api.radio-browser.info/)：目录查询和节点发现说明。
- [RoamDial](https://roamdial.com/)：随机收听和空间探索的交互灵感。
- 各电台及其运营者：提供实时广播服务。

## 许可证

应用代码采用 [MIT License](LICENSE)。国家参考数据遵循 ODbL 1.0，Media3 及其他依赖保留各自的许可证；详见 [第三方说明](THIRD_PARTY_NOTICES.md)。MIT 不授予电台广播内容或第三方数据的权利。
