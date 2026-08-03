/**
 * 全局确认弹窗（Element Plus 适配层）
 *
 * 用法：
 *   const { confirm, prompt } = useConfirm()
 *   const ok = await confirm('确定删除？', '此操作不可恢复')
 *   const reason = await prompt('取消订单', '请输入原因')
 *   const score = await prompt('评价', '', { type: 'number', placeholder: '1-5分' })
 */
import { ElMessageBox } from 'element-plus'

interface PromptOptions {
  type?: string
  placeholder?: string
  default?: string
}

export function useConfirm() {
  /** 确认对话框：点确定返回 true，点取消/关闭返回 false */
  async function confirm(
    title: string,
    message?: string,
    opts?: { confirmText?: string; cancelText?: string }
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(message || '', title, {
        confirmButtonText: opts?.confirmText || '确定',
        cancelButtonText: opts?.cancelText || '取消',
        type: 'warning',
        closeOnClickModal: false
      })
      return true
    } catch {
      return false
    }
  }

  /** 输入对话框：返回输入内容；点取消返回 false */
  async function prompt(
    title: string,
    message?: string,
    opts?: PromptOptions
  ): Promise<string | false> {
    try {
      const { value } = await ElMessageBox.prompt(message || '', title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: opts?.type || 'text',
        inputPlaceholder: opts?.placeholder || '',
        inputValue: opts?.default || '',
        closeOnClickModal: false
      })
      return value
    } catch {
      return false
    }
  }

  return { confirm, prompt }
}
