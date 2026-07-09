<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElMessage, ElInputNumber } from 'element-plus'
import ElSelectOption from 'element-plus'
import { applianceApi, roomApi, shipApi, type ElectricAppliance, type LoungeRoom, type Ship } from '@/api'

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
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const deleteItem = async (id: number) => {
  try {
    await applianceApi.delete(id)
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
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openDialog(true, row as ElectricAppliance)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="deleteItem((row as ElectricAppliance).id!)">删除</ElButton>
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
          <ElSelect v-model="form.roomId">
            <ElSelectOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="绑定船舶">
          <ElSelect v-model="form.shipId">
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
</style>