# Quickstart: 同步验收

## OneDrive 配置

1. 在 Microsoft Entra 注册公共客户端，添加
   `markbook://oauth/onedrive` 重定向 URI。
2. 授予委托权限 `Files.ReadWrite.AppFolder`；应用会同时请求
   `offline_access`。
3. 将应用客户端 ID 写入
   `entry/src/main/resources/base/element/string.json` 的
   `onedrive_client_id`，不要添加客户端密钥。
4. 在 DevEco Studio 使用 HarmonyOS 4/API 10 或 HarmonyOS 5 真机运行，
   打开“同步”，连接 OneDrive，
   并确认浏览器能返回 Markbook。

## S3 兼容 NAS 配置

1. 准备支持 S3 path-style 请求、SigV4 和条件写入的 NAS 对象存储。
2. 在“同步”中选择 NAS，填写 HTTPS Endpoint、Bucket、Region、目录前缀及
   Access Key/Secret Key。
3. 连接后确认同步范围。凭据仅在本次运行保留，重启应用后需重新填写。
4. 首版单文件上传上限为 64 MiB；部署前验证 NAS 对 `If-Match` 和
   `If-None-Match` 的兼容性。

## 验收

先构建测试 HAP，确认 OAuth、SigV4、S3 分页、规划器、基线编解码、冲突保留
和故障注入测试通过。随后在两个独立安装实例和测试远端执行：

1. 设备 A 创建含三张照片的笔记并同步到空远端。
2. 设备 B 首次同步，确认 Markdown 和所有相对图片引用可用。
3. A、B 离线修改同一笔记，再依次同步。
4. 确认产生清晰命名的冲突副本，两份内容均未丢失。
5. 在上传大图中途断网并结束应用，恢复网络后重试。
6. 确认远端无半文件，本地内容完整，同步基线未提前更新。
7. 使凭据失效，确认应用要求重新认证且日志不含敏感值。
8. 取消浏览器授权与进行中的同步，确认 UI 可恢复且旧基线保持不变。
