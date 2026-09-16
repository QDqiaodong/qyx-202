<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElMessage, ElInputNumber, ElTag } from 'element-plus'
import ElSelectOption from 'element-plus'
import { roomApi, applianceApi, type LoungeRoom, type ElectricAppliance } from '@/api'

const DEFAULT_CAPACITY = 5

const rooms = ref<LoungeRoom[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<LoungeRoom>({
  roomCode: '',
  roomName: '',
  status: 'ACTIVE',
  powerCapacity: DEFAULT_CAPACITY
})

// 展开行里逐台列出该房电器，用来跟「功率合计」当场对账
const appliancesByRoom = ref<Record<number, ElectricAppliance[]>>({})
const loadingRooms = ref<Set<number>>(new Set())

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' }
]

const kW = (value?: number | null) => (value === null || value === undefined ? '0.00' : Number(value).toFixed(2))

const isOver = (room: LoungeRoom) => room.overCapacity === true

const rowClassName = ({ row }: { row: LoungeRoom }) => (isOver(row) ? 'room-over-capacity' : '')

const loadData = async () => {
  try {
    const res = await roomApi.list()
    rooms.value = res.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const loadRoomAppliances = async (roomId: number) => {
  if (appliancesByRoom.value[roomId] || loadingRooms.value.has(roomId)) {
    return
  }
  loadingRooms.value.add(roomId)
  try {
    const res = await applianceApi.listByRoom(roomId)
    appliancesByRoom.value[roomId] = res.data.data || []
  } catch (error) {
    ElMessage.error('加载房间电器失败')
  } finally {
    loadingRooms.value.delete(roomId)
  }
}

const handleExpand = (row: unknown, expanded: unknown) => {
  // Element Plus：单行展开变化时第二参为已展开行数组；直接判断本行是否在其中
  if (Array.isArray(expanded) && expanded.some(item => item.id === (row as LoungeRoom).id)) {
    const target = row as LoungeRoom
    if (target.id) {
      loadRoomAppliances(target.id)
    }
  }
}

const openDialog = (edit = false, data?: LoungeRoom) => {
  isEdit.value = edit
  if (edit && data) {
    form.value = { ...data }
  } else {
    form.value = {
      roomCode: '',
      roomName: '',
      status: 'ACTIVE',
      powerCapacity: DEFAULT_CAPACITY
    }
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    if (form.value.powerCapacity === undefined || form.value.powerCapacity === null) {
      ElMessage.error('请填写用电承载（千瓦）')
      return
    }
    if (Number(form.value.powerCapacity) < 0) {
      ElMessage.error('用电承载不能为负数')
      return
    }
    if (isEdit.value && form.value.id) {
      await roomApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await roomApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const deleteItem = async (id: number) => {
  try {
    await roomApi.delete(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElButton type="primary" @click="openDialog(false)" style="margin-bottom: 20px;">
      添加休息室
    </ElButton>
    <ElTable :data="rooms" border style="width: 100%" :row-class-name="rowClassName" @expand-change="handleExpand">
      <ElTableColumn type="expand">
        <template #default="{ row }">
          <div class="expand-box">
            <div class="expand-title">本房已挂电器（功率合计应与「已挂功率」一致）：</div>
            <div v-if="loadingRooms.has((row as LoungeRoom).id!)">加载中…</div>
            <template v-else>
              <div v-if="(appliancesByRoom[(row as LoungeRoom).id!] || []).length === 0" class="expand-empty">
                还没有挂任何电器
              </div>
              <ElTable
                v-else
                :data="appliancesByRoom[(row as LoungeRoom).id!]"
                size="small"
                border
                style="margin: 8px 0 0 12px; width: calc(100% - 12px);"
              >
                <ElTableColumn prop="deviceCode" label="设备编号" width="140" />
                <ElTableColumn prop="deviceName" label="设备名称" />
                <ElTableColumn prop="applianceType" label="类型" width="100" />
                <ElTableColumn label="功率(kW)" width="110" align="right">
                  <template #default="{ row: device }">{{ kW((device as ElectricAppliance).power) }}</template>
                </ElTableColumn>
                <ElTableColumn label="状态" width="80">
                  <template #default="{ row: device }">
                    {{ (device as ElectricAppliance).status === 'ACTIVE' ? '启用' : '停用' }}
                  </template>
                </ElTableColumn>
              </ElTable>
              <div v-if="(appliancesByRoom[(row as LoungeRoom).id!] || []).length > 0" class="expand-sum">
                逐台相加：
                {{ (appliancesByRoom[(row as LoungeRoom).id!] || []).reduce((sum, item) => sum + Number(item.power || 0), 0).toFixed(2) }} kW
                ｜ 服务端合计：{{ kW((row as LoungeRoom).powerUsed) }} kW
              </div>
            </template>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="roomCode" label="房间编号" />
      <ElTableColumn prop="roomName" label="房间名称" />
      <ElTableColumn prop="floor" label="楼层" />
      <ElTableColumn prop="capacity" label="容纳人数" />
      <ElTableColumn label="用电承载(kW)" width="120" align="right">
        <template #default="{ row }">{{ kW((row as LoungeRoom).powerCapacity) }}</template>
      </ElTableColumn>
      <ElTableColumn label="已挂功率(kW)" width="130" align="right">
        <template #default="{ row }">
          <span :class="{ 'power-over': isOver(row as LoungeRoom) }">
            {{ kW((row as LoungeRoom).powerUsed) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="还能接(kW)" width="120" align="right">
        <template #default="{ row }">
          <span :class="{ 'power-over': isOver(row as LoungeRoom) }">
            {{ kW((row as LoungeRoom).powerRemaining) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="承载状态" width="110" align="center">
        <template #default="{ row }">
          <ElTag v-if="isOver(row as LoungeRoom)" type="danger">已超承载</ElTag>
          <ElTag v-else type="success">正常</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="状态" width="80">
        <template #default="{ row }">
          <span :class="(row as LoungeRoom).status === 'ACTIVE' ? 'text-green' : 'text-red'">
            {{ (row as LoungeRoom).status === 'ACTIVE' ? '启用' : '停用' }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="shipCode" label="绑定船舶" width="110" />
      <ElTableColumn prop="shipName" label="船舶名称" />
      <ElTableColumn prop="changeBatch" label="最近换班批次" min-width="180" />
      <ElTableColumn label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <ElButton size="small" @click="openDialog(true, row as LoungeRoom)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="deleteItem((row as LoungeRoom).id!)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑休息室' : '添加休息室'" v-model="dialogVisible">
      <ElForm :model="form" label-width="110px">
        <ElFormItem label="房间编号" required>
          <ElInput v-model="form.roomCode" />
        </ElFormItem>
        <ElFormItem label="房间名称" required>
          <ElInput v-model="form.roomName" />
        </ElFormItem>
        <ElFormItem label="楼层">
          <ElInput v-model="form.floor" />
        </ElFormItem>
        <ElFormItem label="容纳人数">
          <ElInputNumber v-model="form.capacity" :min="1" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="用电承载(kW)" required>
          <ElInputNumber v-model="form.powerCapacity" :min="0" :step="0.5" :precision="2" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status">
            <ElSelectOption v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="save">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.text-green {
  color: #67c23a;
}
.text-red {
  color: #f56c6c;
}
.power-over {
  color: #f56c6c;
  font-weight: bold;
}
.expand-box {
  padding: 8px 16px 12px;
}
.expand-title {
  font-weight: bold;
  color: #606266;
}
.expand-empty {
  margin-top: 6px;
  color: #909399;
}
.expand-sum {
  margin: 8px 0 0 12px;
  color: #606266;
}
:deep(.room-over-capacity) {
  background-color: #fef0f0 !important;
}
:deep(.room-over-capacity td) {
  background-color: #fef0f0 !important;
}
</style>
