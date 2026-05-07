/**
 * 开发环境 Mock 拦截器
 * 仅在 VITE_ENABLE_MOCK=true 时生效，拦截 axios 请求返回假数据
 */

// 模拟课件列表
const mockCoursewareList = [
  {
    id: 'cware_001',
    name: '数据结构与算法.pptx',
    size: 2048000,
    status: 'PARSED',
    createdAt: '2026-04-06T10:00:00.000Z',
  },
  {
    id: 'cware_002',
    name: '操作系统概论.pdf',
    size: 5120000,
    status: 'PARSED',
    createdAt: '2026-04-05T14:30:00.000Z',
  },
]

// 模拟讲稿数据
const mockScriptData = {
  outline: [
    { id: 1, title: '课程介绍' },
    { id: 2, title: '第一章：基础概念' },
    { id: 3, title: '第二章：核心算法' },
    { id: 4, title: '第三章：实践应用' },
    { id: 5, title: '总结与回顾' },
  ],
  segments: [
    {
      id: 1,
      title: '课程介绍',
      content:
        '欢迎来到本课程。本课程将系统地介绍数据结构与算法的核心内容，帮助同学们建立清晰的知识框架，掌握常见数据结构的特性及其应用场景。',
      knowledgePoints: ['课程目标', '学习路径', '考核方式'],
    },
    {
      id: 2,
      title: '基础概念',
      content:
        '数据结构是计算机存储、组织数据的方式。它是指相互之间存在一种或多种特定关系的数据元素的集合。常见的数据结构包括数组、链表、栈、队列、树和图等。',
      knowledgePoints: ['数据结构定义', '逻辑结构', '物理结构', '时间复杂度'],
    },
    {
      id: 3,
      title: '核心算法',
      content:
        '排序算法是最基础也是最重要的算法之一。常见的排序算法包括冒泡排序、快速排序、归并排序等。每种排序算法都有其适用场景和时间复杂度特点。',
      knowledgePoints: ['冒泡排序', '快速排序', '归并排序', '时间复杂度分析'],
    },
    {
      id: 4,
      title: '实践应用',
      content:
        '在实际开发中，合理选择数据结构和算法能够显著提升程序的性能。本章将通过多个实战案例，展示如何将理论知识应用到真实项目中。',
      knowledgePoints: ['缓存设计', '搜索优化', '图算法应用'],
    },
    {
      id: 5,
      title: '总结与回顾',
      content:
        '本课程覆盖了数据结构与算法的核心内容。希望同学们课后多加练习，在实践中巩固所学知识。下节课我们将进入更高级的主题。',
      knowledgePoints: ['知识回顾', '课后作业'],
    },
  ],
}

let uploadCounter = 100
let scriptGenerateState = {} // coursewareId -> boolean
let sessionCounter = 0

// 模拟问答回复
const mockAnswers = [
  {
    answer: '**递归**是指函数直接或间接调用自身的一种编程技巧。\n\n递归的两个要素：\n1. **基准条件**（Base Case）：递归终止的条件\n2. **递归步骤**（Recursive Step）：将问题分解为更小的子问题\n\n```python\ndef factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)\n```',
    evidence: [
      { source: '第二章 P3', text: '递归是一种常见的算法思想，其核心在于将大问题分解为相同结构的小问题。' },
    ],
  },
  {
    answer: '**时间复杂度**用来衡量算法执行时间随输入规模增长的变化趋势，通常用大 O 表示法。\n\n常见复杂度（从低到高）：\n- O(1) 常数\n- O(log n) 对数\n- O(n) 线性\n- O(n log n)\n- O(n²) 平方',
    evidence: [
      { source: '第一章 P5', text: '时间复杂度是评估算法效率的重要指标。' },
    ],
  },
  {
    answer: '快速排序的平均时间复杂度为 **O(n log n)**，最坏情况为 O(n²)。\n\n它采用分治策略：\n1. 选一个 **基准元素**（pivot）\n2. 将数组分为小于和大于 pivot 的两部分\n3. 对两部分递归排序',
    evidence: [
      { source: '第二章 P8', text: '快速排序是实践中最常用的排序算法之一。' },
    ],
  },
]

// 模拟延迟
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

// 路由匹配
const matchRoute = (method, url) => {
  const routes = [
    { method: 'POST', pattern: /\/courseware\/upload$/, handler: handleUpload },
    { method: 'GET', pattern: /\/courseware$/, handler: handleCoursewareList },
    { method: 'GET', pattern: /\/courseware\/([^/]+)$/, handler: handleCoursewareDetail },
    { method: 'GET', pattern: /\/courseware\/([^/]+)\/script$/, handler: handleGetScript },
    { method: 'POST', pattern: /\/courseware\/([^/]+)\/script\/generate$/, handler: handleGenerateScript },
    { method: 'POST', pattern: /\/lecture\/start$/, handler: handleLectureStart },
    { method: 'POST', pattern: /\/lecture\/([^/]+)\/pause$/, handler: handleLecturePause },
    { method: 'POST', pattern: /\/lecture\/resume$/, handler: handleLectureResume },
    { method: 'POST', pattern: /\/qa\/ask-text$/, handler: handleQAText },
  ]

  for (const route of routes) {
    if (route.method === method) {
      const match = url.match(route.pattern)
      if (match) {
        return { handler: route.handler, params: match.slice(1) }
      }
    }
  }
  return null
}

// 处理函数
function handleUpload() {
  uploadCounter++
  const id = `cware_mock_${uploadCounter}`
  mockCoursewareList.unshift({
    id,
    name: `上传课件_${uploadCounter}.pptx`,
    size: 1024000,
    status: 'PARSING',
    createdAt: new Date().toISOString(),
  })
  // 模拟 3 秒后解析完成
  setTimeout(() => {
    const item = mockCoursewareList.find(c => c.id === id)
    if (item) item.status = 'PARSED'
  }, 3000)
  return { code: 0, message: 'success', data: { coursewareId: id, status: 'UPLOADED' } }
}

function handleCoursewareList() {
  return {
    code: 0,
    message: 'success',
    data: { items: mockCoursewareList, total: mockCoursewareList.length, page: 1, pageSize: 20 },
  }
}

function handleCoursewareDetail(params) {
  const item = mockCoursewareList.find(c => c.id === params[0])
  if (item) {
    return { code: 0, message: 'success', data: item }
  }
  return { code: 40001, message: '课件不存在', data: null }
}

function handleGetScript(params) {
  const coursewareId = params[0]
  // 只有解析完成且生成过讲稿的才返回数据
  const item = mockCoursewareList.find(c => c.id === coursewareId)
  if (item && item.status === 'PARSED' && scriptGenerateState[coursewareId]) {
    return { code: 0, message: 'success', data: mockScriptData }
  }
  // 预置课件直接有讲稿
  if (coursewareId === 'cware_001' || coursewareId === 'cware_002') {
    return { code: 0, message: 'success', data: mockScriptData }
  }
  return { code: 0, message: 'success', data: null }
}

function handleGenerateScript(params) {
  const coursewareId = params[0]
  // 模拟 5 秒后生成完成
  setTimeout(() => {
    scriptGenerateState[coursewareId] = true
  }, 5000)
  return { code: 0, message: 'success', data: { status: 'GENERATING' } }
}

function handleLectureStart(params, body) {
  sessionCounter++
  const sid = `sess_mock_${sessionCounter}`
  return {
    code: 0,
    message: 'success',
    data: {
      sessionId: sid,
      currentNode: { nodeId: 'node_001', pageIndex: 1, content: '' },
    },
  }
}

function handleLecturePause() {
  return { code: 0, message: 'success', data: null }
}

function handleLectureResume(params, body) {
  return {
    code: 0,
    message: 'success',
    data: {
      sessionId: body?.sessionId || 'sess_mock_0',
      currentNode: { nodeId: 'node_001', pageIndex: 1, content: '' },
    },
  }
}

function handleQAText(params, body) {
  const idx = Math.floor(Math.random() * mockAnswers.length)
  const mock = mockAnswers[idx]
  return {
    code: 0,
    message: 'success',
    data: {
      answer: mock.answer,
      evidence: mock.evidence,
      latencyMs: 800,
    },
  }
}

/**
 * 注册 mock 拦截器到 axios 实例
 */
export function setupMock(axiosInstance) {
  axiosInstance.interceptors.request.use(async config => {
    const url = config.url || ''
    const method = (config.method || 'GET').toUpperCase()
    const matched = matchRoute(method, url)

    if (matched) {
      await delay(300 + Math.random() * 500) // 模拟网络延迟
      const body = config.data ? (typeof config.data === 'string' ? JSON.parse(config.data) : config.data) : {}
      const result = matched.handler(matched.params || [], body)

      // 通过 adapter 短路请求，直接返回 mock 响应
      config.adapter = () =>
        Promise.resolve({
          data: result,
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        })
    }

    return config
  })
}
