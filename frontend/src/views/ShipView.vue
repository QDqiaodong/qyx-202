<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElMessage } from 'element-plus'
import ElSelectOption from 'element-plus'
import { shipApi, type Ship } from '@/api'

const ships = ref<Ship[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<Ship>({
  shipCode: '',
  shipName: '',
  status: 'DOCKED'
})

const shipTypes = ['货轮', '客轮', '油轮', '集装箱船', '渔船', '其他']
const statusOptions = [
  { label: '停靠中', value: 'DOCKED' },
  { label: '航行中', value: 'SAILING' },
  { label: '维修中', value: 'MAINTENANCE' }
]

const loadData = async () => {
  try {
    const res = await shipApi.list()
    ships.value = res.data.data
  } catch (error) {
    ElMessage.error('加载数据失败')
  }
}

const openDialog = (edit = false, data?: Ship) => {
  isEdit.value = edit
  if (edit && data) {
    form.value = { ...data }
  } else {
    form.value = {
      shipCode: '',
      shipName: '',
      status: 'DOCKED'
    }
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    if (isEdit.value && form.value.id) {
      await shipApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await shipApi.create(form.value)
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
    await shipApi.delete(id)
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
      添加船舶
    </ElButton>
    <ElTable :data="ships" border style="width: 100%">
      <ElTableColumn prop="shipCode" label="船舶编号" />
      <ElTableColumn prop="shipName" label="船舶名称" />
      <ElTableColumn prop="shipType" label="船舶类型" />
      <ElTableColumn prop="dockCode" label="停靠码头" />
      <ElTableColumn prop="status" label="状态">
        <template #default="{ row }">
          <span :class="{
            'text-green': (row as Ship).status === 'DOCKED',
            'text-blue': (row as Ship).status === 'SAILING',
            'text-orange': (row as Ship).status === 'MAINTENANCE'
          }">
            {{ (row as Ship).status === 'DOCKED' ? '停靠中' : (row as Ship).status === 'SAILING' ? '航行中' : '维修中' }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作">
        <template #default="{ row }">
          <ElButton size="small" @click="openDialog(true, row as Ship)">编辑</ElButton>
          <ElButton size="small" type="danger" @click="deleteItem((row as Ship).id!)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog :title="isEdit ? '编辑船舶' : '添加船舶'" v-model="dialogVisible">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="船舶编号" required>
          <ElInput v-model="form.shipCode" />
        </ElFormItem>
        <ElFormItem label="船舶名称" required>
          <ElInput v-model="form.shipName" />
        </ElFormItem>
        <ElFormItem label="船舶类型">
          <ElSelect v-model="form.shipType">
            <ElSelectOption v-for="type in shipTypes" :key="type" :label="type" :value="type" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="停靠码头">
          <ElInput v-model="form.dockCode" />
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
.text-blue {
  color: #409EFF;
}
.text-orange {
  color: #e6a23c;
}
.text-red {
  color: #f56c6c;
}
</style>