import type { Router } from 'vue-router';
import { useTitle } from '@vueuse/core';
import { $t } from '@/locales';
import { useSystemConfigStore } from '@/store/modules/system-config';

export function createDocumentTitleGuard(router: Router) {
  router.afterEach(to => {
    const { i18nKey, title } = to.meta;

    const systemConfigStore = useSystemConfigStore();
    const pageTitle = i18nKey ? $t(i18nKey) : title;
    const documentTitle = pageTitle ? `${systemConfigStore.systemName} - ${pageTitle}` : systemConfigStore.systemName;

    useTitle(documentTitle);
  });
}
