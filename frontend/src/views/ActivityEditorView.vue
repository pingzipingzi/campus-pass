<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { createActivity, getMyActivityDetail, updateActivityDraft } from '@/api/activity'
import { ApiError } from '@/api/http'
import type { ActivityDTO } from '@/types/api'
import { activityTypes } from '@/utils/activity'

interface ActivityForm {
  activityName: string
  activityDescription: string
  activityType?: number
  activityLocation: string
  registrationStartTime: string
  registrationEndTime: string
  startTime: string
  endTime: string
  totalCapacity: number
}

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const form = reactive<ActivityForm>({
  activityName: '',
  activityDescription: '',
  activityType: undefined,
  activityLocation: '',
  registrationStartTime: '',
  registrationEndTime: '',
  startTime: '',
  endTime: '',
  totalCapacity: 20,
})

const activityId = computed(() => {
  const value = Number(route.params.activityId)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
})
const editing = computed(() => Boolean(activityId.value))

function sourceLocation(): string {
  const value = Array.isArray(route.query.returnTo) ? route.query.returnTo[0] : route.query.returnTo
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/my-activities'
}

function disablePastDate(date: Date): boolean {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function validateFutureTime(_rule: unknown, value: string, callback: (error?: Error) => void): void {
  if (!value) {
    callback()
    return
  }
  const timestamp = new Date(value).getTime()
  if (Number.isNaN(timestamp)) {
    callback(new Error('时间格式不正确'))
    return
  }
  callback(timestamp < Date.now() ? new Error('时间不能早于当前时间') : undefined)
}

function validateTimeOrder(_rule: unknown, _value: string, callback: (error?: Error) => void): void {
  const values = [form.registrationStartTime, form.registrationEndTime, form.startTime, form.endTime]
  if (values.some((value) => !value)) {
    callback()
    return
  }
  const timestamps = values.map((value) => new Date(value).getTime())
  const correct = timestamps.every((value, index) => index === 0 || timestamps[index - 1]! < value)
  callback(correct ? undefined : new Error('时间顺序必须为：报名开始 < 报名截止 < 活动开始 < 活动结束'))
}

const rules: FormRules<ActivityForm> = {
  activityName: [
    { required: true, message: '请输入活动名称', trigger: 'blur' },
    { max: 100, message: '活动名称不能超过 100 个字符', trigger: 'blur' },
  ],
  activityType: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  activityLocation: [
    { required: true, message: '请输入活动地点', trigger: 'blur' },
    { max: 100, message: '活动地点不能超过 100 个字符', trigger: 'blur' },
  ],
  registrationStartTime: [
    { required: true, message: '请选择报名开始时间', trigger: 'change' },
    { validator: validateFutureTime, trigger: 'change' },
    { validator: validateTimeOrder, trigger: 'change' },
  ],
  registrationEndTime: [
    { required: true, message: '请选择报名截止时间', trigger: 'change' },
    { validator: validateFutureTime, trigger: 'change' },
    { validator: validateTimeOrder, trigger: 'change' },
  ],
  startTime: [
    { required: true, message: '请选择活动开始时间', trigger: 'change' },
    { validator: validateFutureTime, trigger: 'change' },
    { validator: validateTimeOrder, trigger: 'change' },
  ],
  endTime: [
    { required: true, message: '请选择活动结束时间', trigger: 'change' },
    { validator: validateFutureTime, trigger: 'change' },
    { validator: validateTimeOrder, trigger: 'change' },
  ],
  totalCapacity: [
    { required: true, message: '请输入活动名额', trigger: 'blur' },
    { type: 'number', min: 1, max: 2000, message: '活动名额应在 1～2000 之间', trigger: 'blur' },
  ],
}

async function loadActivity(): Promise<void> {
  if (!activityId.value) return
  loading.value = true
  try {
    const result = await getMyActivityDetail(activityId.value)
    const activity = result.data
    if (!activity) throw new ApiError('活动不存在')
    if (activity.status !== 0) {
      ElMessage.warning('只有草稿活动可以编辑')
      await router.replace({ name: 'activity-manage', params: { activityId: activity.activityId } })
      return
    }
    Object.assign(form, {
      activityName: activity.activityName,
      activityDescription: activity.activityDescription ?? '',
      activityType: activity.activityType,
      activityLocation: activity.activityLocation,
      registrationStartTime: activity.registrationStartTime,
      registrationEndTime: activity.registrationEndTime,
      startTime: activity.startTime,
      endTime: activity.endTime,
      totalCapacity: activity.totalCapacity,
    })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '草稿加载失败')
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || form.activityType === undefined) return

  const data: ActivityDTO = {
    activityName: form.activityName.trim(),
    activityDescription: form.activityDescription.trim() || undefined,
    activityType: form.activityType,
    activityLocation: form.activityLocation.trim(),
    registrationStartTime: form.registrationStartTime,
    registrationEndTime: form.registrationEndTime,
    startTime: form.startTime,
    endTime: form.endTime,
    totalCapacity: form.totalCapacity,
  }

  saving.value = true
  try {
    if (activityId.value) await updateActivityDraft(activityId.value, data)
    else await createActivity(data)
    ElMessage.success(activityId.value ? '草稿已保存' : '活动草稿已创建')
    await router.replace(sourceLocation())
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '保存活动失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadActivity)
</script>

<template>
  <main class="editor-page">
    <section class="page-heading">
      <p>ACTIVITY EDITOR</p>
      <h1>{{ editing ? '编辑活动草稿' : '创建活动' }}</h1>
      <span>保存后活动仍为草稿，需要在“我的活动”中手动发布</span>
    </section>

    <section v-loading="loading" class="editor-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <div class="two-columns">
          <el-form-item label="活动名称" prop="activityName">
            <el-input v-model="form.activityName" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="活动类型" prop="activityType">
            <el-select v-model="form.activityType" placeholder="请选择活动类型">
              <el-option v-for="item in activityTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="活动介绍" prop="activityDescription">
          <el-input v-model="form.activityDescription" :rows="5" placeholder="介绍活动内容和参与须知" type="textarea" />
        </el-form-item>

        <div class="two-columns">
          <el-form-item label="活动地点" prop="activityLocation">
            <el-input v-model="form.activityLocation" maxlength="100" />
          </el-form-item>
          <el-form-item label="总名额" prop="totalCapacity">
            <el-input-number v-model="form.totalCapacity" :max="2000" :min="1" controls-position="right" />
          </el-form-item>
        </div>

        <div class="two-columns">
          <el-form-item label="报名开始时间" prop="registrationStartTime">
            <el-date-picker
              v-model="form.registrationStartTime"
              :disabled-date="disablePastDate"
              placeholder="选择日期和时间"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="报名截止时间" prop="registrationEndTime">
            <el-date-picker
              v-model="form.registrationEndTime"
              :disabled-date="disablePastDate"
              placeholder="选择日期和时间"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="活动开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              :disabled-date="disablePastDate"
              placeholder="选择日期和时间"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="活动结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              :disabled-date="disablePastDate"
              placeholder="选择日期和时间"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button size="large" @click="router.push(sourceLocation())">取消</el-button>
          <el-button :loading="saving" native-type="submit" size="large" type="primary">保存草稿</el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.editor-page {
  width: min(900px, calc(100% - 40px));
  margin: 0 auto;
  padding: 52px 0 80px;
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

.page-heading span {
  color: #7a8499;
}

.editor-card {
  min-height: 420px;
  margin-top: 30px;
  padding: 32px;
  border: 1px solid #e3e8f0;
  border-radius: 20px;
  background: #fff;
}

.two-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.el-select,
.el-date-editor,
.el-input-number {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 680px) {
  .two-columns {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .editor-card {
    padding: 22px;
  }
}
</style>
