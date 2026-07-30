# Tasks: HarmonyOS 4 Android APK

## 工程基线

- [x] T501 创建 `android/` Kotlin/Gradle 工程与 Gradle Wrapper。
- [x] T502 配置 application ID、arm64 构建、debug 签名和版本信息。
- [x] T503 使用 Gradle Wrapper 生成 debug APK。
- [ ] T504 在 Mate 60 HarmonyOS 4 完成安装、启动和升级安装。
- [ ] T505 记录可用 Android API、WebView、目录授权、相机 URI 和后台限制。

## 本地笔记

- [x] T506 实现 Vault 选择、持久 URI 权限、重新授权和最近 Vault 恢复。
- [x] T507 实现原子文件仓储、每日笔记和共享路径规则。
- [x] T508 实现所见即所得编辑、600 ms 自动保存和手动保存。
- [ ] T509 实现文件夹浏览、搜索、标签和双向链接基础能力。
- [x] T517 将启动页改为 Vault 文件库，显示当前 Vault、目录结构、Markdown 笔记和
  今日笔记入口。
- [x] T518 实现文件库到编辑器的逐层导航与返回上下文；搜索、标签和双向链接仍由
  T509 跟踪。

## 拍照闭环

- [x] T510 接入系统相机并事务式复制照片到 Vault。
- [x] T511 实现直接插入、矩形裁剪和四点透视校正。
- [x] T512 保存原图与校正图并插入相对 Markdown 链接。
- [x] T513 实现取消、权限拒绝、空间不足、强制结束后的临时文件清理和孤儿附件恢复逻辑。
  真机异常退出验证仍由 T515 覆盖。

## 验收

- [ ] T514 运行 Android 单测和共享格式样例。
- [ ] T515 在 Mate 60 验证竖横屏、字体缩放、重启和连续拍照。
- [ ] T516 输出可安装 APK、版本号、构建记录和真机验收结果。

本机已验证 `android/gradlew --offline --no-daemon assembleDebug` 成功，产物为
`android/app/build/outputs/apk/debug/app-debug.apk`；Mate 60 真机安装与验收仍未完成。
