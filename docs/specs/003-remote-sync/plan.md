# Implementation Plan: Google Drive 与 NAS 同步

## Architecture

`SyncEngine` 比较本地快照、远端变更和上次成功基线，生成操作计划；
`SyncProvider` 只负责协议 I/O；`SyncConflictResolver` 负责无协议依赖的冲突
决策。Android APK 与 HarmonyOS HAP 分别实现该边界；Google Drive 和 NAS
Provider 共用文件内容流、基线、断点与错误契约及测试样例。

## Constitution Check

- [x] Provider 与本地仓储边界已定义。
- [x] 冲突默认保留两份。
- [x] 登录状态优先使用系统安全存储，能力不足时仅驻留内存。
- [x] 增量状态只在整轮成功且两端摘要一致后提交。
- [ ] Google Drive 与两台目标 NAS 真实环境通过故障注入测试。

## Delivery Order

1. 定义文件快照、同步基线、操作计划和 Provider 契约。
2. 实现本地模拟 Provider，完成冲突和中断测试。
3. 实现 Google Drive 官方授权、增量变更和可恢复上传。
4. 调研并实现同时适配威联通 TS-251D、极空间的通用 NAS Provider，验证局域网
   与外网访问、证书和权限行为。
5. 增加配置、提供方无关的后台任务状态、进度、取消、通知、详情、错误摘要和手动同步 UI。
6. 完成多设备、弱网、令牌失效和 NAS 兼容性验证。

## Android Google Drive First Slice

Android APK 先交付一个可在 Mate 60 使用的手动双向同步闭环：Google Play 服务账号授权、
Drive Vault 文件夹选择与新建、首次范围确认、递归比较、上传、下载、保留冲突副本和可见结果。
实现直接使用 Drive REST API，不引入已废弃的 Drive Android API，也不把访问令牌写入
SharedPreferences、Vault 或日志。

首阶段每轮重新列出远端，且不传播删除；它是安全可用的电脑 Obsidian 互通基础，而非
完整的增量同步实现。完成后再接入 Drive changes 游标、条件写入、可恢复上传和远端
Provider 回收站；本地 `.trash/` 始终排除在同步范围外。

后台执行采用每个 Vault 唯一的前台后台任务。其状态、通知和详情页只依赖通用任务模型，
Google Drive 仅提供授权与传输实现。网络传输不占用编辑器；远端下载在最终提交前重新检查
同路径本地文件，发现并发本地改动时保留两份内容。
