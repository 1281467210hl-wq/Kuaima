<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: `${statusBarHeight}px` }">
      <view class="nav-inner">
        <view class="nav-back" @click="goBack">‹</view>
        <text class="nav-title">发单模版</text>
        <view class="nav-space" />
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="loading" class="state">模板加载中...</view>
      <view v-else-if="loadError" class="state">{{ loadError }}</view>
      <view v-else-if="!templates.length" class="state">暂无招工模板</view>
      <template v-else>
        <view v-for="item in templates" :key="item.id" class="tpl-card">
          <view class="tpl-name-row">模版名称：<text>{{ item.templateName }}</text></view>
          <view class="tpl-job-row">
            <text class="tpl-job-name">{{ item.jobName }}</text>
            <view class="tpl-job-price"><text>{{ item.wage }}</text><small>{{ item.unit }}</small></view>
          </view>
          <view class="tpl-tags">
            <text class="tpl-tag highlight">{{ item.tag }}</text>
            <text class="tpl-tag plain">{{ item.time }}</text>
            <text class="tpl-tag plain">{{ item.hours }}</text>
            <text class="tpl-tag plain">{{ item.count }}</text>
          </view>
          <view class="tpl-tags">
            <text class="tpl-tag plain">{{ item.age }}</text>
            <text class="tpl-tag plain">{{ item.gender }}</text>
          </view>
          <view class="tpl-actions">
            <text class="tpl-btn ghost" @click="deleteTemplate(item)">删除</text>
            <text class="tpl-btn ghost" @click="renameTemplate(item)">修改模版名称</text>
            <text class="tpl-btn main" @click="useTemplate(item)">使用</text>
          </view>
        </view>
        <view class="no-more">没有更多了</view>
      </template>
    </scroll-view>
  </view>
</template>

<script>
import {
  createBossOrderTemplate,
  deleteBossOrderTemplate,
  getBossOrderTemplate,
  listBossOrderTemplates,
  updateBossOrderTemplate,
} from "@/api/backend";
import { handleTokenInvalid } from "@/api/auth";

function getErrorMessage(error, fallback) {
  if (Number(error?.code || error?.statusCode) === 401) return "登录已失效，请重新登录";
  if (Number(error?.code || error?.statusCode) === 403) return "无权操作";
  if (Number(error?.code || error?.statusCode) === 404) return "模板不存在";
  return error?.message || fallback;
}

function decodeRouteValue(value, fallback = "") {
  if (value === undefined || value === null || value === "") return fallback;
  try {
    return decodeURIComponent(String(value));
  } catch (_) {
    return String(value);
  }
}

function extractClock(value) {
  const match = String(value || "").match(/(?:T|\s)(\d{1,2}:\d{2})/);
  return match ? match[1] : "";
}

export default {
  data() {
    return {
      statusBarHeight: 0,
      orderId: "",
      templateId: null,
      templateName: "",
      jobName: "",
      wage: "0",
      unit: "元/天",
      tag: "每天日结",
      time: "时间待定",
      hours: "工时待定",
      count: "招0人",
      age: "18岁-不限",
      gender: "男女不限",
      loading: false,
      loadError: "",
      operating: false,
      listMode: false,
      templates: [],
    };
  },
  async onLoad(options = {}) {
    const info = typeof uni.getWindowInfo === "function" ? uni.getWindowInfo() : uni.getSystemInfoSync();
    this.statusBarHeight = Number(info.statusBarHeight || 0);
    this.orderId = decodeRouteValue(options.orderId);
    this.templateId = decodeRouteValue(options.templateId) || null;
    this.templateName = decodeRouteValue(options.name, "未命名模板");
    this.jobName = decodeRouteValue(options.name, "未命名岗位");
    this.wage = decodeRouteValue(options.wage, "0");
    this.unit = decodeRouteValue(options.unit, "元/天");
    this.tag = decodeRouteValue(options.tag, "每天日结");
    this.time = decodeRouteValue(options.time, "时间待定");
    this.hours = decodeRouteValue(options.hours, "工时待定");
    this.count = decodeRouteValue(options.count, "招0人");
    this.age = decodeRouteValue(options.age, "18岁-不限");
    this.gender = decodeRouteValue(options.gender, "男女不限");
    if (this.templateId) {
      await this.loadTemplateDetail(this.templateId);
      return;
    }
    if (this.orderId) {
      const saved = await this.saveTemplate(false);
      if (saved && this.templateId) await this.loadTemplateDetail(this.templateId);
      return;
    }
    this.listMode = true;
    await this.loadTemplates();
  },
  methods: {
    normalizeTemplate(detail = {}) {
      const typeLabels = {
        daily: "每天日结",
        DAY: "每天日结",
        month: "月结",
        MONTHLY: "月结",
        heldBack: "压薪日结",
        PRESS: "压薪日结",
      };
      const start = detail.startTime ? extractClock(detail.startTime) : "";
      const end = detail.endTime ? extractClock(detail.endTime) : "";
      return {
        ...detail,
        id: detail.id || detail.templateId,
        templateName: detail.templateName || detail.orderTitle || "未命名模板",
        jobName: detail.orderTitle || detail.postion || "未命名岗位",
        wage: detail.salary ?? "0",
        unit: detail.unit || "元/天",
        tag: typeLabels[detail.type] || detail.settleMode || "每天日结",
        time: start && end ? `${start}开工 ${end}完工` : "时间待定",
        hours: detail.duration
          ? `${detail.duration}${String(detail.duration).includes("工") ? "" : "工时"}`
          : "工时待定",
        count: detail.orderNum != null ? `招${detail.orderNum}人` : "招0人",
        age: detail.experience || "经验不限",
        gender: detail.gender || "男女不限",
      };
    },
    async loadTemplates() {
      this.loading = true;
      this.loadError = "";
      try {
        const pageSize = 20;
        const rows = [];
        const seenIds = new Set();
        let page = 0;
        let hasMore = true;
        while (hasMore) {
          const result = await listBossOrderTemplates({ page, size: pageSize });
          const records = Array.isArray(result)
            ? result
            : result?.records || result?.content || [];
          let added = 0;
          records.forEach((item) => {
            const id = item?.id || item?.templateId;
            if (!id || seenIds.has(id)) return;
            seenIds.add(id);
            rows.push(item);
            added += 1;
          });
          const total = Number(result?.total);
          hasMore = Number.isFinite(total)
            ? rows.length < total && added > 0
            : records.length === pageSize && added > 0;
          page += 1;
        }
        this.templates = rows
          .filter((item) => item?.id || item?.templateId)
          .map((item) => this.normalizeTemplate(item));
      } catch (error) {
        this.templates = [];
        this.loadError = getErrorMessage(error, "模板加载失败");
        await this.handleRequestError(error, "模板加载失败");
      } finally {
        this.loading = false;
      }
    },
    async loadTemplateDetail(id) {
      this.loading = true;
      this.loadError = "";
      try {
        const detail = await getBossOrderTemplate(id);
        this.applyTemplateDetail(detail || {});
      } catch (error) {
        this.loadError = getErrorMessage(error, "模板加载失败");
        await this.handleRequestError(error, "模板加载失败");
      } finally {
        this.loading = false;
      }
    },
    applyTemplateDetail(detail) {
      const normalized = this.normalizeTemplate(detail);
      this.templates = normalized.id ? [normalized] : [];
      this.templateId = detail.id || detail.templateId || this.templateId;
      this.orderId = detail.sourceOrderId || detail.orderId || this.orderId;
      this.templateName = detail.templateName || detail.orderTitle || this.templateName || "未命名模板";
      this.jobName = detail.orderTitle || detail.postion || this.jobName || "未命名岗位";
      this.wage = detail.salary ?? this.wage ?? "0";
      this.unit = detail.unit || this.unit || "元/天";
      this.tag = normalized.tag;
      this.time = normalized.time;
      this.hours = detail.duration ? `${detail.duration}${String(detail.duration).includes("工") ? "" : "工时"}` : (this.hours || "工时待定");
      this.count = detail.orderNum != null ? `招${detail.orderNum}人` : (this.count || "招0人");
      this.age = detail.experience || this.age || "18岁-不限";
      this.gender = detail.gender || this.gender || "男女不限";
    },
    async handleRequestError(error, fallback) {
      const status = Number(error?.code || error?.statusCode);
      if (status === 401) return handleTokenInvalid({ role: "boss" });
      uni.showToast({ title: getErrorMessage(error, fallback), icon: "none" });
    },
    goBack() {
      uni.navigateBack();
    },
    async saveTemplate(overwrite = false) {
      if (!this.orderId || !this.templateName.trim() || this.operating) {
        if (!this.orderId) uni.showToast({ title: "缺少订单信息", icon: "none" });
        return false;
      }
      this.operating = true;
      try {
        const result = await createBossOrderTemplate(this.orderId, {
          templateName: this.templateName.trim(),
          overwrite,
        });
        this.templateId = result?.id || result?.templateId || this.templateId;
        uni.$emit("bossTemplateChanged");
        uni.showToast({ title: overwrite ? "模板已覆盖" : "模板已收藏", icon: "success" });
        return true;
      } catch (error) {
        const duplicate = Number(error?.code || error?.statusCode) === 400 || /重复|已存在|duplicate/i.test(error?.message || "");
        if (!overwrite && duplicate) {
          uni.showModal({
            title: "模板名称重复",
            content: error?.message || "已存在同名模板，是否覆盖？",
            confirmText: "覆盖",
            success: ({ confirm }) => {
              if (confirm) this.saveTemplate(true);
            },
          });
        } else {
          await this.handleRequestError(error, "模板保存失败");
        }
        return false;
      } finally {
        this.operating = false;
      }
    },
    renameTemplate(item) {
      const templateId = item?.id || this.templateId;
      if (!templateId || this.operating) {
        uni.showToast({ title: "请先保存模板", icon: "none" });
        return;
      }
      uni.showModal({
        title: "修改模版名称",
        editable: true,
        placeholderText: "请输入模版名称",
        content: item?.templateName || this.templateName,
        success: ({ confirm, content }) => {
          if (confirm && content?.trim()) this.renameTemplateRequest(templateId, content.trim());
        },
      });
    },
    async renameTemplateRequest(templateId, name) {
      this.operating = true;
      try {
        await updateBossOrderTemplate(templateId, { templateName: name });
        this.templateName = name;
        if (this.listMode) await this.loadTemplates();
        else await this.loadTemplateDetail(templateId);
        uni.$emit("bossTemplateChanged");
        uni.showToast({ title: "名称已修改", icon: "success" });
      } catch (error) {
        await this.handleRequestError(error, "模板名称修改失败");
      } finally {
        this.operating = false;
      }
    },
    async deleteTemplate(item) {
      const templateId = item?.id || this.templateId;
      if (!templateId || this.operating) {
        uni.showToast({ title: "请先保存模板", icon: "none" });
        return;
      }
      uni.showModal({
        title: "删除模版",
        content: "确认删除当前模版？",
        confirmColor: "#f5222d",
        success: async ({ confirm }) => {
          if (!confirm) return;
          this.operating = true;
          try {
            await deleteBossOrderTemplate(templateId);
            uni.$emit("bossTemplateChanged");
            uni.showToast({ title: "模版已删除", icon: "success" });
            if (this.listMode) await this.loadTemplates();
            else setTimeout(() => uni.navigateBack(), 500);
          } catch (error) {
            await this.handleRequestError(error, "模版删除失败");
          } finally {
            this.operating = false;
          }
        },
      });
    },
    async useTemplate(item) {
      const templateId = item?.id || this.templateId;
      if (templateId) {
        uni.navigateTo({
          url: `/pages/boss-publish/publish-info?templateId=${encodeURIComponent(templateId)}`,
        });
        return;
      }
      const saved = await this.saveTemplate(false);
      if (saved) uni.navigateTo({ url: "/pages/boss/home" });
    },
  },
};
</script>

<style lang="scss" scoped>
.page { height:100vh; display:flex; flex-direction:column; overflow:hidden; background:#f3f4f6; }
.nav { flex-shrink:0; background:#fff; border-bottom:1px solid #f0f0f0; }
.nav-inner { height:50px; display:flex; align-items:center; padding:0 16px; position:relative; }
.nav-back { width:32px; height:32px; display:flex; align-items:center; justify-content:center; color:#333; font-size:26px; }
.nav-title { position:absolute; left:50%; transform:translateX(-50%); color:#222; font-size:17px; font-weight:700; }
.nav-space { width:32px; margin-left:auto; }
.content { flex:1; min-height:0; padding:12px 16px 30px; box-sizing:border-box; }
.tpl-card { padding:16px; margin-bottom:12px; border-radius:14px; background:#fff; box-shadow:0 2px 8px rgba(0,0,0,.04); }
.tpl-name-row { padding-bottom:12px; border-bottom:1px solid #f5f5f5; color:#222; font-size:16px; font-weight:700; }
.tpl-job-row { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; margin-top:12px; }
.tpl-job-name { min-width:0; color:#333; font-size:15px; font-weight:600; }
.tpl-job-price { display:flex; align-items:baseline; flex-shrink:0; color:#ff4d1f; font-size:22px; font-weight:800; line-height:1.1; white-space:nowrap; }
.tpl-job-price small { margin-left:2px; font-size:12px; font-weight:600; }
.tpl-tags { display:flex; flex-wrap:wrap; align-items:center; gap:8px; margin-top:10px; }
.tpl-tag { padding:2px 8px; border-radius:4px; font-size:12px; }
.tpl-tag.highlight { color:#fff; background:#ff6b35; font-weight:600; }
.tpl-tag.plain { color:#666; border:1px solid #ebedf0; background:#f7f8fa; }
.tpl-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:14px; padding-top:14px; border-top:1px solid #f5f5f5; }
.tpl-btn { padding:8px 18px; border-radius:999px; font-size:14px; }
.tpl-btn.ghost { color:#333; border:1px solid #e5e5e5; background:#fff; }
.tpl-btn.main { padding:8px 26px; color:#3e2723; background:#ffd96f; font-weight:700; }
.no-more { padding:24px 0; color:#b0b0b0; font-size:14px; text-align:center; }
.state { padding:120px 0; color:#999; font-size:14px; text-align:center; }
</style>
