<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElMessage, ElInputNumber } from 'element-plus'
import ElSelectOption from 'element-plus'
import { roomApi, type LoungeRoom } from '@/api'

const rooms = ref<LoungeRoom[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<LoungeRoom>({
  roomCode: '',
  roomName: '',
  status: 'ACTIVE'
})

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' }
]

const loadData = async () => {
  try {
    const res = await roomApi.list()
    rooms.value = res.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
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
      status: 'ACTIVE'
    }
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
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
    <ElTable :data="rooms" border style="width: 100%">
      <ElTableColumn prop="roomCode" label="房间编号" />
      <ElTableColumn prop="roomName" label="房间名称" />
      <ElTableColumn prop="floor" label="楼层" />
      <ElTableColumn prop="capacity" label="容纳人数" />
      <ElTableColumn prop="status" label="状态">
        <template #default="{ row }">
          <span :class="(row as LoungeRoom).status === 'ACTIVE' ? 'text-green' : 'text-red'">
            {{ (row as LoungeRoom).status === 'ACTIVE' ? '启用' : '停用' }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="shipCode" label="绑定船舶" />
      <ElTableColumn prop="shipName" label="船舶名称" />
      <ElTableColumn prop="changeBatch" label="最近换班批次" min-width="180" />
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openDialog(true, row as LoungeRoom)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="deleteItem((row as LoungeRoom).id!)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑休息室' : '添加休息室'" v-model="dialogVisible">
      <ElForm :model="form" label-width="100px">
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
</style>