export const activityTypes = [
  { value: 0, label: '讲座' },
  { value: 1, label: '竞赛' },
  { value: 2, label: '体育活动' },
  { value: 3, label: '志愿活动' },
  { value: 4, label: '社团/文娱活动' },
  { value: 5, label: '其他' },
]

export function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export function getRegistrationPhase(start: string, end: string): { label: string; type: 'info' | 'success' | 'warning' } {
  const now = Date.now()
  if (now < new Date(start).getTime()) return { label: '即将开放', type: 'info' }
  if (now >= new Date(end).getTime()) return { label: '报名已截止', type: 'warning' }
  return { label: '报名进行中', type: 'success' }
}
