<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">客服管理</h1>
      <p class="page-desc">在线客服、会话记录、常见问题</p>
    </div>

    <div class="service-grid">
      <!-- 左列：当前活跃会话 -->
      <div class="card">
        <div class="section-title">
          <span><i class="fas fa-headset section-icon"></i>当前活跃会话</span>
          <button class="btn btn-outline btn-sm" @click="filterVisible = !filterVisible"><i class="fas fa-filter"></i> 筛选</button>
        </div>

        <div v-if="filterVisible" class="filter-panel">
          <div class="filter-row"><span class="filter-label">用户：</span><el-input v-model="filterUser" placeholder="输入用户名或手机号" clearable style="width: 160px" /></div>
          <div class="filter-row"><span class="filter-label">类型：</span>
            <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 120px">
              <el-option label="咨询" value="consult" /><el-option label="投诉" value="complaint" /><el-option label="其他" value="other" />
            </el-select>
          </div>
          <div class="filter-row"><span class="filter-label">状态：</span>
            <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 120px" @change="loadSessions">
              <el-option label="等待中" value="OPEN" /><el-option label="处理中" value="PROCESSING" /><el-option label="已解决" value="CLOSED" />
            </el-select>
          </div>
          <div class="filter-actions">
            <button class="btn btn-outline btn-sm" @click="resetFilter">重置</button>
            <button class="btn btn-primary btn-sm" @click="loadSessions"><i class="fas fa-search"></i> 应用筛选</button>
          </div>
        </div>

        <el-table :data="filteredSessions" stripe :header-cell-style="{ background: '#F9FAFB', color: '#6B7280', fontWeight: 500 }">
          <el-table-column label="会话ID" width="110">
            <template #default="{ row }"><span class="session-id">{{ row.id }}</span></template>
          </el-table-column>
          <el-table-column label="用户" min-width="140">
            <template #default="{ row }">
              <div class="user-cell"><span class="user-avatar" :style="{ background: avatarColor(row.userId) }">{{ String(row.userName || row.userId || 'U').charAt(0) }}</span><span class="user-name">{{ row.userName || `用户${row.userId}` }}</span></div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }"><span :class="['type-tag', typeClass(row.type)]">{{ typeText(row.type) }}</span></template>
          </el-table-column>
          <el-table-column label="等待时间" width="100">
            <template #default="{ row }"><span :class="waitClass(row)">{{ waitText(row) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><span :class="['status-badge', statusBadge(row.status)]">{{ statusText(row.status) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <template v-if="row.status === 'CLOSED'">
                  <el-button link type="primary" size="small" @click="viewSession(row)">查看</el-button>
                </template>
                <template v-else>
                  <el-button link type="primary" size="small" @click="handleClaim(row)">处理</el-button>
                  <el-button link type="danger" size="small" @click="handleClose(row)">关闭</el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 右列：快捷回复 + 常见问题 -->
      <div class="right-col">
        <div class="card">
          <div class="section-title">
            <span><i class="fas fa-bolt section-icon"></i>快捷回复</span>
            <a class="add-btn" @click="showReplyDialog()"><i class="fas fa-plus"></i> 新增</a>
          </div>
          <div class="quick-reply-list">
            <div v-for="reply in quickReplies" :key="reply.id" class="quick-reply-item">
              <div class="quick-reply-content">
                <span class="quick-reply-tag">{{ reply.category || '通用' }}</span>
                <span class="quick-reply-text">{{ reply.content }}</span>
              </div>
              <div class="quick-reply-actions">
                <button class="btn btn-outline btn-sm" type="button" title="复制" aria-label="复制" @click="copyReply(reply)">
                  <i class="fas fa-copy"></i>
                </button>
                <button class="btn btn-secondary btn-sm" type="button" title="编辑" aria-label="编辑" @click="showReplyDialog(reply)">
                  <i class="fas fa-edit"></i>
                </button>
                <button class="btn btn-danger btn-sm" type="button" title="删除" aria-label="删除" @click="handleDeleteReply(reply.id)">
                  <i class="fas fa-trash"></i>
                </button>
              </div>
            </div>
            <div v-if="!quickReplies.length" class="empty-text">暂无快捷回复</div>
          </div>
        </div>

        <div class="card">
          <div class="section-title">
            <span><i class="fas fa-question-circle section-icon"></i>常见问题</span>
            <a class="add-btn" @click="showFaqDialog()"><i class="fas fa-plus"></i> 新增</a>
          </div>
          <div class="faq-list">
            <div v-for="faq in faqs" :key="faq.id" :class="['faq-item', { open: openFaq === faq.id }]">
              <div class="faq-question" @click="openFaq = openFaq === faq.id ? null : faq.id">
                <span class="faq-question-text">
                  <span v-if="faq.category" class="faq-category-tag">{{ faq.category }}</span>
                  {{ faq.question }}
                </span>
                <i :class="['fas', 'fa-chevron-down', 'faq-chevron']"></i>
              </div>
              <div v-show="openFaq === faq.id" class="faq-answer">{{ faq.answer }}</div>
            </div>
            <div v-if="!faqs.length" class="empty-text">暂无常见问题</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷回复编辑弹窗 -->
    <el-dialog v-model="replyDialogVisible" :title="replyForm.id ? '编辑快捷回复' : '新增快捷回复'" width="500px" class="prototype-dialog">
      <el-form :model="replyForm" label-position="top" class="dialog-form">
        <el-form-item label="回复内容" required>
          <el-input v-model="replyForm.content" type="textarea" :autosize="{ minRows: 4 }" placeholder="请输入快捷回复内容..." />
        </el-form-item>
        <el-form-item label="分类标签">
          <el-select v-model="replyForm.category" placeholder="请选择分类标签" style="width: 100%">
            <el-option v-for="tag in replyTags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveReply">保存</el-button>
      </template>
    </el-dialog>

    <!-- FAQ 编辑弹窗 -->
    <el-dialog v-model="faqDialogVisible" :title="faqForm.id ? '编辑常见问题' : '新增常见问题'" width="560px" class="prototype-dialog">
      <el-form :model="faqForm" label-position="top" class="dialog-form">
        <el-form-item label="问题分类">
          <el-select v-model="faqForm.category" placeholder="请选择问题分类" style="width: 100%">
            <el-option v-for="cat in faqCategories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题标题" required>
          <el-input v-model="faqForm.question" placeholder="请输入问题标题" />
        </el-form-item>
        <el-form-item label="解答内容" required>
          <el-input v-model="faqForm.answer" type="textarea" :autosize="{ minRows: 6 }" placeholder="请输入详细解答内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="faqDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFaq">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getServiceStats, getServiceSessions, claimServiceSession, closeServiceSession,
  getQuickReplies, createQuickReply, updateQuickReply, deleteQuickReply,
  getFaqs, createFaq, updateFaq, deleteFaq
} from '@/api/service'

const router = useRouter()
const stats = ref({})
const sessions = ref([])
const filterStatus = ref('')
const filterUser = ref('')
const filterType = ref('')
const replyTags = ['通用', '订单咨询', '投诉处理', '结算问题', '系统使用']
const faqCategories = ['账号问题', '订单问题', '结算问题', '功能使用', '投诉建议']
const filterVisible = ref(false)
const openFaq = ref(null)
const quickReplies = ref([])
const faqs = ref([])

const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
}

const loadStats = async () => {
  try {
    const res = await getServiceStats()
    stats.value = res.data || {}
  } catch (e) { console.error(e) }
}

const loadSessions = async () => {
  try {
    const res = await getServiceSessions({ status: filterStatus.value, page: 0, size: 50 })
    sessions.value = res.data || []
    applySessionFilters()
  } catch (e) { console.error(e) }
}

const loadQuickReplies = async () => {
  try {
    const res = await getQuickReplies()
    quickReplies.value = res.data || []
  } catch (e) { console.error(e) }
}

const loadFaqs = async () => {
  try {
    const res = await getFaqs()
    faqs.value = res.data || []
  } catch (e) { console.error(e) }
}

const filteredSessions = ref([])
const applySessionFilters = () => {
  let list = sessions.value
  if (filterType.value) { list = list.filter(item => (item.type || 'other') === filterType.value) }
  if (filterUser.value) {
    const kw = filterUser.value.trim().toLowerCase()
    list = list.filter(item => String(item.userId || '').includes(kw) || String(item.userName || '').toLowerCase().includes(kw) || String(item.userPhone || '').includes(kw))
  }
  filteredSessions.value = list
}
const resetFilter = () => { filterUser.value = ''; filterType.value = ''; filterStatus.value = ''; loadSessions() }
const avatarColor = (id) => {
  const palette = ['#FF6B35', '#2563EB', '#10B981', '#8B5CF6', '#F59E0B', '#EC4899', '#06B6D4', '#64748B']
  return `linear-gradient(135deg, ${palette[Number(id) % palette.length]}, ${palette[Number(id) % palette.length]}cc)`
}
const typeText = (type) => ({ consult: '咨询', complaint: '投诉', other: '其他' }[type] || '其他')
const typeClass = (type) => ({ consult: 'type-consult', complaint: 'type-complaint', other: 'type-other' }[type] || 'type-other')
const statusText = (status) => ({ OPEN: '等待中', PROCESSING: '处理中', CLOSED: '已解决' }[status] || status || '-')
const statusBadge = (status) => ({ OPEN: 'warning', PROCESSING: 'info', CLOSED: 'success' }[status] || 'default')
const waitText = (row) => {
  if (row.status !== 'OPEN' && row.status !== 'PROCESSING') return '-'
  if (!row.timestamp) return '刚刚'
  const minutes = Math.floor((Date.now() - new Date(row.timestamp).getTime()) / 60000)
  if (minutes < 1) return '刚刚'
  return `${minutes}分钟`
}
const waitClass = (row) => {
  if (row.status !== 'OPEN') return ''
  const minutes = row.timestamp ? Math.floor((Date.now() - new Date(row.timestamp).getTime()) / 60000) : 0
  return minutes > 5 ? 'waiting-danger' : minutes > 2 ? 'waiting-warn' : ''
}

const viewSession = row => router.push({ name: 'ServiceChat', params: { id: String(row.id) } })
const handleClaim = async row => {
  try {
    await claimServiceSession(row.id)
    await loadSessions()
    router.push({ name: 'ServiceChat', params: { id: String(row.id) } })
  } catch (e) {
    if (e?.message) ElMessage.warning(e.message)
  }
}
const handleClose = async row => {
  try {
    await ElMessageBox.confirm(`确定关闭会话 ${row.id} 吗？关闭后将标记为已解决。`, '关闭会话', { type: 'warning', confirmButtonText: '关闭', cancelButtonText: '取消' })
  } catch { return }
  try {
    await closeServiceSession(row.id)
    ElMessage.success('会话已关闭')
    await loadSessions()
  } catch (e) {
    if (e?.message) ElMessage.warning(e.message)
  }
}

// ====== 快捷回复 ======
const replyDialogVisible = ref(false)
const replyForm = reactive({ id: null, content: '', category: '', sortOrder: 0, enabled: true })

const showReplyDialog = (item) => {
  if (item) {
    Object.assign(replyForm, item)
  } else {
    Object.assign(replyForm, { id: null, content: '', category: '通用', sortOrder: 0, enabled: true })
  }
  replyDialogVisible.value = true
}

const saveReply = async () => {
  if (!replyForm.content.trim()) { ElMessage.warning('请输入回复内容'); return }
  try {
    if (replyForm.id) {
      await updateQuickReply(replyForm.id, replyForm)
    } else {
      await createQuickReply(replyForm)
    }
    ElMessage.success('保存成功')
    replyDialogVisible.value = false
    loadQuickReplies()
    loadStats()
  } catch (e) { ElMessage.error('保存失败') }
}

const handleDeleteReply = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此快捷回复？', '提示', { type: 'warning' })
    await deleteQuickReply(id)
    ElMessage.success('已删除')
    loadQuickReplies()
    loadStats()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

const copyReply = async (reply) => {
  const content = reply?.content?.trim()
  if (!content) {
    ElMessage.warning('快捷回复内容为空')
    return
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = content
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动复制')
  }
}

// ====== FAQ ======
const faqDialogVisible = ref(false)
const faqForm = reactive({ id: null, question: '', answer: '', category: '', sortOrder: 0, enabled: true })

const showFaqDialog = (item) => {
  if (item) {
    Object.assign(faqForm, item)
  } else {
    Object.assign(faqForm, { id: null, question: '', answer: '', category: '账号问题', sortOrder: 0, enabled: true })
  }
  faqDialogVisible.value = true
}

const saveFaq = async () => {
  if (!faqForm.question.trim()) { ElMessage.warning('请输入问题'); return }
  try {
    if (faqForm.id) {
      await updateFaq(faqForm.id, faqForm)
    } else {
      await createFaq(faqForm)
    }
    ElMessage.success('保存成功')
    faqDialogVisible.value = false
    loadFaqs()
    loadStats()
  } catch (e) { ElMessage.error('保存失败') }
}

const handleDeleteFaq = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此FAQ？', '提示', { type: 'warning' })
    await deleteFaq(id)
    ElMessage.success('已删除')
    loadFaqs()
    loadStats()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

onMounted(() => {
  loadStats()
  loadSessions()
  loadQuickReplies()
  loadFaqs()
})
</script>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.stat-card-title {
  font-size: 14px;
  color: var(--text-secondary);
}
.stat-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  background: #fff0eb;
  color: #FF6B35;
}
.stat-card-icon.blue { background: #eff6ff; color: #409eff; }
.stat-card-icon.green { background: #f0f9eb; color: #67c23a; }
.stat-card-icon.yellow { background: #fdf6ec; color: #e6a23c; }
.stat-card-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}
.service-list {
  display: flex;
  flex-direction: column;
}
.service-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.service-item:hover {
  background: #f8f9fa;
}
.service-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.mini-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #FF6B35;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.service-item-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.service-time {
  font-size: 12px;
  color: #999;
}
.quick-replies {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.reply-card {
  padding: 12px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 3px solid #FF6B35;
}
.reply-card-text {
  font-size: 13px;
  color: #333;
  margin-bottom: 6px;
}
.reply-card-answer {
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
  line-height: 1.5;
}
.reply-card-actions {
  display: flex;
  gap: 8px;
}
.service-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; align-items: start; }
.right-col { display: flex; flex-direction: column; gap: 16px; }
.section-title { display: flex; align-items: center; justify-content: space-between; font-size: 16px; font-weight: 600; padding: 16px 20px; border-bottom: 1px solid var(--border); }
.section-icon { color: var(--primary); margin-right: 8px; }
.filter-panel { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin: 0 0 16px; padding: 14px 16px; background: #fff; border: 1px solid var(--border); border-top: none; box-shadow: 0 4px 12px rgba(0,0,0,.06); }
.filter-row { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 13px; color: var(--text-secondary); white-space: nowrap; }
.filter-actions { display: flex; gap: 8px; margin-left: auto; }
.service-grid > .card > .el-table { width: 100%; }
.session-id { font-family: monospace; color: var(--primary); font-size: 12px; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.user-avatar { width: 28px; height: 28px; border-radius: 50%; color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.user-name { font-weight: 500; font-size: 13px; }
.type-tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.type-consult { background: #EFF6FF; color: #2563EB; }
.waiting-warn { color: #D97706; font-weight: 500; }
.waiting-danger { color: #DC2626; font-weight: 500; }
.quick-reply-list { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px 16px; }
.quick-reply-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; background: #FAFAFA; border-left: 3px solid var(--primary); border-radius: 8px; }
.quick-reply-item:hover { background: #FFF7F0; }
.quick-reply-content { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.quick-reply-tag { flex-shrink: 0; padding: 3px 8px; border-radius: 4px; font-size: 10px; background: #FFF3ED; color: var(--primary); }
.quick-reply-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: var(--text-primary); }
.quick-reply-actions { display: flex; gap: 6px; flex-shrink: 0; }
.quick-reply-actions .btn { padding: 3px 8px; font-size: 11px; }
.faq-list { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px 16px; }
.faq-item { border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.faq-question { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; cursor: pointer; background: #fff; }
.faq-question:hover { background: #F9FAFB; }
.faq-question-text { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 500; }
.faq-category-tag { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #FFF3ED; color: var(--primary); }
.faq-chevron { font-size: 12px; color: var(--text-muted); transition: transform .2s; }
.faq-item.open .faq-chevron { transform: rotate(180deg); }
.faq-answer { padding: 12px 14px; background: #FAFAFA; border-top: 1px solid var(--border); font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.empty-text { text-align: center; color: var(--text-muted); font-size: 13px; padding: 24px 0; }
@media (max-width: 768px) { .service-grid { grid-template-columns: 1fr; } }
.prototype-dialog { border-radius: 16px; overflow: hidden; }
.dialog-form :deep(.el-form-item__label) { padding-bottom: 6px; color: var(--text-secondary); font-size: 13px; font-weight: 500; }
.dialog-form :deep(.el-textarea__inner) { line-height: 1.6; }
.row-actions{display:flex;align-items:center;justify-content:center;gap:2px;white-space:nowrap}
</style>
