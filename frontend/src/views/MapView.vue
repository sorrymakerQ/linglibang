<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { getHelpList, getCategoryList } from '@/api/help'
import type { HelpRequest, Category } from '@/types'

/** HTML 转义，防止 InfoWindow 拼接用户输入导致存储型 XSS */
function escapeHtml(s: unknown) {
  return String(s ?? '').replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string
  ))
}

const router = useRouter()

// ==================== 状态 ====================
const helpList = ref<HelpRequest[]>([])
const loading = ref(true)
const mapLoading = ref(false)
const mapReady = ref(false)
const selectedId = ref<number | null>(null)
const currentPos = ref({ lng: 116.397128, lat: 39.916527 })
const searchRadius = ref(5)
const categories = ref<Category[]>([])
const activeCategoryId = ref(0)

let map: any = null
let cluster: any = null
let userMarker: any = null
let moveTimer: ReturnType<typeof setTimeout> | null = null

// ==================== 半径选项 ====================
const radiusOptions = [
  { label: '1km', value: 1 },
  { label: '3km', value: 3 },
  { label: '5km', value: 5 },
  { label: '10km', value: 10 },
  { label: '全城', value: 50 },
]

// ==================== 生命周期 ====================
onMounted(async () => {
  await getLocation()
  await fetchCategories()
  await loadNearby()
  await waitForAMap()
  await nextTick()
  initMap()
})

onUnmounted(() => {
  if (map) map.destroy()
  if (moveTimer) clearTimeout(moveTimer)
})

// ==================== AMap SDK 加载等待 ====================
async function waitForAMap(): Promise<void> {
  if ((window as any).AMap) { mapReady.value = true; return }

  if (document.querySelector('script[src*="webapi.amap.com"]')) {
    let waited = 0
    while (!(window as any).AMap && waited < 30000) {
      await new Promise(r => setTimeout(r, 200))
      waited += 200
    }
    mapReady.value = !!(window as any).AMap
    if (!mapReady.value) console.warn('高德地图加载超时')
    return
  }

  return new Promise<void>((resolve) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${import.meta.env.VITE_AMAP_KEY}&plugin=AMap.MarkerClusterer,AMap.Scale`
    script.onload = () => { mapReady.value = true; resolve() }
    script.onerror = () => { console.warn('高德地图加载失败，请检查网络'); resolve() }
    document.head.appendChild(script)
  })
}

// ==================== 定位 ====================
async function getLocation(): Promise<void> {
  return new Promise<void>((resolve) => {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lng = pos.coords.longitude
          const lat = pos.coords.latitude
          currentPos.value = { lng, lat }
          if (map) {
            map.setCenter([lng, lat])
            map.setZoom(15)
            updateUserMarker()
          }
          resolve()
        },
        () => resolve(),
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
      )
    } else {
      resolve()
    }
  })
}

// ==================== 分类 ====================
async function fetchCategories() {
  try {
    const res = await getCategoryList()
    if (res.data) categories.value = res.data
  } catch { /* 拦截器已提示 */ }
}

// ==================== 数据加载 ====================
async function loadNearby() {
  mapLoading.value = true
  try {
    const res = await getHelpList({
      page: 1, size: 50,
      lng: currentPos.value.lng,
      lat: currentPos.value.lat,
      radius: searchRadius.value,
      categoryId: activeCategoryId.value || undefined
    })
    if (res.data) {
      helpList.value = res.data.list || []
    }
  } catch {
    helpList.value = []
  } finally {
    loading.value = false
    mapLoading.value = false
  }
}

// ==================== 地图初始化 ====================
function initMap() {
  const AMap = (window as any).AMap
  if (!AMap) {
    console.warn('高德地图未加载，请检查 index.html 中的 Key')
    return
  }

  const container = document.getElementById('map-container')
  if (!container || container.clientHeight === 0) {
    setTimeout(() => initMap(), 200)
    return
  }

  map = new AMap.Map('map-container', {
    zoom: 14,
    center: [currentPos.value.lng, currentPos.value.lat],
    resizeEnable: true,
  })

  updateUserMarker()

  if (AMap.MarkerClusterer) {
    cluster = new AMap.MarkerClusterer(map, [], {
      gridSize: 50,
      maxZoom: 16,
      styles: [{
        url: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png',
        size: new AMap.Size(32, 32),
        textColor: '#fff',
        textSize: 12,
      }]
    })
  }

  map.addControl(new AMap.Scale())
  map.on('moveend', onMapMove)
  map.on('zoomend', onMapMove)
  renderMarkers()
  setTimeout(() => map.resize(), 500)
}

function onMapMove() {
  if (moveTimer) clearTimeout(moveTimer)
  moveTimer = setTimeout(() => {
    const center = map.getCenter()
    if (center) {
      currentPos.value = { lng: center.lng, lat: center.lat }
    }
    loadNearby()
  }, 500)
}

// ==================== 用户标记 ====================
function updateUserMarker() {
  const AMap = (window as any).AMap
  if (!AMap || !map) return

  if (userMarker) {
    userMarker.setMap(null)
    userMarker = null
  }
  userMarker = new AMap.Marker({
    position: [currentPos.value.lng, currentPos.value.lat],
    map: map,
    icon: new AMap.Icon({
      size: new AMap.Size(28, 28),
      image: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_b.png',
      imageSize: new AMap.Size(28, 28)
    }),
    title: '我的位置',
    zIndex: 100,
  })
}

// ==================== 回到我的位置 ====================
async function locateMe() {
  const AMap = (window as any).AMap
  if (!AMap || !map) return

  try {
    const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true, timeout: 15000, maximumAge: 0
      })
    })
    const lng = pos.coords.longitude
    const lat = pos.coords.latitude
    currentPos.value = { lng, lat }
    map.setCenter([lng, lat])
    map.setZoom(15)
    updateUserMarker()
    loadNearby()
    ElMessage.success('已回到当前位置')
  } catch {
    ElMessage.error('无法获取位置，请检查浏览器定位权限')
  }
}

// ==================== 标记渲染 ====================
function renderMarkers() {
  const AMap = (window as any).AMap
  if (!AMap || !map) return

  map.clearMap()
  updateUserMarker()

  if (helpList.value.length === 0) return

  const iconMap: Record<number, string> = {
    1: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png',
    2: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_y.png',
    3: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_g.png',
  }

  const markers: any[] = []
  helpList.value.forEach((h) => {
    if (!h.lng || !h.lat) return

    const marker = new AMap.Marker({
      position: [h.lng, h.lat],
      icon: new AMap.Icon({
        size: new AMap.Size(22, 22),
        image: iconMap[h.status] || iconMap[1],
        imageSize: new AMap.Size(22, 22)
      }),
      title: h.title,
      offset: new AMap.Pixel(-11, -11)
    })

    marker.on('click', () => {
      const info = new AMap.InfoWindow({
        content: `
          <div style="padding:10px;max-width:220px">
            <b style="font-size:14px">${escapeHtml(h.title)}</b><br/>
            <span style="color:#999;font-size:12px">📍 ${escapeHtml(h.address)}</span><br/>
            <span style="font-size:12px">📏 ${fmtDist(h.distance)}</span>
            ${h.reward > 0 ? `&nbsp;<span style="color:#e65100;font-size:12px">💰 ¥${escapeHtml(h.reward)}</span>` : ''}
            <br/>
            <a href="javascript:void(0)" id="info-go-${h.id}" style="color:#00B96B;font-size:12px;text-decoration:none">查看详情 →</a>
          </div>`,
        offset: new AMap.Pixel(0, -28)
      })
      info.open(map, marker.getPosition())

      setTimeout(() => {
        const el = document.getElementById('info-go-' + h.id)
        if (el) {
          el.onclick = () => goDetail(h.id)
        }
      }, 50)

      selectHelp(h)
    })

    markers.push(marker)
  })

  if (cluster) {
    cluster.setMap(null)
    cluster = new AMap.MarkerClusterer(map, markers, {
      gridSize: 50,
      maxZoom: 16,
      styles: [{
        url: 'https://webapi.amap.com/theme/v1.3/markers/n/mark_r.png',
        size: new AMap.Size(32, 32),
        textColor: '#fff',
        textSize: 12,
      }]
    })
  } else {
    markers.forEach(m => m.setMap(map))
  }
}

// ==================== 列表联动 ====================
function selectHelp(h: HelpRequest) {
  selectedId.value = h.id
  const el = document.getElementById('help-' + h.id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function goDetail(id: number) {
  router.push('/help/' + id)
}

function fmtDist(m: number): string {
  if (!m) return '-'
  return m < 1000 ? Math.round(m) + 'm' : (m / 1000).toFixed(1) + 'km'
}

// ==================== 半径 & 分类 ====================
function switchRadius(r: number) {
  searchRadius.value = r
  loadNearby()
}

function switchCat(id: number) {
  activeCategoryId.value = id
  loadNearby()
}

// 监听 helpList 变化 → 重新渲染标记
watch(helpList, () => {
  renderMarkers()
})
</script>

<template>
  <div class="map-page">
    <div class="map-container">
      <!-- ========== 左侧面板 ========== -->
      <div class="map-left">
        <div class="panel-header">
          <h2 class="section-title">
            <el-icon><Location /></el-icon>
            附近求助
          </h2>
        </div>

        <!-- 分类筛选 -->
        <div class="filter-block">
          <div class="cat-row">
            <el-check-tag
              :checked="activeCategoryId === 0"
              @change="switchCat(0)"
            >
              全部
            </el-check-tag>
            <el-check-tag
              v-for="c in categories"
              :key="c.id"
              :checked="activeCategoryId === c.id"
              @change="switchCat(c.id)"
            >
              {{ c.icon }} {{ c.name }}
            </el-check-tag>
          </div>
        </div>

        <!-- 半径选择 -->
        <div class="filter-block">
          <span class="filter-label">搜索范围</span>
          <el-radio-group v-model="searchRadius" size="small" @change="loadNearby">
            <el-radio-button
              v-for="opt in radiusOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 结果统计 -->
        <div class="stat-bar">
          找到 <b>{{ helpList.length }}</b> 条求助
        </div>

        <!-- 列表 -->
        <div class="help-list" v-loading="loading" element-loading-text="加载中...">
          <template v-if="helpList.length > 0">
            <div
              v-for="h in helpList"
              :key="h.id"
              :id="'help-' + h.id"
              class="help-row"
              :class="{ selected: selectedId === h.id }"
              @click="goDetail(h.id)"
            >
              <div class="row-left">
                <el-tag size="small" type="success" effect="plain" class="row-cat">
                  {{ h.categoryName }}
                </el-tag>
                <span class="row-title" :title="h.title">{{ h.title }}</span>
                <span class="row-addr">📍 {{ h.address }}</span>
              </div>
              <div class="row-right">
                <span class="row-distance">{{ fmtDist(h.distance) }}</span>
                <span v-if="h.reward > 0" class="row-reward">¥{{ h.reward }}</span>
              </div>
            </div>
          </template>

          <el-empty
            v-else-if="!loading"
            description="附近暂无求助，试试扩大搜索范围"
            :image-size="80"
          />
        </div>
      </div>

      <!-- ========== 右侧地图 ========== -->
      <div class="map-right">
        <!-- 地图 SDK 加载中 -->
        <div v-if="!mapReady" class="map-status">
          <el-icon size="20" style="margin-right: 8px;"><Loading /></el-icon>
          地图加载中...
        </div>

        <div id="map-container" style="width: 100%; height: 100%;"></div>

        <!-- 搜索加载遮罩 -->
        <div class="map-loading" v-if="mapLoading && mapReady">
          <el-icon class="loading-spin"><Loading /></el-icon>
          <span>搜索中...</span>
        </div>

        <!-- 定位按钮：右下角悬浮 -->
        <el-tooltip content="回到我的位置" placement="left">
          <el-button
            type="primary"
            circle
            size="large"
            class="locate-btn"
            @click="locateMe"
          >
            <el-icon><Aim /></el-icon>
          </el-button>
        </el-tooltip>

        <!-- 底部拖动提示 -->
        <div class="map-hint" v-if="!mapLoading && mapReady">
          <el-icon><InfoFilled /></el-icon>
          拖动地图自动搜索附近
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.map-page { padding: 16px 0; }

.map-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
}

/* ==================== 左侧面板 ==================== */
.map-left {
  width: 380px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 16px 20px 8px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

/* 筛选区块 */
.filter-block {
  padding: 12px 20px 0;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

/* 分类标签 */
.cat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cat-row :deep(.el-check-tag) {
  cursor: pointer;
  padding: 3px 10px;
  font-size: 12px;
}

/* 统计栏 */
.stat-bar {
  padding: 12px 20px;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}
.stat-bar b { color: #606266; }

/* 列表 */
.help-list {
  flex: 1;
  overflow-y: auto;
  position: relative;
  min-height: 200px;
}

.help-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
}
.help-row:hover { background: #f6f7f8; }
.help-row.selected {
  background: #f0fdf6;
  border-left: 3px solid #00B96B;
}

.row-left { flex: 1; min-width: 0; }
.row-cat {
  margin-bottom: 4px;
}
.row-title {
  display: block;
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 4px;
}
.row-addr {
  display: block;
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.row-right { text-align: right; flex-shrink: 0; }
.row-distance {
  display: block;
  font-size: 12px;
  color: #00B96B;
  font-weight: 600;
}
.row-reward {
  font-size: 12px;
  color: #e65100;
}

/* ==================== 右侧地图 ==================== */
.map-right {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  position: relative;
}

#map-container { min-height: 400px; }

/* 地图 SDK 加载中占位 */
.map-status {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #909399;
  font-size: 15px;
}

/* 搜索加载遮罩 */
.map-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
}

.loading-spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 定位按钮：官方 el-button 圆形 */
.locate-btn {
  position: absolute !important;
  bottom: 20px;
  right: 20px;
  z-index: 10;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 底部拖动提示 */
.map-hint {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  pointer-events: none;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>

<!-- 非 scoped：修复全局 box-sizing: border-box 破坏高德瓦片 -->
<style>
.amap-container,
.amap-container * {
  box-sizing: content-box !important;
}
</style>
