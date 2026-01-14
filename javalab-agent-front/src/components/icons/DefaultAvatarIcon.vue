<template>
  <!-- 默认“小人头像”图标：用于用户未设置头像时的占位显示 -->
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- 圆形背景 -->
    <circle cx="24" cy="24" r="24" fill="#F1E6FF" />

    <!-- 头像（头部） -->
    <circle
      cx="24"
      cy="19"
      r="7"
      stroke="#4D0099"
      stroke-width="3"
      stroke-linecap="round"
      stroke-linejoin="round"
    />

    <!-- 头像（身体/肩膀） -->
    <path
      d="M12 38c2.5-7 8-10 12-10s9.5 3 12 10"
      stroke="#4D0099"
      stroke-width="3"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<script setup>
// 默认头像图标尺寸，单位像素
defineProps({
  size: {
    type: [Number, String],
    default: 32
  }
})
</script>
