import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { hasPerm } from '@/utils/permission'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '管理员登录', public: true }
  },
  {
    path: '/',
    name: 'Home',
    redirect: '/login',
    meta: { title: '快马日结管理后台', public: true }
  },
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      // 数据统计（所有角色可见）
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '数据概览', icon: 'fa-chart-line' }
      },
      // 用户管理
      {
        path: 'workers',
        name: 'Workers',
        component: () => import('@/views/user/Workers.vue'),
        meta: { title: '零工管理', icon: 'fa-user', permKey: 'permUser.workers' }
      },
      {
        path: 'workers/detail/:id',
        name: 'WorkerDetail',
        component: () => import('@/views/user/WorkerDetail.vue'),
        meta: { title: '零工详情', icon: 'fa-eye', hidden: true, permKey: 'permUser.workers' }
      },
      {
        path: 'credit-manage',
        name: 'CreditManage',
        component: () => import('@/views/user/CreditManage.vue'),
        meta: { title: '信用分管理', icon: 'fa-star', permKey: 'permUser.credits' }
      },
      {
        path: 'bosses',
        name: 'Bosses',
        component: () => import('@/views/user/Bosses.vue'),
        meta: { title: '老板管理', icon: 'fa-building', permKey: 'permUser.bosses' }
      },
      {
        path: 'bosses/detail/:id',
        name: 'BossDetail',
        component: () => import('@/views/user/BossDetail.vue'),
        meta: { title: '老板详情', icon: 'fa-eye', hidden: true, permKey: 'permUser.bosses' }
      },
      {
        path: 'enterprise-cert',
        name: 'EnterpriseCert',
        component: () => import('@/views/user/EnterpriseCert.vue'),
        meta: { title: '企业认证', icon: 'fa-id-card', permKey: 'permUser.cert' }
      },
      {
        path: 'insurance',
        name: 'InsuranceManage',
        component: () => import('@/views/user/InsuranceManage.vue'),
        meta: { title: '保险管理', icon: 'fa-shield-halved', permKey: 'permUser.insurance' }
      },
      {
        path: 'insurance/create',
        name: 'InsuranceCreate',
        component: () => import('@/views/user/InsuranceForm.vue'),
        meta: { title: '新建保险产品', icon: 'fa-plus', hidden: true, permKey: 'permUser.insurance' }
      },
      {
        path: 'insurance/edit',
        name: 'InsuranceEdit',
        component: () => import('@/views/user/InsuranceForm.vue'),
        meta: { title: '编辑保险产品', icon: 'fa-edit', hidden: true, permKey: 'permUser.insurance' }
      },
      {
        path: 'insurance/detail',
        name: 'InsuranceDetail',
        component: () => import('@/views/user/InsuranceDetail.vue'),
        meta: { title: '保险产品详情', icon: 'fa-eye', hidden: true, permKey: 'permUser.insurance' }
      },
      {
        path: 'academy',
        name: 'Academy',
        component: () => import('@/views/content/Academy.vue'),
        meta: { title: '学堂管理', icon: 'fa-graduation-cap', permKey: 'permMarketing.academy' }
      },
      // 招工管理
      {
        path: 'jobs',
        name: 'Jobs',
        component: () => import('@/views/job/Jobs.vue'),
        meta: { title: '招工管理', icon: 'fa-briefcase', permKey: 'permJob.jobs' }
      },
      {
        path: 'jobs/edit/:id?',
        name: 'JobEdit',
        component: () => import('@/views/job/JobEdit.vue'),
        meta: { title: '编辑招工', icon: 'fa-edit', hidden: true, permKey: 'permJob.jobs' }
      },
      {
        path: 'jobs/applicants/:id',
        name: 'JobApplicants',
        component: () => import('@/views/job/JobApplicants.vue'),
        meta: { title: '报名人员', icon: 'fa-users', hidden: true, permKey: 'permJob.jobs' }
      },
      {
        path: 'job-audit',
        name: 'JobAudit',
        component: () => import('@/views/job/JobAudit.vue'),
        meta: { title: '招工审核', icon: 'fa-check-circle', permKey: 'permJob.audit' }
      },
      {
        path: 'point-purchase',
        name: 'PointPurchase',
        component: () => import('@/views/point/PointPurchase.vue'),
        meta: { title: '积分管理', icon: 'fa-coins', permKey: 'permMarketing.points' }
      },
      {
        path: 'coupon-manage',
        name: 'CouponManage',
        component: () => import('@/views/point/CouponManage.vue'),
        meta: { title: '优惠券管理', icon: 'fa-ticket-alt', permKey: 'permMarketing.coupons' }
      },
      {
        path: 'reward-manage',
        name: 'RewardManage',
        component: () => import('@/views/point/RewardManage.vue'),
        meta: { title: '奖励金管理', icon: 'fa-gift', permKey: 'permMarketing.rewards' }
      },
      // 订单结算
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/order/Orders.vue'),
        meta: { title: '用工订单', icon: 'fa-clipboard-list', permKey: 'permOrder.orders' }
      },
      {
        path: 'settlement',
        name: 'Settlement',
        component: () => import('@/views/order/Settlement.vue'),
        meta: { title: '结算管理', icon: 'fa-coins', permKey: 'permOrder.settlement' }
      },
      {
        path: 'settlement/detail/:id',
        name: 'SettlementDetail',
        component: () => import('@/views/order/SettlementDetail.vue'),
        meta: { title: '结算详情', icon: 'fa-eye', hidden: true, permKey: 'permOrder.settlement' }
      },
      // 发薪与员工
      {
        path: 'project-salary',
        name: 'ProjectSalary',
        component: () => import('@/views/payroll/ProjectSalary.vue'),
        meta: { title: '发薪管理', icon: 'fa-money-bill-wave', permKey: 'permPayroll.payroll' }
      },
      // 企业工作台
      {
        path: 'enterprise-member',
        name: 'EnterpriseMember',
        component: () => import('@/views/employee/EnterpriseMember.vue'),
        meta: { title: '企业成员', icon: 'fa-sitemap', permKey: 'permEmployee.employees' }
      },
      {
        path: 'project-mgmt',
        name: 'ProjectMgmt',
        component: () => import('@/views/project/ProjectMgmt.vue'),
        meta: { title: '项目管理', icon: 'fa-tasks', permKey: 'permEmployee.employees' }
      },
      {
        path: 'project-detail/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetail.vue'),
        meta: { title: '项目详情', icon: 'fa-circle-info', hidden: true, permKey: 'permEmployee.employees' }
      },
      {
        path: 'smart-recruit',
        name: 'SmartRecruit',
        component: () => import('@/views/recruit/SmartRecruit.vue'),
        meta: { title: '智能招聘', icon: 'fa-magnifying-glass-plus', permKey: 'permJob.jobs' }
      },
      {
        path: 'payroll-detail/:id',
        name: 'PayrollDetail',
        component: () => import('@/views/payroll/PayrollDetail.vue'),
        meta: { title: '薪单详情', icon: 'fa-eye', hidden: true, permKey: 'permPayroll.payroll' }
      },
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employee/EmployeeManage.vue'),
        meta: { title: '员工管理', icon: 'fa-users', permKey: 'permEmployee.employees' }
      },
      // 内容管理
      {
        path: 'certification',
        name: 'Certification',
        component: () => import('@/views/content/Certification.vue'),
        meta: { title: '认证审核', icon: 'fa-id-card', permKey: 'permUser.cert' }
      },
      {
        path: 'certification/detail/:id',
        name: 'CertificationDetail',
        component: () => import('@/views/content/CertificationDetail.vue'),
        meta: { title: '认证详情', icon: 'fa-eye', hidden: true, permKey: 'permUser.cert' }
      },
      // {
      //   path: 'banners',
      //   name: 'Banners',
      //   component: () => import('@/views/content/Banners.vue'),
      //   meta: { title: 'Banner管理', icon: 'fa-image', permKey: 'permContent.c2' }
      // },
      {
        path: 'notices',
        name: 'Notices',
        component: () => import('@/views/content/Notices.vue'),
        meta: { title: '公告管理', icon: 'fa-bullhorn', permKey: 'permContent.notices' }
      },
      {
        path: 'rules',
        name: 'Rules',
        component: () => import('@/views/content/Rules.vue'),
        meta: { title: '规则管理', icon: 'fa-book', permKey: 'permContent.rules' }
      },
      {
        path: 'rules/edit/:id?/:tab?',
        name: 'RulesEdit',
        component: () => import('@/views/content/RulesEdit.vue'),
        meta: { title: '编辑规则', icon: 'fa-edit', hidden: true, permKey: 'permContent.rules' }
      },
      // 消息客服
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/message/Messages.vue'),
        meta: { title: '消息管理', icon: 'fa-envelope', permKey: 'permService.messages' }
      },
      {
        path: 'service',
        name: 'Service',
        component: () => import('@/views/message/Service.vue'),
        meta: { title: '客服管理', icon: 'fa-headset', permKey: 'permService.service' }
      },
      {
        path: 'service/chat/:id',
        name: 'ServiceChat',
        component: () => import('@/views/message/ServiceChat.vue'),
        meta: { title: '会话处理', icon: 'fa-comments', hidden: true, permKey: 'permService.service' }
      },
      // 系统管理
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/system/Settings.vue'),
        meta: { title: '系统设置', icon: 'fa-cog', permKey: 'permSystem.settings' }
      },
      {
        path: 'template-edit',
        name: 'TemplateEdit',
        component: () => import('@/views/system/TemplateEdit.vue'),
        meta: { title: '模板编辑', icon: 'fa-edit', hidden: true, permKey: 'permSystem.settings' }
      },
      {
        path: 'admin-user/form',
        name: 'AdminUserForm',
        component: () => import('@/views/system/AdminUserForm.vue'),
        meta: { title: '新建账号', icon: 'fa-user-plus', hidden: true, permKey: 'permSystem.settings' }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/system/Logs.vue'),
        meta: { title: '操作日志', icon: 'fa-file-alt', permKey: 'permSystem.logs' }
      },
      // 个人资料/密码/账户：所有登录用户可见，无需额外权限
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/system/Profile.vue'),
        meta: { title: '个人资料', icon: 'fa-user-circle', hidden: true }
      },
      {
        path: 'password',
        name: 'Password',
        component: () => import('@/views/system/Password.vue'),
        meta: { title: '修改密码', icon: 'fa-key', hidden: true }
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('@/views/system/Account.vue'),
        meta: { title: '账户设置', icon: 'fa-cog', hidden: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/admin/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录校验 + 权限校验
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token') || sessionStorage.getItem('admin_token')

  if (to.meta.public) {
    return next()
  }
  if (!token) {
    return next('/login')
  }

  // 权限校验：路由 meta.permKey 配置了所需权限
  const permKey = to.meta?.permKey
  if (permKey) {
    const userStore = useUserStore()
    const perms = userStore.userInfo?.permissions
    if (!hasPerm(perms, permKey)) {
      // 无权限：回到首页（dashboard），避免死循环
      if (to.path === '/admin/dashboard') {
        return next()
      }
      return next('/admin/dashboard')
    }
  }
  next()
})

export default router
