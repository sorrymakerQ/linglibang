import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Element Plus 样式 & 中文语言包
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import ElementPlus from 'element-plus'
// 图标（自动导入组件会按需注册用到的图标，这里做兜底全量注册，方便动态图标名场景）
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import './assets/style.css'

const app = createApp(App)

// Element Plus（组件本身由 unplugin 按需注册；这里传 locale 让 message/pagination 等全局中文）
app.use(ElementPlus, { locale: zhCn })

// 图标全量注册（组件名 = 图标名，如 <el-icon><Edit /></el-icon>）
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp as any)
}

// 全局状态管理
app.use(createPinia())

// 路由
app.use(router)

app.mount('#app')
