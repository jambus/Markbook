# HAP 本地签名

仓库不保存签名证书、私钥或 provisioning profile。`scripts/build-hap.sh` 在没有
本地签名配置时会成功生成 unsigned HAP，但该文件不能直接安装到真机。

## DevEco Studio

1. 用 DevEco Studio 打开仓库，在项目的 Signing Configs 中创建或选择本机 debug
   signing config，并将它绑定到 `default` product。
2. 选择自动生成 debug 签名材料，或使用团队已经分发的本地 profile；证书、私钥和
   profile 只保存在本机，不复制到仓库。
3. 在 DevEco 内置 Terminal 中运行 `./scripts/build-hap.sh`，确认 Hvigor 的
   `SignHap` 不再显示 `skip sign`。
4. 使用 `hdc install` 安装生成的已签名 HAP，并记录设备型号、系统版本、包版本和
   签名配置名称。

## 仓库规则

不要提交 `.p12`、`.pfx`、`.cer`、`.p7b`、签名 profile、密码或包含私钥的配置。
如果构建机需要固定路径，应通过本机环境变量或 DevEco 设置提供，不要写入
`build-profile.json5` 的共享配置。
