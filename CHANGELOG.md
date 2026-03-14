# Changelog

## [1.1.0]

- 新增文件同步功能：在插件详情页的"文件同步"Tab 中，可一键将 OpenList 上的文件导入 Halo 附件库
- 支持递归扫描 OpenList 目录，自动跳过已存在的附件记录
- 新增 OpenList 目录列表 API（listFiles）
- 新增 CustomEndpoint 同步接口

## [1.0.3]

- 修复文件流被消费两次导致上传空文件的问题
- 修复同一 OpenList 站点不同账号 Token 缓存互相覆盖的问题
- 新增 Token 过期或 401 时自动刷新重试
- 修复中文文件名生成 permalink 时 URL 编码异常
- 上传文件名添加 UUID 前缀，避免同名文件覆盖

## [1.0.2]

- 将 API 依赖从 SNAPSHOT 改为正式版 2.22.11，修复 CI 构建失败
- 兼容性要求降为 Halo >= 2.22.0

## [1.0.1]

- 添加 plugin-components.idx 修复插件无法被 Halo 发现的问题
- 上传根路径改为用户自定义，不再强制默认值

## [1.0.0]

- 初始版本
- 实现 AttachmentHandler 扩展点，支持通过 OpenList API 上传、删除附件
- 支持创建多个存储策略对应不同 OpenList 实例
- 自动按年月分目录存储
- Token 自动缓存管理
