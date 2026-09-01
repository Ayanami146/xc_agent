<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules, UploadFile, UploadFiles, UploadRawFile, UploadUserFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import type { TicketDraft } from '../types/ticket'

const props = defineProps<{ modelValue: boolean; submitting?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [draft: TicketDraft, files: File[]] }>()
const formRef = ref<FormInstance>()
const files = ref<UploadUserFile[]>([])
const form = reactive<TicketDraft>({ title: '', category: '', deviceBrand: '', deviceModel: '', description: '', contact: '' })
const rules: FormRules<TicketDraft> = {
  title: [{ required: true, message: '请输入问题标题', trigger: 'blur' }, { min: 4, max: 60, message: '标题长度为 4～60 个字符', trigger: 'blur' }],
  category: [{ required: true, message: '请选择问题分类', trigger: 'change' }],
  deviceBrand: [{ required: true, message: '请输入设备品牌', trigger: 'blur' }],
  deviceModel: [{ required: true, message: '请输入设备型号', trigger: 'blur' }],
  description: [{ required: true, message: '请描述问题现象', trigger: 'blur' }, { min: 10, max: 2000, message: '问题描述为 10～2000 个字符', trigger: 'blur' }],
  contact: [{ required: true, message: '请输入联系方式', trigger: 'blur' }, { pattern: /^(1\d{10}|[^\s@]+@[^\s@]+\.[^\s@]+)$/, message: '请输入手机号或邮箱', trigger: 'blur' }],
}

function beforeUpload(file: UploadRawFile) {
  if ((file.size ?? 0) > 50 * 1024 * 1024) { ElMessage.error('单个附件不能超过 50 MiB'); return false }
  return true
}
function handleFiles(file: UploadFile, list: UploadFiles) {
  if ((file.size ?? 0) > 50 * 1024 * 1024) {
    ElMessage.error('单个附件不能超过 50 MiB')
    files.value = list.filter((item) => item.uid !== file.uid)
    return
  }
  files.value = list
}
async function submit() {
  if (await formRef.value?.validate().catch(() => false)) {
    emit('submit', { ...form }, files.value.flatMap((item) => item.raw ? [item.raw] : []))
  }
}
function reset() { formRef.value?.resetFields(); files.value = [] }
watch(() => props.modelValue, (open) => { if (!open) reset() })
</script>

<template>
  <el-drawer :model-value="modelValue" title="提交留言工单" size="min(520px, 94vw)" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <div class="drawer-intro"><strong>让专业客服继续协助</strong><p>请尽量填写完整的设备信息和问题现象，便于快速定位。</p></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="问题标题" prop="title"><el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：统信 UOS 打印机驱动安装失败" /></el-form-item>
      <div class="form-grid">
        <el-form-item label="问题分类" prop="category"><el-select v-model="form.category" placeholder="请选择"><el-option v-for="item in ['驱动问题','网络问题','软件兼容','系统故障','保修咨询','其他']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="设备品牌" prop="deviceBrand"><el-input v-model="form.deviceBrand" placeholder="例如：长城" /></el-form-item>
      </div>
      <el-form-item label="设备型号" prop="deviceModel"><el-input v-model="form.deviceModel" placeholder="请输入设备完整型号" /></el-form-item>
      <el-form-item label="问题描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="请说明系统版本、操作步骤、错误提示和已尝试的方法" /></el-form-item>
      <el-form-item label="联系方式" prop="contact"><el-input v-model="form.contact" placeholder="手机号或邮箱" /></el-form-item>
      <el-form-item label="附件（可选）">
        <el-upload v-model:file-list="files" action="#" :auto-upload="false" multiple :limit="5" :before-upload="beforeUpload" :on-change="handleFiles" :on-remove="handleFiles" accept=".png,.jpg,.jpeg,.pdf,.txt,.log,.docx">
          <el-button>选择附件</el-button><template #tip><div class="el-upload__tip">支持图片、PDF、日志和 DOCX，最多 5 个，单个文件不超过 50 MiB。</div></template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer><div class="drawer-footer"><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">提交工单</el-button></div></template>
  </el-drawer>
</template>

<style scoped>
.drawer-intro { margin-bottom: 22px; padding: 16px; border-radius: 14px; background: linear-gradient(135deg, #eef6ff, #f4fbff); color: #275785; }.drawer-intro p { margin: 6px 0 0; color: #71859d; font-size: 13px; }.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.form-grid :deep(.el-select) { width: 100%; }.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }@media (max-width: 520px) { .form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
