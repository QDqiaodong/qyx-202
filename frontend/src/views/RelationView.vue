<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElSelect, ElInput, ElMessage, ElCard } from 'element-plus'
import ElSelectOption from 'element-plus'
import { applianceApi, roomApi, shipApi, relationApi, type ElectricAppliance, type LoungeRoom, type Ship } from '@/api'

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
    ElMessage.error(error.response?.data?.message || '绑定失败')
  }
}

const handleUpdateRelation = async () => {
  try {
    await relationApi.updateRelation(
      updateForm.value.roomId!,
      updateForm.value.shipId!,
      updateForm.value.operator,
      updateForm.value.remark
    )
    ElMessage.success('关联更新成功')
    updateDialogVisible.value = false
    updateForm.value = {
      roomId: undefined,
      shipId: undefined,
      operator: '',
      remark: ''
    }
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '更新失败')
  }
}

const handleShipChange = async () => {
  try {
    await relationApi.shipChange(
      shipChangeForm.value.oldShipId!,
      shipChangeForm.value.newShipId!,
      shipChangeForm.value.operator,
      shipChangeForm.value.remark
    )
    ElMessage.success('船舶换班成功')
    shipChangeDialogVisible.value = false
    shipChangeForm.value = {
      oldShipId: undefined,
      newShipId: undefined,
      operator: '',
      remark: ''
    }
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '换班失败')
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