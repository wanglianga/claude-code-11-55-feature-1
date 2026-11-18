<template>
  <div>
    <div class="page-card">
      <h3 class="page-title">用电账（工厂 × 计量期间的外购用电事实）</h3>
      <div class="toolbar">
        <el-input v-model="filterPeriod" placeholder="期间 YYYY-MM" style="width:140px" clearable />
        <el-button type="primary" @click="loadAll">查询</el-button>
        <el-button @click="openLedger">登记用电账</el-button>
        <el-button @click="openSync">从已核验电力批次生成</el-button>
      </div>
      <el-table :data="ledgers" size="small" border v-loading="loading" @row-click="openLedgerDetail">
        <el-table-column label="工厂" width="120">
          <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
        </el-table-column>
        <el-table-column prop="period" label="计量期间" width="90" />
        <el-table-column prop="region" label="供电区域" width="100" />
        <el-table-column label="用电总量(kWh)" width="130">
          <template #default="{ row }">{{ fmtNum(row.totalKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="已声明覆盖(kWh)" width="140">
          <template #default="{ row }">
            <span class="diff-neg">{{ fmtNum(row.coveredKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="覆盖率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="coverPct(row)" :stroke-width="12" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click.stop="openLedgerDetail(row)">凭证链/声明册</el-button>
            <el-button size="small" link type="success" @click.stop="openAllocate(row)">归属声明</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <h3 class="page-title">声明册（可匹配量 / 不可匹配量 / 剩余量 / 重复主张控制）</h3>
      <el-table :data="statements" size="small" border>
        <el-table-column label="工厂" width="120">
          <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
        </el-table-column>
        <el-table-column prop="period" label="期间" width="85" />
        <el-table-column prop="region" label="区域" width="95" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag kind="statement" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="用电总量" width="110">
          <template #default="{ row }">{{ fmtNum(row.totalKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="可匹配量" width="110">
          <template #default="{ row }">
            <span class="diff-neg">{{ fmtNum(row.matchedKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不可匹配量" width="110">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': row.unmatchedKwh > 0 }">{{ fmtNum(row.unmatchedKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余量" width="110">
          <template #default="{ row }">{{ fmtNum(row.residualKwh, 0) }}</template>
        </el-table-column>
        <el-table-column label="重复主张冻结" width="120">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': row.duplicateClaimKwh > 0 }">{{ fmtNum(row.duplicateClaimKwh, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事后失效链" width="110">
          <template #default="{ row }">
            <span :class="{ 'diff-pos': row.invalidatedChainKwh > 0 }">{{ fmtNum(row.invalidatedChainKwh, 0) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">
        恒等式：用电总量 = 可匹配量 + 剩余量；不可匹配量（跨区/错月/冻结未决）单独列示，不得抵减剩余用电，
        凭证总量不能直接从集团排放中扣除。
      </p>
    </div>

    <!-- 登记用电账 -->
    <el-dialog v-model="ledgerDlg.visible" title="登记用电账" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="工厂" required>
          <el-select v-model="ledgerDlg.form.plantId" style="width:260px" @change="onLedgerPlant">
            <el-option v-for="p in store.plants" :key="p.id" :label="`${p.name}（${p.region}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量期间" required>
          <el-input v-model="ledgerDlg.form.period" placeholder="YYYY-MM" />
        </el-form-item>
        <el-form-item label="供电区域">
          <el-input v-model="ledgerDlg.form.region" placeholder="默认取工厂区域" />
        </el-form-item>
        <el-form-item label="用电总量(kWh)" required>
          <el-input-number v-model="ledgerDlg.form.totalKwh" :min="1" :step="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ledgerDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitLedger">提交</el-button>
      </template>
    </el-dialog>

    <!-- 从批次生成 -->
    <el-dialog v-model="syncDlg" title="从已核验电力批次生成用电账" width="420px" destroy-on-close>
      <el-input v-model="syncPeriod" placeholder="期间 YYYY-MM" />
      <template #footer>
        <el-button @click="syncDlg = false">取消</el-button>
        <el-button type="primary" @click="doSync">生成（已存在的工厂期间跳过）</el-button>
      </template>
    </el-dialog>

    <!-- 归属对话框 -->
    <el-dialog v-model="allocDlg.visible" title="凭证逐期归属声明" width="640px" destroy-on-close>
      <el-radio-group v-model="allocDlg.form.method" class="mb12">
        <el-radio-button label="CERTIFICATE">逐张分配（精确使用链/支持拆分）</el-radio-button>
        <el-radio-button label="POOL">批次池扣减（混池/成员留痕）</el-radio-button>
      </el-radio-group>
      <el-form label-width="120px">
        <el-form-item v-if="allocDlg.form.method === 'CERTIFICATE'" label="选择凭证" required>
          <el-select v-model="allocDlg.form.certificateId" filterable style="width:420px">
            <el-option v-for="c in holderCerts" :key="c.id"
                       :label="`${c.serialNo}｜${c.region}｜${c.technology}｜发电${c.generationMonth}｜余额${c.availableKwh ?? c.quantityKwh}kWh｜${c.status}`"
                       :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="选择批次池" required>
          <el-select v-model="allocDlg.form.poolId" style="width:420px">
            <el-option v-for="p in pools" :key="p.id"
                       :label="`${p.poolNo}｜${p.region}｜${p.technology}｜${p.generationMonth}｜余额${p.availableKwh}kWh`"
                       :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="声明电量(kWh)">
          <el-input-number v-model="allocDlg.form.quantityKwh" :min="1" :step="10000" />
          <span class="hint">留空按可匹配余额与剩余用电自动取最小值</span>
        </el-form-item>
      </el-form>
      <el-alert v-for="(w, i) in allocDlg.warnings" :key="i" :title="w" type="warning" :closable="false" class="mb12" />
      <template #footer>
        <el-button @click="allocDlg.visible = false">关闭</el-button>
        <el-button type="primary" @click="submitAllocate">执行归属</el-button>
      </template>
    </el-dialog>

    <!-- 用电账详情抽屉 -->
    <el-drawer v-model="detailVisible" size="66%" :title="`用电账与凭证链 #${currentLedger?.id || ''}`">
      <template v-if="ledgerDetail">
        <el-descriptions :column="3" border size="small" class="mb12">
          <el-descriptions-item label="工厂">{{ store.plantName(ledgerDetail.ledger.plantId) }}</el-descriptions-item>
          <el-descriptions-item label="期间">{{ ledgerDetail.ledger.period }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ ledgerDetail.ledger.region }}</el-descriptions-item>
          <el-descriptions-item label="用电总量">{{ fmtNum(ledgerDetail.ledger.totalKwh, 0) }} kWh</el-descriptions-item>
          <el-descriptions-item label="已覆盖">{{ fmtNum(ledgerDetail.ledger.coveredKwh, 0) }} kWh</el-descriptions-item>
          <el-descriptions-item label="剩余">
            {{ fmtNum(ledgerDetail.ledger.totalKwh - ledgerDetail.ledger.coveredKwh, 0) }} kWh
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="ledgerDetail.statement" class="mb12">
          <el-tag type="success">可匹配 {{ fmtNum(ledgerDetail.statement.matchedKwh, 0) }}</el-tag>
          <el-tag type="danger" style="margin-left:8px">不可匹配 {{ fmtNum(ledgerDetail.statement.unmatchedKwh, 0) }}</el-tag>
          <el-tag type="info" style="margin-left:8px">剩余 {{ fmtNum(ledgerDetail.statement.residualKwh, 0) }}</el-tag>
          <el-tag type="warning" style="margin-left:8px">重复冻结 {{ fmtNum(ledgerDetail.statement.duplicateClaimKwh, 0) }}</el-tag>
          <el-tag :type="ledgerDetail.statement.invalidatedChainKwh > 0 ? 'danger' : 'info'" style="margin-left:8px">
            事后失效链 {{ fmtNum(ledgerDetail.statement.invalidatedChainKwh, 0) }}
          </el-tag>
        </div>
        <el-table :data="ledgerDetail.allocations" size="small" border>
          <el-table-column label="凭证序列号" width="140">
            <template #default="{ row }">{{ row.certificate?.serialNo }}</template>
          </el-table-column>
          <el-table-column label="方式" width="100">
            <template #default="{ row }"><StatusTag kind="claimMethod" :value="row.allocation.method" /></template>
          </el-table-column>
          <el-table-column label="声明量(kWh)" width="110">
            <template #default="{ row }">{{ fmtNum(row.allocation.allocatedKwh, 0) }}</template>
          </el-table-column>
          <el-table-column label="匹配判定" width="105">
            <template #default="{ row }"><StatusTag kind="match" :value="row.allocation.matchStatus" /></template>
          </el-table-column>
          <el-table-column label="链状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.allocation.chainValid ? 'success' : 'danger'">
                {{ row.allocation.chainValid ? '有效' : '后续失效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="凭证区域/发电月" width="140">
            <template #default="{ row }">{{ row.allocation.region }} / {{ row.allocation.generationMonth }}</template>
          </el-table-column>
          <el-table-column label="失效原因/报告版本" min-width="200">
            <template #default="{ row }">
              {{ row.allocation.invalidatedReason || '-' }}
              <span v-if="row.allocation.reportVersionId">（报告版本 #{{ row.allocation.reportVersionId }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button v-if="row.allocation.chainValid && !ledgerDetail.statement?.reportVersionId"
                         size="small" link type="danger"
                         @click="removeAllocation(row.allocation.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { store, fmtNum } from '../store'
import StatusTag from '../components/StatusTag.vue'

const loading = ref(false)
const ledgers = ref([])
const statements = ref([])
const pools = ref([])
const certs = ref([])
const filterPeriod = ref('')
const detailVisible = ref(false)
const currentLedger = ref(null)
const ledgerDetail = ref(null)
const syncDlg = ref(false)
const syncPeriod = ref('2025-06')

const ledgerDlg = reactive({
  visible: false,
  form: { plantId: null, period: '2025-06', region: '', totalKwh: 100000 }
})
const allocDlg = reactive({
  visible: false,
  warnings: [],
  form: { method: 'CERTIFICATE', ledgerId: null, certificateId: null, poolId: null, quantityKwh: null }
})

const holderCerts = computed(() => {
  const plantId = currentLedger.value?.plantId
  return certs.value.filter(c => c.holderPlantId === plantId)
})

function coverPct(row) {
  if (!row.totalKwh) return 0
  return Math.min(100, Math.round(Number(row.coveredKwh) / Number(row.totalKwh) * 1000) / 10)
}

async function loadAll() {
  loading.value = true
  try {
    const params = {}
    if (filterPeriod.value) params.period = filterPeriod.value
    ledgers.value = (await api.get('/claims/ledgers', { params })).data
    statements.value = (await api.get('/claims/statements', { params })).data
    pools.value = (await api.get('/claims/pools')).data
    certs.value = (await api.get('/certificates')).data
  } finally {
    loading.value = false
  }
}

function openLedger() {
  ledgerDlg.form = { plantId: store.plants[0]?.id, period: filterPeriod.value || '2025-06',
    region: store.plants[0]?.region || '', totalKwh: 100000 }
  ledgerDlg.visible = true
}
function onLedgerPlant(pid) {
  const p = store.plants.find(x => x.id === pid)
  if (p) ledgerDlg.form.region = p.region
}
async function submitLedger() {
  await api.post('/claims/ledgers', ledgerDlg.form)
  ElMessage.success('用电账已登记，声明册已自动建立')
  ledgerDlg.visible = false
  loadAll()
}

function openSync() { syncDlg.value = true }
async function doSync() {
  const resp = await api.post(`/claims/ledgers/sync?period=${syncPeriod.value}`)
  ElMessage.success(`生成 ${resp.data.length} 个用电账`)
  syncDlg.value = false
  loadAll()
}

function openAllocate(row) {
  currentLedger.value = row
  allocDlg.form = { method: 'CERTIFICATE', ledgerId: row.id, certificateId: null, poolId: null, quantityKwh: null }
  allocDlg.warnings = []
  allocDlg.visible = true
}

async function submitAllocate() {
  const body = { ...allocDlg.form }
  if (!body.quantityKwh) delete body.quantityKwh
  try {
    const resp = await api.post('/claims/allocations', body)
    allocDlg.warnings = resp.data.warnings || []
    ElMessage.success(`归属完成：声明 ${resp.data.grantedKwh} kWh`)
    loadAll()
    if (currentLedger.value) openLedgerDetail(currentLedger.value, true)
  } catch (e) {
    // 409 重复主张/停用等由拦截器统一提示
  }
}

async function openLedgerDetail(row, silent) {
  currentLedger.value = row
  ledgerDetail.value = (await api.get(`/claims/ledgers/${row.id}`)).data
  detailVisible.value = true
}

async function removeAllocation(id) {
  await api.delete(`/claims/allocations/${id}`)
  ElMessage.success('声明链已删除，覆盖量已回补')
  loadAll()
  if (currentLedger.value) openLedgerDetail(currentLedger.value, true)
}

onMounted(async () => {
  await store.loadMeta()
  loadAll()
})
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
.mb12 { margin-bottom: 12px; }
</style>
