<template>
  <!-- 三个点菜单图标组件 -->
  <svg
    xmlns="http://www.w3.org/2000/svg"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <circle cx="12" cy="12" r="1" />
    <circle cx="19" cy="12" r="1" />
    <circle cx="5" cy="12" r="1" />
  </svg>
</template>

<script setup>
defineProps({
  size: {
    type: Number,
    default: 16,
  },
  strokeWidth: {
    type: Number,
    default: 2,
  },
})
</script>
