import { createRouter, createWebHistory } from 'vue-router'
// 首页直接引入（不懒加载），保证首屏速度
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
    // 非首屏路由均使用动态 import 懒加载，减小初始包体积
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
      title: '我的课程',
    },
  },
  {
    path: '/mine',
    name: 'Mine',
    component: () => import('../views/Mine.vue'),
    meta: {
      title: '我的',
    },
  },
  {
    path: '/lecture/:coursewareId',
    name: 'Lecture',
    component: () => import('../views/Lecture.vue'),
    meta: {
      title: '讲课',
    },
  },
  {
    path: '/script/:coursewareId',
    name: 'Script',
    component: () => import('../views/Script.vue'),
    meta: {
      title: '讲稿预览',
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

// 全局前置守卫：每次路由切换时同步更新浏览器标签页标题
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'Interactive-Edu-Agent'} - ${import.meta.env.VITE_APP_TITLE}`
  next()
})

export default router
