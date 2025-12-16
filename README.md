# JavaLab Agent

基于 Spring AI 的智能 RAG（检索增强生成）对话系统，集成通义千问大模型，支持知识库管理和智能问答。

## 📋 项目简介

主要功能包括：

- 🤖 **智能对话**：基于 RAG 技术的上下文问答，支持流式响应
- 📚 **知识库管理**：文档上传、向量化存储、智能检索
- 🛡️ **敏感词过滤**：内置敏感词检测机制
- 📊 **数据分析**：词频统计、日志记录
- 👤 **用户管理**：JWT 认证、用户权限管理

## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.2 | 核心框架 |
| JDK | 17 | Java 运行环境 |
| Spring AI | 1.0.0-M5 | AI 集成框架 |
| Spring AI Alibaba | 1.0.0-M5.1 | 通义千问适配器 |
| PostgreSQL | 16.6 | 关系型数据库 |
| pgvector | 0.7.2 | 向量存储扩展 |
| Redis | - | 缓存服务 |
| Maven | 3.9.9 | 构建工具 |

## 🚀 快速开始

### 1. 环境准备

#### 1.1 安装 PostgreSQL 并配置向量扩展

1. **安装 PostgreSQL 16.6**
   - 下载地址：https://www.postgresql.org/download/
   - 安装教程参考：[PostgreSQL 及 vector 扩展安装指南](https://blog.csdn.net/typeracer/article/details/140711057)

2. **安装 pgvector 扩展**
   ```sql
   -- 创建数据库后执行
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS hstore;
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
   ```

3. **初始化数据库**
   ```bash
   # 创建数据库
   createdb 你的数据库名
   
   # 执行初始化脚本
   psql -d 你的数据库名 -f src/main/resources/sql/init.sql
   ```


#### 1.2 准备阿里云 OSS

1. 登录 [阿里云控制台](https://oss.console.aliyun.com/)
2. 创建 OSS Bucket
3. 获取以下信息：
   - Endpoint（如：`oss-cn-chengdu.aliyuncs.com`）
   - AccessKey ID
   - AccessKey Secret

#### 1.3 申请通义千问 API Key

1. 访问 [通义千问控制台](https://dashscope.console.aliyun.com/)
2. 创建模型应用
3. 获取 API Key

### 2. 配置项目

#### 2.1 配置数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
spring:
   datasource:
      username: postgres        # 你的数据库用户名
      password: admin           # 你的数据库密码
      url: jdbc:postgresql://localhost:5433/postgres  # 你的数据库地址
```

#### 2.2 配置环境变量

创建环境变量或修改 `src/main/resources/application-dev.yml`：

**方式一：设置环境变量（推荐）**
```bash

# Linux/Mac
export OSS_ACCESS_KEY_ID="your-access-key-id"
export OSS_ACCESS_KEY_SECRET="your-access-key-secret"
export DASHSCOPE_API_KEY="your-dashscope-api-key"
```


### 3. 运行项目

#### 3.1 使用 Maven 运行

```bash
# 进入项目目录
cd javalab-agent-back

# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```




## 🎨 前端部署

### 1. 环境要求

- **Node.js**: ^20.19.0 或 >=22.12.0
- **Docker Desktop**: 用于容器化部署

### 2. 安装依赖

```bash
# 进入前端项目目录
cd javalab-agent-front

# 安装依赖
npm install
```

### 3. 启动服务

#### 3.1 Windows 环境

1. 打开 Docker Desktop
2. 进入项目目录：
   ```bash
   cd javalab-agent-front
   ```
3. 启动 nginx 容器：
   ```bash
docker-compose up -d
      ```

启动成功后，前端服务运行在 `http://localhost`

### 4. 开发流程

#### 4.1 代码更新

代码修改后需要重新构建：

```bash


```

#### 4.2 应用更新

由于使用了 Docker volume 挂载，构建完成后新文件会自动生效。如遇缓存问题：

- 清除浏览器缓存（Ctrl+F5 强制刷新）
- 或重启容器：`docker-compose restart nginx`

#### 4.3 停止服务

```bash
docker-compose down
```

## ✨ 功能特性

### 前端功能

- 🔐 **用户认证**：登录和权限管理
- 💬 **实时聊天**：流畅的对话界面
- 📡 **SSE 流式传输**：实时消息推送
- 📱 **响应式设计**：适配多种设备
- 🛡️ **路由保护**：认证用户访问控制
- 📚 **知识库管理**：文档上传和管理界面

