---
name: "websearchskill"
description: "Searches the web for up-to-date external information. Invoke when the user needs current facts, official docs, or information unavailable in the local codebase."
---

# Web Search Skill

Use this skill when the task requires reliable external information beyond the local workspace or model memory.

## When To Invoke

- The user asks for current or time-sensitive information.
- The user needs official documentation, release notes, API references, or product pages.
- The answer depends on facts that are not present in the repository.
- Local code inspection is insufficient and external verification is needed.

## Search Principles

1. Prefer official sources, vendor docs, standards bodies, and primary references.
2. Use precise queries that include product name, version, and year when relevant.
3. Cross-check important claims with more than one trustworthy source when possible.
4. Avoid low-quality SEO pages unless no better source exists.
5. Distinguish clearly between confirmed facts, likely interpretations, and unknowns.

## Working Process

1. Identify exactly what must be verified externally.
2. Search with focused keywords.
3. Open the most relevant sources and extract the needed facts.
4. Summarize the answer briefly and cite the source links.
5. Mention uncertainty if the sources conflict or are incomplete.

## Output Guidelines

- Lead with the direct answer.
- Include short source-backed evidence.
- Prefer official links over secondary summaries.
- Call out date sensitivity when relevant.
- If no trustworthy source is found, say so explicitly.

## Example Triggers

- "帮我查一下最新的 Spring Boot 版本"
- "找一下 OpenAI 官方 API 文档"
- "这个库在 2026 年还维护吗"
- "帮我确认某个报错对应的官方说明"
