# Talk 表情贴纸（Lottie）

聊天页支持 Lottie 表情贴纸，资源由 `manifest.json` 索引。

## 目录结构

```
assets/stickers/
├── manifest.json           # 贴纸包索引
└── lottie/                 # Lottie JSON 资源
    ├── tiger_pop.json
    ├── tiger_shake.json
    └── tiger_jump.json
```

## manifest.json 字段

```json
{
  "packs": [
    {
      "id": "tigers",                  // 贴纸包 id (英文，唯一)
      "name": "虎虎表情包",            // 显示名
      "cover": "asset:///...",         // 封面 (可选)
      "stickers": [
        {
          "id": "happy",               // 单贴纸 id (英文，包内唯一)
          "name": "开心虎",            // 显示名 (可选)
          "url": "asset:///stickers/lottie/tiger_pop.json",  // assets:/// 或 https://
          "lottie": true,              // 是否 Lottie；默认 true
          "preview": null              // 静态预览图 URL；可选
        }
      ]
    }
  ]
}
```

## 替换为 iconfont 真实虎虎表情包

1. 把 9 个 Lottie JSON 拷到 `assets/stickers/lottie/`，命名建议英文（例如 `iconfont_tiger_happy.json`）。
2. 在 `manifest.json` 中将每个 sticker 的 `url` 替换为新文件路径。
3. 重新构建即可，无需修改任何 Kotlin 代码。

## 消息内容协议

发送贴纸消息时：
- `messages.type = "sticker"`
- `messages.content = "sticker://<packId>/<stickerId>"`

收到旧版客户端不识别 `sticker` 类型时，会自动回落为 `[表情]` 占位文本。
