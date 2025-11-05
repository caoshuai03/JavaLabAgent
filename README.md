# JavaLab Agent

基于 Spring AI 的智能 RAG（检索增强生成）对话系统，集成通义千问大模型，支持知识库管理和智能问答。

## 📋 项目简介

主要功能包括：

- 🤖 **智能对话**：基于 RAG 技术的上下文问答，支持流式响应
- 📚 **知识库管理**：文档上传、向量化存储、智能检索
- 🔍 **网络搜索增强**：集成 Tavily 搜索，扩展知识范围
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
export SEARCHAPI_API_KEY="your-searchapi-key"
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



### 4. 验证运行

启动成功后，服务默认运行在 `http://localhost:8989`

