import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: {
      title: '首页',
    },
  },
  {
    path: '/upload',
    name: 'Upload',
    component: () => import('../views/Upload.vue'),
    meta: {
      title: '上传课件',
    },
  },
  {
    path: '/courses',
    name: 'Courses',
    component: () => import('../views/Courses.vue'),
    meta: {
      title: '课程列表',
    },
  },
  {
    path: '/mine',
    name: 'Mine',
    component: () => import('../views/Mine.vue'),
    meta: {
      title: '我的课程',
    },
  },
  {
    path: '/lecture/:coursewareId',
    name: 'Lecture',
    component: () => import('../views/Lecture.vue'),
    meta: {
      title: '互动课堂',
    },
  },
  {
    path: '/script/:coursewareId',
    name: 'Script',
    component: () => import('../views/Script.vue'),
    meta: {
      title: '课件讲稿',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: {
      title: '页面不存在',
    },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const appTitle = import.meta.env.VITE_APP_TITLE || 'IEA 智能教学助手'
  document.title = `${to.meta.title || 'Interactive-Edu-Agent'} - ${appTitle}`
  next()
})

export default router
