<template>
  <div class="admin-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <!-- Logo -->
    <div class="sidebar-header">
      <div class="sidebar-logo">
        <i class="fas fa-bolt"></i>
      </div>
      <div v-show="!appStore.sidebarCollapsed">
        <div class="sidebar-title">快马日结</div>
        <div class="sidebar-version">管理后台 v2.0</div>
      </div>
    </div>
    
    <!-- 菜单 -->
    <nav class="sidebar-menu">
      <template v-for="group in filteredMenuGroups" :key="group.id">
        <div v-if="group.items.length" class="menu-parent" :class="{ open: openGroups.has(group.id) }" @click="toggleGroup(group.id)">
          <i :class="['fas', group.icon]"></i>
          <span v-show="!appStore.sidebarCollapsed">{{ group.name }}</span>
          <i v-show="!appStore.sidebarCollapsed" class="fas fa-chevron-down menu-arrow"></i>
        </div>
        <template v-if="openGroups.has(group.id) || appStore.sidebarCollapsed">
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            active-class="active"
          >
            <i :class="['fas', item.icon]"></i>
            <span v-show="!appStore.sidebarCollapsed">{{ item.name }}</span>
          </router-link>
        </template>
      </template>
    </nav>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { hasPerm } from '@/utils/permission'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const menuGroups = [
  {
    id: 'dashboard',
    name: '数据统计',
    items: [
      { path: '/admin/dashboard', name: '数据概览', icon: 'fa-chart-line' }
    ]
  },
  {
    id: 'user',
    name: '用户管理',
    items: [
      { path: '/admin/workers', name: '零工管理', icon: 'fa-user', permKey: 'permUser.workers' },
      { path: '/admin/credit-manage', name: '信用分管理', icon: 'fa-star', permKey: 'permUser.credits' },
      { path: '/admin/bosses', name: '老板管理', icon: 'fa-building', permKey: 'permUser.bosses' },
      { path: '/admin/enterprise-cert', name: '企业认证', icon: 'fa-id-card', permKey: 'permUser.cert' },
      { path: '/admin/insurance', name: '保险管理', icon: 'fa-shield-halved', permKey: 'permUser.insurance' }
    ]
  },
  {
    id: 'job',
    name: '招工管理',
    items: [
      { path: '/admin/jobs', name: '招工管理', icon: 'fa-briefcase', permKey: 'permJob.jobs' },
      { path: '/admin/job-audit', name: '招工审核', icon: 'fa-check-circle', permKey: 'permJob.audit' }
    ]
  },
  {
    id: 'marketing',
    name: '营销管理',
    items: [
      { path: '/admin/point-purchase', name: '积分管理', icon: 'fa-coins', permKey: 'permMarketing.points' },
      { path: '/admin/coupon-manage', name: '优惠券管理', icon: 'fa-ticket-alt', permKey: 'permMarketing.coupons' },
      { path: '/admin/reward-manage', name: '奖励金管理', icon: 'fa-gift', permKey: 'permMarketing.rewards' },
      { path: '/admin/academy', name: '学堂管理', icon: 'fa-graduation-cap', permKey: 'permMarketing.academy' }
    ]
  },
  {
    id: 'order',
    name: '订单结算',
    items: [
      { path: '/admin/orders', name: '用工订单', icon: 'fa-clipboard-list', permKey: 'permOrder.orders' },
      { path: '/admin/settlement', name: '结算管理', icon: 'fa-coins', permKey: 'permOrder.settlement' }
    ]
  },
  {
    id: 'employee',
    name: '企业工作台',
    items: [
      { path: '/admin/employees', name: '员工管理', icon: 'fa-users', permKey: 'permEmployee.employees' },
      { path: '/admin/project-salary', name: '发薪管理', icon: 'fa-money-bill-wave', permKey: 'permPayroll.payroll' }
    ]
  },
  {
    id: 'content',
    name: '内容管理',
    items: [
      { path: '/admin/notices', name: '公告管理', icon: 'fa-bullhorn', permKey: 'permContent.notices' },
      { path: '/admin/rules', name: '规则管理', icon: 'fa-book', permKey: 'permContent.rules' },
      // { path: '/admin/banners', name: 'Banner管理', icon: 'fa-image', permKey: 'permContent.c2' }
    ]
  },
  {
    id: 'message',
    name: '消息客服',
    items: [
      { path: '/admin/messages', name: '消息管理', icon: 'fa-envelope', permKey: 'permService.messages' },
      { path: '/admin/service', name: '客服管理', icon: 'fa-headset', permKey: 'permService.service' }
    ]
  },
  {
    id: 'system',
    name: '系统管理',
    items: [
      { path: '/admin/settings', name: '系统设置', icon: 'fa-cog', permKey: 'permSystem.settings' },
      { path: '/admin/logs', name: '操作日志', icon: 'fa-file-alt', permKey: 'permSystem.logs' }
    ]
  }
]

// 根据当前管理员权限过滤菜单项；空分组自动隐藏
const filteredMenuGroups = computed(() => {
  const perms = userStore.userInfo?.permissions
  return menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.permKey || hasPerm(perms, item.permKey))
    }))
    .filter((group) => group.items.length > 0)
})

const openGroups = ref(new Set())

const toggleGroup = groupId => {
  const next = new Set(openGroups.value)
  if (next.has(groupId)) next.delete(groupId)
  else next.add(groupId)
  openGroups.value = next
}

const syncOpenGroupByRoute = () => {
  const activeGroup = filteredMenuGroups.value.find(group =>
    group.items.some(item => route.path === item.path || route.path.startsWith(item.path + '/'))
  )
  if (activeGroup) openGroups.value = new Set([activeGroup.id])
}

watch(() => route.path, syncOpenGroupByRoute, { immediate: true })

</script>

<style scoped>
.admin-sidebar {
  width: 220px;
  background: var(--sidebar-bg);
  color: #fff;
  flex-shrink: 0;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  overflow-y: auto;
  z-index: 100;
  transition: width 0.3s;
  
  &.collapsed {
    width: 64px;
    
    .menu-item span,
    .menu-group-title {
      display: none;
    }
  }
  
  &::-webkit-scrollbar {
    width: 4px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(255,255,255,0.1);
    border-radius: 2px;
  }
}

.sidebar-header {
  padding: 20px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-logo {
  width: 36px;
  height: 36px;
  background: var(--primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
}

.sidebar-version {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
}

.sidebar-menu {
  padding: 12px 0;
}

.menu-group-title {
  padding: 12px 20px 8px;
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
  text-decoration: none;
  font-size: 14px;
  position: relative;
  
  &:hover {
    background: var(--sidebar-hover);
    color: #fff;
  }
  
  &.active {
    background: rgba(255,107,53,0.1);
    color: var(--primary);
    border-left-color: var(--primary);
  }
  
  i {
    width: 20px;
    text-align: center;
    font-size: 14px;
  }
}

.menu-parent { display: flex; align-items: center; gap: 10px; padding: 11px 20px; color: rgba(255,255,255,.85); cursor: pointer; font-size: 14px; font-weight: 500; user-select: none; border-left: 3px solid transparent; }
.menu-parent:hover { background: rgba(255,255,255,.08); color: #fff; }
.menu-parent > i { width: 20px; text-align: center; font-size: 14px; }
.menu-parent .menu-arrow { margin-left: auto; font-size: 10px; opacity: .55; transition: transform .25s; }
.menu-parent.open .menu-arrow { transform: rotate(180deg); }
</style>
