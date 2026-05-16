<template>
  <svg
    class="heart-icon"
    :class="{ filled }"
    viewBox="0 0 1024 1024"
    aria-hidden="true"
  >
    <path
      d="M512 904s-92-68-184-160C216 632 128 520 128 384c0-104 72-184 176-184 80 0 144 44 208 120 64-76 128-120 208-120 104 0 176 80 176 184 0 136-88 248-200 360-92 92-184 160-184 160z"
    />
  </svg>
</template>

<script setup>
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.heart-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 48;
  stroke-linejoin: round;
}

.heart-icon.filled {
  fill: currentColor;
}
</style>
