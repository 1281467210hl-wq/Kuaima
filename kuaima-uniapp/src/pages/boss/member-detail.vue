<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><text class="back-ico">‹</text></view>
      <text class="wb-title">成员详情</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="wb-capsule">
        <view class="cap-btn"><text class="cap-ico">⋯</text></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><text class="cap-ico">○</text></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body has-footer">
      <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
      <template v-else>
        <!-- 成员信息头 -->
        <view class="md-head">
          <view class="md-avatar" :style="{ background: avatarColor((detail.userId || '') + '') }">{{ (detail.name || '成').charAt(0) }}</view>
          <view class="md-head-info">
            <view class="md-name-line">
              <text class="md-name">{{ detail.name }}</text>
              <text class="md-role-tag" :class="{ boss: detail.role === 'OWNER' }">{{ roleText(detail.role) }}</text>
            </view>
            <text class="md-sub">{{ detail.title ? detail.title + ' · ' : '' }}{{ detail.phone }}</text>
          </view>
        </view>

        <!-- 自我账号提示 -->
        <view v-if="isSelf" class="self-tip">
          <text>ℹ️ 这是当前登录账号，超级管理员不支持编辑角色或移出企业</text>
        </view>

        <!-- 基本信息（1013：新增 姓名/职位，去掉 邀请来源/负责岗位） -->
        <view class="md-block-title"><text class="block-ico">🪪</text>基本信息</view>
        <view class="md-info">
          <view class="md-line"><text class="md-key">姓名</text><text class="md-val">{{ detail.name || '—' }}</text></view>
          <view class="md-line"><text class="md-key">性别</text><text class="md-val">{{ genderText(detail.gender) }}</text></view>
          <view class="md-line"><text class="md-key">年龄</text><text class="md-val">{{ ageText(detail.age) }}</text></view>
          <view class="md-line"><text class="md-key">职位</text><text class="md-val">{{ detail.title || '—' }}</text></view>
          <view class="md-line"><text class="md-key">手机号</text><text class="md-val">{{ detail.phone || '—' }}</text></view>
          <view class="md-line"><text class="md-key">企业角色</text><text class="md-val">{{ roleText(detail.role) }}</text></view>
          <view class="md-line"><text class="md-key">加入时间</text><text class="md-val">{{ detail.joinDate || '—' }}</text></view>
        </view>

        <!-- 权限明细（1013：五大模块 + 已开通/未开通徽标） -->
        <view class="md-block-title"><text class="block-ico">🛡</text>权限明细</view>
        <view class="md-info">
          <view v-for="mod in permModules" :key="mod.key" class="md-line">
            <text class="md-key"><text class="md-key-ico">{{ mod.icon }}</text>{{ mod.name }}</text>
            <text :class="moduleOn(mod.key) ? 'perm-on' : 'perm-off'">{{ moduleOn(mod.key) ? '已开通' : '未开通' }}</text>
          </view>
        </view>
      </template>
    </view>

    <!-- 底部操作 -->
    <view v-if="!loading && !isSelf" class="wb-footer">
      <view class="wb-btn-outline-red" @click="openRemove">➖ 移除企业</view>
      <view class="wb-btn-primary-gradient" @click="openRole">✏️ 编辑</view>
    </view>

    <!-- 编辑角色与权限（1013：底部弹层，姓名/手机号、职位/角色两列，工作台权限开关组） -->
    <view class="wb-modal" v-if="roleModalShow" @click.self="closeRole">
      <view class="wb-modal-box sheet">
        <view class="handle-bar"></view>
        <view class="wb-modal-header">
          <text class="wb-modal-title">编辑角色与权限</text>
          <view class="rd-header-right">
            <text class="rd-more" @click="goEditPage">更多编辑 ›</text>
            <text class="wb-modal-close" @click="closeRole">✕</text>
          </view>
        </view>
        <view class="wb-modal-body">
          <view class="rd-col">
            <view class="rd-row2">
              <view class="rd-field">
                <text class="rd-label">成员姓名</text>
                <input class="rd-input" type="text" v-model="editForm.name" placeholder="请输入成员姓名" />
              </view>
              <view class="rd-field">
                <text class="rd-label">手机号</text>
                <input class="rd-input" type="text" v-model="editForm.phone" placeholder="请输入手机号" />
              </view>
            </view>
            <view class="rd-row2">
              <view class="rd-field">
                <text class="rd-label">职位</text>
                <input class="rd-input" type="text" v-model="editForm.title" placeholder="请输入职位" />
              </view>
              <view class="rd-field">
                <text class="rd-label">角色</text>
                <picker mode="selector" :range="roleOptions" :value="editForm.roleIndex" @change="onRolePick">
                  <view class="rd-input rd-picker">
                    <text>{{ roleOptions[editForm.roleIndex] }}</text>
                    <text class="rd-picker-arrow">⌄</text>
                  </view>
                </picker>
              </view>
            </view>
            <view class="rd-section">
              <text class="rd-section-title">工作台权限</text>
              <view class="org-card">
                <text class="org-sub">❤️ 所属组织</text>
                <view class="org-row">
                  <text class="org-row-name">🏢 {{ companyName || detail.companyName || '我的企业' }}<text class="org-row-tag">· 所属公司</text></text>
                </view>
                <view v-for="mod in permModules" :key="mod.key" class="org-row">
                  <text class="org-row-name">{{ mod.icon }} {{ mod.name }}</text>
                  <view class="rd-switch" :class="{ on: editPerms[mod.key] }" @click="togglePerm(mod.key)">
                    <view class="rd-knob"></view>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
        <view class="wb-modal-footer sheet-footer">
          <view class="wb-btn-outline pill" @click="closeRole">取消</view>
          <view class="wb-btn-primary-gradient pill" @click="saveRole">保存</view>
        </view>
      </view>
    </view>

    <!-- 移除企业弹框（底部弹层） -->
    <view class="wb-modal" v-if="removeModalShow" @click.self="closeRemove">
      <view class="wb-modal-box sheet">
        <view class="handle-bar"></view>
        <view class="wb-modal-body remove-body">
          <view class="remove-icon-wrap"><text class="remove-icon">➖</text></view>
          <text class="modal-title">确认移出「{{ detail.name }}」？</text>
          <text class="modal-desc">移出后该成员将无法访问企业数据{"\n"}历史考勤与发薪记录将保留</text>
        </view>
        <view class="wb-modal-footer sheet-footer">
          <view class="wb-btn-outline pill" @click="closeRemove">取消</view>
          <view class="wb-btn-danger-gradient pill" @click="confirmRemove">确认移出</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getMemberDetail, updateMemberRole, removeMember, getExitInfo } from "@/api/enterprise";
import { request } from "@/api/http";
import { avatarColor } from "@/api/project";

// 原型五大权限模块开关 → WorkbenchPermCodes 统一大写码（对照 TOKEN_TO_CODE 映射）：
// 企业成员→MEMBER_MANAGE；项目管理→PROJECT_MANAGE；智能招聘→岗位发布/简历库/候选人；
// 闪电发薪→制单发薪/发薪审批；员工管理→MEMBER_WRITE（WorkbenchPermCodes 无独立员工管理码，
// 取企业与成员域最接近的写码，后端 normalize 亦会原样保留）。
const PERM_MODULE_CODES = {
  MEMBER: ["MEMBER_MANAGE"],
  PROJECT: ["PROJECT_MANAGE"],
  RECRUIT: ["POSITION_PUBLISH", "RESUME_VIEW", "CANDIDATE_MANAGE"],
  PAYROLL: ["PAYROLL_CREATE", "PAYROLL_APPROVE"],
  STAFF: ["MEMBER_WRITE"],
};

export default {
  data() {
    return {
      statusBarHeight: 44,
      memberId: "",
      detail: {},
      loading: false,
      isSelf: false,
      saving: false,
      companyName: "",
      roleModalShow: false,
      removeModalShow: false,
      roleOptions: ["超级管理员", "管理员", "成员"],
      roleCodes: ["OWNER", "ADMIN", "STAFF"],
      editForm: { name: "", phone: "", title: "", roleIndex: 2 },
      editPerms: { MEMBER: false, PROJECT: false, RECRUIT: false, PAYROLL: false, STAFF: false },
      // 打开弹层时的权限快照，保存时对比决定是否调 PUT permissions
      initialPerms: { MEMBER: false, PROJECT: false, RECRUIT: false, PAYROLL: false, STAFF: false },
      // 1013 原型五大权限模块（企业成员/项目管理/智能招聘/闪电发薪/员工管理）
      permModules: [
        { key: "MEMBER", name: "企业成员", icon: "👥", match: ["MEMBER_MANAGE", "MEMBER_VIEW", "企业成员", "成员管理", "成员管理与邀请", "员工列表"] },
        { key: "PROJECT", name: "项目管理", icon: "📁", match: ["PROJECT_MANAGE", "项目管理", "project_manage", "project"] },
        { key: "RECRUIT", name: "智能招聘", icon: "📢", match: ["POSITION_PUBLISH", "RESUME_VIEW", "CANDIDATE_MANAGE", "智能招聘", "岗位发布", "岗位发布与编辑", "简历库", "候选人"] },
        { key: "PAYROLL", name: "闪电发薪", icon: "⚡", match: ["PAYROLL_CREATE", "PAYROLL_APPROVE", "闪电发薪", "制单发薪", "发薪审批", "批量发薪"] },
        { key: "STAFF", name: "员工管理", icon: "🪪", match: ["MEMBER_WRITE", "STAFF_MANAGE", "EMPLOYEE_MANAGE", "员工管理"] },
      ],
    };
  },
  onLoad(options) {
    this.memberId = options.id || "";
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadDetail();
    this.loadCompany();
  },
  methods: {
    avatarColor,
    roleText(role) {
      return { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" }[role] || role || "成员";
    },
    // 后端 gender 为 male/female，UI 展示男/女；已是中文原样返回，无值回退 '—'
    genderText(gender) {
      if (gender === "male") return "男";
      if (gender === "female") return "女";
      return gender || "—";
    },
    // 年龄由后端按身份证计算返回整数，原型展示带「岁」（如 36岁）
    ageText(age) {
      if (age == null || age === "") return "—";
      return `${age}岁`;
    },
    // 权限模块开通状态：优先按后端 permissions 数组匹配；无数据时按角色默认降级
    moduleOn(key) {
      if (this.detail.role === "OWNER") return true;
      const perms = this.detail.permissions;
      if (Array.isArray(perms) && perms.length) {
        const mod = this.permModules.find((m) => m.key === key);
        return perms.some((p) => mod.match.some((token) => String(p).includes(token)));
      }
      if (this.detail.role === "ADMIN") return key === "MEMBER" || key === "PROJECT";
      return false;
    },
    async loadCompany() {
      try {
        const info = await getExitInfo();
        this.companyName = (info && info.companyName) || "";
      } catch (e) {}
    },
    async loadDetail() {
      if (!this.memberId) return;
      this.loading = true;
      try {
        this.detail = (await getMemberDetail(this.memberId)) || {};
        // isSelf 修复：后端 self 字段目前恒为 false，前端按当前登录 userId 判定
        const myId = String(uni.getStorageSync("userId") || "");
        this.isSelf = this.detail.self === true || (!!myId && String(this.detail.userId) === myId);
        this.editForm.name = this.detail.name || "";
        this.editForm.phone = this.detail.phone || "";
        this.editForm.title = this.detail.title || "";
        this.editForm.roleIndex = Math.max(0, this.roleCodes.indexOf(this.detail.role));
        this.permModules.forEach((mod) => {
          this.editPerms[mod.key] = this.moduleOn(mod.key);
        });
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    onRolePick(e) {
      this.editForm.roleIndex = Number(e.detail.value);
    },
    togglePerm(key) {
      this.editPerms[key] = !this.editPerms[key];
    },
    openRole() {
      if (this.detail.role === "OWNER") {
        uni.showToast({ title: "超级管理员默认拥有全部权限，不支持编辑", icon: "none" });
        return;
      }
      this.editForm.roleIndex = Math.max(0, this.roleCodes.indexOf(this.detail.role));
      this.initialPerms = { ...this.editPerms };
      this.roleModalShow = true;
    },
    closeRole() {
      this.roleModalShow = false;
    },
    /** 开关组 → 大写权限码数组（按 PERM_MODULE_CODES 映射，去重）。 */
    permCodesFromSwitches() {
      const codes = [];
      this.permModules.forEach((mod) => {
        if (!this.editPerms[mod.key]) return;
        (PERM_MODULE_CODES[mod.key] || []).forEach((code) => {
          if (!codes.includes(code)) codes.push(code);
        });
      });
      return codes;
    },
    permsChanged() {
      return this.permModules.some((mod) => this.editPerms[mod.key] !== this.initialPerms[mod.key]);
    },
    async saveRole() {
      if (this.saving) return;
      const picked = this.roleCodes[this.editForm.roleIndex];
      // 姓名/手机号属 sys_user 域，info 接口不修改：有改动仅提示
      if (this.editForm.name && this.editForm.name !== (this.detail.name || "")) {
        uni.showToast({ title: "成员姓名由用户中心维护，此处不支持修改", icon: "none" });
      }
      const userId = this.detail.userId;
      const ops = [];
      if ((this.editForm.title || "") !== (this.detail.title || "")) {
        ops.push({
          label: "职位",
          run: () =>
            request({
              url: `/boss/enterprise/members/${userId}/info`,
              method: "PUT",
              data: { title: this.editForm.title || "" },
            }),
        });
      }
      if (this.permsChanged()) {
        ops.push({
          label: "工作台权限",
          run: () =>
            request({
              url: `/boss/enterprise/members/${userId}/permissions`,
              method: "PUT",
              data: { permissions: this.permCodesFromSwitches() },
            }),
        });
      }
      if (picked !== this.detail.role) {
        ops.push({ label: "角色", run: () => updateMemberRole(this.memberId, picked) });
      }
      if (!ops.length) {
        uni.showToast({ title: "没有需要保存的修改", icon: "none" });
        return;
      }
      this.saving = true;
      const done = [];
      const failed = [];
      let lastError = "";
      for (const op of ops) {
        try {
          await op.run();
          done.push(op.label);
        } catch (e) {
          failed.push(op.label);
          lastError = e.message || "";
        }
      }
      this.saving = false;
      if (failed.length === ops.length) {
        // 全失败才报后端错误
        uni.showToast({ title: lastError || "操作失败", icon: "none" });
        return;
      }
      if (failed.length) {
        uni.showToast({ title: `${done.join("、")}已更新，${failed.join("、")}更新失败`, icon: "none" });
      } else {
        uni.showToast({ title: `已更新${done.join("、")}`, icon: "none" });
      }
      this.closeRole();
      this.loadDetail();
    },
    goEditPage() {
      this.roleModalShow = false;
      uni.navigateTo({ url: `/pages/boss/member-edit?id=${this.memberId}&name=${encodeURIComponent(this.detail.name || "")}` });
    },
    openRemove() {
      this.removeModalShow = true;
    },
    closeRemove() {
      this.removeModalShow = false;
    },
    async confirmRemove() {
      try {
        await removeMember(this.memberId);
        uni.showToast({ title: "已将该成员移出企业", icon: "none" });
        this.closeRemove();
        setTimeout(() => uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/members" }) }), 600);
      } catch (e) {
        uni.showToast({ title: e.message || "操作失败", icon: "none" });
      }
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/members" }) });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f7f7f7;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 6px 16px 12px;
  flex-shrink: 0;
}

.wb-back {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-ico {
  font-size: 22px;
  color: #333;
}

.wb-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.wb-capsule {
  display: flex;
  align-items: center;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 17px;
  padding: 0 6px;
  height: 32px;
}

.cap-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-ico {
  font-size: 14px;
  color: #666;
}

.cap-divider {
  width: 1px;
  height: 16px;
  background: rgba(0, 0, 0, 0.15);
  margin: 0 2px;
}

.wb-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 20px;
}

.wb-body.has-footer {
  padding-bottom: 90px;
}

.md-head {
  background: #fff;
  border-radius: 16px;
  padding: 18px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 13px;
}

.md-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.md-head-info {
  flex: 1;
  min-width: 0;
}

.md-name-line {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.md-name {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.md-role-tag {
  background: #fff0e8;
  color: #ff6b35;
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
}

.md-role-tag.boss {
  background: #fff7e0;
  color: #d97706;
}

.md-sub {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: block;
}

.self-tip {
  display: flex;
  align-items: center;
  gap: 9px;
  background: #fff7e0;
  border-radius: 12px;
  padding: 11px 14px;
  font-size: 12px;
  color: #92600a;
  margin-bottom: 14px;
}

.md-block-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 700;
  color: #333;
  margin: 16px 2px 10px;
}

.block-ico {
  font-size: 13px;
  color: #ff6b35;
}

.md-info {
  background: #fff;
  border-radius: 16px;
  padding: 4px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.md-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
}

.md-line:last-child {
  border-bottom: none;
}

.md-key {
  color: #999;
  display: flex;
  align-items: center;
}

.md-key-ico {
  margin-right: 6px;
  font-size: 13px;
}

.md-val {
  color: #333;
  font-weight: 500;
}

/* 1013 权限徽标 */
.perm-on {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #16a34a;
  background: #eafbf0;
  padding: 3px 10px;
  border-radius: 10px;
}

.perm-on::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #16a34a;
}

.perm-off {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: #999;
  background: #f3f4f6;
  padding: 3px 10px;
  border-radius: 10px;
}

.perm-off::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c0c0;
}

.wb-empty {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}

/* 底栏：1013 背景 #F7F7F7 + 上边框 #EAEAEA */
.wb-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 12px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: #f7f7f7;
  border-top: 1px solid #eaeaea;
  z-index: 5;
}

.wb-footer view {
  flex: 1;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 23px;
  font-size: 14px;
  font-weight: 600;
}

.wb-btn-outline-red {
  color: #ff5c33;
  border: 1.5px solid #ffd0d0;
  background: #fff;
}

.wb-btn-primary-gradient {
  color: #fff;
  background: linear-gradient(135deg, #ff8c5a, #ff6b35);
  box-shadow: 0 4px 14px rgba(255, 107, 53, 0.3);
}

.wb-btn-danger-gradient {
  color: #fff;
  background: linear-gradient(135deg, #ff7743, #ff5c33);
  font-weight: 600;
}

/* 底部弹层 */
.wb-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}

.wb-modal-box {
  background: #fff;
  width: 100%;
  border-radius: 20px 20px 0 0;
  overflow: hidden;
}

.handle-bar {
  width: 36px;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  margin: 10px auto 4px;
}

.wb-modal-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 0.5px solid #f0f0f0;
}

.wb-modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.rd-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rd-more {
  font-size: 12px;
  color: #ff6b35;
  font-weight: 500;
}

.wb-modal-close {
  color: #999;
  font-size: 16px;
  width: 28px;
  text-align: center;
}

.wb-modal-body {
  padding: 16px 18px;
}

.wb-modal-footer {
  display: flex;
  gap: 12px;
  padding: 8px 18px calc(18px + env(safe-area-inset-bottom));
}

.wb-modal-footer .pill {
  flex: 1;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  font-size: 15px;
  font-weight: 600;
}

.wb-btn-outline.pill {
  color: #666;
  border: 1px solid #eee;
  background: #fff;
  font-weight: 500;
}

/* 编辑弹层表单 */
.rd-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rd-row2 {
  display: flex;
  gap: 12px;
}

.rd-field {
  flex: 1;
  min-width: 0;
}

.rd-label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
}

.rd-input {
  width: 100%;
  border: 1.5px solid #eee;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  color: #333;
  background: #fff;
  box-sizing: border-box;
}

.rd-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rd-picker-arrow {
  color: #999;
  font-size: 14px;
}

.rd-section-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.org-card {
  background: #fff6f0;
  border-radius: 12px;
  padding: 12px;
}

.org-sub {
  display: block;
  font-size: 13px;
  color: #ff5c33;
  font-weight: 600;
  margin-bottom: 2px;
}

.org-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid rgba(255, 107, 53, 0.08);
}

.org-row:last-child {
  border-bottom: none;
}

.org-row-name {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #333;
  min-width: 0;
}

.org-row-tag {
  color: #999;
  font-size: 12px;
  margin-left: 4px;
}

/* 权限开关（42x24 橙 #FF6B35） */
.rd-switch {
  width: 42px;
  height: 24px;
  background: #e5e5e5;
  border-radius: 12px;
  position: relative;
  flex-shrink: 0;
}

.rd-switch.on {
  background: #ff6b35;
}

.rd-knob {
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: left 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.rd-switch.on .rd-knob {
  left: 20px;
}

/* 移出确认 */
.remove-body {
  text-align: center;
  padding-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.remove-icon-wrap {
  width: 56px;
  height: 56px;
  background: #fef2f2;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-icon {
  font-size: 26px;
  color: #ff5c33;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-top: 14px;
}

.modal-desc {
  font-size: 13px;
  color: #999;
  margin-top: 8px;
  line-height: 20px;
}
</style>
