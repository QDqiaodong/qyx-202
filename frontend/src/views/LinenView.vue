<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElCard, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElTag, ElTable, ElTableColumn, ElMessage, ElMessageBox, ElEmpty, ElDescriptions, ElDescriptionsItem
} from 'element-plus'
import {
  linenApi,
  type LinenRoomState,
  type LinenRecovery,
  type LinenBlocked
} from '@/api'

const rooms = ref<LinenRoomState[]>([])
const records = ref<LinenRecovery[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const editingId = ref<number | undefined>(undefined)
const formRoom = ref<LinenRoomState | null>(null)
const form = ref<{ setCount: number | null; bagWeight: number | null; witnessName: string; operator: string; remark: string }>({
  setCount: null,
  bagWeight: null,
  witnessName: '',
  operator: '',
  remark: ''
})

const kgText = (room: LinenRoomState) =>
  `${Number(room.kgPerSetMin ?? 1.5).toFixed(2)}~${Number(room.kgPerSetMax ?? 2.5).toFixed(2)}kg/套`

const stateTag = (room: LinenRoomState): { type: 'primary' | 'success' | 'warning' | 'info' | 'danger'; text: string } => {
  switch (room.linenState) {
    case 'RECOVERED':
      return { type: 'success', text: '可住' }
    case 'DRAFT':
      return { type: 'warning', text: '回收未齐·草稿' }
    case 'PENDING':
      return { type: 'danger', text: '回收未齐' }
    default:
      return { type: 'info', text: '首班·无需回收' }
  }
}

const canOpen = (room: LinenRoomState) => room.linenState !== 'NOT_NEEDED'
const isConfirmed = (room: LinenRoomState) => room.linenState === 'RECOVERED'

const pendingCount = computed(() =>
  rooms.value.filter(r => r.recoveryPendingLight).length
)
const availableCount = computed(() =>
  rooms.value.filter(r => r.availableLight).length
)

const loadData = async () => {
  loading.value = true
  try {
    const [roomRes, recordRes] = await Promise.all([
      linenApi.roomStates(),
      linenApi.records()
    ])
    rooms.value = roomRes.data.data || []
    records.value = recordRes.data.data || []
  } catch (error) {
    ElMessage.error('加载布草数据失败')
  } finally {
    loading.value = false
  }
}

const openCreate = (room: LinenRoomState) => {
  dialogMode.value = 'create'
  formRoom.value = room
  editingId.value = room.recoveryId
  form.value = {
    setCount: room.setCount ?? null,
    bagWeight: room.bagWeight ?? null,
    witnessName: room.witnessName ?? '',
    operator: '',
    remark: ''
  }
  dialogVisible.value = true
}

const openEdit = (room: LinenRoomState) => {
  dialogMode.value = 'edit'
  formRoom.value = room
  editingId.value = room.recoveryId
  form.value = {
    setCount: room.setCount ?? null,
    bagWeight: room.bagWeight ?? null,
    witnessName: room.witnessName ?? '',
    operator: '',
    remark: ''
  }
  dialogVisible.value = true
}

const dialogTitle = computed(() => {
  if (!formRoom.value) return '布草回收'
  const prefix = dialogMode.value === 'edit' ? '补登/修改' : '新开'
  return `${prefix}回收单 · ${formRoom.value.roomCode}`
})

const numericLocked = computed(() => formRoom.value ? isConfirmed(formRoom.value) : false)

// 把后端退回明细整理成弹窗文本：约定区间、本次秤重、按公斤折出来的套数
const blockedText = (blocked: LinenBlocked | undefined): string => {
  if (!blocked) return ''
  const lines: string[] = []
  if (blocked.existingRecoveryNo) {
    lines.push(`已在库未作废回收单：${blocked.existingRecoveryNo}`)
  }
  const min = blocked.kgPerSetMin !== undefined ? Number(blocked.kgPerSetMin).toFixed(2) : '1.50'
  const max = blocked.kgPerSetMax !== undefined ? Number(blocked.kgPerSetMax).toFixed(2) : '2.50'
  lines.push(`每套约定公斤区间：${min}~${max}kg/套`)
  lines.push(`本次秤重：${blocked.submittedBagWeight ?? '空'}kg`)
  if (blocked.inferredSetCountMin !== undefined && blocked.inferredSetCountMax !== undefined) {
    lines.push(`按公斤折出来的套数：${blocked.inferredSetCountMin}~${blocked.inferredSetCountMax} 套`)
  }
  if (blocked.submittedSetCount !== undefined && blocked.submittedSetCount !== null) {
    lines.push(`本次登记套数：${blocked.submittedSetCount} 套`)
  }
  return lines.join('\n')
}

const save = async () => {
  if (!formRoom.value) return
  saving.value = true
  try {
    await linenApi.save({
      id: editingId.value,
      roomId: formRoom.value.roomId,
      setCount: form.value.setCount,
      bagWeight: form.value.bagWeight,
      witnessName: form.value.witnessName?.trim() || null,
      operator: form.value.operator?.trim() || undefined,
      remark: form.value.remark?.trim() || undefined
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (error: any) {
    const data = error.response?.data
    const blocked: LinenBlocked | undefined = data?.data
    const message: string = data?.message || '保存失败'
    try {
      await ElMessageBox.alert(blocked ? `${message}\n\n${blockedText(blocked)}` : message, '保存失败', {
        type: 'error',
        confirmButtonText: '知道了'
      })
    } catch {
      // 用户关闭弹窗
    }
  } finally {
    saving.value = false
  }
}

const voidRecord = async (room: LinenRoomState) => {
  if (!room.recoveryId) return
  try {
    const { value } = await ElMessageBox.prompt(
      `确认作废回收单 ${room.recoveryNo}？作废后可为本周期重新开单，可住灯随之熄灭。`,
      '作废回收单',
      { confirmButtonText: '作废', cancelButtonText: '取消', inputPlaceholder: '作废原因（选填）', type: 'warning' }
    )
    await linenApi.voidRecord(room.recoveryId, '', value || undefined)
    ElMessage.success('已作废')
    await loadData()
  } catch (error: any) {
    if (error === 'cancel' || error?.action === 'cancel') return
    ElMessage.error(error.response?.data?.message || '作废失败')
  }
}

const recordStatusText = (status: string) => {
  if (status === 'CONFIRMED') return '已确认'
  if (status === 'VOID') return '已作废'
  return '草稿'
}
const recordStatusType = (status: string): 'success' | 'info' | 'warning' => {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'VOID') return 'info'
  return 'warning'
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <ElButton @click="loadData">刷新</ElButton>
      <span class="summary">
        <ElTag type="danger" effect="light">回收未齐 {{ pendingCount }} 间</ElTag>
        <ElTag type="success" effect="light" style="margin-left: 8px;">可住 {{ availableCount }} 间</ElTag>
      </span>
    </div>

    <div class="card-grid">
      <ElCard v-for="room in rooms" :key="room.roomId" class="room-card" shadow="hover">
        <div class="card-head">
          <div class="room-title">{{ room.roomCode }} · {{ room.roomName }}</div>
          <div class="lamp">
            <!-- 可住灯 与 回收未齐 互斥，只可能亮一个 -->
            <span v-if="room.availableLight" class="lamp-green">🟢 可住灯</span>
            <span v-else-if="room.recoveryPendingLight" class="lamp-red">🔴 回收未齐</span>
            <span v-else class="lamp-gray">⚪ 无需回收</span>
          </div>
        </div>

        <ElDescriptions :column="1" size="small" border class="card-desc">
          <ElDescriptionsItem label="当前停靠">
            {{ room.currentShipCode ? `${room.currentShipCode} ${room.currentShipName || ''}` : '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="上一班船">
            {{ room.departedShipCode ? `${room.departedShipCode} ${room.departedShipName || ''}` : '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="布草状态">
            <ElTag :type="stateTag(room).type" size="small">{{ stateTag(room).text }}</ElTag>
          </ElDescriptionsItem>
          <!-- 只有已确认（三栏齐、对得上）才给已换洗对勾，并把三栏摆出来供验收 -->
          <ElDescriptionsItem label="换洗验收">
            <template v-if="isConfirmed(room)">
              <span class="check-green">✔ 已换洗</span>
              <div class="detail-line">套数：{{ room.setCount }} 套 ｜ 封袋：{{ Number(room.bagWeight).toFixed(2) }}kg ｜ 见证人：{{ room.witnessName }}</div>
              <div class="detail-line dim">单号：{{ room.recoveryNo }} ｜ 约定 {{ kgText(room) }}</div>
            </template>
            <template v-else>
              <span class="check-gray">— 未完成，无对勾 —</span>
              <div v-if="room.recoveryNo" class="detail-line dim">
                草稿 {{ room.recoveryNo }}：套数 {{ room.setCount ?? '空' }} / 封袋 {{ room.bagWeight != null ? Number(room.bagWeight).toFixed(2) + 'kg' : '空' }} / 见证人 {{ room.witnessName || '空' }}
              </div>
            </template>
          </ElDescriptionsItem>
        </ElDescriptions>

        <div class="card-actions">
          <ElButton
            v-if="room.linenState === 'PENDING'"
            type="primary"
            size="small"
            @click="openCreate(room)"
          >新开回收单</ElButton>
          <ElButton
            v-if="room.linenState === 'DRAFT'"
            type="primary"
            size="small"
            @click="openEdit(room)"
          >补登（离泊后仍可用）</ElButton>
          <ElButton
            v-if="isConfirmed(room)"
            size="small"
            disabled
          >已确认·套数公斤已锁定</ElButton>
          <ElButton
            v-if="canOpen(room) && room.recoveryId"
            size="small"
            type="danger"
            plain
            @click="voidRecord(room)"
          >作废</ElButton>
          <ElTag v-if="!canOpen(room)" type="info" size="small">首班船</ElTag>
        </div>
      </ElCard>
    </div>

    <div class="records">
      <div class="records-title">回收单流水</div>
      <ElTable :data="records" border size="small" style="width: 100%">
        <ElTableColumn prop="recoveryNo" label="回收单号" width="210" />
        <ElTableColumn prop="roomCode" label="房间" width="100" />
        <ElTableColumn label="上一班船" width="120">
          <template #default="{ row }">{{ (row as LinenRecovery).departedShipCode || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="套数" width="70" align="right">
          <template #default="{ row }">{{ (row as LinenRecovery).setCount ?? '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="封袋(kg)" width="90" align="right">
          <template #default="{ row }">
            {{ (row as LinenRecovery).bagWeight != null ? Number((row as LinenRecovery).bagWeight).toFixed(2) : '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="约定区间" width="110">
          <template #default="{ row }">
            {{ Number((row as LinenRecovery).kgPerSetMin ?? 1.5).toFixed(2) }}~{{ Number((row as LinenRecovery).kgPerSetMax ?? 2.5).toFixed(2) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="witnessName" label="见证人" width="100">
          <template #default="{ row }">{{ (row as LinenRecovery).witnessName || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="recordStatusType((row as LinenRecovery).status)" size="small">
              {{ recordStatusText((row as LinenRecovery).status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="operator" label="登记人" width="90" />
        <ElTableColumn prop="confirmedTime" label="确认时间" min-width="160" />
      </ElTable>
      <ElEmpty v-if="records.length === 0" description="还没有回收单" />
    </div>

    <ElDialog :title="dialogTitle" v-model="dialogVisible" width="520px">
      <ElForm label-width="120px">
        <ElFormItem label="房间">
          <span>{{ formRoom?.roomCode }} · {{ formRoom?.roomName }}</span>
        </ElFormItem>
        <ElFormItem label="上一班船">
          <span>{{ formRoom?.departedShipCode || '—' }} {{ formRoom?.departedShipName || '' }}</span>
        </ElFormItem>
        <ElFormItem label="收走套数">
          <ElInputNumber
            v-model="form.setCount"
            :min="1"
            :disabled="numericLocked"
            style="width: 100%"
            placeholder="脏床品套数"
          />
        </ElFormItem>
        <ElFormItem label="封袋公斤数">
          <ElInputNumber
            v-model="form.bagWeight"
            :min="0"
            :step="0.1"
            :precision="2"
            :disabled="numericLocked"
            style="width: 100%"
            placeholder="秤上的封袋公斤数"
          />
        </ElFormItem>
        <ElFormItem label="见证人姓名">
          <ElInput v-model="form.witnessName" :disabled="numericLocked" placeholder="见证人" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="登记人">
          <ElInput v-model="form.operator" placeholder="保洁/值班员" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </ElFormItem>
        <div class="hint" v-if="formRoom">
          每套约定 {{ kgText(formRoom) }}。三栏没齐前保存为草稿、可住灯不亮；三栏齐且公斤折套数对得上，保存即点亮可住灯。
          可住灯亮过后套数与公斤数锁死。
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.summary {
  display: inline-flex;
  align-items: center;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}
.room-card {
  border-top: 3px solid #dcdfe6;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.room-title {
  font-weight: bold;
  color: #303133;
}
.lamp-green {
  color: #67c23a;
  font-weight: bold;
}
.lamp-red {
  color: #f56c6c;
  font-weight: bold;
}
.lamp-gray {
  color: #909399;
}
.card-desc {
  margin-bottom: 10px;
}
.detail-line {
  font-size: 12px;
  margin-top: 2px;
}
.detail-line.dim {
  color: #909399;
}
.check-green {
  color: #67c23a;
  font-weight: bold;
}
.check-gray {
  color: #c0c4cc;
}
.card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.records-title {
  font-weight: bold;
  margin-bottom: 10px;
  color: #303133;
}
.hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  padding: 0 8px;
}
</style>
