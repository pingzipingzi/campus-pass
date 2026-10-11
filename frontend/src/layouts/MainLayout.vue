<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

async function handleLogout(): Promise<void> {
  await authStore.logout()
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">CP</span>
        <span>CampusPass</span>
      </RouterLink>
      <nav>
        <RouterLink :class="{ active: route.name === 'home' || route.name === 'activity-detail' }" to="/">
          发现活动
        </RouterLink>
        <RouterLink :class="{ active: route.name === 'records' }" to="/records">我的记录</RouterLink>
        <RouterLink
          :class="{ active: ['my-activities', 'activity-create', 'activity-edit', 'activity-manage'].includes(String(route.name)) }"
          to="/my-activities"
        >
          我的活动
        </RouterLink>
        <RouterLink v-if="authStore.isAdmin" :class="{ active: route.name === 'admin' }" to="/admin">
          平台管理
        </RouterLink>
        <RouterLink :class="{ active: route.name === 'profile' }" to="/me">个人中心</RouterLink>
      </nav>
      <div class="user-actions">
        <el-tag v-if="authStore.isAdmin" type="warning">管理员</el-tag>
        <span>{{ authStore.currentUser?.userName }}</span>
        <el-button text @click="handleLogout">退出</el-button>
      </div>
    </header>
    <RouterView />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f5f7fb;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 70px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 5vw;
  border-bottom: 1px solid #e7ebf2;
  background: rgb(255 255 255 / 92%);
  backdrop-filter: blur(16px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: #17213a;
  font-size: 19px;
  font-weight: 700;
  text-decoration: none;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: #ffffff;
  font-weight: 800;
  border-radius: 11px;
  background: #315bd9;
}

nav a {
  padding: 23px 12px 20px;
  color: #667085;
  text-decoration: none;
  border-bottom: 3px solid transparent;
}

  nav a.active {
  color: #315bd9;
  font-weight: 600;
  border-bottom-color: #315bd9;
}

.user-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: #475467;
}

@media (max-width: 680px) {
  .app-header {
    grid-template-columns: 1fr auto;
  }

  nav {
    display: none;
  }

  .user-actions > span:not(.el-tag) {
    display: none;
  }
}
</style>
