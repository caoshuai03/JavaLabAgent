import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

// HTML 转义函数
function escapeHtml(str) {
  if (typeof str !== 'string') return str
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  }
  return str.replace(/[&<>"']/g, (m) => map[m])
}

// 配置 markdown-it
const md = new MarkdownIt({
  html: true, // 允许 HTML 标签
  linkify: true, // 自动将 URL 转换为链接
  typographer: true, // 启用一些语言中性的替换 + 引号美化
  breaks: false, // 将换行符转换为 <br>
})

// 自定义代码块渲染规则，避免 markdown-it 自动包裹 <pre>
md.renderer.rules.fence = function (tokens, idx, options, env, self) {
  const token = tokens[idx]
  const info = token.info ? md.utils.unescapeAll(token.info).trim() : ''
  const lang = info.split(/\s+/g)[0]
  const content = token.content

  let highlighted = ''
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(content, { language: lang, ignoreIllegals: true }).value
    } catch (__) {}
  }

  if (!highlighted) {
    // 使用自定义的 escapeHtml 函数
    highlighted = escapeHtml(content)
  }

  const langLabel = lang || 'text'
  // 添加 highlight.js 的语言类名
  const codeClass = lang ? `language-${lang}` : ''
  
  return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-block-lang">${langLabel}</span></div><pre class="hljs"><code class="${codeClass}">${highlighted}</code></pre></div>\n`
}

// 渲染 Markdown 为 HTML
export const renderMarkdown = (content) => {
  if (!content) return ''
  return md.render(content)
}

