<template>
  <div class="w-full h-full flex items-center justify-center py-10">
    <n-spin size="small" />
  </div>
</template>

<script setup lang="ts">
  import { onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { NSpin } from 'naive-ui';

  const route = useRoute();
  const router = useRouter();

  onMounted(() => {
    const raw = route.params.path;
    const path = Array.isArray(raw) ? raw.join('/') : String(raw ?? '');
    const targetPath = path.startsWith('/') ? path : `/${path}`;
    router.replace({
      path: targetPath,
      query: route.query,
      hash: route.hash,
    });
  });
</script>
