/**
 * 全局通知（Element Plus 适配层）
 *
 * 业务代码统一用这里的 showToast，不直接依赖 Element Plus。
 * 将来换 UI 库只改这一个文件。
 */
import { ElMessage } from 'element-plus'

type ToastType = 'success' | 'error' | 'warning' | 'info'

/**
 * 显示轻量通知
 * @example showToast('保存成功', 'success')
 */
export function showToast(message: string, type: ToastType = 'info'): void {
  ElMessage({
    message,
    type,
    duration: 2000,
    grouping: true,  // 同内容合并，避免连点刷屏
    showClose: false
  })
}
