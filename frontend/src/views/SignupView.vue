<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import { signUp } from '@/api/auth'
import { ApiError } from '@/api/http'
import AuthCard from '@/components/auth/AuthCard.vue'
import { useVerificationCode } from '@/composables/useVerificationCode'

interface SignupForm {
  phone: string
  userName: string
  studentId: string
  password: string
  confirmPassword: string
  code: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive<SignupForm>({
  phone: '',
  userName: '',
  studentId: '',
  password: '',
  confirmPassword: '',
  code: '',
})
const { countdown, sending, requestCode } = useVerificationCode(() => form.phone)

const passwordPattern = /^(?=.*(?:[A-Za-z].*[0-9]|[0-9].*[A-Za-z]|[A-Za-z].*[^A-Za-z0-9]|[^A-Za-z0-9].*[A-Za-z]|[0-9].*[^A-Za-z0-9]|[^A-Za-z0-9].*[0-9]))[!-~]{6,32}$/

const rules: FormRules<SignupForm> = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^20[12]\d{8}$/, message: '学号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: passwordPattern, message: '密码为 6～32 位，且至少包含字母、数字、符号中的两种', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须是六位数字', trigger: 'blur' },
  ],
}

async function handleSignup(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await signUp({
      phone: form.phone,
      userName: form.userName,
      studentId: form.studentId,
      password: form.password,
      code: form.code,
    })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="创建账号" subtitle="填写校园身份信息，开始发现活动">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSignup">
      <div class="two-columns">
        <el-form-item label="用户名" prop="userName">
          <el-input v-model="form.userName" maxlength="50" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="学号" prop="studentId">
          <el-input v-model="form.studentId" maxlength="11" placeholder="请输入学号" size="large" />
        </el-form-item>
      </div>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" size="large" />
      </el-form-item>
      <el-form-item label="验证码" prop="code">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="六位验证码" size="large" />
          <el-button :disabled="countdown > 0" :loading="sending" size="large" @click="requestCode">
            {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" maxlength="32" placeholder="请输入密码" show-password size="large" type="password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" maxlength="32" placeholder="再次输入密码" show-password size="large" type="password" />
      </el-form-item>
      <el-button class="submit-button" :loading="loading" native-type="submit" size="large" type="primary">注册</el-button>
    </el-form>
    <template #footer>已有账号？<RouterLink to="/login">返回登录</RouterLink></template>
  </AuthCard>
</template>

<style scoped>
.two-columns,
.code-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.code-row {
  width: 100%;
  grid-template-columns: 1fr 126px;
}

.submit-button {
  width: 100%;
  margin-top: 8px;
}

a {
  color: #315bd9;
  text-decoration: none;
}

@media (max-width: 560px) {
  .two-columns {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
