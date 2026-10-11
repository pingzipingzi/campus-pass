import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { sendCode } from '@/api/auth'
import { ApiError } from '@/api/http'

export function useVerificationCode(getPhone: () => string) {
  const countdown = ref(0)
  const sending = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  async function requestCode(): Promise<void> {
    const phone = getPhone()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      ElMessage.warning('请先输入正确的手机号')
      return
    }

    sending.value = true
    try {
      await sendCode(phone)
      ElMessage.success('验证码已发送')
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value -= 1
        if (countdown.value <= 0 && timer) {
          clearInterval(timer)
          timer = undefined
        }
      }, 1000)
    } catch (error) {
      ElMessage.error(error instanceof ApiError ? error.message : '验证码发送失败')
    } finally {
      sending.value = false
    }
  }

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })

  return { countdown, sending, requestCode }
}
