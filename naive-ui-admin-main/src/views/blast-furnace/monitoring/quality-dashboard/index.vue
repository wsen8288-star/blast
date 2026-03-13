<template>
  <div class="quality-dashboard h-full p-4">
    
    <n-card title="模拟数据质量看板" size="small" class="mb-4 quality-head-card">
      <div class="flex items-center gap-3">
        <n-select v-model:value="selectedFurnace" :options="furnaceOptions" clearable placeholder="全部高炉" style="width: 180px" />
        <n-input-number v-model:value="windowSize" :min="100" :max="5000" :step="100" />
        <n-button round type="primary" @click="loadMetrics" :loading="loading">刷新指标</n-button>
      </div>
    </n-card>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-4">
      <n-card v-for="item in metricCards" :key="item.key" size="small" class="quality-metric-card">
        <div class="text-xs transition-colors duration-300" style="color: var(--n-text-color-3);">{{ item.label }}</div>
        <div class="mt-2 text-2xl font-semibold">{{ item.value }}</div>
      </n-card>
    </div>

    <n-alert title="阈值来源说明" type="info" class="mb-4">
      <div class="text-xs leading-6 transition-colors duration-300" style="color: var(--n-text-color-2);">
        <div>预警触发率/硬限制越界率按统一优先级取阈值：炉号参数配置 → GLOBAL → 默认阈值。</div>
        <div class="mt-2 flex flex-wrap gap-2">
          <n-tag
            v-for="item in thresholdSourceItems"
            :key="item.key"
            size="small"
            :type="item.source === 'FURNACE' ? 'success' : item.source === 'GLOBAL' ? 'warning' : 'default'"
          >
            {{ item.label }}: {{ item.source }}
          </n-tag>
        </div>
      </div>
    </n-alert>

    <n-card title="验收指标" size="small" class="quality-table-card">
      <n-data-table :columns="columns" :data="indicatorList" :pagination="false" size="small" />
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, ref } from 'vue';
  import { NTag, useMessage } from 'naive-ui';
  import { dataManagementApi } from '@/api/blast-furnace';

  const message = useMessage();
  const loading = ref(false);
  const selectedFurnace = ref<string | null>(null);
  const windowSize = ref<number | null>(1000);
  const metrics = ref<any>({});
  const indicatorList = ref<any[]>([]);
  const metricNameMap: Record<string, string> = {
    temperature: '温度',
    pressure: '压力',
    windVolume: '风量',
    coalInjection: '喷煤量',
    materialHeight: '料面高度',
    gasFlow: '煤气流量',
    oxygenLevel: '氧气含量',
    productionRate: '生产率',
    energyConsumption: '能耗',
    hotMetalTemperature: '铁水温度',
    siliconContent: '硅含量',
  };
  const furnaceOptions = [
    { label: 'BF-001', value: 'BF-001' },
    { label: 'BF-002', value: 'BF-002' },
    { label: 'BF-003', value: 'BF-003' },
  ];

  const metricCards = computed(() => [
    { key: 'sample', label: '样本数', value: metrics.value.sampleSize ?? 0 },
    { key: 'missing', label: '缺失率', value: `${metrics.value?.metrics?.missingRatePct ?? 0}%` },
    { key: 'sentinel', label: '哨兵值占比', value: `${metrics.value?.metrics?.sentinelRatePct ?? 0}%` },
    { key: 'range', label: '硬限制越界率', value: `${metrics.value?.metrics?.outOfRangeRatePct ?? 0}%` },
    { key: 'warning', label: '预警触发率', value: `${metrics.value?.metrics?.warningRatePct ?? 0}%` },
    { key: 'corr', label: '风量-生产率相关', value: metrics.value?.metrics?.corrWindVsProduction ?? 0 },
  ]);

  const thresholdSourceItems = computed(() => {
    const sources = metrics.value?.thresholdSources ?? {};
    return Object.keys(metricNameMap).map((key) => ({
      key,
      label: metricNameMap[key] || key,
      source: sources[key] || 'DEFAULT',
    }));
  });

  const columns = [
    { title: '类别', key: 'category' },
    { title: '指标', key: 'name' },
    { title: '当前值', key: 'value' },
    { title: '目标', key: 'target' },
    {
      title: '结论',
      key: 'pass',
      render: (row: any) =>
        h(
          NTag,
          { type: row.pass ? 'success' : 'error', size: 'small' },
          { default: () => (row.pass ? '达标' : '未达标') }
        ),
    },
  ];

  const loadMetrics = async () => {
    try {
      loading.value = true;
      const res: any = await dataManagementApi.getQualityMetrics({
        furnaceId: selectedFurnace.value || undefined,
        limit: windowSize.value || 1000,
      });
      if (res.code === 200) {
        metrics.value = res.data || {};
        indicatorList.value = res.data?.indicators || [];
      } else {
        message.error(res.msg || '获取质量指标失败');
      }
    } catch (error) {
      message.error('获取质量指标失败');
    } finally {
      loading.value = false;
    }
  };

  onMounted(() => {
    loadMetrics();
  });
</script>

<style scoped>
  .quality-head-card,
  .quality-metric-card,
  .quality-table-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .quality-dashboard :deep(.n-base-selection),
  .quality-dashboard :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>
