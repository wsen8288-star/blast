import { App } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import { createRouterGuards } from './guards';
import { constantRouter } from './routes';

const router = createRouter({
  history: createWebHistory(),
  routes: constantRouter,
  strict: true,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

export function setupRouter(app: App) {
  app.use(router);
  // 创建路由守卫
  createRouterGuards(router);
}

export default router;
