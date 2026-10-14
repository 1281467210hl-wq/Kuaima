<template>
  <view class="container">
    <BossPageHeader title="岗位详情" />

    <scroll-view scroll-y class="body" :style="{ paddingBottom: safeBottom + 70 + 'px' }">
      <!-- 岗位头 -->
      <view class="pd-head">
        <view class="pd-icon">
          <text class="pd-ico">💼</text>
        </view>
        <view class="pd-main">
          <view class="pd-name-line">
            <text class="pd-name">{{ position.name || "—" }}</text>
            <text v-if="position.status === 'on'" class="wb-tag green">在招中</text>
            <text v-else class="wb-tag gray">已停用</text>
          </view>
          <text class="pd-code">岗位编码：{{ position.code || "—" }}</text>
        </view>
      </view>

      <!-- 招聘进度 -->
      <view class="pd-progress">
        <view class="pd-step">
          <text class="pd-step-num">{{ position.hireCount || 0 }}</text>
          <text class="pd-step-label">招聘人数</text>
        </view>
        <view class="pd-step" @click="goApplicants">
          <text class="pd-step-num hl">{{ position.applyCount || 0 }}</text>
          <text class="pd-step-label">已投递</text>
        </view>
        <view class="pd-step" @click="goApplicants">
          <text class="pd-step-num hl">{{ position.interviewCount || 0 }}</text>
          <text class="pd-step-label">面试中</text>
        </view>
        <view class="pd-step" @click="goApplicants">
          <text class="pd-step-num">{{ position.hiredCount || 0 }}</text>
          <text class="pd-step-label">已录用</text>
        </view>
      </view>

      <!-- 岗位信息 -->
      <view class="card" style="padding: 2px 16px 10px">
        <view class="block-title">
          <text class="block-ico">ℹ️</text>岗位信息
          <text class="more-link" @click="goApplicants">查看申请 ›</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">岗位类别</text>
          <text class="pd-val">{{ position.category || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">所属部门</text>
          <text class="pd-val">{{ position.department || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">工作地点</text>
          <text class="pd-val">{{ position.location || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">薪资范围</text>
          <text class="pd-val orange">{{ position.salaryRange || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">工作经验</text>
          <text class="pd-val">{{ position.experience || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">学历要求</text>
          <text class="pd-val">{{ position.education || "—" }}</text>
        </view>
        <view class="pd-line">
          <text class="pd-key">发布时间</text>
          <text class="pd-val">{{ formatPublishTime(position.publishTime) }}</text>
        </view>
      </view>

      <!-- 岗位描述 -->
      <view class="card" style="padding: 2px 16px 14px">
        <view class="block-title">
          <text class="block-ico">📄</text>岗位描述
        </view>
        <text class="pd-desc">{{ position.description || "暂无岗位描述" }}</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部操作 -->
    <view class="footer">
      <view class="footer-btn outline" @click="goEdit">
        <text class="footer-btn-ico">✏️</text>编辑
      </view>
      <view class="footer-btn primary" @click="shareJob">
        <text class="footer-btn-ico">🔗</text>分享岗位
      </view>
    </view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import { getPosition } from "@/api/position";

export default {
  components: { BossPageHeader },
  data() {
    return {
      positionId: "",
      position: {},
      safeBottom: 0,
    };
  },
  onLoad(query) {
    this.positionId = query.id;
    this.load();
    try {
      const sysInfo = uni.getSystemInfoSync();
      this.safeBottom = sysInfo.safeAreaInsets?.bottom || 0;
    } catch (e) {
      // ignore
    }
  },
  onShow() {
    this.load();
  },
  methods: {
    async load() {
      try {
        const data = await getPosition(this.positionId).catch(() => ({}));
        this.position = data || {};
      } catch (e) {
        console.warn("岗位详情加载失败", e);
      }
    },
    formatPublishTime(time) {
      if (!time) return "—";
      const d = new Date(time);
      if (isNaN(d.getTime())) return time;
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      const h = String(d.getHours()).padStart(2, "0");
      const min = String(d.getMinutes()).padStart(2, "0");
      return `${y}-${m}-${day} ${h}:${min}`;
    },
    goEdit() {
      uni.navigateTo({ url: `/pages/boss/position-form?mode=edit&id=${this.positionId}` });
    },
    goApplicants() {
      // 原型 boss-position-detail.html「查看申请」→ boss-applicants.html（按岗位过滤）
      const job = encodeURIComponent(this.position?.name || "");
      uni.navigateTo({
        url: `/pages/boss/applicants?positionId=${this.positionId || ""}&job=${job}`,
      });
    },
    shareJob() {
      uni.showToast({ title: "原型演示：分享岗位到微信/抖音/复制链接", icon: "none" });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f3f4f6;
}
.body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 0;
}
.pd-head {
  background: #fff;
  border-radius: 16px;
  padding: 17px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  gap: 13px;
}
.pd-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #ffb84d, #f09a3e);
  display: flex;
  align-items: center;
  justify-content: center;
}
.pd-ico {
  font-size: 22px;
}
.pd-main {
  flex: 1;
  min-width: 0;
}
.pd-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pd-name {
  font-size: 18px;
  font-weight: 700;
  color: #222;
}
.wb-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.wb-tag.green {
  background: #e8f5e9;
  color: #4caf50;
}
.wb-tag.gray {
  background: #f3f4f6;
  color: #999;
}
.pd-code {
  font-size: 12px;
  color: #aaa;
  margin-top: 5px;
}
.pd-progress {
  background: #fff;
  border-radius: 14px;
  padding: 16px 6px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  display: flex;
}
.pd-step {
  flex: 1;
  text-align: center;
  cursor: pointer;
}
.pd-step-num {
  font-size: 20px;
  font-weight: 700;
  color: #333;
}
.pd-step-num.hl {
  color: #ff6b35;
}
.pd-step-label {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.block-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  padding: 15px 0 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.block-ico {
  font-size: 13px;
}
.more-link {
  margin-left: auto;
  font-size: 12px;
  color: #ff6b35;
  font-weight: 500;
  cursor: pointer;
}
.pd-line {
  display: flex;
  padding: 12px 0;
  border-bottom: 0.5px solid #f6f6f6;
  font-size: 13.5px;
}
.pd-line:last-child {
  border-bottom: none;
}
.pd-key {
  width: 80px;
  color: #999;
  flex-shrink: 0;
}
.pd-val {
  flex: 1;
  color: #333;
}
.pd-val.orange {
  color: #ff6b35;
  font-weight: 600;
}
.pd-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.8;
  padding: 10px 0 4px;
}
.bottom-space {
  height: 20px;
}
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 0.5px solid #f0f0f0;
  display: flex;
  gap: 12px;
}
.footer-btn {
  flex: 1;
  border-radius: 22px;
  padding: 11px 0;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.footer-btn.outline {
  background: #fff;
  color: #333;
  border: 1px solid #e0e0e0;
}
.footer-btn.primary {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  flex: 1.4;
}
.footer-btn-ico {
  margin-right: 5px;
}
</style>
