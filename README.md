

![1](./app/src/main/res/mipmap-xhdpi/ic_launcher.png)
# FingerprintPay
让微信、支付宝、淘宝、腾讯QQ、云闪付在支持指纹识别的手机上使用指纹支付.

## 最低要求
* 有指纹硬件
* Android 6.0+
* [Magisk](https://github.com/topjohnwu/Magisk)、[Zygisk](https://github.com/topjohnwu/Magisk) 、 [Xposed](https://github.com/ElderDrivers/EdXposed) 或 [APatch](https://github.com/bmax121/APatch) + [Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext)

## 实现原理
1. 利用 Zygisk 或 Xposed 加载指纹支付模块
2. 在指纹支付模块中录入应用的"支付密码"
3. 在对应应用内完成指纹确认，使用该应用的硬件 Android Keystore 密钥进行 AES-GCM 加密。原生模块将配置和密码密文保存在 `/data/adb/fingerprintpay/`；Xposed 由模块 APK 的 Provider 集中保存。密钥不能导出，宿主不保存配置或密码密文副本。
4. 对应程序在支付界面时, 验证手机指纹, 验证成功解密"支付密码"
5. 自动替代用户输入"支付密码", 完成支付操作

## 国内镜像
- [夸克网盘](https://pan.quark.cn/s/dae9bc84d64f)
- [镜像网盘](https://file.xdow.net/fingerprintpay/)
- [加群下载](#提示)

## 使用教程

### Magisk + Zygisk

1. 确认 Magisk Manager 应用设置中启用 Zygisk功能
2. 下载插件: [zygisk-module-xfingerprint-pay-all-release.zip](https://github.com/eritpchy/FingerprintPay/releases)
3. 进入 Magisk Manager 的模块页面安装五合一 ZIP，安装后在 WebUI 调整作用域。
4. 确认启用模块, 重启手机
5. 录入支付密码后开始使用。

### APatch + Zygisk Next

1. 下载插件: [Zygisk-Next-release.zip](https://github.com/Dr-TSNG/ZygiskNext/releases)
2. 下载插件: [zygisk-module-xfingerprint-pay-all-release.zip](https://github.com/eritpchy/FingerprintPay/releases)
3. 进入 Apatch 管理器, 模块, 安装这几个模块, 没装完不要重启, 安装完毕后再重启手机
4. 开机后确认模块工作是否正常, 若不正常再次重启手机
5. 录入支付密码后开始使用。

### Xposed

1. 下载并安装插件: [xposed.com.surcumference.fingerprintpay.release.apk](https://github.com/eritpchy/FingerprintPay/releases/latest)
2. 在Xposed管理器启用插件, 确保作用域正确
3. 重启手机
4. 找到指纹支付app录入密码开始使用
5. 录入支付密码后开始使用。

## 常见问题

1. 为什么在微信或支付宝内看不到指纹支付设置菜单？\
   7.0.0 起不再提供应用内设置菜单，请在 Root 管理器中打开模块 WebUI，或打开“指纹支付”应用进行配置。
2. Magisk 没有 WebUI 入口怎么办？\
   可安装 [KsuWebUI](https://github.com/KOWX712/KsuWebUIStandalone/releases)（Android 8.0+），授予 Root 权限后选择“Zygisk - 指纹支付”进行配置。

## 致谢
* [Magisk](https://github.com/topjohnwu/Magisk)
* [WechatFp](https://github.com/dss16694/WechatFp)
* [Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext)
* [APatch](https://github.com/bmax121/APatch)
* [LSPosed](https://github.com/LSPosed/LSPosed)

## 提示

1. 本软件的网络功能仅限检查自己软件更新功能, 如不放心, 欢迎REVIEW代码.
2. 支付宝、淘宝、微信、QQ、云闪付支持版本请参考镜像站的适配版本, 随意升级新版本可能不兼容
3. 自5.0.0版本开始, 如果您**每次**(请注意, 是**每次**!)都识别出错第一次, 属于不正常现象, 正常现象应为首次出错一次,后续正常, 您可以删除系统指纹再重新添加并重新录入支付密码尝试
4. Zygisk Next 需要开启"遵守排除列表", 如果取消, 会导致框架全局排除列表失效. 不保证每个框架都如上述表现, 具体以自己测试结果为准
5. 测试环境: Oneplus Ace5 + Apatch + Zygisk Next
6. Oneplus用户一般是能用上TEE的，用不上请自己找原因

<img src="./doc/qq_group.jpg" alt="QQ交流群: [665167891]" width="500">

#### QQ交流群: [665167891](https://h5.qun.qq.com/h5/qun-share-page/?_wv=1027&k=fCZf_WEKL1Rj_N0gi9JgkH7bfnKj11Wy&authKey=acNcoIs325Uco7v2JZY4NObRFA3sJU%2FWI1%2FH64DkP50cn6HBRUzBZ9cvZGNqmzGi&market_channel_source=665167891_1&noverify=0&group_code=665167891)
