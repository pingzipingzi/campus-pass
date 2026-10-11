<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'

import { resetPassword } from '@/api/auth'
import { ApiError } from '@/api/http'
import AuthCard from '@/components/auth/AuthCard.vue'
import { useVerificationCode } from '@/composables/useVerificationCode'

interface ResetForm {
  phone: string
  code: string
  newPassword: string
  confirmPassword: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive<ResetForm>({ phone: '', code: '', newPassword: '', confirmPassword: '' })
const { countdown, sending, requestCode } = useVerificationCode(() => form.phone)
const passwordPattern = /^(?=.*(?:[A-Za-z].*[0-9]|[0-9].*[A-Za-z]|[A-Za-z].*[^A-Za-z0-9]|[^A-Za-z0-9].*[A-Za-z]|[0-9].*[^A-Za-z0-9]|[^A-Za-z0-9].*[0-9]))[!-~]{6,32}$/

const rules: FormRules<ResetForm> = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须是六位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: passwordPattern, message: '密码为 6～32 位，且至少包含字母、数字、符号中的两种', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleReset(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await resetPassword({ phone: form.phone, code: form.code, newPassword: form.newPassword })
    ElMessage.success('密码已重置，请重新登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '密码重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="找回密码" subtitle="验证手机号后设置一个新密码">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleReset">
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
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" maxlength="32" placeholder="请输入新密码" show-password size="large" type="password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" maxlength="32" placeholder="再次输入新密码" show-password size="large" type="password" />
      </el-form-item>
      <el-button class="submit-button" :loading="loading" native-type="submit" size="large" type="primary">确认重置</el-button>
    </el-form>
    <template #footer>想起密码了？<RouterLink to="/login">返回登录</RouterLink></template>
  </AuthCard>
</template>

<style scoped>
.code-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 126px;
  gap: 10px;
}

.submit-button {
  width: 100%;
  margin-top: 8px;
}

a {
  color: #315bd9;
  text-decoration: none;
}
</style>
