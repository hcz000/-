<template>
  <teleport to="body">
    <transition name="context-menu">
      <div
        v-if="visible"
        ref="menuRef"
        class="context-menu"
        :style="{ left: x + 'px', top: y + 'px' }"
        @click.stop
        @contextmenu.prevent.stop
      >
        <template v-if="hasCustomItems">
          <div
            v-for="item in items"
            :key="item.key"
            class="context-menu-item"
            :class="{ danger: item.danger }"
            @click="handleAction(item)"
          >
            <span>{{ item.label }}</span>
          </div>
        </template>
        <div
          v-else
          class="context-menu-item danger"
          @click="handleDelete"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"></polyline>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            <line x1="10" y1="11" x2="10" y2="17"></line>
            <line x1="14" y1="11" x2="14" y2="17"></line>
          </svg>
          <span>删除</span>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  x: {
    type: Number,
    default: 0
  },
  y: {
    type: Number,
    default: 0
  },
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['delete', 'close', 'action'])
const menuRef = ref(null)
let ready = false
const hasCustomItems = computed(() => Array.isArray(props.items) && props.items.length > 0)

const handleDelete = () => {
  emit('delete')
  emit('close')
}

const handleAction = (item) => {
  emit('action', item)
  if (item?.key === 'delete') {
    emit('delete')
  }
  emit('close')
}

const handleClickOutside = (e) => {
  if (!props.visible || !ready) return
  if (menuRef.value && menuRef.value.contains(e.target)) return
  emit('close')
}

const handleKeydown = (e) => {
  if (e.key === 'Escape' && props.visible) {
    emit('close')
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    ready = false
    nextTick(() => {
      setTimeout(() => {
        ready = true
      }, 50)
    })
  }
})

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('contextmenu', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('contextmenu', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 140px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  border: 1px solid rgba(255, 255, 255, 0.5);
  padding: 8px 0;
  animation: context-menu-in 0.18s var(--spring) both;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
  transition: var(--transition-all);
}

.context-menu-item:hover {
  background: rgba(var(--accent-rgb), 0.08);
  color: var(--accent);
}

.context-menu-item.danger {
  color: #e74c3c;
}

.context-menu-item.danger:hover {
  background: rgba(231, 76, 60, 0.08);
  color: #e74c3c;
}

.context-menu-item .el-icon {
  font-size: 16px;
}

.context-menu-enter-active,
.context-menu-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}

.context-menu-enter-from,
.context-menu-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

@keyframes context-menu-in {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
