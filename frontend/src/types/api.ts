export interface ApiResult<T> {
  code: number
  message: string
  data?: T
}

export interface LoginVO {
  token: string
  userId: number
  userName: string
  role: number
}

export type CurrentUser = Omit<LoginVO, 'token'>

export interface PasswordLoginDTO {
  phone: string
  password: string
}

export interface CodeLoginDTO {
  phone: string
  code: string
}

export interface SignUpDTO {
  phone: string
  userName: string
  password: string
  code: string
  studentId: string
}

export interface ForgetPasswordDTO {
  phone: string
  code: string
  newPassword: string
}

export interface PageResult<T> {
  total: number
  current: number
  size: number
  pages: number
  pageResult: T[]
}

export interface ActivityPageVO {
  activityId: number
  activityName: string
  activityLocation: string
  registrationStartTime: string
  registrationEndTime: string
  startTime: string
  endTime: string
  totalCapacity: number
  remainingCapacity: number
  status: number
}

export interface ActivityVO extends ActivityPageVO {
  activityDescription?: string
  activityType: number
  activityTypeName?: string
  creatorId: number
}

export interface ActivityDTO {
  activityName: string
  activityDescription?: string
  activityType: number
  activityLocation: string
  registrationStartTime: string
  registrationEndTime: string
  startTime: string
  endTime: string
  totalCapacity: number
}

export interface RegistrationVO {
  registrationId?: number
  activityId: number
  activityName?: string
  userId: number
  userName?: string
  studentId?: string
  status?: number
  waitStatus?: number
  statusDescription: string
}

export interface WaitListVO {
  waitId: number
  activityId: number
  activityName?: string
  userId: number
  userName?: string
  studentId?: string
  status: number
  statusDescription: string
  joinTime: string
  promotedTime?: string
}

export interface RequestVO {
  requestId: string
  activityId: number
  activityName: string
  userId: number
  status: number
}

export interface UserVO {
  userId: number
  userName: string
  studentId: string
  phone: string
  role: number
  status: number
  createTime: string
}

export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
}
