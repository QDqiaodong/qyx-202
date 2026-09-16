<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElSelect, ElOption,
  ElTag, ElTable, ElTableColumn, ElMessage, ElMessageBox, ElEmpty, ElDatePicker
} from 'element-plus'
import {
  fuelApi,
  type Generator,
  type GeneratorFuelState,
  type FuelRefill,
  type FuelBlocked
} from '@/api'

const generators = ref<GeneratorFuelState[]>([])
const records = ref<FuelRefill[]>([])
const loading = ref(false)

const recordFilterGenerator = ref<number | undefined>(undefined)
const recordFilterDate = ref<string>('')

const refillDialogVisible = ref(false)
const reviewDialogVisible = ref(false)
const generatorDialogVisible = ref(false)
const saving = ref(false)

const refillGenerator = ref<GeneratorFuelState | null>(null)
const refillForm = ref<{ canNo: string; liters: number | null; dutyOfficer: string; dutyShift: string; remark: string }>({
  canNo: '',
  liters: null,
  dutyOfficer: '',
  dutyShift: 'DAY',
  remark: ''
})

const reviewTarget = ref<GeneratorFuelState | null>(null)
const reviewForm = ref<{ reviewerName: string; reviewerShift: string; reviewComment: string }>({
  reviewerName: '',
  reviewerShift: 'DAY',
  reviewComment: ''
})

const generatorForm = ref<Generator>({ genCode: '', genName: '', location: '', fuelStockLiters: 0, remark: '' })

const shiftOptions = [
  { value: 'DAY', label: '早班' },
  { value: 'MIDDLE', label: '中班' },
  { value: 'NIGHT', label: '夜班' }
]
const shiftText = (shift?: string) => shiftOptions.find(s => s.value === shift)?.label || shift || '—'

const pendingCount = computed(() => generators.value.filter(g => g.todayState === 'PENDING_REVIEW').length)
const reviewedCount = computed(() => generators.value.filter(g => g.todayState === 'REVIEWED').length)

const stateTag = (g: GeneratorFuelState): { type: 'success' | 'warning' | 'info'; text: string } => {
  switch (g.todayState) {
    case 'REVIEWED':
      return { type: 'success', text: '已加油' }
    case 'PENDING_REVIEW':
      return { type: 'warning', text: '待复核' }
    default:
      return { type: 'info', text: '未加油' }
  }
}

const fmtLiters = (v?: number | null) => (v === undefined || v === null ? '—' : Number(v).toFixed(2))

const loadData = async () => {
  loading.value = true
  try {
    const [stateRes, recordRes] = await Promise.all([
      fuelApi.generatorStates(),
      fuelApi.records(recordFilterGenerator.value, recordFilterDate.value || undefined)
    ])
    generators.value = stateRes.data.data || []
    records.value = recordRes.data.data || []
  } catch (error) {
    ElMessage.error('加载加油数据失败')
  } finally {
    loading.value = false
  }
}

const loadRecords = async () => {
  try {
    const res = await fuelApi.records(recordFilterGenerator.value, recordFilterDate.value || undefined)
    records.value = res.data.data || []
  } catch (error) {
    ElMessage.error('加载加油流水失败')
  }
}

// ---- 登记加油 ----
const openRefill = (g: GeneratorFuelState) => {
  refillGenerator.value = g
  refillForm.value = { canNo: '', liters: null, dutyOfficer: '', dutyShift: 'DAY', remark: '' }
  refillDialogVisible.value = true
}

const submitRefill = async () => {
  if (!refillGenerator.value) return
  if (!refillForm.value.canNo.trim()) {
    ElMessage.warning('本罐编号不能为空')
    return
  }
  if (!refillForm.value.liters || refillForm.value.liters <= 0) {
    ElMessage.warning('实加升数必须大于 0')
    return
  }
  if (!refillForm.value.dutyOfficer.trim()) {
    ElMessage.warning('经办值班不能为空')
    return
  }
  saving.value = true
  try {
    await fuelApi.create({
      generatorId: refillGenerator.value.generatorId,
      canNo: refillForm.value.canNo.trim(),
      liters: refillForm.value.liters,
      dutyOfficer: refillForm.value.dutyOfficer.trim(),
      dutyShift: refillForm.value.dutyShift,
      remark: refillForm.value.remark?.trim() || undefined
    })
    ElMessage.success('登记成功，等待另一个班的人复核')
    refillDialogVisible.value = false
    await loadData()
  } catch (error: any) {
    const data = error.response?.data
    const blocked: FuelBlocked | undefined = data?.data
    const message: string = data?.message || '登记失败'
    const lines: string[] = [message]
    if (blocked?.existingRefillNo) {
      lines.push('', '已在库未复核的那条：')
      lines.push(`单号：${blocked.existingRefillNo}`)
      lines.push(`本罐编号：${blocked.canNo || '—'} ｜ 实加升数：${fmtLiters(blocked.liters)} 升`)
      lines.push(`经办值班：${blocked.dutyOfficer || '—'}（${shiftText(blocked.dutyShift)}）`)
      lines.push('', '复核通过之后，这台机今天才能再开下一条。')
    }
    try {
      await ElMessageBox.alert(lines.join('\n'), '登记被退回', { type: 'error', confirmButtonText: '知道了' })
    } catch {
      // 用户关闭弹窗
    }
  } finally {
    saving.value = false
  }
}

// ---- 复核 ----
const openReview = (g: GeneratorFuelState) => {
  reviewTarget.value = g
  reviewForm.value = { reviewerName: '', reviewerShift: 'DAY', reviewComment: '' }
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (!reviewTarget.value?.pendingRefillId) return
  if (!reviewForm.value.reviewerName.trim()) {
    ElMessage.warning('复核人不能为空')
    return
  }
  saving.value = true
  try {
    await fuelApi.review(reviewTarget.value.pendingRefillId, {
      reviewerName: reviewForm.value.reviewerName.trim(),
      reviewerShift: reviewForm.value.reviewerShift,
      reviewComment: reviewForm.value.reviewComment?.trim() || undefined
    })
    ElMessage.success('复核通过，实加升数已进库存，这台机今天可再开下一条')
    reviewDialogVisible.value = false
    await loadData()
  } catch (error: any) {
    const data = error.response?.data
    const blocked: FuelBlocked | undefined = data?.data
    const message: string = data?.message || '复核失败'
    const lines: string[] = [message]
    if (blocked?.reason === 'ALREADY_REVIEWED') {
      lines.push('', '这条已经核过，先写完的那次结论：')
      lines.push(`复核人：${blocked.reviewerName || '—'}（${shiftText(blocked.reviewerShift)}）`)
      lines.push(`复核时间：${blocked.reviewTime || '—'}`)
      lines.push('后到的复核不再写入。')
    }
    try {
      await ElMessageBox.alert(lines.join('\n'), '复核未写入', { type: 'error', confirmButtonText: '知道了' })
    } catch {
      // 用户关闭弹窗
    }
    await loadData()
  } finally {
    saving.value = false
  }
}

// ---- 发电机建档 ----
const openGeneratorDialog = () => {
  generatorForm.value = { genCode: '', genName: '', location: '', fuelStockLiters: 0, remark: '' }
  generatorDialogVisible.value = true
}

const submitGenerator = async () => {
  if (!generatorForm.value.genCode?.trim() || !generatorForm.value.genName?.trim()) {
    ElMessage.warning('机号和名称不能为空')
    return
  }
  saving.value = true
  try {
    await fuelApi.createGenerator({
      ...generatorForm.value,
      genCode: generatorForm.value.genCode.trim(),
      genName: generatorForm.value.genName.trim()
    })
    ElMessage.success('发电机建档成功')
    generatorDialogVisible.value = false
    await loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '建档失败')
  } finally {
    saving.value = false
  }
}

const recordStatusText = (status: string) => (status === 'REVIEWED' ? '已复核' : '待复核')
const recordStatusType = (status: string): 'success' | 'warning' => (status === 'REVIEWED' ? 'success' : 'warning')

onMounted(loadData)
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <ElButton @click="loadData">刷新</ElButton>
      <ElButton type="primary" plain @click="openGeneratorDialog">发电机建档</ElButton>
      <span class="summary">
        <ElTag type="warning" effect="light">待复核 {{ pendingCount }} 台</ElTag>
        <ElTag type="success" effect="light" style="margin-left: 8px;">今日已加油 {{ reviewedCount }} 台</ElTag>
      </span>
    </div>

    <!-- 发电机列表：已加油这一列背后带得出今天已核升数和复核人 -->
    <ElTable :data="generators" border size="small" style="width: 100%">
      <ElTableColumn prop="genCode" label="机号" width="130" />
      <ElTableColumn prop="genName" label="名称" min-width="150" />
      <ElTableColumn prop="location" label="位置" width="110">
        <template #default="{ row }">{{ (row as GeneratorFuelState).location || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="库存升数" width="100" align="right">
        <template #default="{ row }">{{ fmtLiters((row as GeneratorFuelState).fuelStockLiters) }}</template>
      </ElTableColumn>
      <ElTableColumn label="已加油" width="100">
        <template #default="{ row }">
          <ElTag :type="stateTag(row as GeneratorFuelState).type" size="small">
            {{ stateTag(row as GeneratorFuelState).text }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="今日已核升数" width="110" align="right">
        <template #default="{ row }">{{ fmtLiters((row as GeneratorFuelState).todayReviewedLiters) }}</template>
      </ElTableColumn>
      <ElTableColumn label="复核人" width="130">
        <template #default="{ row }">
          <span v-if="(row as GeneratorFuelState).todayReviewerName">
            {{ (row as GeneratorFuelState).todayReviewerName }}（{{ shiftText((row as GeneratorFuelState).todayReviewerShift) }}）
          </span>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="待复核单" min-width="180">
        <template #default="{ row }">
          <span v-if="(row as GeneratorFuelState).pendingRefillNo" class="pending-text">
            {{ (row as GeneratorFuelState).pendingRefillNo }} ｜ 罐 {{ (row as GeneratorFuelState).pendingCanNo }}
            ｜ {{ fmtLiters((row as GeneratorFuelState).pendingLiters) }} 升
            ｜ {{ (row as GeneratorFuelState).pendingDutyOfficer }}（{{ shiftText((row as GeneratorFuelState).pendingDutyShift) }}）
          </span>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <ElButton
            v-if="(row as GeneratorFuelState).todayState === 'PENDING_REVIEW'"
            type="primary"
            size="small"
            @click="openReview(row as GeneratorFuelState)"
          >复核</ElButton>
          <ElButton
            v-else
            type="primary"
            size="small"
            plain
            @click="openRefill(row as GeneratorFuelState)"
          >登记加油</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElEmpty v-if="generators.length === 0" description="还没有发电机档案，请先建档" />

    <div class="records">
      <div class="records-head">
        <span class="records-title">加油流水（升数、罐号、经办、复核人都可查）</span>
        <span class="records-filter">
          <ElSelect v-model="recordFilterGenerator" placeholder="全部发电机" clearable size="small" style="width: 180px" @change="loadRecords">
            <ElOption v-for="g in generators" :key="g.generatorId" :value="g.generatorId" :label="`${g.genCode} ${g.genName || ''}`" />
          </ElSelect>
          <ElDatePicker
            v-model="recordFilterDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="全部日期"
            size="small"
            style="width: 150px; margin-left: 8px;"
            @change="loadRecords"
          />
        </span>
      </div>
      <ElTable :data="records" border size="small" style="width: 100%">
        <ElTableColumn prop="refillNo" label="加油单号" width="190" />
        <ElTableColumn prop="genCode" label="机号" width="120" />
        <ElTableColumn prop="refillDate" label="日期" width="105" />
        <ElTableColumn prop="canNo" label="本罐编号" width="120" />
        <ElTableColumn label="实加升数" width="90" align="right">
          <template #default="{ row }">{{ fmtLiters((row as FuelRefill).liters) }}</template>
        </ElTableColumn>
        <ElTableColumn label="经办值班" width="130">
          <template #default="{ row }">
            {{ (row as FuelRefill).dutyOfficer }}（{{ shiftText((row as FuelRefill).dutyShift) }}）
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="recordStatusType((row as FuelRefill).status)" size="small">
              {{ recordStatusText((row as FuelRefill).status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="复核人" width="130">
          <template #default="{ row }">
            <span v-if="(row as FuelRefill).reviewerName">
              {{ (row as FuelRefill).reviewerName }}（{{ shiftText((row as FuelRefill).reviewerShift) }}）
            </span>
            <span v-else>—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="reviewTime" label="复核时间" min-width="160">
          <template #default="{ row }">{{ (row as FuelRefill).reviewTime || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="库存变化(升)" width="140" align="right">
          <template #default="{ row }">
            <span v-if="(row as FuelRefill).stockAfterLiters != null">
              {{ fmtLiters((row as FuelRefill).stockBeforeLiters) }} → {{ fmtLiters((row as FuelRefill).stockAfterLiters) }}
            </span>
            <span v-else>—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="reviewComment" label="复核意见" min-width="140">
          <template #default="{ row }">{{ (row as FuelRefill).reviewComment || '—' }}</template>
        </ElTableColumn>
      </ElTable>
      <ElEmpty v-if="records.length === 0" description="还没有加油登记" />
    </div>

    <!-- 登记加油 -->
    <ElDialog :title="`登记加油 · ${refillGenerator?.genCode || ''} ${refillGenerator?.genName || ''}`" v-model="refillDialogVisible" width="480px">
      <ElForm label-width="110px">
        <ElFormItem label="发电机">
          <span>{{ refillGenerator?.genCode }} · {{ refillGenerator?.genName }}（{{ refillGenerator?.location || '—' }}）</span>
        </ElFormItem>
        <ElFormItem label="本罐编号" required>
          <ElInput v-model="refillForm.canNo" placeholder="哪一罐油，如 CAN-2026-091" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="实加升数" required>
          <ElInputNumber v-model="refillForm.liters" :min="0.01" :step="1" :precision="2" style="width: 100%" placeholder="实际加进去的升数" />
        </ElFormItem>
        <ElFormItem label="经办值班" required>
          <ElInput v-model="refillForm.dutyOfficer" placeholder="值班人姓名" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="经办班次" required>
          <ElSelect v-model="refillForm.dutyShift" style="width: 100%">
            <ElOption v-for="s in shiftOptions" :key="s.value" :value="s.value" :label="s.label" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="refillForm.remark" type="textarea" :rows="2" maxlength="500" />
        </ElFormItem>
        <div class="hint">
          登记后这条加油「待复核」，库存升数暂不变化；同一台机同一天只允许一条待复核，
          由另一个班的人复核通过后，升数才进库存，当天才能再开下一条。
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="refillDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitRefill">登记</ElButton>
      </template>
    </ElDialog>

    <!-- 复核 -->
    <ElDialog :title="`复核加油 · ${reviewTarget?.pendingRefillNo || ''}`" v-model="reviewDialogVisible" width="480px">
      <ElForm label-width="110px">
        <ElFormItem label="发电机">
          <span>{{ reviewTarget?.genCode }} · {{ reviewTarget?.genName }}</span>
        </ElFormItem>
        <ElFormItem label="待复核单">
          <span>
            罐 {{ reviewTarget?.pendingCanNo }} ｜ {{ fmtLiters(reviewTarget?.pendingLiters) }} 升
            ｜ 经办 {{ reviewTarget?.pendingDutyOfficer }}（{{ shiftText(reviewTarget?.pendingDutyShift) }}）
          </span>
        </ElFormItem>
        <ElFormItem label="复核人" required>
          <ElInput v-model="reviewForm.reviewerName" placeholder="不能是经办本人" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="复核人班次" required>
          <ElSelect v-model="reviewForm.reviewerShift" style="width: 100%">
            <ElOption v-for="s in shiftOptions" :key="s.value" :value="s.value" :label="s.label" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="复核意见">
          <ElInput v-model="reviewForm.reviewComment" type="textarea" :rows="2" maxlength="500" placeholder="选填，如罐号与油枪读数核对情况" />
        </ElFormItem>
        <div class="hint">
          复核人必须是另一个班的人，自己加的不能自己核。
          复核通过即把实加升数加进库存（同事务，失败不留半截）；
          两人同时复核，只留先写完的那次结论。
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="reviewDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitReview">复核通过</ElButton>
      </template>
    </ElDialog>

    <!-- 发电机建档 -->
    <ElDialog title="发电机建档" v-model="generatorDialogVisible" width="460px">
      <ElForm label-width="110px">
        <ElFormItem label="机号" required>
          <ElInput v-model="generatorForm.genCode" placeholder="如 GEN-ROOF-01" maxlength="50" />
        </ElFormItem>
        <ElFormItem label="名称" required>
          <ElInput v-model="generatorForm.genName" placeholder="如 码头楼顶应急发电机" maxlength="100" />
        </ElFormItem>
        <ElFormItem label="位置">
          <ElInput v-model="generatorForm.location" placeholder="如 码头楼顶" maxlength="100" />
        </ElFormItem>
        <ElFormItem label="期初库存(升)">
          <ElInputNumber v-model="generatorForm.fuelStockLiters" :min="0" :step="1" :precision="2" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="generatorForm.remark" type="textarea" :rows="2" maxlength="500" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="generatorDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitGenerator">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.summary {
  display: inline-flex;
  align-items: center;
}
.pending-text {
  color: #e6a23c;
}
.records {
  margin-top: 24px;
}
.records-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.records-title {
  font-weight: bold;
  color: #303133;
}
.hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  padding: 0 8px;
}
</style>
