<script setup lang="ts">
import { ref, nextTick, onUnmounted, watch } from 'vue'
import { Client } from '@stomp/stompjs'
import { getChatMessages, getHelpChatMessages, sendChatMessage, sendHelpMessage, type ChatMsg } from '@/api/chat'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ orderId?: number; helpId?: number; show: boolean }>()
const emit = defineEmits<{ close: [] }>()
const userStore = useUserStore()

const msgs = ref<ChatMsg[]>([])
const inputText = ref('')
const loading = ref(false)
const msgContainer = ref<HTMLElement>()
let stompClient: Client | null = null

const currentUserId = userStore.userInfo?.id || 0

watch(() => [props.show, props.orderId, props.helpId], async ([val]) => {
  if (val) {
    loading.value = true
    try {
      if (props.orderId) {
        const res = await getChatMessages(props.orderId)
        msgs.value = res.data || []
      } else if (props.helpId) {
        const res = await getHelpChatMessages(props.helpId)
        msgs.value = res.data || []
      }
    } catch {
      msgs.value = []
    } finally {
      loading.value = false
    }
    await nextTick()
    scrollBottom()
    connectWS()
  } else {
    disconnectWS()
  }
}, { immediate: true })

onUnmounted(() => disconnectWS())

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
      stompClient!.subscribe(`/user/queue/chat`, (msg) => {
        const body: ChatMsg = JSON.parse(msg.body)
        const isMyMsg = body.orderId === props.orderId || body.helpId === props.helpId
        if (isMyMsg && !msgs.value.find(m => m.id === body.id)) {
          msgs.value.push(body)
          nextTick(() => scrollBottom())
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

function scrollBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}

async function send() {
  const text = inputText.value.trim()
  if (!text) return
  inputText.value = ''
  try {
    let res: any
    if (props.orderId) {
      res = await sendChatMessage(props.orderId, text)
    } else if (props.helpId) {
      res = await sendHelpMessage(props.helpId, text)
    }
    if (res?.data) {
      msgs.value.push(res.data)
      await nextTick()
      scrollBottom()
    }
  } catch {
    ElMessage.error('消息发送失败，请重试')
    inputText.value = text
  }
}

function fmtTime(t?: string) {
  if (!t) return ''
  return t.slice(11, 16) // HH:mm
}
</script>

<template>
  <!-- 用 el-dialog 承载聊天弹窗：自带遮罩、关闭按钮、居中、esc 关闭 -->
  <el-dialog
    :model-value="show"
    width="440px"
    :show-close="true"
    :close-on-click-modal="true"
    :append-to-body="true"
    class="chat-dialog"
    @close="emit('close')"
  >
    <template #header>
      <div class="chat-title">
        <el-icon><ChatDotRound /></el-icon>
        <span>聊天</span>
      </div>
    </template>

    <!-- 消息主体 -->
    <div class="chat-body" ref="msgContainer" v-loading="loading" element-loading-text="加载中...">
      <div
        v-for="m in msgs"
        :key="m.id"
        class="chat-msg"
        :class="m.senderId === currentUserId ? 'me' : 'other'"
      >
        <div class="msg-bubble">{{ m.content }}</div>
        <div class="msg-time">{{ fmtTime(m.createTime) }}</div>
      </div>

      <div v-if="!loading && msgs.length === 0" class="chat-empty">
        暂无消息，开始聊天吧
      </div>
    </div>

    <!-- 输入区（放到 footer 插槽） -->
    <template #footer>
      <div class="chat-foot">
        <el-input
          v-model="inputText"
          placeholder="输入消息..."
          @keyup.enter="send"
          clearable
        />
        <el-button
          type="primary"
          :disabled="!inputText.trim()"
          @click="send"
        >
          <el-icon><Promotion /></el-icon>
          <span style="margin-left: 4px;">发送</span>
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 定制 dialog 内部布局 */
.chat-dialog :deep(.el-dialog__body) {
  padding: 0;
}
.chat-dialog :deep(.el-dialog__footer) {
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
}

/* 标题 */
.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* 消息区 */
.chat-body {
  height: 400px;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 消息气泡：me 靠右绿色，other 靠左灰色 */
.chat-msg {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}
.chat-msg.me {
  align-self: flex-end;
  align-items: flex-end;
}
.chat-msg.other {
  align-self: flex-start;
  align-items: flex-start;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}
.me .msg-bubble {
  background: #00B96B;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.other .msg-bubble {
  background: #f0f0f0;
  color: #333;
  border-bottom-left-radius: 4px;
}
.msg-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
}

.chat-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 60px 0;
}

/* 输入区 */
.chat-foot {
  display: flex;
  gap: 8px;
}
</style>
