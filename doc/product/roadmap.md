# 产品路线图

## M0 工程基线

建立双端规格体系、真机调试和持续构建入口。HarmonyOS 4 使用 Android APK，
HarmonyOS 5/6 使用原生 Stage HAP；两端共享 Vault 数据契约，不共享平台 UI。

## M1 HarmonyOS 4 APK 本地可用

优先在 Mate 60 HarmonyOS 4 上完成 Android APK：选择并读取已有 Obsidian Vault，
支持每日笔记、所见即所得编辑、自动保存、文件夹浏览、全文搜索、标签和双向链接。
退出并重启后数据保持完整。

## M2 HarmonyOS 4 APK 丝滑拍照

从每日笔记拉起系统相机，支持直接插入、矩形裁剪和四点透视校正；同时保留原图
与校正图，并写入相对 Markdown 链接。覆盖取消、权限/系统异常、空间不足及
孤儿附件清理。

## M3 HarmonyOS 5/6 原生对齐

在现有 Stage HAP 中实现与 APK 一致的本地笔记和拍照闭环，并验证同一 Vault
在 APK、HAP 和电脑 Obsidian 之间可直接读取。HAP 不要求复用 Android UI 代码。

## M4 可控同步

优先交付 Google Drive 与 NAS 二选一同步目标，支持手动双向同步、可选自动提示、
增量状态、进度、取消、离线恢复、回收站同步和保留双份的冲突处理。使用威联通
TS-251D 与极空间 NAS 完成真实验证。

## M5 知识工作区

后续再评估反向链接视图、关系图谱、插件和华为应用市场发布适配。
