<template>
  <div class="agreement-edit">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑协议' : '新建协议' }}</h1>
        <p class="page-desc">填写协议名称与类型，协议内容支持手动输入或上传文件</p>
      </div>
      <button class="btn btn-outline" @click="backToList">
        <i class="fas fa-arrow-left"></i> 返回列表
      </button>
    </div>

    <div class="card form-card">
      <div class="form-section">基础信息</div>
      <div class="base-grid">
        <div class="form-field">
          <label><span class="required">*</span>协议名称</label>
          <el-input v-model="form.name" maxlength="120" placeholder="请输入协议名称，如：快马日结用户服务协议" />
        </div>
        <div class="form-field">
          <label><span class="required">*</span>协议类型</label>
          <el-select v-model="form.type" placeholder="请选择协议类型">
            <el-option v-for="type in agreementTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </div>
      </div>

      <div class="form-section">协议内容</div>
      <div class="content-tabs">
        <button :class="['content-tab', { active: form.contentMode === 'INPUT' }]" @click="switchMode('INPUT')">
          <i class="fas fa-keyboard"></i> 手动输入
        </button>
        <button :class="['content-tab', { active: form.contentMode === 'FILE' }]" @click="switchMode('FILE')">
          <i class="fas fa-cloud-upload-alt"></i> 上传文件
        </button>
      </div>

      <div v-if="form.contentMode === 'INPUT'">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="12"
          placeholder="请输入协议正文内容，支持换行分段"
        />
      </div>

      <div v-else>
        <label
          class="upload-area"
          :class="{ uploading }"
          @click="openFilePicker"
          @dragover.prevent
          @drop.prevent="handleFileDrop"
        >
          <span class="upload-icon"><i class="fas fa-cloud-upload-alt"></i></span>
          <span class="upload-main">{{ uploading ? '文件上传中...' : '点击选择文件，或将文件拖到此处' }}</span>
          <span class="upload-hint">支持 PDF / DOC / DOCX / TXT 格式，单文件 ≤ 5MB</span>
          <span class="upload-button">选择文件</span>
        </label>
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.doc,.docx,.txt"
          class="hidden-input"
          @change="onFileChange"
        />

        <div v-if="form.fileName" class="file-item">
          <div class="file-icon"><i class="fas fa-file-lines"></i></div>
          <div class="file-info">
            <div class="file-name">{{ form.fileName }}</div>
            <div class="file-size">文件大小：{{ formatFileSize(form.fileSize) }}</div>
          </div>
          <button class="remove-file" @click="removeFile"><i class="fas fa-trash-alt"></i></button>
        </div>
      </div>

      <div class="footer-bar">
        <button class="btn btn-outline" @click="backToList">取消</button>
        <button class="btn btn-primary" :disabled="saving || uploading" @click="save">
          <i class="fas fa-check"></i> {{ isEdit ? '保存修改' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAgreement, getAgreement, updateAgreement, uploadAgreementFile } from '@/api/content'

const route = useRoute()
const router = useRouter()
const agreementTypes = [
  '用户服务协议',
  '隐私协议',
  '知识产权协议',
  '实名认证&人脸验证服务协议',
  '信息发布协议'
]
const isEdit = computed(() => Boolean(route.params.id))
const form = ref({
  name: '',
  type: '',
  contentMode: 'INPUT',
  content: '',
  fileObjectKey: '',
  fileName: '',
  fileSize: 0,
  enabled: true
})
const fileInput = ref()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)

const loadAgreement = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await getAgreement(route.params.id)
    form.value = {
      name: res.data?.name || '',
      type: res.data?.type || '',
      contentMode: res.data?.contentMode || 'INPUT',
      content: res.data?.content || '',
      fileObjectKey: res.data?.fileObjectKey || '',
      fileName: res.data?.fileName || '',
      fileSize: Number(res.data?.fileSize || 0),
      enabled: res.data?.enabled !== false
    }
  } finally {
    loading.value = false
  }
}

const switchMode = (mode) => {
  form.value.contentMode = mode
}

const openFilePicker = () => {
  if (!uploading.value) fileInput.value?.click()
}

const onFileChange = (event) => {
  uploadFile(event.target.files?.[0])
  event.target.value = ''
}

const handleFileDrop = (event) => {
  uploadFile(event.dataTransfer?.files?.[0])
}

const uploadFile = async (file) => {
  if (!file) return
  const name = file.name.toLowerCase()
  if (!/\.(pdf|doc|docx|txt)$/.test(name)) {
    ElMessage.warning('仅支持 PDF / DOC / DOCX / TXT 格式的文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadAgreementFile(file)
    form.value.fileObjectKey = res.data?.objectKey || ''
    form.value.fileName = res.data?.fileName || file.name
    form.value.fileSize = Number(res.data?.fileSize || file.size)
  } finally {
    uploading.value = false
  }
}

const removeFile = () => {
  form.value.fileObjectKey = ''
  form.value.fileName = ''
  form.value.fileSize = 0
}

const save = async () => {
  if (!form.value.name.trim()) return ElMessage.warning('请填写协议名称')
  if (!form.value.type) return ElMessage.warning('请选择协议类型')
  if (form.value.contentMode === 'INPUT' && !form.value.content.trim()) {
    return ElMessage.warning('请输入协议正文内容')
  }
  if (form.value.contentMode === 'FILE' && !form.value.fileObjectKey) {
    return ElMessage.warning('请上传协议文件')
  }
  saving.value = true
  try {
    if (isEdit.value) await updateAgreement(route.params.id, form.value)
    else await createAgreement(form.value)
    ElMessage.success(isEdit.value ? '协议已更新' : '协议已创建')
    backToList()
  } finally {
    saving.value = false
  }
}

const backToList = () => router.push('/admin/agreements')

function formatFileSize(value) {
  const size = Number(value || 0)
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  if (size >= 1024) return `${Math.max(1, Math.round(size / 1024))} KB`
  return `${size} B`
}

onMounted(loadAgreement)
</script>

<style scoped>
.agreement-edit { padding-bottom:0; }
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:20px; }
.form-card { padding:24px 30px; }
.form-section { display:flex; align-items:center; gap:8px; margin:20px 0 14px; color:#374151; font-size:13px; font-weight:600; }
.form-section::before { width:3px; height:13px; border-radius:2px; background:linear-gradient(180deg,#FF8C42,#FF6B35); content:''; }
.form-section:first-child { margin-top:0; }
.base-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.form-field { margin-bottom:16px; }
.form-field label { display:block; margin-bottom:6px; color:var(--text-secondary,#6B7280); font-size:13px; }
.required { margin-right:3px; color:#E5484D; }
.content-tabs { display:flex; gap:4px; margin-bottom:16px; border-bottom:1px solid var(--border,#E5E7EB); }
.content-tab { display:flex; align-items:center; gap:6px; margin-bottom:-1px; padding:9px 18px; border:0; border-bottom:2px solid transparent; background:none; color:var(--text-secondary,#6B7280); font-size:13px; font-weight:500; cursor:pointer; }
.content-tab:hover, .content-tab.active { color:var(--primary,#FF6B35); }
.content-tab.active { border-bottom-color:var(--primary,#FF6B35); }
.upload-area { position:relative; display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:176px; padding:32px; border:1.5px dashed #D1D5DB; border-radius:10px; background:#FAFAFA; cursor:pointer; text-align:center; transition:all .2s; }
.upload-area:hover, .upload-area.uploading { border-color:var(--primary,#FF6B35); background:#FFF7F0; }
.upload-icon { display:flex; align-items:center; justify-content:center; width:56px; height:56px; margin-bottom:10px; border-radius:14px; background:linear-gradient(135deg,#FFE8D6,#FFD6B0); color:var(--primary,#FF6B35); font-size:26px; box-shadow:0 4px 10px rgba(255,107,53,.18); }
.upload-main { color:#374151; font-size:14px; font-weight:500; }
.upload-hint { margin-top:4px; color:#9CA3AF; font-size:12px; }
.upload-button { display:inline-block; margin-top:12px; padding:7px 18px; border-radius:6px; background:var(--primary,#FF6B35); color:#fff; font-size:13px; font-weight:500; box-shadow:0 2px 6px rgba(255,107,53,.3); }
.hidden-input { display:none; }
.file-item { display:flex; align-items:center; gap:12px; margin-top:12px; padding:12px 14px; border:1px solid var(--border,#E5E7EB); border-radius:8px; background:#fff; }
.file-icon { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:38px; height:38px; border-radius:8px; background:linear-gradient(135deg,#FFE8D6,#FFD6B0); color:var(--primary,#FF6B35); font-size:16px; }
.file-info { flex:1; min-width:0; }
.file-name { overflow:hidden; color:var(--text-primary,#1F2937); font-size:13px; font-weight:500; text-overflow:ellipsis; white-space:nowrap; }
.file-size { margin-top:2px; color:var(--text-muted,#9CA3AF); font-size:12px; }
.remove-file { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:30px; height:30px; border:0; border-radius:6px; background:#FEF2F2; color:#E5484D; font-size:13px; cursor:pointer; }
.remove-file:hover { background:#FDE4E4; }
.footer-bar { position:sticky; bottom:0; display:flex; justify-content:flex-end; gap:12px; margin-top:30px; padding:14px 0 0; border-top:1px solid var(--border,#E5E7EB); background:#fff; }
@media (max-width: 900px) { .base-grid { grid-template-columns:1fr; } }
</style>
