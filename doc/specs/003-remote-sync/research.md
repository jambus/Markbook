# Research: 远端同步

## 已确定方向

Markbook 首版同步 `notes/` 和 `attachments/`，而不是数据库记录。内部
`.markbook/`、凭据和同步基线不进入远端，避免设备状态
反复自同步。Markdown、图片和其他附件仍可被普通文件工具读取与备份。

## 共同模型

每个同步端记录相对路径、内容摘要、修改时间和远端版本标识。同步开始时将
当前状态与上次成功同步的基线比较：

- 仅本地变化：上传。
- 仅远端变化：下载。
- 双边均变化：两份都保留，副本名追加
  ` (冲突-<设备名>-<时间戳>)`。
- 删除与另一端编辑同时发生：保留编辑版本，不静默删除。

一次同步完全成功后才能更新基线。中断时保留旧基线，下一次操作必须幂等。

本地扫描使用 SHA-256 分段摘要，文件读写使用固定大小数据块。下载先写入内部
临时目录并执行 `fsync`，再通过重命名替换目标；符号链接和不受支持的顶层
目录会被拒绝。依据：
[OpenHarmony API 12 消息摘要指南](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/security/CryptoArchitectureKit/crypto-generate-message-digest.md)、
[Core File Kit API 12](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-core-file-kit/js-apis-file-fs.md)。

## OneDrive

使用 Microsoft Graph 与 OAuth 2.0 Authorization Code + PKCE。权限限制为
`Files.ReadWrite.AppFolder`、`offline_access` 和身份所需的最小范围。文件
放在 OneDrive 的 Markbook 应用专属目录下，小文件直接上传，大图片使用可恢复
上传会话。远端变更通过 `driveItem/delta` 和持久化的 opaque delta link 拉取。
刷新令牌必须写入 HarmonyOS Asset Store，日志不得包含令牌或下载 URL。

认证层已拆成可测试的 OAuth 核心和 HarmonyOS 适配器。PKCE 使用
CryptoArchitectureKit 的安全随机数与 SHA-256；回调必须同时匹配
`markbook://oauth/onedrive` 和一次性 `state`。系统浏览器通过
`ohos.want.action.viewData` 与 `entity.system.browsable` 打开，回调由
`onCreate`/`onNewWant` 转交页面。Access token 只保存在内存中，refresh
token 写入 Asset Store。由于 Asset Store 的 `SECRET` 单条上限为 1024
字节，令牌按 generation 分片保存，并在所有新分片成功后原子切换清单。
依据：
[Asset Store API 12](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-asset-store-kit/js-apis-asset.md)、
[CryptoArchitectureKit API 12](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-crypto-architecture-kit/js-apis-cryptoFramework.md)、
[Microsoft 授权码与 PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)。

Provider 已按 app folder 的稳定 item ID 工作，不依赖本地化路径。Delta
分页会保留最终 delta link、文件 ID、父目录 ID 和目录层级；目录删除会展开
为该目录下基线文件的 tombstone。小文件使用条件 PUT，大图片使用 320 KiB
整数倍分片的上传会话，预签名上传 URL 不携带 OAuth 头。若最终响应丢失，
Provider 会范围回读并比对内容，再决定是否将操作视为成功。依据：
[OneDrive app folder](https://learn.microsoft.com/en-us/graph/onedrive-sharepoint-appfolder)、
[driveItem delta](https://learn.microsoft.com/en-us/graph/api/driveitem-delta?view=graph-rest-1.0)、
[可恢复上传会话](https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession?view=graph-rest-1.0)。

## WebDAV 与 NAS

NAS 首选 WebDAV over HTTPS。远端版本使用 `ETag`，目录枚举使用
`PROPFIND Depth: 1`，写入使用临时文件加 `MOVE`，避免网络中断留下半文件。
证书错误默认失败；不得提供“忽略 TLS”开关。SMB 可作为后续独立 provider，
不应混入 WebDAV 实现。

API 12 Network Kit 已验证支持流式响应、自定义请求头、系统 CA、自定义 CA 和
证书锁定，但 `RequestMethod` 仅包含 GET、HEAD、POST、PUT、DELETE、OPTIONS、
TRACE、CONNECT，不包含 WebDAV 必需的 `PROPFIND` 与 `MOVE`。因此不能把
ArkTS 类型强转当作受支持实现。后续必须选择经过真机验证的原生传输层，或将
NAS 首版调整为只使用标准 HTTP 方法的 S3 兼容 Provider。依据：
[OpenHarmony API 12 HTTP API](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-network-kit/js-apis-http.md)。

## 后台与用户控制

首版只支持用户主动同步。加入后台任务前，需要具备网络约束、退避重试、
前台状态提示和可取消操作。首次启用任何远端时明确说明哪些文件会离开设备。

## 待验证

- 在 Microsoft Entra 测试租户注册 HarmonyOS 自定义 URI，并在 API 12
  真机验证系统浏览器冷启动、热启动和用户取消回调。
- Network Kit 对 WebDAV `PROPFIND`、`MOVE` 和大文件流式传输的支持边界。
- 主流 NAS（群晖、威联通及通用 WebDAV）的 ETag 与锁行为差异。
- OneDrive Personal 与 Business 在 App Folder、delta 和上传会话上的差异。
