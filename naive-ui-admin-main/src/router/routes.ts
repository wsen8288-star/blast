import { RouteRecordRaw } from 'vue-router';
import { RedirectRoute } from '@/router/base';
import { PageEnum } from '@/enums/pageEnum';
import type { IModuleType } from './types';

const modules = import.meta.glob<IModuleType>('./modules/**/*.ts', { eager: true });

const routeModuleList: RouteRecordRaw[] = Object.keys(modules).reduce((list, key) => {
  const mod = modules[key].default ?? {};
  const modList = Array.isArray(mod) ? [...mod] : [mod];
  return [...list, ...modList];
}, []);

function sortRoute(a, b) {
  return (a.meta?.sort ?? 0) - (b.meta?.sort ?? 0);
}

routeModuleList.sort(sortRoute);

export const RootRoute: RouteRecordRaw = {
  path: '/',
  name: 'Root',
  redirect: PageEnum.BASE_HOME,
  meta: {
    title: 'Root',
  },
};

export const LoginRoute: RouteRecordRaw = {
  path: '/login',
  name: 'Login',
  component: () => import('@/views/login/index.vue'),
  meta: {
    title: '登录',
  },
};

export const RegisterRoute: RouteRecordRaw = {
  path: '/register',
  name: 'Register',
  component: () => import('@/views/login/register.vue'),
  meta: {
    title: '注册',
  },
};

export const ForgotPasswordRoute: RouteRecordRaw = {
  path: '/forgot-password',
  name: 'ForgotPassword',
  component: () => import('@/views/login/forgot-password.vue'),
  meta: {
    title: '忘记密码',
  },
};

export const ResetPasswordRoute: RouteRecordRaw = {
  path: '/reset-password',
  name: 'ResetPassword',
  component: () => import('@/views/login/reset-password.vue'),
  meta: {
    title: '重置密码',
  },
};

//需要验证权限
export const asyncRoutes = [...routeModuleList];

//普通路由 无需验证权限
export const constantRouter: RouteRecordRaw[] = [
  LoginRoute,
  RegisterRoute,
  ForgotPasswordRoute,
  ResetPasswordRoute,
  RootRoute,
  RedirectRoute,
];
