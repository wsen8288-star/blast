<template>
  <div class="min-h-screen flex items-center justify-center p-6">
    <n-card class="w-full max-w-[560px]">
      <n-result status="403" title="无权限访问" description="当前账号没有访问该页面的权限，请联系管理员授权。">
        <template #footer>
          <n-space justify="center">
            <n-button type="primary" @click="goHome">返回首页</n-button>
            <n-button @click="goLogin">重新登录</n-button>
          </n-space>
        </template>
      </n-result>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';

const router = useRouter();
const authStore = useAuthStore();

function goHome() {
  router.replace('/');
}

function goLogin() {
  authStore.resetStore();
  router.replace({ name: 'login' });
}
</script>
