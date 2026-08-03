/**
 * 前端权限控制
 * 基于角色（role）控制页面和功能访问
 *
 * 角色定义：
 *   1 — 普通用户
 *   2 — 管理员
 */

import { useUserStore } from '@/stores/user'
import type { RouteLocationNormalized } from 'vue-router'

/** 需要管理员权限的路由名称 */
const adminRoutes = ['admin']

/**
 * 检查当前用户是否有权访问目标路由
 * @returns true=放行, false=拦截
 */
export function checkRoutePermission(to: RouteLocationNormalized): boolean {
  const userStore = useUserStore()
  const routeName = to.name as string

  // 1. 不需要登录 → 直接放行
  const needAuth = to.meta?.requireAuth === true || adminRoutes.includes(routeName)
  if (!needAuth) {
    return true
  }

  // 2. 需要登录但未登录 → 拦截
  if (!userStore.isLoggedIn) {
    return false
  }

  // 3. 需要管理员但当前用户不是管理员 → 拦截
  if (adminRoutes.includes(routeName) && !userStore.isAdmin) {
    return false
  }

  return true
}

export { adminRoutes }
