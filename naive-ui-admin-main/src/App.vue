<template>
  <NConfigProvider
    :locale="zhCN"
    :theme="getDarkTheme"
    :theme-overrides="getThemeOverrides"
    :date-locale="dateZhCN"
    class="h-full w-full"
  >
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <RouterView />
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </NConfigProvider>
</template>

<script lang="ts" setup>
  import { computed, watchEffect } from 'vue';
  import { zhCN, dateZhCN, darkTheme } from 'naive-ui';
  import { useDesignSettingStore } from '@/store/modules/designSetting';
  import { darkIndustrialThemeOverrides, lightIndustrialThemeOverrides } from '@/settings/designSetting';
  import { lighten } from '@/utils/index';

  const designStore = useDesignSettingStore();

  // 监听暗黑模式切换，同步给 HTML 标签，触发 Tailwind 的 .dark 变量
  watchEffect(() => {
    document.documentElement.classList.toggle('dark', designStore.darkTheme);
  });

  const getThemeOverrides = computed(() => {
    const appTheme = designStore.appTheme;
    const lightenStr = lighten(designStore.appTheme, 6);

    const baseOverrides = designStore.darkTheme
      ? darkIndustrialThemeOverrides
      : lightIndustrialThemeOverrides;

    return {
      ...baseOverrides,
      common: {
        ...(baseOverrides.common ?? {}),
        primaryColor: appTheme,
        primaryColorHover: lightenStr,
        primaryColorPressed: lightenStr,
        primaryColorSuppl: appTheme,
      },
      LoadingBar: {
        colorLoading: appTheme,
      },
    };
  });

  const getDarkTheme = computed(() => (designStore.darkTheme ? darkTheme : undefined));
</script>

<style>
/* ==============================================================
   Soybean 风格的极简滚动条 (全局)
   ============================================================== */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

/* 浅色模式滚动条 */
::-webkit-scrollbar-thumb {
  background-color: rgba(144, 147, 153, 0.3);
  border-radius: 3px;
  transition: background-color 0.3s ease;
}

::-webkit-scrollbar-thumb:hover {
  background-color: rgba(144, 147, 153, 0.5);
}

/* 轨道完全透明，显得更干净 */
::-webkit-scrollbar-track {
  background: transparent;
}

/* 深色模式滚动条适配 */
html.dark ::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.2);
}

html.dark ::-webkit-scrollbar-thumb:hover {
  background-color: rgba(255, 255, 255, 0.3);
}
</style>