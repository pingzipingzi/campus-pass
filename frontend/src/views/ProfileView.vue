<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { changePassword, getMyProfile, updateMyName } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { UserVO } from '@/types/api'

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const router = useRouter()
const authStore = useAuthStore()
const profile = ref<UserVO>()
const loading = ref(false)
const nameDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const savingName = ref(false)
const changingPassword = ref(false)
const passwordFormRef = ref<FormInstance>()
const newName = ref('')
const passwordForm = reactive<PasswordForm>({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordPattern = /^(?=.*(?:[A-Za-z].*[0-9]|[0-9].*[A-Za-z]|[A-Za-z].*[^A-Za-z0-9]|[^A-Za-z0-9].*[A-Za-z]|[0-9].*[^A-Za-z0-9]|[^A-Za-z0-9].*[0-9]))[!-~]{6,32}$/

const avatarText = computed(() => profile.value?.userName?.slice(0, 1).toUpperCase() || 'U')
const maskedPhone = computed(() => profile.value?.phone?.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2'))

const passwordRules: FormRules<PasswordForm> = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: passwordPattern, message: '密码为 6～32 位，且至少包含字母、数字、符号中的两种', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function formatCreatedAt(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(date)
}

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    const result = await getMyProfile()
    profile.value = result.data
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '个人资料加载失败')
  } finally {
    loading.value = false
  }
}

function openNameDialog(): void {
  newName.value = profile.value?.userName ?? ''
  nameDialogVisible.value = true
}

function isValidName(name: string): boolean {
  if (!name || /[\s\p{Cc}]/u.test(name)) return false
  const weightedLength = Array.from(name).reduce((sum, char) => sum + (/\p{Script=Han}/u.test(char) ? 2 : 1), 0)
  return weightedLength >= 4 && weightedLength <= 20
}

async function saveName(): Promise<void> {
  if (!isValidName(newName.value)) {
    ElMessage.warning('用户名不能包含空白字符，中文 2～10 个，英文 4～20 个')
    return
  }
  if (newName.value === profile.value?.userName) {
    nameDialogVisible.value = false
    return
  }

  savingName.value = true
  try {
    await updateMyName(newName.value)
    authStore.updateUserName(newName.value)
    await loadProfile()
    nameDialogVisible.value = false
    ElMessage.success('用户名已更新')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '用户名更新失败')
  } finally {
    savingName.value = false
  }
}

function openPasswordDialog(): void {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

async function savePassword(): Promise<void> {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  try {
    await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    authStore.clearSession()
    ElMessage.success('密码修改成功，请重新登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <main v-loading="loading" class="profile-page">
    <section class="page-heading">
      <p>ACCOUNT CENTER</p>
      <h1>个人中心</h1>
      <span>管理个人资料与账号安全</span>
    </section>

    <section v-if="profile" class="profile-grid">
      <article class="profile-summary">
        <div class="avatar">{{ avatarText }}</div>
        <h2>{{ profile.userName }}</h2>
        <el-tag :type="profile.role === 1 ? 'warning' : 'primary'">
          {{ profile.role === 1 ? '管理员' : '普通用户' }}
        </el-tag>
        <p>加入于 {{ formatCreatedAt(profile.createTime) }}</p>
      </article>

      <article class="settings-card">
        <div class="card-heading">
          <div>
            <h2>基本资料</h2>
            <p>这些信息用于识别你的校园身份</p>
          </div>
          <el-button @click="openNameDialog">修改用户名</el-button>
        </div>
        <dl>
          <div><dt>用户名</dt><dd>{{ profile.userName }}</dd></div>
          <div><dt>学号</dt><dd>{{ profile.studentId }}</dd></div>
          <div><dt>手机号</dt><dd>{{ maskedPhone }}</dd></div>
          <div><dt>账号状态</dt><dd>{{ profile.status === 0 ? '正常' : '已禁用' }}</dd></div>
        </dl>

        <div class="security-row">
          <div>
            <h2>账号安全</h2>
            <p>定期修改密码有助于保护你的账号</p>
          </div>
          <el-button type="primary" @click="openPasswordDialog">修改密码</el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="nameDialogVisible" title="修改用户名" width="min(420px, 90vw)">
      <el-input v-model="newName" maxlength="20" placeholder="输入新的用户名" size="large" />
      <p class="dialog-hint">中文 2～10 个，英文 4～20 个，不能包含空格。</p>
      <template #footer>
        <el-button @click="nameDialogVisible = false">取消</el-button>
        <el-button :loading="savingName" type="primary" @click="saveName">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="min(460px, 90vw)">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" show-password size="large" type="password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" maxlength="32" show-password size="large" type="password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" maxlength="32" show-password size="large" type="password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button :loading="changingPassword" type="primary" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.profile-page {
  width: min(1060px, calc(100% - 40px));
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

.page-heading span,
.card-heading p,
.security-row p {
  color: #7a8499;
}

.profile-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 22px;
  margin-top: 32px;
}

.profile-summary,
.settings-card {
  border: 1px solid #e3e8f0;
  border-radius: 20px;
  background: #ffffff;
}

.profile-summary {
  align-self: start;
  padding: 38px 24px;
  text-align: center;
}

.avatar {
  width: 84px;
  height: 84px;
  display: grid;
  place-items: center;
  margin: 0 auto 20px;
  color: #ffffff;
  font-size: 32px;
  font-weight: 700;
  border-radius: 24px;
  background: linear-gradient(135deg, #315bd9, #7790e5);
}

.profile-summary h2,
.card-heading h2,
.security-row h2 {
  color: #17213a;
  font-size: 19px;
  font-weight: 700;
}

.profile-summary .el-tag { margin: 12px 0 20px; }
.profile-summary p { color: #98a2b3; font-size: 13px; }
.settings-card { padding: 32px; }

.card-heading,
.security-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.card-heading p,
.security-row p { margin-top: 5px; font-size: 14px; }

dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  margin: 28px 0;
  overflow: hidden;
  border: 1px solid #edf0f5;
  border-radius: 14px;
  background: #edf0f5;
}

dl div { padding: 18px; background: #fafbfc; }
dt { margin-bottom: 5px; color: #98a2b3; font-size: 12px; }
dd { color: #344054; font-weight: 600; }

.security-row {
  padding-top: 28px;
  border-top: 1px solid #edf0f5;
}

.dialog-hint {
  margin-top: 10px;
  color: #98a2b3;
  font-size: 12px;
}

@media (max-width: 760px) {
  .profile-grid { grid-template-columns: 1fr; }
  dl { grid-template-columns: 1fr; }
  .card-heading,
  .security-row { align-items: flex-start; flex-direction: column; }
}
</style>
