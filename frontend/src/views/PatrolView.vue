<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElCard, ElSelect, ElOption, ElTable, ElTableColumn, ElTag, ElTabs, ElTabPane,
  ElDialog, ElForm, ElFormItem, ElInput, ElDatePicker, ElMessage, ElMessageBox, ElDescriptions,
  ElDescriptionsItem, ElCheckbox, ElAlert
} from 'element-plus'
import {
  patrolApi, shipApi, roomApi,
  type Ship, type LoungeRoom, type PatrolWindow, type PatrolOfficer, type PatrolShipState,
  type PatrolHandoverRecord, type PatrolCheckEntry
} from '@/api'

const ships = ref<Ship[]>([])
const rooms = ref<LoungeRoom[]>([])
const windows = ref<PatrolWindow[]>([])
const officers = ref<PatrolOfficer[]>([])

const selectedShipId = ref<number | undefined>()
const selectedWindowId = ref<number | undefined>()
const state = ref<PatrolShipState | null>(null)
const records = ref<PatrolHandoverRecord[]>([])

const activeTab = ref('handover')

// 交班表单
const handoverDialogVisible = ref(false)
const leaderId = ref<number | undefined>()
const remark = ref('')
/** 走房勾选：roomId -> 是否勾选；顺序按应巡房间楼层从低到高自动生成 */
const walkedRoomIds = ref<number[]>([])
const handoverSubmitting = ref(false)

// 窗口维护
const windowDialogVisible = ref(false)
const windowForm = ref<Partial<PatrolWindow>>({})
const windowRange = ref<[string, string] | null>(null)

const leaders = computed(() => officers.value.filter(o => o.role === 'LEADER' && o.status === 'ACTIVE'))
const allWindowsForShip = computed(() => windows.value.filter(w => w.shipId === selectedShipId.value))
const canHandover = computed(() =>
  state.value?.windowState === 'OPEN' && state.value?.handoverState !== 'HANDED')

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const stateTagType = (s?: string): TagType => {
  switch (s) {
    case 'HANDED': return 'success'
    case 'BLOCKED': return 'danger'
    case 'LOCKED': return 'info'
    case 'NOT_HANDED': return 'warning'
    default: return 'info'
  }
}
const stateText = (s?: string) => ({
  HANDED: '已交',
  BLOCKED: '卡在退回（漏房/跳层/房间脱挂）',
  LOCKED: '窗口外已锁',
  NOT_HANDED: '未交'
} as Record<string, string>)[s || ''] || s || '-'

const windowTagType = (s?: string): TagType => ({
  OPEN: 'success', LOCKED: 'info', CLOSED: 'info', NOT_STARTED: 'warning'
} as Record<string, TagType>)[s || ''] || 'info'
const windowText = (s?: string) => ({
  OPEN: '窗口进行中', LOCKED: '窗口外已锁', CLOSED: '已人工关闭', NOT_STARTED: '窗口未开始'
} as Record<string, string>)[s || ''] || s

const loadBase = async () => {
  const [shipRes, roomRes, winRes, officerRes] = await Promise.all([
    shipApi.list(), roomApi.list(), patrolApi.listWindows(), patrolApi.listOfficers()
  ])
  ships.value = shipRes.data.data
  rooms.value = roomRes.data.data
  windows.value = winRes.data.data
  officers.value = officerRes.data.data
}

const loadState = async () => {
  if (!selectedWindowId.value || !selectedShipId.value) {
    state.value = null
    return
  }
  const res = await patrolApi.state(selectedWindowId.value, selectedShipId.value)
  state.value = res.data.data
}

const loadRecords = async () => {
  const res = await patrolApi.records(undefined, selectedShipId.value)
  records.value = res.data.data
}

const onShipChange = async () => {
  // 切船后只保留该船窗口，默认选第一个进行中的窗口
  const shipWindows = allWindowsForShip.value
  selectedWindowId.value = shipWindows.find(w => w.windowState === 'OPEN')?.id
    || shipWindows[0]?.id
  await refreshCurrent()
}

const refreshCurrent = async () => {
  await Promise.all([loadState(), loadRecords()])
}

const onWindowChange = () => refreshCurrent()

// ---------------- 交班弹窗 ----------------

const openHandoverDialog = () => {
  if (!state.value) return
  leaderId.value = leaders.value[0]?.id
  remark.value = ''
  // 默认按应巡顺序全选，值班长可取消；seq 始终由后端按楼层顺序最终裁定
  walkedRoomIds.value = state.value.expectedRooms.map(r => r.roomId)
  handoverDialogVisible.value = true
}

/** 弹窗内按楼层从低到高展示的应巡房间 */
const orderedExpectedRooms = computed(() => state.value?.expectedRooms ?? [])

const isWalked = (roomId: number) => walkedRoomIds.value.includes(roomId)
const toggleWalked = (roomId: number) => {
  if (isWalked(roomId)) {
    walkedRoomIds.value = walkedRoomIds.value.filter(id => id !== roomId)
  } else {
    // 追加到末尾，提交时仍按应巡楼层顺序重排生成 seq
    walkedRoomIds.value = [...walkedRoomIds.value, roomId]
  }
}
const seqOf = (roomId: number) => {
  // seq 以“按楼层从低到高的已选房间”生成，避免界面乱序导致误判
  const ordered = orderedExpectedRooms.value
    .filter(r => walkedRoomIds.value.includes(r.roomId))
    .map(r => r.roomId)
  return ordered.indexOf(roomId) + 1
}

const showBlocked = (result: { message?: string; blocked?: any }, title: string) => {
  const b = result.blocked
  const lines: string[] = []
  if (b) {
    lines.push(`卡住窗口：${b.windowCode}`)
    if (b.roomCode) lines.push(`卡住房间：${b.floor ? b.floor + '层 ' : ''}${b.roomCode}（${b.roomName || ''}）`)
    if (b.previousFloor) lines.push(`跳层来源：${b.previousFloor}层 ${b.previousRoomCode || ''}`)
    if (b.currentShipCode) lines.push(`房间当前停靠：${b.currentShipCode}（${b.currentShipName || ''}）`)
    const reasonText: Record<string, string> = {
      ROOM_DETACHED: '该房间已不再停靠本船',
      MISSING_ROOM: '该船挂靠房间未走完（漏房）',
      FLOOR_SKIP: '未按楼层从低到高（跳层）',
      BAD_ENTRY: '走房顺序乱序/重复'
    }
    if (b.reason) lines.push(`退回原因：${reasonText[b.reason] || b.reason}`)
  }
  ElMessageBox.alert(`${result.message || '整份交班已退回'}\n\n${lines.join('\n')}`, title, {
    type: 'error', confirmButtonText: '知道了', customClass: 'patrol-blocked-message'
  })
}

const submitHandover = async () => {
  if (!state.value || !leaderId.value) {
    ElMessage.warning('请选择交班值班长（本班在岗值班长）')
    return
  }
  if (walkedRoomIds.value.length === 0) {
    ElMessage.warning('一间房都没走，不能交班')
    return
  }
  // 按楼层从低到高生成 seq
  const entries: PatrolCheckEntry[] = orderedExpectedRooms.value
    .filter(r => walkedRoomIds.value.includes(r.roomId))
    .map((r, idx) => ({ roomId: r.roomId, seq: idx + 1 }))

  handoverSubmitting.value = true
  try {
    const res = await patrolApi.submitHandover({
      windowId: state.value.windowId,
      shipId: state.value.shipId,
      leaderId: leaderId.value,
      remark: remark.value,
      entries
    })
    ElMessage.success(res.data.data.message)
    handoverDialogVisible.value = false
    await refreshCurrent()
  } catch (error: any) {
    const status = error.response?.status
    const payload = error.response?.data
    if (status === 409 && payload?.data) {
      // 整份退回（漏房/跳层/房间脱挂），data 里带卡住房间与窗口
      showBlocked({ message: payload.message, blocked: payload.data.blocked }, '交班整单退回')
      handoverDialogVisible.value = false
      await refreshCurrent()
    } else if (status === 423) {
      ElMessageBox.alert(payload?.message || '接班窗口已锁', '窗口外已锁', { type: 'warning' })
      handoverDialogVisible.value = false
      await refreshCurrent()
    } else if (status === 403) {
      ElMessage.error(payload?.message || '只有本班值班长才能交班')
    } else {
      ElMessage.error(payload?.message || '交班失败')
    }
  } finally {
    handoverSubmitting.value = false
  }
}

// ---------------- 窗口维护 ----------------
const openWindowDialog = () => {
  windowForm.value = { shipId: selectedShipId.value }
  windowRange.value = null
  windowDialogVisible.value = true
}

const saveWindow = async () => {
  if (!windowForm.value.windowCode || !windowForm.value.shipId || !windowRange.value) {
    ElMessage.warning('窗口编号、船舶、起止时间必填')
    return
  }
  try {
    await patrolApi.createWindow({
      windowCode: windowForm.value.windowCode,
      windowName: windowForm.value.windowName,
      shipId: windowForm.value.shipId,
      startTime: windowRange.value[0],
      endTime: windowRange.value[1],
      remark: windowForm.value.remark
    })
    ElMessage.success('接班窗口已建立（起止时间不可修改）')
    windowDialogVisible.value = false
    const winRes = await patrolApi.listWindows()
    windows.value = winRes.data.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '窗口建立失败')
  }
}

const closeWindow = async (row: PatrolWindow) => {
  try {
    await ElMessageBox.confirm(`确认提前关闭窗口 ${row.windowCode}？关闭后该窗口不能再补勾或交班`, '关闭窗口', {
      type: 'warning'
    })
    await patrolApi.closeWindow(row.id!)
    ElMessage.success('窗口已关闭')
    const winRes = await patrolApi.listWindows()
    windows.value = winRes.data.data
    await refreshCurrent()
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '关闭失败')
  }
}

const statusTag = (status: string): TagType => (status === 'HANDED' ? 'success' : 'danger')

onMounted(async () => {
  await loadBase()
  if (ships.value.length > 0) {
    selectedShipId.value = ships.value[0].id
    await onShipChange()
  }
})
</script>

<template>
  <div>
    <ElCard style="margin-bottom: 20px;">
      <template #header><div style="font-weight: bold;">夜班巡检交班：选择船舶与接班窗口</div></template>
      <div style="display: flex; gap: 16px; align-items: center; flex-wrap: wrap;">
        <ElSelect v-model="selectedShipId" placeholder="选择船舶" style="width: 240px;" @change="onShipChange">
          <ElOption v-for="ship in ships" :key="ship.id" :label="`${ship.shipCode}（${ship.shipName}）`" :value="ship.id!" />
        </ElSelect>
        <ElSelect v-model="selectedWindowId" placeholder="选择接班窗口" style="width: 320px;" @change="onWindowChange">
          <ElOption
            v-for="w in allWindowsForShip" :key="w.id"
            :label="`${w.windowCode}（${w.startTime?.replace('T', ' ')} ~ ${w.endTime?.replace('T', ' ')}）`"
            :value="w.id!"
          >
            <span>{{ w.windowCode }}</span>
            <ElTag size="small" :type="windowTagType(w.windowState)" style="margin-left: 8px;">
              {{ windowText(w.windowState) }}
            </ElTag>
          </ElOption>
        </ElSelect>
        <ElButton type="primary" @click="openWindowDialog">建立接班窗口</ElButton>
      </div>
    </ElCard>

    <ElCard v-if="state" style="margin-bottom: 20px;">
      <template #header><div style="font-weight: bold;">该船当前交班状态（关掉页面重开仍一致）</div></template>
      <ElDescriptions :column="3" border>
        <ElDescriptionsItem label="船舶">{{ state.shipCode }}（{{ state.shipName }}）</ElDescriptionsItem>
        <ElDescriptionsItem label="接班窗口">{{ state.windowCode }}</ElDescriptionsItem>
        <ElDescriptionsItem label="窗口状态">
          <ElTag :type="windowTagType(state.windowState)">{{ windowText(state.windowState) }}</ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="交班状态">
          <ElTag :type="stateTagType(state.handoverState)">{{ stateText(state.handoverState) }}</ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="交班值班长">{{ state.leaderName || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="交班批次">{{ state.handoverBatch || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="应巡房间数">{{ state.expectedRooms.length }}</ElDescriptionsItem>
        <ElDescriptionsItem label="已巡/走房数">{{ state.roomCount ?? 0 }}</ElDescriptionsItem>
        <ElDescriptionsItem label="交班时间">{{ state.submitTime || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="走房顺序" :span="3">{{ state.walkOrder || '-' }}</ElDescriptionsItem>
      </ElDescriptions>

      <ElAlert
        v-if="state.blocked && state.handoverState !== 'HANDED'"
        :title="state.blocked.message || '上一次交班被整单退回'"
        type="error" :closable="false" show-icon style="margin-top: 12px;"
      >
        <div style="line-height: 1.7;">
          <div>卡住窗口：{{ state.blocked.windowCode }}</div>
          <div v-if="state.blocked.roomCode">
            卡住房间：{{ state.blocked.floor ? state.blocked.floor + '层 ' : '' }}{{ state.blocked.roomCode }}
            （{{ state.blocked.roomName }}）
          </div>
          <div v-if="state.blocked.previousFloor">
            跳层来源：{{ state.blocked.previousFloor }}层 {{ state.blocked.previousRoomCode }}
          </div>
          <div v-if="state.blocked.currentShipCode">
            房间当前停靠：{{ state.blocked.currentShipCode }}（{{ state.blocked.currentShipName }}）
          </div>
        </div>
      </ElAlert>

      <div style="margin-top: 14px;">
        <ElButton type="primary" :disabled="!canHandover" @click="openHandoverDialog">
          {{ state.windowState !== 'OPEN' ? '窗口未在进行中' : state.handoverState === 'HANDED' ? '本窗口已交班' : '值班长交班' }}
        </ElButton>
        <span v-if="state.windowState !== 'OPEN'" style="margin-left: 10px; color: #909399; font-size: 12px;">
          接班窗口一过（或未开始/已关闭）即锁，不能再补勾或改交班
        </span>
      </div>
    </ElCard>

    <ElCard>
      <ElTabs v-model="activeTab">
        <ElTabPane label="该船当时挂靠房间（应巡顺序）" name="rooms">
          <ElTable :data="state?.expectedRooms ?? []" border>
            <ElTableColumn label="走房顺序" width="90">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </ElTableColumn>
            <ElTableColumn prop="floor" label="楼层（低→高）" width="120" />
            <ElTableColumn prop="roomCode" label="房间编号" width="130" />
            <ElTableColumn prop="roomName" label="房间名称" min-width="140" />
            <ElTableColumn label="交班时是否已巡" width="130">
              <template #default="{ row }">
                <ElTag :type="(row as any).checked ? 'success' : 'info'">
                  {{ (row as any).checked ? '已巡' : '未巡' }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
          <div v-if="!state" style="color: #909399; padding: 12px 0;">请先选择船舶和接班窗口</div>
        </ElTabPane>

        <ElTabPane label="交班流水" name="records">
          <ElTable :data="records" border>
            <ElTableColumn prop="handoverBatch" label="交班批次" min-width="170" />
            <ElTableColumn prop="windowCode" label="接班窗口" width="130" />
            <ElTableColumn prop="shipCode" label="船舶" width="110" />
            <ElTableColumn prop="leaderName" label="交班值班长" width="110" />
            <ElTableColumn label="结果" width="100">
              <template #default="{ row }">
                <ElTag :type="statusTag((row as PatrolHandoverRecord).status)">
                  {{ (row as PatrolHandoverRecord).status === 'HANDED' ? '已交' : '整单退回' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="卡住房间" width="120">
              <template #default="{ row }">
                <template v-if="(row as PatrolHandoverRecord).blockedRoom?.roomCode">
                  {{ ((row as PatrolHandoverRecord).blockedRoom?.floor ? (row as PatrolHandoverRecord).blockedRoom?.floor + '层 ' : '') }}{{ (row as PatrolHandoverRecord).blockedRoom?.roomCode }}
                </template>
                <span v-else>-</span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="walkOrder" label="走房顺序（楼层低→高）" min-width="260" />
            <ElTableColumn prop="roomCount" label="走房数" width="80" />
            <ElTableColumn prop="submitTime" label="提交时间" min-width="160" />
          </ElTable>
        </ElTabPane>

        <ElTabPane label="接班窗口维护" name="windows">
          <ElTable :data="allWindowsForShip" border>
            <ElTableColumn prop="windowCode" label="窗口编号" width="140" />
            <ElTableColumn prop="windowName" label="窗口名称" min-width="180" />
            <ElTableColumn prop="startTime" label="开始时间" min-width="160" />
            <ElTableColumn prop="endTime" label="截止时间" min-width="160" />
            <ElTableColumn label="状态" width="120">
              <template #default="{ row }">
                <ElTag :type="windowTagType((row as PatrolWindow).windowState)">
                  {{ windowText((row as PatrolWindow).windowState) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="120">
              <template #default="{ row }">
                <ElButton
                  size="small" type="warning"
                  :disabled="['LOCKED', 'CLOSED'].includes((row as PatrolWindow).windowState || '')"
                  @click="closeWindow(row as PatrolWindow)"
                >提前关闭</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <!-- 交班弹窗：不是“随便勾一下”，勾选必须覆盖全部应巡房间且按楼层从低到高 -->
    <ElDialog title="值班长夜班巡检交班" v-model="handoverDialogVisible" width="720px">
      <ElForm label-width="120px">
        <ElFormItem label="交班值班长" required>
          <ElSelect v-model="leaderId" placeholder="只有本班在岗值班长">
            <ElOption
              v-for="o in leaders" :key="o.id"
              :label="`${o.officerName}（${o.officerCode}）`" :value="o.id!"
            />
          </ElSelect>
          <div style="color: #909399; font-size: 12px;">巡检员/已停用值班长不在此列，后端会再次校验</div>
        </ElFormItem>
        <ElFormItem label="接班窗口">
          <span>{{ state?.windowCode }}（{{ state?.startTime }} ~ {{ state?.endTime }}）</span>
        </ElFormItem>
        <ElFormItem label="走房（楼层低→高）" required>
          <div style="width: 100%;">
            <div
              v-for="(room, idx) in orderedExpectedRooms" :key="room.roomId"
              style="display: flex; align-items: center; padding: 6px 0; border-bottom: 1px dashed #eee; cursor: pointer;"
              @click="toggleWalked(room.roomId)"
            >
              <ElCheckbox :model-value="isWalked(room.roomId)" style="margin-right: 8px;" @click.stop />
              <span style="font-weight: bold; min-width: 64px;">
                第 {{ isWalked(room.roomId) ? seqOf(room.roomId) : '-' }} 间
              </span>
              <ElTag size="small" style="margin-right: 8px;">{{ room.floor }}层</ElTag>
              <span>{{ room.roomCode }}（{{ room.roomName }}）</span>
              <span style="margin-left: auto; color: #909399; font-size: 12px;">应巡序 {{ idx + 1 }}</span>
            </div>
          </div>
          <div style="color: #e6a23c; font-size: 12px; margin-top: 6px;">
            必须一层不漏、按楼层从低到高；漏一间、跳一层或含已改挂别船的房间，整份交班退回且不留勾。
          </div>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="handoverDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="handoverSubmitting" @click="submitHandover">确认交班</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="建立接班窗口" v-model="windowDialogVisible" width="560px">
      <ElForm :model="windowForm" label-width="100px">
        <ElFormItem label="窗口编号" required><ElInput v-model="windowForm.windowCode" /></ElFormItem>
        <ElFormItem label="窗口名称"><ElInput v-model="windowForm.windowName" /></ElFormItem>
        <ElFormItem label="船舶" required>
          <ElSelect v-model="windowForm.shipId">
            <ElOption v-for="ship in ships" :key="ship.id" :label="`${ship.shipCode}（${ship.shipName}）`" :value="ship.id!" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="接班时间段" required>
          <ElDatePicker
            v-model="windowRange" type="datetimerange" range-separator="至"
            start-placeholder="开始时间" end-placeholder="截止（过点即锁）"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="备注"><ElInput type="textarea" v-model="windowForm.remark" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="windowDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveWindow">建立</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style>
.patrol-blocked-message .el-message-box__message {
  white-space: pre-line;
  line-height: 1.7;
}
</style>
