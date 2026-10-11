<template>
  <div>
    <div class="detail-topbar">
      <button class="btn btn-outline btn-sm" @click="goBack"><i class="fas fa-list"></i> 返回列表</button>
    </div>

    <div class="detail-hero">
      <div class="hero-logo" :class="carrierClass(detail.carrier)">{{ (detail.carrier || '?').charAt(0) }}</div>
      <div class="hero-main">
        <div class="hero-title">{{ detail.carrier }} · 最高保额 {{ maxAmount }}万</div>
        <div class="hero-tags">
          <span class="status-tag" :class="detail.enabled ? 'on' : 'off'">{{ detail.enabled ? '已启用' : '已停用' }}</span>
          <span class="status-tag job">投保工种：普工</span>
          <span class="status-tag code">{{ detail.code || '-' }}</span>
        </div>
      </div>
    </div>

    <div class="card info-card">
      <div class="info-grid">
        <div class="info-item"><div class="label">保险公司</div><div class="val">{{ detail.carrier || '-' }}</div></div>
        <div class="info-item"><div class="label">产品编码</div><div class="val">{{ detail.code || '-' }}</div></div>
        <div class="info-item"><div class="label">最高保额</div><div class="val">{{ maxAmount }}万</div></div>
        <div class="info-item"><div class="label">状态</div><div class="val" :class="detail.enabled ? 'ok' : 'bad'">{{ detail.enabled ? '已启用' : '已停用' }}</div></div>
      </div>
    </div>

    <div class="card tab-card">
      <div class="tabs">
        <div v-for="item in tabs" :key="item.key" class="tab" :class="{ active: activeTab === item.key }" @click="activeTab = item.key">{{ item.label }}</div>
      </div>

      <div v-if="activeTab === 'config'">
        <el-table :data="detail.tiers || []" class="tier-table" :header-cell-style="{ background: '#FAFAFA', color: '#6B7280', fontWeight: 600 }">
          <el-table-column label="保额档位" min-width="110">
            <template #default="{ row }"><b class="amount">{{ row.amountText || (row.amount ? row.amount + '万' : '-') }}</b></template>
          </el-table-column>
          <el-table-column label="意外伤害身故残疾" min-width="150">
            <template #default="{ row }">{{ row.deathText || (row.death != null ? row.death + '万' : '-') }}</template>
          </el-table-column>
          <el-table-column label="意外医疗" min-width="120">
            <template #default="{ row }">{{ row.medicalText || (row.medical != null ? row.medical + '万' : '-') }}</template>
          </el-table-column>
          <el-table-column label="日耗积分" min-width="100">
            <template #default="{ row }">{{ row.pointsText || (row.points != null ? row.points + '积分' : '-') }}</template>
          </el-table-column>
          <el-table-column label="备注">
            <template #default="{ row }"><span class="muted">{{ row.tag || '-' }}</span></template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="activeTab === 'notes'">
        <ul v-if="notes.length" class="notes-list">
          <li v-for="(note, index) in notes" :key="index">
            <span class="n">{{ index + 1 }}</span>
            <div>{{ note }}</div>
          </li>
        </ul>
        <div v-else class="empty-text">暂未配置注意事项</div>
      </div>

      <div v-else>
        <div v-if="noticeFile.url" class="file-card" @click="openNotice">
          <span class="file-icon"><i class="fas fa-file-pdf"></i></span>
          <div class="file-info">
            <div class="file-name">{{ noticeFile.name }}</div>
            <div class="file-meta">{{ noticeFile.ext }} · {{ noticeFile.size || '—' }} · 保险产品附件</div>
          </div>
          <span class="file-view"><i class="fas fa-eye"></i> 在线查看</span>
        </div>
        <div v-else class="file-empty"><i class="fas fa-file-circle-plus"></i>暂未上传客户告知书文件</div>
      </div>
    </div>

    <el-dialog v-model="noticeVisible" width="680px" :close-on-click-modal="false">
      <template #header>
        <div class="modal-head">
          <div class="modal-head-left">
            <span class="file-icon small"><i class="fas fa-file-pdf"></i></span>
            <div>
              <div class="modal-title">{{ noticeFile.name }}</div>
              <div class="modal-sub">在线预览 · 仅限平台内部查看</div>
            </div>
          </div>
        </div>
      </template>
          <iframe v-if="signedNoticeUrl" :src="signedNoticeUrl" class="notice-frame" frameborder="0" />
          <div v-else class="empty-text">正在生成预览地址...</div>
      <template #footer>
        <el-button @click="noticeVisible = false">关闭</el-button>
        <el-button type="primary" @click="openFile(signedNoticeUrl)">新窗口打开</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInsurance, getInsuranceNoticeUrl } from '@/api/insurance'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const activeTab = ref('config')
const noticeVisible = ref(false)
const signedNoticeUrl = ref('')
const tabs = [
  { key: 'config', label: '产品配置' },
  { key: 'notes', label: '赠送意外险注意事项' },
  { key: 'notice', label: '客户告知书' }
]

const maxAmount = computed(() => Math.max(0, ...(detail.value.tiers || []).map(tier => Number(tier.amount || 0))))

const notes = computed(() => {
  const first = (detail.value.tiers || []).find(tier => tier.notice)
  if (!first) return []
  return String(first.notice)
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => line.replace(/^\d+[.、]\s*/, ''))
})

const noticeFile = computed(() => {
  const first = (detail.value.tiers || []).find(tier => tier.noticeFileUrl)
  const url = first?.noticeFileUrl || ''
  const name = first?.noticeFileName || ''
  const ext = name.includes('.') ? name.split('.').pop().toUpperCase() : 'PDF'
  return { url, name, size: first?.noticeFileSize || '', ext }
})

const carrierClass = (carrier) => ({
  '众安保险': 'zongan', '平安保险': 'pingan', '太平洋保险': 'taipingyang', '中国人保': 'renbao', '中国人寿': 'renbao'
}[carrier] || 'default')

const loadDetail = async () => {
  try {
    const result = await getInsurance(route.query.id)
    detail.value = result?.data || {}
    const tab = String(route.query.tab || 'config')
    if (['config', 'notes', 'notice'].includes(tab)) activeTab.value = tab
  } catch (error) {
    console.warn('[InsuranceDetail] 加载失败:', error)
    ElMessage.error('加载保险产品失败')
  }
}

const openNotice = async () => {
  signedNoticeUrl.value = ''
  noticeVisible.value = true
  try {
    const result = await getInsuranceNoticeUrl(route.query.id)
    signedNoticeUrl.value = result?.data?.url || ''
    if (!signedNoticeUrl.value) throw new Error('获取预览地址失败')
  } catch (error) {
    noticeVisible.value = false
    console.warn('[InsuranceDetail] 获取告知书预览地址失败:', error)
    ElMessage.error('获取告知书预览地址失败')
  }
}

const openFile = (url) => window.open(url, '_blank')
const goBack = () => router.push('/admin/insurance')

onMounted(loadDetail)
</script>

<style scoped>
.detail-topbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.detail-hero { display: flex; align-items: center; gap: 18px; padding: 22px 26px; background: #fff; border: 1px solid var(--border); border-radius: 12px; margin-bottom: 14px; }
.hero-logo { width: 56px; height: 56px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; color: #fff; flex-shrink: 0; box-shadow: 0 4px 12px rgba(255,107,53,.3); }
.zongan { background: linear-gradient(135deg, #FF6B35, #FF8C5A); }
.pingan { background: linear-gradient(135deg, #1677FF, #4096FF); }
.taipingyang { background: linear-gradient(135deg, #16A34A, #52C41A); }
.renbao { background: linear-gradient(135deg, #9333EA, #C084FC); }
.default { background: linear-gradient(135deg, #6B7280, #9CA3AF); }
.hero-main { flex: 1; min-width: 0; }
.hero-title { font-size: 17px; font-weight: 700; color: #111827; }
.hero-tags { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
.status-tag { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 500; }
.status-tag.on { background: #F0FDF4; color: #16A34A; }
.status-tag.off { background: #FEF2F2; color: #DC2626; }
.status-tag.job { background: #EFF6FF; color: #2563EB; }
.status-tag.code { background: #FFF7ED; color: #92400E; font-family: monospace; }
.info-card { padding: 16px; }
.info-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.info-item { background: #FAFAFA; border-radius: 10px; padding: 14px 16px; }
.info-item .label { font-size: 12px; color: var(--text-muted); margin-bottom: 6px; }
.info-item .val { font-size: 15px; font-weight: 600; color: #111827; }
.info-item .val.ok { color: #16A34A; }
.info-item .val.bad { color: #DC2626; }
.tab-card { padding: 6px 20px 20px; }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border); margin-bottom: 18px; }
.tab { padding: 10px 18px; font-size: 14px; color: var(--text-secondary); cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; }
.tab:hover { color: var(--text-primary); }
.tab.active { color: var(--primary); border-bottom-color: var(--primary); font-weight: 600; }
.tier-table .amount { color: #FF6B35; font-weight: 600; }
.muted { color: #9CA3AF; }
.notes-list { list-style: none; padding: 0; margin: 0; }
.notes-list li { display: flex; gap: 10px; padding: 10px 0; border-bottom: .5px solid #F3F4F6; font-size: 13px; color: #374151; line-height: 1.7; }
.notes-list li:last-child { border-bottom: none; }
.notes-list .n { width: 22px; height: 22px; border-radius: 50%; background: #FFF3ED; color: #FF6B35; font-size: 11px; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; }
.empty-text { text-align: center; color: #9CA3AF; font-size: 13px; padding: 40px 0; }
.file-card { display: flex; align-items: center; gap: 14px; padding: 16px 18px; border: 1px solid var(--border); border-radius: 10px; background: #FAFAFA; cursor: pointer; transition: all .2s; }
.file-card:hover { border-color: #FF6B35; background: #FFF9F6; box-shadow: 0 2px 10px rgba(255,107,53,.1); }
.file-icon { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 19px; color: #fff; background: linear-gradient(135deg, #E5484D, #FF7A7E); flex-shrink: 0; }
.file-icon.small { width: 34px; height: 34px; font-size: 15px; border-radius: 8px; }
.file-info { flex: 1; min-width: 0; }
.file-name { font-size: 14px; font-weight: 600; color: #111827; }
.file-meta { font-size: 12px; color: #9CA3AF; margin-top: 4px; }
.file-view { font-size: 13px; color: #FF6B35; font-weight: 500; flex-shrink: 0; }
.file-empty { border: 1px dashed #D1D5DB; border-radius: 10px; padding: 44px 20px; text-align: center; color: #9CA3AF; font-size: 13px; }
.file-empty i { font-size: 26px; margin-bottom: 10px; display: block; color: #D1D5DB; }
.modal-head { display: flex; align-items: center; justify-content: space-between; }
.modal-head-left { display: flex; align-items: center; gap: 10px; }
.modal-title { font-size: 15px; font-weight: 700; color: #111827; }
.modal-sub { font-size: 12px; color: #9CA3AF; margin-top: 2px; }
.notice-frame { width: 100%; height: 60vh; border-radius: 8px; background: #FAFAFA; }
</style>
