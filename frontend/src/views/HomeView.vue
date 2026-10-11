<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { getActivityPage } from '@/api/activity'
import { ApiError } from '@/api/http'
import type { ActivityPageVO } from '@/types/api'
import { activityTypes, formatDateTime, getRegistrationPhase } from '@/utils/activity'

const activities = ref<ActivityPageVO[]>([])
const loading = ref(false)
const total = ref(0)
const pageSize = ref(10)
const route = useRoute()
const router = useRouter()

function readPage(value: unknown): number {
  const page = Number(value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

function readType(value: unknown): number | undefined {
  if (value === undefined || value === '') return undefined
  const type = Number(value)
  return Number.isInteger(type) && type >= 0 && type <= 5 ? type : undefined
}

const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  activityType: readType(route.query.activityType),
  page: readPage(route.query.page),
})

function syncLocation(): void {
  void router.replace(homeLocation())
}

function homeLocation() {
  return {
    name: 'home',
    query: {
      ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
      ...(filters.activityType !== undefined ? { activityType: String(filters.activityType) } : {}),
      page: String(filters.page),
    },
  }
}

function returnPath(): string {
  return router.resolve(homeLocation()).fullPath
}

async function loadActivities(): Promise<void> {
  loading.value = true
  try {
    const result = await getActivityPage(filters)
    activities.value = result.data?.pageResult ?? []
    total.value = result.data?.total ?? 0
    pageSize.value = result.data?.size ?? 10
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '活动列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  syncLocation()
  void loadActivities()
}

function resetFilters(): void {
  filters.keyword = ''
  filters.activityType = undefined
  filters.page = 1
  syncLocation()
  void loadActivities()
}

function changePage(page: number): void {
  filters.page = page
  syncLocation()
  void loadActivities()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(loadActivities)
</script>

<template>
  <main class="activities-page">
    <section class="hero">
      <div>
        <p class="eyebrow">DISCOVER CAMPUS</p>
        <h1>发现值得参与的校园活动</h1>
        <p>浏览最新活动，查看名额与报名时间。</p>
      </div>
      <div class="hero-decoration"><span></span><span></span><span></span></div>
    </section>

    <section class="content">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          maxlength="100"
          placeholder="搜索活动名称"
          size="large"
          @keyup.enter="search"
        />
        <el-select v-model="filters.activityType" clearable placeholder="全部类型" size="large">
          <el-option v-for="item in activityTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button size="large" type="primary" @click="search">搜索</el-button>
        <el-button size="large" @click="resetFilters">重置</el-button>
      </div>

      <div v-loading="loading" class="activity-grid">
        <RouterLink
          v-for="activity in activities"
          :key="activity.activityId"
          class="activity-card"
          :to="{
            name: 'activity-detail',
            params: { activityId: activity.activityId },
            query: { returnTo: returnPath() },
          }"
        >
          <div class="card-top">
            <el-tag :type="getRegistrationPhase(activity.registrationStartTime, activity.registrationEndTime).type">
              {{ getRegistrationPhase(activity.registrationStartTime, activity.registrationEndTime).label }}
            </el-tag>
            <span class="capacity">余 {{ activity.remainingCapacity }} / {{ activity.totalCapacity }}</span>
          </div>
          <h2>{{ activity.activityName }}</h2>
          <p class="location">{{ activity.activityLocation }}</p>
          <dl>
            <div>
              <dt>活动时间</dt>
              <dd>{{ formatDateTime(activity.startTime) }}</dd>
            </div>
            <div>
              <dt>报名截止</dt>
              <dd>{{ formatDateTime(activity.registrationEndTime) }}</dd>
            </div>
          </dl>
          <span class="detail-link">查看详情 →</span>
        </RouterLink>

        <el-empty v-if="!loading && activities.length === 0" description="暂时没有符合条件的活动" />
      </div>

      <el-pagination
        v-if="total > pageSize"
        background
        :current-page="filters.page"
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="total"
        @current-change="changePage"
      />
    </section>
  </main>
</template>

<style scoped>
.activities-page {
  padding-bottom: 70px;
}

.hero {
  position: relative;
  min-height: 270px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 50px max(5vw, calc((100vw - 1180px) / 2));
  overflow: hidden;
  color: #ffffff;
  background: linear-gradient(125deg, #172f72, #315bd9 58%, #6382e5);
}

.eyebrow {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  opacity: 0.72;
}

.hero h1 {
  margin-bottom: 15px;
  font-size: clamp(32px, 4.2vw, 54px);
  font-weight: 700;
  letter-spacing: -0.04em;
}

.hero > div > p:last-child {
  color: rgb(255 255 255 / 72%);
  font-size: 16px;
}

.hero-decoration {
  position: relative;
  width: 220px;
  height: 160px;
}

.hero-decoration span {
  position: absolute;
  display: block;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 28px;
  transform: rotate(-12deg);
}

.hero-decoration span:nth-child(1) { inset: 0 30px 30px 0; }
.hero-decoration span:nth-child(2) { inset: 28px 0 0 50px; }
.hero-decoration span:nth-child(3) { width: 54px; height: 54px; right: 30px; top: 18px; background: rgb(255 255 255 / 12%); }

.content {
  width: min(1180px, calc(100% - 40px));
  margin: -28px auto 0;
  position: relative;
  z-index: 2;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 190px auto auto;
  gap: 12px;
  padding: 18px;
  border: 1px solid #e5eaf2;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 16px 45px rgb(35 54 99 / 10%);
}

.activity-grid {
  min-height: 280px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 28px 0;
}

.activity-card {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  padding: 28px;
  color: inherit;
  text-decoration: none;
  border: 1px solid #e4e9f1;
  border-radius: 20px;
  background: #ffffff;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.activity-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 45px rgb(30 52 93 / 10%);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.capacity {
  color: #667085;
  font-size: 13px;
}

.activity-card h2 {
  margin: 24px 0 8px;
  color: #17213a;
  font-size: 22px;
  font-weight: 700;
}

.location {
  color: #667085;
}

dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 24px;
}

dt {
  margin-bottom: 4px;
  color: #98a2b3;
  font-size: 12px;
}

dd {
  color: #475467;
  font-size: 14px;
}

.detail-link {
  margin-top: auto;
  padding-top: 22px;
  color: #315bd9;
  font-weight: 600;
}

.el-empty {
  grid-column: 1 / -1;
}

.el-pagination {
  justify-content: center;
}

@media (max-width: 760px) {
  .hero-decoration { display: none; }
  .filter-bar { grid-template-columns: 1fr 1fr; }
  .filter-bar :first-child { grid-column: 1 / -1; }
  .activity-grid { grid-template-columns: 1fr; }
}
</style>
