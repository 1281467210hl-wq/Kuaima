<template>
  <view class="container">
    <BossPageHeader :title="isEdit ? '编辑岗位' : '新建岗位'" />

    <scroll-view scroll-y class="body" :style="{ paddingBottom: safeBottom + 70 + 'px' }">
      <!-- 基本信息 -->
      <view class="form-section-title">
        <text class="section-ico">🪪</text>基本信息
      </view>
      <view class="card" style="padding: 2px 16px">
        <view class="f-row">
          <text class="f-label">岗位名称<text class="req">*</text></text>
          <input class="f-input" v-model="form.name" placeholder="请输入岗位名称" />
        </view>
        <view class="f-row">
          <text class="f-label">岗位编码<text class="req">*</text></text>
          <input class="f-input" v-model="form.code" placeholder="请输入岗位编码" />
        </view>
        <view class="f-row" @click="openPicker('category')">
          <text class="f-label">岗位类别<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.category }">
            <text class="f-select-text">{{ form.category || "请选择岗位类别" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
        <view class="f-row" @click="openPicker('department')">
          <text class="f-label">所属部门<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.department }">
            <text class="f-select-text">{{ form.department || "请选择所属部门" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
        <view class="f-row" @click="openPicker('location')">
          <text class="f-label">工作地点<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.location }">
            <text class="f-select-text">{{ form.location || "请选择工作地点" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 招聘信息 -->
      <view class="form-section-title">
        <text class="section-ico">👤</text>招聘信息
      </view>
      <view class="card" style="padding: 2px 16px">
        <view class="f-row">
          <text class="f-label">招聘人数<text class="req">*</text></text>
          <input class="f-input" v-model="form.hireCount" type="number" placeholder="请输入招聘人数" />
        </view>
        <view class="f-row" @click="openPicker('salaryRange')">
          <text class="f-label">薪资范围<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.salaryRange }">
            <text class="f-select-text">{{ form.salaryRange || "请选择薪资范围" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
        <view class="f-row" @click="openPicker('experience')">
          <text class="f-label">工作经验<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.experience }">
            <text class="f-select-text">{{ form.experience || "请选择工作经验" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
        <view class="f-row" @click="openPicker('education')">
          <text class="f-label">学历要求<text class="req">*</text></text>
          <view class="f-select" :class="{ 'has-val': form.education }">
            <text class="f-select-text">{{ form.education || "请选择学历要求" }}</text>
            <text class="f-select-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 岗位描述 -->
      <view class="form-section-title">
        <text class="section-ico">📝</text>岗位描述
      </view>
      <view class="card" style="padding: 12px 16px">
        <textarea
          class="f-textarea"
          v-model="form.description"
          placeholder="请输入岗位描述..."
          maxlength="500"
        />
        <text class="textarea-counter">{{ (form.description || "").length }}/500</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>

    <!-- 底部按钮 -->
    <view class="footer">
      <view v-if="isEdit" class="footer-btn outline" @click="goBack">取消</view>
      <view class="footer-btn primary" @click="saveForm">
        <text class="footer-btn-ico">✓</text>保存
      </view>
    </view>

    <!-- 选择抽屉 -->
    <view v-if="showPicker" class="pick-mask" @click="closePicker" />
    <view class="pick-sheet" :class="{ show: showPicker }">
      <view class="pick-head">
        <text class="pick-cancel" @click="closePicker">取消</text>
        <text class="pick-title">{{ pickerTitle }}</text>
        <view style="width: 32px" />
      </view>
      <scroll-view scroll-y class="pick-opts">
        <view
          v-for="opt in pickerOptions"
          :key="opt"
          class="pick-opt"
          :class="{ sel: isSelected(opt) }"
          @click="pickValue(opt)"
        >
          <text class="pick-opt-text">{{ opt }}</text>
          <text v-if="isSelected(opt)" class="pick-opt-check">✓</text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import BossPageHeader from "@/components/BossPageHeader.vue";
import { getPosition, createPosition, updatePosition } from "@/api/position";

const OPTIONS = {
  category: ["分拣打包类", "搬运装卸类", "餐饮服务类", "仓储理货类", "生产制造类", "物流快递类", "其他"],
  department: ["仓储部", "配送部", "餐饮部", "生产部", "物流部", "人事部", "综合部"],
  location: ["南昌 · 青山湖区", "南昌 · 高新区", "南昌 · 经开区", "九江 · 浔阳区", "赣州 · 章贡区", "上饶 · 信州区"],
  salaryRange: ["100-150元/天", "150-200元/天", "180-260元/天", "200-300元/天", "300元以上/天", "面议"],
  experience: ["不限", "无经验可做", "1个月以内", "1-3个月", "3-6个月", "6个月以上"],
  education: ["不限", "初中及以上", "高中/中专及以上", "大专及以上"],
};

const LABELS = {
  category: "岗位类别",
  department: "所属部门",
  location: "工作地点",
  salaryRange: "薪资范围",
  experience: "工作经验",
  education: "学历要求",
};

export default {
  components: { BossPageHeader },
  data() {
    return {
      isEdit: false,
      positionId: "",
      form: {
        name: "",
        code: "",
        category: "",
        department: "",
        location: "",
        hireCount: "",
        salaryRange: "",
        experience: "",
        education: "",
        description: "",
      },
      showPicker: false,
      currentKey: "",
      pickerTitle: "",
      pickerOptions: [],
      safeBottom: 0,
    };
  },
  onLoad(query) {
    this.isEdit = query.mode === "edit";
    this.positionId = query.id || "";
    if (this.isEdit && this.positionId) {
      this.loadPosition();
    }
    try {
      const sysInfo = uni.getSystemInfoSync();
      this.safeBottom = sysInfo.safeAreaInsets?.bottom || 0;
    } catch (e) {
      // ignore
    }
  },
  methods: {
    async loadPosition() {
      try {
        const data = await getPosition(this.positionId).catch(() => ({}));
        if (data) {
          this.form = {
            name: data.name || "",
            code: data.code || "",
            category: data.category || "",
            department: data.department || "",
            location: data.location || "",
            hireCount: data.hireCount || "",
            salaryRange: data.salaryRange || "",
            experience: data.experience || "",
            education: data.education || "",
            description: data.description || "",
          };
        }
      } catch (e) {
        console.warn("岗位数据加载失败", e);
      }
    },
    openPicker(key) {
      this.currentKey = key;
      this.pickerTitle = "请选择" + LABELS[key];
      this.pickerOptions = OPTIONS[key] || [];
      this.showPicker = true;
    },
    closePicker() {
      this.showPicker = false;
    },
    isSelected(opt) {
      return this.form[this.currentKey] === opt;
    },
    pickValue(val) {
      this.form[this.currentKey] = val;
      this.closePicker();
    },
    async saveForm() {
      if (!this.form.name.trim()) {
        uni.showToast({ title: "请输入岗位名称", icon: "none" });
        return;
      }
      if (!this.form.code.trim()) {
        uni.showToast({ title: "请输入岗位编码", icon: "none" });
        return;
      }
      if (!this.form.category) {
        uni.showToast({ title: "请选择岗位类别", icon: "none" });
        return;
      }
      if (!this.form.department) {
        uni.showToast({ title: "请选择所属部门", icon: "none" });
        return;
      }
      if (!this.form.location) {
        uni.showToast({ title: "请选择工作地点", icon: "none" });
        return;
      }
      if (!this.form.hireCount) {
        uni.showToast({ title: "请输入招聘人数", icon: "none" });
        return;
      }
      if (!this.form.salaryRange) {
        uni.showToast({ title: "未填写薪资", icon: "none" });
        return;
      }
      if (!this.form.experience) {
        uni.showToast({ title: "未填写工作经验", icon: "none" });
        return;
      }
      if (!this.form.education) {
        uni.showToast({ title: "未填写学历要求", icon: "none" });
        return;
      }
      try {
        const data = {
          name: this.form.name.trim(),
          code: this.form.code.trim(),
          category: this.form.category,
          department: this.form.department,
          location: this.form.location,
          hireCount: parseInt(this.form.hireCount) || 0,
          salaryRange: this.form.salaryRange,
          experience: this.form.experience,
          education: this.form.education,
          description: this.form.description,
        };
        if (this.isEdit) {
          await updatePosition(this.positionId, data);
          uni.showToast({ title: "岗位已更新", icon: "success" });
        } else {
          await createPosition(data);
          uni.showToast({ title: "岗位已创建", icon: "success" });
        }
        setTimeout(() => {
          uni.navigateBack();
        }, 1000);
      } catch (e) {
        uni.showToast({ title: "保存失败，请重试", icon: "none" });
      }
    },
    goBack() {
      uni.navigateBack();
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
.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 16px 2px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-ico {
  font-size: 13px;
}
.card {
  background: #fff;
  border-radius: 16px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.f-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 0.5px solid #f6f6f6;
}
.f-row:last-child {
  border-bottom: none;
}
.f-label {
  width: 84px;
  flex-shrink: 0;
  font-size: 13.5px;
  color: #333;
}
.req {
  color: #ff4d4f;
  margin-left: 2px;
}
.f-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13.5px;
  color: #333;
  background: transparent;
}
.f-input::placeholder {
  color: #b8b8b8;
}
.f-select {
  flex: 1;
  font-size: 13.5px;
  color: #b8b8b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.f-select.has-val {
  color: #333;
}
.f-select-text {
  flex: 1;
}
.f-select-arrow {
  color: #ccc;
  font-size: 16px;
}
.f-textarea {
  width: 100%;
  min-height: 100px;
  font-size: 13.5px;
  color: #333;
  line-height: 1.6;
}
.textarea-counter {
  display: block;
  text-align: right;
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
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
  flex: 1.2;
}
.footer-btn-ico {
  margin-right: 6px;
}
.pick-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 90;
}
.pick-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  z-index: 95;
  border-radius: 18px 18px 0 0;
  transform: translateY(100%);
  transition: transform 0.25s;
  padding-bottom: 24px;
  max-height: 62%;
  display: flex;
  flex-direction: column;
}
.pick-sheet.show {
  transform: translateY(0);
}
.pick-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 18px;
  border-bottom: 0.5px solid #f0f0f0;
}
.pick-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.pick-cancel {
  font-size: 13.5px;
  color: #999;
  cursor: pointer;
}
.pick-opts {
  overflow-y: auto;
  padding: 6px 0;
  max-height: 300px;
}
.pick-opt {
  padding: 14px 18px;
  font-size: 14px;
  color: #444;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pick-opt:active {
  background: #fafafa;
}
.pick-opt.sel {
  color: #ff6b35;
  font-weight: 600;
}
.pick-opt-text {
  flex: 1;
}
.pick-opt-check {
  color: #ff6b35;
}
</style>
