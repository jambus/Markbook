# Implementation Plan: HarmonyOS 4 Android APK

## Technical Context

- Kotlin Android 工程位于 `android/`，使用项目内 Gradle Wrapper。
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
