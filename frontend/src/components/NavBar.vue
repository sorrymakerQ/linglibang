<script setup lang="ts">
/**
 * 顶部导航栏组件
 * 支持标题、返回按钮、右侧操作按钮/插槽
 */
import { useRouter } from 'vue-router'

withDefaults(defineProps<{
  title: string           // 标题文字
  showBack?: boolean      // 是否显示返回按钮
  backText?: string       // 返回按钮文字
  rightText?: string      // 右侧文字按钮
  fixed?: boolean         // 是否固定在顶部
}>(), {
  showBack: false,
  backText: '',
  rightText: '',
  fixed: true
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'rightClick'): void
}>()

const router = useRouter()

/** 返回上一页 */
function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
  emit('back')
}

/** 右侧按钮点击 */
function onRightClick(): void {
  emit('rightClick')
}
</script>

<template>
  <div class="navbar" :class="{ 'navbar-fixed': fixed }">
    <!-- 左侧返回按钮 -->
    <div class="navbar-left">
      <el-button
        v-if="showBack"
        link
        type="primary"
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>
        <span v-if="backText" style="margin-left: 2px;">{{ backText }}</span>
      </el-button>
    </div>

    <!-- 中间标题 -->
    <div class="navbar-title">{{ title }}</div>

    <!-- 右侧按钮 / 插槽 -->
    <div class="navbar-right">
      <el-button
        v-if="rightText"
        link
        type="primary"
        @click="onRightClick"
      >
        {{ rightText }}
      </el-button>
      <slot name="right"></slot>
    </div>
  </div>
</template>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 46px;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  z-index: 99;
}

.navbar-fixed {
  position: sticky;
  top: 0;
  left: 0;
  right: 0;
}

.navbar-left,
.navbar-right {
  display: flex;
  align-items: center;
  min-width: 80px;
}

.navbar-right {
  justify-content: flex-end;
}

.navbar-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
