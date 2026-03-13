import type { RouteRecordRaw } from 'vue-router';
import { Layout } from '@/router/constant';

const route: RouteRecordRaw = {
  path: '/home',
  name: 'Home',
  component: Layout,
  redirect: '/home/index',
  meta: {
    title: '首页',
    icon: 'ic:outline-home',
    sort: -100,
    isRoot: true,
  },
  children: [
    {
      path: 'index',
      name: 'HomeIndex',
      component: () => import('@/views/home/index.vue'),
      meta: {
        title: '首页',
        icon: 'ic:outline-home',
        affix: true,
      },
    },
  ],
};

export default route;
