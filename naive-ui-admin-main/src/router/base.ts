import { RedirectName, Layout } from '@/router/constant';
import { RouteRecordRaw } from 'vue-router';

// 404 on a page
export const ErrorPageRoute: RouteRecordRaw = {
  path: '/:path(.*)*',
  name: 'ErrorPage',
  component: Layout,
  meta: {
    title: 'ErrorPage',
    hideBreadcrumb: true,
  },
  children: [
    {
      path: '/:path(.*)*',
      name: 'ErrorPageSon',
      component: () => import('@/views/login/index.vue'),
      meta: {
        title: 'ErrorPage',
        hideBreadcrumb: true,
      },
    },
  ],
};

export const RedirectRoute: RouteRecordRaw = {
  path: '/redirect',
  name: RedirectName,
  component: Layout,
  meta: {
    title: '',
    hideBreadcrumb: true,
  },
  children: [
    {
      path: ':path(.*)',
      name: `${RedirectName}Son`,
      component: () => import('@/views/redirect/index.vue'),
      meta: {
        title: '',
        hideBreadcrumb: true,
      },
    },
  ],
};
