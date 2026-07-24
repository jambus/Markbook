# Implementation Plan: 拍照与本地附件

## Technical Context

使用 `cameraPicker.pick` 获取系统相机结果，通过 Core File Kit 将 URI 内容复制
到笔记本附件目录。附件命名使用 UUID，并从媒体元数据确定扩展名。

## Constitution Check

- [x] 使用系统相机，不额外申请直接控制相机权限。
- [x] 附件写入后使用相对 Markdown 链接。
- [ ] 文件与正文按事务式流程提交。
- [ ] 取消、空间不足和崩溃不留下孤儿附件。
- [ ] 真机完成拍照、重启和连续多拍验证。

## Transaction

1. CameraPicker 返回 URI。
2. 复制到 `.markbook/tmp/` 并校验可读、大小和格式。
3. 原子移动到 `attachments/`。
4. 向正文插入相对链接并原子保存笔记。
5. 更新预览；任何一步失败则回滚临时文件和正文变更。

