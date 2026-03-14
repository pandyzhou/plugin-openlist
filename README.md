# plugin-openlist

Halo 附件存储插件，将 [OpenList](https://github.com/OpenListTeam/OpenList)（AList 分支）作为附件存储后端。

## 功能

- 通过 OpenList API 上传、删除附件
- 自动按年月分目录存储（`/your-path/2026/03/image.png`）
- 支持创建多个存储策略，对应不同的 OpenList 实例
- 自动管理登录 Token（缓存 48 小时）

## 安装

从 [Releases](https://github.com/pandyzhou/plugin-openlist/releases) 下载最新的 `plugin-openlist-x.x.x.jar`，在 Halo Console → 插件管理 → 安装插件中上传即可。

要求 Halo >= 2.22.0。

## 配置

安装启用后，进入 Console → 附件 → 存储策略 → 新建存储策略，选择「OpenList 存储」：

| 配置项 | 说明 | 示例 |
|--------|------|------|
| OpenList 服务地址 | 完整访问地址 | `https://alist.example.com` |
| 用户名 | 登录用户名 | `admin` |
| 密码 | 登录密码 | |
| 上传根路径 | 文件存储目录 | `/zhouyi/blog` |
| Token 接口路径 | 登录接口，通常无需修改 | `/api/auth/login` |

可以创建多个存储策略来对接不同的 OpenList 实例。

## 构建

```bash
./gradlew jar
```

产物在 `build/libs/` 下。

## 发版

推送 tag 会自动触发 GitHub Actions 构建并发布 Release：

```bash
git tag v1.0.3
git push origin v1.0.3
```

## License

MIT
