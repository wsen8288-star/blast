import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { useTitle } from '@vueuse/core';
import { systemApi } from '@/api/blast-furnace';
import { SetupStoreId } from '@/enum';
import { $t } from '@/locales';
import { router } from '@/router';

type SysConfigEntity = {
  configKey: string;
  configValue: string;
};

export const useSystemConfigStore = defineStore(SetupStoreId.SystemConfig, () => {
  const loading = ref(false);
  const loaded = ref(false);
  const configMap = reactive<Record<string, string>>({});

  const systemName = computed(() => {
    const name = String(configMap.system_name ?? '').trim();
    return name || $t('system.title');
  });

  function getConfigValue(key: string, fallback = '') {
    const value = configMap[key];
    if (value === null || value === undefined || String(value).trim() === '') {
      return fallback;
    }
    return String(value);
  }

  async function loadSystemConfig(force = false) {
    if (loading.value) return;
    if (loaded.value && !force) return;
    loading.value = true;
    try {
      const res: any = await systemApi.config.getListByGroup('SYSTEM_CONFIG');
      const items: SysConfigEntity[] = Array.isArray(res?.data) ? res.data : [];
      Object.keys(configMap).forEach(key => {
        delete configMap[key];
      });
      items.forEach(item => {
        configMap[item.configKey] = item.configValue;
      });
      loaded.value = true;
      const { i18nKey, title } = router.currentRoute.value.meta;
      const pageTitle = i18nKey ? $t(i18nKey) : title;
      const documentTitle = pageTitle ? `${systemName.value} - ${pageTitle}` : systemName.value;
      useTitle(documentTitle);
    } finally {
      loading.value = false;
    }
  }

  return {
    loading,
    loaded,
    configMap,
    systemName,
    getConfigValue,
    loadSystemConfig
  };
});
