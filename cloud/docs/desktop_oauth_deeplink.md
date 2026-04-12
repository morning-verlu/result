# Desktop OAuth DeepLink 接入说明

当前基础架构版本已实现以下能力：

- 登录页可以发起 OAuth（GitHub / Google）并拿到应打开的浏览器 URL。
- 应用内支持手动粘贴回调链接并调用 `handleDeepLink()`。
- 扫码登录提供 ticket 生成接口（`beginDesktopQrLogin()`），后续接实时授权。
- Windows Desktop 启动时自动尝试注册 `verlucloud://` 协议（当前用户 HKCU）。

下一步（生产化）建议：

1. OAuth `redirect_uri` 固定指向 `verlucloud://login`。
2. Windows 首次启动应用时会写入协议注册表：
   - `HKCU\\Software\\Classes\\verlucloud`
   - `HKCU\\Software\\Classes\\verlucloud\\shell\\open\\command`
3. 系统把 deep link 作为启动参数传回桌面 App，`main(args)` 会自动投递到 `AuthDeepLinkBus`。
4. `CloudAppRoot` 已订阅该 bus，会自动完成 session 更新和导航切换。
