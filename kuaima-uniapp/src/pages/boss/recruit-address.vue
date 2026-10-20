<template>
  <view class="container">
    <!-- 状态栏 -->
    <view class="status-bar">
      <text>19:53</text>
      <view class="status-icons">
        <text>📶</text>
        <text>📡</text>
        <text>🔋</text>
      </view>
    </view>

    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @click="goBack">
        <image class="nav-icon" src="/static/icons/boss-recruit-address/arrow-left-dark.svg" mode="aspectFit" />
      </view>
      <text class="nav-title">招工地址</text>
      <view class="nav-right">
        <image class="nav-icon nav-add-icon" src="/static/icons/boss-recruit-address/plus-dark.svg" mode="aspectFit" @click="openAddressSheet" />
      </view>
    </view>

    <scroll-view scroll-y class="scroll-area">
      <!-- 添加地址按钮 -->
      <view class="add-btn" @click="openAddressSheet">
        <image class="add-location-icon" src="/static/icons/boss-recruit-address/map-marker-alt-orange.svg" mode="aspectFit" />
        <text>添加招工地址</text>
      </view>

      <!-- 地址列表 -->
      <view v-if="loading" class="empty-state">地址加载中...</view>
      <view v-else-if="!addresses.length" class="empty-state">暂无招工地址</view>
      <view class="address-card" v-for="addr in addresses" :key="addr.id">
        <view class="address-head">
          <text class="address-name">{{ addr.name }}</text>
          <text class="address-default" v-if="addr.isDefault">默认</text>
        </view>
        <text class="address-detail">{{ addr.detail }}</text>
        <view class="address-actions">
          <view class="action-btn" @click="navigate(addr)">
            <image class="action-icon" src="/static/icons/boss-recruit-address/location-arrow-gray.svg" mode="aspectFit" />
            <text>导航</text>
          </view>
          <view class="action-btn" @click="editAddress(addr)">
            <image class="action-icon" src="/static/icons/boss-recruit-address/edit-gray.svg" mode="aspectFit" />
            <text>编辑</text>
          </view>
          <view class="action-btn" @click="setDefault(addr)" v-if="!addr.isDefault">
            <text>设默认</text>
          </view>
          <view class="action-btn danger" @click="deleteAddress(addr)" v-if="!addr.isDefault">
            <image class="action-icon" src="/static/icons/boss-recruit-address/trash-red.svg" mode="aspectFit" />
            <text>删除</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <BossAddressEditor
      :visible="sheetVisible"
      :editing="Boolean(editingAddressId)"
      :saving="savingAddress"
      :draft="addressDraft"
      :tags="tagPresets"
      @cancel="closeAddressSheet"
      @choose-location="chooseAddressLocation"
      @toggle-tag="toggleAddressTag"
      @save="saveAddress"
    />
  </view>
</template>

<script>
import {
  createBossRecruitAddress,
  deleteBossRecruitAddress,
  listBossRecruitAddresses,
  setDefaultBossRecruitAddress,
  updateBossRecruitAddress,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";
import BossAddressEditor from "@/components/BossAddressEditor.vue";
import { extractBossAddressRegion } from "@/utils/boss-address";

function normalizeLocationParams(draft = {}) {
  const latitude = Number(draft.latitude);
  const longitude = Number(draft.longitude);
  const hasLatitude = draft.latitude !== null && draft.latitude !== "" && Number.isFinite(latitude);
  const hasLongitude = draft.longitude !== null && draft.longitude !== "" && Number.isFinite(longitude);
  return {
    ...draft,
    city: draft.city || "",
    district: draft.district || "",
    latitude: hasLatitude ? latitude : null,
    longitude: hasLongitude ? longitude : null,
  };
}

export default {
  components: { BossAddressEditor },
  data() {
    return {
      addresses: [],
      loading: false,
      actionLoading: false,
      authRedirecting: false,
      sheetVisible: false,
      savingAddress: false,
      editingAddressId: null,
      addressDraft: {
        name: "",
        detail: "",
        contactName: "",
        contactPhone: "",
        tags: [],
        city: "",
        district: "",
        latitude: null,
        longitude: null,
        isDefault: false,
      },
      tagPresets: [
        "电子厂",
        "有空调",
        "物流",
        "仓库",
        "餐饮",
        "分拣",
        "装卸",
        "包装",
        "有休息区",
        "室内",
      ],
    };
  },
  onLoad() {
    this.loadAddresses();
  },
  onShow() {
    if (this.addresses) this.loadAddresses();
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    openAddressSheet() {
      this.editingAddressId = null;
      this.addressDraft = {
        name: "",
        detail: "",
        contactName: "",
        contactPhone: "",
        tags: [],
        city: "",
        district: "",
        latitude: null,
        longitude: null,
        isDefault: false,
      };
      this.sheetVisible = true;
    },
    editAddress(address) {
      this.editingAddressId = address.id;
      this.addressDraft = {
        ...address,
        name: address.name || "",
        detail: address.detail || address.address || "",
        contactName: address.contactName || "",
        contactPhone: address.contactPhone || "",
        tags: Array.isArray(address.tags)
          ? [...address.tags]
          : String(address.tags || "")
              .split(",")
              .map((tag) => tag.trim())
              .filter(Boolean),
        latitude: address.latitude ?? address.lat ?? null,
        longitude: address.longitude ?? address.lng ?? null,
        isDefault: !!address.isDefault,
      };
      this.sheetVisible = true;
    },
    chooseAddressLocation() {
      // #ifdef MP-WEIXIN
      this.ensureLocationPermission();
      return;
      // #endif

      // App 的隐私同意由启动时的原生隐私弹窗处理，定位权限由系统弹窗申请，直接打开地图选点
      // #ifdef APP-PLUS
      this.openLocationPicker();
      return;
      // #endif

      // #ifdef H5
      uni.showToast({ title: "请在下方手动填写地址", icon: "none" });
      // #endif
    },
    ensureLocationPermission() {
      // #ifdef MP-WEIXIN
      const continueAuthorize = () => {
        uni.getSetting({
          success: ({ authSetting = {} }) => {
            if (authSetting["scope.userLocation"] === false) {
              this.showLocationSettingModal();
              return;
            }
            this.openLocationPicker();
          },
          fail: () => this.openLocationPicker(),
        });
      };

      if (typeof wx !== "undefined" && typeof wx.getPrivacySetting === "function") {
        wx.getPrivacySetting({
          success: ({ needAuthorization }) => {
            if (!needAuthorization) {
              continueAuthorize();
              return;
            }
            if (typeof wx.requirePrivacyAuthorize !== "function") {
              this.showPrivacyGuide();
              return;
            }
            wx.requirePrivacyAuthorize({
              success: continueAuthorize,
              fail: (error) => this.handleLocationError(error),
            });
          },
          fail: continueAuthorize,
        });
        return;
      }

      continueAuthorize();
      // #endif
    },
    openLocationPicker() {
      // #ifdef MP-WEIXIN || APP-PLUS
      const options = {
        success: ({ name, address, latitude, longitude }) => {
          const region = extractBossAddressRegion(address);
          this.addressDraft = {
            ...this.addressDraft,
            name: this.addressDraft.name || name || "",
            detail: address || name || this.addressDraft.detail,
            city: region.city || this.addressDraft.city,
            district: region.district || this.addressDraft.district,
            latitude: Number(latitude),
            longitude: Number(longitude),
          };
        },
        fail: (error) => this.handleLocationError(error),
      };
      const location = normalizeLocationParams(this.addressDraft);
      if (location.latitude !== null && location.longitude !== null) {
        options.latitude = location.latitude;
        options.longitude = location.longitude;
      }
      uni.chooseLocation(options);
      // #endif
    },
    handleLocationError(error) {
      const message = String(error?.errMsg || error?.message || "地图选点失败");
      const normalized = message.toLowerCase();
      if (normalized.includes("cancel")) return;
      if (
        normalized.includes("auth deny") ||
        normalized.includes("authorize") ||
        normalized.includes("permission denied") ||
        normalized.includes("system permission")
      ) {
        this.showLocationSettingModal();
        return;
      }
      if (normalized.includes("privacy")) {
        this.showPrivacyGuide();
        return;
      }
      uni.showModal({
        title: "地图选点失败",
        content: message.replace(/^chooseLocation:fail\s*/i, "") || "请稍后重试",
        showCancel: false,
      });
    },
    showLocationSettingModal() {
      uni.showModal({
        title: "需要位置权限",
        content: "请在设置中允许使用位置信息，然后重新选择招工地址。",
        confirmText: "去设置",
        success: ({ confirm }) => {
          if (!confirm) return;
          // App 没有 openSetting，需跳转系统的应用权限设置页
          // #ifdef APP-PLUS
          uni.openAppAuthorizeSetting();
          // #endif
          // #ifndef APP-PLUS
          uni.openSetting();
          // #endif
        },
      });
    },
    showPrivacyGuide() {
      uni.showModal({
        title: "需要隐私授权",
        content: "请先同意小程序隐私保护指引，并确认微信公众平台已声明地图选点用途。",
        showCancel: false,
      });
    },
    toggleAddressTag(tag) {
      const tags = new Set(this.addressDraft.tags);
      if (tags.has(tag)) tags.delete(tag);
      else tags.add(tag);
      this.addressDraft.tags = [...tags];
    },
    closeAddressSheet() {
      if (this.savingAddress) return;
      this.sheetVisible = false;
    },
    async saveAddress() {
      if (this.savingAddress) return;
      const location = normalizeLocationParams(this.addressDraft);
      const data = {
        name: this.addressDraft.name.trim(),
        contactName: this.addressDraft.contactName.trim(),
        contactPhone: this.addressDraft.contactPhone.trim(),
        city: this.addressDraft.city.trim(),
        district: this.addressDraft.district.trim(),
        detail: this.addressDraft.detail.trim(),
        latitude: location.latitude,
        longitude: location.longitude,
        isDefault: Boolean(this.addressDraft.isDefault),
      };
      if (!data.name) {
        uni.showToast({ title: "请输入地点名称", icon: "none" });
        return;
      }
      if (!data.detail) {
        uni.showToast({ title: "请输入详细地址", icon: "none" });
        return;
      }
      if (!data.city) {
        uni.showToast({ title: "请输入所在城市", icon: "none" });
        return;
      }
      if (!data.district) {
        uni.showToast({ title: "请输入所在区县", icon: "none" });
        return;
      }
      // #ifdef MP-WEIXIN
      if (!Number.isFinite(data.latitude) || !Number.isFinite(data.longitude)) {
        uni.showToast({ title: "请先在地图选择地点", icon: "none" });
        return;
      }
      // #endif
      if (!data.contactName) {
        uni.showToast({ title: "请输入联系人姓名", icon: "none" });
        return;
      }
      if (!/^1\d{10}$/.test(data.contactPhone)) {
        uni.showToast({ title: "请输入正确的联系电话", icon: "none" });
        return;
      }

      this.savingAddress = true;
      try {
        if (this.editingAddressId) {
          await updateBossRecruitAddress(this.editingAddressId, data);
        } else {
          await createBossRecruitAddress(data);
        }
        this.sheetVisible = false;
        await this.loadAddresses();
        uni.showToast({ title: "地址已保存", icon: "success" });
      } catch (error) {
        this.handleRequestError(error, "地址保存失败");
      } finally {
        this.savingAddress = false;
      }
    },
    navigate(address) {
      if (!address?.latitude || !address?.longitude) {
        uni.showToast({ title: "暂无地址坐标", icon: "none" });
        return;
      }
      uni.openLocation({
        latitude: Number(address.latitude),
        longitude: Number(address.longitude),
        name: address.name || "招工地址",
        address: address.detail || "",
      });
    },
    async loadAddresses() {
      if (this.loading) return;
      this.loading = true;
      try {
        const result = await listBossRecruitAddresses();
        const rows = Array.isArray(result)
          ? result
          : result?.records || result?.content || [];
        this.addresses = rows.map((item) => ({
          ...item,
          detail: item.detail || item.address || "",
          isDefault: item.isDefault === true,
        }));
      } catch (error) {
        this.addresses = [];
        this.handleRequestError(error, "地址加载失败");
      } finally {
        this.loading = false;
      }
    },
    async setDefault(address) {
      if (!address?.id || this.actionLoading) return;
      this.actionLoading = true;
      try {
        await setDefaultBossRecruitAddress(address.id);
        await this.loadAddresses();
        uni.showToast({ title: "默认地址已更新", icon: "success" });
      } catch (error) {
        this.handleRequestError(error, "设置默认地址失败");
      } finally {
        this.actionLoading = false;
      }
    },
    deleteAddress(address) {
      uni.showModal({
        title: '提示',
        content: `确定要删除“${address?.name || "该地址"}”吗？`,
        success: async (res) => {
          if (res.confirm) {
            if (!address?.id || this.actionLoading) return;
            this.actionLoading = true;
            try {
              await deleteBossRecruitAddress(address.id);
              await this.loadAddresses();
              uni.showToast({ title: "地址已删除", icon: "success" });
            } catch (error) {
              this.handleRequestError(error, "地址删除失败");
            } finally {
              this.actionLoading = false;
            }
          }
        }
      });
    },
    handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) {
        if (!this.authRedirecting) {
          this.authRedirecting = true;
          handleTokenInvalid({ role: "boss" }).finally(() => {
            this.authRedirecting = false;
          });
        }
        return;
      }
      const title = status === 403 ? "无权操作" : status === 404 ? "地址不存在" : error?.message || fallback;
      uni.showToast({ title, icon: "none" });
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

.nav-bar {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
}

.nav-back {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-icon {
  width: 18px;
  height: 18px;
}

.nav-add-icon {
  width: 16px;
  height: 16px;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.nav-right {
  display: flex;
  gap: 14px;
  color: #333;
}

.scroll-area {
  flex: 1;
  height: 0;
  min-height: 0;
  width: 100%;
  overflow-y: auto;
}

.empty-state {
  padding: 72px 16px;
  color: #999;
  font-size: 14px;
  text-align: center;
}

.add-btn {
  margin: 12px 16px;
  padding: 12px;
  background: #fff;
  border: 1px dashed #FF6B35;
  border-radius: 10px;
  color: #FF6B35;
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.add-location-icon {
  width: 14px;
  height: 16px;
  flex-shrink: 0;
}

.address-card {
  background: #fff;
  margin: 8px 16px;
  border-radius: 12px;
  padding: 14px;
}

.address-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.address-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.address-default {
  font-size: 11px;
  color: #FF6B35;
  background: #FFF3ED;
  padding: 2px 8px;
  border-radius: 4px;
}

.address-detail {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  display: block;
}

.address-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.action-btn {
  padding: 4px 10px;
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
  border-radius: 4px;
  display: flex;
  align-items: center;
}

.action-icon {
  width: 12px;
  height: 12px;
  margin-right: 4px;
  flex-shrink: 0;
}

.action-btn.danger {
  color: #FF4D4F;
}

.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(0, 0, 0, 0.42);
  animation: address-mask-fade-in 180ms ease-out both;
}

.address-sheet {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 81;
  max-height: calc(100vh - 88px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 20px 20px 0 0;
  box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.15);
  animation: address-sheet-slide-up 220ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.sheet-grip {
  width: 40px;
  height: 4px;
  margin: 9px auto 2px;
  background: #ddd;
  border-radius: 2px;
}

.sheet-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50px;
  padding: 0 52px;
  border-bottom: 1px solid #f2f2f2;
}

.sheet-title {
  color: #222;
  font-size: 17px;
  font-weight: 600;
}

.sheet-close {
  position: absolute;
  top: 7px;
  right: 12px;
  width: 36px;
  height: 36px;
  padding: 0;
  color: #999;
  font-size: 25px;
  line-height: 34px;
  background: transparent;
  border: 0;
}

.sheet-close::after {
  border: 0;
}

.sheet-body {
  flex: 1;
  min-height: 0;
  height: 0;
  width: 100%;
  /* 让表单占用操作栏上方的剩余空间，避免固定最大高度把内容顶到按钮下面 */
  max-height: none;
  padding: 4px 16px 14px;
  box-sizing: border-box;
}

.address-minimap {
  position: relative;
  height: 140px;
  margin: 8px 0 2px;
  overflow: hidden;
  background: linear-gradient(180deg, #e8f4fd 0%, #f0ede4 100%);
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.minimap-grid {
  position: absolute;
  inset: 0;
  opacity: 0.65;
  background-image:
    linear-gradient(rgba(190, 196, 202, 0.32) 1px, transparent 1px),
    linear-gradient(90deg, rgba(190, 196, 202, 0.32) 1px, transparent 1px);
  background-size: 40px 40px;
}

.minimap-road {
  position: absolute;
  background: rgba(255, 255, 255, 0.45);
}

.minimap-road-horizontal {
  top: 82px;
  right: 0;
  left: 0;
  height: 12px;
}

.minimap-road-vertical {
  top: 0;
  bottom: 0;
  left: 38%;
  width: 10px;
}

.minimap-search {
  position: absolute;
  top: 10px;
  right: 12px;
  left: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 14px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  box-sizing: border-box;
}

.minimap-search-icon {
  width: 17px;
  height: 17px;
  margin-right: 9px;
  flex-shrink: 0;
}

.minimap-search-text {
  min-width: 0;
  overflow: hidden;
  color: #6b7280;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.minimap-pin {
  position: absolute;
  top: 54%;
  left: 50%;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  background: #ff6b35;
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
  transform: translate(-50%, -50%);
}

.minimap-pin image {
  width: 14px;
  height: 14px;
}

.form-item {
  padding-top: 14px;
}

/* 给最后一个联系电话输入框和底部操作栏留出明确间距，避免滚动区内容贴住按钮 */
.sheet-body > .form-item:last-child {
  padding-bottom: 14px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.required {
  color: #ff4d4f;
}

.form-input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  color: #333;
  font-size: 14px;
  background: #f7f7f7;
  border: 1px solid #eee;
  border-radius: 8px;
  box-sizing: border-box;
}

.tag-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  padding: 5px 12px;
  color: #ff6b35;
  font-size: 12px;
  background: #fff8e6;
  border: 1px solid #ffe8dc;
  border-radius: 16px;
}

.tag-chip.active {
  color: #fff;
  background: #ff6b35;
  border-color: #ff6b35;
}

.sheet-footer {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  border-top: 1px solid #f2f2f2;
}

.cancel-btn,
.save-btn {
  flex: 1;
  height: 44px;
  margin: 0;
  font-size: 15px;
  line-height: 44px;
  border-radius: 22px;
}

.cancel-btn {
  color: #666;
  background: #f5f5f5;
}

.save-btn {
  color: #fff;
  background: #ff6b35;
}

.cancel-btn::after,
.save-btn::after {
  border: 0;
}

.save-btn[disabled] {
  color: rgba(255, 255, 255, 0.8);
  background: #ffad90;
}

@keyframes address-mask-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes address-sheet-slide-up {
  from { opacity: 0; transform: translateY(28px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
