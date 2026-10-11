<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { getActivityDetail } from '@/api/activity'
import { ApiError } from '@/api/http'
import {
  cancelRegistration,
  cancelWaitList,
  getRegistrationStatus,
  queryRegistrationRequest,
  registerActivity,
} from '@/api/registration'
import type { ActivityVO, RegistrationVO } from '@/types/api'
import { formatDateTime, getRegistrationPhase } from '@/utils/activity'

const route = useRoute()
const router = useRouter()
const activity = ref<ActivityVO>()
const registration = ref<RegistrationVO>()
const loading = ref(false)
const actionLoading = ref(false)
const polling = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | undefined

const phase = computed(() =>
  activity.value
    ? getRegistrationPhase(activity.value.registrationStartTime, activity.value.registrationEndTime)
    : { label: '', type: 'info' as const },
)

const capacityPercent = computed(() => {
  if (!activity.value?.totalCapacity) return 0
  return Math.round((activity.value.remainingCapacity / activity.value.totalCapacity) * 100)
})

const action = computed(() => {
  const description = registration.value?.statusDescription
  if (description === '已报名') return { label: '取消报名', kind: 'cancel-registration' as const }
  if (description === '候补中') return { label: '取消候补', kind: 'cancel-wait-list' as const }
  if (phase.value.label === '报名进行中') {
    return {
      label: activity.value?.remainingCapacity ? '立即报名' : '加入候补',
      kind: 'register' as const,
    }
  }
  return { label: description || phase.value.label, kind: 'disabled' as const }
})

async function loadDetail(): Promise<void> {
  const activityId = Number(route.params.activityId)
  if (!Number.isSafeInteger(activityId) || activityId <= 0) {
    ElMessage.error('活动编号不正确')
    await router.replace({ name: 'home' })
    return
  }

  loading.value = true
  try {
    const result = await getActivityDetail(activityId)
    if (!result.data) throw new ApiError('活动详情不存在')
    activity.value = result.data
    await loadRegistrationStatus(activityId)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '活动详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRegistrationStatus(activityId = activity.value?.activityId): Promise<void> {
  if (!activityId) return
  const result = await getRegistrationStatus(activityId)
  registration.value = result.data
}

async function pollRequest(requestId: string, attempt = 0): Promise<void> {
  if (attempt >= 30) {
    polling.value = false
    actionLoading.value = false
    ElMessage.warning('请求仍在处理中，请稍后刷新查看结果')
    return
  }

  try {
    const result = await queryRegistrationRequest(requestId)
    if (result.data?.status === 1) {
      polling.value = false
      actionLoading.value = false
      await loadDetail()
      ElMessage.success(registration.value?.statusDescription === '候补中' ? '已加入候补' : '报名成功')
      return
    }
    if (result.data?.status === 2) {
      polling.value = false
      actionLoading.value = false
      ElMessage.error('报名请求处理失败，请稍后重试')
      return
    }
  } catch (error) {
    const stillProcessing = error instanceof ApiError && (error.code === 40402 || error.status === 404)
    if (!stillProcessing) {
      polling.value = false
      actionLoading.value = false
      ElMessage.error(error instanceof ApiError ? error.message : '查询报名结果失败')
      return
    }
  }

  pollTimer = setTimeout(() => void pollRequest(requestId, attempt + 1), 1000)
}

async function handleAction(): Promise<void> {
  if (!activity.value || action.value.kind === 'disabled') return
  actionLoading.value = true
  try {
    if (action.value.kind === 'cancel-registration') {
      await cancelRegistration(activity.value.activityId)
      await loadDetail()
      ElMessage.success('报名已取消')
      return
    }

    if (action.value.kind === 'cancel-wait-list') {
      await cancelWaitList(activity.value.activityId)
      await loadRegistrationStatus()
      ElMessage.success('候补已取消')
      return
    }

    const result = await registerActivity(activity.value.activityId)
    if (!result.data) throw new ApiError('服务器未返回请求编号')
    polling.value = true
    ElMessage.info(result.message || '报名请求已提交')
    await pollRequest(result.data)
  } catch (error) {
    actionLoading.value = false
    ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
  } finally {
    if (!polling.value) actionLoading.value = false
  }
}

async function returnToSource(): Promise<void> {
  const queryValue = route.query.returnTo
  const returnTo = Array.isArray(queryValue) ? queryValue[0] : queryValue
  if (returnTo && returnTo.startsWith('/') && !returnTo.startsWith('//')) {
    await router.push(returnTo)
    return
  }
  await router.push({ name: 'home' })
}

onMounted(loadDetail)
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <main v-loading="loading" class="detail-page">
    <button class="back-button" type="button" @click="returnToSource">← 返回上一页</button>

    <template v-if="activity">
      <section class="detail-hero">
        <div class="hero-content">
          <div class="tags">
            <el-tag effect="dark">{{ activity.activityTypeName || '校园活动' }}</el-tag>
            <el-tag :type="phase.type">{{ phase.label }}</el-tag>
          </div>
          <h1>{{ activity.activityName }}</h1>
          <p>{{ activity.activityLocation }}</p>
        </div>
      </section>

      <div class="detail-grid">
        <article class="detail-card description-card">
          <h2>活动介绍</h2>
          <p>{{ activity.activityDescription || '活动组织者暂未填写详细介绍。' }}</p>

          <h2>时间安排</h2>
          <div class="timeline">
            <div>
              <span>报名开放</span>
              <strong>{{ formatDateTime(activity.registrationStartTime) }}</strong>
            </div>
            <div>
              <span>报名截止</span>
              <strong>{{ formatDateTime(activity.registrationEndTime) }}</strong>
            </div>
            <div>
              <span>活动开始</span>
              <strong>{{ formatDateTime(activity.startTime) }}</strong>
            </div>
            <div>
              <span>活动结束</span>
              <strong>{{ formatDateTime(activity.endTime) }}</strong>
            </div>
          </div>
        </article>

        <aside class="detail-card capacity-card">
          <p class="aside-label">剩余名额</p>
          <div class="capacity-number">
            <strong>{{ activity.remainingCapacity }}</strong>
            <span>/ {{ activity.totalCapacity }}</span>
          </div>
          <el-progress :percentage="capacityPercent" :show-text="false" />
          <p class="capacity-note">
            {{ activity.remainingCapacity > 0 ? '当前仍有可报名名额' : '正式名额已满，可进入候补' }}
          </p>
          <p v-if="registration" class="registration-status">当前状态：{{ registration.statusDescription }}</p>
          <el-button
            :disabled="action.kind === 'disabled'"
            :loading="actionLoading"
            size="large"
            :type="action.kind.startsWith('cancel') ? 'danger' : 'primary'"
            @click="handleAction"
          >
            {{ polling ? '正在确认结果…' : action.label }}
          </el-button>
        </aside>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="没有找到该活动" />
  </main>
</template>

<style scoped>
.detail-page {
  width: min(1120px, calc(100% - 40px));
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 32px 0 70px;
}

.back-button {
  margin-bottom: 24px;
  padding: 0;
  color: #667085;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.detail-hero {
  min-height: 300px;
  display: flex;
  align-items: flex-end;
  padding: 48px;
  overflow: hidden;
  color: #ffffff;
  border-radius: 26px;
  background:
    radial-gradient(circle at 85% 20%, rgb(255 255 255 / 18%), transparent 26%),
    linear-gradient(135deg, #173274, #315bd9 62%, #7992e7);
}

.tags {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.detail-hero h1 {
  max-width: 800px;
  margin-bottom: 12px;
  font-size: clamp(34px, 5vw, 58px);
  font-weight: 700;
  line-height: 1.16;
}

.detail-hero p {
  color: rgb(255 255 255 / 75%);
  font-size: 17px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 22px;
  margin-top: 22px;
}

.detail-card {
  padding: 34px;
  border: 1px solid #e4e9f1;
  border-radius: 20px;
  background: #ffffff;
}

.description-card h2 {
  margin-bottom: 14px;
  color: #17213a;
  font-size: 20px;
  font-weight: 700;
}

.description-card > p {
  min-height: 90px;
  margin-bottom: 36px;
  color: #667085;
  line-height: 1.9;
  white-space: pre-wrap;
}

.timeline {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.timeline div {
  padding: 16px;
  border-radius: 12px;
  background: #f6f8fc;
}

.timeline span,
.timeline strong {
  display: block;
}

.timeline span,
.aside-label {
  margin-bottom: 6px;
  color: #98a2b3;
  font-size: 12px;
}

.timeline strong {
  color: #344054;
  font-size: 14px;
  font-weight: 600;
}

.capacity-card {
  align-self: start;
  position: sticky;
  top: 92px;
}

.capacity-number {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 16px;
}

.capacity-number strong {
  color: #17213a;
  font-size: 52px;
  font-weight: 700;
}

.capacity-number span,
.capacity-note {
  color: #7a8499;
}

.capacity-note {
  margin: 16px 0 24px;
  font-size: 14px;
}

.registration-status {
  margin: -8px 0 18px;
  padding: 10px 12px;
  color: #315bd9;
  font-size: 13px;
  border-radius: 10px;
  background: #eef3ff;
}

.capacity-card .el-button {
  width: 100%;
}

@media (max-width: 780px) {
  .detail-hero { min-height: 250px; padding: 30px; }
  .detail-grid { grid-template-columns: 1fr; }
  .capacity-card { position: static; }
}
</style>
