<template>
  <el-dialog
    v-model="visible"
    title="举报"
    width="500px"
    class="report-dialog"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="90px"
    >
      <el-form-item label="举报对象">
        <el-tag :type="targetTypeLabelColor" effect="light" round>
          {{ targetTypeLabel }}
        </el-tag>
      </el-form-item>
      
      <el-form-item label="举报原因" prop="reasonType">
        <el-select v-model="formData.reasonType" placeholder="请选择举报原因" style="width: 100%;" size="large">
          <el-option label="垃圾广告" value="SPAM" />
          <el-option label="辱骂攻击" value="ABUSE" />
          <el-option label="违法违规" value="ILLEGAL" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="详细描述" prop="reasonDetail">
        <el-input
          v-model="formData.reasonDetail"
          type="textarea"
          :rows="4"
          placeholder="请详细描述举报原因（选填）"
          size="large"
        />
      </el-form-item>
    </el-form>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          提交举报
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  targetType: {
    type: String,
    required: true,
    validator: (value) => ['POST', 'COMMENT', 'USER'].includes(value)
  },
  targetId: {
    type: [Number, String],
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const formRef = ref(null)
const submitting = ref(false)

const formData = reactive({
  reasonType: '',
  reasonDetail: ''
})

const formRules = {
  reasonType: [
    { required: true, message: '请选择举报原因', trigger: 'change' }
  ]
}

const targetTypeLabel = computed(() => {
  const labels = {
    'POST': '帖子',
    'COMMENT': '评论',
    'USER': '用户'
  }
  return labels[props.targetType] || props.targetType
})

const targetTypeLabelColor = computed(() => {
  const colors = {
    'POST': 'warning',
    'COMMENT': 'primary',
    'USER': 'danger'
  }
  return colors[props.targetType] || 'info'
})

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        await http.post('/report/submit', null, {
          params: {
            targetType: props.targetType,
            targetId: props.targetId,
            reasonType: formData.reasonType,
            reasonDetail: formData.reasonDetail || ''
          }
        })
        ElMessage.success('举报提交成功，感谢你的反馈！')
        visible.value = false
        emit('submitted')
      } catch (error) {
        ElMessage.error(error.message || '举报提交失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleClose = () => {
  formRef.value?.resetFields()
  formData.reasonType = ''
  formData.reasonDetail = ''
}

// 监听关闭，重置表单
watch(() => visible.value, (newVal) => {
  if (!newVal) {
    handleClose()
  }
})
</script>

<style scoped>
.report-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.report-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  margin-right: 0;
}

.report-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.report-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.report-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--ink);
}

.report-dialog :deep(.el-input__wrapper),
.report-dialog :deep(.el-textarea__inner) {
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.report-dialog :deep(.el-input__wrapper.is-focus),
.report-dialog :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.report-dialog :deep(.el-select__wrapper) {
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.report-dialog :deep(.el-select__wrapper.is-focused) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.dialog-footer {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--border);
  text-align: right;
}

.dialog-footer .el-button {
  border-radius: var(--radius-md);
  font-weight: 600;
}

.dialog-footer .el-button--primary {
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
}

.dialog-footer .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}
</style>
