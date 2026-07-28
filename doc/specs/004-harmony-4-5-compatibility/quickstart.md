# Quickstart: Mate 双端验收

## HarmonyOS 4 APK

1. 使用 Android 工程生成 debug APK，并安装到 HarmonyOS 4 Mate 60。
2. 选择已有 Obsidian Vault，创建每日笔记并输入中文、列表和 `[[双向链接]]`。
3. 拍照后分别测试直接插入、矩形裁剪和四点透视校正。
4. 强制结束并重新打开，确认正文、原图、校正图和相对链接均可恢复。
5. 重启手机后再次打开 Vault，确认目录授权仍有效；撤销权限后应要求重新选择。

## HarmonyOS 5/6 HAP

1. 使用 DevEco Studio 同步、签名并构建 Stage HAP。
2. 在 HarmonyOS 5 和 6 Mate 手机上重复本地笔记与拍照核心流程。
3. 选择 APK 已写入的测试 Vault，确认无需转换即可继续编辑。

## Cross-platform Checks

1. 使用共享样例比较两端生成的 Markdown、附件路径和图片格式。
2. 在电脑 Obsidian 中打开同一 Vault，确认笔记与图片正常显示。
3. 检查两端日志不包含令牌、密钥、临时下载 URL 或笔记正文。
