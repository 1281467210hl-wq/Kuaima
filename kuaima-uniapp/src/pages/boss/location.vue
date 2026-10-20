<template>
  <view class="container">
    <view :style="{ height: `${statusBarHeight}px` }" />
    <view class="page-bg">
      <!-- 导航栏 -->
      <view class="nav-bar">
        <view class="nav-back" @click="closePage">
          <image src="/static/icons/worker-job-detail/chevron-left-gray.svg" mode="aspectFit" />
        </view>
        <text class="nav-title">设置干活地点</text>
        <view class="nav-placeholder"></view>
      </view>

      <!-- 地图区域 -->
      <view class="map-container">
        <view class="map-bg"></view>
        <view class="map-road map-road-h"></view>
        <view class="map-road map-road-h2"></view>
        <view class="map-road map-road-v"></view>
        <view class="map-road map-road-v2"></view>
        <view class="map-building b1"></view>
        <view class="map-building b2"></view>
        <view class="map-building b3"></view>
        <view class="map-building b4"></view>
        <view class="map-building b5"></view>
        <view class="map-building b6"></view>

        <!-- 搜索栏 -->
        <view class="search-bar">
          <image class="ui-icon" src="/static/icons/boss-location/search-gray.svg" mode="aspectFit" />
          <input type="text" v-model="keyword" placeholder="搜索地址或地点" confirm-type="search" @confirm="searchAddress" />
        </view>

        <!-- 定位按钮 -->
        <view class="locate-btn" @click="locateMe">
          <image class="ui-icon" src="/static/icons/boss-location/crosshairs-orange.svg" mode="aspectFit" />
        </view>

        <!-- 地图标记 -->
        <view class="map-pin">
          <view class="pin-pulse"></view>
          <view class="pin-outer">
            <image class="ui-icon pin-icon" src="/static/icons/boss-location/map-marker-white.svg" mode="aspectFit" />
          </view>
        </view>

        <!-- 地图控制 -->
        <view class="map-controls">
          <view class="map-ctrl-btn" @click="zoomIn">
            <image src="/static/icons/boss-location/plus-dark.svg" mode="aspectFit" />
          </view>
          <view class="map-ctrl-btn" @click="zoomOut">
            <image src="/static/icons/boss-location/minus-dark.svg" mode="aspectFit" />
          </view>
        </view>
      </view>

      <!-- 滚动区域 -->
      <scroll-view
        scroll-y
        class="scroll-area"
        style="flex: 1; overflow-y: auto; min-height: 0"
      >
        <!-- 常用地点 -->
        <view class="section-header">
          <text class="section-title">常用地点</text>
        </view>

        <!-- 地址列表 -->
        <view class="address-list">
          <view
            class="address-item"
            :class="{ selected: selectedIndex === index }"
            v-for="(item, index) in addresses"
            :key="item.id || index"
            @click="selectAddress(index)"
          >
            <view class="address-icon">
              <image class="address-type-icon" :src="addressIcon(item, index)" mode="aspectFit" />
            </view>
            <view class="address-info">
              <view class="address-name">
                {{ item.name }}
                <text class="addr-tag hot" v-if="item.tag === '最近'">{{
                  item.tag
                }}</text>
              </view>
              <text class="address-detail">{{ item.address }}</text>
              <view class="address-tags" style="margin-top: 4px">
                <text class="addr-tag" v-for="(t, i) in item.tags" :key="i">{{
                  t
                }}</text>
              </view>
            </view>
            <view v-if="selectedIndex === index" class="addr-selected-badge">
              <image src="/static/icons/boss-location/check-white.svg" mode="aspectFit" />
            </view>
            <view class="address-actions">
              <view class="address-action" @click.stop="editAddress(index)">
                <image src="/static/icons/boss-location/pen-gray.svg" mode="aspectFit" />
              </view>
              <view class="address-action danger" @click.stop="removeAddress(index)">
                <image src="/static/icons/boss-location/trash-red.svg" mode="aspectFit" />
              </view>
            </view>
          </view>
        </view>

        <!-- 新增地址 -->
        <view class="add-address-btn" @click="addNewAddress">
          <image src="/static/icons/boss-location/plus-gray.svg" mode="aspectFit" />
          <text>新增工作地点</text>
        </view>
      </scroll-view>

      <!-- 底部按钮 -->
      <view class="bottom-bar">
        <button class="submit-btn" @click="saveLocation">确定使用此地点</button>
      </view>
    </view>

    <BossAddressEditor
      :visible="sheetVisible"
      :editing="editingIndex >= 0"
      :saving="savingAddress"
      :draft="draft"
      :tags="tagPresets"
      @cancel="closeAddressSheet"
      @choose-location="chooseAddressLocation"
      @toggle-tag="toggleDraftTag"
      @save="saveAddressDraft"
    />
    <view v-if="false && sheetVisible" class="sheet-mask" @click="closeAddressSheet" />
    <view v-if="false && sheetVisible" class="address-sheet">
      <view class="sheet-grip" />
      <view class="sheet-header">
        <text class="sheet-title">{{ editingIndex === -1 ? '新增工作地点' : '编辑工作地点' }}</text>
        <button class="sheet-close" @click="closeAddressSheet"><image src="/static/icons/boss-location/xmark-gray.svg" mode="aspectFit" /></button>
      </view>
      <scroll-view scroll-y class="sheet-body">
        <view class="mini-map">
          <view class="map-bg" />
          <view class="mini-search"><image class="ui-icon" src="/static/icons/boss-location/search-gray.svg" mode="aspectFit" /><text>点击地图选点或搜索地址</text></view>
          <view class="mini-pin"><image class="ui-icon pin-icon" src="/static/icons/boss-location/map-pin-white.svg" mode="aspectFit" /></view>
        </view>
        <view class="sheet-field">
          <text class="field-label"><text class="required">*</text> 地点名称</text>
          <input v-model="draft.name" class="field-input" placeholder="请输入地点名称，如：观澜电子厂" />
        </view>
        <view class="sheet-field">
          <text class="field-label"><text class="required">*</text> 详细地址</text>
          <input v-model="draft.detail" class="field-input" placeholder="请输入详细地址，如：龙华区观澜街道桂月路316号" />
        </view>
        <view class="sheet-field">
          <text class="field-label">地点标签（可多选）</text>
          <view class="tag-chips">
            <text v-for="tag in tagPresets" :key="tag" :class="['tag-chip', { active: draft.tags.includes(tag) }]" @click="toggleDraftTag(tag)">{{ tag }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="sheet-footer">
        <button class="cancel-btn" @click="closeAddressSheet">取消</button>
        <button class="save-btn" @click="saveAddressDraft"><image src="/static/icons/boss-location/check-white.svg" mode="aspectFit" /> 保存</button>
      </view>
    </view>
  </view>
</template>

<script>
import {
  createBossRecruitAddress,
  deleteBossRecruitAddress,
  listBossRecruitAddresses,
  updateBossRecruitAddress,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import { extractBossAddressRegion, normalizeBossAddressSelection, readAddressCoordinates } from "@/utils/boss-address";
import BossAddressEditor from "@/components/BossAddressEditor.vue";
export default {
  components: { BossAddressEditor },
  data() {
    return {
      selectedIndex: 0,
      statusBarHeight: uni.getSystemInfoSync().statusBarHeight || 0,
      distance: "1.2",
      addresses: [], keyword: "", loading: false,
      sheetVisible: false,
      editingIndex: -1,
      pendingEditId: null,
      draft: { name: "", detail: "", city: "", district: "", contactName: "", contactPhone: "", tags: [], latitude: null, longitude: null, isDefault: false },
      tagPresets: ["电子厂", "有空调", "物流", "仓库", "餐饮", "分拣", "装卸", "包装", "有休息区", "室内"],
      savingAddress: false,
      authRedirecting: false,
      returnAfterSave: false,
      locationSelecting: false,
    };
  },
  computed: {
    selectedAddress() {
      return this.addresses[this.selectedIndex] || { name: "", address: "" };
    },
  },
  async onLoad(options = {}) {
    this.returnAfterSave = options.returnAfterSave === "1";
    const id = Number(options.id);
    this.pendingEditId = Number.isSafeInteger(id) && id > 0 ? id : null;
    // 新增模式先展示弹层，再异步加载地址列表，避免进入页面时等待接口导致闪烁。
    if (options.mode === "add") this.addNewAddress();
    await this.loadAddresses();
    if (options.mode === "edit" && this.pendingEditId) {
      const index = this.addresses.findIndex((item) => Number(item.id) === this.pendingEditId);
      if (index >= 0) this.editAddress(index);
    }
  },
  methods: {
    async loadAddresses() {
      this.loading = true;
      try {
        const result = await listBossRecruitAddresses();
        const rows = Array.isArray(result) ? result : result?.records || result?.content || [];
        this.addresses = rows.map((item) => ({
          ...item,
          address: [item.city, item.district, item.detail || item.address].filter(Boolean).join("") || item.address || "",
          tags: Array.isArray(item.tags) ? item.tags : [],
          ...readAddressCoordinates(item),
        }));
        const index = this.addresses.findIndex((item) => item.isDefault);
        this.selectedIndex = index >= 0 ? index : 0;
      } catch (error) {
        this.addresses = [];
        this.handleRequestError(error, "地址加载失败");
      } finally {
        this.loading = false;
      }
    },
    async searchAddress() {
      const keyword = this.keyword.trim().toLowerCase();
      await this.loadAddresses();
      if (!keyword) return;
      this.addresses = this.addresses.filter((item) =>
        `${item.name || ""}${item.address || ""}`.toLowerCase().includes(keyword),
      );
      this.selectedIndex = 0;
    },
    closePage() {
      uni.navigateBack();
    },
    locateMe() {
      uni.showToast({ title: "正在获取当前位置...", icon: "loading" });
    },
    chooseAddressLocation() {
      // App 端依赖 manifest 中配置的高德地图 Key
      // #ifdef MP-WEIXIN || APP-PLUS
      uni.chooseLocation({
        success: ({ name, address, latitude, longitude }) => {
          const region = extractBossAddressRegion(address);
          this.draft = {
            ...this.draft,
            name: this.draft.name || name || "",
            detail: address || name || this.draft.detail,
            city: region.city || this.draft.city,
            district: region.district || this.draft.district,
            latitude: Number(latitude),
            longitude: Number(longitude),
          };
        },
        fail: (error) => { if (!String(error?.errMsg || "").includes("cancel")) uni.showToast({ title: "地图选点失败", icon: "none" }); },
      });
      // #endif
      // #ifdef H5
      uni.showToast({ title: "请在微信小程序或 App 中使用地图选点", icon: "none" });
      // #endif
    },
    zoomIn() {
      uni.showToast({ title: "放大地图", icon: "none" });
    },
    zoomOut() {
      uni.showToast({ title: "缩小地图", icon: "none" });
    },
    async selectAddress(index) {
      this.selectedIndex = index;
    },
    manageAddress() {
      uni.showToast({ title: "管理地址", icon: "none" });
    },
    addressIcon(item, index) {
      const name = `${item?.name || ""}${item?.tag || ""}${(item?.tags || []).join("")}`;
      if (/电子|工厂|生产/.test(name)) return "/static/icons/boss-location/industry-orange.svg";
      if (/物流|仓库|分拣|装卸/.test(name)) return "/static/icons/boss-location/warehouse-orange.svg";
      if (/餐饮|美食|酒店/.test(name)) return "/static/icons/boss-location/hotel-orange.svg";
      return [
        "/static/icons/boss-location/industry-orange.svg",
        "/static/icons/boss-location/warehouse-orange.svg",
        "/static/icons/boss-location/store-orange.svg",
      ][index % 3];
    },
    addNewAddress() { this.editingIndex = -1; this.draft = { name: "", detail: "", city: "", district: "", contactName: "", contactPhone: "", tags: [], latitude: null, longitude: null, isDefault: false }; this.sheetVisible = true; },
    editAddress(index) { const a = this.addresses[index]; this.editingIndex = index; this.draft = { ...a, name: a.name || "", detail: a.detail || a.address || "", city: a.city || "", district: a.district || "", contactName: a.contactName || "", contactPhone: a.contactPhone || "", tags: Array.isArray(a.tags) ? [...a.tags] : [], latitude: a.latitude ?? a.lat ?? null, longitude: a.longitude ?? a.lng ?? null, isDefault: !!a.isDefault }; this.sheetVisible = true; },
    closeAddressSheet() { this.sheetVisible = false; },
    toggleDraftTag(tag) { const tags = new Set(this.draft.tags); tags.has(tag) ? tags.delete(tag) : tags.add(tag); this.draft.tags = [...tags]; },
    async saveAddressDraft() {
      if (this.savingAddress) return;
      const name = this.draft.name.trim();
      const detail = this.draft.detail.trim();
      if (!name) return uni.showToast({ title: "请输入地点名称", icon: "none" });
      if (!detail) return uni.showToast({ title: "请输入详细地址", icon: "none" });
      if (!this.draft.city?.trim()) return uni.showToast({ title: "请输入所在城市", icon: "none" });
      if (!this.draft.district?.trim()) return uni.showToast({ title: "请输入所在区县", icon: "none" });
      if (!this.draft.contactName?.trim()) return uni.showToast({ title: "请输入联系人姓名", icon: "none" });
      if (!/^1\d{10}$/.test(this.draft.contactPhone?.trim() || "")) return uni.showToast({ title: "请输入正确的联系电话", icon: "none" });
      if (!Number.isFinite(Number(this.draft.latitude)) || !Number.isFinite(Number(this.draft.longitude))) return uni.showToast({ title: "请先在地图选择地点", icon: "none" });
      const data = {
        name,
        detail,
        tags: this.draft.tags,
        contactName: this.draft.contactName.trim(),
        contactPhone: this.draft.contactPhone.trim(),
        city: this.draft.city.trim(),
        district: this.draft.district.trim(),
        latitude: Number(this.draft.latitude),
        longitude: Number(this.draft.longitude),
        isDefault: Boolean(this.draft.isDefault),
        isDefault: this.editingIndex >= 0 ? !!this.addresses[this.editingIndex]?.isDefault : false,
      };
      this.savingAddress = true;
      try {
        if (this.editingIndex === -1) await createBossRecruitAddress(data);
        else await updateBossRecruitAddress(this.addresses[this.editingIndex].id, data);
        this.closeAddressSheet();
        uni.showToast({ title: "地址已保存", icon: "success" });
        if (this.returnAfterSave) {
          setTimeout(() => uni.navigateBack(), 350);
          return;
        }
        await this.loadAddresses();
      } catch (error) {
        this.handleRequestError(error, "地址保存失败");
      } finally {
        this.savingAddress = false;
      }
    },
    removeAddress(index) { const a = this.addresses[index]; uni.showModal({ title: "提示", content: `确定删除“${a.name}”吗？`, success: async ({ confirm }) => { if (!confirm) return; try { await deleteBossRecruitAddress(a.id); await this.loadAddresses(); } catch (error) { this.handleRequestError(error, "地址删除失败"); } } }); },
    handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) {
        if (!this.authRedirecting) {
          this.authRedirecting = true;
          handleTokenInvalid({ role: "boss" }).finally(() => { this.authRedirecting = false; });
        }
        return;
      }
      uni.showToast({ title: status === 403 ? "无权操作" : status === 404 ? "地址不存在" : error?.message || fallback, icon: "none" });
    },
    saveLocation() {
      const addr = this.selectedAddress;
      const data = {
        ...normalizeBossAddressSelection(addr),
        tag: addr.tag,
        distance: this.distance,
      };
      uni.setStorageSync("workLocationSelection", data);
      uni.$emit("workLocationSelected", data);
      uni.navigateBack();
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100vh;
  background: #fff8e6;
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
  background: transparent;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.page-bg {
  background: #fff;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: white;
  flex-shrink: 0;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  font-size: 20px;
}

.nav-back image { width: 18px; height: 18px; }

.nav-title {
  font-size: 17px;
  font-weight: 700;
  color: #333;
}

.nav-placeholder {
  width: 32px;
}

.map-container {
  position: relative;
  height: 260px;
  background: linear-gradient(180deg, #e8f4fd 0%, #f0ede4 100%);
  overflow: hidden;
  flex-shrink: 0;
}

.map-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(200, 200, 200, 0.3) 1px, transparent 1px),
    linear-gradient(90deg, rgba(200, 200, 200, 0.3) 1px, transparent 1px);
  background-size: 40px 40px;
}

.map-road {
  position: absolute;
  background: #ddd;
  border-radius: 4px;
}

.map-road-h {
  height: 14px;
  top: 60px;
  left: 0;
  right: 0;
}
.map-road-h2 {
  height: 10px;
  top: 160px;
  left: 0;
  right: 0;
}
.map-road-v {
  width: 14px;
  top: 0;
  bottom: 0;
  left: 100px;
}
.map-road-v2 {
  width: 10px;
  top: 0;
  bottom: 0;
  right: 80px;
}

.map-building {
  position: absolute;
  border-radius: 4px;
}

.b1 {
  width: 50px;
  height: 40px;
  background: #b8d4e3;
  top: 80px;
  left: 30px;
}
.b2 {
  width: 45px;
  height: 55px;
  background: #c5ddb0;
  top: 180px;
  left: 40px;
}
.b3 {
  width: 60px;
  height: 35px;
  background: #e8d5b7;
  top: 90px;
  right: 40px;
}
.b4 {
  width: 40px;
  height: 50px;
  background: #d4c5dd;
  top: 180px;
  right: 100px;
}
.b5 {
  width: 35px;
  height: 30px;
  background: #f5d7b5;
  top: 25px;
  left: 180px;
}
.b6 {
  width: 55px;
  height: 45px;
  background: #b8d4e3;
  top: 30px;
  right: 20px;
}

.map-pin {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -100%);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pin-outer {
  width: 36px;
  height: 36px;
  background: #ff6b35;
  border: 3px solid white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
}

.pin-pulse {
  position: absolute;
  width: 60px;
  height: 60px;
  background: rgba(255, 107, 53, 0.2);
  border-radius: 50%;
  animation: pulse 2s infinite;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.8;
  }
  100% {
    transform: translate(-50%, -50%) scale(1.5);
    opacity: 0;
  }
}

.map-controls {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.map-ctrl-btn {
  width: 36px;
  height: 36px;
  background: white;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  color: #333;
}

.locate-btn {
  position: absolute;
  right: 12px;
  top: 12px;
  width: 36px;
  height: 36px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  color: #ff6b35;
}

.ui-icon { width: 18px; height: 18px; flex-shrink: 0; }
.pin-icon { width: 14px; height: 18px; }

.search-bar {
  position: absolute;
  left: 12px;
  right: 60px;
  top: 12px;
  height: 36px;
  background: white;
  border-radius: 18px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-bar input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  background: transparent;
}

.search-bar input::placeholder {
  color: #bbb;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.section-action {
  font-size: 13px;
  color: #ff6b35;
}

.address-list {
  padding: 0 12px;
}

.address-item {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  background: white;
  border-radius: 10px;
  margin-bottom: 8px;
  border: 1px solid transparent;
}

.address-item.selected {
  background: #fff8e6;
  border-color: #ff6b35;
}

.address-icon {
  width: 36px;
  height: 36px;
  background: #f5f5f5;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff6b35;
  margin-right: 12px;
  flex-shrink: 0;
}

.address-type-icon { width: 18px; height: 18px; }

.addr-selected-badge {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  margin-left: 8px;
  border-radius: 50%;
  background: #10b981;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.35);
}
.addr-selected-badge image { width: 11px; height: 11px; }
.address-actions { display: flex; gap: 4px; flex-shrink: 0; margin-left: 8px; opacity: 0.45; }
.address-item.selected .address-actions { opacity: 1; }
.address-action { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border-radius: 6px; }
.address-action image { width: 13px; height: 13px; }
.address-action.danger { background: #fee2e2; }

.address-info {
  flex: 1;
  min-width: 0;
}

.address-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.address-detail {
  font-size: 12px;
  color: #999;
  line-height: 1.4;
  display: block;
}

.address-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.addr-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: #f0f0f0;
  color: #666;
}

.addr-tag.hot {
  background: #ff6b35;
  color: white;
}

.add-address-btn {
  margin: 12px;
  height: 44px;
  border: 1.5px dashed #ddd;
  border-radius: 10px;
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  color: #999;
}
.add-address-btn image { width: 14px; height: 14px; }

.scroll-area {
  padding-bottom: 12px;
}

.bottom-bar {
  background: white;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
  padding-bottom: 20px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: white;
  border-radius: 9999px;
  font-size: 16px;
  font-weight: 600;
  border: none;
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.3);
}

.sheet-mask { position: fixed; inset: 0; z-index: 80; background: rgba(0, 0, 0, 0.4); animation: address-mask-fade-in 180ms ease-out both; }
.address-sheet { position: fixed; left: 0; right: 0; bottom: 0; z-index: 81; max-height: calc(100vh - 120px); display: flex; flex-direction: column; overflow: hidden; border-radius: 20px 20px 0 0; background: #fff; box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.15); animation: address-sheet-slide-up 220ms cubic-bezier(0.22, 1, 0.36, 1) both; }

@keyframes address-mask-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes address-sheet-slide-up {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}
.sheet-grip { width: 40px; height: 4px; margin: 10px auto 6px; border-radius: 2px; background: #e5e7eb; }
.sheet-header { display: flex; align-items: center; justify-content: space-between; padding: 4px 18px 14px; }
.sheet-title { color: #1f2937; font-size: 17px; font-weight: 700; }
.sheet-close { width: 28px; height: 28px; margin: 0; padding: 0; display: flex; align-items: center; justify-content: center; border: 0; border-radius: 50%; background: #f3f4f6; }
.sheet-close image { width: 12px; height: 12px; }
.sheet-close::after, .cancel-btn::after, .save-btn::after { border: 0; }
.sheet-body { flex: 1; min-height: 0; box-sizing: border-box; padding: 0 18px 18px; }
.mini-map { position: relative; height: 140px; margin-bottom: 16px; overflow: hidden; border: 1px solid #e5e7eb; border-radius: 12px; background: linear-gradient(180deg, #e8f4fd 0%, #f0ede4 100%); }
.mini-map .map-bg { opacity: 0.6; }
.mini-search { position: absolute; top: 10px; left: 12px; right: 12px; z-index: 3; display: flex; align-items: center; gap: 8px; padding: 7px 14px; border-radius: 20px; background: #fff; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08); color: #6b7280; font-size: 13px; }
.search-symbol { font-size: 20px; line-height: 1; }
.mini-pin { position: absolute; top: 50%; left: 50%; z-index: 2; width: 28px; height: 28px; transform: translate(-50%, -100%); display: flex; align-items: center; justify-content: center; border: 3px solid #fff; border-radius: 50%; background: #ff6b35; color: #fff; font-size: 10px; }
.sheet-field { margin-bottom: 14px; }
.field-label { display: block; margin-bottom: 8px; color: #374151; font-size: 13px; font-weight: 600; }
.required { color: #ef4444; }
.field-input { width: 100%; height: 42px; padding: 0 12px; box-sizing: border-box; border: 1px solid #e5e7eb; border-radius: 10px; background: #f9fafb; color: #374151; font-size: 14px; }
.tag-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.tag-chip { padding: 5px 12px; border: 1px solid #ffe8dc; border-radius: 16px; background: #fff8e6; color: #ff6b35; font-size: 12px; }
.tag-chip.active { border-color: #ff6b35; background: #ff6b35; color: #fff; }
.sheet-footer { flex-shrink: 0; display: flex; gap: 10px; padding: 14px 18px calc(18px + env(safe-area-inset-bottom)); border-top: 1px solid #f3f4f6; background: #fff; }
.cancel-btn, .save-btn { height: 48px; margin: 0; padding: 0; border-radius: 24px; font-size: 14px; font-weight: 600; line-height: 48px; }
.cancel-btn { flex: 1; border: 1px solid #e5e7eb; background: #fff; color: #374151; }
.save-btn { flex: 2; display: flex; align-items: center; justify-content: center; gap: 5px; background: linear-gradient(135deg, #ff6b35, #ff8c5a); box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3); color: #fff; }
.save-btn image { width: 12px; height: 12px; }
.map-ctrl-btn image { width: 14px; height: 14px; }
</style>
