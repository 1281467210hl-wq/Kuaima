<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><text class="back-ico">‹</text></view>
      <text class="wb-title">{{ isEdit ? "编辑员工" : "创建员工" }}</text>
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
      <!-- 温馨提示 -->
      <view v-if="!isEdit" class="tip-box">
        <text class="tip-ico">ℹ️</text>
        <text class="tip-text">员工创建后将收到邀请短信，首次登录需设置密码。</text>
      </view>

      <!-- 所属公司 -->
      <view class="company-card">
        <view class="company-icon">🏢</view>
        <view class="company-info">
          <text class="company-label">所属公司</text>
          <text class="company-name">{{ companyName || "我的企业" }}</text>
        </view>
        <text v-if="certified" class="company-badge">✓ 已认证</text>
      </view>

      <!-- 基础信息（1013 新增 职位 字段） -->
      <view class="form-card">
        <view class="form-item">
          <text class="form-label">员工姓名</text>
          <input class="form-input" type="text" v-model="form.name" placeholder="请输入真实姓名" />
        </view>
        <view class="form-item">
          <text class="form-label">职位</text>
          <input class="form-input" type="text" v-model="form.title" placeholder="请输入职位" />
        </view>
        <view class="form-item">
          <text class="form-label">性别</text>
          <picker class="form-picker" mode="selector" :range="genderOptions" :value="genderIndex" @change="onGenderPick">
            <view class="picker-value">
              <text class="picker-text" :class="{ placeholder: !form.gender }">{{ form.gender || "请选择性别" }}</text>
              <text class="picker-arrow">›</text>
            </view>
          </picker>
        </view>
        <view class="form-item">
          <text class="form-label">身份证号</text>
          <input class="form-input" type="idcard" maxlength="18" v-model="form.idCard" placeholder="请输入18位身份证号" />
        </view>
        <view class="form-item">
          <text class="form-label">手机号</text>
          <text class="phone-prefix">+86</text>
          <input class="form-input" type="number" maxlength="11" v-model="form.phone" :placeholder="isEdit ? form.phone || '手机号' : '请输入11位手机号'" disabled />
        </view>
        <view class="form-item">
          <text class="form-label">验证码</text>
          <input class="form-input" type="number" maxlength="6" v-model="form.code" placeholder="请输入6位验证码" />
          <view class="sms-btn" :class="{ disabled: countdown > 0 }" @click="sendSms">
            <text>{{ countdown > 0 ? countdown + "s 后重试" : "获取验证码" }}</text>
          </view>
        </view>
      </view>

      <!-- 角色选择（1013：白卡分段式 + 右上勾选圆点） -->
      <view class="role-title">选择员工角色</view>
      <view class="role-opts">
        <view class="role-opt" :class="{ active: form.role === 'STAFF' }" @click="form.role = 'STAFF'">
          <view class="r-check" :class="{ checked: form.role === 'STAFF' }"><text v-if="form.role === 'STAFF'" class="r-check-ico">✓</text></view>
          <view class="r-icon">👤</view>
          <text class="r-name">成员</text>
          <text class="r-desc">仅可打卡考勤{"\n"}查看工资单</text>
        </view>
        <view class="role-opt" :class="{ active: form.role === 'ADMIN' }" @click="form.role = 'ADMIN'">
          <view class="r-check" :class="{ checked: form.role === 'ADMIN' }"><text v-if="form.role === 'ADMIN'" class="r-check-ico">✓</text></view>
          <view class="r-icon">🛡</view>
          <text class="r-name">管理员</text>
          <text class="r-desc">管理岗位与成员{"\n"}制单发薪审批</text>
        </view>
      </view>

      <!-- 备注 -->
      <view class="form-card mt16">
        <view class="form-item">
          <text class="form-label">备注</text>
          <input class="form-input" type="text" v-model="form.remark" placeholder="选填，备注信息" />
        </view>
      </view>
    </view>

    <!-- 底部提交 -->
    <view class="footer-bar">
      <view class="submit-btn" @click="submitForm"><text>{{ isEdit ? "保存修改" : "创建员工" }}</text></view>
    </view>
  </view>
</template>

<script>
import { getMemberDetail, updateMemberRole, getExitInfo } from "@/api/enterprise";
import { request } from "@/api/http";

export default {
  data() {
    return {
      statusBarHeight: 44,
      memberId: "",
      userId: "",
      loading: false,
      submitting: false,
      companyName: "",
      certified: false,
      countdown: 0,
      timer: null,
      genderOptions: ["男", "女"],
      form: { name: "", title: "", gender: "", idCard: "", phone: "", code: "", remark: "", role: "STAFF" },
      // 编辑模式初始值：保存时按改动分别提交，姓名仅提示（sys_user 域，接口不改）
      original: { name: "", title: "", gender: "", idCard: "", remark: "", role: "STAFF" },
    };
  },
  computed: {
    isEdit() {
      return !!this.memberId;
    },
    // picker 当前选中索引：按 男/女 反查，未选时回退 0（占位文案由 form.gender 是否为空控制）
    genderIndex() {
      const i = this.genderOptions.indexOf(this.form.gender);
      return i < 0 ? 0 : i;
    },
  },
  onLoad(options) {
    this.memberId = options.id || "";
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadCompany();
    if (this.memberId) this.loadDetail();
  },
  onUnload() {
    if (this.timer) clearInterval(this.timer);
  },
  methods: {
    async loadCompany() {
      try {
        const info = await getExitInfo();
        this.companyName = (info && info.companyName) || "";
        this.certified = !!(info && info.certified);
      } catch (e) {}
    },
    async loadDetail() {
      this.loading = true;
      try {
        const d = (await getMemberDetail(this.memberId)) || {};
        // info/permissions 接口路径参数是 sys_user 的 userId，非成员表主键
        this.userId = d.userId != null ? String(d.userId) : "";
        this.form.name = d.name || "";
        this.form.title = d.title || "";
        // 性别后端为 male/female，回填展示用男/女；身份证号后端已脱敏，仅作展示、
        // 保存时仅提交完整 18 位值（脱敏串不覆盖后端）
        this.form.gender = this.codeToGender(d.gender);
        this.form.idCard = d.idCard && d.idCard !== "—" ? d.idCard : "";
        this.form.phone = d.phone || "";
        this.form.remark = d.remark || "";
        this.form.role = d.role === "ADMIN" ? "ADMIN" : "STAFF";
        this.original = {
          name: this.form.name,
          title: this.form.title,
          gender: this.form.gender,
          idCard: this.form.idCard,
          remark: this.form.remark,
          role: this.form.role,
        };
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    onGenderPick(e) {
      this.form.gender = this.genderOptions[Number(e.detail.value)] || "";
    },
    /** 展示值 男/女 → 后端码 male/female；未选返回空串。 */
    genderToCode(gender) {
      if (gender === "男") return "male";
      if (gender === "女") return "female";
      return "";
    },
    /** 后端码 male/female（兼容已是中文）→ 展示值 男/女；无值返回空串。 */
    codeToGender(code) {
      if (code === "male" || code === "男") return "男";
      if (code === "female" || code === "女") return "女";
      return "";
    },
    /** 身份证号仅在完整 18 位（数字 + 末位校验位 X/x）时提交，脱敏串不写回后端。 */
    fullIdCardOrEmpty(value) {
      const v = String(value || "").trim();
      return /^[0-9Xx]{18}$/.test(v) ? v : "";
    },
    async sendSms() {
      if (this.countdown > 0) return;
      const phone = String(this.form.phone || "").trim();
      if (!/^1\d{10}$/.test(phone)) {
        uni.showToast({ title: "请输入正确的11位手机号", icon: "none" });
        return;
      }
      try {
        // POST /boss/sms/code {phone} → { phone, expiresIn:60, devMode }
        const res = await request({ url: "/boss/sms/code", method: "POST", data: { phone } });
        const seconds = Number(res && res.expiresIn) > 0 ? Number(res.expiresIn) : 60;
        this.countdown = seconds;
        if (this.timer) clearInterval(this.timer);
        this.timer = setInterval(() => {
          this.countdown--;
          if (this.countdown <= 0) clearInterval(this.timer);
        }, 1000);
        uni.showToast({
          title: res && res.devMode ? "验证码已发送（开发模式固定码 123456）" : "验证码已发送",
          icon: "none",
        });
      } catch (e) {
        uni.showToast({ title: e.message || "验证码发送失败", icon: "none" });
      }
    },
    async submitForm() {
      const name = String(this.form.name || "").trim();
      const phone = String(this.form.phone || "").trim();
      const code = String(this.form.code || "").trim();
      if (!name) {
        uni.showToast({ title: "请输入员工姓名", icon: "none" });
        return;
      }
      if (!/^1\d{10}$/.test(phone) && !this.isEdit) {
        uni.showToast({ title: "请输入正确的手机号", icon: "none" });
        return;
      }
      if (!this.isEdit && code.length !== 6) {
        uni.showToast({ title: "请输入6位验证码", icon: "none" });
        return;
      }
      if (this.submitting) return;
      this.submitting = true;
      try {
        if (this.isEdit) {
          await this.saveEdit(name);
        } else {
          // 创建前先校验短信验证码：POST /boss/sms/verify {phone, code}，通过后再发邀请
          await request({ url: "/boss/sms/verify", method: "POST", data: { phone, code } });
          // 邀请制创建：仅手机号 + 角色可即时落库，姓名/职位/性别/身份证随请求透传。
          // TODO 后端 POST /boss/enterprise/invites 目前只按 phone+role 建邀请（无成员行），
          // 性别/身份证号此处透传但不保证持久化；待邀请/创建接口支持身份字段后由后端落库，
          // 年龄由后端按完整身份证计算（创建页不采集年龄）。
          const gender = this.genderToCode(this.form.gender);
          const idCard = this.fullIdCardOrEmpty(this.form.idCard);
          await request({
            url: "/boss/enterprise/invites",
            method: "POST",
            data: {
              phone,
              role: this.form.role,
              gender: gender || undefined,
              idCard: idCard || undefined,
            },
          });
          uni.showToast({ title: "邀请已发送，员工创建成功！", icon: "none" });
        }
        setTimeout(() => this.goBack(), 600);
      } catch (e) {
        uni.showToast({ title: e.message || "操作失败", icon: "none" });
      } finally {
        this.submitting = false;
      }
    },
    /** 编辑保存：姓名改动仅提示不落库（姓名属 sys_user 域）；职位/备注走 PUT info；角色走 PUT role。 */
    async saveEdit(name) {
      if (name !== this.original.name) {
        uni.showToast({ title: "员工姓名由用户中心维护，此处不支持修改", icon: "none" });
      }
      const ops = [];
      const titleChanged = this.form.title !== this.original.title;
      const remarkChanged = this.form.remark !== this.original.remark;
      const newGender = this.genderToCode(this.form.gender);
      const genderChanged = !!newGender && newGender !== this.genderToCode(this.original.gender);
      const newIdCard = this.fullIdCardOrEmpty(this.form.idCard);
      const idCardChanged = !!newIdCard && newIdCard !== this.original.idCard;
      if (titleChanged || remarkChanged || genderChanged || idCardChanged) {
        const payload = {};
        const labels = [];
        if (titleChanged) { payload.title = this.form.title; labels.push("职位"); }
        if (remarkChanged) { payload.remark = this.form.remark; labels.push("备注"); }
        if (genderChanged) { payload.gender = newGender; labels.push("性别"); }
        if (idCardChanged) { payload.idCard = newIdCard; labels.push("身份证号"); }
        // 年龄由后端按完整身份证自动计算，编辑页不采集/不下发 age，避免覆盖派生值。
        ops.push({
          label: labels.join("/"),
          run: () =>
            request({
              url: `/boss/enterprise/members/${this.userId}/info`,
              method: "PUT",
              data: payload,
            }),
        });
      }
      if (this.form.role !== this.original.role) {
        ops.push({ label: "角色", run: () => updateMemberRole(this.memberId, this.form.role) });
      }
      if (!ops.length) {
        uni.showToast({ title: "没有需要保存的修改", icon: "none" });
        return;
      }
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
      if (failed.length === ops.length) throw new Error(lastError || "保存失败");
      if (failed.length) {
        uni.showToast({ title: `${done.join("、")}已保存，${failed.join("、")}保存失败`, icon: "none" });
      } else {
        uni.showToast({ title: "已保存修改", icon: "none" });
      }
    },
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: this.memberId ? "/pages/boss/member-detail" : "/pages/boss/members" }),
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f5f5f5;
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
  background: rgba(255, 255, 255, 0.8);
  border-radius: 9999px;
  padding: 3px 6px;
  gap: 2px;
}

.cap-btn {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cap-ico {
  font-size: 10px;
  color: #555;
}

.cap-divider {
  width: 1px;
  height: 10px;
  background: #ddd;
}

.wb-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.wb-body.has-footer {
  padding-bottom: 110px;
}

/* 温馨提示 */
.tip-box {
  padding: 12px 14px;
  background: #fff8e6;
  border-radius: 10px;
  border: 1px solid #ffe8b0;
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.tip-ico {
  font-size: 13px;
  margin-top: 2px;
  flex-shrink: 0;
}

.tip-text {
  font-size: 12px;
  color: #8b4513;
  line-height: 1.7;
  flex: 1;
}

/* 所属公司卡片 */
.company-card {
  background: #fff;
  border-radius: 14px;
  padding: 18px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  gap: 14px;
}

.company-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff6b35, #ffa14e);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
}

.company-info {
  flex: 1;
  min-width: 0;
}

.company-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
  display: block;
}

.company-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.company-sub {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.company-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 9999px;
  background: #f0fdf4;
  color: #16a34a;
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}

/* 表单卡片 */
.form-card {
  background: #fff;
  border-radius: 14px;
  padding: 6px 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.form-card.mt16 {
  margin-top: 16px;
  margin-bottom: 0;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid #f5f5f5;
  overflow: hidden;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  font-size: 14px;
  color: #666;
  width: 72px;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #333;
  min-width: 0;
}

/* 性别选择器（复用 form-item 行布局：左标签 + 右 picker，未选显示占位灰字） */
.form-picker {
  flex: 1;
  min-width: 0;
}
.picker-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  color: #333;
}
.picker-text.placeholder {
  color: #999;
}
.picker-arrow {
  color: #ccc;
  font-size: 16px;
}

.phone-prefix {
  font-size: 15px;
  color: #333;
  margin-right: 4px;
  padding-right: 10px;
  border-right: 1px solid #eee;
  flex-shrink: 0;
}

.sms-btn {
  background: #ff6b35;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  border-radius: 9999px;
  padding: 7px 12px;
  flex-shrink: 0;
  white-space: nowrap;
}

.sms-btn.disabled {
  background: #ddd;
}

/* 角色选择（1013 分段式） */
.role-title {
  font-size: 14px;
  color: #333;
  font-weight: 600;
  margin: 4px 2px 10px;
}

.role-opts {
  display: flex;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.role-opt {
  flex: 1;
  padding: 18px 10px 14px;
  text-align: center;
  background: #fff;
  position: relative;
}

.role-opt + .role-opt {
  border-left: 1px solid #f2f2f2;
}

.role-opt.active {
  background: #fff6f0;
}

.r-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid #ddd;
  display: flex;
  align-items: center;
  justify-content: center;
}

.r-check.checked {
  background: #ff6b35;
  border-color: #ff6b35;
}

.r-check-ico {
  color: #fff;
  font-size: 10px;
}

.r-icon {
  width: 40px;
  height: 40px;
  margin: 0 auto 8px;
  border-radius: 50%;
  background: #f5f5f5;
  color: #999;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.role-opt.active .r-icon {
  background: linear-gradient(135deg, #ff7743, #ff9a5a);
  color: #fff;
}

.r-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.role-opt.active .r-name {
  color: #ff6b35;
}

.r-desc {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  line-height: 15px;
}

/* 底部提交 */
.footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.05);
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 24px;
  background: linear-gradient(135deg, #ff8c5a, #ff6b35);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(255, 107, 53, 0.3);
}

.submit-btn text {
  font-size: 16px;
  font-weight: 600;
}
</style>
