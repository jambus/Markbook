# Implementation Plan: 拍照与本地附件

## Technical Context

Android 端使用系统相机 Intent/Activity Result，HarmonyOS 端使用
`ohos.want.action.imageCapture`。两端都将返回 URI 的内容复制到 Vault 附件目录，
使用加密随机 UUID 命名，并从文件签名字节确定扩展名和 MIME 类型。

## Constitution Check

- [x] 使用系统相机，不额外申请直接控制相机权限。
- [x] 附件写入后使用相对 Markdown 链接。
- [x] 文件与正文按事务式流程提交。
- [x] 取消、空间不足和崩溃可通过事务标记恢复。
- [ ] 真机完成拍照、重启和连续多拍验证。

## Transaction

1. 对应平台的系统相机返回 URI。
2. 复制到 `.markbook/tmp/`，校验非空、同步落盘并识别文件签名。
3. 写入附件事务标记，再原子移动到 `attachments/`。
4. 在当前选区插入相对链接并原子保存笔记。
5. 删除事务标记并更新预览；保存失败则回滚附件和正文。
6. 应用启动时扫描未完成标记：已被 Markdown 引用的附件保留，未引用附件删除。
