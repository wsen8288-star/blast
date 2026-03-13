import type { App } from 'vue';
import {
  type RouterHistory,
  type RouteRecordRaw,
  createMemoryHistory,
  createRouter,
  createWebHashHistory,
  createWebHistory
} from 'vue-router';
import { createBuiltinVueRoutes } from './routes/builtin';
import { createRouterGuard } from './guard';

const { VITE_ROUTER_HISTORY_MODE = 'history', VITE_BASE_URL } = import.meta.env;

const historyCreatorMap: Record<Env.RouterHistoryMode, (base?: string) => RouterHistory> = {
  hash: createWebHashHistory,
  history: createWebHistory,
  memory: createMemoryHistory
};

const authAssistRoutes: RouteRecordRaw[] = [
  {
    name: 'login-register',
    path: '/login/register',
    component: () => import('@/views/login/register.vue'),
    meta: { constant: true, hideInMenu: true, title: 'register' }
  },
  {
    name: 'login-reset-pwd',
    path: '/login/reset-pwd',
    component: () => import('@/views/login/forgot-password.vue'),
    meta: { constant: true, hideInMenu: true, title: 'forgot-password' }
  },
  {
    name: 'login-reset-password',
    path: '/login/reset-password',
    component: () => import('@/views/login/reset-password.vue'),
    meta: { constant: true, hideInMenu: true, title: 'reset-password' }
  }
];

export const router = createRouter({
  history: historyCreatorMap[VITE_ROUTER_HISTORY_MODE](VITE_BASE_URL),
  routes: [...createBuiltinVueRoutes(), ...authAssistRoutes]
});

/** Setup Vue Router */
export async function setupRouter(app: App) {
  app.use(router);
  createRouterGuard(router);
  await router.isReady();
}
