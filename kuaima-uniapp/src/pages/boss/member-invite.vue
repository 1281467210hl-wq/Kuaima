<template>
  <view class="container">
    <!-- 顶部导航 -->
    <view class="wb-header" :style="{ paddingTop: statusBarHeight + 8 + 'px' }">
      <view class="wb-back" @click="goBack"><text class="back-ico">‹</text></view>
      <text class="wb-title">邀请新成员</text>
      <!-- 微信小程序原生胶囊已占右上角，自绘胶囊会与其重叠，仅在非小程序端保留 -->
      <!-- #ifdef H5 -->
      <view class="wb-capsule">
        <view class="cap-btn"><text class="cap-ico">⋯</text></view>
        <view class="cap-divider"></view>
        <view class="cap-btn"><text class="cap-ico">○</text></view>
      </view>
      <!-- #endif -->
    </view>

    <view class="wb-body">
      <!-- 邀请方式 -->
      <view class="block-title"><image class="block-ico" src="/static/icons/boss-members/paper-plane-gray.svg" mode="aspectFit" />邀请方式</view>
      <view class="iv-card">
        <view class="iv-item" @click="shareWechat">
          <view class="iv-icon" style="background: linear-gradient(135deg, #10B981, #059669)"><image class="iv-ico" src="/static/icons/boss-tabbar/comment-dots-white.svg" mode="aspectFit" /></view>
          <view class="iv-main">
            <text class="iv-name">微信分享邀请</text>
            <text class="iv-desc">生成邀请链接，发送给微信好友或群聊</text>
          </view>
          <image class="wb-row-arrow" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
        <view class="iv-item" @click="openQr">
          <view class="iv-icon" style="background: linear-gradient(135deg, #FFB84D, #F09A3E)"><image class="iv-ico" src="/static/icons/boss-members/mobile-screen-white.svg" mode="aspectFit" /></view>
          <view class="iv-main">
            <text class="iv-name">邀请二维码</text>
            <text class="iv-desc">成员扫码即可申请加入企业</text>
          </view>
          <image class="wb-row-arrow" src="/static/icons/boss-points/chevron-right-gray.svg" mode="aspectFit" />
        </view>
      </view>

      <!-- 手机号添加 -->
      <view class="block-title"><image class="block-ico" src="/static/icons/boss-points/search-gray.svg" mode="aspectFit" />手机号添加</view>
      <view class="iv-card">
        <view class="iv-phone">
          <input type="tel" v-model="phone" maxlength="11" placeholder="请输入对方手机号" />
          <view class="iv-add-btn" @click="addByPhone"><image class="iv-add-ico" src="/static/icons/boss-reward/plus-white.svg" mode="aspectFit" /><text>添加</text></view>
        </view>
      </view>

      <!-- 角色设置 -->
      <view class="block-title"><image class="block-ico" src="/static/icons/boss-members/shield-gray.svg" mode="aspectFit" />新成员角色</view>
      <view class="iv-card" style="margin-bottom: 16px">
        <view class="role-opts">
          <view class="role-opt" :class="{ active: inviteRole === 'STAFF' }" @click="inviteRole = 'STAFF'">
            <text class="r-name">成员</text>
            <text class="r-desc">仅可打卡考勤\n查看自己的工资单</text>
          </view>
          <view class="role-opt" :class="{ active: inviteRole === 'ADMIN' }" @click="inviteRole = 'ADMIN'">
            <text class="r-name">管理员</text>
            <text class="r-desc">可管理岗位与成员\n制单发薪、审批考勤</text>
          </view>
        </view>
      </view>

      <!-- 邀请记录 -->
      <view class="block-title"><image class="block-ico" src="/static/icons/worker-classroom/clock-gray.svg" mode="aspectFit" />邀请记录</view>
      <view class="rc-card">
        <view v-if="inviteLoading" class="wb-empty"><text class="wb-empty-text">加载中...</text></view>
        <view v-else-if="!records.length" class="wb-empty">
          <image class="wb-empty-icon" src="/static/icons/boss-projects/user-gray.svg" mode="aspectFit" />
          <text class="wb-empty-text">暂无邀请记录</text>
        </view>
        <view v-for="r in records" :key="r.id" class="rc-row">
          <view class="rc-avatar">{{ (r.name || '新').charAt(0) }}</view>
          <view class="rc-main">
            <view class="rc-name-line">
              <text class="rc-name">{{ r.name }}</text>
              <text class="wb-tag" :class="r.status === 'ACCEPTED' ? 'green' : 'orange'">
                {{ r.status === 'ACCEPTED' ? '已接受' : '待接受' }}
              </text>
            </view>
            <text class="rc-desc">{{ roleText(r.role) }} · {{ r.phone }} · {{ r.time }}</text>
          </view>
          <text v-if="r.status === 'PENDING'" class="remind-link" @click="remind(r)">提醒</text>
        </view>
      </view>
    </view>

    <!-- 二维码弹窗 -->
    <view class="wb-modal" v-if="qrModalShow" @click.self="closeQr">
      <view class="wb-modal-box">
        <view class="wb-modal-header">
          <text class="wb-modal-title">邀请二维码</text>
          <image class="wb-modal-close-ico" src="/static/icons/boss-points/xmark-gray.svg" mode="aspectFit" @click="closeQr" />
        </view>
        <view class="qr-box">
          <view class="qr-img" @click="goCreateEmployee">
            <image v-if="qrImageUrl" class="qr-image" :src="qrImageUrl" mode="aspectFit" />
          </view>
          <text class="qr-code-text">邀请码 {{ qrInfo.inviteCode || '—' }}</text>
          <text class="qr-tip">请使用微信「扫一扫」识别二维码\n扫码后填写信息提交入企申请</text>
          <text class="qr-create-link" @click="goCreateEmployee">没有微信？点此创建员工 ›</text>
        </view>
        <view class="wb-modal-footer">
          <view class="wb-btn-outline wb-btn-row" @click="saveQr">
            <image class="wb-btn-ico" src="/static/icons/boss-members/download-gray.svg" mode="aspectFit" />
            <text>保存图片</text>
          </view>
          <view class="wb-btn-primary wb-btn-row" @click="shareQr">
            <image class="wb-btn-ico" src="/static/icons/boss-reward/share-orange.svg" mode="aspectFit" />
            <text>分享</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { createInvite, getInvites, remindInvite, getInviteQr } from "@/api/enterprise";

export default {
  data() {
    return {
      statusBarHeight: 44,
      phone: "",
      inviteRole: "STAFF",
      records: [],
      inviteLoading: false,
      qrModalShow: false,
      qrInfo: {},
      qrImageUrl: "",
    };
  },
  onLoad() {
    try {
      const info = uni.getSystemInfoSync();
      this.statusBarHeight = info.statusBarHeight || 44;
    } catch (e) {}
    this.loadInvites();
  },
  methods: {
    roleText(role) {
      return { OWNER: "超级管理员", ADMIN: "管理员", STAFF: "成员" }[role] || role || "成员";
    },
    async loadInvites() {
      this.inviteLoading = true;
      try {
        const data = await getInvites();
        this.records = data?.content || data || [];
      } catch (e) {
        // 静默失败，保留空列表
      } finally {
        this.inviteLoading = false;
      }
    },
    async addByPhone() {
      const v = (this.phone || "").trim();
      if (!/^1\d{10}$/.test(v)) {
        uni.showToast({ title: "请输入正确的11位手机号", icon: "none" });
        return;
      }
      try {
        await createInvite(v, this.inviteRole);
        this.phone = "";
        uni.showToast({ title: `已向该手机号发送邀请，角色：${this.roleText(this.inviteRole)}`, icon: "none" });
        this.loadInvites();
      } catch (e) {
        uni.showToast({ title: e.message || "邀请失败", icon: "none" });
      }
    },
    async remind(r) {
      try {
        await remindInvite(r.id);
        uni.showToast({ title: `已向 ${r.phone} 发送提醒`, icon: "none" });
      } catch (e) {
        uni.showToast({ title: e.message || "操作失败", icon: "none" });
      }
    },
    async openQr() {
      this.qrModalShow = true;
      try {
        this.qrInfo = (await getInviteQr()) || {};
        // 后端直接返回 data:image/png;base64,xxx 格式，无需再走外部 QR Server
        this.qrImageUrl = this.qrInfo.qrImage || "";
      } catch (e) {
        uni.showToast({ title: e.message || "获取邀请码失败", icon: "none" });
      }
    },
    closeQr() {
      this.qrModalShow = false;
    },
    /** 进入「创建员工」：与原型一致，从邀请二维码处可直接新建员工 */
    goCreateEmployee() {
      this.closeQr();
      uni.navigateTo({ url: "/pages/boss/member-edit" });
    },
    saveQr() {
      if (!this.qrImageUrl) return;
      // base64 data URL → 写入临时文件再保存相册
      if (this.qrImageUrl.startsWith("data:image")) {
        // #ifdef APP-PLUS
        // App 没有 wx.env / 小程序文件系统，用原生 Bitmap 落盘后再保存相册
        const bitmap = new plus.nativeObj.Bitmap(`qr_invite_${Date.now()}`);
        const appPath = `_doc/qr_invite_${Date.now()}.png`;
        bitmap.loadBase64Data(this.qrImageUrl, () => {
          bitmap.save(appPath, { overwrite: true }, () => {
            bitmap.clear();
            uni.saveImageToPhotosAlbum({
              filePath: appPath,
              success: () => uni.showToast({ title: "二维码已保存到相册", icon: "success" }),
              fail: () => uni.showToast({ title: "保存失败，请在系统设置中允许访问相册", icon: "none" }),
            });
          }, () => {
            bitmap.clear();
            uni.showToast({ title: "保存失败", icon: "none" });
          });
        }, () => {
          bitmap.clear();
          uni.showToast({ title: "保存失败", icon: "none" });
        });
        return;
        // #endif
        const base64 = this.qrImageUrl.split(",")[1];
        const fs = uni.getFileSystemManager();
        const tempPath = `${wx.env.USER_DATA_PATH || "/usrdata"}/qr_invite_${Date.now()}.png`;
        try {
          fs.writeFileSync(tempPath, base64, "base64");
          uni.saveImageToPhotosAlbum({
            filePath: tempPath,
            success: () => uni.showToast({ title: "二维码已保存到相册", icon: "success" }),
            fail: (err) => {
              if (err?.errMsg?.includes("auth deny")) {
                uni.showModal({
                  title: "需要相册权限",
                  content: "请在微信设置中开启相册权限后重试",
                  confirmText: "去设置",
                  success: (r) => { if (r.confirm) uni.openSetting?.(); },
                });
              } else {
                uni.showToast({ title: "保存失败", icon: "none" });
              }
            },
          });
        } catch (e) {
          uni.showToast({ title: "保存失败", icon: "none" });
        }
      } else {
        uni.downloadFile({
          url: this.qrImageUrl,
          success: (res) => {
            uni.saveImageToPhotosAlbum({
              filePath: res.tempFilePath,
              success: () => uni.showToast({ title: "二维码已保存到相册", icon: "success" }),
              fail: () => uni.showToast({ title: "保存失败", icon: "none" }),
            });
          },
          fail: () => uni.showToast({ title: "下载失败", icon: "none" }),
        });
      }
    },
    shareQr() {
      uni.setClipboardData({
        data: this.qrInfo.link || "",
        success: () => uni.showToast({ title: "邀请链接已复制，可粘贴分享", icon: "none" }),
      });
    },
    shareWechat() {
      uni.setClipboardData({
        data: this.qrInfo.link || "https://kuaima.com/invite",
        success: () => uni.showToast({ title: "邀请链接已复制，可粘贴到微信", icon: "none" }),
      });
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
  background: #f3f4f6;
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

.block-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 15px;
  font-weight: 700;
  color: #333;
  margin: 4px 0 10px;
}

.block-ico {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.iv-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
  overflow: hidden;
}

.iv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.iv-item:last-child {
  border-bottom: none;
}

.iv-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.iv-ico {
  width: 18px;
  height: 18px;
}

.iv-main {
  flex: 1;
  min-width: 0;
}

.iv-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  display: block;
}

.iv-desc {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.wb-row-arrow {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.iv-phone {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.iv-phone input {
  flex: 1;
  border: 1px solid #eee;
  background: #fafafa;
  border-radius: 10px;
  padding: 11px 14px;
  font-size: 14px;
}

.iv-add-btn {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 11px 18px;
  border-radius: 9999px;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.iv-add-ico {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.role-opts {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
}

.role-opt {
  flex: 1;
  border: 1.5px solid #eee;
  border-radius: 12px;
  padding: 12px;
  text-align: center;
}

.r-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  display: block;
}

.r-desc {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  line-height: 16px;
  display: block;
  white-space: pre-line;
}

.role-opt.active {
  border-color: #ff6b35;
  background: #fff5ee;
}

.role-opt.active .r-name {
  color: #ff6b35;
}

.rc-card {
  background: #fff;
  border-radius: 16px;
  padding: 4px 16px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.rc-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid #f5f5f5;
}

.rc-row:last-child {
  border-bottom: none;
}

.rc-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8b5cf6, #6d28d9);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rc-main {
  flex: 1;
  min-width: 0;
}

.rc-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rc-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.wb-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 5px;
  font-weight: 600;
}

.wb-tag.green {
  background: #e8f8ef;
  color: #10b981;
}

.wb-tag.orange {
  background: #fff7e0;
  color: #d97706;
}

.rc-desc {
  font-size: 12px;
  color: #999;
  margin-top: 3px;
  display: block;
}

.remind-link {
  color: #ff6b35;
  font-size: 12px;
}

.wb-empty {
  padding: 30px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.wb-empty-icon {
  width: 34px;
  height: 34px;
  margin-bottom: 10px;
}

.wb-empty-text {
  font-size: 13px;
  color: #999;
}

.wb-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 32px;
}

.wb-modal-box {
  background: #fff;
  border-radius: 18px;
  width: 100%;
  overflow: hidden;
}

.wb-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 0;
}

.wb-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
}

.wb-modal-close-ico {
  width: 16px;
  height: 16px;
  padding: 4px;
  flex-shrink: 0;
}

.qr-box {
  text-align: center;
  padding: 10px 0 6px;
}

.qr-img {
  width: 190px;
  height: 190px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 14px;
  padding: 12px;
}

.qr-image {
  width: 100%;
  height: 100%;
}

.qr-code-text {
  display: inline-block;
  margin-top: 10px;
  background: #f7f7f7;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  color: #ff6b35;
  font-weight: 600;
  letter-spacing: 1px;
}

.qr-tip {
  font-size: 12px;
  color: #999;
  margin-top: 12px;
  line-height: 18px;
  display: block;
  white-space: pre-line;
}

.qr-create-link {
  display: block;
  margin-top: 14px;
  font-size: 12.5px;
  color: #FF6B35;
  font-weight: 500;
}

.wb-modal-footer {
  display: flex;
  border-top: 1px solid #f5f5f5;
}

.wb-modal-footer view {
  flex: 1;
  text-align: center;
  padding: 14px 0;
  font-size: 15px;
}

.wb-btn-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.wb-btn-ico {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.wb-btn-outline {
  color: #666;
  border-right: 1px solid #f5f5f5;
}

.wb-btn-primary {
  color: #ff6b35;
  font-weight: 600;
}
</style>
