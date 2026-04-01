# Skills 功能使用指南

## 功能概述

Skills 功能为 JavaLab Agent 提供了可扩展的专业技能支持，允许通过简单的 Markdown 文件定义 AI 助手的专业知识领域。

## 核心特性

### 1. 自动触发注入
- 用户发送消息时，系统自动根据 **trigger_keywords** 匹配相关 Skills
- 匹配的 Skills 内容会自动注入到 ReAct Agent 的系统提示词中
- 单次对话最多注入 3 个 Skills（按匹配度排序）

### 2. 只读展示
- 前端提供 Skills 管理页面，展示所有可用 Skills
- 支持搜索、查看详情、刷新列表
- 查看每个 Skill 的元数据、触发关键词和完整内容

### 3. 渐进式加载
- 应用启动时仅加载 Skills 元数据
- 实际匹配时才读取完整 Markdown 内容
- 优化 token 使用和性能

## 如何创建 Skill

### 目录结构

```
javalab-agent-back/
  src/main/resources/
    skills/
      java-debug-helper/          # Skill 文件夹
        SKILL.md                   # 主要内容（必需）
        scripts/                   # 可选：脚本资源
        references/                # 可选：参考文档
        assets/                    # 可选：其他资源
      code-review-expert/
        SKILL.md
```

### SKILL.md 格式

```markdown
---
name: skill-name                  # 必填：Skill 唯一标识
description: Skill 简介           # 必填：简短描述
trigger_keywords:                 # 必填：触发关键词列表
  - 关键词1
  - 关键词2
  - keyword3
version: "1.0"                    # 可选：版本号
author: 作者名                    # 可选：作者
license: MIT                      # 可选：许可证
---

# Skill 内容

这里是 Skill 的详细指导内容，会在匹配时注入到 Agent 提示词中。

## 示例章节

可以使用 Markdown 格式编写专业指导...
```

### 示例：Java 调试助手

参考 `src/main/resources/skills/java-debug-helper/SKILL.md`

## 使用方式

### 1. 管理 Skills

访问前端页面：**Skills 管理**

- 查看所有可用 Skills
- 搜索特定 Skills
- 点击卡片查看详情
- 点击"刷新"按钮重新扫描文件系统

### 2. 在对话中使用

当用户发送包含触发关键词的消息时，Skills 自动激活：

**示例 1**：用户问 "我的代码报 NullPointerException 错误，怎么调试？"
- 自动匹配：`java-debug-helper` Skill（关键词：调试、错误、NullPointerException）
- Agent 会基于该 Skill 的专业指导回答问题

**示例 2**：用户问 "帮我审查这段代码"
- 自动匹配：`code-review-expert` Skill（关键词：代码审查、code review）
- Agent 按照代码审查流程提供专业建议

### 3. 查看激活的 Skills

在对话界面中，当 Skills 被激活时：
- 系统会通过 SSE 事件通知前端
- 显示当前激活的 Skills 标签（开发中）

## API 接口

### 获取所有 Skills
```
GET /api/v1/skills
```

### 获取 Skill 详情
```
GET /api/v1/skills/{name}
```

### 刷新 Skills 列表
```
POST /api/v1/skills/refresh
```

## 配置

在 `application.yml` 中配置 Skills 目录：

```yaml
skills:
  directory: src/main/resources/skills
```

## 最佳实践

### 1. 命名规范
- 使用小写字母和连字符：`java-debug-helper`
- 描述性命名，表明 Skill 的用途

### 2. 触发关键词
- 包含中英文关键词以提高匹配率
- 添加常见同义词和相关术语
- 3-10 个关键词为佳

### 3. 内容编写
- 结构清晰，分段明确
- 提供具体示例和代码
- 聚焦单一专业领域
- 长度控制在 500-1000 行内

### 4. 版本管理
- 使用语义化版本号
- 重大更新时增加版本号
- 保持向后兼容

## 注意事项

1. **文件编码**：使用 UTF-8 编码
2. **YAML 格式**：frontmatter 必须以 `---` 包围
3. **文件名**：主文件必须命名为 `SKILL.md`（大写）
4. **缓存机制**：修改 Skill 后需点击"刷新"按钮
5. **匹配优先级**：匹配关键词越多，优先级越高

## 扩展建议

未来可以添加的功能：
- Skills 的 CRUD 管理（通过 UI 创建和编辑）
- 从远程仓库导入社区 Skills
- 基于向量的语义匹配
- Skills 版本管理和更新检测
- Skills 使用统计和推荐

## 故障排查

**问题：Skills 列表为空**
- 检查 `application.yml` 中的目录配置是否正确
- 确认 Skills 目录下有 Skill 文件夹
- 查看后端日志是否有解析错误

**问题：Skills 未自动激活**
- 检查 trigger_keywords 是否包含用户消息中的关键词
- 确认使用的是 ReAct Agent 模式（/api/v1/ai/react-agent）
- 查看后端日志中的匹配信息

**问题：Skill 内容显示异常**
- 检查 YAML frontmatter 格式是否正确
- 确认 Markdown 内容没有语法错误
- 使用"刷新"按钮重新加载
