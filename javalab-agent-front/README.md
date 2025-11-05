# JavaLabAgent 前端

这是 JavaLabAgent 应用程序的前端项目，基于 Vue.js 3 和 Vite 构建，提供与 RAG AI 系统交互的用户界面。

## 功能特性

- 用户登录和认证
- 实时聊天界面
- SSE流式消息传输
- 响应式设计
- 路由保护（仅认证用户可访问聊天功能）

## 环境要求
- Node.js: ^20.19.0 或 >=22.12.0
- Docker

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 启动服务

在 Windows 下：

1. 打开 Docker Desktop
2. 进入项目目录：`cd javalab-agent-front`
3. 启动 nginx 容器：`docker-compose up -d`


启动后访问：`http://localhost`

### 3. 代码更新

代码更新后执行构建：

```bash
npm run build
```

构建完成后，由于 Docker volume 挂载，新文件会自动生效。如遇缓存问题，可：
- 清除浏览器缓存（Ctrl+F5 强制刷新）
- 或重启容器：`docker-compose restart nginx`



