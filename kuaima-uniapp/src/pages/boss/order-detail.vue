<template>
  <view class="page">
    <AppNavBar title="招工详情" :show-back="true" />
    <scroll-view scroll-y class="content">
      <view v-if="loading" class="state">详情加载中…</view>
      <view v-else-if="error" class="state error" @click="loadDetail">加载失败，点击重试</view>
      <template v-else>
        <view class="status-header">
          <text class="status-badge" :class="statusClass">● {{ detail.statusText }}</text>
          <text class="job-title">{{ detail.title }}</text>
          <view class="job-meta"><text>◷ {{ detail.workDate }}</text><text>⌖ {{ detail.shortAddress }}</text></view>
          <view class="wage-main"><text class="symbol">¥</text><text class="wage">{{ detail.salary }}</text><text class="unit">{{ detail.salaryUnit }}</text><text class="settlement">· {{ detail.settlementLabel }}</text></view>
        </view>

        <view class="info-card">
          <text class="card-title">▤ 招工信息</text>
          <InfoRow icon="▣" label="招聘岗位" :value="detail.title" />
          <InfoRow icon="◷" theme="blue" label="工作时间" :value="detail.timeText" />
          <InfoRow icon="⌖" theme="green" label="工作地点" :value="detail.address" />
          <InfoRow icon="♟" theme="purple" label="招募人数" :value="detail.peopleText" />
          <InfoRow icon="¥" label="报酬说明" :value="detail.salaryDescription" />
        </view>

        <view class="info-card">
          <text class="card-title">☷ 干活内容</text>
          <text class="description">{{ detail.orderContent || "暂无干活内容" }}</text>
        </view>

        <view class="info-card">
          <view class="card-title title-row"><text>✓ 报名情况</text><text class="apply-total">共 {{ applicants.length }} 人报名</text></view>
          <view v-if="applicants.length" class="apply-list">
            <view v-for="item in previewApplicants" :key="item.id" class="apply-item">
              <view class="avatar">{{ item.name.charAt(0) }}</view>
              <view class="apply-info"><text class="apply-name">{{ item.name }}</text><text class="apply-sub">{{ item.subText }}</text></view>
              <text class="apply-status" :class="item.statusClass">{{ item.status }}</text>
            </view>
            <view class="apply-more" @click="openApplicants">查看全部报名 ›</view>
          </view>
          <view v-else class="empty-inline">暂无报名人员</view>
        </view>

        <view class="info-card">
          <text class="card-title">⌁ 招工进度</text>
          <view class="timeline">
            <view v-for="item in timeline" :key="item.title" class="timeline-item" :class="{ pending: item.pending }">
              <text class="timeline-title">{{ item.title }}</text><text class="timeline-time">{{ item.time }}</text>
            </view>
          </view>
        </view>
        <view class="scroll-space"></view>
      </template>
    </scroll-view>

    <view v-if="!loading && !error && mainAction" class="bottom-bar"><button class="main-button" @click="handleMainAction">{{ mainAction.label }}</button></view>
  </view>
</template>

<script setup>
import { computed, defineComponent, h, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppNavBar from "@/components/AppNavBar.vue";
import { getOrder, listOrderItems } from "@/api/backend";

const InfoRow = defineComponent({
  props: { icon: String, label: String, value: String, theme: String },
  setup(props) { return () => h("view", { class: "info-row" }, [h("view", { class: ["info-icon", props.theme] }, props.icon), h("view", { class: "info-content" }, [h("text", { class: "info-label" }, props.label), h("text", { class: "info-value" }, props.value || "--")])]); },
});

const detail = ref({});
const applicants = ref([]);
const loading = ref(false);
const error = ref(false);
let orderId = "";

const previewApplicants = computed(() => applicants.value.slice(0, 4));
const statusClass = computed(() => ({ "招工中": "recruiting", "招工结束": "ended", "待结算": "settling", "已完成": "completed" }[detail.value.orderStatus] || "cancelled"));
const mainAction = computed(() => {
  if (["待审核", "招工中"].includes(detail.value.orderStatus)) return { label: "编辑招工", type: "edit" };
  if (detail.value.orderStatus === "招工结束") return { label: "确认到岗", type: "arrive" };
  if (detail.value.orderStatus === "待结算") return { label: "去结算", type: "settle" };
  return null;
});
const timeline = computed(() => {
  const firstApply = applicants.value.map(item => item.applyDate).filter(Boolean).sort()[0];
  const arrived = applicants.value.filter(item => ["已到岗", "已完成"].includes(item.rawStatus));
  return [
    { title: "发布招工", time: detail.value.createTime || "时间暂无" },
    { title: "有人报名", time: firstApply ? `${formatDateTime(firstApply)} · 共 ${applicants.value.length} 人报名` : "暂无报名", pending: !firstApply },
    { title: "招工截止", time: detail.value.startTimeText || "时间暂无", pending: !detail.value.startTimeText },
    { title: "工人到岗", time: arrived.length ? `${arrived.length} 人已到岗` : "等待确认到岗", pending: !arrived.length },
    { title: "完工结算", time: detail.value.endTimeText ? `${detail.value.endTimeText} 后（预计）` : "时间暂无", pending: !["已完成"].includes(detail.value.orderStatus) },
  ];
});

onLoad(options => { orderId = options?.id || ""; loadDetail(); });

async function loadDetail() {
  if (!orderId) { error.value = true; return; }
  loading.value = true; error.value = false;
  try {
    const [order, itemResult] = await Promise.all([getOrder(orderId), listOrderItems(orderId).catch(() => [])]);
    const items = Array.isArray(itemResult) ? itemResult : itemResult?.records || itemResult?.content || [];
    detail.value = normalizeDetail(order || {});
    applicants.value = items.map(normalizeApplicant);
  } catch (e) { error.value = true; uni.showToast({ title: e.message || "招工详情加载失败", icon: "none" }); }
  finally { loading.value = false; }
}

function normalizeDetail(row) {
  const hourly = String(row.tags || "").match(/时薪:([\d.]+)/);
  const piece = String(row.tags || "").match(/计件单价:([\d.]+)/);
  const pieceUnit = String(row.tags || "").match(/计件单位:([^,]+)/);
  const salary = hourly?.[1] || piece?.[1] || row.salary || 0;
  const salaryUnit = hourly ? "/小时" : piece ? `/${pieceUnit?.[1] || "件"}` : row.type === "month" ? "/月" : "/天";
  const settlementLabel = row.type === "month" ? "月结" : row.type === "heldBack" ? "压薪日结" : "日结";
  const address = row.address || row.workAddress || "地点待定";
  return { ...row, title: row.orderTitle || row.postion || "未命名岗位", orderStatus: row.orderStatus || "待审核", statusText: statusText(row.orderStatus), salary, salaryUnit, settlementLabel, workDate: formatDay(row.startTime), shortAddress: address.length > 10 ? `${address.slice(0, 10)}…` : address, address, timeText: `${formatFullTime(row.startTime)} ~ ${formatClock(row.endTime)}`, peopleText: `需要 ${row.orderNum || 0} 人${row.gender ? ` · ${row.gender}` : ""}`, salaryDescription: `${salary}元${salaryUnit} · ${settlementLabel}`, orderContent: row.orderContent || row.content || "", createTime: formatDateTime(row.createTime || row.createdAt), startTimeText: formatDateTime(row.startTime), endTimeText: formatDateTime(row.endTime) };
}
function normalizeApplicant(item) {
  const worker = item.user || item.worker || {};
  const name = worker.nickname || worker.realName || worker.name || (item.userId ? `零工${item.userId}` : "零工");
  const rawStatus = item.status || "已报名";
  return { id: item.id, name, rawStatus, status: rawStatus === "已报名" ? "待确认" : rawStatus, statusClass: ["已到岗", "已完成", "已录用"].includes(rawStatus) ? "arrived" : ["取消报名", "取消招工", "已拒绝"].includes(rawStatus) ? "cancel" : "waiting", subText: worker.certStatus ? `${worker.certStatus}` : "报名零工", applyDate: item.applyDate };
}
function openApplicants() { uni.navigateTo({ url: `/pages/boss/applicant-info?orderId=${encodeURIComponent(orderId)}` }); }
function handleMainAction() {
  if (mainAction.value.type === "edit") return uni.navigateTo({ url: `/pages/boss-publish/publish-info?id=${encodeURIComponent(orderId)}` });
  if (mainAction.value.type === "settle") return uni.navigateTo({ url: "/pages/boss/suspend-settle" });
  if (mainAction.value.type === "arrive") return openApplicants();
}
function statusText(status) { return ({ "招工中": "招募中", "招工结束": "已结束·待到岗", "待结算": "待结算", "已完成": "已完成", "取消招工": "已取消", "待审核": "待审核", "审核拒绝": "审核拒绝" }[status] || status || "待审核"); }
function formatDateTime(value) { return value ? String(value).replace("T", " ").slice(0, 16) : ""; }
function formatClock(value) { return value ? String(value).replace("T", " ").slice(11, 16) : "--"; }
function formatFullTime(value) { return value ? String(value).replace("T", " ").slice(0, 16) : "--"; }
function formatDay(value) { return value ? String(value).replace("T", " ").slice(0, 10) : "日期待定"; }
</script>

<style scoped>
.page { height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: #f7f2ed; }
.content { flex: 1; height: 0; min-height: 0; }
.status-header { padding: 28rpx 32rpx 42rpx; background: linear-gradient(155deg, #fff4e7, #ffe0c4); }
.status-badge { display: inline-block; padding: 9rpx 18rpx; border-radius: 24rpx; background: #fff3e0; color: #e65100; font-size: 22rpx; }
.status-badge.recruiting { background: #e8f5e9; color: #2e7d32; }.status-badge.settling { background: #e3f2fd; color: #1565c0; }.status-badge.completed { background: #e8f5e9; color: #2e7d32; }.status-badge.cancelled { background: #eee; color: #666; }
.job-title { display: block; margin-top: 20rpx; color: #2c1810; font-size: 42rpx; font-weight: 800; }
.job-meta { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 14rpx; color: #6d4c41; font-size: 24rpx; }
.job-meta text { padding: 8rpx 15rpx; border-radius: 20rpx; background: rgba(255,255,255,.7); }
.wage-main { display: flex; align-items: baseline; margin-top: 24rpx; color: #ff5722; }.symbol { font-size: 28rpx; font-weight: 700; }.wage { font-size: 60rpx; font-weight: 800; }.unit { font-size: 26rpx; font-weight: 600; }.settlement { margin-left: 16rpx; color: #8d6e63; font-size: 23rpx; }
.info-card { margin: 24rpx 32rpx 0; padding: 30rpx 32rpx; border-radius: 28rpx; background: #fff; }
.card-title { display: block; margin-bottom: 22rpx; color: #2c1810; font-size: 29rpx; font-weight: 700; }.title-row { display: flex; justify-content: space-between; }.apply-total { margin-left: auto; color: #a1887f; font-size: 22rpx; font-weight: 500; }
.info-row { display: flex; gap: 22rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f7f0ea; }.info-row:last-child { border-bottom: 0; padding-bottom: 0; }.info-icon { width: 68rpx; height: 68rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 20rpx; background: #fff4e6; color: #ff7043; font-size: 27rpx; }.info-icon.blue { background: #e3f2fd; color: #1e88e5; }.info-icon.green { background: #e8f5e9; color: #43a047; }.info-icon.purple { background: #f3e5f5; color: #8e24aa; }.info-content { flex: 1; min-width: 0; }.info-label { display: block; margin-bottom: 5rpx; color: #a1887f; font-size: 22rpx; }.info-value { display: block; color: #3e2723; font-size: 27rpx; font-weight: 500; line-height: 1.5; }
.description { display: block; padding-left: 78rpx; color: #5d4037; font-size: 26rpx; line-height: 1.8; white-space: pre-wrap; }
.apply-list { display: flex; flex-direction: column; gap: 16rpx; }.apply-item { display: flex; align-items: center; gap: 18rpx; padding: 18rpx 22rpx; border-radius: 22rpx; background: #fff8f2; }.avatar { width: 74rpx; height: 74rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: linear-gradient(135deg,#ffcc80,#ff8a65); color: #fff; font-size: 26rpx; font-weight: 700; }.apply-info { flex: 1; }.apply-name { display: block; color: #2c1810; font-size: 26rpx; font-weight: 600; }.apply-sub { display: block; margin-top: 4rpx; color: #a1887f; font-size: 20rpx; }.apply-status { padding: 6rpx 18rpx; border-radius: 18rpx; font-size: 20rpx; font-weight: 600; }.apply-status.arrived { background: rgba(76,175,80,.12); color: #2e7d32; }.apply-status.waiting { background: rgba(255,152,0,.12); color: #e65100; }.apply-status.cancel { background: rgba(158,158,158,.12); color: #616161; }.apply-more { padding: 20rpx 0 4rpx; text-align: center; color: #ff7043; font-size: 24rpx; }.empty-inline { padding: 30rpx; text-align: center; color: #aaa; font-size: 24rpx; }
.timeline { padding-left: 64rpx; }.timeline-item { position: relative; padding-bottom: 30rpx; }.timeline-item::before { content: ""; position: absolute; left: -48rpx; top: 7rpx; width: 18rpx; height: 18rpx; border: 4rpx solid #fff; border-radius: 50%; background: #ff7043; box-shadow: 0 0 0 3rpx #ff7043; }.timeline-item::after { content: ""; position: absolute; left: -38rpx; top: 28rpx; bottom: -4rpx; width: 3rpx; background: #ffe0cc; }.timeline-item:last-child::after { display: none; }.timeline-item.pending::before { background: #fff; box-shadow: 0 0 0 3rpx #d7ccc8; }.timeline-title { display: block; color: #2c1810; font-size: 26rpx; font-weight: 600; }.timeline-time { display: block; margin-top: 5rpx; color: #a1887f; font-size: 22rpx; }.timeline-item.pending .timeline-title, .timeline-item.pending .timeline-time { color: #a1887f; }
.scroll-space { height: 32rpx; }.state { padding: 220rpx 0; text-align: center; color: #999; font-size: 25rpx; }.state.error { color: #ff6b35; }
.bottom-bar { flex-shrink: 0; padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #eee; background: #fff; }.main-button { height: 88rpx; margin: 0; border: 0; border-radius: 46rpx; background: linear-gradient(135deg,#ff7043,#ff5722); color: #fff; font-size: 30rpx; font-weight: 700; line-height: 88rpx; }.main-button::after { border: 0; }
</style>
