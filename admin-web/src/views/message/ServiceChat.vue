<template>
  <div class="service-chat-page">
    <div class="breadcrumb">
      <router-link to="/admin/dashboard">首页</router-link><span>›</span>
      <router-link to="/admin/service">客服管理</router-link><span>›</span><span>会话处理</span>
    </div>
    <div class="chat-layout">
      <section class="chat-main">
        <header class="chat-header">
          <div class="chat-header-left">
            <el-button class="back-button" size="small" @click="router.back()"><i class="fas fa-arrow-left"></i></el-button>
            <div class="avatar user-avatar">{{ userInitial }}</div>
            <div class="chat-user-info"><div class="name">{{ userName }} <span :class="['status-badge', isOpen ? 'warning' : 'closed']">{{ isOpen ? '进行中' : '已结束' }}</span></div><div class="meta">会话ID: {{ displaySessionId }} · {{ typeText }}</div></div>
          </div>
          <el-button v-if="isOpen" type="danger" size="small" @click="handleCloseSession"><i class="fas fa-times"></i> 结束会话</el-button>
        </header>
        <div ref="messagesRef" class="chat-body">
          <div v-if="messages.length" class="chat-time-divider"><span>{{ firstMessageDate }}</span></div>
          <div class="msg-system">{{ isOpen ? '用户进入会话，已分配给客服处理' : '该会话已结束' }}</div>
          <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.fromType === 'AGENT' ? 'agent' : 'user']">
            <div :class="['avatar', 'msg-avatar', msg.fromType === 'AGENT' ? 'agent-avatar' : 'user-avatar']">{{ msg.fromType === 'AGENT' ? '客' : userInitial }}</div>
            <div class="msg-content"><div class="msg-bubble">{{ msg.content }}</div><div class="msg-time">{{ formatTime(msg.timestamp) }}</div></div>
          </div>
          <div v-if="messages.length === 0" class="empty-messages"><i class="far fa-comments"></i><span>暂无聊天消息</span></div>
        </div>
        <footer class="chat-input-area">
          <div v-if="quickReplies.length" class="quick-reply-bar">
            <button v-for="reply in quickReplies" :key="reply.id" class="reply-chip" type="button" :disabled="!isOpen" @click="fillQuickReply(reply.content)">{{ reply.category || reply.content }}</button>
          </div>
          <div class="input-box">
            <div class="input-tools"><button v-for="tool in inputTools" :key="tool.icon" type="button" :title="`${tool.label}（暂未开放）`" @click="showComingSoon(tool.label)"><i :class="tool.icon"></i></button></div>
            <el-input v-model="inputMsg" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" resize="none" placeholder="请输入回复内容... (Enter发送，Shift+Enter换行)" :disabled="!isOpen" @keyup.enter.exact.prevent="handleSend" />
            <el-button type="primary" class="send-button" :disabled="!isOpen || !inputMsg.trim()" @click="handleSend">发送</el-button>
          </div>
        </footer>
      </section>
      <aside class="chat-sidebar">
        <div class="info-card details-card">
          <section class="info-section">
            <h3><i class="fas fa-user"></i> 用户信息</h3>
            <div class="info-row"><span>姓名</span><strong>{{ userName }}</strong></div>
            <div class="info-row"><span>手机号</span><strong>{{ maskedPhone }}</strong></div>
            <div class="info-row"><span>零工等级</span><strong><span :class="['level-badge', realnameVerified ? 'verified' : 'unverified']">{{ realnameStatusText }}</span></strong></div>
            <div class="info-row"><span>信用评分</span><strong>{{ creditScoreText }}</strong></div>
          </section>
          <section class="info-section">
            <h3><i class="fas fa-file-alt"></i> 相关订单</h3>
            <div v-if="relatedOrders.length" class="related-order-list">
              <div v-for="order in relatedOrders" :key="order.itemId || order.orderId" class="related-order">
                <div class="related-order-icon"><i class="fas fa-briefcase"></i></div>
                <div class="related-order-info">
                  <div class="order-id">{{ order.orderNo || `订单${order.orderId}` }}</div>
                  <div class="order-desc">{{ orderDescription(order) }}</div>
                  <div class="order-meta">{{ order.status || '-' }} · {{ orderDate(order.startTime) }}</div>
                </div>
              </div>
            </div>
            <div v-else class="empty-info"><i class="fas fa-briefcase"></i><span>暂无关联订单数据</span></div>
          </section>
          <section class="info-section"><h3><i class="fas fa-history"></i> 历史会话</h3><div class="empty-info compact">暂无历史会话数据</div></section>
        </div>
        <div v-if="isOpen" class="info-card action-card"><section class="info-section"><h3><i class="fas fa-cog"></i> 会话操作</h3><div class="type-actions"><el-button :type="session?.type === 'consult' ? 'primary' : 'default'" class="action-button" @click="handleUpdateType('consult')"><i class="fas fa-comments"></i> 标记咨询</el-button><el-button :type="session?.type === 'complaint' ? 'danger' : 'default'" class="action-button" @click="handleUpdateType('complaint')"><i class="fas fa-flag"></i> 标记投诉</el-button></div><el-button type="primary" class="action-button" :disabled="!isOpen" @click="handleCloseSession"><i class="fas fa-check-circle"></i> 标记已解决</el-button></section></div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeServiceSession, getQuickReplies, getServiceSession, getSessionMessages, sendAgentMessage, updateServiceSessionType } from '@/api/service'
import wsClient from '@/api/websocket'

const route = useRoute(); const router = useRouter(); const sessionId = Number(route.params.id)
const session = ref(null); const messages = ref([]); const inputMsg = ref(''); const messagesRef = ref(null); const quickReplies = ref([])
const inputTools = [{ icon: 'fas fa-image', label: '发送图片' }, { icon: 'fas fa-paperclip', label: '发送文件' }, { icon: 'fas fa-bookmark', label: '快捷回复' }, { icon: 'fas fa-share', label: '转接会话' }]
const userName = computed(() => session.value?.userName || `用户${session.value?.userId || ''}`); const userInitial = computed(() => String(session.value?.userName || session.value?.userId || '用').charAt(0)); const typeText = computed(() => ({ consult: '咨询', complaint: '投诉', other: '其他' }[session.value?.type] || '其他'))
const maskedPhone = computed(() => {
  const phone = session.value?.userPhone ? String(session.value.userPhone) : ''
  if (!phone) return '—'
  return /^\d{11}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
})
const realnameVerified = computed(() => ['APPROVED', '已通过', '已认证', 'VERIFIED'].includes(session.value?.realnameStatus))
const realnameStatusText = computed(() => realnameVerified.value ? '认证' : ({ PENDING: '待审核', REJECTED: '已拒绝' }[session.value?.realnameStatus] || '未认证'))
const creditScoreText = computed(() => {
  const rawScore = session.value?.creditScore
  if (rawScore === null || rawScore === undefined || rawScore === '') return '—'
  const score = Number(rawScore)
  return Number.isFinite(score) ? `${score}分` : '—'
})
const relatedOrders = computed(() => session.value?.relatedOrders || [])
const orderDescription = order => [
  ...new Set([order.title, order.position].filter(Boolean)),
  order.salary === null || order.salary === undefined ? null : `${order.salary}元`,
].filter(Boolean).join(' · ') || '岗位信息待补充'
const orderDate = value => {
  if (!value) return '时间待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待定'
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const handleUpdateType = async type => {
  try {
    const res = await updateServiceSessionType(sessionId, type)
    if (res?.data) session.value = { ...session.value, ...res.data }
    ElMessage.success(type === 'complaint' ? '已标记为投诉' : '已标记为咨询')
  } catch (e) { ElMessage.error(e?.message || '更新对话类型失败') }
}
const isOpen = computed(() => session.value?.status === 'OPEN'); const displaySessionId = computed(() => Number.isFinite(sessionId) ? `S${String(sessionId).padStart(11, '0')}` : '—')
const formatTime = ts => { if (!ts) return ''; const d = new Date(ts); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }
const firstMessageDate = computed(() => { const ts = messages.value[0]?.timestamp; if (!ts) return '本次会话'; const d = new Date(ts); return `${d.toDateString() === new Date().toDateString() ? '今天' : `${d.getMonth() + 1}月${d.getDate()}日`} ${formatTime(ts)}` })
const scrollToBottom = () => nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight })
const loadSession = async () => { try { session.value = (await getServiceSession(sessionId)).data } catch (e) { console.error('加载会话失败:', e); ElMessage.error('会话信息加载失败') } }
const loadMessages = async () => { try { messages.value = (await getSessionMessages(sessionId, { page: 0, size: 100 })).data || []; scrollToBottom() } catch (e) { console.error('加载消息失败:', e) } }
const loadQuickReplies = async () => { try { quickReplies.value = ((await getQuickReplies()).data || []).filter(r => r.enabled !== false) } catch (e) { console.error('加载快捷回复失败:', e) } }
const handleSend = async () => { const content = inputMsg.value.trim(); if (!content || !isOpen.value) return; const sent = wsClient.send({ type: 'MESSAGE', sessionId, content, contentType: 'TEXT' }); if (sent) { messages.value.push({ id: Date.now(), sessionId, fromId: 'agent', fromType: 'AGENT', content, contentType: 'TEXT', timestamp: new Date().toISOString() }); scrollToBottom() } else { try { await sendAgentMessage(sessionId, { content }); await loadMessages() } catch (e) { ElMessage.error('发送失败'); return } } inputMsg.value = '' }
const fillQuickReply = content => { inputMsg.value = content }
const showComingSoon = feature => ElMessage.info(`${feature}功能暂未开放`)
const handleCloseSession = async () => { if (!isOpen.value) return; try { await ElMessageBox.confirm('确定要结束此会话吗？', '提示', { type: 'warning' }); await closeServiceSession(sessionId); ElMessage.success('会话已结束'); await loadSession() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error('结束会话失败') } }
const onWsMessage = data => { if (Number(data.sessionId) !== sessionId) return; if (data.type === 'MESSAGE') { messages.value.push({ id: data.messageId || Date.now(), sessionId: data.sessionId, fromId: data.fromId, fromType: data.fromType, content: data.content, contentType: data.contentType, timestamp: data.timestamp }); scrollToBottom() } else if (data.type === 'SESSION_CLOSED') { if (session.value) session.value.status = 'CLOSED'; ElMessage.info('会话已结束') } }
onMounted(async () => { if (!Number.isFinite(sessionId)) { ElMessage.error('会话编号无效'); router.replace('/admin/service'); return } await Promise.all([loadSession(), loadMessages(), loadQuickReplies()]); wsClient.connect(localStorage.getItem('admin_user_id') || '1', 'AGENT'); wsClient.on('MESSAGE', onWsMessage); wsClient.on('SESSION_CLOSED', onWsMessage) })
onUnmounted(() => { wsClient.off('MESSAGE', onWsMessage); wsClient.off('SESSION_CLOSED', onWsMessage); wsClient.close() })
</script>

<style scoped>
.service-chat-page{min-height:calc(100vh - 72px);background:#f5f6f8;margin:-24px;padding:12px 24px 24px;color:#1f2937}.breadcrumb{display:flex;align-items:center;gap:8px;height:32px;color:#9ca3af;font-size:13px}.breadcrumb a{color:#6b7280;text-decoration:none}.breadcrumb a:hover{color:#ff6b35}.chat-layout{display:flex;gap:16px;height:calc(100vh - 140px);min-height:560px}.chat-main,.info-card{background:#fff;border-radius:12px;box-shadow:0 1px 3px rgba(0,0,0,.05)}.chat-main{flex:1;min-width:0;display:flex;flex-direction:column;overflow:hidden}.chat-header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 20px;border-bottom:1px solid #e5e7eb}.chat-header-left{display:flex;align-items:center;gap:12px;min-width:0}.back-button{width:32px;padding:0}.avatar{display:flex;align-items:center;justify-content:center;border-radius:50%;color:#fff;font-weight:600;flex-shrink:0}.user-avatar{background:linear-gradient(135deg,#ff8c42,#ff6b35)}.agent-avatar{background:linear-gradient(135deg,#2563eb,#1e40af)}.chat-header .avatar{width:40px;height:40px;font-size:15px}.chat-user-info{min-width:0}.chat-user-info .name{font-size:15px;font-weight:600;white-space:nowrap}.chat-user-info .meta{margin-top:3px;color:#9ca3af;font-size:12px}.status-badge{display:inline-block;margin-left:6px;padding:2px 7px;border-radius:10px;font-size:11px;font-weight:500}.status-badge.warning{color:#d97706;background:#fef3c7}.status-badge.closed{color:#6b7280;background:#f3f4f6}.chat-body{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:16px;padding:20px;background:#fafafa}.chat-time-divider{text-align:center;color:#9ca3af;font-size:12px;margin:8px 0}.chat-time-divider span{padding:3px 10px;border-radius:10px;background:#e5e7eb}.msg-system{align-self:center;padding:5px 12px;border-radius:12px;background:#f3f4f6;color:#6b7280;font-size:12px}.msg{display:flex;gap:10px;max-width:75%}.msg.user{align-self:flex-start}.msg.agent{align-self:flex-end;flex-direction:row-reverse}.msg-avatar{width:34px;height:34px;font-size:13px}.msg-content{min-width:0}.msg-bubble{padding:10px 14px;border-radius:10px;font-size:14px;line-height:1.5;white-space:pre-wrap;word-break:break-word}.msg.user .msg-bubble{background:#fff;border:1px solid #e5e7eb;border-top-left-radius:4px}.msg.agent .msg-bubble{background:#ff6b35;color:#fff;border-top-right-radius:4px}.msg-time{margin-top:4px;color:#9ca3af;font-size:11px}.msg.agent .msg-time{text-align:right}.empty-messages{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#c1c5cc}.empty-messages i{font-size:34px}.chat-input-area{flex-shrink:0;padding:12px 16px;border-top:1px solid #e5e7eb;background:#fff}.quick-reply-bar{display:flex;gap:6px;overflow-x:auto;margin-bottom:10px;padding-bottom:4px}.reply-chip{flex-shrink:0;max-width:180px;overflow:hidden;padding:5px 12px;border:1px solid #e5e7eb;border-radius:16px;background:#fff;color:#4b5563;font-size:12px;text-overflow:ellipsis;white-space:nowrap;cursor:pointer}.reply-chip:hover:not(:disabled){border-color:#ff6b35;color:#ff6b35}.reply-chip:disabled{cursor:not-allowed;opacity:.55}.input-box{display:flex;align-items:flex-end;gap:10px}.input-tools{display:flex;gap:4px;padding:3px 0}.input-tools button{width:32px;height:32px;border:0;border-radius:6px;background:transparent;color:#9ca3af;cursor:pointer}.input-tools button:hover{background:#f5f6f8;color:#ff6b35}.input-box :deep(.el-textarea){flex:1}.input-box :deep(.el-textarea__inner){min-height:36px!important;padding:8px 12px;border-radius:8px;box-shadow:0 0 0 1px #e5e7eb inset}.input-box :deep(.el-textarea__inner:focus){box-shadow:0 0 0 1px #ff6b35 inset}.send-button{height:36px;padding:0 20px}.chat-sidebar{width:300px;flex-shrink:0;display:flex;flex-direction:column;gap:16px}.details-card{flex:1;min-height:0;overflow-y:auto}.action-card{flex:0 0 auto}.info-section{padding:14px 16px;border-bottom:1px solid #e5e7eb}.info-section:last-child{border-bottom:0}.info-section h3{display:flex;align-items:center;gap:7px;margin:0 0 12px;color:#374151;font-size:14px}.info-section h3 i{width:16px;color:#ff6b35;text-align:center}.info-row{display:flex;justify-content:space-between;gap:12px;margin-top:10px;font-size:12px}.info-row span{color:#9ca3af}.info-row strong{overflow:hidden;color:#374151;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.empty-info{min-height:58px;display:flex;align-items:center;justify-content:center;gap:8px;border-radius:6px;background:#f8f9fa;color:#9ca3af;font-size:12px}.empty-info i{font-size:18px}.empty-info.compact{min-height:42px}.action-button{width:100%;margin:0 0 8px!important}.action-button:last-child{margin-bottom:0!important}@media(max-width:1050px){.chat-sidebar{width:250px}.input-tools{display:none}}@media(max-width:800px){.service-chat-page{margin:-16px;padding:8px 16px 16px}.chat-layout{height:auto;min-height:calc(100vh - 110px);flex-direction:column}.chat-main{min-height:620px}.chat-sidebar{width:100%}}
.type-actions{display:flex;gap:8px}.type-actions .el-button+.el-button{margin-left:0}
.info-section h3{margin-bottom:8px;color:#9ca3af;font-size:12px}.info-section h3 i{width:auto;color:inherit}.info-row{margin-top:0;padding:4px 0;font-size:13px}.info-row strong{display:flex;justify-content:flex-end}
.level-badge{display:inline-flex;align-items:center;gap:4px;padding:3px 9px;border-radius:999px;font-size:11px;font-weight:500}.level-badge::before{content:'';width:6px;height:6px;border-radius:50%;background:currentColor}.level-badge.verified{background:#eff6ff;color:#2563eb}.level-badge.unverified{background:#f3f4f6;color:#6b7280}
.related-order-list{display:flex;flex-direction:column;gap:8px}.related-order{display:flex;align-items:flex-start;gap:10px;padding:8px;border-radius:8px;background:#f5f6f8;transition:background .2s}.related-order:hover{background:#fff3ed}.related-order-icon{width:32px;height:32px;flex-shrink:0;display:flex;align-items:center;justify-content:center;border-radius:6px;background:#fff;color:var(--primary,#ff6b35)}.related-order-info{min-width:0;flex:1}.order-id{overflow:hidden;color:var(--primary,#ff6b35);font-size:12px;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.order-desc{overflow:hidden;color:#374151;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.order-meta{margin-top:2px;color:#9ca3af;font-size:11px}
</style>
