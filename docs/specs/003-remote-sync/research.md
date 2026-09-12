# Open Research: Google Drive 与 NAS 同步

已确定的同步范围、架构、冲突策略和交付顺序统一维护在 [`plan.md`](plan.md)，数据字段
见 [`data-model.md`](data-model.md)，Provider 行为见
[`contracts/sync-provider.md`](contracts/sync-provider.md)。本文只保留尚未形成结论、必须
通过平台或真实设备验证的问题，避免与计划重复维护。

## Google Drive 待验证

- Mate 60 HarmonyOS 4 是否具备当前 Google Play Services 授权流程所需的完整运行环境；
  若不具备，需选择不依赖 GMS 的官方 OAuth 流程并更新 `003` 与 `005`。
- 用户选择的 Drive Vault 使用完整 Drive scope 时的审核、限额和正式发布要求。
- Drive changes 游标、条件写入、较大图片可恢复上传、范围下载和远端回收站语义。
- 电脑端 Obsidian 与 禾记（Heji Notes） 同时修改时，修改时间、revision 和冲突副本的实际表现。

## NAS 待验证

- 威联通 TS-251D 与极空间共同稳定提供的协议；不得在验证前预设为 S3、WebDAV、SMB
  或某一厂商私有接口。
- 两台设备在局域网和外网下的地址形态、TLS 证书、认证方式和权限错误表现。
- 目录枚举、条件写入、文件锁、断点续传、删除恢复和大文件读取能力。

## 平台待验证

- Mate 60 上的安全存储、后台网络、网络恢复和长任务取消行为。
- Activity 重建或用户离开同步页后，任务状态与结果重新进入的实现约束。
