/**
 * 文件上传 API
 * 上传图片到阿里云 OSS
 */
import request from '@/utils/request'

/**
 * 上传单张图片
 * @param file 图片文件
 * @param folder 存储目录：'avatar' | 'help'
 * @returns 图片URL
 *
 * 注意：axios 实例默认 header 是 application/json，
 * 上传 FormData 必须显式设 multipart/form-data 覆盖，
 * axios 会自动补上正确的 boundary=...
 */
export function uploadImage(file: File, folder: 'avatar' | 'help' = 'help'): Promise<{ data: { url: string } }> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('folder', folder)
  return request.post('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
