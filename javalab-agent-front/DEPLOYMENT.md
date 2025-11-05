# 前端项目部署指南

## 构建项目

在部署之前，需要先构建项目：

```bash
# 进入前端项目目录
cd javalab-agent-front

# 安装依赖
npm install

# 构建生产版本
npm run build
```

构建完成后，会在 `dist` 目录下生成生产环境的静态文件。

## Nginx 部署配置

### 1. 复制文件到 Nginx 目录

将 `dist` 目录下的所有文件复制到 Nginx 的 HTML 目录中（通常是 `/usr/share/nginx/html`）：

```bash
# 构建项目
npm run build

# 复制文件到 Nginx 目录
cp -r dist/* /usr/share/nginx/html/
```

### 2. 配置 Nginx

使用项目中的 `nginx.conf` 配置文件，或将其内容合并到现有的 Nginx 配置中。

关键配置项：

```nginx
server {
    listen       80;
    server_name  localhost;

    location / {
        root   /usr/share/nginx/html;
        index  index.html index.htm;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://your-backend-server:8989;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 支持 SSE (Server-Sent Events)
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_buffering off;
        proxy_cache off;
    }
}
```

注意：将 `your-backend-server` 替换为实际的后端服务器地址。

### 3. 重启 Nginx

```bash
# 测试配置文件
nginx -t

# 重启 Nginx
nginx -s reload
# 或者
systemctl restart nginx
```

## 环境变量配置

在生产环境中，确保后端服务可以访问以下环境变量：

- `DASHSCOPE_API_KEY`: 通义千问API密钥
- `OSS_ACCESS_KEY_ID`: 阿里云OSS访问密钥ID
- `OSS_ACCESS_KEY_SECRET`: 阿里云OSS访问密钥
- `SEARCHAPI_API_KEY`: 搜索API密钥（如果使用）

## 部署目录结构

```
/usr/share/nginx/html/
├── assets/
│   ├── index-*.js
│   ├── index-*.css
│   └── ...
├── index.html
└── ...
```

## 验证部署

1. 访问 `http://your-server-ip` 应该能看到登录页面
2. 尝试登录系统
3. 登录成功后应该能访问聊天功能
4. 确保API请求能够正确代理到后端服务

## 故障排除

### 1. 页面刷新404问题

确保Nginx配置中包含：
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### 2. API请求失败

检查：
1. Nginx配置中的 `proxy_pass` 是否正确指向后端服务
2. 后端服务是否正常运行
3. 防火墙是否允许相应端口通信

### 3. SSE连接问题

确保Nginx配置支持SSE：
```nginx
location /api {
    # ... 其他配置 ...
    
    # 支持 SSE (Server-Sent Events)
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding off;
    proxy_buffering off;
    proxy_cache off;
}
```