import type { Directive } from 'vue';
import { useAuthStore } from '@/store/modules/auth';

function hasAccess(value: unknown) {
  const authStore = useAuthStore();
  const buttons = authStore.userInfo.buttons || [];
  if (!Array.isArray(value) || value.length === 0) return true;
  return value.some(code => buttons.includes(String(code)));
}

export const permission: Directive = {
  mounted(el, binding) {
    if (!hasAccess(binding.value)) {
      el.parentNode?.removeChild(el);
    }
  }
};
