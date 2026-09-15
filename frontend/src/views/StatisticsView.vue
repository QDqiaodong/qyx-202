<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElSelect, ElButton, ElCard, ElMessage } from 'element-plus'
import { roomApi, shipApi, relationApi, type ElectricAppliance, type LoungeRoom, type Ship } from '@/api'

const rooms = ref<LoungeRoom[]>([])
const ships = ref<Ship[]>([])
const selectedRoomId = ref<number | undefined>()
const selectedShipId = ref<number | undefined>()
const appliances = ref<ElectricAppliance[]>([])
const mode = ref<'room' | 'ship'>('room')

const loadData = async () => {
  try {
    const [roomRes, shipRes] = await Promise.all([
      roomApi.list(),
      shipApi.list()
    ])
    rooms.value = roomRes.data.data
    ships.value = shipRes.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const searchByRoom = async () => {
  if (!selectedRoomId.value) {
    ElMessage.warning('请选择休息室')
    return
  }
  try {
    const res = await relationApi.getAppliancesByRoom(selectedRoomId.value)
    appliances.value = res.data.data
  } catch (error) {
    ElMessage.error('查询失败')
  }
}

const searchByShip = async () => {
  if (!selectedShipId.value) {
    ElMessage.warning('请选择船舶')
    return
  }
  try {
    const res = await relationApi.getAppliancesByShip(selectedShipId.value)
    appliances.value = res.data.data
  } catch (error) {
    ElMessage.error('查询失败')
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">双维度查询：按休息室 / 按船舶</div>
      </template>

      <div style="margin-bottom: 20px;">
        <ElButton :type="mode === 'room' ? 'primary' : 'default'" @click="mode = 'room'">
          按休息室查询
        </ElButton>
        <ElButton :type="mode === 'ship' ? 'primary' : 'default'" @click="mode = 'ship'" style="margin-left: 10px;">
          按船舶查询
        </ElButton>
      </div>

      <div v-if="mode === 'room'">
        <ElSelect v-model="selectedRoomId" placeholder="选择休息室" style="width: 300px; margin-right: 10px;">
          <ElSelectOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id" />
        </ElSelect>
        <ElButton type="primary" @click="searchByRoom">查询电器清单</ElButton>
      </div>

      <div v-else>
        <ElSelect v-model="selectedShipId" placeholder="选择船舶" style="width: 300px; margin-right: 10px;">
          <ElSelectOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id" />
        </ElSelect>
        <ElButton type="primary" @click="searchByShip">查询电器清单</ElButton>
      </div>
    </ElCard>

    <ElCard>
      <template #header>
        <div style="font-weight: bold;">电器清单</div>
      </template>
      <ElTable :data="appliances" border style="width: 100%">
        <ElTableColumn prop="deviceCode" label="设备编号" />
        <ElTableColumn prop="deviceName" label="设备名称" />
        <ElTableColumn prop="power" label="功率(kW)" />
        <ElTableColumn prop="applianceType" label="电器类型" />
        <ElTableColumn prop="status" label="状态">
          <template #default="{ row }">
            <span :class="row.status === 'ACTIVE' ? 'text-green' : 'text-red'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="roomCode" label="所属休息室" />
        <ElTableColumn prop="roomName" label="休息室名称" />
        <ElTableColumn prop="shipCode" label="绑定船舶" />
        <ElTableColumn prop="shipName" label="船舶名称" />
        <ElTableColumn prop="lastChangeBatch" label="最近换班批次" min-width="180" />
      </ElTable>
    </ElCard>
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