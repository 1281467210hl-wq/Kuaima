<template>
  <view class="page"
    ><AppNavBar title="订单详情" :show-back="true" /><scroll-view
      scroll-y
      class="content"
      ><view v-if="loading" class="page-state">订单详情加载中…</view
      ><view v-else-if="loadError" class="page-state error"
        >订单详情加载失败</view
      ><view v-else
        ><view class="status-box"
          ><text class="status-title">{{ order.statusText }}</text
          ><text class="status-desc">{{ statusDesc }}</text></view
        ><view class="card"
          ><text class="section">岗位信息</text
          ><view class="job"
            ><text class="job-title">{{ order.title }}</text
            ><text class="employer">{{ order.employer }}</text></view
          ><view class="row"
            ><text>工作时间</text><text>{{ order.time }}</text></view
          ><view class="row"
            ><text>工作地点</text><text>{{ order.address }}</text></view
          ><view class="row"
            ><text>结算方式</text><text>{{ order.unit }}</text></view
          ><view class="row"
            ><text>订单金额</text
            ><text class="amount">¥{{ order.amount }}</text></view
          ></view
        ><view class="card"
          ><text class="section">流程进度</text
          ><view v-for="step in steps" :key="step.key" class="step"
            ><text :class="['dot', { done: step.done }]">{{
              step.done ? "✓" : ""
            }}</text
            ><view
              ><text :class="['step-title', { doneText: step.done }]">{{
                step.label
              }}</text
              ><text class="step-desc">{{ step.desc }}</text></view
            ></view
          ></view
        ></view
      ><view class="card safety"
        ><text class="section">温馨提示</text
        ><text
          >请按约定时间到岗，遇到异常情况及时联系雇主或平台客服。</text
        ></view
      ></scroll-view
    ><SafeBottomAction
      ><view class="bottom-actions"
        ><button class="service" @click="contact">联系客服</button
        ><button class="action" :disabled="operating" @click="primaryAction">
          {{ actionText }}
        </button></view
      ></SafeBottomAction
    ></view
  >
</template>
<script setup>
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppNavBar from "@/components/AppNavBar.vue";
import SafeBottomAction from "@/components/safe-bottom-action.vue";
import {
  cancelWorkerItem,
  getWorkerOrder,
  listSettlements,
  workerCheckIn,
  workerEarlyLeave,
} from "@/api/backend";
const order = ref({
  id: "",
  title: "",
  employer: "",
  time: "",
  address: "",
  amount: 0,
  unit: "",
  status: "",
  statusText: "",
});
const operating = ref(false);
const loading = ref(false);
const loadError = ref(false);
onLoad(async (options = {}) => {
  order.value.id = options.id || "";
  if (!options.id) {
    loadError.value = true;
    return;
  }
  loading.value = true;
  try {
    const item = await getWorkerOrder(options.id);
    if (!item?.id) throw new Error("订单不存在");
    order.value = normalizeOrderDetail(
      item,
      {
        ...item,
        id: item.orderId,
      },
    );
  } catch (error) {
    loadError.value = true;
    uni.showToast({ title: error.message || "订单详情加载失败", icon: "none" });
  } finally {
    loading.value = false;
  }
});
const statusDesc = computed(
  () =>
    ({
      applied: "等待雇主审核报名信息",
      hired: "雇主已录用，请按时到岗",
      arrived: "工作中，完成工作后进入结算",
      done: "工作已完成，等待结算到账",
    })[order.value.status] || "订单处理中",
);
const steps = computed(() => {
  const orderList = ["applied", "hired", "arrived", "done"];
  const index = orderList.indexOf(order.value.status);
  return [
    {
      key: "applied",
      label: "已报名",
      desc: "报名信息已提交",
      done: index >= 0,
    },
    {
      key: "hired",
      label: "已录用",
      desc: "等待雇主确认录用",
      done: index >= 1,
    },
    {
      key: "arrived",
      label: "工作中",
      desc: "开工码验证或雇主确认后开始工作",
      done: index >= 2,
    },
    {
      key: "done",
      label: "已完成",
      desc: "完成工作并进入结算",
      done: index >= 3,
    },
  ];
});
const actionText = computed(
  () =>
    ({
      applied: "取消报名",
      hired: order.value.workCodeEnabled ? "输入开工码" : "等待老板确认到岗",
      arrived: order.value.leaveCodeEnabled ? "输入早退码" : "等待老板确认完工",
      done: "查看结算",
    })[order.value.status] || "查看钱包",
);
function contact() {
  const message = `我在零工订单详情页需要客服帮助：岗位「${order.value.title || "未知岗位"}」，报名记录ID：${order.value.id || "未知"}，订单ID：${order.value.orderId || "未知"}。`;
  uni.navigateTo({
    url: `/pages/worker/service-chat?initialInput=${encodeURIComponent(message)}`,
  });
}
function primaryAction() {
  if (order.value.status === "applied") return cancel();
  if (order.value.status === "done") return openSettlement();
  if (order.value.status === "hired" && !order.value.workCodeEnabled) {
    return uni.showToast({ title: "雇主未开启开工码，请等待老板确认到岗", icon: "none" });
  }
  if (order.value.status === "hired") return enterAttendanceCode("work");
  if (order.value.status === "arrived" && !order.value.leaveCodeEnabled) {
    return uni.showToast({ title: "雇主未开启早退码，请等待老板确认完工", icon: "none" });
  }
  if (order.value.status === "arrived") return enterAttendanceCode("leave");
  uni.navigateTo({ url: "/pages/worker/wallet" });
}
function enterAttendanceCode(type) {
  const isWork = type === "work";
  const label = isWork ? "开工码" : "早退码";
  uni.showModal({
    title: `输入${label}`,
    editable: true,
    placeholderText: `请输入${label}`,
    success: async ({ confirm, content }) => {
      const code = String(content || "").trim();
      if (!confirm) return;
      if (!code) return uni.showToast({ title: "请输入验证码", icon: "none" });
      if (operating.value) return;
      operating.value = true;
      try {
        if (isWork) {
          await workerCheckIn(order.value.orderId, code);
          order.value.status = "arrived";
          order.value.statusText = "工作中";
        } else {
          await workerEarlyLeave(order.value.orderId, code);
          order.value.status = "done";
          order.value.statusText = "已完成";
        }
        uni.showToast({ title: `${label}成功`, icon: "success" });
      } catch (error) {
        uni.showToast({ title: error?.message || `${label}失败`, icon: "none" });
      } finally { operating.value = false; }
    },
  });
}
async function openSettlement() {
  try {
    const rows = await listSettlements(uni.getStorageSync("userId") || "2001");
    const settlement = (Array.isArray(rows) ? rows : []).find(
      (item) =>
        String(item.itemId) === String(order.value.id) ||
        String(item.orderId) === String(order.value.orderId),
    );
    if (!settlement)
      return uni.showToast({ title: "暂无对应结算单", icon: "none" });
    uni.navigateTo({
      url: `/pages/worker/settlement-detail?id=${settlement.id}`,
    });
  } catch (error) {
    uni.showToast({ title: error.message || "结算单加载失败", icon: "none" });
  }
}
function cancel() {
  uni.showModal({
    title: "取消报名",
    content: "确定取消本次报名吗？",
    success: async ({ confirm }) => {
      if (!confirm) return;
      operating.value = true;
      try {
        await cancelWorkerItem(order.value.id);
        order.value.status = "cancelled";
        order.value.statusText = "已取消";
        uni.showToast({ title: "已取消报名", icon: "success" });
      } catch (e) {
        uni.showToast({ title: e.message || "取消失败", icon: "none" });
      } finally {
        operating.value = false;
      }
    },
  });
}
function normalizeOrderDetail(item, detail) {
  const statusMap = {
    已报名: "applied",
    已录用: "hired",
    已到岗: "arrived",
    工作中: "arrived",
    待结算: "done",
    已完成: "done",
    已拒绝: "cancelled",
    已取消: "cancelled",
    取消报名: "cancelled",
    取消招工: "cancelled",
  };
  const typeMap = { daily: "日结", heldBack: "压薪日结", month: "月结" };
  return {
    ...order.value,
    ...detail,
    id: item.id,
    orderId: item.orderId || detail.id,
    title: detail.orderTitle || detail.postion || order.value.title,
    employer:
      detail.companyName ||
      detail.bossName ||
      (detail.createBy ? `雇主 #${detail.createBy}` : "平台认证雇主"),
    time:
      [detail.startTime, detail.endTime].filter(Boolean).join(" - ") ||
      detail.date ||
      "时间待定",
    address: detail.address || "地点待定",
    amount: detail.salary || 0,
    unit: typeMap[detail.type] || "日结",
    status: statusMap[item.status] || "applied",
    statusText: item.status || "已报名",
    workCodeEnabled: Boolean(item.workCodeEnabled),
    leaveCodeEnabled: Boolean(item.leaveCodeEnabled),
  };
}
</script>
<style scoped>
.page {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  height: calc(100vh - 176rpx);
}
.status-box {
  background: linear-gradient(135deg, #fff4e6, #ffe4b5);
  padding: 42rpx 32rpx;
}
.status-title {
  display: block;
  font-size: 40rpx;
  font-weight: 800;
  color: #8b4513;
}
.status-desc {
  display: block;
  color: #a0522d;
  font-size: 24rpx;
  margin-top: 12rpx;
}
.card {
  background: #fff;
  border-radius: 22rpx;
  margin: 22rpx;
  padding: 28rpx;
}
.section {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  margin-bottom: 24rpx;
}
.job {
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid #f1f1f1;
}
.job-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #333;
}
.employer {
  display: block;
  color: #888;
  font-size: 23rpx;
  margin-top: 10rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  padding-top: 20rpx;
  color: #888;
  font-size: 24rpx;
}
.row text:last-child {
  color: #333;
  max-width: 60%;
  text-align: right;
}
.amount {
  color: #ff4757 !important;
  font-size: 30rpx;
  font-weight: 700;
}
.step {
  display: flex;
  gap: 20rpx;
  position: relative;
  padding-bottom: 26rpx;
}
.step:last-child {
  padding-bottom: 0;
}
.step:not(:last-child)::after {
  content: "";
  position: absolute;
  left: 16rpx;
  top: 34rpx;
  width: 2rpx;
  height: calc(100% - 22rpx);
  background: #ddd;
}
.dot {
  z-index: 1;
  width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  text-align: center;
  border-radius: 50%;
  border: 2rpx solid #ddd;
  color: #fff;
  font-size: 20rpx;
}
.dot.done {
  background: #52c41a;
  border-color: #52c41a;
}
.step-title {
  display: block;
  color: #999;
  font-size: 26rpx;
}
.step-title.doneText {
  color: #333;
  font-weight: 600;
}
.step-desc {
  display: block;
  color: #aaa;
  font-size: 21rpx;
  margin-top: 5rpx;
}
.safety text:last-child {
  display: block;
  color: #888;
  font-size: 23rpx;
  line-height: 1.7;
}
.bottom-actions {
  display: flex;
  gap: 18rpx;
}
.service,
.action {
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 44rpx;
  font-size: 26rpx;
}
.service {
  width: 34%;
  border: 1rpx solid #ddd;
  background: #fff;
  color: #666;
}
.action {
  flex: 1;
  border: 0;
  background: linear-gradient(135deg, #ffd700, #ffa500);
  color: #8b4513;
  font-weight: 700;
}
.action[disabled] {
  background: #bbb;
  color: #fff;
}
</style>
