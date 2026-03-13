<template>
  <div class="settings-page min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border space-y-4">
    <n-card :bordered="false" size="small" class="settings-head-card">
      <div class="flex items-start justify-between">
        <div>
          <div class="text-base font-semibold transition-colors duration-300 settings-title">系统设置</div>
          <div class="mt-1 text-xs transition-colors duration-300 settings-subtitle">配置将实时联动算法、告警与数据清理模块</div>
        </div>
        <div class="flex items-center gap-2">
          <n-button round secondary :loading="loading" @click="refreshAll">
            <template #icon>
              <n-icon><refresh-outline /></n-icon>
            </template>
            刷新
          </n-button>
          <n-button round type="primary" :loading="saving" @click="saveActive">
            <template #icon>
              <n-icon><save-outline /></n-icon>
            </template>
            保存当前分组
          </n-button>
        </div>
      </div>
    </n-card>

    <n-card :bordered="false" size="small" class="settings-tabs-card">
      <n-tabs v-model:value="activeGroup" type="line" animated>
        <n-tab-pane name="SYSTEM_CONFIG" tab="基础设置">
          <ConfigGroupForm
            group="SYSTEM_CONFIG"
            :loading="loadingMap.SYSTEM_CONFIG"
            :items="configMap.SYSTEM_CONFIG"
            :values="valueMap.SYSTEM_CONFIG"
            @update="(k, v) => updateValue('SYSTEM_CONFIG', k, v)"
          />
        </n-tab-pane>
        <n-tab-pane name="ALARM_CONFIG" tab="告警与监控设置">
          <ConfigGroupForm
            group="ALARM_CONFIG"
            :loading="loadingMap.ALARM_CONFIG"
            :items="configMap.ALARM_CONFIG"
            :values="valueMap.ALARM_CONFIG"
            @update="(k, v) => updateValue('ALARM_CONFIG', k, v)"
          />
        </n-tab-pane>
        <n-tab-pane name="ALGO_CONFIG" tab="进化计算底层参数">
          <ConfigGroupForm
            group="ALGO_CONFIG"
            :loading="loadingMap.ALGO_CONFIG"
            :items="configMap.ALGO_CONFIG"
            :values="valueMap.ALGO_CONFIG"
            @update="(k, v) => updateValue('ALGO_CONFIG', k, v)"
          />
        </n-tab-pane>
        <n-tab-pane name="STORAGE_CONFIG" tab="存储与运维策略">
          <ConfigGroupForm
            group="STORAGE_CONFIG"
            :loading="loadingMap.STORAGE_CONFIG"
            :items="configMap.STORAGE_CONFIG"
            :values="valueMap.STORAGE_CONFIG"
            @update="(k, v) => updateValue('STORAGE_CONFIG', k, v)"
          />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, defineComponent, h, onMounted, PropType, reactive, ref } from 'vue';
  import {
    NButton,
    NCard,
    NEmpty,
    NForm,
    NFormItem,
    NIcon,
    NInput,
    NInputGroup,
    NInputGroupLabel,
    NInputNumber,
    NSlider,
    NSkeleton,
    NSpace,
    NSwitch,
    NTooltip,
    NTabPane,
    NTabs,
    NSelect,
    useMessage,
  } from 'naive-ui';
  import {
    RefreshOutline,
    SaveOutline,
    CopyOutline,
    GitMergeOutline,
    ScaleOutline,
    CashOutline,
    SettingsOutline,
  } from '@vicons/ionicons5';
  import { systemApi } from '@/api/blast-furnace';

  type SysConfigGroup = 'ALGO_CONFIG' | 'ALARM_CONFIG' | 'SYSTEM_CONFIG' | 'STORAGE_CONFIG';

  type SysConfigEntity = {
    id: number;
    configKey: string;
    configValue: string;
    configName: string;
    configGroup: SysConfigGroup;
    description: string | null;
  };

  const message = useMessage();

  const groups: SysConfigGroup[] = ['SYSTEM_CONFIG', 'ALARM_CONFIG', 'ALGO_CONFIG', 'STORAGE_CONFIG'];

  const activeGroup = ref<SysConfigGroup>('SYSTEM_CONFIG');
  const loading = ref(false);
  const saving = ref(false);

  const loadingMap = reactive<Record<SysConfigGroup, boolean>>({
    SYSTEM_CONFIG: false,
    ALARM_CONFIG: false,
    ALGO_CONFIG: false,
    STORAGE_CONFIG: false,
  });

  const configMap = reactive<Record<SysConfigGroup, SysConfigEntity[]>>({
    SYSTEM_CONFIG: [],
    ALARM_CONFIG: [],
    ALGO_CONFIG: [],
    STORAGE_CONFIG: [],
  });

  const valueMap = reactive<Record<SysConfigGroup, Record<string, any>>>({
    SYSTEM_CONFIG: {},
    ALARM_CONFIG: {},
    ALGO_CONFIG: {},
    STORAGE_CONFIG: {},
  });

  function isBooleanString(v: string) {
    const s = (v ?? '').trim().toLowerCase();
    return s === 'true' || s === 'false' || s === '1' || s === '0';
  }

  function toBoolean(v: string) {
    const s = (v ?? '').trim().toLowerCase();
    return s === 'true' || s === '1';
  }

  function isNumberString(v: string) {
    if (v == null) return false;
    const s = String(v).trim();
    if (!s) return false;
    return Number.isFinite(Number(s));
  }

  function parseValue(v: string) {
    if (isBooleanString(v)) return toBoolean(v);
    if (isNumberString(v)) return Number(v);
    return v ?? '';
  }

  function normalizeValue(v: any) {
    if (typeof v === 'boolean') return v ? 'true' : 'false';
    if (typeof v === 'number' && Number.isFinite(v)) return String(v);
    return (v ?? '').toString();
  }

  const forceBooleanConfigKeys = new Set<string>(['alarm_global_enable', 'monitor_strict_live_enable']);
  const forceNumberConfigKeys = new Set<string>([
    'alarm_push_interval',
    'data_retention_days',
    'system_log_retention_days',
  ]);
  const forceSelectConfigOptions: Record<string, Array<{ label: string; value: string }>> = {
    system_timezone: [
      { label: 'Asia/Shanghai', value: 'Asia/Shanghai' },
      { label: 'UTC', value: 'UTC' },
      { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
      { label: 'Europe/Berlin', value: 'Europe/Berlin' },
      { label: 'America/New_York', value: 'America/New_York' },
    ],
    system_default_furnace_id: [
      { label: 'BF-001', value: 'BF-001' },
      { label: 'BF-002', value: 'BF-002' },
      { label: 'BF-003', value: 'BF-003' },
    ],
    monitor_live_fail_threshold: [
      { label: '1次', value: '1' },
      { label: '2次', value: '2' },
      { label: '3次', value: '3' },
      { label: '5次', value: '5' },
    ],
    monitor_anomaly_sync_delay_ms: [
      { label: '100ms', value: '100' },
      { label: '300ms', value: '300' },
      { label: '500ms', value: '500' },
      { label: '1000ms', value: '1000' },
    ],
  };

  function updateValue(group: SysConfigGroup, key: string, value: any) {
    valueMap[group][key] = value;
  }

  async function loadGroup(group: SysConfigGroup) {
    loadingMap[group] = true;
    try {
      const res: any = await systemApi.config.getListByGroup(group);
      const items: SysConfigEntity[] = res.data || [];
      configMap[group] = items;
      const values: Record<string, any> = {};
      for (const item of items) {
        values[item.configKey] = parseValue(item.configValue);
      }
      valueMap[group] = values;
    } catch (e: any) {
      message.error(e?.message || '加载配置失败');
    } finally {
      loadingMap[group] = false;
    }
  }

  async function refreshAll() {
    loading.value = true;
    try {
      await Promise.all(groups.map((g) => loadGroup(g)));
    } finally {
      loading.value = false;
    }
  }

  async function saveGroup(group: SysConfigGroup) {
    saving.value = true;
    try {
      const items = (configMap[group] || []).map((c) => ({
        configKey: c.configKey,
        configValue: normalizeValue(valueMap[group][c.configKey]),
        configName: c.configName,
        description: c.description,
      }));
      await systemApi.config.batchUpdate(group, items);
      message.success('保存成功');
      await loadGroup(group);
    } catch (e: any) {
      message.error(e?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  }

  async function saveActive() {
    await saveGroup(activeGroup.value);
  }

  const ConfigGroupForm = defineComponent({
    name: 'ConfigGroupForm',
    props: {
      group: { type: String as PropType<SysConfigGroup>, required: true },
      loading: { type: Boolean, required: true },
      items: { type: Array as PropType<SysConfigEntity[]>, required: true },
      values: { type: Object as PropType<Record<string, any>>, required: true },
    },
    emits: ['update'],
    setup(props, { emit }) {
      const hasItems = computed(() => props.items && props.items.length > 0);

      const copyKey = async (key: string) => {
        try {
          await navigator.clipboard.writeText(key);
          message.success('已复制');
        } catch (_) {}
      };

      const getAlgoHelp = (key: string) => {
        const k = (key || '').trim();
        const map: Record<string, string> = {
          evo_max_iterations: '影响模块：演化计算\n作用：限制最多“尝试多少轮”。数值越大更容易找到更好的方案，但计算更久。',
          evo_max_population: '影响模块：演化计算\n作用：限制每轮“同时尝试多少个方案”。数值越大搜索更全面，但更耗时。',
          evo_weight_balanced_production: '影响模块：演化计算\n作用：平衡模式下“更看重产量”。调大后更倾向推荐产量更高的方案。',
          evo_weight_balanced_energy: '影响模块：演化计算\n作用：平衡模式下“更看重省能”。调大后更倾向推荐能耗更低的方案。',
          evo_weight_high_yield_production: '影响模块：演化计算\n作用：高产模式下“更看重产量”。调大后推荐更激进追产量。',
          evo_weight_high_yield_energy: '影响模块：演化计算\n作用：高产模式下对能耗的“刹车”。调大后高产模式也会更在意能耗。',
          evo_weight_low_energy_production: '影响模块：演化计算\n作用：低耗模式下保留多少“产量底线”。调大后低耗模式不容易把产量压太低。',
          evo_weight_low_energy_energy: '影响模块：演化计算\n作用：低耗模式下“更看重省能”。调大后更倾向推荐能耗更低的方案。',
          evo_cost_energy_weight: '影响模块：成本评分\n作用：成本里能耗占比。调大后成本评分更跟着能耗变化。',
          evo_cost_oxygen_weight: '影响模块：成本评分\n作用：成本里富氧占比。调大后富氧对成本评分影响更明显。',
          evo_cost_oxygen_base: '影响模块：成本评分\n作用：富氧起算线（默认 21%）。只有氧气含量高于它，才会开始增加“富氧成本”。',
          evo_cost_oxygen_factor: '影响模块：成本评分\n作用：富氧成本增长速度。调大后，氧气含量每多一点，成本上升更快。',
          evo_cost_oxygen_max_delta: '影响模块：成本评分\n作用：用于估算成本评分的“范围尺子”。调大后同样的富氧变化，评分波动会更平缓。',
        };
        return map[k] || '';
      };

      function renderControl(item: SysConfigEntity) {
        const v = props.values[item.configKey];
        const raw = item.configValue ?? '';
        if (forceBooleanConfigKeys.has(item.configKey)) {
          return h(NSwitch, {
            value: Boolean(v),
            'onUpdate:value': (val: boolean) => emit('update', item.configKey, val),
            size: 'small',
          });
        }
        if (forceNumberConfigKeys.has(item.configKey)) {
          const current = Number(v);
          return h(NInputNumber, {
            value: Number.isFinite(current) ? current : 0,
            min: 0,
            'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
            style: { width: '100%' },
            size: 'small',
          });
        }
        if (forceSelectConfigOptions[item.configKey]) {
          return h(NSelect, {
            value: String(v ?? raw ?? ''),
            options: forceSelectConfigOptions[item.configKey],
            filterable: true,
            clearable: false,
            'onUpdate:value': (val: string) => emit('update', item.configKey, val),
            size: 'small',
          });
        }
        if (isBooleanString(raw)) {
          return h(NSwitch, {
            value: Boolean(v),
            'onUpdate:value': (val: boolean) => emit('update', item.configKey, val),
            size: 'small',
          });
        }
        if (isNumberString(raw)) {
          const key = (item.configKey || '').toLowerCase();
          const rawText = String(raw ?? '').trim();
          if (props.group !== 'ALGO_CONFIG') {
            return h(NInputNumber, {
              value: typeof v === 'number' ? v : Number(v),
              min: 0,
              'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
              style: { width: '100%' },
              size: 'small',
            });
          }

          const isWeight = key.includes('weight') || key.includes('rate') || key.includes('prob');
          const isMoney = key.includes('price');
          const isCost = key.includes('cost') || isMoney;
          const isInt = key.includes('max_') || key.includes('iterations') || key.includes('generation') || key.includes('population');
          const isOxygenBase = key.includes('oxygen_base');
          const isOxygenMaxDelta = key.includes('oxygen_max_delta');
          const isOxygenFactor = key.includes('oxygen_factor');

          const numValue = typeof v === 'number' ? v : Number(v);

          if (isWeight) {
            const safeVal = Number.isFinite(numValue) ? Math.min(1, Math.max(0, numValue)) : 0;
            return h('div', { class: 'flex items-center gap-3 w-full' }, [
              h(NSlider, {
                value: safeVal, min: 0, max: 1, step: 0.01,
                'onUpdate:value': (val: number) => emit('update', item.configKey, val),
              }),
              h(NInputNumber, {
                value: safeVal, min: 0, max: 1, step: 0.01, precision: 2,
                'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
                style: { width: '120px' }, size: 'small',
              }),
            ]);
          }

          if (isMoney) {
            return h(NInputGroup, null, {
              default: () => [
                h(NInputGroupLabel, null, { default: () => '¥' }),
                h(NInputNumber, {
                  value: Number.isFinite(numValue) ? numValue : 0, min: 0, step: 1, precision: 2,
                  'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
                  style: { width: '120px' }, size: 'small',
                }),
              ],
            });
          }

          if (isOxygenBase || isOxygenMaxDelta) {
            return h(NInputNumber, {
              value: Number.isFinite(numValue) ? numValue : 0, min: 0, max: 100, step: 0.1, precision: 2,
              'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
              style: { width: '120px' }, size: 'small',
            }, { suffix: () => h('span', { style: 'font-size: 12px; color: var(--n-text-color-3); transition: color 0.3s;' }, '%') });
          }

          if (isOxygenFactor) {
            return h(NInputNumber, {
              value: Number.isFinite(numValue) ? numValue : 0, min: 0, step: 0.5, precision: 2,
              'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
              style: { width: '120px' }, size: 'small',
            }, { suffix: () => h('span', { style: 'font-size: 12px; color: var(--n-text-color-3); transition: color 0.3s;' }, '系数') });
          }

          if (isCost) {
            return h(NInputNumber, {
              value: Number.isFinite(numValue) ? numValue : 0, min: 0, step: 0.5, precision: 2,
              'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
              style: { width: '120px' }, size: 'small',
            });
          }

          const suffixText = key.includes('population') ? '个' : key.includes('iterations') || key.includes('generation') ? '代' : '';
          return h(NInputNumber, {
            value: Number.isFinite(numValue) ? numValue : 0, min: 0, step: isInt ? 1 : 0.1, precision: isInt ? 0 : 2,
            'onUpdate:value': (val: number | null) => emit('update', item.configKey, val ?? 0),
            style: { width: '120px' }, size: 'small',
          }, suffixText ? { suffix: () => h('span', { style: 'font-size: 12px; color: var(--n-text-color-3); transition: color 0.3s;' }, suffixText) } : undefined);
        }
        return h(NInput, {
          value: String(v ?? ''),
          'onUpdate:value': (val: string) => emit('update', item.configKey, val),
          size: 'small',
        });
      }

      const algoSections = computed(() => {
        const sections: Array<{ title: string; subtitle: string; items: SysConfigEntity[] }> = [
          { title: '迭代与种群', subtitle: '控制演化搜索规模与收敛速度', items: [] },
          { title: '权重与偏好', subtitle: '不同优化模式下的产量/能耗偏好权重', items: [] },
          { title: '成本函数', subtitle: '能耗、氧气等成本估算相关参数', items: [] },
          { title: '其他', subtitle: '未归类的底层参数', items: [] },
        ];
        const getBucketIndex = (key: string) => {
          const k = (key || '').toLowerCase();
          if (k.startsWith('evo_max_') || k.includes('iterations') || k.includes('generation') || k.includes('population')) return 0;
          if (k.startsWith('evo_weight_') || k.includes('weight')) return 1;
          if (k.startsWith('evo_cost_') || k.includes('cost') || k.includes('oxygen') || k.includes('energy')) return 2;
          return 3;
        };
        for (const item of props.items || []) {
          const idx = getBucketIndex(item.configKey);
          sections[idx].items.push(item);
        }
        return sections
          .map((s) => ({ ...s, items: s.items.slice().sort((a, b) => (a.configName || a.configKey).localeCompare(b.configName || b.configKey)) }))
          .filter((s) => s.items.length > 0);
      });

      function renderAlgoLayout() {
        const iconByTitle: Record<string, any> = { '迭代与种群': GitMergeOutline, '权重与偏好': ScaleOutline, '成本函数': CashOutline, '其他': SettingsOutline };
        return h('div', { class: 'space-y-4' }, algoSections.value.map((section) =>
          h('div', { key: section.title, class: 'algo-section' }, [
            h('div', { class: 'algo-section-header' }, [
              h('div', { class: 'flex items-center gap-3' }, [
                h('div', {
                  class: 'w-8 h-8 rounded-lg border flex items-center justify-center transition-colors duration-300',
                  style: 'background-color: var(--n-action-color); border-color: var(--n-border-color); color: #3b82f6;',
                }, h(NIcon, null, { default: () => h(iconByTitle[section.title] || SettingsOutline) })),
                h('div', null, [
                  h('div', { class: 'config-section-title' }, section.title),
                  h('div', { class: 'config-section-subtitle' }, section.subtitle),
                ]),
              ]),
            ]),
            h('div', { class: 'grid grid-cols-1 xl:grid-cols-2 gap-x-6 gap-y-2 p-4' }, section.items.map((item) =>
              h('div', { key: item.configKey, class: 'algo-row group rounded-md' }, [
                h('div', { class: 'algo-row-left' }, [
                  h('div', { class: 'algo-row-title' }, [h('div', { class: 'algo-title-text' }, item.configName || item.configKey)]),
                  h('div', { class: 'algo-row-subtitle' }, item.description ? item.description : item.configKey),
                ]),
                h('div', { class: 'flex items-center justify-end gap-2' }, [
                  (() => {
                    const helpText = getAlgoHelp(item.configKey);
                    if (!helpText) return null;
                    return h(NTooltip, { trigger: 'hover' }, {
                      trigger: () => h('span', { class: 'algo-meta-badge opacity-0 group-hover:opacity-55 transition-opacity' }, 'i'),
                      default: () => h('div', { style: 'max-width: 360px; white-space: pre-wrap; line-height: 18px;' }, helpText),
                    });
                  })(),
                  h(NTooltip, { trigger: 'hover' }, {
                    trigger: () => h('span', { class: 'algo-meta-badge opacity-0 group-hover:opacity-55 transition-opacity' }, 'k'),
                    default: () => item.configKey,
                  }),
                  h(NTooltip, { trigger: 'hover' }, {
                    trigger: () => h(NButton, { quaternary: true, size: 'tiny', class: 'opacity-0 group-hover:opacity-70 transition-opacity', onClick: () => copyKey(item.configKey) }, { icon: () => h(NIcon, null, { default: () => h(CopyOutline) }) }),
                    default: () => '复制 key',
                  }),
                  h('div', { class: 'algo-row-right' }, renderControl(item)),
                ]),
              ])
            )),
          ])
        ));
      }

      return () => {
        if (props.loading) {
          return h(NSpace, { vertical: true, size: 12 }, () => Array.from({ length: 6 }).map((_, idx) => h(NSkeleton, { key: idx, text: true, repeat: 2, height: 12 })));
        }
        if (!hasItems.value) return h(NEmpty, { description: '暂无配置' });
        if (props.group === 'ALGO_CONFIG') return renderAlgoLayout();
        return h(NForm, { labelPlacement: 'top' }, () => props.items.map((item) => h(NFormItem, { key: item.configKey, label: item.configName || item.configKey }, { default: () => renderControl(item), feedback: () => (item.description ? item.description : '') })));
      };
    },
  });

  onMounted(() => { refreshAll(); });
</script>

<style scoped>
.settings-page {
  color: var(--n-text-color-2);
}

.settings-head-card,
.settings-tabs-card {
  border: 1px solid var(--n-border-color);
  border-radius: 12px;
  box-shadow: var(--n-box-shadow-1);
}

.settings-title {
  color: var(--n-text-color-1);
}

.settings-subtitle {
  color: var(--n-text-color-3);
}

.settings-tabs-card :deep(.n-tabs-nav) {
  margin-bottom: 14px;
}

.config-section-title {
  color: var(--n-text-color-1);
  font-weight: 600;
  font-size: 13px;
  transition: color 0.3s;
}

.config-section-subtitle {
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 12px;
  transition: color 0.3s;
}

.algo-section {
  border: 1px solid var(--n-border-color);
  border-radius: 12px;
  background: var(--n-color);
  overflow: hidden;
  box-shadow: var(--n-box-shadow-1);
  transition: all 0.3s;
}

.algo-section-header {
  padding: 12px 14px;
  border-bottom: 1px solid var(--n-border-color);
  transition: border-color 0.3s;
}

.algo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  transition: background-color 0.3s;
}
.algo-row:hover {
  background-color: var(--n-hover-color);
}

.algo-row-left {
  min-width: 0;
  flex: 1 1 auto;
}

.algo-row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.algo-title-text {
  color: var(--n-text-color-1);
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.3s;
}

.algo-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  opacity: 0.55;
  flex: 0 0 auto;
}

.algo-meta-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 6px;
  border: 1px solid var(--n-border-color);
  color: var(--n-text-color-3);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  user-select: none;
  transition: all 0.3s;
}

.algo-row-subtitle {
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.3s;
}

.algo-row-right {
  flex: 0 0 auto;
  width: 120px;
}

.settings-tabs-card :deep(.n-form-item-label) {
  font-weight: 600;
}

.settings-tabs-card :deep(.n-input),
.settings-tabs-card :deep(.n-input-number),
.settings-tabs-card :deep(.n-base-selection) {
  border-radius: 10px;
}
</style>
