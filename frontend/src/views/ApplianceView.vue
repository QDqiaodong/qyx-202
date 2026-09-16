<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElMessage, ElInputNumber, ElMessageBox } from 'element-plus'
import ElSelectOption from 'element-plus'
import { applianceApi, roomApi, shipApi, type ElectricAppliance, type LoungeRoom, type Ship, type RoomOverCapacity } from '@/api'

const appliances = ref<ElectricAppliance[]>([])
const rooms = ref<LoungeRoom[]>([])
const ships = ref<Ship[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<ElectricAppliance>({
  deviceCode: '',
  deviceName: '',
  power: 0,
  applianceType: '',
  status: 'ACTIVE'
})

const powerInput = ref<number | null>(0)

const applianceTypes = ['冰箱', '热水器', '空调', '电视', '洗衣机', '其他']
const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' }
]

const selectedRoom = computed<LoungeRoom | undefined>(() =>
  rooms.value.find(room => room.id === form.value.roomId)
)

const roomHintClass = computed(() => {
  const room = selectedRoom.value
  if (!room) {
    return 'room-hint'
  }
  const used = Number(room.powerUsed || 0)
  const capacity = Number(room.powerCapacity ?? 5)
  if (used > capacity) {
    return 'room-hint room-hint-over'
  }
  if (used + Number(powerInput.value || 0) > capacity) {
    return 'room-hint room-hint-warn'
  }
  return 'room-hint room-hint-ok'
})

const loadData = async () => {
  try {
    const [applianceRes, roomRes, shipRes] = await Promise.all([
      applianceApi.list(),
      roomApi.list(),
      shipApi.list()
    ])
    appliances.value = applianceRes.data.data
    rooms.value = roomRes.data.data
    ships.value = shipRes.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const showOverCapacity = (error: any) => {
  const detail: RoomOverCapacity | undefined = error.response?.data?.data
  if (!detail) {
    ElMessage.error(error.response?.data?.message || '操作失败')
    return
  }
  const n = (value: number) => Number(value).toFixed(2)
  const lines = [
    `房间：${detail.roomCode}（${detail.roomName || ''}）`,
    `承载：${n(detail.powerCapacity)} kW`,
    `当前已挂：${n(detail.powerUsed)} kW`,
    `这一台（${detail.applianceName || ''} ${detail.applianceCode || ''}）：${n(detail.appliancePower)} kW`,
    `挂上后合计：${n(detail.projectedTotal)} kW`,
    '',
    '本次挂入没有落账。请先拆下电器或调低功率，把合计降回承载以内。'
  ]
  ElMessageBox.alert(lines.join('\n'), '挂入被退回：会压过房间承载', {
    type: 'error',
    confirmButtonText: '知道了',
    customClass: 'room-over-capacity-message'
  })
}

const openDialog = (edit = false, data?: ElectricAppliance) => {
  isEdit.value = edit
  if (edit && data) {
    form.value = { ...data }
    powerInput.value = data.power || 0
  } else {
    form.value = {
      deviceCode: '',
      deviceName: '',
      power: 0,
      applianceType: '',
      status: 'ACTIVE'
    }
    powerInput.value = 0
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    if ((form.value.roomId === undefined) !== (form.value.shipId === undefined)) {
      ElMessage.error('房间和船舶必须同时登记，不能只挂一层')
      return
    }
    form.value.power = powerInput.value || 0
    if (isEdit.value && form.value.id) {
      await applianceApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await applianceApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    if (error.response?.status === 409 && error.response?.data?.data?.roomId) {
      showOverCapacity(error)
    } else {
      ElMessage.error(error.response?.data?.message || '操作失败')
    }
  }
}

const deleteItem = async (id: number) => {
  try {
    await applianceApi.delete(id)
    ElMessage.success('拆除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '拆除失败')
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElButton type="primary" @click="openDialog(false)" style="margin-bottom: 20px;">
      添加电器
    </ElButton>
    <ElTable :data="appliances" border style="width: 100%">
      <ElTableColumn prop="deviceCode" label="设备编号" />
      <ElTableColumn prop="deviceName" label="设备名称" />
      <ElTableColumn prop="power" label="功率(kW)" />
      <ElTableColumn prop="applianceType" label="电器类型" />
      <ElTableColumn prop="status" label="状态">
        <template #default="{ row }">
          <span :class="(row as ElectricAppliance).status === 'ACTIVE' ? 'text-green' : 'text-red'">
            {{ (row as ElectricAppliance).status === 'ACTIVE' ? '启用' : '停用' }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="roomCode" label="所属休息室" />
      <ElTableColumn prop="shipCode" label="绑定船舶" />
      <ElTableColumn prop="lastChangeBatch" label="最近换班批次" min-width="180" />
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openDialog(true, row as ElectricAppliance)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="deleteItem((row as ElectricAppliance).id!)">拆走</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑电器' : '添加电器'" v-model="dialogVisible">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="设备编号" required>
          <ElInput v-model="form.deviceCode" />
        </ElFormItem>
        <ElFormItem label="设备名称" required>
          <ElInput v-model="form.deviceName" />
        </ElFormItem>
        <ElFormItem label="功率(kW)" required>
          <ElInputNumber v-model="powerInput" :min="0" :step="0.1" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="电器类型" required>
          <ElSelect v-model="form.applianceType">
            <ElSelectOption v-for="type in applianceTypes" :key="type" :label="type" :value="type" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status">
            <ElSelectOption v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="所属休息室">
          <ElSelect v-model="form.roomId" :disabled="isEdit" placeholder="绑定后请通过换班调整">
            <ElSelectOption
              v-for="room in rooms"
              :key="room.id"
              :label="`${room.roomName}（已挂 ${Number(room.powerUsed || 0).toFixed(2)}/${Number(room.powerCapacity ?? 5).toFixed(2)} kW）`"
              :value="room.id"
            />
          </ElSelect>
          <div v-if="!isEdit && selectedRoom" :class="roomHintClass">
            <template v-if="Number(selectedRoom.powerUsed || 0) > Number(selectedRoom.powerCapacity ?? 5)">
              该房已挂 {{ Number(selectedRoom.powerUsed).toFixed(2) }} kW，已超承载
              {{ Number(selectedRoom.powerCapacity ?? 5).toFixed(2) }} kW，先降下合计才能挂这台
            </template>
            <template v-else>
              承载 {{ Number(selectedRoom.powerCapacity ?? 5).toFixed(2) }} kW，已挂
              {{ Number(selectedRoom.powerUsed || 0).toFixed(2) }} kW，剩余
              {{ Number(selectedRoom.powerRemaining ?? 0).toFixed(2) }} kW；本台
              {{ Number(powerInput || 0).toFixed(2) }} kW，挂上后合计
              {{ (Number(selectedRoom.powerUsed || 0) + Number(powerInput || 0)).toFixed(2) }} kW
            </template>
          </div>
        </ElFormItem>
        <ElFormItem label="绑定船舶">
          <ElSelect v-model="form.shipId" :disabled="isEdit" placeholder="绑定后请通过换班调整">
            <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
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
.room-hint {
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
.room-hint-ok {
  color: #67c23a;
}
.room-hint-warn {
  color: #e6a23c;
}
.room-hint-over {
  color: #f56c6c;
  font-weight: bold;
}
</style>

<style>
.room-over-capacity-message .el-message-box__message {
  white-space: pre-line;
  line-height: 1.7;
}
</style>
