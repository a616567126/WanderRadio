# 漫听 v0.1.5

原生 Android 全球网络电台客户端，支持 Android 8.0 及以上版本。

## 本次修复

- 国家、省州选择器改为读取后台生成的缓存索引，减少全量目录下打开与选择时的主线程工作。
- 切换地区直接查找该地区电台；目录分页进度不再重复构建地图。
- 地图仅解析实际绘制的点位，频道列表仍使用完整目录。
- 补充 MIT 许可证、贡献指南与最新构建、检查命令。

## 已有功能

- 全量电台目录分页、UUID 去重与本地缓存；一次测试快照为全球 69,408 台、中国 2,441 台。
- 地球/流派星云、国家与省州筛选、国旗、大尺寸频道列表、收藏与最近收听。
- Media3 ExoPlayer、MP3/HLS 播放、HTTPS 优先和原地址回退、通知栏控制、睡眠定时。

目录包含失效标记，台数随上游变化，不代表所有线路均可播放。近似地理位置会标明精度。

## 构建与安装

```bash
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Release 可附上构建得到的 APK。调试版使用开发者调试签名，正式发行需要维护者自行配置发布签名。

完整说明见 [README](README.md)，版本变化见 [CHANGELOG](CHANGELOG.md)。应用代码采用 [MIT](LICENSE)，第三方数据与依赖保留各自许可证，见 [第三方说明](THIRD_PARTY_NOTICES.md)。
