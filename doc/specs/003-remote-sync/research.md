# Research: 远端同步

Markbook 同步整个 `Markbook/` 目录，而不是单独同步数据库记录。这样
Markdown、图片和其他附件始终可以被普通文件工具读取与备份。

## 已确定方向

Markbook 同步整个 `Markbook/` 目录，而不是单独同步数据库记录。这样
Markdown、图片和其他附件始终可以被普通文件工具读取与备份。

## 共同模型

每个同步端记录相对路径、内容摘要、修改时间和远端版本标识。同步开始时将
当前状态与上次成功同步的基线比较：

- 仅本地变化：上传。
- 仅远端变化：下载。
- 双边均变化：两份都保留，副本名追加
  ` (冲突-<设备名>-<时间戳>)`。
- 删除与另一端编辑同时发生：保留编辑版本，不静默删除。

一次同步完全成功后才能更新基线。中断时保留旧基线，下一次操作必须幂等。

## OneDrive

使用 Microsoft Graph 与 OAuth 2.0 Authorization Code + PKCE。权限限制为
`Files.ReadWrite.AppFolder`、`offline_access` 和身份所需的最小范围。文件
放在 OneDrive 应用目录的 `Markbook/` 下，小文件直接上传，大图片使用可恢复
上传会话。远端变更通过 `driveItem/delta` 和持久化的 opaque delta link 拉取。
刷新令牌必须写入 HarmonyOS Asset Store，日志不得包含令牌或下载 URL。

## WebDAV 与 NAS

NAS 首选 WebDAV over HTTPS。远端版本使用 `ETag`，目录枚举使用
`PROPFIND Depth: 1`，写入使用临时文件加 `MOVE`，避免网络中断留下半文件。
证书错误默认失败；不得提供“忽略 TLS”开关。SMB 可作为后续独立 provider，
不应混入 WebDAV 实现。

## 后台与用户控制

首版只支持用户主动同步。加入后台任务前，需要具备网络约束、退避重试、
前台状态提示和可取消操作。首次启用任何远端时明确说明哪些文件会离开设备。

## 待验证

- HarmonyOS 上适合 Microsoft Entra PKCE 的系统浏览器回调方案。
- Network Kit 对 WebDAV `PROPFIND`、`MOVE` 和大文件流式传输的支持边界。
- 主流 NAS（群晖、威联通及通用 WebDAV）的 ETag 与锁行为差异。
- OneDrive Personal 与 Business 在 App Folder、delta 和上传会话上的差异。
