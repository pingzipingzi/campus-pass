<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { cancelActivity, getMyActivityPage, publishActivity } from '@/api/activity'
import { ApiError } from '@/api/http'
import type { ActivityPageVO } from '@/types/api'
import { formatDateTime } from '@/utils/activity'

const activities = ref<ActivityPageVO[]>([])
const route = useRoute()
const router = useRouter()

function readPage(value: unknown): number {
  const valueAsNumber = Number(value)
  return Number.isSafeInteger(valueAsNumber) && valueAsNumber > 0 ? valueAsNumber : 1
}

const loading = ref(false)
const operatingId = ref<number>()
const page = ref(readPage(route.query.page))
const pageSize = ref(10)
const total = ref(0)

function statusMeta(status: number): { label: string; type: 'info' | 'success' | 'danger' } {
  if (status === 0) return { label: '草稿', type: 'info' }
  if (status === 1) return { label: '已发布', type: 'success' }
  return { label: '已取消', type: 'danger' }
}

async function loadActivities(targetPage = page.value): Promise<void> {
  loading.value = true
  try {
    const result = await getMyActivityPage(targetPage)
    activities.value = result.data?.pageResult ?? []
    page.value = result.data?.current ?? targetPage
    pageSize.value = result.data?.size ?? 10
    total.value = result.data?.total ?? 0
    void router.replace({ name: 'my-activities', query: { page: String(page.value) } })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '我的活动加载失败')
  } finally {
    loading.value = false
  }
}

async function handlePublish(activity: ActivityPageVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `发布“${activity.activityName}”后将不能再修改，确定发布吗？`,
    '发布活动',
    { confirmButtonText: '确认发布', cancelButtonText: '暂不发布', type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return

  operatingId.value = activity.activityId
  try {
    await publishActivity(activity.activityId)
    ElMessage.success('活动已发布')
    await loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '发布失败')
  } finally {
    operatingId.value = undefined
  }
}

async function handleCancel(activity: ActivityPageVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `取消“${activity.activityName}”后，现有报名和候补记录将失效，是否继续？`,
    '取消活动',
    { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'error' },
  ).catch(() => false)
  if (!confirmed) return

  operatingId.value = activity.activityId
  try {
    await cancelActivity(activity.activityId)
    ElMessage.success('活动已取消')
    await loadActivities()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '取消活动失败')
  } finally {
    operatingId.value = undefined
  }
}

onMounted(() => loadActivities())
</script>

<template>
  <main class="management-page">
    <section class="page-heading">
      <div>
        <p>ACTIVITY STUDIO</p>
        <h1>我的活动</h1>
        <span>管理草稿、发布活动并查看参与情况</span>
      </div>
      <RouterLink :to="{ name: 'activity-create', query: { returnTo: route.fullPath } }">
        <el-button size="large" type="primary">创建活动</el-button>
      </RouterLink>
    </section>

    <section v-loading="loading" class="activity-list">
      <article v-for="activity in activities" :key="activity.activityId" class="activity-row">
        <div class="activity-summary">
          <div class="title-line">
            <el-tag :type="statusMeta(activity.status).type">{{ statusMeta(activity.status).label }}</el-tag>
            <h2>{{ activity.activityName }}</h2>
          </div>
          <p>{{ activity.activityLocation }}</p>
          <div class="activity-meta">
            <span>活动时间：{{ formatDateTime(activity.startTime) }}</span>
            <span>名额：{{ activity.remainingCapacity }} / {{ activity.totalCapacity }}</span>
          </div>
        </div>
        <div class="row-actions">
          <RouterLink
            :to="{
              name: 'activity-manage',
              params: { activityId: activity.activityId },
              query: { returnTo: route.fullPath },
            }"
          >
            <el-button>管理详情</el-button>
          </RouterLink>
          <RouterLink
            v-if="activity.status === 0"
            :to="{
              name: 'activity-edit',
              params: { activityId: activity.activityId },
              query: { returnTo: route.fullPath },
            }"
          >
            <el-button>编辑</el-button>
          </RouterLink>
          <el-button
            v-if="activity.status === 0"
            :loading="operatingId === activity.activityId"
            type="primary"
            @click="handlePublish(activity)"
          >
            发布
          </el-button>
          <el-button
            v-if="activity.status === 1"
            :loading="operatingId === activity.activityId"
            type="danger"
            plain
            @click="handleCancel(activity)"
          >
            取消活动
          </el-button>
        </div>
      </article>
      <el-empty v-if="!loading && activities.length === 0" description="还没有创建活动" />
    </section>

    <el-pagination
      v-if="total > pageSize"
      background
      :current-page="page"
      layout="prev, pager, next, total"
      :page-size="pageSize"
      :total="total"
      @current-change="loadActivities"
    />
  </main>
</template>

<style scoped>
.management-page {
  width: min(1080px, calc(100% - 40px));
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 52px 0 80px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
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
.activity-summary p,
.activity-meta {
  color: #7a8499;
}

.activity-list {
  min-height: 260px;
  display: grid;
  gap: 14px;
  margin: 30px 0;
}

.activity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 25px 28px;
  border: 1px solid #e3e8f0;
  border-radius: 18px;
  background: #fff;
}

.title-line,
.row-actions,
.activity-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-line h2 {
  color: #17213a;
  font-size: 20px;
}

.activity-summary p {
  margin: 9px 0;
}

.activity-meta {
  gap: 24px;
  font-size: 13px;
}

.row-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.el-pagination {
  justify-content: center;
}

@media (max-width: 760px) {
  .activity-row,
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .row-actions {
    justify-content: flex-start;
  }
}
</style>
