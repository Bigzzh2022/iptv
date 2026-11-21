# LeanTV

运行在 Android 5.1.1（API 22）老电视上的轻量级 IPTV 应用。电视端只有频道列表和播放器，所有配置（直播源、EPG、解码方式、缓冲档位、画面比例、拉流 UA）都通过手机/电脑浏览器在局域网内完成。

## 特性

- **单机前后端分离**：Android 原生电视端 + 内嵌 NanoHTTPD（端口 `10481`），无需外部服务
- **零遥控器配置**：电视端展示配置二维码（首次无源时自动弹出，遥控器菜单键可随时唤出），手机扫码即可管理
- **多格式直播源**：M3U / M3U8 / TXT，支持 `tvg-name`、`group-title`、`tvg-logo`、`#genre#` 分组和 `#` 多线路
- **配置页可预览频道**：按名称/分组搜索已解析频道，点击复制拉流地址
- **低内存设计**：Room + MMKV 替代重型存储，无跨平台框架，release 包约 3.1 MB
- **播放容错**：同频道多线路自动切换，线路全部失败后自动降级软解
- **面向老设备**：订阅源逐行流式解析（样本探测 UTF-8/GBK），频道网格按差异增量刷新；15s/50s 自定义缓冲水位、OkHttp 长连接 + DNS 缓存、内存低于 200MB 时自动软解

## 技术栈

| 模块 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 1.9.24、Leanback 1.0.0、XML |
| 播放器 | Media3 1.0.2（ExoPlayer + HLS + OkHttp DataSource） |
| 存储 | Room 2.5.2（KSP）、MMKV 1.2.16 |
| 网络 | OkHttp 4.10.0、NanoHTTPD 2.3.1 |
| 构建 | AGP 8.5.2、Gradle 8.7、JDK 17、compileSdk 34、minSdk 22 |

## 快速开始

环境要求：JDK 17、Android SDK Platform 34 + Build-Tools 34.0.0。

```bash
./gradlew assembleDebug        # 调试包
./gradlew assembleRelease      # 开启 R8 的发布包（未签名）
```

用 Android Studio 直接打开项目根目录即可运行。安装到电视后：

1. 保证电视与手机在同一局域网；
2. 电视首页右上角「配置二维码」或遥控器菜单键可显示二维码，手机扫码打开；也可手动输入 `http://192.168.1.20:10481`；
3. 手机浏览器打开该地址，添加 M3U/TXT 订阅源即可。

> 首次添加订阅源后频道在后台解析，稍等几秒回到电视端即可看到列表。

## Web API

所有响应均为 `{ "ok": true, "data": ... }` 或 `{ "ok": false, "error": "..." }`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 返回配置页（`assets/index.html`） |
| GET | `/api/config` | 获取订阅源（含频道数/最近错误）、EPG、解码方式、UA、缓冲档位、画面比例、TLS 兼容开关 |
| POST | `/api/config` | 更新设置或整体同步订阅源列表 |
| POST | `/api/source/refresh` | 同步刷新指定订阅源，body：`{"id": 1}`，返回 `count` 或 `error` |
| POST | `/api/source/refresh-all` | 依次刷新全部订阅源 |
| GET | `/api/channels` | 频道预览，参数 `q`（名称/分组搜索）、`limit`（默认 200，最大 500） |
| GET | `/api/status` | 电视 IP、版本、内存占用、频道数/源数量、正在刷新数 |

## 目录结构

```
app/src/main/java/com/lean/tv/
├── App.kt                     # Application：MMKV / Room / 协程作用域
├── data/                      # Room 实体与 DAO、配置存储、订阅源仓库
├── parser/                    # M3U / TXT 解析器
├── player/PlayerManager.kt    # 缓冲、软硬解切换、线路重试
├── server/                    # NanoHTTPD 服务与 REST 路由
├── ui/                        # Leanback 频道网格、播放页、配置二维码弹窗
└── util/                      # OkHttp + DNS 缓存、局域网 IP、二维码生成

app/src/main/assets/index.html # 单文件移动端配置页
```

## 兼容性与限制

- `targetSdk 22`：老电视不涉及运行时权限、前台服务限制和明文 HTTP 限制，部署最简单。
- 软解依赖系统自带的 `OMX.google.*` 解码器，未内置 FFmpeg，以控制内存占用。
- 仅针对电视横屏与遥控器操作设计，手机端只作为配置入口。
- 默认端口 `10481`，被占用时会启动失败，可在 `ConfigStore` 中调整后重新构建。

## 常见问题

**手机打不开配置页**
确认电视与手机在同一网段，并检查电视防火墙/路由器 AP 隔离是否关闭。

**频道列表为空**
查看订阅源 URL 能否在手机浏览器直接下载；部分源限制 UA 或需要代理。

**播放黑屏或频繁缓冲**
在配置页切换解码方式为「强制软解」；软解卡顿时优先使用码率更低的线路。
