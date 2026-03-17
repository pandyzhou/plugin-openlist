# plugin-openlist

Halo 附件存储插件，将 [OpenList](https://github.com/OpenListTeam/OpenList)（AList 分支）作为附件存储后端。

## 功能

- 通过 OpenList API 上传、删除附件
- 自动按年月分目录存储（`/your-path/2026/03/image.png`）
- 支持创建多个存储策略，对应不同的 OpenList 实例
- 自动管理登录 Token（缓存 48 小时，401 自动刷新）
- 文件同步：一键将 OpenList 上已有的文件导入 Halo 附件库

## 安装

从 [Releases](https://github.com/pandyzhou/plugin-openlist/releases) 下载最新的 `plugin-openlist-x.x.x.jar`，在 Halo Console → 插件管理 → 安装插件中上传即可。

要求 Halo >= 2.22.0。

## 配置

安装启用后，进入 Console → 附件 → 存储策略 → 新建存储策略，选择「OpenList 存储」：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| OpenList 服务地址 | 完整访问地址，必填 | 无 |
| 用户名 | 登录用户名，必填 | 无 |
| 密码 | 登录密码，必填 | 无 |
| 上传根路径 | 文件存储的目录路径，可自定义 | 空 |
| Token 接口路径 | 登录接口路径，可自定义 | `/api/auth/login` |

所有配置项均可在每个存储策略中独立设置。

可以创建多个存储策略来对接不同的 OpenList 实例。

## 文件同步

插件安装后，左侧菜单「工具」分组下会出现「OpenList 同步」入口。

使用方式：
1. 进入 Console → 工具 → OpenList 同步
2. 选择要同步的存储策略
3. 点击「同步文件」

插件会递归扫描 OpenList 对应目录下的所有文件，将未在 Halo 附件库中注册的文件自动导入，已存在的文件会跳过。

## License

MIT
