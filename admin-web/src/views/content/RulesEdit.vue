<template>
  <div>
    <template v-if="isCredit">
      <div class="credit-edit-header">
        <div><h2>{{ isEdit ? '编辑信用分规则' : '新建信用分规则' }}</h2><span>信用分规则</span></div>
        <div class="edit-header-actions">
          <button class="btn btn-outline" @click="goBack">返回列表</button>
          <button class="btn btn-outline" @click="saveDraft">保存草稿</button>
          <button class="btn btn-primary" @click="saveAndPublish">保存发布</button>
        </div>
      </div>
      <div class="credit-meta-card">
        <div class="credit-section-title"><i class="fas fa-file-lines"></i> 规则基础信息</div>
        <div class="credit-meta-grid">
          <label>规则名称<span>*</span><el-input v-model="form.title" placeholder="请输入规则名称" /></label>
          <label>分类<span>*</span><el-select v-model="creditCategory"><el-option label="信用分规则" value="信用分规则" /><el-option label="订单规则" value="订单规则" /><el-option label="结算规则" value="结算规则" /><el-option label="评价规则" value="评价规则" /></el-select></label>
          <label>版本<span>*</span><el-input v-model="form.version" /></label>
          <label>状态<span>*</span><el-select v-model="form.status"><el-option label="草稿" value="draft" /><el-option label="立即发布" value="published" /></el-select></label>
        </div>
      </div>
      <div class="credit-split">
        <CreditRulePanel title="加分项规则" :rows="creditAdds" type="add" @add="addCreditRow('add')" @remove="removeCreditRow('add', $event)" />
        <CreditRulePanel title="减分项规则" :rows="creditSubs" type="sub" @add="addCreditRow('sub')" @remove="removeCreditRow('sub', $event)" />
      </div>
    </template>
    <template v-else>
    <!-- 顶部编辑头部 -->
    <div class="edit-header">
      <div class="edit-header-left">
        <div class="edit-header-info">
          <h2>{{ isEdit ? (isCredit ? '编辑信用分规则' : currentType === 'platform' ? '编辑平台规则' : '编辑规则') : (isCredit ? '新建信用分规则' : currentType === 'platform' ? '新增平台规则' : '新增规则') }}</h2>
          <div v-if="currentType !== 'platform'" class="edit-header-meta">
            <span class="edit-id-badge">{{ isEdit ? ('ID: ' + (form.code || form.id || '--')) : '新规则' }}</span>
            <span>{{ form.category || typeCategoryMap[currentType] }}</span>
          </div>
        </div>
        <span v-if="currentType !== 'platform'" :class="['status-badge', statusBadgeClass]">{{ statusLabel(form.status) }}</span>
      </div>
      <div class="edit-header-actions">
        <button class="btn btn-outline" @click="goBack">
          <i class="fas fa-arrow-left"></i> 返回
        </button>
        <button v-if="currentType !== 'platform'" class="btn btn-outline" @click="previewRule">
          <i class="fas fa-eye"></i> 预览
        </button>
        <button v-else class="btn btn-outline" @click="saveDraft"><i class="fas fa-save"></i> 保存草稿</button>
        <button class="btn btn-primary" @click="saveAndPublish">
          <i class="fas fa-paper-plane"></i> {{ currentType === 'platform' ? '保存并发布' : '保存发布' }}
        </button>
      </div>
    </div>

    <div class="edit-layout" :class="{ 'platform-edit': currentType === 'platform' }">
      <!-- 左栏 -->
      <div class="left-col">
        <!-- 1. 基本信息 -->
        <div class="card">
          <div class="form-section">
            <div class="form-section-title">
              <span class="section-num">1</span> 基本信息
            </div>

            <div class="form-group">
              <div>
                <label class="form-label">规则名称 <span class="required">*</span></label>
                <el-input v-model="form.title" placeholder="请输入规则名称，如：平台服务协议" />
              </div>
              <div>
                <label class="form-label">版本号</label>
                <el-input v-model="form.version" placeholder="如：v1.0" />
              </div>
            </div>

            <div v-if="currentType !== 'platform'" style="margin-bottom: 18px;">
              <label class="form-label">规则类型 <span class="required">*</span></label>
              <el-select v-model="currentType" style="width: 240px;" @change="selectType">
                <el-option v-for="t in typeOptions" :key="t.key" :label="t.name" :value="t.key" />
              </el-select>
            </div>

            <div class="form-group">
              <div>
                <label class="form-label">规则分类</label>
                <el-select v-if="currentType === 'platform'" v-model="form.category" style="width: 100%;"><el-option label="规则公示" value="规则公示" /><el-option label="收费规则" value="收费规则" /><el-option label="交易规则" value="交易规则" /><el-option label="飞单认定与处理规则" value="飞单认定与处理规则" /></el-select>
                <el-input v-else v-model="form.category" placeholder="自动生成或手动填写" />
              </div>
              <div>
                <label class="form-label">状态</label>
                <el-radio-group v-model="form.status" @change="onStatusChange">
                  <el-radio value="draft">草稿</el-radio>
                  <el-radio value="published">立即发布</el-radio>
                  <el-radio v-if="currentType !== 'platform'" value="archived">归档</el-radio>
                </el-radio-group>
              </div>
            </div>
          </div>
        </div>

        <!-- 2. 规则内容 -->
        <div class="card">
          <div class="form-section">
            <div class="form-section-title">
              <span class="section-num">2</span> 规则内容
            </div>

            <div class="full-width">
              <label class="form-label">规则详细内容 <span class="required">*</span></label>
              <div class="rich-text-toolbar">
                <select class="toolbar-select" @change="execBlock($event.target.value); $event.target.selectedIndex = 0">
                  <option value="p">正文</option>
                  <option value="h1">标题1</option>
                  <option value="h2">标题2</option>
                  <option value="h3">标题3</option>
                </select>
                <span class="divider"></span>
                <button type="button" title="加粗" @click="exec('bold')"><i class="fas fa-bold"></i></button>
                <button type="button" title="斜体" @click="exec('italic')"><i class="fas fa-italic"></i></button>
                <button type="button" title="下划线" @click="exec('underline')"><i class="fas fa-underline"></i></button>
                <span class="divider"></span>
                <button type="button" title="无序列表" @click="exec('insertUnorderedList')"><i class="fas fa-list-ul"></i></button>
                <button type="button" title="有序列表" @click="exec('insertOrderedList')"><i class="fas fa-list-ol"></i></button>
                <button type="button" title="引用" @click="exec('formatBlock', 'blockquote')"><i class="fas fa-quote-left"></i></button>
                <span class="divider"></span>
                <button type="button" title="超链接" @click="insertLink"><i class="fas fa-link"></i></button>
                <button type="button" title="图片"><i class="fas fa-image"></i></button>
                <span class="divider"></span>
                <button type="button" title="左对齐" @click="exec('justifyLeft')"><i class="fas fa-align-left"></i></button>
                <button type="button" title="居中" @click="exec('justifyCenter')"><i class="fas fa-align-center"></i></button>
                <button type="button" title="右对齐" @click="exec('justifyRight')"><i class="fas fa-align-right"></i></button>
                <span class="divider"></span>
                <button type="button" title="撤销" @click="exec('undo')"><i class="fas fa-undo"></i></button>
                <button type="button" title="重做" @click="exec('redo')"><i class="fas fa-redo"></i></button>
              </div>
              <div
                ref="editorRef"
                class="content-editor"
                contenteditable="true"
                @input="onEditorInput"
              ></div>
              <div class="form-hint">支持多段落、列表、加粗等格式。内容会展示给所有相关用户查看。</div>
            </div>
          </div>
        </div>

        <!-- 3. 发布设置 -->
        <div v-if="currentType !== 'platform'" class="card">
          <div class="form-section">
            <div class="form-section-title">
              <span class="section-num">3</span> 发布设置
            </div>

            <div class="form-group">
              <div>
                <label class="form-label">生效时间</label>
                <el-date-picker
                  v-model="form.effectiveTime"
                  type="date"
                  placeholder="选择生效日期"
                  value-format="YYYY-MM-DD"
                  style="width: 100%;"
                />
              </div>
              <div>
                <label class="form-label">失效时间</label>
                <el-date-picker
                  v-model="form.expireTime"
                  type="date"
                  placeholder="选择失效日期"
                  value-format="YYYY-MM-DD"
                  style="width: 100%;"
                />
              </div>
              <div>
                <label class="form-label">适用对象</label>
                <el-select v-model="form.scope" style="width: 100%;">
                  <el-option label="全部用户" value="all" />
                  <el-option label="仅零工" value="worker" />
                  <el-option label="仅雇主" value="boss" />
                </el-select>
              </div>
              <div>
                <label class="form-label">是否置顶</label>
                <el-radio-group v-model="form.pinned">
                  <el-radio value="yes">是</el-radio>
                  <el-radio value="no">否</el-radio>
                </el-radio-group>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div v-if="currentType !== 'platform'" class="action-bar">
          <button class="btn btn-outline" @click="goBack">
            <i class="fas fa-arrow-left"></i> 返回
          </button>
          <button class="btn btn-outline" @click="saveDraft">
            <i class="fas fa-save"></i> 保存草稿
          </button>
          <button class="btn btn-primary" @click="saveAndPublish">
            <i class="fas fa-paper-plane"></i> 保存并发布
          </button>
        </div>
      </div>

      <!-- 右栏 -->
      <div v-if="currentType !== 'platform'" class="right-col">
        <!-- 规则信息 -->
        <div class="card info-card">
          <div class="info-card-title">
            <i class="fas fa-info-circle"></i> 规则信息
          </div>
          <div class="info-row">
            <span class="info-row-label">规则ID</span>
            <span class="info-row-value">{{ isEdit ? (form.code || form.id || '--') : '自动生成' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row-label">创建人</span>
            <span class="info-row-value">{{ form.creator || '超级管理员' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row-label">创建时间</span>
            <span class="info-row-value">{{ form.createTime || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row-label">更新时间</span>
            <span class="info-row-value">{{ form.updateTime || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row-label">状态</span>
            <span class="info-row-value">{{ statusLabel(form.status) }}</span>
          </div>
        </div>

        <!-- 填写提示 -->
        <div class="card info-card">
          <div class="info-card-title">
            <i class="fas fa-lightbulb"></i> 填写提示
          </div>
          <div class="tip-list">
            <div class="tip-item" v-for="(tip, i) in tips" :key="i">
              <i class="fas fa-check-circle"></i>
              <span>{{ tip }}</span>
            </div>
          </div>
        </div>

        <!-- 修改历史（仅编辑时显示） -->
        <div v-if="isEdit && history.length" class="card info-card">
          <div class="info-card-title">
            <i class="fas fa-history"></i> 修改历史
          </div>
          <div class="timeline-list">
            <div class="timeline-item" v-for="(h, i) in history" :key="i">
              <div class="timeline-dot"><i :class="['fas', h.icon]"></i></div>
              <div class="timeline-content">
                <div class="timeline-title">{{ h.title }}</div>
                <div class="timeline-time">{{ h.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="规则预览" width="640px">
      <div class="preview-meta">
        <span><i class="fas fa-tag" style="color: var(--primary);"></i> {{ form.category }}</span>
        <span><i class="fas fa-code-branch" style="color: var(--primary);"></i> {{ form.version || 'v1.0' }}</span>
        <span :class="['status-badge', statusBadgeClass]">{{ statusLabel(form.status) }}</span>
      </div>
      <div class="preview-content" v-html="form.content || '(暂无内容)'"></div>
      <template #footer>
        <button class="btn btn-outline" @click="previewVisible = false">关闭</button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { reactive, computed, ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listRules, createRules, updateRules } from '@/api/content'
import CreditRulePanel from './CreditRulePanel.vue'

const router = useRouter()
const route = useRoute()

const routeId = computed(() => route.params.id)
const routeTab = computed(() => route.params.tab || 'platform')
const isEdit = computed(() => !!routeId.value)
const isCredit = computed(() => routeTab.value === 'credit' || currentType.value === 'credit')

const typeCategoryMap = {
  platform: '规则公示',
  credit: '信用分规则',
  fee: '收费规则',
  trade: '交易规则',
  private: '飞单认定与处理规则',
  ip: '知识产权规则'
}

const typeOptions = [
  { key: 'platform', name: '平台规则' },
  { key: 'fee', name: '收费规则' },
  { key: 'trade', name: '交易规则' },
  { key: 'private', name: '飞单认定与处理' },
  { key: 'ip', name: '知识产权规则' }
]

const tips = [
  '规则名称建议简洁明确，如"平台服务协议"',
  '内容支持富文本格式，可使用标题、列表、加粗等',
  '发布前请仔细检查内容，已发布规则仅能编辑',
  '建议先保存草稿，预览无误后再发布',
  '版本号规则：首次发布为v1.0，后续递增'
]

const initialType = routeTab.value === 'credit' ? 'credit' : 'platform'
const currentType = ref(initialType)
const previewVisible = ref(false)
const history = ref([])
const creditCategory = ref('信用分规则')
const creditAdds = ref([
  { name: '完成订单', desc: '结算成功后给老板加分', trigger: '结算成功时触发', score: 3, times: '一个订单一次', updateTime: '实时' },
  { name: '及时结算', desc: '完工后1小时内结算', trigger: '结算成功时触发', score: 2, times: '一个订单一次', updateTime: '实时' }
])
const creditSubs = ref([
  { name: '零工差评', desc: '零工评价平均2星及以下', trigger: '评价提交时触发', score: -10, times: '一个订单一次', updateTime: '实时' },
  { name: '超时结算', desc: '完工后超过24小时仍未结算', trigger: '定时任务检查', score: -5, times: '一个订单一次', updateTime: '每天凌晨' }
])

const addCreditRow = (type) => {
  const rows = type === 'add' ? creditAdds.value : creditSubs.value
  rows.push({ name: '新规则', desc: '', trigger: '', score: type === 'add' ? 1 : -1, times: '一个订单一次', updateTime: '实时' })
}
const removeCreditRow = (type, index) => {
  const rows = type === 'add' ? creditAdds.value : creditSubs.value
  if (rows.length <= 1) return ElMessage.warning('至少保留一条规则')
  rows.splice(index, 1)
}
const editorRef = ref(null)

// 富文本编辑器命令
const exec = (command, value = null) => {
  document.execCommand(command, false, value)
  editorRef.value?.focus()
}
const execBlock = (tag) => {
  document.execCommand('formatBlock', false, tag)
  editorRef.value?.focus()
}
const insertLink = () => {
  const url = window.prompt('请输入链接地址：')
  if (url) exec('createLink', url)
}
const onEditorInput = () => {
  if (editorRef.value) {
    form.content = editorRef.value.innerHTML
  }
}

const form = reactive({
  id: null,
  code: '',
  title: '',
  category: typeCategoryMap[initialType],
  version: 'v1.0',
  status: 'draft',
  content: '',
  effectiveTime: '',
  expireTime: '',
  scope: 'all',
  pinned: 'no',
  creator: '',
  createTime: '',
  updateTime: ''
})

const statusBadgeClass = computed(() => {
  const map = { published: 'success', draft: 'draft', archived: 'archived' }
  return map[form.status] || 'draft'
})

const statusLabel = (status) => {
  const map = { published: '已发布', draft: '草稿', archived: '已归档' }
  return map[status] || '草稿'
}

const selectType = (key) => {
  currentType.value = key
  form.category = typeCategoryMap[key]
}

const onStatusChange = () => {}

const goBack = () => router.push({ name: 'Rules' })

const previewRule = () => {
  if (!form.title || !form.content) {
    ElMessage.warning('请填写规则名称和内容后再预览')
    return
  }
  previewVisible.value = true
}

const validateForm = () => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入规则名称')
    return false
  }
  if (!isCredit.value && !form.content.trim()) {
    ElMessage.warning('请输入规则内容')
    return false
  }
  return true
}

const buildPayload = (overrideStatus) => {
  const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
  return {
    ...form,
    type: isCredit.value ? 'credit' : 'platform',
    category: isCredit.value ? creditCategory.value : form.category,
    content: isCredit.value ? JSON.stringify({ addRules: creditAdds.value, subRules: creditSubs.value }) : form.content,
    status: overrideStatus || form.status,
    statusClass: overrideStatus === 'published' ? 'success' : overrideStatus === 'archived' ? 'default' : 'warning',
    updateTime: now,
    createTime: form.createTime || now
  }
}

const saveDraft = async () => {
  if (!validateForm()) return
  try {
    const payload = buildPayload('draft')
    if (isEdit.value) {
      await updateRules(routeId.value, payload)
    } else {
      await createRules(payload)
    }
    ElMessage.success('已保存草稿')
    goBack()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const saveAndPublish = async () => {
  if (!validateForm()) return
  const targetStatus = form.status === 'published' ? 'published' : (form.status === 'archived' ? 'archived' : 'published')
  try {
    const payload = buildPayload(targetStatus)
    if (isEdit.value) {
      await updateRules(routeId.value, payload)
    } else {
      await createRules(payload)
    }
    ElMessage.success(targetStatus === 'published' ? '规则已发布' : '已保存')
    goBack()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const loadExisting = async () => {
  if (!isEdit.value) {
    const now = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    form.createTime = now
    form.updateTime = now
    return
  }
  try {
    const res = await listRules()
    const list = Array.isArray(res) ? res : (res?.data || [])
    const found = list.find(r => String(r.id) === String(routeId.value))
    if (found) {
      Object.assign(form, {
        id: found.id,
        code: found.code || '',
        title: found.title || '',
        category: found.category || '',
        version: found.version || 'v1.0',
        status: found.status || 'draft',
        content: found.content || '',
        effectiveTime: found.effectiveTime || '',
        expireTime: found.expireTime || '',
        scope: found.scope || 'all',
        pinned: found.pinned || 'no',
        creator: found.creator || '超级管理员',
        createTime: found.createTime || '',
        updateTime: found.updateTime || ''
      })
      if (found.type === 'credit' || found.category === '信用分规则') {
        creditCategory.value = found.category || '信用分规则'
        try {
          const structured = JSON.parse(found.content || '{}')
          if (Array.isArray(structured.addRules)) creditAdds.value = structured.addRules
          if (Array.isArray(structured.subRules)) creditSubs.value = structured.subRules
        } catch (_) { /* 兼容历史纯文本内容 */ }
      }
      if (found.type && typeCategoryMap[found.type]) {
        currentType.value = found.type
      } else if (found.category) {
        // 后端数据无 type 字段时，按分类名称反查规则类型
        const match = Object.entries(typeCategoryMap).find(([, label]) => label === found.category)
        if (match) currentType.value = match[0]
      }
      history.value = [
        { icon: 'fa-plus', title: '创建规则', time: `由 ${form.creator || '管理员'} 创建` },
        ...(found.updateTime ? [{ icon: 'fa-edit', title: '修改内容', time: form.updateTime }] : []),
        ...(found.status === 'published' ? [{ icon: 'fa-check-circle', title: '发布上线', time: form.updateTime }] : [])
      ]
      // 编辑器回显
      await nextTick()
      if (editorRef.value) {
        editorRef.value.innerHTML = form.content || ''
      }
    }
  } catch (e) {
    console.warn('[RulesEdit] 加载失败:', e)
    ElMessage.error('加载规则失败')
  }
}

onMounted(loadExisting)
</script>

<style scoped>
.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  margin-bottom: 16px;
}
.edit-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.edit-header-info h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin: 0 0 4px 0;
}
.edit-header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-muted, #9CA3AF);
}
.edit-id-badge {
  padding: 2px 8px;
  background: var(--bg-page, #F9FAFB);
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
}
.edit-header-actions {
  display: flex;
  gap: 10px;
}

.edit-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  align-items: start;
}
.edit-layout.platform-edit { display: block; }
.platform-edit .left-col { gap: 16px; }
.left-col, .right-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.form-section {
  padding: 20px 24px;
}
.form-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border, #E5E7EB);
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-section-title .section-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary, #FF6B35);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.form-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 24px;
}
.form-group .full-width { grid-column: 1 / -1; }
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #6B7280);
  margin-bottom: 6px;
}
.form-label .required { color: #ef4444; margin-left: 2px; }
.form-hint {
  font-size: 12px;
  color: var(--text-muted, #9CA3AF);
  margin-top: 6px;
}

.rich-text-toolbar {
  display: flex;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--border, #E5E7EB);
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  background: var(--bg-page, #F9FAFB);
  flex-wrap: wrap;
  align-items: center;
}
.rich-text-toolbar button {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: var(--text-secondary, #6B7280);
  transition: all 0.2s;
}
.rich-text-toolbar button:hover {
  background: #fff;
  color: var(--primary, #FF6B35);
}
.rich-text-toolbar .divider {
  width: 1px;
  height: 20px;
  background: var(--border, #E5E7EB);
  margin: 4px 4px;
}
.rich-text-toolbar .toolbar-select {
  height: 32px;
  border: 1px solid var(--border, #E5E7EB);
  border-radius: 4px;
  padding: 0 8px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
}
.content-editor {
  min-height: 340px;
  border: 1px solid var(--border, #E5E7EB);
  border-top: none;
  border-radius: 0 0 6px 6px;
  padding: 12px 16px;
  line-height: 1.7;
  font-size: 14px;
  outline: none;
  overflow-y: auto;
  background: #fff;
}
.content-editor:empty::before {
  content: '请输入规则详细内容...';
  color: var(--text-muted, #9CA3AF);
}
.content-editor :deep(h1) { font-size: 20px; font-weight: 700; margin: 12px 0 8px; }
.content-editor :deep(h2) { font-size: 18px; font-weight: 600; margin: 10px 0 6px; }
.content-editor :deep(h3) { font-size: 16px; font-weight: 600; margin: 8px 0 4px; }
.content-editor :deep(blockquote) {
  border-left: 3px solid var(--border, #E5E7EB);
  padding-left: 12px;
  color: var(--text-secondary, #6B7280);
  margin: 8px 0;
}
.content-editor :deep(ul),
.content-editor :deep(ol) { padding-left: 24px; margin: 8px 0; }
.content-editor :deep(a) { color: var(--primary, #FF6B35); text-decoration: underline; }

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}
.status-badge.draft { background: #FEF3C7; color: #D97706; }
.status-badge.success { background: #D1FAE5; color: #059669; }
.status-badge.archived { background: #E5E7EB; color: #6B7280; }

.action-bar {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 18px 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.info-card { padding: 18px 20px; }
.info-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.info-card-title i { color: var(--primary, #FF6B35); }
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border, #E5E7EB);
  font-size: 13px;
}
.info-row:last-child { border-bottom: none; }
.info-row-label { color: var(--text-muted, #9CA3AF); }
.info-row-value { color: var(--text-primary, #111827); font-weight: 500; }

.tip-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tip-item {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary, #6B7280);
  line-height: 1.5;
}
.tip-item i {
  color: var(--primary, #FF6B35);
  margin-top: 3px;
  flex-shrink: 0;
}

.timeline-list {
  display: flex;
  flex-direction: column;
}
.timeline-item {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border, #E5E7EB);
}
.timeline-item:last-child { border-bottom: none; }
.timeline-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #FFF8F3;
  color: var(--primary, #FF6B35);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
}
.timeline-title { font-size: 13px; font-weight: 500; margin-bottom: 2px; }
.timeline-time { font-size: 12px; color: var(--text-muted, #9CA3AF); }

.preview-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted, #9CA3AF);
  flex-wrap: wrap;
}
.preview-content {
  background: var(--bg-page, #F9FAFB);
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary, #6B7280);
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
}
.credit-edit-header{display:flex;justify-content:space-between;align-items:center;padding:18px 24px;background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.04)}.credit-edit-header h2{margin:0 0 4px;font-size:18px}.credit-edit-header span{font-size:13px;color:#999}.credit-meta-card{margin-top:16px;background:#fff;border:1px solid #eee;border-radius:12px;padding:20px 24px}.credit-section-title{font-size:15px;font-weight:700;margin-bottom:18px}.credit-section-title i{color:#ff6b35;margin-right:7px}.credit-meta-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px 24px}.credit-meta-grid label{display:flex;flex-direction:column;gap:6px;font-size:13px;color:#666}.credit-meta-grid label>span{color:#ff5c33;margin-left:2px}.credit-split{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:16px;padding-bottom:80px}@media(max-width:1100px){.credit-meta-grid{grid-template-columns:repeat(2,1fr)}.credit-split{grid-template-columns:1fr}}@media(max-width:640px){.credit-meta-grid{grid-template-columns:1fr}.credit-edit-header{align-items:flex-start;gap:12px;flex-direction:column}}
</style>
