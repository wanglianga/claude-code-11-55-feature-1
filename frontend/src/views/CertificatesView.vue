<template>
  <div>
    <div class="page-card">
      <h3 class="page-title">可再生电力属性凭证（登记 · 注销 · 撤销 · 转让 · 重复裁决）</h3>
      <div class="toolbar">
        <el-select v-model="filterStatus" placeholder="凭证状态" style="width:140px" clearable @change="loadCerts">
          <el-option v-for="(v, k) in STATUS_MAPS.certificate" :key="k" :label="v[0]" :value="k" />
        </el-select>
        <el-button @click="loadCerts">查询</el-button>
        <el-button type="primary" @click="openRegister">登记凭证</el-button>
        <el-button @click="loadDuplicates">刷新重复主张</el-button>
        <el-badge :value="pendingDupCount" :hidden="pendingDupCount === 0" type="danger">
          <el-button :type="pendingDupCount ? 'warning' : ''" @click="dupVisible = true">重复上传裁决</el-button>
        </el-badge>
      </div>
      <el-table :data="certs" size="small" border v-loading="loading">
        <el-table-column prop="serialNo" label="序列号" width="150" />
        <el-table-column label="持有人" width="100">
          <template #default="{ row }">{{ store.plantName(row.holderPlantId) }}</template>
        </el-table-column>
        <el-table-column prop="region" label="交付区域" width="95" />
        <el-table-column prop="technology" label="技术属性" width="95" />
        <el-table-column prop="generationMonth" label="发电月份" width="90" />
        <el-table-column label="交付区间" width="190">
          <template #default="{ row }">{{ row.deliveryFrom }} ~ {{ row.deliveryTo }}</template>
        </el-table-column>
        <el-table-column label="电量(kWh)" width="110">
          <template #default="{ row }">{{ fmtNum(row.quantityKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="冻结(kWh)" width="100">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': row.frozenKwh > 0 }">{{ fmtNum(row.frozenKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag kind="certificate" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="注销" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.cancelled" size="small" type="info">已注销</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">凭证链</el-button>
            <el-button v-if="!row.cancelled && row.status === 'ACTIVE'" size="small" link
                       @click="openPool(row)">入池</el-button>
            <el-button v-if="!row.cancelled && row.status === 'ACTIVE'" size="small" link type="success"
                       @click="doCancel(row)">注销</el-button>
            <el-button v-if="row.status !== 'REVOKED' && !row.cancelled" size="small" link type="danger"
                       @click="openRevoke(row)">撤销</el-button>
            <el-button v-if="row.status === 'ACTIVE'" size="small" link type="warning"
                       @click="openTransfer(row)">转让</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <h3 class="page-title">可互换批次池（同区域 + 同发电月份 + 同技术属性混池，成员逐张留痕）</h3>
      <el-table :data="pools" size="small" border>
        <el-table-column prop="poolNo" label="池编号" width="160" />
        <el-table-column prop="region" label="区域" width="100" />
        <el-table-column prop="generationMonth" label="发电月份" width="100" />
        <el-table-column prop="technology" label="技术属性" width="100" />
        <el-table-column label="入池总量(kWh)" width="130">
          <template #default="{ row }">{{ fmtNum(row.totalKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="可扣余额(kWh)" width="130">
          <template #default="{ row }">
            <span class="diff-neg">{{ fmtNum(row.availableKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成员凭证" min-width="260">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openPoolDetail(row)">查看成员明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 登记对话框 -->
    <el-dialog v-model="reg.visible" title="登记可再生电力属性凭证" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="序列号" required>
          <el-input v-model="reg.form.serialNo" placeholder="全局唯一，重复上传将触发冻结裁决" />
        </el-form-item>
        <el-form-item label="持有工厂" required>
          <el-select v-model="reg.form.holderPlantId" style="width:260px">
            <el-option v-for="p in store.plants" :key="p.id" :label="`${p.name}（${p.region}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="交付区域" required>
          <el-input v-model="reg.form.region" placeholder="如 CN-EAST / CN-WEST" />
        </el-form-item>
        <el-form-item label="技术属性" required>
          <el-select v-model="reg.form.technology" style="width:260px">
            <el-option v-for="t in technologies" :key="t.code" :label="`${t.label}（${t.code}）`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="发电月份" required>
          <el-input v-model="reg.form.generationMonth" placeholder="YYYY-MM" />
        </el-form-item>
        <el-form-item label="交付区间" required>
          <el-date-picker v-model="reg.deliveryRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="起" end-placeholder="止" />
        </el-form-item>
        <el-form-item label="凭证电量(kWh)" required>
          <el-input-number v-model="reg.form.quantityKwh" :min="1" :step="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRegister">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- 凭证链抽屉 -->
    <el-drawer v-model="detailVisible" size="62%" :title="`凭证链与变更留痕：${current?.serialNo || ''}`">
      <template v-if="currentDetail">
        <el-descriptions :column="3" border size="small" class="mb12">
          <el-descriptions-item label="可声明余额(kWh)">
            <b>{{ fmtNum(currentDetail.availableKwh, 0) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag kind="certificate" :value="currentDetail.certificate.status" />
          </el-descriptions-item>
          <el-descriptions-item label="失效原因">{{ currentDetail.certificate.invalidReason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>声明链（逐张/池扣减共用）</h4>
        <el-table :data="currentDetail.allocations" size="small" border class="mb12">
          <el-table-column label="用电账" width="90">
            <template #default="{ row }">#{{ row.allocation.ledgerId }}</template>
          </el-table-column>
          <el-table-column label="方式" width="100">
            <template #default="{ row }"><StatusTag kind="claimMethod" :value="row.allocation.method" /></template>
          </el-table-column>
          <el-table-column label="声明量(kWh)" width="110">
            <template #default="{ row }">{{ fmtNum(row.allocation.allocatedKwh, 0) }}</template>
          </el-table-column>
          <el-table-column label="匹配" width="100">
            <template #default="{ row }"><StatusTag kind="match" :value="row.allocation.matchStatus" /></template>
          </el-table-column>
          <el-table-column label="链有效性" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.allocation.chainValid ? 'success' : 'danger'">
                {{ row.allocation.chainValid ? '有效' : '后续失效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="失效原因/报告版本" min-width="200">
            <template #default="{ row }">
              {{ row.allocation.invalidatedReason || '-' }}
              <span v-if="row.allocation.reportVersionId">（报告版本 #{{ row.allocation.reportVersionId }}）</span>
            </template>
          </el-table-column>
        </el-table>
        <h4>持有人/状态变更留痕</h4>
        <el-timeline>
          <el-timeline-item v-for="e in currentDetail.events" :key="e.id"
                            :timestamp="fmtTime(e.createdAt)" placement="top">
            <StatusTag kind="certEvent" :value="e.eventType" />
            <span style="margin-left:8px">{{ e.detail }}</span>
            <div class="hint">操作人：{{ e.operator }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 池成员抽屉 -->
    <el-drawer v-model="poolDetailVisible" size="55%" :title="`批次池成员：${poolCurrent?.poolNo || ''}`">
      <el-table v-if="poolCurrent" :data="poolMembers" size="small" border>
        <el-table-column label="凭证序列号" min-width="150">
          <template #default="{ row }">{{ row.certificate?.serialNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag kind="certificate" :value="row.certificate?.status" /></template>
        </el-table-column>
        <el-table-column label="入池量" width="110">
          <template #default="{ row }">{{ fmtNum(row.member.joinedKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="已扣减" width="110">
          <template #default="{ row }">{{ fmtNum(row.member.usedKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="冻结" width="110">
          <template #default="{ row }">{{ fmtNum(row.member.frozenKwh, 0) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 重复裁决抽屉 -->
    <el-drawer v-model="dupVisible" size="55%" title="重复上传裁决（同序列号多工厂竞争）">
      <el-table :data="duplicates" size="small" border>
        <el-table-column prop="serialNo" label="序列号" width="140" />
        <el-table-column label="竞争工厂" width="100">
          <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
        </el-table-column>
        <el-table-column label="上传量" width="100">
          <template #default="{ row }">{{ fmtNum(row.quantityKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="冻结未决" width="100">
          <template #default="{ row }">
            <span class="diff-pos">{{ fmtNum(row.contestedKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag kind="duplicate" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="裁决" width="200">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" type="success" @click="resolve(row, 'FIRST')">首张有效</el-button>
              <el-button size="small" type="warning" @click="resolve(row, 'DUPLICATE')">后传有效</el-button>
            </template>
            <span v-else>{{ row.resolution }}（{{ row.resolvedBy }}）</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { store, STATUS_MAPS, fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const loading = ref(false)
const certs = ref([])
const pools = ref([])
const technologies = ref([])
const filterStatus = ref('')
const duplicates = ref([])
const dupVisible = ref(false)
const detailVisible = ref(false)
const poolDetailVisible = ref(false)
const current = ref(null)
const currentDetail = ref(null)
const poolCurrent = ref(null)
const poolMembers = ref([])

const pendingDupCount = computed(() => duplicates.value.filter(d => d.status === 'PENDING').length)

const reg = reactive({
  visible: false,
  deliveryRange: [],
  form: { serialNo: '', holderPlantId: null, region: '', technology: '', generationMonth: '', quantityKwh: 100000 }
})

async function loadCerts() {
  loading.value = true
  try {
    const params = {}
    if (filterStatus.value) params.status = filterStatus.value
    certs.value = (await api.get('/certificates', { params })).data
  } finally {
    loading.value = false
  }
}
async function loadPools() {
  pools.value = (await api.get('/claims/pools')).data
}
async function loadDuplicates() {
  duplicates.value = (await api.get('/certificates/duplicates')).data
}

function openRegister() {
  reg.form = { serialNo: '', holderPlantId: store.plants[0]?.id, region: store.plants[0]?.region || '',
    technology: 'SOLAR', generationMonth: '2025-06', quantityKwh: 100000 }
  reg.deliveryRange = ['2025-06-01', '2025-06-30']
  reg.visible = true
}

async function submitRegister() {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(reg.form.generationMonth)) {
    ElMessage.warning('发电月份格式必须为 YYYY-MM')
    return
  }
  const body = { ...reg.form, deliveryFrom: reg.deliveryRange[0], deliveryTo: reg.deliveryRange[1] }
  const resp = await api.post('/certificates', body)
  if (resp.data.duplicate) {
    ElMessage.warning(`重复上传已识别：冻结未决 ${resp.data.contestedKwh} kWh，请到"重复上传裁决"处理`)
  } else {
    ElMessage.success('凭证登记成功')
  }
  reg.visible = false
  loadCerts(); loadDuplicates()
}

async function openDetail(row) {
  current.value = row
  currentDetail.value = (await api.get(`/certificates/${row.id}`)).data
  detailVisible.value = true
}

async function doCancel(row) {
  await ElMessageBox.confirm(`确认注销凭证 ${row.serialNo}？已用于声明的凭证不能注销。`, '注销确认', { type: 'warning' })
  await api.post(`/certificates/${row.id}/cancel`)
  ElMessage.success('凭证已注销')
  loadCerts()
}

function openRevoke(row) {
  ElMessageBox.prompt('请输入撤销原因（撤销后新声明停用，既有声明链保留并标记失效）', `撤销凭证 ${row.serialNo}`,
    { confirmButtonText: '确认撤销', cancelButtonText: '取消', inputPattern: /\S+/, inputErrorMessage: '原因不能为空' })
    .then(async ({ value }) => {
      await api.post(`/certificates/${row.id}/revoke`, { reason: value })
      ElMessage.success('凭证已撤销，相关声明链已标记后续失效')
      loadCerts()
    }).catch(() => {})
}

function openTransfer(row) {
  ElMessageBox.prompt(`输入受让工厂 ID（可用工厂：${store.plants.map(p => p.id + '=' + p.name).join('，')}）与说明，格式 工厂ID|说明`,
    `转让凭证 ${row.serialNo}`, { confirmButtonText: '确认转让', cancelButtonText: '取消', inputPlaceholder: '2|集团内部调拨' })
    .then(async ({ value }) => {
      const [pid, reason] = value.split('|')
      await api.post(`/certificates/${row.id}/transfer`, { toPlantId: Number(pid), reason })
      ElMessage.success('凭证已转让，原持有人声明链已标记失效')
      loadCerts()
    }).catch(() => {})
}

async function openPool(row) {
  await api.post('/claims/pools/join', { certificateId: row.id })
  ElMessage.success('凭证已入对应区域/月份/技术属性批次池')
  loadPools()
}

async function openPoolDetail(row) {
  const d = (await api.get(`/claims/pools/${row.id}`)).data
  poolCurrent.value = d.pool
  poolMembers.value = d.members
  poolDetailVisible.value = true
}

async function resolve(row, winner) {
  const label = winner === 'FIRST' ? '首张凭证有效，竞争主张作废' : '后传工厂胜诉，凭证持有人变更且原链失效'
  const { value: reason } = await ElMessageBox.prompt(`裁决理由（${label}）`, '重复上传裁决',
    { inputPattern: /\S+/, inputErrorMessage: '理由不能为空' })
  await api.post(`/certificates/duplicates/${row.id}/resolve`, { winner, reason })
  ElMessage.success('裁决完成')
  loadDuplicates(); loadCerts()
}

onMounted(async () => {
  await store.loadMeta()
  technologies.value = (await api.get('/certificates/technologies')).data
  loadCerts(); loadPools(); loadDuplicates()
})
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
.mb12 { margin-bottom: 14px; }
</style>
