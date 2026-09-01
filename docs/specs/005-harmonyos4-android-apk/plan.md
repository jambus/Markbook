# Implementation Plan: HarmonyOS 4 Android APK

## Technical Context

- Kotlin Android 工程位于 `android/`，使用项目内 Gradle Wrapper。
- 当前开发版本为 `versionName 0.3.0`、`versionCode 6`；后续候选版本按
  [`docs/RELEASE_NOTES.md`](../../RELEASE_NOTES.md) 的规则提升并记录。
- 通过 Storage Access Framework 选择 Vault 并持久化目录 URI 权限。
- 通过 Activity Result API 拉起系统相机并复制返回内容。
- 平台状态使用 Android 系统安全存储；Markdown 与图片只写入 Vault。
- 富文本编辑器和图片透视实现先做 Mate 60 兼容性原型，再确定最终组件。

## Delivery Phases

1. 创建最小工程，生成并安装 arm64 debug APK。
2. 验证目录选择、持久授权、文件原子写入和应用重启恢复。
3. 完成每日笔记、所见即所得编辑、自动保存和手动保存。
4. 完成系统拍照、直接插入、矩形裁剪和四点透视校正。
5. 使用共享样例和电脑 Obsidian 验证文件兼容性。
6. 以 Vault 文件库作为启动页，提供目录浏览、笔记列表与保留上下文的编辑器导航。
7. 增加设置页，持久保存显示模式和每日笔记目录；目录变更只影响后续每日笔记的创建位置与相对链接计算。
8. 根据 UI 评审完成拍照处理默认选区、模式选中态、原编辑位置插入、持续写入反馈、文件库恢复状态和跨端视觉语义统一。
9. 在设置页显示版本、构建号和当前语言能力；中文为首发语言，完整中英文国际化作为后续独立交付。
10. 将当前已实现功能归档为 Android APK `0.1.0` 开发基线，并在每次候选构建前同步
    release notes、平台规格、`versionName` 与单调递增的 `versionCode`。
11. 在设置页新增“存储”分组，后台统计 `.trash/` 直接内容；空回收站禁用操作，非空时
    二次确认后逐项永久删除，附件保留，结果持续显示并允许失败项重试。
12. 增加普通文件库管理：以独立命名策略校验新建/重命名，以 Provider 原子移动处理回收站，
    并用真实笔记位置计算附件路径与修复每日目录配置。
13. 为主文件库普通条目增加原生左滑操作行：以可单测手势策略处理轴锁、阈值、钳制和甩动，
    保留长按和辅助技术入口；左滑仅显示重命名或删除，删除仍复用既有确认与 Provider 移到回收站流程。
14. 将编辑器拍照入口扩展为系统相机拍照/短视频选择；视频经确认页原样流式写入、hash
    冲突检查和 marker 事务后，在原光标插入普通 Markdown 相对链接。

## Constitution Check

- Vault 文件是唯一事实源，缓存可重建。
- 保存和附件插入具备失败回滚。
- 凭据不进入 Vault 或普通配置。
- 完成 Mate 60 HarmonyOS 4 真机验收后才标记交付。
- Android 界面实现前参照 `docs/design/`；文件库与编辑器的导航、空状态、保存状态和
  返回行为通过 `review_checklist.md` 检查。

## Risks

HarmonyOS 4 Android 兼容层可能对持久 URI 权限、系统相机返回、WebView 编辑器和
后台任务存在差异。所有关键平台选型先以小型真机原型验证，失败时再替换组件。
