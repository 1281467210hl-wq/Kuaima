import { useUserStore } from '@/stores/user'

/**
 * 解析权限对象，统一返回 { module: { key: boolean } } 结构
 * 支持 JSON 字符串或对象
 */
export function parsePermissions(perm) {
  if (!perm) return {}
  if (typeof perm === 'string') {
    try {
      return JSON.parse(perm)
    } catch {
      return {}
    }
  }
  return perm
}

/**
 * 新版权限 key 为「模块.页面」，页面值可能是：
 * - true / false：无按钮的页面
 * - { 按钮: true }：带按钮的页面，任一按钮勾选即视为可进入
 * 旧版两层 key（如 permUser.u1）通过 LEGACY_PAGE_KEYS 兼容读取。
 */
const LEGACY_BUTTON_KEYS = {
  'permJob.jobs.applicants': ['permJob.j3'],
  'permJob.jobs.toggle': ['permJob.j4']
}

const LEGACY_PAGE_KEYS = {
  'permUser.workers': ['permUser.u1'],
  'permUser.bosses': ['permUser.u2'],
  'permUser.credits': ['permUser.u1'],
  'permUser.cert': ['permContent.c1', 'permUser.u2'],
  'permUser.insurance': [],
  'permJob.jobs': ['permJob.j1', 'permJob.j3'],
  'permJob.audit': ['permJob.j2'],
  'permOrder.orders': ['permOrder.o1'],
  'permOrder.settlement': ['permOrder.o3'],
  'permMarketing.points': ['permSystem.sys1'],
  'permMarketing.coupons': [],
  'permMarketing.rewards': [],
  'permMarketing.academy': [],
  'permContent.notices': ['permContent.c3'],
  'permContent.rules': ['permContent.c4'],
  'permService.messages': ['permService.s1'],
  'permService.service': ['permService.s2'],
  'permSystem.settings': ['permSystem.sys3'],
  'permSystem.logs': ['permSystem.sys4'],
  'permEmployee.employees': ['permEmployee.e1'],
  'permPayroll.payroll': ['permPayroll.p1']
}

function pageGranted(value) {
  if (value === true) return true
  if (value && typeof value === 'object') {
    return Object.values(value).some(item => item === true
      || (item && typeof item === 'object' && Object.values(item).some(Boolean)))
  }
  return false
}

/**
 * 判断当前登录用户是否拥有指定页面权限
 * @param {Object} permissions 权限树对象（新三层或旧两层结构）
 * @param {String} permKey 新版权限 key，如 'permUser.workers'
 * @returns {Boolean}
 */
export function hasPerm(permissions, permKey) {
  if (!permKey) return true // 未配置权限 key 视为公开
  // 超级管理员拥有全部权限
  const role = localStorage.getItem('admin_role')
  if (role === 'SUPER_ADMIN') return true
  const perms = parsePermissions(permissions)
  if (!perms) return false
  const parts = String(permKey).split('.')
  const [mod, page] = parts
  if (!mod || !page) return false
  const modPerm = perms[mod]
  if (!modPerm || typeof modPerm !== 'object') return false
  // 按钮权限：模块.页面.按钮，如 permUser.workers.freeze
  if (parts.length >= 3) {
    const button = parts.slice(2).join('.')
    const pagePerm = modPerm[page]
    if (pagePerm && typeof pagePerm === 'object' && pagePerm[button] === true) return true
    return (LEGACY_BUTTON_KEYS[permKey] || []).some(oldKey => {
      const [oldMod, oldPage] = oldKey.split('.')
      return perms[oldMod]?.[oldPage] === true
    })
  }
  if (pageGranted(modPerm[page])) return true
  // 兼容旧版两层 key 的历史权限数据
  return (LEGACY_PAGE_KEYS[permKey] || []).some(oldKey => {
    const [oldMod, oldPage] = oldKey.split('.')
    return perms[oldMod]?.[oldPage] === true
  })
}

/**
 * v-permission 指令：根据权限 key 控制元素显隐
 * 用法：v-permission="'permUser.u1'" 或 v-permission="['permUser.u1','permUser.u2']" (任一满足)
 */
export const vPermission = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const perms = userStore.userInfo.permissions
    const value = binding.value
    let ok = false
    if (Array.isArray(value)) {
      ok = value.some((k) => hasPerm(perms, k))
    } else {
      ok = hasPerm(perms, value)
    }
    if (!ok) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

export default { hasPerm, parsePermissions, vPermission }
