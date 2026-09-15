<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElButton, ElCard, ElMessage } from 'element-plus'
import { roomApi, shipApi, applianceApi, relationApi, type RelationChangeLog, type ElectricAppliance, type LoungeRoom, type Ship } from '@/api'

const logs = ref<RelationChangeLog[]>([])
const rooms = ref<LoungeRoom[]>([])
const ships = ref<Ship[]>([])
const appliances = ref<ElectricAppliance[]>([])

const filterDeviceId = ref<number | undefined>()
const filterRoomId = ref<number | undefined>()
const filterShipId = ref<number | undefined>()

const changeTypeMap: Record<string, string> = {
  'BIND': '绑定',
  'SHIP_CHANGE': '房间换船',
  'SHIP_SWITCH': '整船换班',
  'ROOM_SHIP_CHANGE': '房间占用改挂'
}

const loadData = async () => {
  try {
    const [roomRes, shipRes, applianceRes, logRes] = await Promise.all([
      roomApi.list(),
      shipApi.list(),
      applianceApi.list(),
      relationApi.getLogs()
    ])
    rooms.value = roomRes.data.data
    ships.value = shipRes.data.data
    appliances.value = applianceRes.data.data
    logs.value = logRes.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const search = async () => {
  try {
    const res = await relationApi.getLogs(filterDeviceId.value, filterRoomId.value, filterShipId.value)
    logs.value = res.data.data
  } catch (error) {
    ElMessage.error('查询失败')
  }
}

const reset = () => {
  filterDeviceId.value = undefined
  filterRoomId.value = undefined
  filterShipId.value = undefined
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">关联变更流水查询</div>
      </template>
      <div>
        <ElSelect v-model="filterDeviceId" placeholder="按设备筛选" style="width: 200px; margin-right: 10px;">
          <ElSelectOption v-for="appliance in appliances" :key="appliance.id" :label="appliance.deviceName" :value="appliance.id" />
        </ElSelect>
        <ElSelect v-model="filterRoomId" placeholder="按休息室筛选" style="width: 200px; margin-right: 10px;">
          <ElSelectOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" />
        </ElSelect>
        <ElSelect v-model="filterShipId" placeholder="按船舶筛选" style="width: 200px; margin-right: 10px;">
          <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
        </ElSelect>
        <ElButton type="primary" @click="search">查询</ElButton>
        <ElButton @click="reset">重置</ElButton>
      </div>
    </ElCard>

    <ElCard>
      <template #header>
        <div style="font-weight: bold;">变更记录列表</div>
      </template>
      <ElTable :data="logs" border style="width: 100%">
        <ElTableColumn prop="id" label="ID" width="80" />
        <ElTableColumn prop="changeBatch" label="换班批次" min-width="180" />
        <ElTableColumn prop="changeType" label="变更类型">
          <template #default="{ row }">
            {{ changeTypeMap[row.changeType] || row.changeType }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="deviceCode" label="设备编号" />
        <ElTableColumn prop="roomCode" label="当前休息室" />
        <ElTableColumn prop="oldRoomCode" label="原休息室" />
        <ElTableColumn prop="shipCode" label="当前船舶" />
        <ElTableColumn prop="oldShipCode" label="原船舶" />
        <ElTableColumn prop="operator" label="操作人" />
        <ElTableColumn prop="remark" label="备注" />
        <ElTableColumn prop="changeTime" label="变更时间" />
      </ElTable>
    </ElCard>
  </div>
</template>