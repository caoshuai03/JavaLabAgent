# 前端 Dockerfile：多阶段构建（Vite build -> Nginx 静态站点）

# ---------- 构建阶段 ----------
FROM node:22-alpine AS build

WORKDIR /app

# 先拷贝依赖描述文件
COPY package.json package-lock.json* ./

# 安装依赖（若你没有 lock 文件，npm 仍会正常安装）
RUN npm install

# 拷贝源码并构建
COPY . ./
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

# 使用专门的 docker nginx 配置（/api 代理到 compose 内的 backend 服务）
COPY nginx.docker.conf /etc/nginx/nginx.conf

# 拷贝构建产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
