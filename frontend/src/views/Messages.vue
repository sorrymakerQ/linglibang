<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getNotifications, markNotificationRead, markAllRead } from '@/api/order'
import { getConversations, getChatMessages, getHelpChatMessages, getHelpChatInfo, sendChatMessage, sendHelpMessage, type ChatMsg } from '@/api/chat'
import { useUserStore } from '@/stores/user'
import { Client } from '@stomp/stompjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const currentUserId = userStore.userInfo?.id || 0

const tab = ref<'notice' | 'chat'>('notice')
const notices = ref<any[]>([])
const chats = ref<any[]>([])
const sel = ref<any>(null)
const chatMsgs = ref<any[]>([])
const chatLoading = ref(false)

// ===== 通知 =====
const typeMap: Record<number, string> = { 1: '系统', 2: '订单', 3: '评价' }
const unreadNotice = computed(() => notices.value.filter(m => m.isRead === 0).length)
const unreadChat = computed(() => chats.value.filter(c => c.senderId !== currentUserId && c.isRead === 0).length)

async function loadNotices() {
  try {
    const r = await getNotifications({ page: 1, size: 50 })
    notices.value = r.data?.list || []
  } catch {
    notices.value = []
  }
}

async function readNotice(m: any) {
  sel.value = m
  if (m.isRead === 1) return
  try {
    await markNotificationRead(m.id)
    m.isRead = 1
  } catch {}
}

async function readAllNotices() {
  try {
    await markAllRead()
    notices.value.forEach(m => m.isRead = 1)
    ElMessage.success('已全部标为已读')
  } catch {}
}

// ===== 私信 =====
async function loadChats() {
  try {
    const r = await getConversations()
    chats.value = r.data || []
  } catch {
    chats.value = []
  }
}

async function openChat(c: any) {
  sel.value = c
  chatLoading.value = true
  try {
    if (c.orderId) {
      const r = await getChatMessages(c.orderId)
      chatMsgs.value = r.data || []
    } else if (c.helpId) {
      const r = await getHelpChatMessages(c.helpId)
      chatMsgs.value = r.data || []
    }
  } catch {
    chatMsgs.value = []
  } finally {
    chatLoading.value = false
  }
}

function onTabChange() {
  sel.value = null
  if (tab.value === 'chat') {
    loadChats()
    connectWS()
  } else {
    disconnectWS()
  }
}

const chatInput = ref('')
let stompClient: Client | null = null

// ===== WebSocket 实时推送 =====
function connectWS() {
  if (stompClient) return
  const token = localStorage.getItem('token') || ''
  stompClient = new Client({
    webSocketFactory: () => {
      const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      return new WebSocket(`${proto}//${window.location.host}/api/ws`)
    },
    connectHeaders: { satoken: token },
    onConnect: () => {
      stompClient!.subscribe('/user/queue/chat', (msg) => {
        const body: ChatMsg = JSON.parse(msg.body)
        loadChats()
        if (sel.value && tab.value === 'chat') {
          const match = (sel.value.orderId && body.orderId === sel.value.orderId) ||
                        (sel.value.helpId && body.helpId === sel.value.helpId)
          if (match && !chatMsgs.value.find(m => m.id === body.id)) {
            chatMsgs.value.push(body)
            nextTick(() => {
              const el = document.querySelector('.chat-msg-list')
              if (el) el.scrollTop = el.scrollHeight
            })
          }
        }
      })
    },
    onDisconnect: () => {}
  })
  stompClient.activate()
}

function disconnectWS() {
  stompClient?.deactivate()
  stompClient = null
}

async function sendChat() {
  const text = chatInput.value.trim()
  if (!text || !sel.value) return
  chatInput.value = ''
  try {
    let res: any
    if (sel.value.orderId) {
      res = await sendChatMessage(sel.value.orderId, text)
    } else if (sel.value.helpId) {
      res = await sendHelpMessage(sel.value.helpId, text)
    }
    if (res?.data) {
      chatMsgs.value.push(res.data)
      await new Promise(r => setTimeout(r, 50))
      const el = document.querySelector('.chat-msg-list')
      if (el) el.scrollTop = el.scrollHeight
    }
  } catch {}
}

function goHelp(id: number) {
  router.push('/help/' + id)
}

onMounted(async () => {
  await loadNotices()
  await loadChats()
  connectWS()
  // 从 URL 参数自动打开聊天：?chat=help:123
  const chatParam = route.query.chat as string
  if (chatParam && chatParam.startsWith('help:')) {
    const helpId = parseInt(chatParam.split(':')[1])
    if (helpId) {
      tab.value = 'chat'
      await new Promise(r => setTimeout(r, 300))
      let conv = chats.value.find((c: any) => c.helpId === helpId)
      if (!conv) {
        let info: any = {}
        try { const r = await getHelpChatInfo(helpId); info = r.data || {} } catch {}
        try {
          const r = await getHelpChatMessages(helpId)
          chatMsgs.value = r.data || []
        } catch {
          chatMsgs.value = []
        }
        conv = {
          helpId, orderId: null,
          senderId: currentUserId, receiverId: 0,
          helpTitle: info.helpTitle || '', otherName: info.otherName || '对方',
          content: '',
          createTime: new Date().toISOString()
        }
        chats.value.unshift(conv)
      }
      openChat(conv)
      router.replace({ query: {} })
    }
  }
})

onUnmounted(() => disconnectWS())
</script>

<template>
  <div class="msgs">
    <div class="msgs-layout">
      <!-- ========== 左侧列表 ========== -->
      <div class="list-panel">
        <!-- Tab 切换（用 el-tabs，未读数用 el-badge 展示） -->
        <el-tabs v-model="tab" class="list-tabs" @tab-change="onTabChange">
          <el-tab-pane name="notice">
            <template #label>
              <el-badge :value="unreadNotice" :hidden="unreadNotice === 0" :max="99">
                <span>通知</span>
              </el-badge>
            </template>
          </el-tab-pane>
          <el-tab-pane name="chat">
            <template #label>
              <el-badge :value="unreadChat" :hidden="unreadChat === 0" :max="99">
                <span>私信</span>
              </el-badge>
            </template>
          </el-tab-pane>
        </el-tabs>

        <!-- 通知列表 -->
        <div class="list-body" v-if="tab === 'notice'">
          <div class="sub-head">
            <span class="sub-tit">共 {{ notices.length }} 条</span>
            <el-button link type="primary" size="small" @click="readAllNotices">
              全部已读
            </el-button>
          </div>

          <div v-if="notices.length">
            <div
              v-for="m in notices"
              :key="m.id"
              class="msg-row"
              :class="{ un: m.isRead === 0, ac: sel?.id === m.id && tab === 'notice' }"
              @click="readNotice(m)"
            >
              <div class="mr-top">
                <span class="mr-dot" :class="{ read: m.isRead === 1 }"></span>
                <span class="mr-title" :class="{ dim: m.isRead === 1 }">{{ m.title }}</span>
              </div>
              <p class="mr-preview">{{ m.content?.slice(0, 35) }}{{ m.content?.length > 35 ? '...' : '' }}</p>
              <div class="mr-foot">
                <el-tag size="small" effect="plain">{{ typeMap[m.type] }}</el-tag>
                <span class="mr-time">{{ m.createTime?.slice(0, 16) }}</span>
              </div>
            </div>
          </div>
          <el-empty
            v-else
            description="暂无通知"
            :image-size="80"
          />
        </div>

        <!-- 私信列表 -->
        <div class="list-body" v-if="tab === 'chat'">
          <div v-if="chats.length">
            <div
              v-for="c in chats"
              :key="c.id"
              class="msg-row"
              :class="{ un: c.senderId !== currentUserId && c.isRead === 0, ac: sel?.id === c.id && tab === 'chat' }"
              @click="openChat(c)"
            >
              <div class="mr-top">
                <span
                  class="mr-dot"
                  v-if="c.senderId !== currentUserId && c.isRead === 0"
                ></span>
                <span class="mr-title">{{ c.otherName || '未知用户' }}</span>
              </div>
              <p class="mr-preview">{{ c.content?.slice(0, 35) }}{{ c.content?.length > 35 ? '...' : '' }}</p>
              <div class="mr-foot">
                <el-tag v-if="c.helpTitle" size="small" type="success" effect="plain">
                  {{ c.helpTitle?.slice(0, 15) }}
                </el-tag>
                <span v-else></span>
                <span class="mr-time">{{ c.createTime?.slice(0, 16) }}</span>
              </div>
            </div>
          </div>
          <el-empty
            v-else
            description="暂无私信"
            :image-size="80"
          />
        </div>
      </div>

      <!-- ========== 右侧详情 ========== -->
      <div class="detail-panel">
        <!-- 通知详情 -->
        <template v-if="sel && tab === 'notice'">
          <h2 class="dt-title">{{ sel.title }}</h2>
          <div class="dt-meta">
            <el-tag size="small">{{ typeMap[sel.type] }}通知</el-tag>
            <span class="dt-time">{{ sel.createTime }}</span>
          </div>
          <el-divider />
          <div class="dt-body">{{ sel.content }}</div>
        </template>

        <!-- 私信详情 -->
        <template v-else-if="sel && tab === 'chat'">
          <div class="chat-detail-head">
            <h2 class="dt-title">{{ sel.otherName || '聊天' }}</h2>
            <el-tag
              v-if="sel.helpTitle"
              type="success"
              class="clickable"
              @click="goHelp(sel.helpId)"
            >
              {{ sel.helpTitle }}
            </el-tag>
          </div>
          <el-divider/>

          <div class="chat-msg-list" v-loading="chatLoading" element-loading-text="加载中...">
            <div
              v-for="m in chatMsgs"
              :key="m.id"
              class="cmi"
              :class="m.senderId === currentUserId ? 'me' : 'other'"
            >
              <div class="cmi-bubble">{{ m.content }}</div>
              <div class="cmi-time">{{ m.createTime?.slice(11, 16) }}</div>
            </div>
            <div v-if="!chatLoading && chatMsgs.length === 0" class="empty-inline">
              暂无消息，开始聊天吧
            </div>
          </div>

          <!-- 输入区 -->
          <div class="chat-input-row">
            <el-input
              v-model="chatInput"
              placeholder="输入消息..."
              @keyup.enter="sendChat"
              clearable
            />
            <el-button
              type="primary"
              :disabled="!chatInput.trim()"
              @click="sendChat"
            >
              <el-icon><Promotion /></el-icon>
              <span style="margin-left: 4px;">发送</span>
            </el-button>
          </div>
        </template>

        <!-- 未选中态 -->
        <el-empty
          v-else
          description="点击左侧查看详情"
          :image-size="120"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.msgs {
  padding: 20px 24px;
  height: 100%;
}
.msgs-layout {
  display: flex;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

/* ==================== 左侧列表 ==================== */
.list-panel {
  width: 340px;
  border-right: 1px solid #e8eaed;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* Tab 样式定制：绿色主色 */
.list-tabs {
  padding: 0 16px;
  flex-shrink: 0;
  border-bottom: 1px solid #f1f2f3;
}
.list-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.list-tabs :deep(.el-tabs__item.is-active) {
  color: #00B96B;
  font-weight: 600;
}
.list-tabs :deep(.el-tabs__active-bar) {
  background: #00B96B;
}
.list-tabs :deep(.el-tabs__item:hover) {
  color: #00B96B;
}
/* 徽标数字紧贴文字 */
.list-tabs :deep(.el-badge__content) {
  transform: translate(6px, -6px);
  height: 16px;
  padding: 0 5px;
  line-height: 16px;
  font-size: 10px;
}

.sub-head {
  padding: 10px 16px;
  border-bottom: 1px solid #f1f2f3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.sub-tit { font-size: 12px; color: #9499a0; }

.list-body {
  flex: 1;
  overflow-y: auto;
}

/* 单条消息行 */
.msg-row {
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.1s;
}
.msg-row:hover { background: #f6f7f8; }
.msg-row.ac { background: #f0fdf6; }
.msg-row.un { background: #fbfffb; }

.mr-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.mr-dot {
  width: 6px;
  height: 6px;
  background: #00B96B;
  border-radius: 50%;
  flex-shrink: 0;
}
.mr-dot.read { background: #d9d9d9; }
.mr-title {
  font-size: 13px;
  font-weight: 600;
  color: #18191c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mr-title.dim { font-weight: 400; color: #9499a0; }
.mr-preview {
  font-size: 12px;
  color: #9499a0;
  margin: 0 0 6px 12px;
}
.mr-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-left: 12px;
}
.mr-time { font-size: 11px; color: #c0c4cc; }

/* ==================== 右侧详情 ==================== */
.detail-panel {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.dt-title {
  font-size: 18px;
  font-weight: 600;
  color: #18191c;
  margin-bottom: 10px;
}

.dt-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dt-time { font-size: 12px; color: #c0c4cc; }

.dt-body {
  font-size: 14px;
  color: #61666d;
  line-height: 1.8;
  white-space: pre-wrap;
}

/* 私信头部 */
.chat-detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.clickable { cursor: pointer; }

/* 私信消息列表 */
.chat-msg-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}
.cmi {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}
.cmi.me {
  align-self: flex-end;
  align-items: flex-end;
}
.cmi.other {
  align-self: flex-start;
  align-items: flex-start;
}
.cmi-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}
.me .cmi-bubble {
  background: #00B96B;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.other .cmi-bubble {
  background: #f0f0f0;
  color: #333;
  border-bottom-left-radius: 4px;
}
.cmi-time { font-size: 11px; color: #bbb; margin-top: 4px; }

.empty-inline {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 40px 0;
}

/* 输入框 */
.chat-input-row {
  display: flex;
  gap: 8px;
  padding: 12px 0 0;
  border-top: 1px solid #f0f0f0;
  margin-top: auto;
  flex-shrink: 0;
}
</style>
