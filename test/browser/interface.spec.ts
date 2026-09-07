import { expect, test, type Page } from '@playwright/test'

// Isolated UI fixtures. These tests do not replace real-MySQL API integration tests.
const semester = { id: '1', academicYear: 2026, season: 'AUTUMN', startsOn: '2026-09-01', endsOnInclusive: '2026-11-23', endsOnExclusive: '2026-11-24', status: 'ACTIVE' }
const user = { userId: '2', name: '陈同学', account: 's1000001', role: 'STUDENT', homePath: '/student/courses' }
const course = { id: '10', code: 'C000010', name: '离散数学', description: '集合、图论与逻辑基础。', semester, publication: 'PUBLISHED', version: '1', meetings: [{ weekday: 1, startTime: '08:00', endTime: '10:00' }], displayColor: '#80CBE5', borderStyle: 'solid', teacher: { id: '3', name: '张老师' }, allowedActions: ['select'], selected: false }
async function fixtures(page: Page, signedIn: boolean) {
  let loggedIn = signedIn
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '')
    let data: unknown
    if (path === '/auth/context') data = { ready: true }
    else if (path === '/auth/login') { loggedIn = true; data = user }
    else if (path === '/auth/me') {
      if (!loggedIn) return route.fulfill({ status: 401, json: { error: { code: 'AUTH_REQUIRED', message: '请先登录' } } })
      data = user
    } else if (path === '/semesters') data = { items: [semester, { ...semester, id: '2', academicYear: 2025, status: 'ENDED' }], defaultSemesterId: '1' }
    else if (path === '/student/enrollments') data = { items: [], total: 0, totalSelected: 0, page: 1, size: 20 }
    else if (path === '/student/catalog') data = { items: [course], total: 1, page: 1, size: 20 }
    else if (path === '/student/catalog/10') data = course
    else if (path === '/student/timetable') data = { semester, courses: [course], snapshotAt: '2026-09-06T10:00:00Z' }
    else throw new Error(`Unexpected API request: ${request.method()} ${path}`)
    await route.fulfill({ json: { data, meta: { requestId: 'ui-test' } } })
  })
  return errors
}
test('登录后进入学生工作区，空状态和导航可用', async ({ page }) => {
  const errors = await fixtures(page, false)
  await page.goto('/login')
  await page.getByLabel('登录账号', { exact: true }).fill('s1000001')
  await page.locator('input[name="password"]').fill('Password123')
  await page.screenshot({ path: 'results/login-chrome.png', fullPage: true })
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/student\/courses$/)
  await expect(page.getByRole('heading', { name: '我的课程' })).toBeVisible()
  await page.getByRole('link', { name: '课程选择' }).click()
  await expect(page.getByText('离散数学', { exact: true })).toBeVisible()
  await page.screenshot({ path: 'results/catalog-chrome.png', fullPage: true })
  expect(errors).toEqual([])
})
test('周课表完整时间轴、课程详情和历史只读提示', async ({ page }) => {
  const errors = await fixtures(page, true)
  await page.goto('/student/timetable')
  await expect(page.getByRole('heading', { name: '周课表', exact: true })).toBeVisible()
  await expect(page.getByText('18:00', { exact: true })).toBeVisible()
  await page.screenshot({ path: 'results/timetable-chrome.png', fullPage: true })
  await page.getByRole('button').filter({ hasText: '离散数学' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await page.getByLabel('当前学期', { exact: true }).selectOption('2')
  await expect(page.getByText('本学期已结束，仅可查看历史课程与最终选课结果。')).toBeVisible()
  expect(errors).toEqual([])
})
test('服务不可用时提供重试入口', async ({ page }) => {
  await page.route('**/api/v1/**', route => route.fulfill({ status: 503, json: { error: { code: 'SERVICE_UNAVAILABLE', message: '数据库暂不可用，请稍后重试' } } }))
  await page.goto('/login')
  await expect(page.getByRole('alert')).toContainText('暂时无法连接服务')
  await expect(page.getByRole('button', { name: '重新连接' })).toBeVisible()
})

test('registration omits a user-entered student or employee number', async ({ page }) => {
  let payload: Record<string, string> | undefined
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    if (path === '/auth/context') return route.fulfill({ json: { data: { ready: true } } })
    if (path === '/auth/me') return route.fulfill({ status: 401, json: { error: { code: 'AUTH_REQUIRED', message: 'login required' } } })
    if (path === '/auth/register') {
      payload = route.request().postDataJSON()
      return route.fulfill({ status: 201, json: { data: { account: 's0000123' } } })
    }
    return route.fulfill({ status: 404, json: { error: { code: 'NOT_FOUND', message: 'not found' } } })
  })
  await page.goto('/register')
  await expect(page.getByLabel('学号', { exact: true })).toHaveCount(0)
  await page.locator('input[autocomplete="name"]').fill('Browser Student')
  await page.locator('input[autocomplete="email"]').fill('browser@example.edu')
  await page.locator('input[autocomplete="new-password"]').nth(0).fill('Password123')
  await page.locator('input[autocomplete="new-password"]').nth(1).fill('Password123')
  await page.getByRole('button').last().click()
  await expect(page.getByText('s0000123', { exact: true })).toBeVisible()
  expect(payload).toMatchObject({ role: 'STUDENT', name: 'Browser Student', email: 'browser@example.edu' })
  expect(payload).not.toHaveProperty('registrationNumber')
})

test('administrator edits account details without sending a registration number', async ({ page }) => {
  const admin = { userId: '1', name: '管理员', account: 'admin', role: 'ADMIN', homePath: '/admin/accounts' }
  const account = { id: '7', account: 's0000007', role: 'STUDENT', name: '编辑前', registrationNumber: 's0000007', email: 'before@example.edu', version: '3', createdAt: '2026-09-01T00:00:00Z', status: 'ACTIVE' }
  let payload: Record<string, string> | undefined
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path === '/auth/context') return route.fulfill({ json: { data: { ready: true } } })
    if (path === '/auth/me') return route.fulfill({ json: { data: admin } })
    if (path === '/semesters') return route.fulfill({ json: { data: { items: [semester], defaultSemesterId: '1' } } })
    if (path === '/admin/accounts' && request.method() === 'GET') return route.fulfill({ json: { data: { items: [account], total: 1, page: 1, size: 20 } } })
    if (path === '/admin/accounts/7' && request.method() === 'GET') return route.fulfill({ json: { data: account } })
    if (path === '/admin/accounts/7' && request.method() === 'PATCH') {
      payload = request.postDataJSON()
      return route.fulfill({ json: { data: { ...account, ...payload, version: '4' } } })
    }
    throw new Error(`Unexpected API request: ${request.method()} ${path}`)
  })
  await page.goto('/admin/accounts')
  await page.getByRole('button', { name: '编辑资料' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByLabel('学号', { exact: true })).toHaveCount(0)
  await page.getByLabel('姓名', { exact: true }).fill('编辑后')
  await page.getByLabel('邮箱', { exact: true }).fill('after@example.edu')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
  expect(payload).toEqual({ name: '编辑后', email: 'after@example.edu' })
})
