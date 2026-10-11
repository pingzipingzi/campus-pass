<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { loginWithCode, loginWithPassword, sendCode } from '@/api/auth'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type LoginMode = 'password' | 'code'

interface LoginForm {
  phone: string
  password: string
  code: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const mode = ref<LoginMode>('password')
const loading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | undefined

const form = reactive<LoginForm>({
  phone: '',
  password: '',
  code: '',
})

const rules = computed<FormRules<LoginForm>>(() => ({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  password:
    mode.value === 'password'
      ? [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, max: 32, message: '密码长度为 6～32 位', trigger: 'blur' },
        ]
      : [],
  code:
    mode.value === 'code'
      ? [
          { required: true, message: '请输入验证码', trigger: 'blur' },
          { pattern: /^\d{6}$/, message: '验证码必须是六位数字', trigger: 'blur' },
        ]
      : [],
}))

async function handleSendCode(): Promise<void> {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }

  try {
    await sendCode(form.phone)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = undefined
      }
    }, 1000)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '验证码发送失败')
  }
}

async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const result =
      mode.value === 'password'
        ? await loginWithPassword({ phone: form.phone, password: form.password })
        : await loginWithCode({ phone: form.phone, code: form.code })

    if (!result.data) {
      throw new ApiError('登录响应缺少用户信息')
    }

    authStore.saveLogin(result.data)
    ElMessage.success(`欢迎回来，${result.data.userName}`)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function switchMode(nextMode: LoginMode): void {
  mode.value = nextMode
  formRef.value?.clearValidate()
}

onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <main class="login-page">
    <section class="intro-panel">
      <div class="brand">
        <span class="brand-mark">CP</span>
        <span>CampusPass</span>
      </div>
      <div class="intro-copy">
        <p class="eyebrow">YOUR CAMPUS, YOUR MOMENT</p>
        <h1>发现校园精彩，<br />不错过每一次参与。</h1>
        <p>活动预约、报名与候补，一站完成。</p>
      </div>
      <p class="intro-footer">CampusPass · 校园活动服务平台</p>
    </section>

    <section class="form-panel">
      <div class="login-card">
        <div class="mobile-brand">
          <span class="brand-mark">CP</span>
          <span>CampusPass</span>
        </div>
        <p class="welcome-label">欢迎使用</p>
        <h2>登录 CampusPass</h2>
        <p class="subtitle">使用你的账号继续探索校园活动</p>

        <div class="mode-switch">
          <button :class="{ active: mode === 'password' }" type="button" @click="switchMode('password')">
            密码登录
          </button>
          <button :class="{ active: mode === 'code' }" type="button" @click="switchMode('code')">
            验证码登录
          </button>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" size="large" />
          </el-form-item>

          <el-form-item v-if="mode === 'password'" label="密码" prop="password">
            <el-input
              v-model="form.password"
              maxlength="32"
              placeholder="请输入密码"
              show-password
              size="large"
              type="password"
            />
            <RouterLink class="forgot-link" to="/forgot-password">忘记密码？</RouterLink>
          </el-form-item>

          <el-form-item v-else label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="form.code" maxlength="6" placeholder="六位验证码" size="large" />
              <el-button :disabled="countdown > 0" size="large" @click="handleSendCode">
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-button class="login-button" :loading="loading" native-type="submit" size="large" type="primary">
            登录
          </el-button>
        </el-form>

        <p class="signup-hint">还没有账号？<RouterLink to="/signup">立即注册</RouterLink></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 46%) 1fr;
  background: #f7f9fc;
}

.intro-panel {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 5vw;
  color: #ffffff;
  background:
    radial-gradient(circle at 15% 15%, rgb(104 142 255 / 70%), transparent 32%),
    linear-gradient(145deg, #132a68 0%, #315bd9 62%, #5e7ce2 100%);
}

.brand,
.mobile-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 21px;
  font-weight: 700;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #315bd9;
  font-weight: 800;
  border-radius: 12px;
  background: #ffffff;
}

.intro-copy {
  margin-top: -10vh;
}

.eyebrow,
.welcome-label {
  margin-bottom: 18px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.intro-copy h1 {
  margin-bottom: 24px;
  font-size: clamp(36px, 4vw, 58px);
  font-weight: 700;
  line-height: 1.22;
  letter-spacing: -0.04em;
}

.intro-copy > p:last-child,
.intro-footer {
  color: rgb(255 255 255 / 72%);
}

.form-panel {
  display: grid;
  place-items: center;
  padding: 40px;
}

.login-card {
  width: min(420px, 100%);
}

.mobile-brand {
  display: none;
  margin-bottom: 48px;
  color: #17213a;
}

.mobile-brand .brand-mark {
  color: #ffffff;
  background: #315bd9;
}

.welcome-label {
  margin-bottom: 8px;
  color: #315bd9;
}

h2 {
  color: #17213a;
  font-size: 34px;
  font-weight: 700;
}

.subtitle {
  margin-top: 10px;
  color: #7a8499;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 36px 0 28px;
  padding: 4px;
  border-radius: 12px;
  background: #edf1f7;
}

.mode-switch button {
  padding: 10px;
  color: #727d92;
  font: inherit;
  border: 0;
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
}

.mode-switch button.active {
  color: #17213a;
  font-weight: 600;
  background: #ffffff;
  box-shadow: 0 3px 10px rgb(30 48 80 / 8%);
}

.code-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 126px;
  gap: 10px;
}

.login-button {
  width: 100%;
  margin-top: 10px;
  --el-button-bg-color: #315bd9;
  --el-button-border-color: #315bd9;
  --el-button-hover-bg-color: #244bc5;
  --el-button-hover-border-color: #244bc5;
}

.signup-hint {
  margin-top: 28px;
  color: #8a94a7;
  text-align: center;
}

.signup-hint span {
  color: #315bd9;
}

.signup-hint a,
.forgot-link {
  color: #315bd9;
  text-decoration: none;
}

.forgot-link {
  display: block;
  width: 100%;
  margin-top: 8px;
  font-size: 13px;
  text-align: right;
}

@media (max-width: 820px) {
  .login-page {
    display: block;
  }

  .intro-panel {
    display: none;
  }

  .form-panel {
    min-height: 100vh;
    padding: 32px 24px;
  }

  .mobile-brand {
    display: flex;
  }
}
</style>
