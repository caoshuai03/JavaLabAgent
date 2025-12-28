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

在项目根目录创建 `.env` 文件，配置以下核心环境变量：

```env
# Ollama 服务地址 (如使用本地 Ollama)
OLlama_BASE_URL=http://host.docker.internal:11434

# 阿里云 OSS 配置 (可选，用于知识库文档存储)
OSS_ACCESS_KEY_ID=your_access_key
OSS_ACCESS_KEY_SECRET=your_secret_key
```

还有变量配置待更新...

### 2. Docker 一键启动

确保已安装 Docker 和 Docker Compose，然后在根目录执行：

```bash
docker compose up -d --build
```

启动完成后：

- **前端地址**: [http://localhost](http://localhost)
- **后端接口**: [http://localhost:8989](http://localhost:8989)
- **数据库**: localhost:5433 (PostgreSQL + pgvector)

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
