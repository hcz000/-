<template>
  <div class="placeholder">
    <el-empty :description="description">
      <el-button type="primary" @click="goBack">返回</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const description = computed(() => {
  return `${route.meta.title || '页面'}正在开发中...`
})

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
