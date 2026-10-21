# 第三方组件与数据

应用代码采用 [MIT License](LICENSE)。以下组件与数据保留各自的许可证。

## 国家参考数据

`app/src/main/assets/country-centers.json` 提取自 mledoze/countries，仅保留 ISO 两位国家/地区代码及其参考经纬度。参考经纬度仅用于缺失电台坐标时的国家范围展示，不代表发射台位置。

- 数据来源：https://github.com/mledoze/countries
- 原始数据：https://github.com/mledoze/countries/blob/master/countries.json
- 数据许可证：ODC Open Database License (ODbL) 1.0
- 许可证副本：[COUNTRIES-LICENSE.md](app/src/main/assets/COUNTRIES-LICENSE.md)

该派生数据文件按 ODbL 提供；许可证不代表电台广播内容或本项目应用代码的授权。

## AndroidX Media3

播放器使用 AndroidX Media3 ExoPlayer 与 HLS 模块 1.8.0，以及 Gradle 声明的传递依赖。

- 项目：https://github.com/androidx/media
- 许可证：Apache License 2.0，https://www.apache.org/licenses/LICENSE-2.0
- 各依赖的具体许可证与版权归属以其发布包及上游仓库为准。
