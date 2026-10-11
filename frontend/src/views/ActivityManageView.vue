<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { cancelActivity, getMyActivityDetail, publishActivity } from '@/api/activity'
import { ApiError } from '@/api/http'
import { getRegistrationsOfMyActivity, getWaitListOfMyActivity } from '@/api/registration'
import type { ActivityVO, RegistrationVO, WaitListVO } from '@/types/api'
import { formatDateTime } from '@/utils/activity'

const route = useRoute()
const router = useRouter()
const activity = ref<ActivityVO>()
const loading = ref(false)
const operating = ref(false)
const activeTab = ref('registrations')
const registrations = ref<RegistrationVO[]>([])
const waitLists = ref<WaitListVO[]>([])
const registrationPage = ref({ current: 1, size: 10, total: 0 })
const waitListPage = ref({ current: 1, size: 10, total: 0 })
const loadingRegistrations = ref(false)
const loadingWaitLists = ref(false)

const activityId = computed(() => Number(route.params.activityId))
const statusLabel = computed(() => ['草稿', '已发布', '已取消'][activity.value?.status ?? 0] ?? '未知')
const statusType = computed(() => {
  if (activity.value?.status === 1) return 'success'
  if (activity.value?.status === 2) return 'danger'
  return 'info'
})

function sourceLocation(): string {
  const value = Array.isArray(route.query.returnTo) ? route.query.returnTo[0] : route.query.returnTo
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/my-activities'
}

async function loadActivity(): Promise<void> {
  if (!Number.isSafeInteger(activityId.value) || activityId.value <= 0) {
    ElMessage.error('活动编号不正确')
    await router.replace({ name: 'my-activities' })
    return
  }
  loading.value = true
  try {
    const result = await getMyActivityDetail(activityId.value)
    if (!result.data) throw new ApiError('活动不存在')
    activity.value = result.data
    if (result.data.status === 1) {
      await Promise.all([loadRegistrations(1), loadWaitLists(1)])
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '活动管理信息加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRegistrations(page = registrationPage.value.current): Promise<void> {
  loadingRegistrations.value = true
  try {
    const result = await getRegistrationsOfMyActivity(activityId.value, page)
    registrations.value = result.data?.pageResult ?? []
    registrationPage.value = {
      current: result.data?.current ?? page,
      size: result.data?.size ?? 10,
      total: result.data?.total ?? 0,
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '报名名单加载失败')
  } finally {
    loadingRegistrations.value = false
  }
}

async function loadWaitLists(page = waitListPage.value.current): Promise<void> {
  loadingWaitLists.value = true
  try {
    const result = await getWaitListOfMyActivity(activityId.value, page)
    waitLists.value = result.data?.pageResult ?? []
    waitListPage.value = {
      current: result.data?.current ?? page,
      size: result.data?.size ?? 10,
      total: result.data?.total ?? 0,
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '候补名单加载失败')
  } finally {
    loadingWaitLists.value = false
  }
}

async function handlePublish(): Promise<void> {
  if (!activity.value) return
  const confirmed = await ElMessageBox.confirm('发布后活动将不能再编辑，确定发布吗？', '发布活动', {
    confirmButtonText: '确认发布',
    cancelButtonText: '返回',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  operating.value = true
  try {
    await publishActivity(activity.value.activityId)
    ElMessage.success('活动已发布')
    await loadActivity()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '发布失败')
  } finally {
    operating.value = false
  }
}

async function handleCancel(): Promise<void> {
  if (!activity.value) return
  const confirmed = await ElMessageBox.confirm('取消后报名和候补记录将失效，确定继续吗？', '取消活动', {
    confirmButtonText: '确认取消',
    cancelButtonText: '返回',
    type: 'error',
  }).catch(() => false)
  if (!confirmed) return
  operating.value = true
  try {
    await cancelActivity(activity.value.activityId)
    ElMessage.success('活动已取消')
    await loadActivity()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '取消失败')
  } finally {
    operating.value = false
  }
}

onMounted(loadActivity)
</script>

<template>
  <main v-loading="loading" class="manage-page">
    <button class="back-button" type="button" @click="router.push(sourceLocation())">← 返回我的活动</button>
    <template v-if="activity">
      <section class="detail-card">
        <div class="detail-heading">
          <div>
            <el-tag :type="statusType">{{ statusLabel }}</el-tag>
            <h1>{{ activity.activityName }}</h1>
            <p>{{ activity.activityDescription || '暂无活动介绍' }}</p>
          </div>
          <div class="heading-actions">
            <RouterLink
              v-if="activity.status === 0"
              :to="{
                name: 'activity-edit',
                params: { activityId: activity.activityId },
                query: { returnTo: sourceLocation() },
              }"
            >
              <el-button size="large">编辑草稿</el-button>
            </RouterLink>
            <el-button v-if="activity.status === 0" :loading="operating" size="large" type="primary" @click="handlePublish">
              发布活动
            </el-button>
            <el-button v-if="activity.status === 1" :loading="operating" plain size="large" type="danger" @click="handleCancel">
              取消活动
            </el-button>
          </div>
        </div>
        <dl class="detail-grid">
          <div><dt>活动地点</dt><dd>{{ activity.activityLocation }}</dd></div>
          <div><dt>活动类型</dt><dd>{{ activity.activityTypeName }}</dd></div>
          <div><dt>报名时间</dt><dd>{{ formatDateTime(activity.registrationStartTime) }} 至 {{ formatDateTime(activity.registrationEndTime) }}</dd></div>
          <div><dt>活动时间</dt><dd>{{ formatDateTime(activity.startTime) }} 至 {{ formatDateTime(activity.endTime) }}</dd></div>
          <div><dt>总名额</dt><dd>{{ activity.totalCapacity }}</dd></div>
          <div><dt>剩余名额</dt><dd>{{ activity.remainingCapacity }}</dd></div>
        </dl>
      </section>

      <section v-if="activity.status === 1" class="people-card">
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="`报名用户 (${registrationPage.total})`" name="registrations">
            <div v-loading="loadingRegistrations" class="people-list">
              <article v-for="(record, index) in registrations" :key="record.registrationId ?? record.userId">
                <span class="order-number">{{ (registrationPage.current - 1) * registrationPage.size + index + 1 }}</span>
                <div>
                  <strong>{{ record.userName || '未知用户' }}</strong>
                  <p>学号：{{ record.studentId || '暂无' }}</p>
                </div>
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
              @current-change="loadRegistrations"
            />
          </el-tab-pane>

          <el-tab-pane :label="`候补用户 (${waitListPage.total})`" name="wait-lists">
            <div v-loading="loadingWaitLists" class="people-list">
              <article v-for="(record, index) in waitLists" :key="record.waitId">
                <span class="order-number">{{ (waitListPage.current - 1) * waitListPage.size + index + 1 }}</span>
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
              @current-change="loadWaitLists"
            />
          </el-tab-pane>
        </el-tabs>
      </section>

      <el-alert
        v-else
        :closable="false"
        description="报名和候补名单仅在活动发布后开放。"
        show-icon
        title="当前没有可查看的参与名单"
        type="info"
      />
    </template>
  </main>
</template>

<style scoped>
.manage-page {
  width: min(1040px, calc(100% - 40px));
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 46px 0 80px;
}

.back-button {
  margin-bottom: 22px;
  padding: 0;
  color: #667085;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.detail-card,
.people-card {
  padding: 30px;
  border: 1px solid #e3e8f0;
  border-radius: 20px;
  background: #fff;
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.detail-heading h1 {
  margin: 14px 0 9px;
  color: #17213a;
  font-size: 34px;
}

.detail-heading p,
dt,
.people-list p {
  color: #7a8499;
}

.heading-actions {
  display: flex;
  gap: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 22px;
  margin-top: 30px;
  padding-top: 26px;
  border-top: 1px solid #edf0f5;
}

dt {
  margin-bottom: 6px;
  font-size: 12px;
}

dd {
  color: #344054;
}

.people-card {
  margin-top: 20px;
}

.people-list {
  min-height: 180px;
  display: grid;
  gap: 10px;
  margin: 12px 0 22px;
}

.people-list article {
  display: grid;
  grid-template-columns: 42px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid #e8ecf2;
  border-radius: 13px;
  background: #fafbfc;
}

.order-number {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #315bd9;
  font-weight: 700;
  border-radius: 10px;
  background: #eaf0ff;
}

.people-list p {
  margin-top: 4px;
  font-size: 13px;
}

.el-pagination {
  justify-content: center;
}

.el-alert {
  margin-top: 20px;
}

@media (max-width: 720px) {
  .detail-heading,
  .heading-actions {
    flex-direction: column;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
