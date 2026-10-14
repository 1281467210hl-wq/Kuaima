<template>
  <view class="container">
    <view class="page-bg">
      <!-- 头部 -->
      <view
        class="modal-header"
        :style="{ paddingTop: `${statusBarHeight + 14}px` }"
      >
        <text class="modal-title">更多工种</text>
        <text class="modal-desc">选对工种，免费推荐更多熟手</text>
      </view>

      <!-- Tab切换 -->
      <view class="tabs">
        <text class="tab-item" :class="{ active: currentTab === 'industry' }" @click="switchTab('industry')">请选择行业</text>
        <text class="tab-item" :class="{ active: currentTab === 'type' }" @click="switchTab('type')">请选择企业类型</text>
        <text class="tab-item" :class="{ active: currentTab === 'job' }" @click="switchTab('job')">请选择工种</text>
      </view>

      <!-- Tab内容 -->
      <scroll-view scroll-y class="tab-content">
        <!-- 行业选择 -->
        <view class="tab-pane" :class="{ active: currentTab === 'industry' }">
          <view v-if="loading" class="state">分类加载中...</view>
          <view v-else-if="loadError" class="state error" @click="loadCategories">分类加载失败，点击重试</view>
          <view v-else-if="!industries.length" class="state">暂无行业数据</view>
          <view v-else class="industry-item" v-for="item in industries" :key="item.id" @click="selectIndustry(item)">
            <text>{{ item.name }}</text>
            <text class="arrow-icon">›</text>
          </view>
        </view>

        <!-- 企业类型选择 -->
        <view class="tab-pane" :class="{ active: currentTab === 'type' }">
          <view v-if="loading" class="state">分类加载中...</view>
          <view v-else-if="loadError" class="state error" @click="loadCategories">分类加载失败，点击重试</view>
          <view v-else-if="!currentEnterpriseTypes.length" class="state">暂无企业类型数据</view>
          <view v-else class="type-item" :class="{ selected: isEnterpriseSelected(item) }" v-for="item in currentEnterpriseTypes" :key="item.id" @click="selectType(item)">
            <text>{{ item.name }}</text>
            <view class="check" v-if="isEnterpriseSelected(item)">
              <text class="check-icon">✓</text>
            </view>
          </view>
          <view v-if="!loading && !loadError && selectedIndustry" class="type-item add-type-item" @click="addCustomEnterpriseType">
            <text>其他企业类型</text>
            <text class="add-type-icon">＋</text>
          </view>
        </view>

        <!-- 工种选择 -->
        <view class="tab-pane" :class="{ active: currentTab === 'job' }">
          <view v-if="loading" class="state">分类加载中...</view>
          <view v-else-if="loadError" class="state error" @click="loadCategories">分类加载失败，点击重试</view>
          <view v-else-if="!currentJobs.length" class="state">暂无工种数据</view>
          <view v-else class="job-row" :class="{ selected: selectedJobIds.includes(job.id) }" v-for="job in currentJobs" :key="job.id" @click="toggleJob(job)">
            <view class="job-info">
              <text class="job-name">{{ job.name }}</text>
              <text class="job-desc">{{ job.description || "暂无说明" }}</text>
            </view>
            <view class="checkbox">
              <text v-if="selectedJobIds.includes(job.id)" class="checkbox-icon">✓</text>
            </view>
          </view>
          <view v-if="!loading && !loadError && selectedEnterpriseTypeId !== null" class="job-row add-job-row" @click="addCustomJob">
            <view class="job-info">
              <text class="job-name">其他工种</text>
              <text class="job-desc">点击添加新的工种</text>
            </view>
            <text class="add-type-icon">＋</text>
          </view>
        </view>
      </scroll-view>

      <!-- 底部按钮 -->
      <view class="bottom-bar" :style="{ paddingBottom: `calc(12px + ${safeBottom}px)` }">
        <button class="btn-prev" @click="prevStep">{{ prevLabel }}</button>
          <button class="btn-next" :disabled="!canNext || loading || loadError" @click="nextStep">{{ nextLabel }}</button>
      </view>
    </view>
  </view>
</template>

<script>
import {
  createJobCategory,
  createJobEnterpriseType,
  listJobCategoryTree,
} from "@/api/backend";

function extractRows(result) {
  if (Array.isArray(result)) return result;
  if (Array.isArray(result?.data)) return result.data;
  if (Array.isArray(result?.records)) return result.records;
  if (Array.isArray(result?.content)) return result.content;
  if (Array.isArray(result?.list)) return result.list;
  if (Array.isArray(result?.data?.records)) return result.data.records;
  if (Array.isArray(result?.data?.content)) return result.data.content;
  return [];
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      safeBottom: 0,
      orderId: "",
      currentTab: "industry",
      industries: [],
      selectedIndustryId: null,
      selectedEnterpriseTypeId: null,
      selectedJobIds: [],
      loading: false,
      loadError: "",
    };
  },
  onLoad(options = {}) {
    try {
      const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
      this.statusBarHeight = Number(info.statusBarHeight || 0);
      this.safeBottom = Number(info.safeAreaInsets?.bottom || 0);
      this.orderId = options.id || "";
    } catch (_) {}
    if (!this.orderId) {
      // 从新建流程进入时清理上次编辑留下的跨页缓存；编辑已有岗位则保留回填数据。
      [
        "taskContent",
        "genderAgeSelection",
        "workLocationSelection",
        "workTimeSelection",
        "recruitSettings",
        "jobCategorySelection",
      ].forEach((key) => uni.removeStorageSync(key));
    }
    this.loadCategories();
  },
  computed: {
    selectedIndustry() {
      return this.industries.find((item) => item.id === this.selectedIndustryId) || null;
    },
    currentEnterpriseTypes() {
      const types = Array.isArray(this.selectedIndustry?.enterpriseTypes)
        ? this.selectedIndustry.enterpriseTypes
        : [];
      return types;
    },
    selectedEnterpriseType() {
      return this.currentEnterpriseTypes.find(
        (item) => item.id === this.selectedEnterpriseTypeId,
      ) || null;
    },
    currentJobs() {
      if (!this.selectedEnterpriseType) return [];
      const enterprise = this.selectedEnterpriseType;
      return (Array.isArray(enterprise.jobs) ? enterprise.jobs : []).map((job) => ({
        ...job,
        enterpriseTypeId: job.enterpriseTypeId ?? enterprise.id,
        enterpriseTypeName: enterprise.name,
      }));
    },
    selectedJobs() {
      return this.currentJobs.filter((item) => this.selectedJobIds.includes(item.id));
    },
    canNext() {
      if (this.currentTab === "industry") return Boolean(this.selectedIndustryId);
      if (this.currentTab === "type") {
        return this.selectedEnterpriseTypeId !== null && this.selectedEnterpriseTypeId !== undefined;
      }
      return this.selectedJobIds.length > 0;
    },
    prevLabel() {
      return this.currentTab === "industry" ? "取消" : "上一步";
    },
    nextLabel() {
      if (this.currentTab !== "job") return "下一步";
      return this.selectedJobIds.length ? `完成 (${this.selectedJobIds.length})` : "完成";
    },
  },
  methods: {
    async loadCategories() {
      this.loading = true;
      this.loadError = "";
      try {
        const result = await listJobCategoryTree();
        this.industries = extractRows(result).filter(
          (item) => item && item.id !== undefined && item.id !== null,
        );
        this.restoreSelection();
      } catch (error) {
        this.industries = [];
        this.loadError = error.message || "分类加载失败";
      } finally {
        this.loading = false;
      }
    },
    restoreSelection() {
      const saved = uni.getStorageSync("jobCategorySelection");
      if (!saved || typeof saved !== "object") return;
      const industryId = Number(saved.industryId);
      if (!this.industries.some((item) => Number(item.id) === industryId)) return;
      this.selectedIndustryId = this.industries.find(
        (item) => Number(item.id) === industryId,
      )?.id;
      const validTypeIds = new Set(this.currentEnterpriseTypes.map((item) => String(item.id)));
      const savedTypeId = Array.isArray(saved.enterpriseTypeIds)
        ? saved.enterpriseTypeIds[0]
        : saved.enterpriseTypeId;
      const typeIdStr = savedTypeId !== undefined && savedTypeId !== null ? String(savedTypeId) : "";
      this.selectedEnterpriseTypeId = typeIdStr && validTypeIds.has(typeIdStr)
        ? this.currentEnterpriseTypes.find((item) => String(item.id) === typeIdStr)?.id ?? null
        : null;
      const validJobIds = new Set(this.currentJobs.map((item) => String(item.id)));
      this.selectedJobIds = (saved.jobIds || [])
        .map(String)
        .filter((id) => validJobIds.has(id))
        .map((id) => this.currentJobs.find((item) => String(item.id) === id)?.id);
    },
    goBack() {
      uni.navigateBack();
    },
    switchTab(tab) {
      if (tab === "type" && !this.selectedIndustryId) return;
      if (tab === "job" && this.selectedEnterpriseTypeId === null) return;
      this.currentTab = tab;
    },
    selectIndustry(industry) {
      if (this.selectedIndustryId !== industry.id) {
        this.selectedEnterpriseTypeId = null;
        this.selectedJobIds = [];
      }
      this.selectedIndustryId = industry.id;
      this.currentTab = "type";
    },
    addCustomEnterpriseType() {
      if (!this.selectedIndustryId) return;
      uni.showModal({
        title: "填写企业类型名称",
        editable: true,
        placeholderText: "请输入",
        success: async (result) => {
          if (!result.confirm) return;
          const name = String(result.content || "").trim();
          if (!name) {
            uni.showToast({ title: "请输入企业类型名称", icon: "none" });
            return;
          }
          const exists = [...this.currentEnterpriseTypes].some((item) => item.name === name);
          if (exists) {
            uni.showToast({ title: "该企业类型已存在", icon: "none" });
            return;
          }
          this.loading = true;
          try {
          const result = await createJobEnterpriseType({
            industryId: this.selectedIndustryId,
            name,
          });
            await this.loadCategories();
            const created = this.currentEnterpriseTypes.find(
              (item) => item.name === name || item.id === result?.id,
            );
            if (created) {
              this.selectedEnterpriseTypeId = created.id;
              this.selectedJobIds = [];
              this.currentTab = "type";
            }
            uni.showToast({ title: "企业类型已添加", icon: "success" });
          } catch (error) {
            uni.showToast({
              title: error.message || "企业类型添加失败，请联系管理员",
              icon: "none",
            });
          } finally {
            this.loading = false;
          }
        },
      });
    },
    selectType(enterprise) {
      if (this.selectedEnterpriseTypeId === enterprise.id) {
        this.selectedEnterpriseTypeId = null;
        this.selectedJobIds = [];
      } else {
        const current = this.currentEnterpriseTypes.find(
          (item) => item.id === this.selectedEnterpriseTypeId,
        );
        const retainedIds = new Set(
          (current?.jobs || []).map((job) => String(job.id)),
        );
        this.selectedJobIds = this.selectedJobIds.filter((id) =>
          retainedIds.has(String(id)),
        );
        this.selectedEnterpriseTypeId = enterprise.id;
      }
    },
    isEnterpriseSelected(enterprise) {
      return this.selectedEnterpriseTypeId === enterprise.id;
    },
    async addCustomJob() {
      if (!this.selectedIndustryId || this.selectedEnterpriseTypeId === null) return;
      uni.showModal({
        title: "填写工种名称",
        editable: true,
        placeholderText: "请输入工种名称",
        success: async (result) => {
          if (!result.confirm) return;
          const name = String(result.content || "").trim();
          if (!name) {
            uni.showToast({ title: "请输入工种名称", icon: "none" });
            return;
          }
          uni.showModal({
            title: "填写工种说明（可选）",
            editable: true,
            placeholderText: "请输入工种说明",
            success: async (descriptionResult) => {
              if (!descriptionResult.confirm) return;
              this.loading = true;
              try {
                const response = await createJobCategory({
                  industryId: this.selectedIndustryId,
                  enterpriseTypeId: this.selectedEnterpriseTypeId,
                  name,
                  description: String(descriptionResult.content || "").trim(),
                });
                await this.loadCategories();
                const ids = response?.id
                  ? [response.id]
                  : this.currentJobs
                      .filter((job) => job.name === name)
                      .map((job) => job.id);
                this.selectedJobIds = [...new Set([...this.selectedJobIds, ...ids])];
                uni.showToast({ title: "工种已添加", icon: "success" });
              } catch (error) {
                uni.showToast({
                  title: error.message || "工种添加失败，请重试",
                  icon: "none",
                });
              } finally {
                this.loading = false;
              }
            },
          });
        },
      });
    },
    toggleJob(job) {
      const idx = this.selectedJobIds.indexOf(job.id);
      if (idx >= 0) {
        this.selectedJobIds.splice(idx, 1);
      } else {
        this.selectedJobIds.push(job.id);
      }
    },
    prevStep() {
      if (this.currentTab === "industry") {
        this.goBack();
      } else if (this.currentTab === "type") {
        this.currentTab = "industry";
      } else {
        this.currentTab = "type";
      }
    },
    nextStep() {
      if (!this.canNext) return;
      if (this.currentTab === "industry") {
        this.currentTab = "type";
      } else if (this.currentTab === "type") {
        this.currentTab = "job";
      } else {
        const selectedJobNames = this.selectedJobs.map((item) => item.name);
        const enterpriseTypeIdArr = this.selectedEnterpriseTypeId !== null ? [this.selectedEnterpriseTypeId] : [];
        const enterpriseTypeNames = this.selectedEnterpriseType ? [this.selectedEnterpriseType.name] : [];
        const data = {
          industryId: this.selectedIndustryId,
          industry: this.selectedIndustry?.name || "",
          enterpriseTypeId: this.selectedEnterpriseTypeId,
          enterpriseTypeIds: enterpriseTypeIdArr,
          enterpriseTypes: enterpriseTypeNames,
          jobIds: [...this.selectedJobIds],
          jobs: selectedJobNames,
        };
        uni.setStorageSync("jobCategorySelection", data);
        uni.$emit("jobsSelected", data);
        const params = [
          `job=${encodeURIComponent(selectedJobNames.join("、"))}`,
          `industryId=${encodeURIComponent(this.selectedIndustryId)}`,
          `enterpriseTypeIds=${encodeURIComponent(enterpriseTypeIdArr.join(","))}`,
          `jobIds=${encodeURIComponent(this.selectedJobIds.join(","))}`,
        ];
        if (this.orderId) params.push(`id=${encodeURIComponent(this.orderId)}`);
        uni.navigateTo({
          url: `/pages/boss-publish/publish-info?${params.join("&")}`,
        });
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-bg {
  background: #fff;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.modal-header {
  display: flex;
  align-items: baseline;
  padding: 28rpx 96rpx 0 32rpx;
  min-height: 72rpx;
  box-sizing: content-box;
  position: relative;
  white-space: nowrap;
  flex-shrink: 0;
}

.modal-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #333;
  flex-shrink: 0;
}

.modal-desc {
  font-size: 26rpx;
  color: #999;
  margin-left: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tabs {
  display: flex;
  border-bottom: 2rpx solid #f0f0f0;
  margin: 32rpx 32rpx 0;
  flex-shrink: 0;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #999;
  position: relative;
  font-weight: 500;
}

.tab-item.active {
  color: #FF6B35;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -2rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 80rpx;
  height: 6rpx;
  background: #FF6B35;
  border-radius: 4rpx;
}

.tab-content {
  flex: 1;
  height: 0;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
  padding: 0 32rpx 24rpx;
}

.tab-pane {
  display: none;
}

.tab-pane.active {
  display: block;
}

.state {
  padding: 120rpx 0;
  color: #999;
  text-align: center;
  font-size: 28rpx;
}

.state.error {
  color: #ff6b35;
}

.industry-item {
  padding: 36rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
  font-size: 32rpx;
  color: #333;
  font-weight: 500;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.industry-item:last-child,
.type-item:last-child,
.job-row:last-child {
  border-bottom: none;
}

.industry-item:active {
  color: #FF6B35;
}

.arrow-icon {
  color: #ccc;
  font-size: 24rpx;
}

.type-item {
  padding: 36rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
  font-size: 32rpx;
  color: #333;
  font-weight: 500;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.type-item.selected {
  color: #FF6B35;
}

.add-type-item {
  color: #FF6B35;
}

.add-type-icon {
  font-size: 36rpx;
  font-weight: 300;
}

.check {
  display: flex;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #FF6B35;
  align-items: center;
  justify-content: center;
}

.check-icon {
  color: #fff;
  font-size: 24rpx;
}

.job-row {
  display: flex;
  align-items: center;
  padding: 32rpx 0;
  border-bottom: 2rpx solid #f5f5f5;
}

.job-info {
  flex: 1;
}

.job-name {
  font-size: 30rpx;
  font-weight: 500;
  color: #333;
}

.job-desc {
  font-size: 24rpx;
  color: #999;
  margin-top: 6rpx;
}

.checkbox {
  width: 44rpx;
  height: 44rpx;
  border-radius: 8rpx;
  border: 4rpx solid #ddd;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.job-row.selected .checkbox {
  border-color: #FF6B35;
  background: #FF6B35;
}

.checkbox-icon {
  color: #fff;
  font-size: 24rpx;
}

.bottom-bar {
  box-sizing: border-box;
  width: 100%;
  flex-shrink: 0;
  background: #fff;
  padding: 24rpx 32rpx;
  display: flex;
  gap: 24rpx;
  box-shadow: 0 -4rpx 20rpx rgba(0,0,0,0.05);
}

.btn-prev {
  flex: 1;
  padding: 24rpx;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 48rpx;
  font-size: 30rpx;
  font-weight: 500;
}

.btn-next {
  flex: 2;
  padding: 24rpx;
  background: linear-gradient(135deg, #FFD700, #FFA500);
  color: #fff;
  border: none;
  border-radius: 48rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.btn-next:disabled {
  opacity: 0.5;
}

.btn-prev,
.btn-next {
  margin: 0;
  line-height: 1.4;
}

.btn-prev::after,
.btn-next::after {
  border: none;
}
</style>
