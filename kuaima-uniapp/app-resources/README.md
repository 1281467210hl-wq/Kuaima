# App 打包素材

本目录存放 App 打包用的原始素材，**不会被打包进小程序**（`static/` 下的文件会被打进小程序，所以不放那里）。

| 文件 | 说明 | 状态 |
|---|---|---|
| `icon-1024.png` | App 图标原图（1024×1024） | **模拟图标**（橙底白字 K + TEST 角标），仅供内部测试包使用，正式上架前替换为设计提供的图标 |

## 使用方法：生成各尺寸图标

1. 用 HBuilderX 打开 `kuaima-uniapp` 项目，打开 `src/manifest.json`，切到「App 图标配置」。
2. 点「浏览」选择本目录下的 `icon-1024.png`，再点「自动生成所有图标并替换」。
3. HBuilderX 会生成 Android / iOS 所需的各种尺寸，并自动写入 `manifest.json` 的图标路径。

启动图：内部测试包使用默认的白底启动页即可（`manifest.json` 中 `splashscreen.androidStyle` 为 `common`），正式上架前再按设计稿配置。
