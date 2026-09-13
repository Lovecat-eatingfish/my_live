import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Home', component: () => import('@/views/HomePage.vue') },
  { path: '/login', name: 'Login', component: () => import('@/views/LoginPage.vue') },
  { path: '/room/:id', name: 'Room', component: () => import('@/views/RoomPage.vue') },
  { path: '/wallet', name: 'Wallet', component: () => import('@/views/WalletPage.vue') },
  { path: '/video', name: 'VideoSquare', component: () => import('@/views/VideoSquarePage.vue') },
  { path: '/video/:id', name: 'VideoDetail', component: () => import('@/views/VideoDetailPage.vue') },
  { path: '/recon', name: 'Recon', component: () => import('@/views/ReconPage.vue') },
  { path: '/user/center', name: 'UserCenter', component: () => import('@/views/UserCenterPage.vue') },
  { path: '/profile/:userId?', name: 'Profile', component: () => import('@/views/ProfilePage.vue') },
]

export default createRouter({
  history: createWebHistory(),
  routes
})
