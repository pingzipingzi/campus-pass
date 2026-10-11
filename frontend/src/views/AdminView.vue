<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import {
  disableUser,
  enableUser,
  forceCancelActivity,
  getAdminActivities,
  getAdminUsers,
} from '@/api/admin'
import { ApiError } from '@/api/http'
import { getRegistrationsOfMyActivity, getWaitListOfMyActivity } from '@/api/registration'
import type { ActivityPageVO, RegistrationVO, UserVO, WaitListVO } from '@/types/api'
import { formatDateTime } from '@/utils/activity'

const route = useRoute()
const router = useRouter()

function readPage(value: unknown): number {
  const page = Number(value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

const activeTab = ref(route.query.tab === 'activities' ? 'activities' : 'users')
const users = ref<UserVO[]>([])
const activities = ref<ActivityPageVO[]>([])
const loadingUsers = ref(false)
const loadingActivities = ref(false)
const operatingUserId = ref<number>()
const operatingActivityId = ref<number>()
const userPage = reactive({ current: readPage(route.query.userPage), size: 10, total: 0 })
const activityPage = reactive({ current: readPage(route.query.activityPage), size: 10, total: 0 })
const rosterVisible = ref(false)
const rosterTab = ref('registrations')
const selectedActivity = ref<ActivityPageVO>()
const registrations = ref<RegistrationVO[]>([])
const waitLists = ref<WaitListVO[]>([])
const loadingRegistrations = ref(false)
const loadingWaitLists = ref(false)
const registrationPage = reactive({ current: 1, size: 10, total: 0 })
const waitListPage = reactive({ current: 1, size: 10, total: 0 })

function activityStatus(status: number): { label: string; type: 'info' | 'success' | 'danger' } {
  if (status === 0) return { label: '草稿', type: 'info' }
  if (status === 1) return { label: '已发布', type: 'success' }
  return { label: '已取消', type: 'danger' }
}

function syncLocation(): void {
  void router.replace(adminLocation())
}

function adminLocation() {
  return {
    name: 'admin',
    query: {
      tab: activeTab.value,
      userPage: String(userPage.current),
      activityPage: String(activityPage.current),
    },
  }
}

function returnPath(): string {
  return router.resolve(adminLocation()).fullPath
}

async function loadUsers(page = userPage.current): Promise<void> {
  loadingUsers.value = true
  try {
    const result = await getAdminUsers(page)
    users.value = result.data?.pageResult ?? []
    userPage.current = result.data?.current ?? page
    userPage.size = result.data?.size ?? 10
    userPage.total = result.data?.total ?? 0
    syncLocation()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '用户列表加载失败')
  } finally {
    loadingUsers.value = false
  }
}

async function loadActivities(page = activityPage.current): Promise<void> {
  loadingActivities.value = true
  try {
    const result = await getAdminActivities(page)
    activities.value = result.data?.pageResult ?? []
    activityPage.current = result.data?.current ?? page
    activityPage.size = result.data?.size ?? 10
    activityPage.total = result.data?.total ?? 0
    syncLocation()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '活动列表加载失败')
  } finally {
    loadingActivities.value = false
  }
}

async function changeUserStatus(user: UserVO): Promise<void> {
  const disabling = user.status === 0
  const confirmed = await ElMessageBox.confirm(
    disabling
      ? `禁用“${user.userName}”后将清理其有效报名和候补，是否继续？`
      : `确定恢复“${user.userName}”的账号吗？`,
    disabling ? '禁用用户' : '恢复用户',
    {
      confirmButtonText: disabling ? '确认禁用' : '确认恢复',
      cancelButtonText: '返回',
      type: disabling ? 'warning' : 'info',
    },
  ).catch(() => false)
  if (!confirmed) return

  operatingUserId.value = user.userId
  try {
    if (disabling) await disableUser(user.userId)
    else await enableUser(user.userId)
    ElMessage.success(disabling ? '用户已禁用' : '用户已恢复')
    await loadUsers()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '用户状态修改失败')
  } finally {
    operatingUserId.value = undefined
  }
}

async function handleForceCancel(activity: ActivityPageVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确定强制取消“${activity.activityName}”吗？报名和候补记录将失效。`,
    '强制取消活动',
    { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'error' },
  ).catch(() => false)
  if (!confirmed) return

  operatingActivityId.value = activity.activityId
  try {
    await forceCancelActivity(activity.activityId)
    ElMessage.success('活动已取消')
    await loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '取消活动失败')
  } finally {
    operatingActivityId.value = undefined
  }
}

async function loadRosterRegistrations(page = registrationPage.current): Promise<void> {
  if (!selectedActivity.value) return
  loadingRegistrations.value = true
  try {
    const result = await getRegistrationsOfMyActivity(selectedActivity.value.activityId, page)
    registrations.value = result.data?.pageResult ?? []
    registrationPage.current = result.data?.current ?? page
    registrationPage.size = result.data?.size ?? 10
    registrationPage.total = result.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '报名名单加载失败')
  } finally {
    loadingRegistrations.value = false
  }
}

async function loadRosterWaitLists(page = waitListPage.current): Promise<void> {
  if (!selectedActivity.value) return
  loadingWaitLists.value = true
  try {
    const result = await getWaitListOfMyActivity(selectedActivity.value.activityId, page)
    waitLists.value = result.data?.pageResult ?? []
    waitListPage.current = result.data?.current ?? page
    waitListPage.size = result.data?.size ?? 10
    waitListPage.total = result.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '候补名单加载失败')
  } finally {
    loadingWaitLists.value = false
  }
}

async function openRoster(activity: ActivityPageVO): Promise<void> {
  selectedActivity.value = activity
  rosterTab.value = 'registrations'
  registrationPage.current = 1
  waitListPage.current = 1
  registrations.value = []
  waitLists.value = []
  rosterVisible.value = true
  await Promise.all([loadRosterRegistrations(1), loadRosterWaitLists(1)])
}

onMounted(() => Promise.all([loadUsers(), loadActivities()]))
watch(activeTab, syncLocation)
</script>

<template>
  <main class="admin-page">
    <section class="page-heading">
      <p>ADMIN CONSOLE</p>
      <h1>平台管理</h1>
      <span>管理用户状态和平台活动</span>
    </section>

    <section class="admin-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`用户管理 (${userPage.total})`" name="users">
          <div v-loading="loadingUsers" class="item-list">
            <article v-for="user in users" :key="user.userId" class="list-item">
              <div class="item-main">
                <div class="title-line">
                  <strong>{{ user.userName }}</strong>
                  <el-tag v-if="user.role === 1" type="warning">管理员</el-tag>
                  <el-tag v-else :type="user.status === 0 ? 'success' : 'danger'">
                    {{ user.status === 0 ? '正常' : '已禁用' }}
                  </el-tag>
                </div>
                <p>学号：{{ user.studentId }} · 手机号：{{ user.phone }}</p>
                <span>注册时间：{{ formatDateTime(user.createTime) }}</span>
              </div>
              <el-button
                v-if="user.role !== 1"
                :loading="operatingUserId === user.userId"
                :type="user.status === 0 ? 'danger' : 'success'"
                plain
                @click="changeUserStatus(user)"
              >
                {{ user.status === 0 ? '禁用用户' : '恢复用户' }}
              </el-button>
            </article>
            <el-empty v-if="!loadingUsers && users.length === 0" description="暂无用户" />
          </div>
          <el-pagination
            v-if="userPage.total > userPage.size"
            background
            :current-page="userPage.current"
            layout="prev, pager, next, total"
            :page-size="userPage.size"
            :total="userPage.total"
            @current-change="loadUsers"
          />
        </el-tab-pane>

        <el-tab-pane :label="`活动管理 (${activityPage.total})`" name="activities">
          <div v-loading="loadingActivities" class="item-list">
            <article v-for="activity in activities" :key="activity.activityId" class="list-item">
              <div class="item-main">
                <div class="title-line">
                  <strong>{{ activity.activityName }}</strong>
                  <el-tag :type="activityStatus(activity.status).type">{{ activityStatus(activity.status).label }}</el-tag>
                </div>
                <p>{{ activity.activityLocation }} · {{ formatDateTime(activity.startTime) }}</p>
                <span>剩余名额：{{ activity.remainingCapacity }} / {{ activity.totalCapacity }}</span>
              </div>
              <div class="item-actions">
                <el-button v-if="activity.status === 1" @click="openRoster(activity)">查看名单</el-button>
                <RouterLink
                  v-if="activity.status === 1"
                  :to="{
                    name: 'activity-detail',
                    params: { activityId: activity.activityId },
                    query: { returnTo: returnPath() },
                  }"
                >
                  <el-button>查看活动</el-button>
                </RouterLink>
                <el-button
                  v-if="activity.status !== 2"
                  :loading="operatingActivityId === activity.activityId"
                  plain
                  type="danger"
                  @click="handleForceCancel(activity)"
                >
                  强制取消
                </el-button>
              </div>
            </article>
            <el-empty v-if="!loadingActivities && activities.length === 0" description="暂无活动" />
          </div>
          <el-pagination
            v-if="activityPage.total > activityPage.size"
            background
            :current-page="activityPage.current"
            layout="prev, pager, next, total"
            :page-size="activityPage.size"
            :total="activityPage.total"
            @current-change="loadActivities"
          />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="rosterVisible"
      :title="`${selectedActivity?.activityName ?? ''} · 参与名单`"
      width="min(760px, 92vw)"
    >
      <el-tabs v-model="rosterTab">
        <el-tab-pane :label="`报名用户 (${registrationPage.total})`" name="registrations">
          <div v-loading="loadingRegistrations" class="roster-list">
            <article v-for="record in registrations" :key="record.registrationId ?? record.userId">
              <div><strong>{{ record.userName || '未知用户' }}</strong><p>学号：{{ record.studentId || '暂无' }}</p></div>
              <el-tag type="success">报名成功</el-tag>
            </article>
            <el-empty v-if="!loadingRegistrations && registrations.length === 0" description="暂无报名用户" />
          </div>
          <el-pagination
            v-if="registrationPage.total > registrationPage.size"
            background
            :current-page="registrationPage.current"
            layout="prev, pager, next, total"
            :page-size="registrationPage.size"
            :total="registrationPage.total"
            @current-change="loadRosterRegistrations"
          />
        </el-tab-pane>
        <el-tab-pane :label="`候补用户 (${waitListPage.total})`" name="wait-lists">
          <div v-loading="loadingWaitLists" class="roster-list">
            <article v-for="record in waitLists" :key="record.waitId">
              <div>
                <strong>{{ record.userName || '未知用户' }}</strong>
                <p>学号：{{ record.studentId || '暂无' }} · 加入时间：{{ formatDateTime(record.joinTime) }}</p>
              </div>
              <el-tag type="warning">候补中</el-tag>
            </article>
            <el-empty v-if="!loadingWaitLists && waitLists.length === 0" description="暂无候补用户" />
          </div>
          <el-pagination
            v-if="waitListPage.total > waitListPage.size"
            background
            :current-page="waitListPage.current"
            layout="prev, pager, next, total"
            :page-size="waitListPage.size"
            :total="waitListPage.total"
            @current-change="loadRosterWaitLists"
          />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </main>
</template>

<style scoped>
.admin-page {
  width: min(1080px, calc(100% - 40px));
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 52px 0 80px;
}

.page-heading p {
  margin-bottom: 8px;
  color: #315bd9;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.page-heading h1 {
  color: #17213a;
  font-size: 38px;
}

.page-heading span,
.item-main p,
.item-main > span {
  color: #7a8499;
}

.admin-card {
  margin-top: 30px;
  padding: 26px 30px 34px;
  border: 1px solid #e3e8f0;
  border-radius: 20px;
  background: #fff;
}

.item-list {
  min-height: 260px;
  display: grid;
  gap: 12px;
  margin: 14px 0 26px;
}

.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 22px;
  border: 1px solid #e8ecf2;
  border-radius: 14px;
  background: #fafbfc;
}

.title-line,
.item-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-line strong {
  color: #17213a;
  font-size: 18px;
}

.item-main p {
  margin: 8px 0 4px;
}

.item-main > span {
  font-size: 13px;
}

.item-actions a {
  text-decoration: none;
}

.el-pagination {
  justify-content: center;
}

.roster-list {
  min-height: 180px;
  display: grid;
  gap: 10px;
  margin: 12px 0 22px;
}

.roster-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 15px 17px;
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  background: #fafbfc;
}

.roster-list p {
  margin-top: 5px;
  color: #7a8499;
  font-size: 13px;
}

@media (max-width: 680px) {
  .admin-card {
    padding: 20px;
  }

  .list-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
