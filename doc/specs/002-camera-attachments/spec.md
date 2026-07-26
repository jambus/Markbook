# Feature Specification: 拍照与本地附件

**Status**: Draft  
**Priority**: P1  
**Depends on**: 001-local-markdown-notebook

## User Stories

### US1 从正文拍照（P1）

用户在编辑笔记时点击拍照，完成拍摄后立即回到原编辑位置，看到图片预览，
且 Markdown 链接和图片已经本地保存。

**Acceptance**: 拍照后强制结束应用，重启仍能在该笔记中打开图片。

### US2 连续记录（P2）

用户可以连续拍摄多张图片，每张图片都按拍摄顺序插入，不需要离开编辑器。

### US3 失败可恢复（P1）

用户取消拍照、存储空间不足或系统相机失败时，原正文不受损且不会产生孤儿文件。

## Functional Requirements

- **FR-201** 系统必须通过 `ACTION_IMAGE_CAPTURE` 拉起系统相机，以兼容
  HarmonyOS 4；不得在应用内重复实现相机预览和拍摄流程。
- **FR-202** 返回的 URI 必须复制到笔记本 `attachments/` 后再持久引用。
- **FR-203** Markdown 必须使用相对路径，不能保存临时或设备绝对 URI。
- **FR-204** 成功提示前必须同时完成附件和正文保存。
- **FR-205** 文件扩展名和 MIME 类型必须与真实图片格式一致。
- **FR-206** 用户取消不得显示错误提示。
- **FR-207** 失败或回滚产生的临时文件必须清理。
- **FR-208** 图片处理不得阻塞编辑输入或导致界面长时间无响应。
