<template>
  <view class="container">
    <!-- 导航栏 -->
    <view class="nav-bar" :style="{ paddingTop: `${statusBarHeight}px`, height: `${statusBarHeight + 50}px` }">
      <view class="nav-back" @click="goBack">
        <image class="nav-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">设置薪资</text>
      <view class="nav-right" />
    </view>

    <view class="sub-tip">为已选择的发薪人员设置日薪与出勤天数，应发=日薪×天数</view>

    <scroll-view scroll-y class="body">
      <view v-if="loading" class="page-state">加载中...</view>
      <block v-else>
        <view v-if="!rows.length" class="empty-state">
          <view class="empty-icon"><image class="empty-svg" src="/static/icons/boss-workbench/users-empty.svg" mode="aspectFit" /></view>
          <text class="empty-text">请先添加人员</text>
        </view>
        <view v-for="(row, idx) in rows" :key="idx" class="salary-card">
          <view class="salary-head">
            <text class="salary-name">{{ row.name || '未填写姓名' }}</text>
            <text v-if="row.job" class="salary-job">{{ row.job }}</text>
            <text v-if="row.phone" class="salary-phone">{{ row.phone }}</text>
          </view>
          <view class="salary-inputs">
            <view class="salary-field">
              <text class="salary-field-label">日薪(元)</text>
              <input class="salary-input" type="digit" v-model="row.dailyWageYuan" placeholder="0" @input="amountTick++" />
            </view>
            <view class="salary-field">
              <text class="salary-field-label">出勤天数</text>
              <input class="salary-input" type="number" v-model="row.attendDays" placeholder="0" @input="amountTick++" />
            </view>
            <text class="salary-subtotal">¥{{ subtotal(row) }}</text>
          </view>
        </view>
        <view v-if="rows.length" class="salary-total">合计 ¥{{ totalYuan }} · {{ rows.length }} 人</view>
      </block>
      <view class="bottom-space" />
    </scroll-view>

    <view v-if="rows.length" class="salary-bar">
      <view class="salary-cancel" @click="goBack">取消</view>
      <view class="salary-ok" :class="{ disabled: saving }" @click="confirmSalary">
        {{ saving ? '保存中...' : '确定' }}
      </view>
    </view>
  </view>
</template>

<script>
import { getBossPayrollOrder, setBossPayrollSalary } from "@/api/backend";

function parsePayload(data) {
  return data?.data ?? data ?? {};
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      orderId: null,
      loading: false,
      saving: false,
      rows: [],
      amountTick: 0,
    };
  },
  computed: {
    totalYuan() {
      // eslint-disable-next-line no-unused-expressions
      this.amountTick;
      const total = this.rows.reduce((sum, r) => sum + this.subtotalValue(r), 0);
      return total.toFixed(2);
    },
  },
  onLoad(query) {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.orderId = query.orderId ? Number(query.orderId) : null;
    this.loadDetails();
  },
  methods: {
    goBack() {
      uni.navigateBack({
        fail: () => uni.reLaunch({ url: "/pages/boss/payroll" }),
      });
    },
    async loadDetails() {
      if (!this.orderId) {
        uni.showToast({ title: "发薪单不存在", icon: "none" });
        return;
      }
      this.loading = true;
      try {
        const data = await getBossPayrollOrder(this.orderId);
        const body = parsePayload(data);
        const details = Array.isArray(body.details) ? body.details : [];
        this.rows = details.map((d) => {
          const wageFen = Number(d.dailyWage || 0);
          return {
            userId: d.userId != null ? d.userId : null,
            name: d.name || "",
            phone: d.phone || "",
            job: d.job || "",
            dailyWageYuan: wageFen > 0 ? (wageFen / 100).toFixed(2) : "",
            attendDays: d.attendDays != null && d.attendDays > 0 ? String(d.attendDays) : "",
          };
        });
        this.amountTick++;
      } catch (error) {
        uni.showToast({ title: error?.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    subtotalValue(row) {
      const wage = Number(row.dailyWageYuan || 0);
      const days = Number(row.attendDays || 0);
      if (!Number.isFinite(wage) || !Number.isFinite(days)) return 0;
      return wage * days;
    },
    subtotal(row) {
      // eslint-disable-next-line no-unused-expressions
      this.amountTick;
      return this.subtotalValue(row).toFixed(2);
    },
    /** 校验并生成薪资（金额统一转「分」）。 */
    buildSalaries() {
      const salaries = [];
      for (const row of this.rows) {
        const wage = Number(row.dailyWageYuan || 0);
        const days = Number(row.attendDays || 0);
        if (!(wage > 0)) return { error: `请填写「${row.name || "未填写姓名"}」的日薪` };
        if (!(days > 0) || !Number.isInteger(days)) {
          return { error: `请填写「${row.name || "未填写姓名"}」的出勤天数（正整数）` };
        }
        salaries.push({
          userId: row.userId,
          phone: row.phone || null,
          dailyWage: Math.round(wage * 100),
          attendDays: days,
        });
      }
      return { salaries };
    },
    async confirmSalary() {
      if (!this.orderId) {
        uni.showToast({ title: "发薪单不存在", icon: "none" });
        return;
      }
      if (!this.rows.length) {
        uni.showToast({ title: "请先添加人员", icon: "none" });
        return;
      }
      const built = this.buildSalaries();
      if (built.error) {
        uni.showToast({ title: built.error, icon: "none" });
        return;
      }
      if (this.saving) return;
      this.saving = true;
      try {
        await setBossPayrollSalary(this.orderId, built.salaries);
        uni.showToast({ title: "薪资已设置", icon: "none" });
        uni.navigateBack({
          fail: () => uni.redirectTo({ url: "/pages/boss/payroll" }),
        });
      } catch (error) {
        uni.showToast({ title: error?.message || "保存失败", icon: "none" });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background: #f3f4f6;
  overflow-x: hidden;
  box-sizing: border-box;
}
.nav-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 16px 8px;
  background: #fff;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}
.nav-back, .nav-right { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.nav-svg { width: 16px; height: 16px; }
.nav-title { font-size: 17px; font-weight: 600; color: #333; }

.sub-tip { font-size: 12px; color: #999; text-align: center; padding: 6px 16px 4px; background: #fff; }

.body { flex: 1; min-height: 0; padding: 12px 16px 0; width: 100%; box-sizing: border-box; }
.page-state { padding: 32px 0; color: #999; font-size: 14px; text-align: center; }
.empty-state { text-align: center; padding: 60px 40px; }
.empty-icon {
  width: 84px; height: 84px; border-radius: 50%; background: #F5F0EA;
  display: flex; align-items: center; justify-content: center; margin: 0 auto 12px;
}
.empty-svg { width: 36px; height: 36px; }
.empty-text { font-size: 13px; color: #999; }

.salary-card {
  background: #fff; border-radius: 14px; padding: 12px; margin-bottom: 10px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04); width: 100%; box-sizing: border-box;
}
.salary-head { display: flex; align-items: center; gap: 8px; }
.salary-name { font-size: 14px; color: #333; font-weight: 600; flex-shrink: 0; }
.salary-job { font-size: 12px; color: #999; }
.salary-phone { font-size: 12px; color: #B0B0B0; margin-left: auto; }
.salary-inputs { display: flex; align-items: flex-end; gap: 8px; margin-top: 10px; }
.salary-field { flex: 1; min-width: 0; }
.salary-field-label { display: block; font-size: 11px; color: #999; margin-bottom: 4px; }
.salary-input {
  width: 100%; height: 36px; padding: 0 10px; box-sizing: border-box;
  border: 1px solid #e8e8e8; border-radius: 10px; font-size: 13px; color: #333; background: #fafafa;
}
.salary-subtotal {
  font-size: 13px; color: #FF6B35; font-weight: 600; flex-shrink: 0; min-width: 62px;
  text-align: right; padding-bottom: 9px;
}
.salary-total {
  font-size: 13px; color: #FF6B35; font-weight: 600; text-align: right; padding: 4px 4px 0;
}

.bottom-space { height: 12px; }

.salary-bar {
  display: flex; gap: 12px; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff; border-top: 1px solid #f0f0f0; flex-shrink: 0; width: 100%; box-sizing: border-box;
}
.salary-cancel {
  flex: 1; text-align: center; padding: 12px 0; border-radius: 22px;
  border: 1px solid #E5E5E5; background: #fff; color: #666; font-size: 14px;
}
.salary-ok {
  flex: 1; text-align: center; padding: 12px 0; border-radius: 22px;
  background: linear-gradient(135deg, #FF8C42, #FF6B35); color: #fff; font-size: 14px; font-weight: 600;
}
.salary-ok.disabled { opacity: .6; }
</style>
