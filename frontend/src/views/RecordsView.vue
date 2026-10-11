<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getMyRequests } from '@/api/request'
import {
  cancelRegistration,
  cancelWaitList,
  getMyRegistrations,
  getMyWaitLists,
} from '@/api/registration'
import type { RegistrationVO, RequestVO, WaitListVO } from '@/types/api'
import { formatDateTime } from '@/utils/activity'

const route = useRoute()
const router = useRouter()

function readPage(value: unknown): number {
  const page = Number(value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

const validTabs = new Set(['registrations', 'wait-lists', 'requests'])
const activeTab = ref(
  typeof route.query.tab === 'string' && validTabs.has(route.query.tab) ? route.query.tab : 'registrations',
)
const loadingRegistrations = ref(false)
const loadingWaitLists = ref(false)
const loadingRequests = ref(false)
const cancellingActivityId = ref<number>()
const registrations = ref<RegistrationVO[]>([])
const waitLists = ref<WaitListVO[]>([])
const requests = ref<RequestVO[]>([])
const registrationPage = reactive({ current: readPage(route.query.registrationPage), size: 10, total: 0 })
const waitListPage = reactive({ current: readPage(route.query.waitListPage), size: 10, total: 0 })
const requestPage = reactive({ current: readPage(route.query.requestPage), size: 10, total: 0 })

function syncLocation(): void {
  void router.replace(recordsLocation())
}

function recordsLocation() {
  return {
    name: 'records',
    query: {
      tab: activeTab.value,
      registrationPage: String(registrationPage.current),
      waitListPage: String(waitListPage.current),
      requestPage: String(requestPage.current),
    },
  }
}

function returnPath(): string {
  return router.resolve(recordsLocation()).fullPath
}

function registrationTag(description: string): 'success' | 'info' | 'warning' | 'danger' {
  if (description === '已报名') return 'success'
  if (description === '活动已取消') return 'danger'
  if (description === '已取消报名') return 'info'
  return 'warning'
}

function waitListTag(status: number): 'success' | 'info' | 'warning' {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  return 'info'
}

function requestStatus(status: number): { label: string; type: 'success' | 'warning' | 'danger' } {
  if (status === 0) return { label: '处理中', type: 'warning' }
  if (status === 1) return { label: '处理成功', type: 'success' }
  return { label: '失败或已取消', type: 'danger' }
}

async function loadRegistrations(page = registrationPage.current): Promise<void> {
  loadingRegistrations.value = true
  try {
    const result = await getMyRegistrations(page)
    registrations.value = result.data?.pageResult ?? []
    registrationPage.current = result.data?.current ?? page
    registrationPage.size = result.data?.size ?? 10
    registrationPage.total = result.data?.total ?? 0
    syncLocation()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '报名记录加载失败')
  } finally {
    loadingRegistrations.value = false
  }
}

async function loadWaitLists(page = waitListPage.current): Promise<void> {
  loadingWaitLists.value = true
  try {
    const result = await getMyWaitLists(page)
    waitLists.value = result.data?.pageResult ?? []
    waitListPage.current = result.data?.current ?? page
    waitListPage.size = result.data?.size ?? 10
    waitListPage.total = result.data?.total ?? 0
    syncLocation()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '候补记录加载失败')
  } finally {
    loadingWaitLists.value = false
  }
}

async function loadRequests(page = requestPage.current): Promise<void> {
  loadingRequests.value = true
  try {
    const result = await getMyRequests(page)
    requests.value = result.data?.pageResult ?? []
    requestPage.current = result.data?.current ?? page
    requestPage.size = result.data?.size ?? 10
    requestPage.total = result.data?.total ?? 0
    syncLocation()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '请求记录加载失败')
  } finally {
    loadingRequests.value = false
  }
}

async function handleCancelRegistration(record: RegistrationVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`确定取消“${record.activityName || '该活动'}”的报名吗？`, '取消报名', {
    confirmButtonText: '确认取消',
    cancelButtonText: '暂不取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return

  cancellingActivityId.value = record.activityId
  try {
    await cancelRegistration(record.activityId)
    ElMessage.success('报名已取消')
    await Promise.all([loadRegistrations(), loadWaitLists()])
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '取消报名失败')
  } finally {
    cancellingActivityId.value = undefined
  }
}

async function handleCancelWaitList(record: WaitListVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`确定退出“${record.activityName || '该活动'}”的候补吗？`, '取消候补', {
    confirmButtonText: '确认退出',
    cancelButtonText: '暂不取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return

  cancellingActivityId.value = record.activityId
  try {
    await cancelWaitList(record.activityId)
    ElMessage.success('候补已取消')
    await loadWaitLists()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '取消候补失败')
  } finally {
    cancellingActivityId.value = undefined
  }
}

onMounted(() => Promise.all([loadRegistrations(), loadWaitLists(), loadRequests()]))
watch(activeTab, syncLocation)
</script>

<template>
  <main class="records-page">
    <section class="page-heading">
      <p>MY ACTIVITIES</p>
      <h1>我的活动记录</h1>
      <span>查看正式报名、候补状态和异步请求结果</span>
    </section>

    <section class="records-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="报名记录" name="registrations">
          <div v-loading="loadingRegistrations" class="record-list">
            <article v-for="record in registrations" :key="record.registrationId" class="record-item">
              <div class="record-main">
                <el-tag :type="registrationTag(record.statusDescription)">{{ record.statusDescription }}</el-tag>
                <h2>{{ record.activityName || '历史活动' }}</h2>
                <p>正式报名记录</p>
              </div>
              <div class="record-actions">
                <RouterLink
                  v-if="record.statusDescription !== '活动已取消'"
                  :to="{
                    name: 'activity-detail',
                    params: { activityId: record.activityId },
                    query: { returnTo: returnPath() },
                  }"
                >
                  <el-button>查看活动</el-button>
                </RouterLink>
                <el-button
                  v-if="record.statusDescription === '已报名'"
                  :loading="cancellingActivityId === record.activityId"
                  type="danger"
                  plain
                  @click="handleCancelRegistration(record)"
                >
                  取消报名
                </el-button>
              </div>
            </article>
            <el-empty v-if="!loadingRegistrations && registrations.length === 0" description="暂无报名记录" />
          </div>
          <el-pagination
            v-if="registrationPage.total > registrationPage.size"
            background
            :current-page="registrationPage.current"
            layout="prev, pager, next, total"
            :page-size="registrationPage.size"
            :total="registrationPage.total"
            @current-change="loadRegistrations"
          />
        </el-tab-pane>

        <el-tab-pane label="候补记录" name="wait-lists">
          <div v-loading="loadingWaitLists" class="record-list">
            <article v-for="record in waitLists" :key="record.waitId" class="record-item">
              <div class="record-main">
                <el-tag :type="waitListTag(record.status)">{{ record.statusDescription }}</el-tag>
                <h2>{{ record.activityName || '历史活动' }}</h2>
                <p>加入时间：{{ formatDateTime(record.joinTime) }}</p>
                <p v-if="record.promotedTime">转正时间：{{ formatDateTime(record.promotedTime) }}</p>
              </div>
              <div class="record-actions">
                <RouterLink
                  v-if="record.status !== 3"
                  :to="{
                    name: 'activity-detail',
                    params: { activityId: record.activityId },
                    query: { returnTo: returnPath() },
                  }"
                >
                  <el-button>查看活动</el-button>
                </RouterLink>
                <el-button
                  v-if="record.status === 0"
                  :loading="cancellingActivityId === record.activityId"
                  type="danger"
                  plain
                  @click="handleCancelWaitList(record)"
                >
                  取消候补
                </el-button>
              </div>
            </article>
            <el-empty v-if="!loadingWaitLists && waitLists.length === 0" description="暂无候补记录" />
          </div>
          <el-pagination
            v-if="waitListPage.total > waitListPage.size"
            background
            :current-page="waitListPage.current"
            layout="prev, pager, next, total"
            :page-size="waitListPage.size"
            :total="waitListPage.total"
            @current-change="loadWaitLists"
          />
        </el-tab-pane>

        <el-tab-pane label="请求记录" name="requests">
          <div v-loading="loadingRequests" class="record-list">
            <article v-for="record in requests" :key="record.requestId" class="record-item">
              <div class="record-main">
                <el-tag :type="requestStatus(record.status).type">{{ requestStatus(record.status).label }}</el-tag>
                <h2>{{ record.activityName || '历史活动' }}</h2>
                <p>活动报名申请</p>
              </div>
              <div class="record-actions">
                <RouterLink
                  :to="{
                    name: 'activity-detail',
                    params: { activityId: record.activityId },
                    query: { returnTo: returnPath() },
                  }"
                >
                  <el-button>查看活动</el-button>
                </RouterLink>
              </div>
            </article>
            <el-empty v-if="!loadingRequests && requests.length === 0" description="暂无请求记录" />
          </div>
          <el-pagination
            v-if="requestPage.total > requestPage.size"
            background
            :current-page="requestPage.current"
            layout="prev, pager, next, total"
            :page-size="requestPage.size"
            :total="requestPage.total"
            @current-change="loadRequests"
          />
        </el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>

<style scoped>
.records-page {
  width: min(1000px, calc(100% - 40px));
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 54px 0 80px;
}

.page-heading > p {
  margin-bottom: 8px;
  color: #315bd9;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.page-heading h1 {
  color: #17213a;
  font-size: 38px;
  font-weight: 700;
}

.page-heading span {
  color: #7a8499;
}

.records-card {
  margin-top: 30px;
  padding: 26px 30px 34px;
  border: 1px solid #e3e8f0;
  border-radius: 20px;
  background: #ffffff;
}

.record-list {
  min-height: 220px;
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px;
  border: 1px solid #e8ecf2;
  border-radius: 14px;
  background: #fafbfc;
}

.record-main h2 {
  margin: 10px 0 5px;
  color: #17213a;
  font-size: 18px;
  font-weight: 700;
}

.record-main p {
  margin-top: 3px;
  color: #8a94a7;
  font-size: 13px;
}

.record-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.record-actions a {
  text-decoration: none;
}

.el-pagination {
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 650px) {
  .records-card { padding: 20px; }
  .record-item { align-items: flex-start; flex-direction: column; }
  .record-actions { width: 100%; }
}
</style>
