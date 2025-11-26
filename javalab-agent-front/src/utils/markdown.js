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
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const highlighted = hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        // 添加语言标签，类似 ChatGPT 的风格
        return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-block-lang">${lang}</span></div><pre class="hljs"><code class="language-${lang}">${highlighted}</code></pre></div>`
      } catch (__) {}
    }
    
    // 如果没有语言或语言不支持，也添加包装器
    // 使用自定义的 escapeHtml 函数
    const escaped = escapeHtml(str)
    const langLabel = lang || 'text'
    return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-block-lang">${langLabel}</span></div><pre class="hljs"><code>${escaped}</code></pre></div>`
  }
})

// 渲染 Markdown 为 HTML
export const renderMarkdown = (content) => {
  if (!content) return ''
  return md.render(content)
}

