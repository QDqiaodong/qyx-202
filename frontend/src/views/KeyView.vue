<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElDialog, ElForm, ElFormItem, ElSelect, ElOption, ElInput, ElMessage, ElCard,
  ElMessageBox, ElTable, ElTableColumn, ElTag, ElTabs, ElTabPane
} from 'element-plus'
import {
  keyApi, roomApi, shipApi,
  type LoungeKey, type LoungeRoom, type Ship, type KeyOccupancy, type KeyCheckoutRecord, type KeyBlocked, type LinenBlocked
} from '@/api'

const keys = ref<LoungeKey[]>([])
const rooms = ref<LoungeRoom[]>([])
const ships = ref<Ship[]>([])

const keyDialogVisible = ref(false)
const keyDialogEdit = ref(false)
const keyForm = ref<LoungeKey>({ keyCode: '', keyName: '', roomId: undefined })

const checkoutDialogVisible = ref(false)
const checkoutForm = ref({
  keyId: undefined as number | undefined,
  shipId: undefined as number | undefined,
  holderName: '',
  operator: '',
  remark: ''
})

const returnDialogVisible = ref(false)
const returnForm = ref({
  keyId: undefined as number | undefined,
  operator: '',
  remark: ''
})

const activeTab = ref('byShip')
const shipOccupancy = ref<KeyOccupancy[]>([])
const roomOccupancy = ref<KeyOccupancy[]>([])
const records = ref<KeyCheckoutRecord[]>([])
const filterShipId = ref<number | undefined>()
const filterRoomId = ref<number | undefined>()
const recordKeyId = ref<number | undefined>()
const recordRoomId = ref<number | undefined>()
const recordShipId = ref<number | undefined>()

const occupiedKeys = computed(() => keys.value.filter(key => key.checkoutBatch))
const availableKeys = computed(() => keys.value.filter(key => !key.checkoutBatch))

const selectedCheckoutKey = computed(() => keys.value.find(key => key.id === checkoutForm.value.keyId))
const selectedCheckoutRoom = computed(() => rooms.value.find(room => room.id === selectedCheckoutKey.value?.roomId))
const selectedReturnKey = computed(() => keys.value.find(key => key.id === returnForm.value.keyId))

const loadData = async () => {
  try {
    const [keyRes, roomRes, shipRes] = await Promise.all([keyApi.list(), roomApi.list(), shipApi.list()])
    keys.value = keyRes.data.data
    rooms.value = roomRes.data.data
    ships.value = shipRes.data.data
    if (filterShipId.value) await loadShipOccupancy()
    if (filterRoomId.value) await loadRoomOccupancy()
    await loadRecords()
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const loadShipOccupancy = async () => {
  if (!filterShipId.value) {
    shipOccupancy.value = []
    return
  }
  const res = await keyApi.occupancyByShip(filterShipId.value)
  shipOccupancy.value = res.data.data
}

const loadRoomOccupancy = async () => {
  if (!filterRoomId.value) {
    roomOccupancy.value = []
    return
  }
  const res = await keyApi.occupancyByRoom(filterRoomId.value)
  roomOccupancy.value = res.data.data
}

const loadRecords = async () => {
  const res = await keyApi.records(recordKeyId.value, recordRoomId.value, recordShipId.value)
  records.value = res.data.data
}

const openKeyDialog = (edit: boolean, row?: LoungeKey) => {
  keyDialogEdit.value = edit
  keyForm.value = edit && row ? { ...row } : { keyCode: '', keyName: '', roomId: undefined }
  keyDialogVisible.value = true
}

const saveKey = async () => {
  try {
    if (keyDialogEdit.value && keyForm.value.id) {
      await keyApi.update(keyForm.value.id, keyForm.value)
      ElMessage.success('钥匙已更新')
    } else {
      await keyApi.create(keyForm.value)
      ElMessage.success('钥匙已登记')
    }
    keyDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  }
}

const removeKey = async (row: LoungeKey) => {
  try {
    await keyApi.delete(row.id!)
    ElMessage.success('钥匙已删除')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

const onCheckoutKeyChange = (keyId: number) => {
  const key = keys.value.find(item => item.id === keyId)
  const room = rooms.value.find(item => item.id === key?.roomId)
  checkoutForm.value.shipId = room?.shipId
}

const openCheckoutDialog = () => {
  checkoutForm.value = { keyId: undefined, shipId: undefined, holderName: '', operator: '', remark: '' }
  checkoutDialogVisible.value = true
}

const showBlockedError = (error: any, fallback: string) => {
  const blocked: KeyBlocked | undefined = error.response?.data?.data
  const linenBlocked: LinenBlocked | undefined =
    blocked && (blocked as unknown as LinenBlocked).reason === 'LINEN_PENDING'
      ? (blocked as unknown as LinenBlocked)
      : undefined
  const message = error.response?.data?.message || fallback

  if (linenBlocked) {
    const lines = [
      `卡住的房间：${linenBlocked.roomCode}（${linenBlocked.roomName || '-'}）`
    ]
    if (linenBlocked.departedShipCode) {
      lines.push(`上一班船：${linenBlocked.departedShipCode}（${linenBlocked.departedShipName || '-'}）`)
    }
    if (linenBlocked.existingRecoveryNo) {
      lines.push(`未齐回收单：${linenBlocked.existingRecoveryNo}（套数/封袋/见证人没齐）`)
    } else {
      lines.push('回收单：还没开')
    }
    if (linenBlocked.kgPerSetMin !== undefined && linenBlocked.kgPerSetMax !== undefined) {
      lines.push(`每套约定：${Number(linenBlocked.kgPerSetMin).toFixed(2)}~${Number(linenBlocked.kgPerSetMax).toFixed(2)}kg/套`)
    }
    lines.push('请先到「布草回收」把脏床品套数、封袋公斤数、见证人登记齐，再领取钥匙占用。')
    ElMessageBox.alert(`${message}\n\n${lines.join('\n')}`, '领取整单退回·回收未齐', {
      type: 'error',
      confirmButtonText: '知道了',
      customClass: 'key-blocked-message'
    })
    return
  }

  if (blocked && blocked.keyCode) {
    const lines = [
      `卡住的钥匙：${blocked.keyCode}（${blocked.keyName || '-'}）`,
      `卡住的房间：${blocked.roomCode}（${blocked.roomName || '-'}）`
    ]
    if (blocked.requestedShipCode) {
      lines.push(`领取时按的船舶：${blocked.requestedShipCode}（${blocked.requestedShipName || '-'}）`)
    }
    if (blocked.currentShipCode) {
      lines.push(`房间当前停靠：${blocked.currentShipCode}（${blocked.currentShipName || '-'}）`)
    }
    if (blocked.holderName) {
      lines.push(`当前持匙人：${blocked.holderName}`)
    }
    ElMessageBox.alert(`${message}\n\n${lines.join('\n')}`, '领取整单退回', {
      type: 'error',
      confirmButtonText: '知道了',
      customClass: 'key-blocked-message'
    })
  } else {
    ElMessage.error(message)
  }
}

const handleCheckout = async () => {
  if (!checkoutForm.value.keyId || !checkoutForm.value.shipId || !checkoutForm.value.holderName) {
    ElMessage.warning('钥匙、船舶、持匙人不能为空')
    return
  }
  try {
    const res = await keyApi.checkout({
      keyId: checkoutForm.value.keyId,
      shipId: checkoutForm.value.shipId,
      holderName: checkoutForm.value.holderName,
      operator: checkoutForm.value.operator,
      remark: checkoutForm.value.remark
    })
    ElMessage.success(`领取成功，批次 ${res.data.data.checkoutBatch}`)
    checkoutDialogVisible.value = false
    loadData()
  } catch (error: any) {
    showBlockedError(error, '领取失败，整单已退回')
  }
}

const openReturnDialog = () => {
  returnForm.value = { keyId: undefined, operator: '', remark: '' }
  returnDialogVisible.value = true
}

const handleReturn = async () => {
  if (!returnForm.value.keyId) {
    ElMessage.warning('请选择要归还的钥匙')
    return
  }
  try {
    const res = await keyApi.returnKey({
      keyId: returnForm.value.keyId,
      operator: returnForm.value.operator,
      remark: returnForm.value.remark
    })
    ElMessage.success(`归还成功，批次 ${res.data.data.checkoutBatch}`)
    returnDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '归还失败')
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">钥匙档案：钥匙 → 所属休息室</div>
      </template>
      <ElButton type="primary" @click="openKeyDialog(false)" style="margin-bottom: 12px;">登记钥匙</ElButton>
      <ElTable :data="keys" border style="width: 100%">
        <ElTableColumn prop="keyCode" label="钥匙编号" width="120" />
        <ElTableColumn prop="keyName" label="钥匙名称" min-width="140" />
        <ElTableColumn prop="roomCode" label="所属休息室" width="120" />
        <ElTableColumn prop="roomName" label="休息室名称" min-width="140" />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="(row as LoungeKey).checkoutBatch ? 'danger' : 'success'">
              {{ (row as LoungeKey).checkoutBatch ? '已借出' : '在库' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="holderName" label="持匙人" width="110" />
        <ElTableColumn prop="shipCode" label="领取时停靠船舶" width="140" />
        <ElTableColumn prop="checkoutBatch" label="领取批次" min-width="170" />
        <ElTableColumn label="操作" width="150">
          <template #default="{ row }">
            <ElButton size="small" @click="openKeyDialog(true, row as LoungeKey)">编辑</ElButton>
            <ElButton size="small" type="danger" @click="removeKey(row as LoungeKey)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElCard style="margin-bottom: 20px;">
      <template #header>
        <div style="font-weight: bold;">钥匙领还：领取同时落下「钥匙跟哪间房」和「房间今晚停靠哪条船」</div>
      </template>
      <ElButton type="primary" @click="openCheckoutDialog">领取钥匙</ElButton>
      <ElButton type="success" @click="openReturnDialog" style="margin-left: 10px;">归还钥匙</ElButton>
    </ElCard>

    <ElCard>
      <template #header>
        <div style="font-weight: bold;">三处对账：同一次领取在三个视角必须对得上</div>
      </template>
      <ElTabs v-model="activeTab">
        <ElTabPane label="按船看未还钥匙" name="byShip">
          <div style="margin-bottom: 12px;">
            <ElSelect v-model="filterShipId" placeholder="选择船舶" style="width: 240px; margin-right: 10px;" @change="loadShipOccupancy">
              <ElOption v-for="ship in ships" :key="ship.id" :label="`${ship.shipCode}（${ship.shipName}）`" :value="ship.id!" />
            </ElSelect>
            <ElButton type="primary" @click="loadShipOccupancy">查询</ElButton>
          </div>
          <ElTable :data="shipOccupancy" border style="width: 100%">
            <ElTableColumn prop="checkoutBatch" label="领取批次" min-width="170" />
            <ElTableColumn prop="keyCode" label="钥匙编号" width="110" />
            <ElTableColumn prop="keyName" label="钥匙名称" min-width="120" />
            <ElTableColumn prop="roomCode" label="房间" width="100" />
            <ElTableColumn prop="roomName" label="房间名称" min-width="120" />
            <ElTableColumn prop="shipCode" label="停靠船舶" width="110" />
            <ElTableColumn prop="holderName" label="持匙人" width="100" />
            <ElTableColumn prop="checkoutTime" label="领取时间" min-width="160" />
          </ElTable>
        </ElTabPane>
        <ElTabPane label="按房看持匙人" name="byRoom">
          <div style="margin-bottom: 12px;">
            <ElSelect v-model="filterRoomId" placeholder="选择休息室" style="width: 240px; margin-right: 10px;" @change="loadRoomOccupancy">
              <ElOption v-for="room in rooms" :key="room.id" :label="`${room.roomCode}（${room.roomName}）`" :value="room.id!" />
            </ElSelect>
            <ElButton type="primary" @click="loadRoomOccupancy">查询</ElButton>
          </div>
          <ElTable :data="roomOccupancy" border style="width: 100%">
            <ElTableColumn prop="checkoutBatch" label="领取批次" min-width="170" />
            <ElTableColumn prop="keyCode" label="钥匙编号" width="110" />
            <ElTableColumn prop="keyName" label="钥匙名称" min-width="120" />
            <ElTableColumn prop="holderName" label="持匙人" width="100" />
            <ElTableColumn prop="shipCode" label="领取时停靠船舶" width="140" />
            <ElTableColumn prop="shipName" label="船舶名称" min-width="120" />
            <ElTableColumn prop="checkoutTime" label="领取时间" min-width="160" />
          </ElTable>
        </ElTabPane>
        <ElTabPane label="领还流水" name="records">
          <div style="margin-bottom: 12px;">
            <ElSelect v-model="recordKeyId" placeholder="按钥匙筛选" clearable style="width: 200px; margin-right: 10px;">
              <ElOption v-for="key in keys" :key="key.id" :label="key.keyCode" :value="key.id!" />
            </ElSelect>
            <ElSelect v-model="recordRoomId" placeholder="按休息室筛选" clearable style="width: 200px; margin-right: 10px;">
              <ElOption v-for="room in rooms" :key="room.id" :label="room.roomName" :value="room.id!" />
            </ElSelect>
            <ElSelect v-model="recordShipId" placeholder="按船舶筛选" clearable style="width: 200px; margin-right: 10px;">
              <ElOption v-for="ship in ships" :key="ship.id" :label="ship.shipName" :value="ship.id!" />
            </ElSelect>
            <ElButton type="primary" @click="loadRecords">查询</ElButton>
          </div>
          <ElTable :data="records" border style="width: 100%">
            <ElTableColumn prop="checkoutBatch" label="领取批次" min-width="170" />
            <ElTableColumn prop="keyCode" label="钥匙编号" width="100" />
            <ElTableColumn prop="roomCode" label="房间" width="90" />
            <ElTableColumn prop="shipCode" label="停靠船舶" width="100" />
            <ElTableColumn prop="holderName" label="持匙人" width="90" />
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <ElTag :type="(row as KeyCheckoutRecord).status === 'OUT' ? 'danger' : 'success'">
                  {{ (row as KeyCheckoutRecord).status === 'OUT' ? '未还' : '已还' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="operator" label="领取经办" width="100" />
            <ElTableColumn prop="checkoutTime" label="领取时间" min-width="160" />
            <ElTableColumn prop="returnOperator" label="归还经办" width="100" />
            <ElTableColumn prop="returnTime" label="归还时间" min-width="160" />
            <ElTableColumn prop="remark" label="备注" min-width="140" />
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <ElDialog :title="keyDialogEdit ? '编辑钥匙' : '登记钥匙'" v-model="keyDialogVisible">
      <ElForm :model="keyForm" label-width="100px">
        <ElFormItem label="钥匙编号" required>
          <ElInput v-model="keyForm.keyCode" />
        </ElFormItem>
        <ElFormItem label="钥匙名称" required>
          <ElInput v-model="keyForm.keyName" />
        </ElFormItem>
        <ElFormItem label="所属休息室" required>
          <ElSelect v-model="keyForm.roomId">
            <ElOption v-for="room in rooms" :key="room.id" :label="`${room.roomCode}（${room.roomName}）`" :value="room.id!" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="keyDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveKey">确定</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="领取钥匙" v-model="checkoutDialogVisible">
      <ElForm :model="checkoutForm" label-width="110px">
        <ElFormItem label="选择钥匙" required>
          <ElSelect v-model="checkoutForm.keyId" @change="onCheckoutKeyChange">
            <ElOption
              v-for="key in availableKeys"
              :key="key.id"
              :label="`${key.keyCode}（${key.keyName} / ${key.roomCode}）`"
              :value="key.id!"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="钥匙跟的房间">
          <span>{{ selectedCheckoutRoom ? `${selectedCheckoutRoom.roomCode}（${selectedCheckoutRoom.roomName}）` : '选择钥匙后自动带出' }}</span>
        </ElFormItem>
        <ElFormItem label="房间停靠船舶" required>
          <ElSelect v-model="checkoutForm.shipId">
            <ElOption v-for="ship in ships" :key="ship.id" :label="`${ship.shipCode}（${ship.shipName}）`" :value="ship.id!" />
          </ElSelect>
          <div style="color: #909399; font-size: 12px; line-height: 1.5;">
            默认带出房间当前停靠船舶；若房间已换船，领取将整单退回
          </div>
        </ElFormItem>
        <ElFormItem label="持匙人" required>
          <ElInput v-model="checkoutForm.holderName" />
        </ElFormItem>
        <ElFormItem label="经办值班员">
          <ElInput v-model="checkoutForm.operator" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="checkoutForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="checkoutDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleCheckout">确定领取</ElButton>
      </template>
    </ElDialog>

    <ElDialog title="归还钥匙" v-model="returnDialogVisible">
      <ElForm :model="returnForm" label-width="110px">
        <ElFormItem label="选择钥匙" required>
          <ElSelect v-model="returnForm.keyId">
            <ElOption
              v-for="key in occupiedKeys"
              :key="key.id"
              :label="`${key.keyCode}（${key.keyName} / 持匙人 ${key.holderName}）`"
              :value="key.id!"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="领取批次">
          <span>{{ selectedReturnKey?.checkoutBatch || '选择钥匙后自动带出' }}</span>
        </ElFormItem>
        <ElFormItem label="归还经办">
          <ElInput v-model="returnForm.operator" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput type="textarea" v-model="returnForm.remark" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="returnDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleReturn">确定归还</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style>
.key-blocked-message .el-message-box__message {
  white-space: pre-line;
  line-height: 1.6;
}
</style>
