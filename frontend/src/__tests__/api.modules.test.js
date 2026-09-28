/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

/**
 * API 层契约测试：校验各模块请求 URL / method / 参数透传是否正确。
 * 通过对 @/utils/request 打桩，避免真实网络请求。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

const request = vi.fn(() => Promise.resolve({ code: 200 }))
request.get = vi.fn(() => Promise.resolve({}))
request.post = vi.fn(() => Promise.resolve({}))
request.put = vi.fn(() => Promise.resolve({}))
request.delete = vi.fn(() => Promise.resolve({}))

vi.mock('@/utils/request', () => ({ default: request }))

const lastCall = () => request.mock.calls[request.mock.calls.length - 1][0]

describe('API 模块请求契约', () => {
  beforeEach(() => {
    request.mockClear()
    request.get.mockClear()
    request.post.mockClear()
    request.put.mockClear()
    request.delete.mockClear()
  })

  it('auth：登录/登出/两种 Swagger 登录', async () => {
    const auth = await import('@/api/auth')
    await auth.login({ username: 'a', password: 'b' })
    expect(lastCall()).toMatchObject({ url: '/auth/login', method: 'post' })

    await auth.logout()
    expect(lastCall()).toMatchObject({ url: '/auth/logout', method: 'post' })

    await auth.swaggerLogin({ username: 'a' })
    expect(lastCall()).toMatchObject({ url: '/auth/swagger-login', method: 'post' })

    await auth.swaggerAutoLogin()
    expect(lastCall()).toMatchObject({ url: '/auth/swagger-auto-login', method: 'post' })
  })

  it('user：搜索与启用用户列表', async () => {
    const user = await import('@/api/user')
    await user.searchUsers('li')
    expect(lastCall()).toEqual({ url: '/users/search', method: 'get', params: { keyword: 'li' } })

    await user.listEnabledUsers()
    expect(lastCall()).toEqual({ url: '/users/enabled', method: 'get' })
  })

  it('dashboard：统计接口', async () => {
    const dashboard = await import('@/api/dashboard')
    await dashboard.getDashboardStats()
    expect(lastCall()).toEqual({ url: '/dashboard/stats', method: 'get' })
  })

  it('requestLog：三个查询接口', async () => {
    const log = await import('@/api/requestLog')
    const params = { page: 1 }
    await log.getRequestLogs(params)
    expect(lastCall()).toEqual({ url: '/request-logs/list', method: 'get', params })
    await log.getDelayDistribution(params)
    expect(lastCall()).toEqual({ url: '/request-logs/delay-distribution', method: 'get', params })
    await log.getRequestOverview(params)
    expect(lastCall()).toEqual({ url: '/request-logs/overview', method: 'get', params })
  })

  it('mockTemplate：模板函数与预览', async () => {
    const tpl = await import('@/api/mockTemplate')
    await tpl.getTemplateFunctions()
    expect(lastCall()).toEqual({ url: '/mock-template/functions', method: 'get' })

    await tpl.previewTemplate('abc')
    expect(lastCall()).toEqual({ url: '/mock-template/preview', method: 'post', data: { template: 'abc' } })

    await tpl.previewTemplateBatch('abc', 3)
    expect(lastCall()).toEqual({
      url: '/mock-template/preview/batch',
      method: 'post',
      data: { template: 'abc', count: 3 }
    })
  })

  it('mockTemplate：批量预览默认数量为 5', async () => {
    const tpl = await import('@/api/mockTemplate')
    await tpl.previewTemplateBatch('abc')
    expect(lastCall().data.count).toBe(5)
  })

  it('project：CRUD 与成员管理路径拼接', async () => {
    const project = await import('@/api/project')
    const cases = [
      [() => project.getProjectList(), { url: '/projects', method: 'get' }],
      [() => project.getAccessibleProjects(), { url: '/projects/accessible', method: 'get' }],
      [() => project.getAllAccessibleProjects(), { url: '/projects/accessible/all', method: 'get' }],
      [() => project.getProjectById(7), { url: '/projects/7', method: 'get' }],
      [() => project.getProjectByCode('demo'), { url: '/projects/code/demo', method: 'get' }],
      [() => project.createProject({ name: 'x' }), { url: '/projects', method: 'post' }],
      [() => project.updateProject({ id: 1 }), { url: '/projects', method: 'put' }],
      [() => project.deleteProject(1), { url: '/projects/1', method: 'delete' }],
      [() => project.addProjectMember(1, 2), { url: '/projects/1/members/2', method: 'post' }],
      [() => project.removeProjectMember(1, 2), { url: '/projects/1/members/2', method: 'delete' }]
    ]
    for (const [fn, expected] of cases) {
      await fn()
      expect(lastCall()).toMatchObject(expected)
    }
  })

  it('mockApi：接口与响应 CRUD', async () => {
    const api = await import('@/api/mockApi')
    const cases = [
      [() => api.getMockApiList(), { url: '/mock-apis', method: 'get' }],
      [() => api.getMockApisByProjectId(3), { url: '/mock-apis/project/3', method: 'get' }],
      [() => api.getMockApiById(3), { url: '/mock-apis/3', method: 'get' }],
      [() => api.createMockApi({}), { url: '/mock-apis', method: 'post' }],
      [() => api.updateMockApi({}), { url: '/mock-apis', method: 'put' }],
      [() => api.deleteMockApi(3), { url: '/mock-apis/3', method: 'delete' }],
      [() => api.toggleApiStatus(3), { url: '/mock-apis/3/toggle', method: 'put' }],
      [() => api.addApiResponse(3, {}), { url: '/mock-apis/3/responses', method: 'post' }],
      [() => api.updateApiResponse({}), { url: '/mock-apis/responses', method: 'put' }],
      [() => api.deleteApiResponse(9), { url: '/mock-apis/responses/9', method: 'delete' }]
    ]
    for (const [fn, expected] of cases) {
      await fn()
      expect(lastCall()).toMatchObject(expected)
    }
  })

  it('aiService：服务商/模型/订阅/额度/APIKey 管理接口', async () => {
    const s = await import('@/api/aiService')
    const cases = [
      [() => s.listProviders(), { url: '/admin/ai/providers', method: 'get' }],
      [() => s.createProvider({}), { url: '/admin/ai/providers', method: 'post' }],
      [() => s.updateProvider(1, {}), { url: '/admin/ai/providers/1', method: 'put' }],
      [() => s.deleteProvider(1), { url: '/admin/ai/providers/1', method: 'delete' }],
      [() => s.listModels(1), { url: '/admin/ai/providers/1/models', method: 'get' }],
      [() => s.createModel(1, {}), { url: '/admin/ai/providers/1/models', method: 'post' }],
      [() => s.updateModel(1, 2, {}), { url: '/admin/ai/providers/1/models/2', method: 'put' }],
      [() => s.deleteModel(1, 2), { url: '/admin/ai/providers/1/models/2', method: 'delete' }],
      [() => s.fetchProviderModels(1), { url: '/admin/ai/providers/1/fetch-models', method: 'post' }],
      [() => s.batchCreateModels(1, ['a']), { url: '/admin/ai/providers/1/models/batch', method: 'post' }],
      [() => s.getModelsHealth(), { url: '/admin/ai/models/health', method: 'get' }],
      [() => s.healthCheckModel(2), { url: '/admin/ai/models/2/health-check', method: 'post' }],
      [() => s.listSubscriptions({ page: 1 }), { url: '/admin/ai/subscriptions', method: 'get' }],
      [() => s.createSubscription({}), { url: '/admin/ai/subscriptions', method: 'post' }],
      [() => s.updateSubscription(1, {}), { url: '/admin/ai/subscriptions/1', method: 'put' }],
      [() => s.updateSubscriptionPriority(1, { priority: 1 }), { url: '/admin/ai/subscriptions/1/priority', method: 'put' }],
      [() => s.deleteSubscription(1), { url: '/admin/ai/subscriptions/1', method: 'delete' }],
      [() => s.listQuotas({}), { url: '/admin/ai/quotas', method: 'get' }],
      [() => s.createQuota({}), { url: '/admin/ai/quotas', method: 'post' }],
      [() => s.updateQuota(1, {}), { url: '/admin/ai/quotas/1', method: 'put' }],
      [() => s.deleteQuota(1), { url: '/admin/ai/quotas/1', method: 'delete' }],
      [() => s.listApiKeys({}), { url: '/admin/ai/api-keys', method: 'get' }],
      [() => s.createApiKey({}), { url: '/admin/ai/api-keys', method: 'post' }],
      [() => s.deleteApiKey(1), { url: '/admin/ai/api-keys/1', method: 'delete' }],
      [() => s.listAiUsers(), { url: '/admin/ai/users', method: 'get' }],
      [() => s.getAiStatistics(), { url: '/admin/ai/statistics', method: 'get' }],
      [() => s.getUsageLogs({}), { url: '/admin/ai/usage-logs', method: 'get' }],
      [() => s.getFallbackLogs({}), { url: '/admin/ai/fallback-logs', method: 'get' }]
    ]
    for (const [fn, expected] of cases) {
      await fn()
      expect(lastCall()).toMatchObject(expected)
    }
  })
})

describe('mockApi Markdown 导出', () => {
  beforeEach(() => {
    request.mockClear()
    document.body.innerHTML = ''
  })

  it('默认文件名并返回空警告列表', async () => {
    const blob = new Blob(['# doc'])
    request.mockResolvedValue({ data: blob, headers: {} })
    const click = vi.fn()
    const origCreate = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const el = origCreate(tag)
      if (tag === 'a') el.click = click
      return el
    })

    const api = await import('@/api/mockApi')
    const warnings = await api.exportMockApisMarkdown([1, 2], false)
    expect(warnings).toEqual([])
    expect(click).toHaveBeenCalled()
    expect(lastCall()).toMatchObject({ url: '/mock-apis/export-markdown', method: 'post', responseType: 'blob' })
    vi.restoreAllMocks()
  })

  it('AI 增强时使用带后缀的文件名并解析响应头警告', async () => {
    const warnings = ['AI 增强失败', '内容不合规']
    request.mockResolvedValue({
      data: new Blob(['x']),
      headers: { 'x-export-warnings': encodeURIComponent(JSON.stringify(warnings)) }
    })
    const api = await import('@/api/mockApi')
    const result = await api.exportMockApisMarkdown([1], true)
    expect(result).toEqual(warnings)
  })

  it('响应头警告非法 JSON 时降级为日志且返回空数组', async () => {
    const errorLog = vi.spyOn(console, 'error').mockImplementation(() => {})
    request.mockResolvedValue({ data: new Blob(['x']), headers: { 'x-export-warnings': '%7Bbad' } })
    const api = await import('@/api/mockApi')
    const result = await api.exportMockApisMarkdown([1], false)
    expect(result).toEqual([])
    expect(errorLog).toHaveBeenCalled()
    errorLog.mockRestore()
  })

  it('响应体直接为 Blob 时同样可下载', async () => {
    request.mockResolvedValue(new Blob(['x']))
    const api = await import('@/api/mockApi')
    const result = await api.exportMockApisMarkdown([1], false)
    expect(result).toEqual([])
  })
})
