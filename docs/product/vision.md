# 产品愿景

Markbook 是一款面向 Mate 手机个人用户的本地优先笔记应用。它以 OneNote 式的
低摩擦拍照记录为入口，直接编辑用户选择的 Obsidian Vault，并在 Google Drive
和 NAS 之间提供二选一的同步目标。

产品采用两套客户端实现：HarmonyOS 4 通过 Android APK 交付，HarmonyOS 5/6
通过原生 HarmonyOS HAP 交付。首要目标是让 Mate 60 的 HarmonyOS 4 用户可用，
随后补齐 HarmonyOS 5/6 原生版本；两端必须读写同一种 Vault 和 Markdown 格式。

## 目标用户

- 经常在现场、课堂、会议或旅途中混合记录文字与照片的人。
- 希望长期持有普通 Markdown 文件，而不被专有数据库锁定的人。
- 使用 Mate 手机快速记录、再在电脑 Obsidian 中整理的人。

## 核心价值

1. 随时可取：笔记和附件是普通文件，可备份、迁移和被其他工具读取。
2. 快速记录：从每日笔记中拍照、裁剪或校正后自动落盘并插入链接。
3. 无缝衔接：手机 Markbook 与电脑 Obsidian 共享同一套普通文件。
4. 同步可选：用户在 Google Drive 与 NAS 中选择一个远端，默认手动同步，
   可开启自动同步提示。
5. 不丢内容：异常退出、网络中断和多端冲突都不能静默覆盖数据。
6. 升级可延续：手机从 HarmonyOS 4 升级到 5/6 后，用户可继续使用原 Vault，
   不需要专有格式迁移。

## 非目标（首版）

- 多人实时协作编辑。
- 桌面端 Markbook 客户端。
- Android APK 与 HarmonyOS HAP 共用 UI 或平台代码。
- 服务端 Markdown 渲染或内容分析。
- 将笔记正文存入 Markbook 自有云服务。
- 在未经用户确认时自动上传本地内容。
