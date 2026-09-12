# Quickstart: 共享 Vault 与跨端互操作验收

## 前置条件

- Android APK 已通过 [`005/quickstart.md`](../005-harmonyos4-android-apk/quickstart.md)
  的平台验收。
- HAP 已通过 [`006/quickstart.md`](../006-harmonyos5-6-native-hap/quickstart.md) 的平台验收。
- 准备一个可由两台测试设备与电脑 Obsidian 访问或安全复制的测试 Vault，并保留验收前快照。

## 验收步骤

1. 在 Android APK 中创建含中文、列表、标签、`[[双向链接]]` 和照片的笔记，至少校正
   一张照片；确认保存后记录 Markdown、相对路径和附件文件名。
2. 在 HAP 中重新选择该 Vault，不执行导入或转换；打开同一笔记并确认正文、原图和校正图。
3. 在 HAP 中继续编辑并插入照片，再由 Android APK 重新打开；两端均不得改名、搬移或
   创建私有 Vault 副本。
4. 使用 `shared-testdata/` 比较两端生成的 Markdown、附件命名、图片格式和恢复结果。
5. 在电脑 Obsidian 中打开该 Vault，确认两端内容和图片无需插件或格式迁移即可读取。
6. 模拟从 HarmonyOS 4 升级到 5/6 后重新授权原 Vault，确认继续编辑且历史相对链接有效。
7. 验证同路径双边修改保留两个版本；本地删除进入 `.trash/` 且不参与远端同步，远端删除
   使用 Provider 的可恢复回收站。
8. 检查两端日志不包含令牌、密钥、临时下载 URL 或笔记正文。

记录 APK/HAP 版本、签名配置名称、设备型号、系统版本、权限状态、每步结果和例外。
