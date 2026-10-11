import fs from 'node:fs'

const file = new URL('./CampusPass.apifox.json', import.meta.url)
const project = JSON.parse(fs.readFileSync(file, 'utf8'))

let nextCaseId = 900000000
let nextResponseId = 910000000

const clone = (value) => JSON.parse(JSON.stringify(value))

function walk(value, visitor) {
  if (Array.isArray(value)) {
    value.forEach((item) => walk(item, visitor))
    return
  }
  if (!value || typeof value !== 'object') return
  visitor(value)
  Object.values(value).forEach((child) => walk(child, visitor))
}

function sanitizeString(value) {
  return value
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '{{bearerToken}}')
    .replace(/(?<!\d)1[3-9]\d{9}(?!\d)/g, '13800000000')
    .replace(/("(?:oldPassword|newPassword|password)"\s*:\s*")[^"]*(")/gi, '$1Demo123!$2')
    .replace(/("code"\s*:\s*")\d{6}(")/gi, (_match, prefix, suffix) => `${prefix}000000${suffix}`)
}

function sanitize(value) {
  if (Array.isArray(value)) return value.map(sanitize)
  if (!value || typeof value !== 'object') {
    return typeof value === 'string' ? sanitizeString(value) : value
  }
  const cleaned = {}
  for (const [key, child] of Object.entries(value)) {
    if (key === 'requestResult') continue
    cleaned[key] = sanitize(child)
  }
  return cleaned
}

const cleanedProject = sanitize(project)

const apiByOperation = new Map()
function collectApis(nodes) {
  for (const node of nodes ?? []) {
    if (node.api?.operationId) apiByOperation.set(node.api.operationId, node.api)
    collectApis(node.items)
  }
}
collectApis(cleanedProject.apiCollection)

const authTemplateApi = apiByOperation.get('updateActivityDraft')
const authTemplate = clone(authTemplateApi.auth)
const securityTemplate = clone(authTemplateApi.securityScheme)

function replaceDeep(value, from, to) {
  if (Array.isArray(value)) return value.map((item) => replaceDeep(item, from, to))
  if (!value || typeof value !== 'object') {
    return typeof value === 'string' ? value.replaceAll(from, to) : value
  }
  for (const [key, child] of Object.entries(value)) value[key] = replaceDeep(child, from, to)
  return value
}

for (const api of apiByOperation.values()) {
  if (!api.path.startsWith('/auth/') || api.path === '/auth/logout') {
    api.auth = clone(authTemplate)
    api.securityScheme = clone(securityTemplate)
    if (api.path.startsWith('/admin/')) {
      replaceDeep(api.securityScheme, '{{bearerToken}}', '{{adminToken}}')
    }
  }
}

const statusNames = {
  200: '成功',
  400: '请求参数错误',
  401: '未认证或凭证无效',
  403: '无权执行此操作',
  404: '资源不存在',
  409: '业务状态冲突',
  429: '请求过于频繁',
}

function responseFor(api, status) {
  const existing = (api.responses ?? []).find((response) => Number(response.code) === status)
  if (existing) return String(existing.id)

  const id = nextResponseId++
  api.responses ??= []
  api.responses.push({
    id,
    code: String(status),
    name: statusNames[status] ?? '响应',
    headers: [],
    jsonSchema: {
      type: 'object',
      properties: {
        code: { type: 'integer', description: '业务状态码' },
        message: { type: 'string', description: '结果说明' },
        data: { nullable: true, description: '响应数据，失败时通常为空' },
      },
      required: ['code', 'message'],
    },
    itemSchema: null,
    description: statusNames[status] ?? '',
    contentType: 'json',
    mediaType: 'application/json',
    oasExtensions: {},
  })
  return String(id)
}

function ensureParameter(caseItem, api, group, name) {
  caseItem.parameters ??= { path: [], query: [], cookie: [], header: [] }
  caseItem.parameters[group] ??= []
  let parameter = caseItem.parameters[group].find(
    (item) => item.name === name || item.relatedName === name,
  )
  if (!parameter) {
    const definition = api.parameters?.[group]?.find((item) => item.name === name)
    parameter = {
      id: `${name}#case`,
      relatedId: definition?.id ?? `${name}#0`,
      relatedName: name,
      name,
      type: definition?.type ?? 'string',
      value: '',
      enable: true,
      description: '',
      isDelete: false,
    }
    caseItem.parameters[group].push(parameter)
  }
  return parameter
}

function setParameters(caseItem, api, group, values = {}) {
  for (const [name, value] of Object.entries(values)) {
    const parameter = ensureParameter(caseItem, api, group, name)
    parameter.value = String(value)
    parameter.enable = true
  }
}

function makeCase(api, definition) {
  const base = clone(api.cases?.[0] ?? {})
  base.id = nextCaseId++
  base.type = 'DEBUG_CASE'
  base.path = null
  base.name = definition.name
  base.responseId = responseFor(api, definition.status)
  base.requestResult = null
  base.visibility = 'PRIVATE'
  base.preProcessors = []
  base.postProcessors = []
  base.inheritPreProcessors = true
  base.inheritPostProcessors = true
  base.parameters ??= { path: [], query: [], cookie: [], header: [] }

  setParameters(base, api, 'path', definition.path)
  setParameters(base, api, 'query', definition.query)
  setParameters(base, api, 'header', definition.headers)

  if (definition.authToken) {
    base.auth = clone(authTemplate)
    base.securityScheme = clone(securityTemplate)
    replaceDeep(base.securityScheme, '{{bearerToken}}', definition.authToken)
  }

  if (definition.body !== undefined) {
    base.requestBody = {
      parameters: [],
      data: JSON.stringify(definition.body, null, 2),
      type: 'application/json',
      mediaType: 'application/json',
      generateMode: 'normal',
    }
  }
  return base
}

const futureActivity = {
  activityName: 'CampusPass 测试活动',
  activityDescription: '用于接口测试的公开示例数据',
  activityType: 0,
  activityLocation: '测试教学楼 A101',
  registrationStartTime: '2035-01-01T09:00:00',
  registrationEndTime: '2035-01-02T18:00:00',
  startTime: '2035-01-03T09:00:00',
  endTime: '2035-01-03T12:00:00',
  totalCapacity: 20,
}

const cases = {
  changePassword: [
    { name: '成功-需将旧密码替换为测试账号密码', status: 200, body: { oldPassword: 'OldDemo123!', newPassword: 'NewDemo456!' } },
    { name: '失败-原密码错误', status: 401, body: { oldPassword: 'WrongDemo123!', newPassword: 'NewDemo456!' } },
    { name: '失败-新密码只有数字', status: 400, body: { oldPassword: 'OldDemo123!', newPassword: '12345678' } },
    { name: '失败-旧密码为空', status: 400, body: { oldPassword: '', newPassword: 'NewDemo456!' } },
  ],
  getMyProfile: [
    { name: '成功-查询当前用户资料', status: 200 },
  ],
  updateMyNickName: [
    { name: '成功-修改昵称', status: 200, query: { name: '校园用户' } },
    { name: '失败-昵称包含空格', status: 400, query: { name: 'Campus User' } },
    { name: '失败-昵称长度不足', status: 400, query: { name: '中' } },
  ],
  forgetPassword: [
    { name: '成功-需先获取有效验证码', status: 200, body: { phone: '13800000000', code: '000000', newPassword: 'NewDemo456!' } },
    { name: '失败-验证码错误', status: 401, body: { phone: '13800000000', code: '999999', newPassword: 'NewDemo456!' } },
    { name: '失败-新密码格式不合法', status: 400, body: { phone: '13800000000', code: '000000', newPassword: '12345678' } },
  ],
  signUp: [
    { name: '成功-需使用未注册手机号和有效验证码', status: 200, body: { phone: '13800000000', userName: '测试用户', password: 'Demo123!', code: '000000', studentId: '20260000001' } },
    { name: '失败-手机号格式错误', status: 400, body: { phone: '12800000000', userName: '测试用户', password: 'Demo123!', code: '000000', studentId: '20260000001' } },
    { name: '失败-密码只有数字', status: 400, body: { phone: '13800000000', userName: '测试用户', password: '12345678', code: '000000', studentId: '20260000001' } },
    { name: '失败-学号格式错误', status: 400, body: { phone: '13800000000', userName: '测试用户', password: 'Demo123!', code: '000000', studentId: '20260001' } },
    { name: '失败-验证码不是六位数字', status: 400, body: { phone: '13800000000', userName: '测试用户', password: 'Demo123!', code: '12345', studentId: '20260000001' } },
  ],
  logOut: [
    { name: '成功-退出当前登录', status: 200 },
  ],
  loginWithPassword: [
    { name: '成功-需替换为测试账号', status: 200, body: { phone: '13800000000', password: 'Demo123!' } },
    { name: '失败-密码错误', status: 401, body: { phone: '13800000000', password: 'Wrong123!' } },
    { name: '失败-密码长度不足', status: 400, body: { phone: '13800000000', password: '123' } },
  ],
  loginWithCode: [
    { name: '成功-需先获取有效验证码', status: 200, body: { phone: '13800000000', code: '000000' } },
    { name: '失败-验证码错误', status: 401, body: { phone: '13800000000', code: '999999' } },
    { name: '失败-验证码格式错误', status: 400, body: { phone: '13800000000', code: '12345' } },
  ],
  sendCode: [
    { name: '成功-发送验证码', status: 200, query: { phone: '13800000000' } },
    { name: '失败-手机号格式错误', status: 400, query: { phone: '12800000000' } },
    { name: '失败-短时间重复发送（需连续执行）', status: 429, query: { phone: '13800000000' } },
  ],
  createActivity: [
    { name: '成功-创建活动草稿', status: 200, body: futureActivity },
    { name: '失败-活动名称为空', status: 400, body: { ...futureActivity, activityName: '' } },
    { name: '失败-活动类型超出范围', status: 400, body: { ...futureActivity, activityType: 6 } },
    { name: '失败-总名额为零', status: 400, body: { ...futureActivity, totalCapacity: 0 } },
    { name: '失败-报名截止晚于活动开始', status: 400, body: { ...futureActivity, registrationEndTime: '2035-01-04T18:00:00' } },
  ],
  updateActivityDraft: [
    { name: '成功-修改自己的草稿', status: 200, path: { activityId: '{{draftActivityId}}' }, body: futureActivity },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' }, body: futureActivity },
    { name: '失败-不是活动创建者', status: 403, path: { activityId: '{{otherUserActivityId}}' }, body: futureActivity },
    { name: '失败-活动已发布', status: 409, path: { activityId: '{{publishedActivityId}}' }, body: futureActivity },
    { name: '失败-活动时间顺序错误', status: 400, path: { activityId: '{{draftActivityId}}' }, body: { ...futureActivity, startTime: '2034-12-31T09:00:00' } },
  ],
  getActivityPageList: [
    { name: '成功-查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
  ],
  publishActivity: [
    { name: '成功-发布自己的草稿', status: 200, path: { activityId: '{{draftActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
    { name: '失败-活动状态不允许发布', status: 409, path: { activityId: '{{publishedActivityId}}' } },
  ],
  cancelActivity: [
    { name: '成功-取消自己的活动', status: 200, path: { activityId: '{{publishedActivityId}}' } },
    { name: '失败-不是活动创建者', status: 403, path: { activityId: '{{otherUserActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
  ],
  getActivityDetail: [
    { name: '成功-查询活动详情', status: 200, path: { activityId: '{{publishedActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
  ],
  getMyActivityDetail: [
    { name: '成功-查询自己创建的活动', status: 200, path: { activityId: '{{draftActivityId}}' } },
    { name: '失败-不是活动创建者', status: 403, path: { activityId: '{{otherUserActivityId}}' } },
  ],
  getMyActivityPageList: [
    { name: '成功-查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
  ],
  getActivityPageList_1: [
    { name: '成功-按关键词查询', status: 200, query: { keyword: '校园', pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { keyword: '校园', pages: 0 } },
  ],
  getActivityPageList_2: [
    { name: '成功-按类型和关键词查询', status: 200, path: { activityType: 0 }, query: { keyword: '校园', pages: 1 } },
    { name: '失败-活动类型超出范围', status: 400, path: { activityType: 6 }, query: { keyword: '校园', pages: 1 } },
  ],
  getActivityPageList_3: [
    { name: '成功-按活动类型查询', status: 200, path: { activityType: 0 }, query: { pages: 1 } },
    { name: '失败-活动类型超出范围', status: 400, path: { activityType: 6 }, query: { pages: 1 } },
  ],
  registerActivity: [
    { name: '成功-报名或进入候补', status: 200, path: { activityId: '{{publishedActivityId}}' } },
    { name: '失败-重复报名', status: 409, path: { activityId: '{{alreadyJoinedActivityId}}' } },
    { name: '失败-不在报名时间内', status: 409, path: { activityId: '{{closedActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
  ],
  cancelRegistration: [
    { name: '成功-取消有效报名', status: 200, path: { activityId: '{{registeredActivityId}}' } },
    { name: '失败-当前没有有效报名', status: 409, path: { activityId: '{{notJoinedActivityId}}' } },
  ],
  getRegistrationStatus: [
    { name: '成功-查询当前报名状态', status: 200, path: { activityId: '{{publishedActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
  ],
  pageMyRegistration: [
    { name: '成功-查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
  ],
  getMyRegistration_1: [
    { name: '成功-创建者查看报名名单', status: 200, path: { activityId: '{{publishedActivityId}}' }, query: { pages: 1 } },
    { name: '失败-不是活动创建者', status: 403, path: { activityId: '{{otherUserActivityId}}' }, query: { pages: 1 } },
  ],
  cancelWaitList: [
    { name: '成功-取消候补', status: 200, path: { activityId: '{{waitlistedActivityId}}' } },
    { name: '失败-当前不在候补中', status: 409, path: { activityId: '{{notJoinedActivityId}}' } },
  ],
  pageMyWaitLists: [
    { name: '成功-查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
  ],
  getMyRegistration: [
    { name: '成功-创建者查看候补名单', status: 200, path: { activityId: '{{publishedActivityId}}' }, query: { pages: 1 } },
    { name: '失败-不是活动创建者', status: 403, path: { activityId: '{{otherUserActivityId}}' }, query: { pages: 1 } },
  ],
  enableUser: [
    { name: '成功-管理员启用用户', status: 200, path: { userId: '{{disabledUserId}}' } },
    { name: '失败-用户不存在', status: 404, path: { userId: '999999999' } },
    { name: '失败-普通用户无权限', status: 403, path: { userId: '{{disabledUserId}}' }, authToken: '{{bearerToken}}' },
  ],
  disableUser: [
    { name: '成功-管理员禁用用户', status: 200, path: { userId: '{{enabledUserId}}' } },
    { name: '失败-用户不存在', status: 404, path: { userId: '999999999' } },
    { name: '失败-普通用户无权限', status: 403, path: { userId: '{{enabledUserId}}' }, authToken: '{{bearerToken}}' },
  ],
  forceCancelActivity: [
    { name: '成功-管理员强制取消活动', status: 200, path: { activityId: '{{publishedActivityId}}' } },
    { name: '失败-活动不存在', status: 404, path: { activityId: '999999999' } },
    { name: '失败-普通用户无权限', status: 403, path: { activityId: '{{publishedActivityId}}' }, authToken: '{{bearerToken}}' },
  ],
  pageAllUsers: [
    { name: '成功-管理员查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
    { name: '失败-普通用户无权限', status: 403, query: { pages: 1 }, authToken: '{{bearerToken}}' },
  ],
  pageAllActivities: [
    { name: '成功-管理员查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
    { name: '失败-普通用户无权限', status: 403, query: { pages: 1 }, authToken: '{{bearerToken}}' },
  ],
  queryWithRequestId: [
    { name: '成功-查询自己的异步请求', status: 200, path: { requestId: '{{requestId}}' } },
    { name: '失败-请求不存在', status: 404, path: { requestId: '00000000-0000-0000-0000-000000000001' } },
    { name: '失败-请求编号格式错误', status: 400, path: { requestId: 'not-a-uuid' } },
  ],
  pageMyRequest: [
    { name: '成功-查询第一页', status: 200, query: { pages: 1 } },
    { name: '失败-页码为零', status: 400, query: { pages: 0 } },
  ],
}

for (const [operationId, definitions] of Object.entries(cases)) {
  const api = apiByOperation.get(operationId)
  if (!api) throw new Error(`找不到接口 operationId: ${operationId}`)
  api.cases = definitions.map((definition) => makeCase(api, definition))
}

walk(cleanedProject, (object) => {
  if ('requestResult' in object) delete object.requestResult
})

const output = JSON.stringify(cleanedProject, null, 2) + '\n'
JSON.parse(output)
fs.writeFileSync(file, output, 'utf8')

const caseCount = [...apiByOperation.values()].reduce((sum, api) => sum + (api.cases?.length ?? 0), 0)
console.log(`Prepared ${apiByOperation.size} APIs with ${caseCount} test cases.`)
