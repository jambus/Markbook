# Implementation Plan: Mate 双端适配

## Strategy

采用双工程、单数据契约。仓库保留现有 HarmonyOS Stage 工程，并新增 `android/`
Gradle 工程。Android 端使用 Kotlin，优先产出可在 Mate 60 HarmonyOS 4 安装的
APK；HarmonyOS 端继续使用 ArkTS，面向 HarmonyOS 5/6 产出 HAP。

首版不抽取跨语言运行时代码。可共享部分限定为规格、Markdown/Vault 格式样例、
图片变换输入输出样例和验收用例，从而降低两端耦合。

## Planned Structure

```text
Markbook/
├── android/                  # HarmonyOS 4 APK，Kotlin/Gradle
│   └── app/src/
├── entry/                    # HarmonyOS 5/6 HAP，ArkTS/Hvigor
├── shared-testdata/          # 两端共同读取的 Markdown 与图片样例
└── doc/specs/                # 产品规格、计划和任务
```

## Platform Boundaries

- Android：系统目录选择器及持久 URI 权限、系统相机、平台安全存储、后台任务。
- HarmonyOS：系统文件选择 Ability、CameraPicker、Asset Store、后台任务。
- 共享契约：UTF-8 `.md`、Vault 原目录、POSIX 风格相对链接、`attachments/`、
  原图/校正图命名规则、冲突副本和回收站约定。
- 同步 Provider 在两端分别实现，但必须通过同一组文件级契约测试。

## Delivery Phases

1. 建立 Android 工程、构建 APK，并在 Mate 60 HarmonyOS 4 验证安装和目录授权。
2. 在 Android 端完成本地编辑、自动保存和拍照校正闭环。
3. 用共享样例校验 APK、HAP 和电脑 Obsidian 的文件互操作。
4. 补齐 HarmonyOS 5/6 HAP 功能并执行对应真机回归。
5. 在两端实现并验证 Google Drive/NAS 同步。

## Constitution Check

- Vault 文件仍是唯一事实源，不引入必须迁移的数据库。
- 两端写入操作均采用临时文件、原子替换和失败回滚。
- 相机返回内容落盘与 Markdown 链接插入保持事务式语义。
- 凭据使用平台安全存储，不进入 Vault、日志或普通配置。
- 每个交付物均需在对应系统真机验收。

## Risks

- HarmonyOS 4 Android 兼容层对目录授权、相机 URI、后台任务和 WebView 的行为
  可能与标准 Android 设备不同，必须以 Mate 60 真机为准。
- 手机系统升级后 APK 私有存储不会自动供 HAP 读取，因此任何用户内容都不能只
  存在应用沙箱；Vault 重新授权流程必须清晰。
- 两端独立实现可能产生 Markdown、附件命名和图片变换差异，应以共享样例阻止漂移。
