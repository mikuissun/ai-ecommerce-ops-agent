import { createRouter, createWebHistory } from 'vue-router'
import { savedUser } from './api'
import AgentWorkspace from './AgentWorkspace.vue'
import LoginView from './components/LoginView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: AgentWorkspace },
    { path: '/login', component: LoginView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
router.beforeEach(to => !savedUser() && to.path !== '/login' ? '/login' : savedUser() && to.path === '/login' ? '/' : true)
