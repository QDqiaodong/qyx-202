<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElSelect, ElInput, ElMessage, ElCard, ElMessageBox } from 'element-plus'
import ElSelectOption from 'element-plus'
import { applianceApi, roomApi, shipApi, relationApi, type ElectricAppliance, type LoungeRoom, type Ship, type ShiftBlockedAppliance, type ShiftResult, type RoomOverCapacity } from '@/api'

const appliances = ref<ElectricAppliance[]>([])
const rooms = ref<LoungeRoom[]>([])
const ships = ref<Ship[]>([])

const bindDialogVisible = ref(false)
const bindForm = ref({
  deviceId: undefined as number | undefined,
  roomId: undefined as number | undefined,
  shipId: undefined as number | undefined,
  operator: '',
  remark: ''
})

const updateDialogVisible = ref(false)
const updateForm = ref({
  roomId: undefined as number | undefined,
  shipId: undefined as number | undefined,
  operator: '',
  remark: ''
})

const shipChangeDialogVisible = ref(false)
const shipChangeForm = ref({
  oldShipId: undefined as number | undefined,
  newShipId: undefined as number | undefined,
  operator: '',
  remark: ''
})

const showBlockedError = (error: any, fallbackMessage: string) => {
  if (error.response?.status === 409 && error.response?.data?.data?.roomId
          && !Array.isArray(error.response.data.data)) {
    showOverCapacity(error)
    return
  }
  const blocked: ShiftBlockedAppliance[] = error.response?.data?.data || []
  const message = error.response?.data?.message || fallbackMessage
  if (blocked.length > 0) {
    const detail = blocked.map(item => `${item.deviceCode}（${item.deviceName}）`).join('、')
    ElMessageBox.alert(
      `${message}\n卡住的电器：${detail}`,
      '换班整单退回',
      {
        type: 'error',
        confirmButtonText: '知道了',
        customClass: 'shift-blocked-message'
      }
    )
  } else {
    ElMessage.error(message)
  }
}

const showOverCapacity = (error: any) => {
  const detail: RoomOverCapacity = error.response.data.data
  const n = (value: number) => Number(value).toFixed(2)
  const lines = [
    `房间：${detail.roomCode}（${detail.roomName || ''}）`,
    `承载：${n(detail.powerCapacity)} kW`,
    `当前已挂：${n(detail.powerUsed)} kW`,
    `这一台（${detail.applianceName || ''} ${detail.applianceCode || ''}）：${n(detail.appliancePower)} kW`,
    `挂上后合计：${n(detail.projectedTotal)} kW`,
    '',
    '本次挂入没有落账。该房已超承载时不能再挂新电器，也不接受从别的房间改挂进来；请先拆下电器或调低功率。'
  ]
  ElMessageBox.alert(lines.join('\n'), '挂入被退回：会压过房间承载', {
    type: 'error',
    confirmButtonText: '知道了',
    customClass: 'room-over-capacity-message'
  })
}

const showShiftSuccess = (result: ShiftResult, label: string) => {
  ElMessage.success(`${label}成功：房间 ${result.roomCount} 间、电器 ${result.applianceCount} 台，批次 ${result.changeBatch}`)
}

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

const handleBind = async () => {
  try {
    await relationApi.bind({
      deviceId: bindForm.value.deviceId,
      roomId: bindForm.value.roomId,
      shipId: bindForm.value.shipId,
      operator: bindForm.value.operator,
      remark: bindForm.value.remark
    })
    ElMessage.success('绑定成功')
    bindDialogVisible.value = false
    bindForm.value = {
      deviceId: undefined,
      roomId: undefined,
      shipId: undefined,
      operator: '',
      remark: ''
    }
    loadData()
  } catch (error: any) {
    showBlockedError(error, '绑定失败')
  }
}

const handleUpdateRelation = async () => {
  try {
    await relationApi.updateRelation(
      updateForm.value.roomId!,
      updateForm.value.shipId!,
      updateForm.value.operator,
      updateForm.value.remark
    ).then(res => showShiftSuccess(res.data.data as ShiftResult, '房间换班'))
    updateDialogVisible.value = false
    updateForm.value = {
      roomId: undefined,
      shipId: undefined,
      operator: '',
      remark: ''
    }
    loadData()
  } catch (error: any) {
    showBlockedError(error, '更新失败，换班已整单退回')
  }
}

const handleShipChange = async () => {
  try {
    await relationApi.shipChange(
      shipChangeForm.value.oldShipId!,
      shipChangeForm.value.newShipId!,
      shipChangeForm.value.operator,
      shipChangeForm.value.remark
    ).then(res => showShiftSuccess(res.data.data as ShiftResult, '整船换班'))
    shipChangeDialogVisible.value = false
    shipChangeForm.value = {
      oldShipId: undefined,
      newShipId: undefined,
      operator: '',
      remark: ''
    }
    loadData()
  } catch (error: any) {
    showBlockedError(error, '换班失败，整趟换班已退回')
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">设备绑定：电器 → 休息室 → 船舶</div>
      </template>
      <ElButton type="primary" @click="bindDialogVisible = true">设备绑定</ElButton>
    </ElCard>

    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">休息室重新分配：更换绑定船舶</div>
      </template>
      <ElButton type="primary" @click="updateDialogVisible = true">更新关联</ElButton>
    </ElCard>

    <ElCard>
      <template #header>
        <div style="font-weight: bold;">船舶换班：批量转移所有关联电器</div>
      </template>
      <ElButton type="primary" @click="shipChangeDialogVisible = true">船舶换班</ElButton>
    </ElCard>

    <ElDialog title="设备绑定" v-model="bindDialogVisible">
      <ElForm :model="bindForm" label-width="100px">
        <ElFormItem label="选择电器" required>
          <ElSelect v-model="bindForm.deviceId">
            <ElSelectOption v-for="appliance in appliances" :key="appliance.id" :label="appliance.deviceName" :value="appliance.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="选择休息室" required>
          <ElSelect v-model="bindForm.roomId">
            <ElSelectOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="选择船舶" required>
          <ElSelect v-model="bindForm.shipId">
            <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="bindForm.operator" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="bindForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="bindDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleBind">确定</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="更新休息室关联" v-model="updateDialogVisible">
      <ElForm :model="updateForm" label-width="100px">
        <ElFormItem label="选择休息室" required>
          <ElSelect v-model="updateForm.roomId">
            <ElSelectOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="更换为船舶" required>
          <ElSelect v-model="updateForm.shipId">
            <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="updateForm.operator" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="updateForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="updateDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleUpdateRelation">确定</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="船舶换班" v-model="shipChangeDialogVisible">
      <ElForm :model="shipChangeForm" label-width="100px">
        <ElFormItem label="原船舶" required>
          <ElSelect v-model="shipChangeForm.oldShipId">
            <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="新船舶" required>
          <ElSelect v-model="shipChangeForm.newShipId">
            <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="操作人">
          <ElInput v-model="shipChangeForm.operator" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="shipChangeForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="shipChangeDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleShipChange">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
<style>
.shift-blocked-message .el-message-box__message {
  white-space: pre-line;
  line-height: 1.6;
}
.room-over-capacity-message .el-message-box__message {
  white-space: pre-line;
  line-height: 1.7;
}
</style>
