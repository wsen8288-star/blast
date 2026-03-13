import { ObjectDirective } from 'vue';
import { usePermission } from '@/hooks/web/usePermission';

export const permission: ObjectDirective = {
  mounted(el: HTMLButtonElement, binding) {
    const { hasPermission } = usePermission();
    const { actions, effect } = resolveBinding(binding.value);
    if (!hasPermission(actions)) {
      if (effect === 'disabled') {
        el.disabled = true;
        el.setAttribute('disabled', 'disabled');
        el.classList.add('n-button--disabled');
      } else {
        el.remove();
      }
    }
  },
  updated(el: HTMLButtonElement, binding) {
    const { hasPermission } = usePermission();
    const { actions, effect } = resolveBinding(binding.value);
    const allowed = hasPermission(actions);
    if (!allowed && effect !== 'disabled') {
      el.remove();
      return;
    }
    if (effect === 'disabled') {
      el.disabled = !allowed;
      if (!allowed) {
        el.setAttribute('disabled', 'disabled');
        el.classList.add('n-button--disabled');
      } else {
        el.removeAttribute('disabled');
        el.classList.remove('n-button--disabled');
      }
    }
  },
};

function resolveBinding(value: any): { actions: string[]; effect: 'remove' | 'disabled' } {
  if (Array.isArray(value)) {
    return { actions: value, effect: 'remove' };
  }
  if (typeof value === 'string') {
    return { actions: [value], effect: 'remove' };
  }
  if (value && typeof value === 'object') {
    const rawAction = value.action ?? value.actions ?? [];
    const actions = Array.isArray(rawAction) ? rawAction : [rawAction];
    const effect = value.effect === 'disabled' ? 'disabled' : 'remove';
    return { actions, effect };
  }
  return { actions: [], effect: 'remove' };
}
