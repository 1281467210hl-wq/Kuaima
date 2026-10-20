<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><image class="back-svg" src="/static/icons/boss-recruit-settings/chevron-left.svg" mode="aspectFit" /></view>
      <text class="wb-title">企业成员({{ members.length }})</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifdef H5 -->
      <view class="wb-capsule">
        <view class="cap-btn"><image class="cap-svg" src="/static/icons/boss-profile/ellipsis.svg" mode="aspectFit" /></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><image class="cap-svg cap-svg-dot" src="/static/icons/boss-profile/dot.svg" mode="aspectFit" /></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body">
      <!-- 搜索 -->
      <view class="wb-search">
        <image class="search-svg" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />
        <input type="text" v-model="keyword" placeholder="搜索成员" @confirm="loadMembers" />
      </view>

      <!-- 功能列表（从工作台「员工列表」进入时 src=staff，隐藏功能卡，与原型一致） -->
      <view class="wb-card" v-if="src !== 'staff'">
        <view class="wb-row" @click="navigateTo('member-apply-list')">
          <view class="wb-row-icon" style="background: linear-gradient(135deg, #FFB84D, #F09A3E)"><image class="row-svg" src="/static/icons/boss-members/file-signature-white.svg" mode="aspectFit" /></view>
          <view class="wb-row-main"><text class="wb-row-title">申请列表</text></view>
          <image class="arrow-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view class="wb-row" @click="navigateTo('member-invite')">
          <view class="wb-row-icon" style="background: linear-gradient(135deg, #FF8C5A, #FF6B35)"><image class="row-svg" src="/static/icons/boss-members/user-plus-white.svg" mode="aspectFit" /></view>
          <view class="wb-row-main"><text class="wb-row-title">邀请新成员</text></view>
          <image class="arrow-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view class="wb-row" @click="navigateTo('member-exit')">
          <view class="wb-row-icon" style="background: linear-gradient(135deg, #FF7743, #FF5C33)"><image class="row-svg" src="/static/icons/boss-members/ban-white.svg" mode="aspectFit" /></view>
          <view class="wb-row-main"><text class="wb-row-title">退出当前企业</text></view>
          <image class="arrow-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>

      <!-- 成员列表 -->
      <view class="wb-card" style="margin-top: 12px">
        <view v-if="loading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
        <view v-else-if="!members.length" class="wb-empty">
          <view class="wb-empty-icon"><image class="empty-svg" src="/static/icons/boss-members/users-gray.svg" mode="aspectFit" /></view>
          <text class="wb-empty-text">暂无企业成员</text>
        </view>
        <view v-for="m in members" :key="m.id" class="wb-row" @click="goDetail(m)">
          <view class="wb-avatar" :style="{ background: avatarColor(m.userId + '') }">{{ memberName(m).charAt(0) }}</view>
          <view class="wb-row-main">
            <text class="wb-row-title">{{ memberName(m) }}</text>
            <text class="wb-row-desc">{{ roleDesc(m) }}</text>
          </view>
          <image class="arrow-svg" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getMembers } from "@/api/enterprise";
import { avatarColor } from "@/api/project";

export default {
  data() {
    return {
      statusBarHeight: 44,
      keyword: "",
      src: "",
      members: [],
      loading: false,
      nameCache: {},
    };
  },
  onLoad(options) {
    this.src = (options && options.src) || "";
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadMembers();
  },
  onShow() {
    this.loadMembers();
  },
  methods: {
    avatarColor,
    async loadMembers() {
      this.loading = true;
      try {
        const data = await getMembers(this.keyword);
        this.members = data || [];
      } catch (e) {
        uni.showToast({ title: e.message || "加载失败", icon: "none" });
      } finally {
        this.loading = false;
      }
    },
    memberName(m) {
      return m.name || `成员${m.userId}`;
    },
    roleDesc(m) {
      const roleText = { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" }[m.role] || m.role;
      return m.title ? `${roleText} · ${m.title}` : roleText;
    },
    goDetail(m) {
      const suffix = this.src === "staff" ? "&src=staff" : "";
      uni.navigateTo({ url: `/pages/boss/member-detail?id=${m.id}&name=${encodeURIComponent(this.memberName(m))}${suffix}` });
    },
    navigateTo(page) {
      uni.navigateTo({ url: `/pages/boss/${page}` });
    },
    goBack() {
      uni.navigateBack({ fail: () => uni.reLaunch({ url: "/pages/boss/workbench" }) });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  padding: 8px 16px 12px;
  flex-shrink: 0;
}

.wb-back {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.back-svg {
  width: 16px;
  height: 16px;
}

.back-ico {
  font-size: 22px;
  color: #333;
}

.wb-title {
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
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

.cap-svg {
  width: 14px;
  height: 14px;
}

.cap-svg-dot {
  width: 9px;
  height: 9px;
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

.wb-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 12px;
  padding: 11px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.search-ico {
  font-size: 14px;
}

.search-svg {
  width: 15px;
  height: 15px;
}

.wb-search input {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.wb-card {
  background: #fff;
  border-radius: 16px;
  padding: 4px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.wb-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid #f5f5f5;
}

.wb-row:last-child {
  border-bottom: none;
}

.wb-row-icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.row-ico {
  font-size: 17px;
}

.row-svg {
  width: 18px;
  height: 18px;
}

.wb-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.wb-row-main {
  flex: 1;
  min-width: 0;
}

.wb-row-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  display: block;
}

.wb-row-desc {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.wb-row-arrow {
  font-size: 16px;
  color: #ccc;
}

.arrow-svg {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.wb-empty {
  padding: 30px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-icon {
  width: 84px;
  height: 84px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: #f5f0ea;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-svg {
  width: 34px;
  height: 34px;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}
</style>
