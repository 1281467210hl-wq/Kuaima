<template>
  <view class="page">
    <AppNavBar title="去授权" :show-back="true" />
    <scroll-view scroll-y class="content">
      <view class="intro-card">
        <view class="intro-head">
          <view class="intro-icon"><image :src="shieldHalvedWhiteIcon" mode="aspectFit" /></view>
          <view><text class="intro-title">授权说明</text><text class="intro-desc">将操作权限授予信任的人</text></view>
        </view>
        <text class="intro-line">· 被授权人可代您进行招工、发薪等操作</text>
        <text class="intro-line">· 您可随时查看授权状态并解除授权</text>
        <text class="intro-line">· 建议仅授权给您信任的合作伙伴</text>
      </view>

      <view class="picker-card" :class="{ open: employeePickerOpen }">
        <view class="picker-row" @click="toggleEmployeePicker">
          <text class="picker-label">授权员工</text>
          <view class="picker-value">
            <text v-if="!selectedEmployees.length" class="picker-placeholder">请选择要授权的员工（可多选）</text>
            <view v-else class="picker-selected">
                <view v-for="employee in selectedEmployees" :key="employee.memberId" class="selected-chip">
                <view class="mini-avatar" :style="{ background: employee.avatarBg }">{{ employee.initial }}</view>
                <text class="chip-name">{{ employee.name }}</text>
                <image
                  class="chip-close"
                  :src="xmarkOrangeIcon"
                  mode="aspectFit"
                  @click.stop="toggleEmployee(employee)"
                />
              </view>
            </view>
          </view>
          <image
            class="picker-arrow"
            :src="employeePickerOpen ? chevronDownOrangeIcon : chevronDownGrayIcon"
            mode="aspectFit"
          />
        </view>

        <view v-if="employeePickerOpen" class="picker-dropdown">
          <view class="picker-search">
            <image :src="magnifyingGlassGrayIcon" mode="aspectFit" />
            <input v-model="employeeKeyword" placeholder="搜索员工姓名或手机号" placeholder-class="picker-search-placeholder" />
          </view>
          <scroll-view scroll-y class="picker-list">
            <view v-if="employeesLoading" class="picker-state">员工加载中...</view>
            <view v-else-if="!filteredEmployees.length" class="picker-state">未找到匹配的员工</view>
            <view
              v-for="employee in filteredEmployees"
              :key="employee.memberId"
              class="picker-item"
              :class="{ active: employee.selected }"
              @click="toggleEmployee(employee)"
            >
              <view class="employee-avatar" :style="{ background: employee.avatarBg }">{{ employee.initial }}</view>
              <view class="employee-info">
                <view class="employee-name">
                  {{ employee.name }}
                  <text v-if="employee.statusText !== '在职'" class="employee-status">（{{ employee.statusText }}）</text>
                </view>
                <text class="employee-meta">{{ employee.maskedPhone }} · {{ employee.role }} · {{ employee.title }}</text>
              </view>
              <view class="employee-check">
                <image v-if="employee.selected" :src="checkWhiteIcon" mode="aspectFit" />
              </view>
            </view>
          </scroll-view>
        </view>
      </view>

      <view class="form-card">
        <view class="form-row"><text class="form-label">老板手机号</text><input v-model="form.phone" class="form-input" maxlength="11" type="number" disabled placeholder="当前登录老板手机号" /></view>
        <view class="form-row"><text class="form-label">验证码</text><input v-model="form.code" class="form-input" maxlength="6" type="number" placeholder="请输入验证码" /><text class="code-btn" :class="{ disabled: countdown }" @click="sendCode">{{ countdown ? `${countdown}s 后重发` : "获取验证码" }}</text></view>
      </view>

      <view class="perm-card">
        <view class="perm-title"><image :src="keyOrangeIcon" mode="aspectFit" />授权权限范围</view>
        <view v-for="item in permissions" :key="item.value" class="perm-item" :class="{ active: selectedPermissionGroups.includes(item.value) }" @click="togglePermission(item.value)">
          <view class="perm-icon" :class="`icon-${item.value.toLowerCase()}`"><image :src="item.icon" mode="aspectFit" /></view>
          <view class="perm-main"><text class="perm-name">{{ item.label }}</text><text class="perm-desc">{{ item.desc }}</text></view>
          <view class="check"><image v-if="selectedPermissionGroups.includes(item.value)" :src="checkWhiteIcon" mode="aspectFit" /></view>
        </view>
      </view>
      <view class="bottom-space" />
    </scroll-view>
    <view class="footer">
      <button v-if="selectedEmployees.length === 1 && selectedEmployees[0].portalEnabled" class="revoke" :disabled="submitting" @click="revokeSelected">撤销企业端权限</button>
      <button class="confirm" :disabled="submitting" @click="submit">{{ submitting ? "处理中…" : selectedEmployees.length === 1 && selectedEmployees[0].portalEnabled ? "保存权限" : "确认授权" }}</button>
      <text class="agree">点击确认即表示同意《授权服务协议》</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import AppNavBar from "@/components/AppNavBar.vue";
import { getCurrentUser, sendSmsCode } from "@/api/auth";
import { createBossSubAccount, listBossEnterpriseMembers, revokeBossSubAccountPermissions, updateBossSubAccountPermissions } from "@/api/backend";
import chevronDownGrayIcon from "/static/icons/boss-authorize/chevron-down-gray.svg";
import chevronDownOrangeIcon from "/static/icons/boss-authorize/chevron-down-orange.svg";
import checkWhiteIcon from "/static/icons/boss-authorize/check-white.svg";
import shieldHalvedWhiteIcon from "/static/icons/boss-authorize/shield-halved-white.svg";
import keyOrangeIcon from "/static/icons/boss-authorize/key-orange.svg";
import houseWhiteIcon from "/static/icons/boss-authorize/house-white.svg";
import clipboardListWhiteIcon from "/static/icons/boss-authorize/clipboard-list-white.svg";
import commentDotsWhiteIcon from "/static/icons/boss-authorize/comment-dots-white.svg";
import briefcaseWhiteIcon from "/static/icons/boss-authorize/briefcase-white.svg";
import magnifyingGlassGrayIcon from "/static/icons/boss-authorize/magnifying-glass-gray.svg";
import xmarkOrangeIcon from "/static/icons/boss-authorize/xmark-orange.svg";

const form = ref({ phone: "", code: "", nickname: "" });
const submitting = ref(false);
const sendingCode = ref(false);
const countdown = ref(0);
const permissions = [
  { value: "HOME", codes: ["HOME_VIEW"], label: "首页", desc: "查看数据概览、待办提醒", icon: houseWhiteIcon },
  { value: "ORDER", codes: ["ORDER_VIEW", "ORDER_CREATE"], label: "招工订单", desc: "发布岗位、管理报名、订单结算", icon: clipboardListWhiteIcon },
  { value: "MESSAGE", codes: ["MESSAGE_VIEW"], label: "消息", desc: "系统通知、员工沟通", icon: commentDotsWhiteIcon },
  { value: "WORKBENCH", codes: ["WORKBENCH_VIEW", "ATTENDANCE_VIEW", "SETTLEMENT_VIEW", "REPORT_VIEW"], label: "工作台", desc: "发薪结算、考勤管理、报表查看", icon: briefcaseWhiteIcon },
];
const selectedPermissionGroups = ref(["HOME", "ORDER", "MESSAGE", "WORKBENCH"]);
const employeePickerOpen = ref(false);
const employeeKeyword = ref("");
const employees = ref([]);
const employeesLoading = ref(false);

const selectedEmployees = computed(() => employees.value.filter((employee) => employee.selected));
const filteredEmployees = computed(() => {
  const keyword = employeeKeyword.value.trim().toLowerCase();
  if (!keyword) return employees.value;
  return employees.value.filter((employee) =>
    `${employee.name}${employee.phone}${employee.maskedPhone}${employee.roleName}`.toLowerCase().includes(keyword),
  );
});

onLoad(() => {
  form.value.phone = cachedCurrentUserPhone();
  loadEmployees();
  loadCurrentUserPhone();
});

async function loadEmployees() {
  employeesLoading.value = true;
  try {
    const result = await listBossEnterpriseMembers({ page: 0, size: 100, status: "ACTIVE" });
    const body = result?.data ?? result ?? {};
    const rows = Array.isArray(body)
      ? body
      : body.members || body.records || body.content || body.list || body.employees || [];
    employees.value = rows
      .filter((employee) => !employee.status || String(employee.status).toUpperCase() === "ACTIVE")
      .map((employee, index) => normalizeEmployee(employee, index));
  } catch (error) {
    employees.value = [];
    uni.showToast({ title: error?.message || "员工列表加载失败", icon: "none" });
  } finally {
    employeesLoading.value = false;
  }
}

function normalizeEmployee(employee, index) {
  const name = employee.name || employee.nickname || employee.realName || employee.memberName || `员工${employee.id || index + 1}`;
  const phone = String(employee.phone || employee.mobile || employee.phoneNumber || "").trim();
  const role = employee.role || employee.roleName || employee.position || employee.employmentType || "员工";
  const title = employee.title || employee.jobTitle || employee.projectName || "";
  const statusText = ({ active: "在职", temp: "临时", left: "离职" })[employee.status] || "在职";
  return {
    ...employee,
    memberId: employee.memberId ?? employee.id,
    name,
    initial: name.slice(0, 1),
    phone,
    maskedPhone: maskPhone(phone),
    role,
    title,
    roleName: role,
    statusText,
    avatarBg: avatarGradient(role, statusText, index),
    selected: false,
    portalEnabled: employee.portalEnabled === true,
    permissions: normalizePermissions(employee.permissions || employee.permissionCodes),
  };
}

function normalizePermissions(value) {
  if (Array.isArray(value)) return value;
  if (typeof value === "string") {
    try { const result = JSON.parse(value); return Array.isArray(result) ? result : []; } catch (_) { return []; }
  }
  return [];
}

function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || "未留手机号";
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

function avatarGradient(roleName, statusText, index) {
  if (statusText === "离职") return "linear-gradient(135deg, #BFBFBF, #8C8C8C)";
  if (roleName.includes("财务")) return "linear-gradient(135deg, #36CFC9, #13A8A8)";
  if (roleName.includes("招聘")) return "linear-gradient(135deg, #722ED1, #531DAB)";
  if (roleName.includes("主管") || roleName.includes("管理")) return "linear-gradient(135deg, #1890FF, #096DD9)";
  if (roleName.includes("工头")) return "linear-gradient(135deg, #FF8C5A, #FF6B35)";
  const colors = [
    "linear-gradient(135deg, #FFD96F, #FFB020)",
    "linear-gradient(135deg, #FAAD14, #D48806)",
    "linear-gradient(135deg, #36CFC9, #13A8A8)",
  ];
  return colors[index % colors.length];
}

function toggleEmployeePicker() {
  employeePickerOpen.value = !employeePickerOpen.value;
}

function toggleEmployee(employee) {
  employee.selected = !employee.selected;
  if (employee.selected && employee.portalEnabled && employee.permissions.length) {
    selectedPermissionGroups.value = permissions
      .filter((item) => item.codes.some((code) => employee.permissions.includes(code)))
      .map((item) => item.value);
  }
}

function togglePermission(value) {
  const next = selectedPermissionGroups.value.filter((item) => item !== value);
  if (!selectedPermissionGroups.value.includes(value)) next.push(value);
  selectedPermissionGroups.value = next;
}

function selectedPermissionCodes() {
  return permissions
    .filter((item) => selectedPermissionGroups.value.includes(item.value))
    .flatMap((item) => item.codes);
}

async function sendCode() {
  if (countdown.value || sendingCode.value) return;
  if (!/^1\d{10}$/.test(form.value.phone)) {
    return uni.showToast({ title: "请输入正确手机号", icon: "none" });
  }
  sendingCode.value = true;
  try {
    await sendSmsCode(form.value.phone);
    uni.showToast({ title: "验证码已发送", icon: "success" });
  } catch (error) {
    uni.showToast({ title: error?.message || "验证码发送失败", icon: "none" });
    return;
  } finally {
    sendingCode.value = false;
  }
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) clearInterval(timer);
  }, 1000);
}

async function submit() {
  if (!/^1\d{10}$/.test(form.value.phone)) return uni.showToast({ title: "请输入正确手机号", icon: "none" });
  if (!selectedEmployees.value.length) return uni.showToast({ title: "请先选择要授权的员工", icon: "none" });
  const invalidEmployee = selectedEmployees.value.find((employee) => !employee.memberId);
  if (invalidEmployee) return uni.showToast({ title: `${invalidEmployee.name}成员标识无效`, icon: "none" });
  if (!form.value.code.trim()) return uni.showToast({ title: "请输入验证码", icon: "none" });
  if (!selectedPermissionGroups.value.length) return uni.showToast({ title: "请选择授权权限", icon: "none" });
  if (submitting.value) return;
  submitting.value = true;
  try {
    const permissionCodes = selectedPermissionCodes();
    const role = selectedPermissionGroups.value.includes("WORKBENCH") ? "ADMIN" : "OPERATOR";
    for (const employee of selectedEmployees.value) {
      if (employee.portalEnabled) {
        await updateBossSubAccountPermissions(employee.memberId, permissionCodes);
      } else {
        await createBossSubAccount({ memberId: Number(employee.memberId), role, permissionCodes, code: form.value.code });
      }
    }
    uni.showToast({ title: "权限保存成功", icon: "success" });
    setTimeout(() => uni.navigateBack(), 600);
  } catch (error) {
    uni.showToast({ title: error?.message || "授权失败，请重试", icon: "none" });
  } finally {
    submitting.value = false;
  }
}

function revokeSelected() {
  const employee = selectedEmployees.value[0];
  if (!employee?.memberId || submitting.value) return;
  uni.showModal({
    title: "撤销权限",
    content: `撤销后，${employee.name} 将无法进入当前企业，是否继续？`,
    success: async ({ confirm }) => {
      if (!confirm) return;
      submitting.value = true;
      try {
        await revokeBossSubAccountPermissions(employee.memberId);
        uni.showToast({ title: "已撤销企业端权限", icon: "success" });
        await loadEmployees();
      } catch (error) {
        uni.showToast({ title: error?.message || "撤销失败", icon: "none" });
      } finally { submitting.value = false; }
    },
  });
}

function cachedCurrentUserPhone() {
  const cachedUser = uni.getStorageSync("userInfo") || {};
  return cachedUser.phone || uni.getStorageSync("userPhone") || "";
}

async function loadCurrentUserPhone() {
  try {
    const user = await getCurrentUser();
    if (user?.phone) form.value.phone = user.phone;
  } catch (_) {
    // 接口失败时保留本地缓存手机号，不阻断授权员工列表加载。
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f3f4f6; display: flex; flex-direction: column; }
.content { flex: 1; min-height: 0; padding-top: 12rpx; box-sizing: border-box; }
.intro-card, .picker-card, .form-card, .perm-card { margin: 0 32rpx 24rpx; background: #fff; border-radius: 24rpx; box-sizing: border-box; }
.intro-card { padding: 28rpx 32rpx; background: linear-gradient(135deg, #fff3e6, #ffe8cc); border: 1rpx solid #ffe0b2; }
.intro-head { display: flex; align-items: center; gap: 20rpx; margin-bottom: 16rpx; }
.intro-icon { width: 72rpx; height: 72rpx; border-radius: 20rpx; color: #fff; background: linear-gradient(135deg, #ff8c5a, #ff6b35); display: flex; align-items: center; justify-content: center; font-size: 26rpx; }
.intro-title, .intro-desc, .intro-line { display: block; }.intro-title { color: #8b4513; font-size: 30rpx; font-weight: 600; }.intro-desc, .intro-line { color: #a0522d; font-size: 22rpx; }.intro-desc { margin-top: 4rpx; }.intro-line { line-height: 1.8; }.intro-icon image { width: 34rpx; height: 34rpx; }
.picker-card { padding: 4rpx 32rpx; }
.picker-row { display: flex; align-items: center; min-height: 96rpx; padding: 16rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.picker-card.open .picker-row { border-bottom-color: #f5f5f5; }
.picker-label { width: 196rpx; flex-shrink: 0; color: #333; font-size: 28rpx; }
.picker-value { flex: 1; min-width: 0; }
.picker-placeholder { color: #c8c8c8; font-size: 28rpx; }
.picker-selected { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx; }
.selected-chip { display: flex; align-items: center; gap: 6rpx; max-width: 100%; padding: 4rpx 8rpx 4rpx 4rpx; background: #fff3ed; border-radius: 28rpx; box-sizing: border-box; }
.mini-avatar { display: flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; flex-shrink: 0; color: #fff; font-size: 20rpx; font-weight: 700; border-radius: 50%; }
.chip-name { max-width: 120rpx; overflow: hidden; color: #ff6b35; font-size: 24rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.chip-close { width: 20rpx; height: 20rpx; flex-shrink: 0; }
.picker-arrow { width: 26rpx; height: 26rpx; flex-shrink: 0; margin-left: 12rpx; }
.picker-card.open .picker-arrow { transform: rotate(180deg); }
.picker-dropdown { padding-top: 16rpx; border-top: 1rpx solid #f5f5f5; }
.picker-search { display: flex; align-items: center; gap: 16rpx; height: 68rpx; padding: 0 28rpx; margin-bottom: 16rpx; background: #f7f7f7; border-radius: 34rpx; }
.picker-search image { width: 24rpx; height: 24rpx; flex-shrink: 0; }
.picker-search input { flex: 1; min-width: 0; color: #333; font-size: 26rpx; }
.picker-search-placeholder { color: #c8c8c8; }
.picker-list { height: 480rpx; width: 100%; }
.picker-state { padding: 60rpx 0; color: #999; font-size: 26rpx; text-align: center; }
.picker-item { display: flex; align-items: center; padding: 20rpx 12rpx; border-radius: 20rpx; }
.picker-item.active { background: #fff3ed; }
.employee-avatar { display: flex; align-items: center; justify-content: center; width: 72rpx; height: 72rpx; flex-shrink: 0; margin-right: 20rpx; color: #fff; font-size: 28rpx; font-weight: 700; border-radius: 50%; }
.employee-info { flex: 1; min-width: 0; }
.employee-name { overflow: hidden; color: #333; font-size: 26rpx; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.employee-status { color: #ff4d4f; font-size: 20rpx; }
.employee-meta { display: block; margin-top: 4rpx; overflow: hidden; color: #999; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.employee-check { display: flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; flex-shrink: 0; margin-left: 16rpx; border: 3rpx solid #d8d8d8; border-radius: 50%; box-sizing: border-box; }
.picker-item.active .employee-check { background: #ff6b35; border-color: #ff6b35; }
.employee-check image { width: 22rpx; height: 24rpx; }
.form-card { padding: 4rpx 32rpx; }.form-row { min-height: 100rpx; display: flex; align-items: center; border-bottom: 1rpx solid #f5f5f5; }.form-row:last-child { border-bottom: 0; }.form-label { width: 150rpx; color: #333; font-size: 28rpx; }.form-input { flex: 1; min-width: 0; color: #333; font-size: 28rpx; }.code-btn { margin-left: 12rpx; color: #2563eb; font-size: 24rpx; white-space: nowrap; }.code-btn.disabled { color: #aaa; }
.perm-card { padding: 28rpx 32rpx; }.perm-title { display: flex; align-items: center; gap: 12rpx; margin-bottom: 16rpx; color: #333; font-size: 30rpx; font-weight: 600; }.perm-title image { width: 26rpx; height: 26rpx; }.perm-item { display: flex; align-items: center; padding: 22rpx 24rpx; margin-top: 8rpx; background: #fafafa; border: 2rpx solid transparent; border-radius: 16rpx; }.perm-item.active { background: #fff3ed; border-color: #ff6b35; }.perm-icon { display: flex; align-items: center; justify-content: center; width: 60rpx; height: 60rpx; flex-shrink: 0; margin-right: 20rpx; border-radius: 16rpx; }.perm-icon image { width: 26rpx; height: 26rpx; }.perm-icon.icon-home { background: #ff8c5a; }.perm-icon.icon-order { background: #36cfc9; }.perm-icon.icon-message { background: #722ed1; }.perm-icon.icon-workbench { background: #faad14; }.perm-main { flex: 1; }.perm-name, .perm-desc { display: block; }.perm-name { color: #333; font-size: 27rpx; }.perm-item.active .perm-name { color: #ff6b35; }.perm-desc { margin-top: 4rpx; color: #999; font-size: 22rpx; }.check { display: flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; flex-shrink: 0; border: 3rpx solid #d8d8d8; border-radius: 50%; box-sizing: border-box; }.check image { width: 22rpx; height: 24rpx; }.active .check { background: #ff6b35; border-color: #ff6b35; }
.bottom-space { height: 180rpx; }.footer { padding: 20rpx 32rpx calc(28rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -2rpx 10rpx rgba(0,0,0,.05); }.confirm { width: 100%; height: 88rpx; margin: 0; color: #fff; background: linear-gradient(135deg, #ff8c5a, #ff6b35); border: 0; border-radius: 44rpx; font-size: 30rpx; }.confirm[disabled] { opacity: .6; }.confirm::after { border: 0; }.agree { display: block; margin-top: 14rpx; color: #999; font-size: 21rpx; text-align: center; }
.revoke { width: 100%; height: 76rpx; margin: 0 0 16rpx; border: 2rpx solid #ff4d4f; border-radius: 38rpx; color: #ff4d4f; background: #fff; font-size: 27rpx; }.revoke::after { border: 0; }
</style>
