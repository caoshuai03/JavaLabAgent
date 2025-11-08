<template>
  <router-view />
</template>

<script setup>
// 导入 highlight.js 的 CSS
import 'highlight.js/styles/github.css'
</script>

<style lang="scss">
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  width: 100%;
  overflow: hidden;
  font-family: 
    "Inter",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    Roboto,
    "Helvetica Neue",
    Arial,
    "PingFang SC",
    "Microsoft YaHei",
    "Hiragino Sans GB",
    "WenQuanYi Micro Hei",
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  transition: background-color 0.3s ease, color 0.3s ease;
}

#app {
  height: 100%;
  width: 100%;
}

// 浅色主题变量
:root {
  --bg-primary: #ffffff;
  --bg-secondary: #f7f7f8;
  --bg-tertiary: #f9f9f9;
  --bg-hover: #e5e5e5;
  --bg-active: #ebebeb;
  --border-color: #e5e5e5;
  --border-color-hover: #d1d1d1;
  --text-primary: #353740;
  --text-secondary: #6e6e80;
  --text-tertiary: #8e8ea0;
  --accent-color: #10a37f;
  --user-message-bg: #f7f7f8;
  --user-message-text: #353740;
  --assistant-message-bg: #f9f9f9;
  --assistant-message-text: #353740;
  --input-bg: #ffffff;
  --input-text: #353740;
  --input-border: #d1d1d1;
  --scrollbar-thumb: #d1d1d1;
  --scrollbar-thumb-hover: #b0b0b0;
}

// highlight.js 样式
:root {
  .hljs {
    display: block;
    overflow-x: auto;
    padding: 0.5em;
    background: #f6f8fa;
    color: #24292e;
  }
}
</style>