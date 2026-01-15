# JavaLab Agent

基于 Spring AI 的智能 RAG（检索增强生成）对话系统，集成本地大模型，支持知识库管理和智能问答。

## 📋 项目简介

主要功能包括：

- 🤖 **智能对话**：基于 RAG 技术的上下文问答，支持流式响应
- 📚 **知识库管理**：文档上传、向量化存储、智能检索
- 🛡️ **敏感词过滤**：内置敏感词检测机制
- 📊 **数据分析**：词频统计、日志记录
- 👤 **用户管理**：JWT 认证、用户权限管理

## 🚀 快速开始

### 1. 环境配置

在项目根目录创建 `.env` 文件，配置以下环境变量：

```env
# =================【必需配置】=================
# Ollama 服务地址
OLlama_BASE_URL=http://xxx:11434

# =================【存储配置（二选一）】=================
# 存储类型：minio（推荐默认，本地私有化）或 alioss（阿里云）
STORAGE_TYPE=minio

# ---------- 方式一：MinIO 配置（推荐，无需云服务） ----------
# MinIO 会随 docker compose 自动启动，以下为默认值，可不修改
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_BUCKET=javalab

# ---------- 方式二：阿里云 OSS 配置（需要阿里云账号） ----------
# 如果使用阿里云 OSS，请将 STORAGE_TYPE 改为 alioss，并填写以下配置
# OSS_ACCESS_KEY_ID=your_access_key_id
# OSS_ACCESS_KEY_SECRET=your_access_key_secret
# OSS_BUCKET_NAME=your_bucket_name
# OSS_ENDPOINT=your_oss_endpoint

# =================【可选配置】=================
# PostgreSQL 数据库配置 (有默认值，可不设置)
POSTGRES_USER=postgres
POSTGRES_PASSWORD=admin
POSTGRES_DB=postgres
```

> **💡 说明**：
>
> - 默认使用 **MinIO** 作为文件存储，无需阿里云账号，Bucket 会自动创建
> - MinIO 控制台地址：`http://localhost:9001`（用户名/密码见上方配置）
> - 如需切换回阿里云 OSS，修改 `STORAGE_TYPE=alioss` 并填写 OSS 密钥


### 2. Docker 一键启动

确保已安装 Docker 和 Docker Compose，然后在根目录执行：

```bash
docker compose up -d --build
```

```bash
docker compose -f docker-compose.prod.yml up -d
```

启动完成后：

- **前端地址**: [http://localhost](http://localhost)
- **后端接口**: [http://localhost:8989](http://localhost:8989)
- **MinIO 控制台**: [http://localhost:9001](http://localhost:9001)（用户名：minioadmin，密码：minioadmin123）
- **数据库**: localhost:5432 (PostgreSQL + pgvector)

重启某个容器
docker compose restart backend
删除所有容器和数据卷
docker compose down -v
---

## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
| :--- | :--- | :--- |
| Spring Boot | 3.4.2 | 核心框架 |
| JDK | 17 | 运行环境 |
| Spring AI | 1.0.0-M5 | AI 框架 |
| PostgreSQL | 16.6 | 数据库 |
| pgvector | 0.7.2 | 向量存储 |
| Vue.js | 3.x | 前端框架 |

## ✨ 功能特性

- 🤖 **智能 RAG**：基于 pgvector 的检索增强生成
- 💬 **实时对话**：支持 SSE 流式响应
- 📚 **知识库管理**：支持文档上传、切片、向量化
- 🔐 **权限控制**：JWT 认证机制
- 📱 **响应式 UI**：适配多端访问
