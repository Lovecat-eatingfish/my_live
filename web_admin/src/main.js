import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import LoginPage from './views/LoginPage.vue'
import LayoutPage from './views/LayoutPage.vue'
import ReconPage from './views/ReconPage.vue'
import TagPage from './views/TagPage.vue'
import UserPage from './views/UserPage.vue'
import VideoPage from './views/VideoPage.vue'
import LivingPage from './views/LivingPage.vue'
import LivingCategoryPage from './views/LivingCategoryPage.vue'
import DashPage from './views/DashboardPage.vue'
import AuditPage from './views/AuditPage.vue'
import SnapshotPage from './views/SnapshotPage.vue'
import RiskPage from './views/RiskPage.vue'
import GiftConfigPage from './views/GiftConfigPage.vue'
import PayProductPage from './views/PayProductPage.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: LoginPage },
    {
      path: '/', component: LayoutPage,
      children: [
        { path: '', redirect: '/dash' },
        { path: 'dash', name: 'Dash', component: DashPage },
        { path: 'audit', name: 'Audit', component: AuditPage },
        { path: 'snapshot', name: 'Snapshot', component: SnapshotPage },
        { path: 'risk', name: 'Risk', component: RiskPage },
        { path: 'giftConfig', name: 'GiftConfig', component: GiftConfigPage },
        { path: 'payProduct', name: 'PayProduct', component: PayProductPage },
        { path: 'recon', name: 'Recon', component: ReconPage },
        { path: 'tag', name: 'Tag', component: TagPage },
        { path: 'video', name: 'Video', component: VideoPage },
        { path: 'user', name: 'User', component: UserPage },
        { path: 'living', name: 'Living', component: LivingPage },
        { path: 'livingCategory', name: 'LivingCategory', component: LivingCategoryPage },
      ]
    },
  ]
})

// 全局路由守卫：未登录跳登录页
router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('admin_token')) {
    return '/login'
  }
})

createApp(App).use(router).use(ElementPlus).mount('#app')
