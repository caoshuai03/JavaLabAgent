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

## 项目设置

### 1. 克隆仓库

```bash
git clone xxx
cd javalab-agent-front
```

### 2. 安装依赖

```bash
npm install
```

## 开发模式

### 镜像启动nginx
- windows下打开docker desktop
- 然后执行docker-compose up -d
- 这时候就已经可以访问了，默认端口是80


通常可以通过 `http://localhost` 访问应用。


## 生产部署

### 1. 构建生产版本

```bash
npm run build
```

### 2. 部署到Nginx

构建完成后，将 `dist` 目录中的文件部署到Nginx服务器。

详细部署说明请参考 [DEPLOYMENT.md](DEPLOYMENT.md) 文件。


