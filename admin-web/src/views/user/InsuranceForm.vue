<template>
  <div>
    <div class="page-header page-header-row">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑保险产品' : '新建保险产品' }}</h1>
        <p class="page-desc">配置保险基础信息、产品方案及文档资料</p>
      </div>
      <button class="btn btn-outline" @click="goBack"><i class="fas fa-arrow-left"></i> 返回列表</button>
    </div>

    <div class="card form-card">
      <div class="form-section">基础信息</div>
      <div class="grid-2">
        <div class="form-item">
          <label class="form-label"><span class="required">*</span> 保险公司</label>
          <el-select v-model="form.carrier" placeholder="请选择保险公司">
            <el-option v-for="carrier in carriers" :key="carrier" :label="carrier" :value="carrier" />
          </el-select>
        </div>
        <div class="form-item">
          <label class="form-label"><span class="required">*</span> 产品编码</label>
          <el-input v-model="form.code" placeholder="例如 ZA-33W" />
        </div>
        <div class="form-item">
          <label class="form-label"><span class="required">*</span> 保险档数</label>
          <el-input-number v-model="form.tierCount" :min="1" :max="8" @change="syncTierCount" />
        </div>
        <div class="form-item">
          <label class="form-label">立即启用</label>
          <div class="switch-row-inline">
            <div class="switch" :class="{ on: form.enabled }" @click="form.enabled = !form.enabled"></div>
            <span class="switch-hint">保存后产品将在招工发布页可选</span>
          </div>
        </div>
      </div>

      <div class="form-section">产品配置（多档方案，每档独立配置注意事项与告知书）</div>
      <div v-for="(tier, index) in form.tiers" :key="index" class="tier-card">
        <div class="tier-card-head">
          <span class="t-name">档位 {{ index + 1 }} · {{ tier.amountText || '新档位' }}</span>
          <i class="fas fa-trash-alt t-del" @click="removeTier(index)"></i>
        </div>
        <div class="tier-card-body">
          <div class="tier-fields">
            <div class="form-item">
              <label class="form-label">保额</label>
              <el-input v-model="tier.amountText" placeholder="例如 99万" />
            </div>
            <div class="form-item">
              <label class="form-label">身故残疾</label>
              <el-input v-model="tier.deathText" placeholder="例如 90万" />
            </div>
            <div class="form-item">
              <label class="form-label">意外医疗</label>
              <el-input v-model="tier.medicalText" placeholder="例如 9万" />
            </div>
            <div class="form-item">
              <label class="form-label">积分/天</label>
              <el-input v-model="tier.pointsText" placeholder="例如 5积分" />
            </div>
          </div>
          <div class="form-item notice-item">
            <label class="form-label">赠送意外险注意事项</label>
            <el-input v-model="tier.notice" type="textarea" :rows="4" placeholder="每条一行，最多 8 条" />
          </div>
          <div class="form-item">
            <label class="form-label"><span class="required">*</span> 客户告知书附件</label>
            <div class="file-upload-compact" :class="{ uploaded: !!tier.noticeFileUrl }" @click="triggerUpload(index)">
              <div class="u-icon"><i class="fas fa-cloud-upload-alt"></i></div>
              <div class="u-text">
                <div class="u-main" :class="{ ok: !!tier.noticeFileUrl }">{{ tier.noticeFileName || '点击或拖拽文件到此处上传' }}</div>
                <div class="u-hint">{{ tier.noticeFileSize ? tier.noticeFileSize + ' · 已上传，点击文件区域可重新选择' : '支持 PDF 格式，单文件 ≤ 5MB' }}</div>
              </div>
              <span v-if="tier.noticeFileUrl" class="u-btn u-btn-del" @click.stop="clearNoticeFile(index)">删除</span>
              <span v-else class="u-btn">选择文件</span>
            </div>
            <input :ref="el => setFileInput(el, index)" type="file" accept=".pdf" style="display:none" @change="handleNoticeFile($event, index)" />
          </div>
        </div>
      </div>
      <button class="add-row-btn" @click="addTier"><i class="fas fa-plus"></i> 新增档位</button>
    </div>

    <div class="page-footer-bar">
      <button class="btn btn-outline" @click="goBack">取消</button>
      <button class="btn btn-primary" :disabled="saving" @click="handleSave"><i class="fas fa-check"></i> {{ form.enabled ? '保存并启用' : '保存' }}</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createInsurance, getInsurance, updateInsurance, uploadInsuranceNotice } from '@/api/insurance'

const route = useRoute()
const router = useRouter()
const carriers = ['众安保险', '平安保险', '太平洋保险', '中国人保', '中国人寿']
const isEdit = computed(() => route.query.id != null)
const saving = ref(false)
const fileInputs = {}

const DEFAULT_NOTICE = [
  '1. 被保险人年龄需在 16-60 周岁之间。',
  '2. 职业限制：不含外卖骑手、高空作业、地下采矿等高危职业。',
  '3. 医院要求：限国内二级及以上公立医院普通部。'
].join('\n')

const DEFAULT_TIERS = [
  { amount: 11, death: 10, medical: 1, points: 1 },
  { amount: 33, death: 30, medical: 3, points: 2 },
  { amount: 55, death: 50, medical: 5, points: 3 },
  { amount: 88, death: 80, medical: 8, points: 4 }
]

const form = reactive({
  carrier: '',
  code: '',
  tierCount: 4,
  enabled: true,
  tiers: []
})

const makeTier = (source = {}) => ({
  amountText: source.amountText ?? (source.amount ? `${source.amount}万` : ''),
  deathText: source.deathText ?? (source.death != null ? `${source.death}万` : ''),
  medicalText: source.medicalText ?? (source.medical != null ? `${source.medical}万` : ''),
  pointsText: source.pointsText ?? (source.points != null ? `${source.points}积分` : ''),
  notice: source.notice || DEFAULT_NOTICE,
  noticeFileName: source.noticeFileName || '',
  noticeFileUrl: source.noticeFileUrl || '',
  noticeFileSize: source.noticeFileSize || ''
})

const setFileInput = (el, index) => { if (el) fileInputs[index] = el }
const triggerUpload = (index) => fileInputs[index]?.click()

const syncTierCount = (count) => {
  const target = Math.max(1, Math.min(8, Number(count) || 1))
  while (form.tiers.length < target) form.tiers.push(makeTier())
  while (form.tiers.length > target) form.tiers.pop()
  form.tierCount = form.tiers.length
}

const addTier = () => {
  if (form.tiers.length >= 8) return ElMessage.warning('最多支持 8 档')
  form.tiers.push(makeTier())
  form.tierCount = form.tiers.length
}

const removeTier = async (index) => {
  if (form.tiers.length <= 1) return ElMessage.warning('至少保留一个档位')
  try {
    await ElMessageBox.confirm('确定删除该档位配置吗？', '删除档位', { type: 'warning' })
  } catch { return }
  form.tiers.splice(index, 1)
  form.tierCount = form.tiers.length
}

const clearNoticeFile = (index) => {
  form.tiers[index].noticeFileName = ''
  form.tiers[index].noticeFileUrl = ''
  form.tiers[index].noticeFileSize = ''
}

const handleNoticeFile = async (event, index) => {
  const file = event.target.files?.[0]
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (extension !== 'pdf') {
    event.target.value = ''
    return ElMessage.error('仅支持 PDF 格式附件')
  }
  if (file.size > 5 * 1024 * 1024) {
    event.target.value = ''
    return ElMessage.error('附件大小不能超过5MB')
  }
  try {
    const result = await uploadInsuranceNotice(file)
    const data = result?.data || {}
    if (!data.url && !data.objectKey) throw new Error('上传结果缺少文件地址')
    form.tiers[index].noticeFileName = file.name
    form.tiers[index].noticeFileUrl = data.url || data.objectKey
    form.tiers[index].noticeFileSize = formatSize(file.size)
    ElMessage.success('附件上传成功')
  } catch (error) {
    console.warn('[InsuranceForm] 附件上传失败:', error)
  } finally {
    event.target.value = ''
  }
}

const formatSize = (size) => size > 1024 * 1024
  ? `${(size / 1024 / 1024).toFixed(1)} MB`
  : `${Math.max(1, Math.round(size / 1024))} KB`

const parseAmount = (text, fallback = 0) => {
  const match = String(text || '').match(/[\d.]+/)
  return match ? Number(match[0]) : fallback
}

const loadDetail = async () => {
  try {
    const result = await getInsurance(route.query.id)
    const data = result?.data || {}
    form.carrier = data.carrier || ''
    form.code = data.code || ''
    form.enabled = !!data.enabled
    form.tiers = (data.tiers || []).map(tier => makeTier(tier))
    if (!form.tiers.length) form.tiers.push(makeTier())
    form.tierCount = form.tiers.length
  } catch (error) {
    console.warn('[InsuranceForm] 加载保险产品失败:', error)
    ElMessage.error('加载保险产品失败')
  }
}

const handleSave = async () => {
  if (!form.carrier) return ElMessage.warning('请选择保险公司')
  if (!form.code?.trim()) return ElMessage.warning('请填写产品编码')
  const missingFile = form.tiers.findIndex(tier => !tier.noticeFileUrl)
  if (missingFile >= 0) return ElMessage.warning(`档位 ${missingFile + 1} 请上传客户告知书附件`)
  saving.value = true
  try {
    const tiers = form.tiers.map((tier, index) => ({
      amount: parseAmount(tier.amountText, DEFAULT_TIERS[index]?.amount),
      amountText: tier.amountText,
      death: parseAmount(tier.deathText, DEFAULT_TIERS[index]?.death),
      deathText: tier.deathText,
      medical: parseAmount(tier.medicalText, DEFAULT_TIERS[index]?.medical),
      medicalText: tier.medicalText,
      points: parseAmount(tier.pointsText, DEFAULT_TIERS[index]?.points),
      pointsText: tier.pointsText,
      notice: tier.notice,
      noticeFileName: tier.noticeFileName,
      noticeFileUrl: tier.noticeFileUrl,
      noticeFileSize: tier.noticeFileSize
    }))
    const payload = { carrier: form.carrier, code: form.code.trim(), enabled: form.enabled, tiers }
    if (isEdit.value) await updateInsurance(route.query.id, payload)
    else await createInsurance(payload)
    ElMessage.success(isEdit.value ? '保险产品已更新' : '保险产品保存成功')
    router.push('/admin/insurance')
  } catch (error) {
    console.warn('[InsuranceForm] 保存失败:', error)
  } finally {
    saving.value = false
  }
}

const goBack = () => router.push('/admin/insurance')

onMounted(() => {
  syncTierCount(form.tierCount || 4)
  if (isEdit.value) loadDetail()
})
</script>

<style scoped>
.page-header-row { display: flex; align-items: center; justify-content: space-between; }
.form-card { padding: 24px 30px; }
.form-section { font-size: 13px; font-weight: 600; color: #374151; margin: 20px 0 14px; display: flex; align-items: center; gap: 8px; }
.form-section::before { content: ''; width: 3px; height: 13px; border-radius: 2px; background: linear-gradient(180deg, #FF8C42, #FF6B35); }
.form-section:first-child { margin-top: 0; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-item { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; color: var(--text-secondary); }
.required { color: #EF4444; margin-right: 2px; }
.switch-row-inline { display: flex; align-items: center; gap: 10px; height: 32px; }
.switch-hint { font-size: 12px; color: var(--text-muted); }
.switch { position: relative; width: 46px; height: 24px; border-radius: 12px; background: #D1D5DB; cursor: pointer; transition: background .25s ease; flex-shrink: 0; }
.switch::after { content: ''; position: absolute; top: 2px; left: 2px; width: 20px; height: 20px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.18); transition: left .25s cubic-bezier(.4,.8,.4,1); }
.switch.on { background: #FF6B35; }
.switch.on::after { left: 24px; }
.tier-card { border: 1px solid var(--border); border-radius: 10px; background: #fff; margin-bottom: 16px; overflow: hidden; }
.tier-card-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px; background: #FAFAFA; border-bottom: 1px solid var(--border); }
.t-name { font-size: 14px; font-weight: 600; color: #374151; display: flex; align-items: center; gap: 8px; }
.t-name::before { content: ''; width: 3px; height: 13px; border-radius: 2px; background: linear-gradient(180deg, #FF8C42, #FF6B35); }
.t-del { color: #EF4444; cursor: pointer; font-size: 14px; padding: 4px 8px; border-radius: 6px; }
.t-del:hover { background: #FEF2F2; }
.tier-card-body { padding: 18px; }
.tier-fields { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.notice-item { margin-bottom: 14px; }
.file-upload-compact { display: flex; align-items: center; gap: 12px; border: 1.5px dashed #D1D5DB; border-radius: 8px; padding: 12px 14px; cursor: pointer; transition: all .2s ease; background: #FAFAFA; }
.file-upload-compact:hover { border-color: var(--primary); background: #FFF7F0; }
.file-upload-compact.uploaded { border-color: #16A34A; background: #F0FFF4; }
.u-icon { width: 36px; height: 36px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #FFE8D6, #FFD6B0); border-radius: 8px; color: #FF6B35; font-size: 16px; }
.u-text { flex: 1; min-width: 0; }
.u-main { font-size: 13px; color: #374151; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.u-main.ok { color: #16A34A; font-weight: 600; }
.u-hint { font-size: 11px; color: #9CA3AF; margin-top: 2px; }
.u-btn { padding: 6px 14px; background: #FF6B35; color: #fff; border-radius: 6px; font-size: 12px; font-weight: 500; flex-shrink: 0; box-shadow: 0 2px 6px rgba(255,107,53,.3); }
.u-btn.u-btn-del { background: #E5484D; box-shadow: 0 2px 6px rgba(229,72,77,.3); }
.add-row-btn { width: 100%; padding: 10px; border: 1.5px dashed var(--border); border-radius: 8px; color: var(--text-muted); background: #fff; cursor: pointer; font-size: 13px; display: flex; align-items: center; justify-content: center; gap: 6px; }
.add-row-btn:hover { border-color: var(--primary); color: var(--primary); background: #FFF7F0; }
.page-footer-bar { position: sticky; bottom: 0; z-index: 10; background: #fff; border-top: 1px solid var(--border); padding: 14px 30px; display: flex; justify-content: flex-end; gap: 12px; margin-top: 30px; border-radius: 12px 12px 0 0; box-shadow: 0 -4px 16px rgba(0,0,0,.04); }
</style>
