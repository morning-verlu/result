# 用户手册截图清单（替换指南）

你只需要做一件事：把真实截图按下面路径和文件名放进来。

---

## 1. 命名规则

- 全部使用 `png`
- 尺寸建议：手机竖图宽 1080，桌面图宽 1600+
- 文件名不要改（文档里已经引用了这些路径）

---

## 2. 目录与文件清单

## common
- `common/01-login.png`（统一登录页）
- `common/02-forgot-password.png`（忘记密码页）

## sync
- `sync/01-home-tabs.png`（首页底部导航）
- `sync/02-weather-location.png`（天气页+定位授权）
- `sync/03-sso-scan.png`（扫码授权页）

## talk
- `talk/01-add-friend-by-email.png`（添加好友弹窗）
- `talk/02-new-friends.png`（新的朋友列表）
- `talk/03-chat-room.png`（聊天页）
- `talk/04-qr-add-friend.png`（扫码加好友）

## doctor
- `doctor/01-search.png`（搜索页）
- `doctor/02-article-detail.png`（文章详情）

## cnchess
- `cnchess/01-friends-list.png`（好友列表）
- `cnchess/02-invite-dialog.png`（邀请弹窗）
- `cnchess/03-game-board.png`（棋盘对局）
- `cnchess/04-history-replay.png`（历史回放）

## music
- `music/01-local-list.png`（本地音乐列表）
- `music/02-online-search.png`（在线搜索页）

## cloud
- `cloud/01-file-list-upload.png`（文件列表+上传按钮）
- `cloud/02-download.png`（下载流程）
- `cloud/03-desktop-qr-login.png`（桌面扫码登录）

## admin-web
- `admin-web/01-login.png`（后台登录）
- `admin-web/02-release-form.png`（发布表单）
- `admin-web/03-release-list.png`（版本列表）

---

## 3. 拍图建议（保证“像产品手册”）

- 同一 App 保持同一主题（浅色或深色，不混用）
- 截图前清掉无关通知和测试数据
- 核心按钮要在可视区域内（不用滚动条）
- 有流程步骤的，尽量按 1→2→3 连续截图

---

## 4. 导出 PDF

替换完截图后，回到仓库根目录执行：

```powershell
.\scripts\build-docs.ps1 -DocSet user
```

默认会输出：`docs/dist/Verlu-用户使用手册.pdf`

