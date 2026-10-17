<template>
  <view class="service-chat-panel">
    <view class="chat-header" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="header-action" @click="goBack">‹</view>
      <view class="header-avatar">🎧</view>
      <view class="header-info"><text class="header-name">快马日结小助手</text><view class="header-status"><text class="status-dot" /><text>{{ connected ? "在线 · 平均1分钟回复" : "连接中…" }}</text></view></view>
      <view class="header-action more">•••</view>
    </view>
    <view v-if="showSecurity" class="security-tip"><text class="shield">◆</text><text class="security-text">平台禁止飞单行为，检测到将进行封号处理</text><text class="security-close" @click="showSecurity = false">×</text></view>
    <scroll-view scroll-y class="chat-messages" :scroll-top="scrollTop" :show-scrollbar="false">
      <text class="msg-time">{{ currentTimeLabel }}</text>
      <view class="msg-row"><view class="msg-avatar service">🎧</view><view class="msg-content"><view class="msg-bubble">您好，请问有什么可以帮助您？</view></view></view>
      <view class="msg-row"><view class="msg-avatar service">🎧</view><view class="msg-content hot-content"><view class="hot-questions"><text class="hot-title">常见问题</text><view v-for="item in displayedFaqs" :key="item.id" class="hot-item faq" @click="consultFaq(item)"><text class="hot-dot">●</text><text class="faq-question">{{ item.question }}</text></view><view v-if="!displayedFaqs.length" class="faq-empty">暂未配置常见问题</view><view class="hot-item human" @click="sendText('转人工客服')"><text>🎧</text><text>人工客服</text></view></view></view></view>
      <view v-for="(item, index) in messages" :id="`msg-${index}`" :key="item.id || item.messageId || index" :class="['msg-row', { self: item.self }]"><view :class="['msg-avatar', item.self ? 'user' : 'service']">{{ item.self ? "我" : "🎧" }}</view><view class="msg-content"><view :class="['msg-bubble', { 'self-bubble': item.self }]">{{ item.text }}</view><text v-if="item.time" class="message-time">{{ item.time }}</text></view></view>
      <view v-if="sessionClosed" class="msg-row system-notice"><view class="msg-avatar service">🎧</view><view class="msg-content"><view class="msg-bubble">本次客服会话已结束，如有其他问题请重新发起咨询</view></view></view>
      <view class="scroll-space" />
    </scroll-view>
    <view class="composer" :style="{ paddingBottom: `${bottomInset + 12}px` }">
      <template v-if="sessionClosed">
        <button class="send-button reopen" @click="reopenSession">重新发起会话</button>
      </template>
      <template v-else>
        <view class="voice-button">◉</view>
        <input v-model="input" class="input-field" confirm-type="send" placeholder="请输入您的问题" @confirm="send" />
        <button class="send-button" :disabled="!input.trim() || sending" @click="send">发送</button>
      </template>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { createChatSession, listChatMessages, listMyChatSessions, sendChatMessage } from "@/api/backend";
import wsClient from "@/api/chat-websocket";

const props = defineProps({
  hotQuestions: { type: Array, default: () => [] },
  fallbackPath: { type: String, default: "" },
  initialInput: { type: String, default: "" },
});

const system = uni.getSystemInfoSync();
const statusBarHeight = system.statusBarHeight || 0;
const bottomInset = system.safeAreaInsets?.bottom || 0;
const input = ref("");
const userId = String(uni.getStorageSync("userId") || "2001");
const sessionId = ref("");
const sending = ref(false);
const sessionClosed = ref(false);
const connected = ref(false);
const showSecurity = ref(true);
const messages = ref([]);
let wsBaseUrl = resolveWsBaseUrl();
const hotQuestions = computed(() => props.hotQuestions);
const displayedFaqs = computed(() => props.hotQuestions.map((item, index) => {
  if (typeof item === "string") return { id: `hot-${index}`, question: item, answer: "" };
  return {
    id: item.id || `hot-${index}`,
    question: String(item.question || "").trim(),
    answer: String(item.answer || "").trim(),
  };
}).filter((item) => item.question));
const scrollTop = ref(0);
const currentTimeLabel = computed(() => { const now = new Date(); return `今天 ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`; });

let syncTimer = null;

onMounted(() => {
  input.value = props.initialInput || "";
  loadSession();
});
onUnmounted(() => {
  stopMessageSync();
  removeSocketListeners();
  wsClient.close();
});

async function loadSession() {
  try {
    const session = await createChatSession({ userId: Number(userId) });
    sessionId.value = String(session.sessionId || session.id || "");
    if (!sessionId.value) throw new Error("客服会话创建失败");
    await refreshMessages(true);
    connectIm();
    startMessageSync();
  } catch (error) { uni.showToast({ title: error.message || "客服连接失败", icon: "none" }); }
}
function resolveWsBaseUrl() {
  const explicit = import.meta.env.VITE_WS_BASE_URL;
  if (explicit) return explicit.replace(/\/$/, "");
  const configured = import.meta.env.VITE_MP_API_BASE_URL || import.meta.env.VITE_PROXY_TARGET || "http://127.0.0.1:8080";
  return configured.replace(/^https:/, "wss:").replace(/^http:/, "ws:").replace(/\/$/, "");
}
function removeSocketListeners() {
  wsClient.off("open", handleOpen);
  wsClient.off("close", handleClose);
  wsClient.off("MESSAGE", handleSocketMessage);
  wsClient.off("message", handleSocketMessage);
  wsClient.off("SESSION_CLOSED", handleSocketMessage);
  if (scrollTimer) clearTimeout(scrollTimer);
}
function connectIm() {
  removeSocketListeners();
  wsClient.on("open", handleOpen);
  wsClient.on("close", handleClose);
  wsClient.on("MESSAGE", handleSocketMessage);
  wsClient.on("message", handleSocketMessage);
  wsClient.on("SESSION_CLOSED", handleSocketMessage);
  if (wsClient.connected) return;
  wsClient.connect(wsBaseUrl, userId, "USER");
}
function handleOpen() { connected.value = true; wsClient.send({ type: "JOIN", sessionId: Number(sessionId.value) }); stopMessageSync(); }
function handleClose() { connected.value = false; startMessageSync(); }
function handleSocketMessage(data) {
  if (data?.sessionId && String(data.sessionId) !== String(sessionId.value)) return;
  if (data?.type === "SESSION_CLOSED") {
    sessionClosed.value = true;
    stopMessageSync();
    uni.showToast({ title: "客服已结束会话", icon: "none" });
    return;
  }
  if (data?.type && data.type !== "MESSAGE") return;
  appendMessage(data);
}
async function refreshMessages(initial = false) {
  if (!sessionId.value) return;
  try {
    // 轮询兜底时同步感知会话状态：WS 不在线/跨实例收不到 SESSION_CLOSED 时也能锁定输入。
    if (!connected.value && !sessionClosed.value) {
      const sessions = await listMyChatSessions(userId);
      const current = (Array.isArray(sessions) ? sessions : sessions?.data || [])
        .find(item => String(item.id) === String(sessionId.value));
      if (current && current.status === "CLOSED") {
        sessionClosed.value = true;
        stopMessageSync();
        uni.showToast({ title: "客服已结束会话", icon: "none" });
      }
    }
    const result = await listChatMessages(sessionId.value, { page: 0, size: 50 });
    const rows = Array.isArray(result) ? result : result?.records || result?.content || [];
    const incoming = rows.map(normalizeMessage).filter((item) => item.text);
    if (initial) messages.value = incoming;
    else incoming.forEach(appendMessage);
    if (initial && incoming.length) scrollToLatest();
  } catch (_) {
    // WebSocket 可用时，历史同步失败不阻断实时聊天。
  }
}
function appendMessage(item) {
  const normalized = normalizeMessage(item);
  if (!normalized.text) return;
  const key = String(normalized.id || normalized.messageId || "");
  if (key && messages.value.some((message) => String(message.id || message.messageId || "") === key)) return;
  messages.value.push(normalized);
  scrollToLatest();
}
let scrollTimer = null;
function scrollToLatest() {
  // 固定一个大值即可触底；先归零再跳大值会让 scroll-view 产生两次重排，表现为页面闪烁。
  if (scrollTimer) clearTimeout(scrollTimer);
  scrollTimer = setTimeout(() => { scrollTop.value = scrollTop.value >= 999999 ? 999998 : 999999; }, 30);
}
function startMessageSync() {
  stopMessageSync();
  // WebSocket 已连接时由实时消息驱动，不再轮询；避免定时刷新与 WS 消息叠加导致闪烁。
  if (connected.value) return;
  syncTimer = setInterval(() => refreshMessages(), 3000);
}
function stopMessageSync() {
  if (syncTimer) clearInterval(syncTimer);
  syncTimer = null;
}
function normalizeMessage(item = {}) { const timestamp = item.timestamp || item.createTime; return { ...item, id: item.id || item.messageId, text: item.content || item.text || "", self: String(item.fromId) === userId || String(item.fromType || "").toUpperCase() === "USER", time: timestamp ? formatTime(timestamp) : "" }; }
function formatTime(value) { const date = new Date(value); if (Number.isNaN(date.getTime())) return ""; return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`; }
function consultFaq(item) {
  if (!item.answer) return sendText(item.question);
  appendMessage({
    id: `faq-question-${item.id}`,
    fromId: userId,
    content: item.question,
    timestamp: Date.now(),
  });
  appendMessage({
    id: `faq-answer-${item.id}`,
    fromType: "SERVICE",
    content: item.answer,
    timestamp: Date.now(),
  });
}
async function reopenSession() {
  try {
    const session = await createChatSession({ userId: Number(userId) });
    sessionId.value = String(session.sessionId || session.id || "");
    if (!sessionId.value) throw new Error("客服会话创建失败");
    messages.value = [];
    sessionClosed.value = false;
    connected.value = false;
    await refreshMessages(true);
    connectIm();
    uni.showToast({ title: "已重新接入客服", icon: "success" });
  } catch (error) {
    uni.showToast({ title: error.message || "重新发起会话失败", icon: "none" });
  }
}

function sendText(text) { if (sessionClosed.value) return; input.value = text; send(); }
async function send() {
  const text = input.value.trim(); if (!text || sending.value || sessionClosed.value) return;
  if (!sessionId.value) return uni.showToast({ title: "客服会话尚未建立", icon: "none" });
  sending.value = true; input.value = "";
  const sentBySocket = wsClient.send({ type: "MESSAGE", sessionId: Number(sessionId.value), content: text, contentType: "TEXT" });
  if (sentBySocket) { sending.value = false; scrollToLatest(); return; }
  try { const result = await sendChatMessage(sessionId.value, { fromId: Number(userId), content: text }); if (result) { messages.value.push(normalizeMessage(result)); scrollToLatest(); } } catch (error) { input.value = text; uni.showToast({ title: error.message || "发送失败", icon: "none" }); } finally { sending.value = false; }
}
function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: props.fallbackPath }) });
}

function resume() {
  if (!sessionId.value) return;
  connectIm();
  refreshMessages();
  startMessageSync();
}

defineExpose({ resume, pause: stopMessageSync });
</script>

<style scoped>
.service-chat-panel{height:100vh;display:flex;flex-direction:column;background:#f5f5f5;overflow:hidden}.chat-header{flex:none;display:flex;align-items:center;gap:18rpx;padding-left:20rpx;padding-right:20rpx;padding-bottom:22rpx;background:linear-gradient(135deg,#ff6b35,#ff8c5a);color:#fff}.header-action{width:56rpx;height:56rpx;display:flex;align-items:center;justify-content:center;font-size:50rpx;line-height:1}.header-action.more{font-size:28rpx;letter-spacing:2rpx}.header-avatar{width:80rpx;height:80rpx;display:flex;align-items:center;justify-content:center;flex-shrink:0;border-radius:50%;background:#fff;font-size:36rpx}.header-info{flex:1;min-width:0}.header-name{display:block;font-size:30rpx;font-weight:600}.header-status{display:flex;align-items:center;gap:8rpx;margin-top:6rpx;color:rgba(255,255,255,.85);font-size:22rpx}.status-dot{width:12rpx;height:12rpx;border-radius:50%;background:#52c41a}.security-tip{flex:none;display:flex;align-items:center;gap:12rpx;padding:16rpx 28rpx;border-bottom:1rpx solid #ffe4a0;background:#fff8e6;color:#b8860b;font-size:22rpx}.shield{color:#fa8c16;font-size:18rpx}.security-text{flex:1}.security-close{padding:0 8rpx;color:#fa8c16;font-size:34rpx}.chat-messages{flex:1;min-height:0;width:100%;height:0;padding:22rpx 28rpx 170rpx;box-sizing:border-box;background:#f5f5f5}.msg-time{display:block;margin:14rpx 0 24rpx;text-align:center;color:#999;font-size:20rpx}.msg-row{display:flex;align-items:flex-start;gap:16rpx;margin-bottom:28rpx}.msg-row.self{flex-direction:row-reverse}.msg-avatar{width:64rpx;height:64rpx;display:flex;align-items:center;justify-content:center;flex-shrink:0;border-radius:50%;color:#fff;font-size:25rpx}.msg-avatar.service{background:linear-gradient(135deg,#ff6b35,#ff8c5a)}.msg-avatar.user{background:#52c41a}.msg-content{max-width:75%}.hot-content{width:calc(100% - 80rpx);max-width:calc(100% - 80rpx)}.msg-bubble{padding:22rpx 26rpx;border-radius:8rpx 24rpx 24rpx;background:#fff;color:#333;font-size:26rpx;line-height:1.6;word-break:break-all}.self-bubble{border-radius:24rpx 8rpx 24rpx 24rpx;background:linear-gradient(135deg,#ff6b35,#ff8c5a);color:#fff}.message-time{display:block;margin-top:6rpx;color:#aaa;font-size:18rpx}.self .message-time{text-align:right}.hot-questions{padding:26rpx;border-radius:24rpx;background:#fff}.hot-title{display:block;margin-bottom:16rpx;color:#333;font-size:27rpx;font-weight:600}.hot-item{display:flex;align-items:flex-start;gap:12rpx;margin-top:14rpx;padding:18rpx 20rpx;border-radius:16rpx;background:#fff8f5;color:#ff6b35;font-size:23rpx;line-height:1.45}.hot-dot{padding-top:5rpx;font-size:12rpx}.hot-item.human{align-items:center;justify-content:center;background:#f5f5f5;color:#666}.scroll-space{height:30rpx}.composer{position:fixed;z-index:10;left:0;right:0;bottom:0;display:flex;align-items:center;gap:14rpx;padding:18rpx 24rpx 12rpx;border-top:1rpx solid #eee;background:#fff;box-sizing:border-box}.voice-button{width:64rpx;height:64rpx;display:flex;align-items:center;justify-content:center;color:#666;font-size:34rpx}.input-field{flex:1;height:76rpx;padding:0 26rpx;border-radius:40rpx;background:#f5f5f5;font-size:25rpx}.send-button{width:116rpx;height:76rpx;margin:0;padding:0;border:0;border-radius:40rpx;background:linear-gradient(135deg,#ff6b35,#ff8c5a);color:#fff;font-size:25rpx;line-height:76rpx}.send-button::after{border:0}.send-button[disabled]{opacity:.5}
.system-notice .msg-bubble { background: #f3f4f6; color: #6b7280; }
.send-button.reopen { width: 100%; }
.faq-question { flex: 1; color: #333; }
.faq-empty { margin-top: 14rpx; color: #999; font-size: 22rpx; }
</style>
