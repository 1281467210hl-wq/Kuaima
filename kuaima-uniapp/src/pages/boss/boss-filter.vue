<template>
  <view class="container">
    <!-- 状态栏 -->
    <view class="status-bar">
      <text>19:48</text>
      <view class="status-icons">
        <text>📶</text>
        <text>📡</text>
        <text>🔋</text>
      </view>
    </view>

    <!-- 顶部导航 -->
    <view class="top-nav">
      <view class="nav-back" @click="goBack">
        <text style="color: #333; font-size: 18px">←</text>
      </view>
      <text class="nav-title">筛选</text>
      <view class="nav-right">
        <view class="nav-btn">
          <text style="color: #333; font-size: 13px">⋯</text>
        </view>
        <view
          style="width: 1px; height: 14px; background: #ddd; margin: 0 2px"
        ></view>
        <view class="nav-btn">
          <text style="color: #333; font-size: 11px">●</text>
        </view>
      </view>
    </view>

    <!-- 内容区 -->
    <scroll-view scroll-y class="content-area">
      <!-- 招工日期 -->
      <view class="filter-section">
        <text class="section-title">招工日期</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in dateOptions"
            :key="index"
            class="filter-option"
            :class="{ active: dateActive === index }"
            @click="dateActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 工种类型 -->
      <view class="filter-section">
        <text class="section-title">工种类型</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in jobTypeOptions"
            :key="index"
            class="filter-option"
            :class="{ active: jobTypeActive === index }"
            @click="jobTypeActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 薪资范围 -->
      <view class="filter-section">
        <text class="section-title">薪资范围</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in salaryOptions"
            :key="index"
            class="filter-option"
            :class="{ active: salaryActive === index }"
            @click="salaryActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 工作地点 -->
      <view class="filter-section">
        <text class="section-title">工作地点</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in locationOptions"
            :key="index"
            class="filter-option"
            :class="{ active: locationActive === index }"
            @click="locationActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 零工经验 -->
      <view class="filter-section">
        <text class="section-title">零工经验</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in experienceOptions"
            :key="index"
            class="filter-option"
            :class="{ active: experienceActive === index }"
            @click="experienceActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 性别要求 -->
      <view class="filter-section">
        <text class="section-title">性别要求</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in genderOptions"
            :key="index"
            class="filter-option"
            :class="{ active: genderActive === index }"
            @click="genderActive = index"
            >{{ item }}</text
          >
        </view>
      </view>

      <!-- 特色标签 -->
      <view class="filter-section">
        <text class="section-title">特色标签</text>
        <view class="filter-options">
          <text
            v-for="(item, index) in tagOptions"
            :key="index"
            class="filter-option"
            :class="{ active: tagActive.includes(index) }"
            @click="toggleTag(index)"
          >{{ item }}</text
          >
        </view>
      </view>

      <view class="scroll-bottom-space"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <button class="reset-btn" @click="resetFilter">重置</button>
      <button class="confirm-btn" @click="confirmFilter">确认筛选</button>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      dateOptions: ["全部", "今天", "明天", "后天", "本周内", "本月内"],
      dateActive: 0,
      jobTypeOptions: [
        "全部",
        "餐饮服务",
        "快递配送",
        "仓储物流",
        "制造业",
        "建筑装修",
        "零售促销",
        "家政服务",
      ],
      jobTypeActive: 0,
      salaryOptions: [
        "不限",
        "100元以下",
        "100-200元",
        "200-300元",
        "300元以上",
        "日结周结",
      ],
      salaryActive: 0,
      locationOptions: ["不限", "3公里内", "5公里内", "10公里内", "同城"],
      locationActive: 0,
      experienceOptions: [
        "不限",
        "新手可做",
        "有经验优先",
        "熟练工",
        "技术工人",
      ],
      experienceActive: 0,
      genderOptions: ["不限", "男", "女", "男女均可"],
      genderActive: 0,
      tagOptions: [
        "包吃住",
        "有空调",
        "日结",
        "可长期",
        "报销路费",
        "提供工具",
      ],
      tagActive: [],
    };
  },
  onLoad() {
    const saved = uni.getStorageSync("bossOrderFilter");
    if (!saved || typeof saved !== "object") return;
    this.dateActive = Math.max(0, this.dateOptions.indexOf(saved.date));
    this.jobTypeActive = Math.max(
      0,
      this.jobTypeOptions.indexOf(saved.jobType),
    );
    this.salaryActive = Math.max(0, this.salaryOptions.indexOf(saved.salary));
    this.locationActive = Math.max(
      0,
      this.locationOptions.indexOf(saved.location),
    );
    this.experienceActive = Math.max(
      0,
      this.experienceOptions.indexOf(saved.experience),
    );
    this.genderActive = Math.max(0, this.genderOptions.indexOf(saved.gender));
    this.tagActive = Array.isArray(saved.tags)
      ? saved.tags
          .map((tag) => this.tagOptions.indexOf(tag))
          .filter((index) => index >= 0)
      : [];
  },
  methods: {
    goBack() {
      uni.navigateBack();
    },
    toggleTag(index) {
      const idx = this.tagActive.indexOf(index);
      if (idx >= 0) {
        this.tagActive.splice(idx, 1);
      } else {
        this.tagActive.push(index);
      }
    },
    resetFilter() {
      this.dateActive = 0;
      this.jobTypeActive = 0;
      this.salaryActive = 0;
      this.locationActive = 0;
      this.experienceActive = 0;
      this.genderActive = 0;
      this.tagActive = [];
      uni.removeStorageSync("bossOrderFilter");
      uni.$emit("filterChanged", null);
      uni.navigateBack();
    },
    confirmFilter() {
      const filterData = {
        date: this.dateOptions[this.dateActive],
        jobType: this.jobTypeOptions[this.jobTypeActive],
        salary: this.salaryOptions[this.salaryActive],
        location: this.locationOptions[this.locationActive],
        experience: this.experienceOptions[this.experienceActive],
        gender: this.genderOptions[this.genderActive],
        tags: this.tagActive.map((i) => this.tagOptions[i]),
      };
      uni.setStorageSync("bossOrderFilter", filterData);
      uni.$emit("filterChanged", filterData);
      uni.navigateBack();
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.status-bar {
  height: 47px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  background: #fff;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.top-nav {
  padding: 8px 16px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-area {
  flex: 1;
  width: 100%;
  min-height: 0;
  box-sizing: border-box;
  overflow-y: auto;
  padding: 16px 20px 0;
}

.filter-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  display: block;
}

.filter-options {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 10px;
}

.filter-option {
  box-sizing: border-box;
  max-width: 100%;
  padding: 8px 16px;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  font-size: 13px;
  color: #666;
}

.scroll-bottom-space {
  height: calc(112px + env(safe-area-inset-bottom));
}

.filter-option.active {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
  color: #fff;
  border-color: transparent;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  min-height: 88px;
  box-sizing: border-box;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 24px calc(14px + env(safe-area-inset-bottom));
  z-index: 10;
  gap: 16px;
}

.reset-btn {
  flex: 1;
  height: 48px;
  margin: 0;
  padding: 0;
  background: #f5f5f5;
  border: none;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 500;
  color: #666;
  line-height: 48px;
}

.confirm-btn {
  flex: 1;
  height: 48px;
  margin: 0;
  padding: 0;
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
  border: none;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  line-height: 48px;
  box-shadow: 0 4px 20px rgba(59, 130, 246, 0.3);
}
</style>
