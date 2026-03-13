<template>
  <div
    class="scheme-comparison min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="方案比较" class="mb-4 compare-main-card">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center gap-3">
          <n-text strong>智能演化优化</n-text>
          <n-radio-group v-model:value="evolutionMode" size="small">
            <n-radio-button value="HIGH_YIELD">产量优先</n-radio-button>
            <n-radio-button value="LOW_ENERGY">能耗优先</n-radio-button>
            <n-radio-button value="BALANCED">均衡模式</n-radio-button>
          </n-radio-group>
          <div class="flex items-center gap-2">
            <n-text depth="3">选择预测模型</n-text>
            <n-select
              v-model:value="selectedServiceId"
              :options="serviceOptions"
              :loading="serviceLoading"
              placeholder="选择模型服务"
              size="small"
              class="w-56"
            />
            <n-tooltip v-if="showProductionRateHint" trigger="hover">
              <template #trigger>
                <n-icon size="16" class="text-slate-500">
                  <InfoCircleOutlined />
                </n-icon>
              </template>
              请务必选择预测目标为产量的模型
            </n-tooltip>
          </div>
        </div>
        <n-button round type="primary" :loading="evolutionLoading" @click="handleStartEvolution">
          开始演化计算
        </n-button>
      </div>
      <!-- 方案选择 -->
      <n-form label-placement="left" label-width="120" class="mb-4">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <n-form-item label="方案A">
            <n-select v-model:value="schemeA" :options="schemeAOptions" placeholder="选择方案A" />
          </n-form-item>
          <n-form-item label="方案B">
            <n-select v-model:value="schemeB" :options="schemeBOptions" placeholder="选择方案B" />
          </n-form-item>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <n-form-item label="方案来源">
            <n-select
              v-model:value="schemeSource"
              :options="schemeSourceOptions"
              placeholder="选择方案来源"
            />
          </n-form-item>
          <n-form-item label="高炉选择">
            <n-select
              v-model:value="selectedFurnace"
              :options="furnaceOptions"
              :loading="furnaceOptionsLoading"
              :disabled="!!furnaceOptionsError || furnaceOptions.length === 0"
              :placeholder="furnaceOptionsError ? '高炉列表加载失败' : '选择高炉'"
            />
          </n-form-item>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4" v-if="schemeSource === 'EVOLUTION'">
          <n-form-item label="演化基准模式">
            <n-radio-group v-model:value="baselineMode" size="small">
              <n-radio-button value="AUTO">自动最新</n-radio-button>
              <n-radio-button value="MANUAL">手动选择</n-radio-button>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="演化基准数据">
            <n-select
              v-model:value="selectedBaselineId"
              :options="baselineCandidateOptions"
              :loading="baselineCandidateLoading"
              placeholder="选择演化基准数据"
              :disabled="baselineMode !== 'MANUAL'"
            />
          </n-form-item>
        </div>
        <div v-if="schemeSource === 'EVOLUTION'" class="text-xs text-slate-500 -mt-2 mb-2">
          以上基准仅用于“开始演化计算”，不用于演化历史的 A/B 方案互相比对。
        </div>
        <n-form-item label="数据来源">
          <n-text depth="3">{{ dataSourceSummary }}</n-text>
        </n-form-item>
        <n-form-item label="比较参数">
          <n-select
            v-model:value="selectedCompareParams"
            multiple
            :options="compareParamOptions"
            placeholder="选择比较参数"
          />
        </n-form-item>
        <div class="flex justify-end">
          <n-button round type="primary" @click="startComparison">开始比较</n-button>
          <n-button round @click="resetComparisonConfig" class="ml-2">重置</n-button>
        </div>
      </n-form>
      <n-alert v-if="schemeOptions.length === 0" type="warning" :show-icon="false" class="mb-4">
        {{
          schemeSource === 'EVOLUTION'
            ? '当前没有演化历史方案，请先点击“开始演化计算”生成方案。'
            : schemeSource === 'REAL'
            ? '当前没有真实工况方案，请先导入或采集生产数据。'
            : '当前没有可用于混合对比的方案，请先准备演化历史与真实工况数据。'
        }}
      </n-alert>

      <!-- 比较结果 -->
      <n-tabs type="line" v-model:value="activeTab">
        <n-tab-pane name="comparison" tab="方案比较">
          <div class="compare-panel relative">
            <div
              v-if="adoptExecuting"
              class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2"
            >
              <n-spin size="large" />
              <n-text depth="3">指令下发中...</n-text>
            </div>
            <n-card title="方案对比结果" size="small" class="mb-4 compare-sub-card">
              <div class="h-80 relative">
                <div ref="chartRef" class="w-full h-full"></div>
                <template v-if="comparisonData.length === 0">
                  <div class="absolute inset-0 flex items-center justify-center">
                    <n-empty description="暂无对比数据" />
                  </div>
                </template>
              </div>
            </n-card>

            <n-card title="方案参数对比" size="small" class="compare-sub-card">
              <div class="flex justify-end text-xs text-slate-500 mb-2" v-if="baselineTime">
                基准数据时间：{{ formatDateTime(baselineTime) }}
              </div>
              <n-data-table
                :columns="comparisonColumns"
                :data="filteredComparisonData"
                size="small"
              />
            </n-card>
            <div class="flex justify-end mt-4">
              <n-button round type="primary" size="large" :disabled="!canAdopt" @click="openAdoptModal">
                采纳并执行优化方案
              </n-button>
            </div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="analysis" tab="结果分析">
          <div class="compare-panel">
            <n-card title="方案分析报告" size="small" class="compare-sub-card">
              <div class="mb-4">
                <n-text strong>方案A：{{ getSchemeLabel(schemeA) }}</n-text>
                <n-text class="ml-4">评分：{{ schemeAScoreText }}</n-text>
                <n-text class="ml-4" depth="3">演化 Fitness：{{ schemeAFitnessText }}</n-text>
              </div>
              <div class="mb-4">
                <n-text strong>方案B：{{ getSchemeLabel(schemeB) }}</n-text>
                <n-text class="ml-4">评分：{{ schemeBScoreText }}</n-text>
                <n-text class="ml-4" depth="3">演化 Fitness：{{ schemeBFitnessText }}</n-text>
              </div>
              <div class="mb-4">
                <n-text strong>推荐方案：</n-text>
                <n-text>{{ recommendedSchemeDisplay }}</n-text>
              </div>
              <div class="mb-4">
                <n-text strong>评分口径：</n-text>
                <n-text>{{ scoreTypeText }}</n-text>
              </div>
              <div class="mt-4">
                <n-text strong>分析结论：</n-text>
                <n-text>{{ analysisConclusion }}</n-text>
              </div>
              <div class="mt-4 flex justify-end">
                <n-button round type="primary" ghost @click="exportComparisonReport">
                  一键导出方案报告
                </n-button>
              </div>
            </n-card>
            <n-card title="约束条件可视化" size="small" class="mt-4 compare-sub-card">
              <n-data-table
                :columns="constraintColumns"
                :data="constraintRows"
                size="small"
                :pagination="false"
              />
            </n-card>
            <n-card title="方案可行性原因" size="small" class="mt-4 compare-sub-card">
              <n-list bordered>
                <n-list-item v-for="(reason, index) in feasibilityReasons" :key="index">
                  <n-text>{{ reason }}</n-text>
                </n-list-item>
              </n-list>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="history" tab="历史记录">
          <div class="compare-panel">
            <div class="compare-inner-panel mb-4">
              <div class="flex flex-wrap items-center gap-3">
                <n-date-picker
                  v-model:value="historyDateRange"
                  type="daterange"
                  size="small"
                  clearable
                  placeholder="选择时间范围"
                  class="w-64"
                />
                <n-select
                  v-model:value="comparisonType"
                  :options="comparisonTypeOptions"
                  placeholder="选择比较类型"
                  size="small"
                  clearable
                  class="w-40"
                />
                <n-button round type="primary" ghost size="small" @click="queryComparisonHistory">
                  查询历史
                </n-button>
                <div v-if="selectedHistoryRowKeys.length" class="flex items-center gap-2 ml-auto">
                  <n-dropdown :options="batchOptions" @select="handleBatchSelect">
                    <n-button round type="warning" ghost size="small">批量操作</n-button>
                  </n-dropdown>
                </div>
              </div>
            </div>

            <n-tabs v-model:value="historyTab" type="segment">
              <n-tab-pane name="evolution" tab="演化历史">
                <div class="grid grid-cols-1 gap-4">
                  <n-card size="small" class="compare-sub-card">
                    <template #header>
                      <div class="flex items-center justify-between">
                        <span>演化趋势分析</span>
                        <n-radio-group v-model:value="evolutionParam" size="small">
                          <n-radio-button
                            v-for="opt in evolutionParamOptions"
                            :key="opt.value"
                            :value="opt.value"
                          >
                            {{ opt.label }}
                          </n-radio-button>
                        </n-radio-group>
                      </div>
                    </template>
                    <div class="h-72">
                      <div ref="evolutionChartRef" class="w-full h-full"></div>
                    </div>
                  </n-card>
                  <n-card title="演化方案库" size="small" class="compare-sub-card">
                    <n-data-table
                      :columns="historyColumns"
                      :data="historyList"
                      :row-key="(row) => row.id"
                      size="small"
                      @update:checked-row-keys="handleCheck"
                    />
                  </n-card>
                </div>
              </n-tab-pane>
              <n-tab-pane name="comparison" tab="比较记录">
                <n-card title="对比操作日志" size="small" class="compare-sub-card">
                  <n-data-table
                    :columns="compareHistoryColumns"
                    :data="compareHistoryList"
                    :row-key="(row) => row.id"
                    size="small"
                    @update:checked-row-keys="handleCheck"
                  />
                </n-card>
              </n-tab-pane>
              <n-tab-pane name="execution" tab="执行记录">
                <n-card title="方案执行日志" size="small" class="compare-sub-card">
                  <n-data-table
                    :columns="executionLogColumns"
                    :data="executionLogList"
                    :row-key="(row) => row.id"
                    size="small"
                  />
                </n-card>
              </n-tab-pane>
            </n-tabs>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
    <n-modal v-model:show="detailVisible" preset="card" :style="{ width: 'min(960px, 92vw)' }">
      <n-card title="演化详情" size="small" :bordered="false" class="compare-sub-card">
        <n-descriptions :column="2" size="small" class="mb-4">
          <n-descriptions-item label="演化时间">{{ detailMeta.time }}</n-descriptions-item>
          <n-descriptions-item label="优化模式">{{ detailMeta.mode }}</n-descriptions-item>
          <n-descriptions-item label="最优方案">{{ detailMeta.schemeA }}</n-descriptions-item>
          <n-descriptions-item label="次优候选">{{ detailMeta.schemeB }}</n-descriptions-item>
          <n-descriptions-item label="最优评分">{{ detailMeta.scoreA }}</n-descriptions-item>
          <n-descriptions-item label="次优评分">{{ detailMeta.scoreB }}</n-descriptions-item>
          <n-descriptions-item label="演化结论">{{ detailMeta.result }}</n-descriptions-item>
        </n-descriptions>
        <n-data-table
          :columns="detailColumns"
          :data="detailSolutions"
          size="small"
          :loading="detailLoading"
        />
        <n-card title="寻优维度" size="small" class="mt-4 compare-sub-card">
          <n-data-table
            :columns="detailGeneColumns"
            :data="detailGeneRows"
            size="small"
            :loading="detailLoading"
          />
        </n-card>
      </n-card>
    </n-modal>
    <n-modal v-model:show="adoptVisible" preset="card" :style="{ width: 'min(640px, 92vw)' }">
      <n-card
        title="优化调度指令确认"
        size="small"
        :bordered="false"
      >
        <div v-if="adoptAdjustments.length" class="space-y-2 mb-4">
          <div
            v-for="item in adoptAdjustments"
            :key="item.label"
            class="flex items-center justify-between"
          >
            <n-text>{{ item.label }}</n-text>
            <n-text :type="item.tagType">
              {{ item.from }} -> {{ item.to }}{{ item.unit }} {{ item.directionLabel }}
            </n-text>
          </div>
        </div>
        <n-empty v-else description="暂无可执行的调整项" class="mb-4" />
        <div class="flex justify-end gap-2">
          <n-button @click="adoptVisible = false">取消</n-button>
          <n-button type="primary" :loading="adoptSubmitting" @click="handleConfirmAdopt">
            确认执行
          </n-button>
        </div>
      </n-card>
    </n-modal>
    <n-modal v-model:show="compareDetailVisible" preset="card" :style="{ width: 'min(960px, 92vw)' }">
      <n-card title="比较详情" size="small" :bordered="false" class="compare-sub-card">
        <n-descriptions :column="2" size="small" class="mb-4">
          <n-descriptions-item label="比较时间">{{ compareDetailMeta.time }}</n-descriptions-item>
          <n-descriptions-item label="比较来源">{{ compareDetailMeta.mode }}</n-descriptions-item>
          <n-descriptions-item label="方案A">{{ compareDetailMeta.schemeA }}</n-descriptions-item>
          <n-descriptions-item label="方案B">{{ compareDetailMeta.schemeB }}</n-descriptions-item>
          <n-descriptions-item label="评分A">{{ compareDetailMeta.scoreA }}</n-descriptions-item>
          <n-descriptions-item label="评分B">{{ compareDetailMeta.scoreB }}</n-descriptions-item>
          <n-descriptions-item label="比较结论">{{ compareDetailMeta.result }}</n-descriptions-item>
        </n-descriptions>
        <n-data-table
          :columns="compareDetailColumns"
          :data="compareDetailRows"
          size="small"
          :loading="compareDetailLoading"
        />
      </n-card>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, h, onMounted, watch, computed, nextTick } from 'vue';
  import { useRoute } from 'vue-router';
  import { useMessage, useDialog, NButton, NIcon, NDropdown, NTag } from 'naive-ui';
  import { DeleteOutlined, EyeOutlined, InfoCircleOutlined } from '@vicons/antd';
  import { comparisonApi, optimizationApi, dataManagementApi } from '@/api/blast-furnace';
  import { formatNumericDisplay } from '@/utils/format';
  import request from '@/utils/request';
  import * as echarts from 'echarts';
  import { ensureQuickStartRunId, setQuickStartRun } from '@/utils/quickStartRun';

  const message = useMessage();
  const dialog = useDialog();
  const route = useRoute();

  const resolveRunId = () => {
    const queryRunId = String(route.query.runId || '').trim();
    const runId = queryRunId || ensureQuickStartRunId(24);
    if (queryRunId) setQuickStartRun(queryRunId);
    return runId;
  };
  const activeTab = ref('comparison');

  // 方案选择
  const selectedFurnace = ref('');
  const furnaceOptions = ref<Array<{ label: string; value: string }>>([]);
  const furnaceOptionsLoading = ref(false);
  const furnaceOptionsError = ref<string | null>(null);

  const schemeSource = ref('EVOLUTION');
  const schemeSourceOptions = [
    { label: '演化方案', value: 'EVOLUTION' },
    { label: '真实工况', value: 'REAL' },
    { label: '混合对比', value: 'MIXED' },
  ];
  const schemeA = ref('');
  const schemeB = ref('');
  const schemeOptions = ref<Array<any>>([]);
  const schemeAOptions = computed(() => {
    if (schemeSource.value === 'MIXED') {
      return schemeOptions.value.filter((opt) => opt.type === 'EVOLUTION');
    }
    return schemeOptions.value;
  });

  const schemeBOptions = computed(() => {
    if (schemeSource.value === 'MIXED') {
      return schemeOptions.value.filter((opt) => opt.type === 'REAL');
    }
    return schemeOptions.value;
  });

  const baselineSnapshot = ref<any>(null);
  const baselineMode = ref<'AUTO' | 'MANUAL'>('AUTO');
  const baselineCandidateLoading = ref(false);
  const baselineCandidateOptions = ref<Array<any>>([]);
  const baselineCandidates = ref<Array<any>>([]);
  const selectedBaselineId = ref<number | null>(null);

  const selectedCompareParams = ref(['production', 'energy', 'temperature', 'pressure', 'gasFlow']);
  const compareParamOptions = [
    { label: '产量', value: 'production' },
    { label: '能耗', value: 'energy' },
    { label: '生产率', value: 'productionRate' },
    { label: '能耗强度', value: 'energyConsumption' },
    { label: '温度', value: 'temperature' },
    { label: '压力', value: 'pressure' },
    { label: '风量', value: 'windVolume' },
    { label: '喷煤量', value: 'coalInjection' },
    { label: '煤气流量', value: 'gasFlow' },
    { label: '氧气含量', value: 'oxygenLevel' },
    { label: '料面高度', value: 'materialHeight' },
    { label: '铁水温度', value: 'hotMetalTemperature' },
    { label: '铁水含硅量', value: 'siliconContent' },
    { label: '稳定性', value: 'stability' },
    { label: '成本', value: 'cost' },
  ];

  // 比较结果数据
  const comparisonData = ref<Array<any>>([]);
  const baselineTime = ref<any>(null);

  const loadFurnaceOptions = async () => {
    furnaceOptionsLoading.value = true;
    furnaceOptionsError.value = null;
    try {
      const res: any = await dataManagementApi.getSchemeCandidates({ limit: 200 });
      if (res?.code !== 200) {
        throw new Error(res?.msg || '获取高炉列表失败');
      }
      const rows = (res?.data || []) as Array<any>;
      const ids = Array.from(
        new Set(
          rows
            .map((row) => String(row?.furnaceId || '').trim())
            .filter((id) => id.length > 0)
        )
      );
      const options = ids.map((id) => ({ label: `高炉${id}`, value: id }));
      furnaceOptions.value = options;
      if (!selectedFurnace.value) {
        selectedFurnace.value = options.length > 0 ? options[0].value : '';
      }
      if (options.length === 0) {
        furnaceOptionsError.value = '未获取到任何高炉数据';
      }
    } catch (error: any) {
      furnaceOptions.value = [];
      if (!selectedFurnace.value) {
        selectedFurnace.value = '';
      }
      furnaceOptionsError.value = error?.message || '高炉列表加载失败';
      message.error(`高炉列表加载失败：${furnaceOptionsError.value}`);
    } finally {
      furnaceOptionsLoading.value = false;
    }
  };

  const isBetterMetric = (param: string, diff: number) => {
    if (param === 'production') {
      return diff > 0;
    }
    if (param === 'energy') {
      return diff < 0;
    }
    if (param === 'stability' || param === 'cost') {
      return diff > 0;
    }
    return null;
  };

  const formatDisplayValue = (value: any, unit?: string) => {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return `${formatNumber(Number(value))}${unit ? ` ${unit}` : ''}`;
  };

  const comparisonColumns = [
    {
      title: '比较参数',
      key: 'param',
      render: (row: any) => row.label || getCompareParamLabel(row.param),
    },
    {
      title: '方案A',
      key: 'schemeA',
      render: (row: any) => formatDisplayValue(row.schemeA, row.unit),
    },
    {
      title: '方案B',
      key: 'schemeB',
      render: (row: any) => formatDisplayValue(row.schemeB, row.unit),
    },
    {
      title: '差异',
      key: 'difference',
      render: (row: any) => {
        const hasA =
          row.schemeA !== null && row.schemeA !== undefined && !Number.isNaN(row.schemeA);
        const hasB =
          row.schemeB !== null && row.schemeB !== undefined && !Number.isNaN(row.schemeB);
        if (!hasA || !hasB) {
          return '-';
        }
        const diff =
          row.difference !== null && row.difference !== undefined
            ? row.difference
            : Number(row.schemeA) - Number(row.schemeB);
        const isBetter =
          row.better === null || row.better === undefined
            ? isBetterMetric(row.param, diff)
            : row.better > 0;
        const tagType =
          row.better === 0
            ? 'default'
            : isBetter === null
            ? 'default'
            : isBetter
            ? 'success'
            : 'error';
        const arrow = diff === 0 ? '' : diff > 0 ? '↑' : '↓';
        return h(
          'n-tag',
          { type: tagType },
          { default: () => `${arrow} ${formatNumber(Math.abs(diff))}${row.unit ? ` ${row.unit}` : ''}` }
        );
      },
    },
  ];
  const constraintColumns = [
    { title: '参数', key: 'label', align: 'center' },
    { title: '约束范围', key: 'rangeText', align: 'center' },
    { title: '方案A', key: 'schemeAText', align: 'center' },
    { title: '方案B', key: 'schemeBText', align: 'center' },
    {
      title: '可行性',
      key: 'feasibility',
      align: 'center',
      render: (row: any) => {
        const type = row.feasibleA && row.feasibleB ? 'success' : row.feasibleA || row.feasibleB ? 'warning' : 'error';
        return h(NTag, { type, size: 'small' }, { default: () => row.feasibility });
      },
    },
  ];

  // 方案评分
  const schemeAScore = ref<number | null>(null);
  const schemeBScore = ref<number | null>(null);
  const schemeAFitness = ref<number | null>(null);
  const schemeBFitness = ref<number | null>(null);
  const scoreType = ref('USER_SCORE');
  const recommendedScheme = ref('');
  const analysisConclusionText = ref('');
  const evolutionMode = ref('BALANCED');
  const evolutionLoading = ref(false);
  const serviceOptions = ref<Array<{ label: string; value: number }>>([]);
  const selectedServiceId = ref<number | null>(null);
  const serviceLoading = ref(false);
  const targetVariableSupported = ref(false);
  const showProductionRateHint = computed(() => !targetVariableSupported.value);
  const adoptVisible = ref(false);
  const adoptSubmitting = ref(false);
  const adoptExecuting = ref(false);

  // 历史查询
  const historyDateRange = ref([new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), new Date()]);
  const historyTab = ref('evolution');
  const comparisonType = ref('');
  const comparisonTypeOptions = [
    { label: '产量优先', value: 'HIGH_YIELD' },
    { label: '能耗优先', value: 'LOW_ENERGY' },
    { label: '均衡模式', value: 'BALANCED' },
    { label: '真实工况', value: 'REAL' },
    { label: '混合来源', value: 'MIXED' },
  ];

  // 历史记录
  const historyList = ref<Array<any>>([]);
  const compareHistoryList = ref<Array<any>>([]);
  const executionLogList = ref<Array<any>>([]);
  const selectedHistoryRowKeys = ref<Array<number>>([]);
  const evolutionProcess = ref<{
    maxFitness: number[];
    avgFitness: number[];
    bestSolutions?: Array<{
      temperature: number;
      pressure: number;
      gasFlow: number;
      oxygenLevel: number;
      materialHeight: number;
      genes?: Record<string, number>;
    }>;
  } | null>(null);

  const evolutionParam = ref('fitness');
  const baseEvolutionParamOptions = [
    { label: '适应度', value: 'fitness' },
    { label: '炉温 (℃)', value: 'temperature' },
    { label: '炉顶压力 (MPa)', value: 'pressure' },
    { label: '风量 (m³/h)', value: 'windVolume' },
    { label: '喷煤量 (kg/t)', value: 'coalInjection' },
    { label: '煤气流速 (m³/h)', value: 'gasFlow' },
    { label: '富氧率 (%)', value: 'oxygenLevel' },
    { label: '料线高度 (m)', value: 'materialHeight' },
    { label: '生产率 (t/h)', value: 'productionRate' },
    { label: '能耗 (kgce/t)', value: 'energyConsumption' },
    { label: '铁水温度 (℃)', value: 'hotMetalTemperature' },
    { label: '铁水含硅量 (%)', value: 'siliconContent' },
  ];
  const evolutionParamOptions = computed(() => {
    const keys = Object.keys(evolutionProcess.value?.bestSolutions?.[0]?.genes || {});
    const dynamic = keys
      .filter((key) => key && !baseEvolutionParamOptions.some((opt) => opt.value === key))
      .sort()
      .map((key) => ({ label: key, value: key }));
    return [...baseEvolutionParamOptions, ...dynamic];
  });

  const fetchExecutionLogs = async () => {
    try {
      const res = await comparisonApi.getOperationLogs({ page: 0, size: 200 });
      const data = res?.data;
      const rows = (Array.isArray(data) ? data : data?.content || []) as Array<any>;
      executionLogList.value = rows.map((row) => ({
        id: row.id,
        time: formatDateTime(row.executionTime),
        operator: row.operator,
        schemeId: row.schemeId,
        adjustments: row.adjustments,
      }));
    } catch (e: any) {
      message.error('获取执行记录失败');
    }
  };

  const handleCheck = (rowKeys: Array<number>) => {
    selectedHistoryRowKeys.value = rowKeys;
  };

  const historyColumns = [
    { type: 'selection', width: 50, align: 'center' },
    { title: '历史ID', key: 'id', width: 80, align: 'center' },
    { title: '演化时间', key: 'time', minWidth: 160, align: 'center' },
    {
      title: '演化方案',
      key: 'schemeA',
      minWidth: 140,
      align: 'center',
      ellipsis: { tooltip: true },
      render: (row: any) => getSchemeLabel(row.schemeA).replace('演化方案-', ''),
    },
    {
      title: '适应度评分',
      key: 'scoreA',
      width: 100,
      align: 'center',
      render: (row: any) => formatScoreLabel(row.scoreA),
    },
    {
      title: '次优候选',
      key: 'schemeB',
      minWidth: 140,
      align: 'center',
      ellipsis: { tooltip: true },
      render: (row: any) => getSchemeLabel(row.schemeB).replace('候选方案-', '').replace('基准工况-', ''),
    },
    {
    
      title: '优化模式',
      key: 'type',
      width: 120,
      align: 'center',
      render: (row: any) => {
        const label = getModeLabel(row.type);
        let type = 'default';
        if (row.type === 'HIGH_YIELD') type = 'warning';
        else if (row.type === 'LOW_ENERGY') type = 'success';
        else if (row.type === 'BALANCED') type = 'info';
        else if (row.type === 'REAL') type = 'primary';

        return h(
          NTag,
          { type: type as any, size: 'small', bordered: false },
          { default: () => label }
        );
      },
    },
    {
      title: '演化结论',
      key: 'result',
      minWidth: 150,
      align: 'center',
      ellipsis: { tooltip: true },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      align: 'center',
      render: (row: any) => {
        return h('div', { class: 'flex items-center justify-center gap-3' }, [
          h(
            NButton,
            {
              size: 'small',
              type: 'primary',
              ghost: true,
              onClick: () => handleViewHistory(row),
            },
            {
              icon: () => h(NIcon, null, { default: () => h(EyeOutlined) }),
              default: () => '详情',
            }
          ),
          h(
            NButton,
            {
              size: 'small',
              type: 'error',
              ghost: true,
              onClick: () => handleDeleteHistory(row),
            },
            {
              icon: () => h(NIcon, null, { default: () => h(DeleteOutlined) }),
              default: () => '删除',
            }
          ),
        ]);
      },
    },
  ];

  const compareHistoryColumns = [
    { type: 'selection', width: 50, align: 'center' },
    { title: '记录ID', key: 'id', width: 80, align: 'center' },
    { title: '对比时间', key: 'time', minWidth: 160, align: 'center' },
    {
      title: '方案A名称',
      key: 'schemeA',
      minWidth: 140,
      align: 'center',
      ellipsis: { tooltip: true },
    },
    {
      title: '方案B名称',
      key: 'schemeB',
      minWidth: 140,
      align: 'center',
      ellipsis: { tooltip: true },
    },
    {
      title: '数据来源',
      key: 'type',
      width: 120,
      align: 'center',
      render: (row: any) => {
        const label = getModeLabel(row.type);
        let type = 'default';
        if (row.type === 'HIGH_YIELD') type = 'warning';
        else if (row.type === 'LOW_ENERGY') type = 'success';
        else if (row.type === 'BALANCED') type = 'info';
        else if (row.type === 'REAL') type = 'primary';

        return h(
          NTag,
          { type: type as any, size: 'small', bordered: false },
          { default: () => label }
        );
      },
    },
    {
      title: '评分结果',
      key: 'result',
      minWidth: 150,
      align: 'center',
      ellipsis: { tooltip: true },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      align: 'center',
      render: (row: any) => {
        return h('div', { class: 'flex items-center justify-center gap-3' }, [
          h(
            NButton,
            {
              size: 'small',
              type: 'primary',
              ghost: true,
              onClick: () => handleViewCompareHistory(row),
            },
            {
              icon: () => h(NIcon, null, { default: () => h(EyeOutlined) }),
              default: () => '详情',
            }
          ),
          h(
            NButton,
            {
              size: 'small',
              type: 'error',
              ghost: true,
              onClick: () => handleDeleteHistory(row),
            },
            {
              icon: () => h(NIcon, null, { default: () => h(DeleteOutlined) }),
              default: () => '删除',
            }
          ),
        ]);
      },
    },
  ];

  const executionLogColumns = [
    { title: '记录ID', key: 'id', width: 80, align: 'center' },
    { title: '执行时间', key: 'time', minWidth: 160, align: 'center' },
    { title: '操作人', key: 'operator', width: 100, align: 'center' },
    {
      title: '执行参数摘要',
      key: 'summary',
      minWidth: 300,
      render: (row: any) => {
        try {
          const data = JSON.parse(row.adjustments);
          const parts: string[] = [];
          if (data.temperature) parts.push(`温度:${formatNumber(data.temperature)}℃`);
          if (data.pressure) parts.push(`压力:${formatNumber(data.pressure)}MPa`);
          if (data.gasFlow) parts.push(`煤气:${formatNumber(data.gasFlow)}m³/h`);
          return parts.join(' | ');
        } catch (e) {
          return '解析失败';
        }
      },
    },
  ];

  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailMeta = ref({
    time: '',
    mode: '',
    schemeA: '',
    schemeB: '',
    result: '',
    scoreA: '',
    scoreB: '',
  });
  const detailSolutions = ref<Array<any>>([]);
  const detailSearchFeatures = ref<string[]>([]);
  const detailRanges = ref<Record<string, any> | null>(null);
  const detailBaselineGenes = ref<Record<string, any> | null>(null);
  const detailGeneRows = ref<Array<any>>([]);
  const detailColumns = [
    { title: '方案', key: 'name' },
    {
      title: '预测来源',
      key: 'modelUsed',
      render: (row: any) => {
        const modelUsed = String(row.modelUsed || '');
        const source = modelUsed.includes(':') ? modelUsed.split(':')[0] : modelUsed || '-';
        return source;
      },
    },
    { title: '预测模型', key: 'modelUsed' },
    {
      title: '产量',
      key: 'predictedProduction',
      render: (row: any) => `${formatNumber(row.predictedProduction)} t/d`,
    },
    {
      title: '能耗',
      key: 'estimatedEnergy',
      render: (row: any) => `${formatNumber(row.estimatedEnergy)} kgce/t`,
    },
    {
      title: '温度',
      key: 'temperature',
      render: (row: any) => `${formatNumber(row.temperature)} ℃`,
    },
    {
      title: '压力',
      key: 'pressure',
      render: (row: any) => `${formatNumber(row.pressure)} MPa`,
    },
    {
      title: '煤气流量',
      key: 'gasFlow',
      render: (row: any) => `${formatNumber(row.gasFlow)} m³/h`,
    },
    {
      title: '氧气含量',
      key: 'oxygenLevel',
      render: (row: any) => `${formatNumber(row.oxygenLevel)} %`,
    },
    {
      title: '料面高度',
      key: 'materialHeight',
      render: (row: any) => `${formatNumber(row.materialHeight)} m`,
    },
    { title: '适应度', key: 'fitness', render: (row: any) => formatNumber(row.fitness) },
    { title: '评分解释', key: 'explanation' },
  ];
  const detailGeneColumns = [
    { title: '特征', key: 'feature' },
    { title: '参考值', key: 'baseline', render: (row: any) => formatNumber(row.baseline) },
    { title: '优化值', key: 'optimized', render: (row: any) => formatNumber(row.optimized) },
    { title: '变化量', key: 'delta', render: (row: any) => formatNumber(row.delta) },
    { title: '下限', key: 'min', render: (row: any) => formatNumber(row.min) },
    { title: '上限', key: 'max', render: (row: any) => formatNumber(row.max) },
  ];

  const compareDetailVisible = ref(false);
  const compareDetailLoading = ref(false);
  const compareDetailMeta = ref({
    time: '',
    mode: '',
    schemeA: '',
    schemeB: '',
    result: '',
    scoreA: '',
    scoreB: '',
  });
  const compareDetailRows = ref<Array<any>>([]);
  const compareDetailColumns = [
    { title: '比较参数', key: 'label' },
    {
      title: '方案A',
      key: 'schemeA',
      render: (row: any) => `${formatNumber(row.schemeA)} ${row.unit}`,
    },
    {
      title: '方案B',
      key: 'schemeB',
      render: (row: any) => `${formatNumber(row.schemeB)} ${row.unit}`,
    },
    {
      title: '差异',
      key: 'difference',
      render: (row: any) => `${formatNumber(row.difference)} ${row.unit}`,
    },
  ];

  // 方法
  const getSchemeLabel = (scheme: string) => {
    const option = schemeOptions.value.find((opt) => opt.value === scheme);
    let label = option ? option.label : scheme;

    if (schemeSource.value === 'EVOLUTION') {
      // Remove furnace info from label
      // Format is usually: "🤖 方案名 | 评分: xx" or similar
      // If it contains furnace info, strip it. But Evolution label usually doesn't have it.
      // Wait, the user said "Evolution scheme naming rule, furnace selection is meaningless"
      // The issue is likely that the generated scheme name contains furnace ID which is redundant
      // Let's clean up the label display
      return label.replace(/高炉BF-\d+-/, '');
    }
    return label;
  };

  const getCompareParamLabel = (param: string) => {
    const option = compareParamOptions.find((opt) => opt.value === param);
    return option ? option.label : param;
  };

  const getSchemeAlias = (key: 'A' | 'B') => {
    if (schemeSource.value === 'EVOLUTION') {
      return key === 'A' ? '演化方案A' : '演化方案B';
    }
    if (schemeSource.value === 'REAL') {
      return key === 'A' ? '真实工况A' : '真实工况B';
    }
    return key === 'A' ? '演化方案' : '真实工况';
  };

  const getOptionByValue = (value: string) => {
    return schemeOptions.value.find((option) => option.value === value);
  };

  const getAdoptOption = () => {
    if (recommendedScheme.value === '方案B') {
      return getOptionByValue(schemeB.value);
    }
    return getOptionByValue(schemeA.value);
  };

  const openAdoptModal = () => {
    if (!canAdopt.value) {
      message.warning('暂无可采纳的优化方案');
      return;
    }
    adoptVisible.value = true;
  };

  const handleConfirmAdopt = async () => {
    if (!canAdopt.value) {
      message.warning('暂无可采纳的优化方案');
      return;
    }
    const option = getAdoptOption();
    if (!option?.historyId || option.solutionIndex === undefined) {
      message.error('请选择演化方案进行采纳');
      return;
    }
    adoptSubmitting.value = true;
    try {
      await comparisonApi.adoptScheme(option.historyId, option.solutionIndex);
      adoptVisible.value = false;
      adoptExecuting.value = true;
      message.success('指令下发成功，已记录操作日志');
      await refreshBaselineAfterAdopt();
      await fetchExecutionLogs();
    } catch (error: any) {
      message.error(error?.message || '指令下发失败');
    } finally {
      adoptExecuting.value = false;
      adoptSubmitting.value = false;
    }
  };

  const refreshBaselineAfterAdopt = async () => {
    await fetchBaselineData();
    if (baselineSnapshot.value?.timestamp) {
      baselineTime.value = baselineSnapshot.value.timestamp;
    }
    updateChart();
  };

  const startComparison = async () => {
    if (schemeOptions.value.length === 0) {
      message.warning(
        schemeSource.value === 'EVOLUTION'
          ? '暂无演化历史方案，请先进行演化计算'
          : schemeSource.value === 'REAL'
          ? '暂无真实工况方案，请先导入或采集生产数据'
          : '暂无可用于混合对比的方案，请先准备演化历史与真实工况数据'
      );
      return;
    }
    try {
      const optionA = getOptionByValue(schemeA.value);
      const optionB = getOptionByValue(schemeB.value);

      if (!optionA) {
        message.warning('请选择方案A');
        return;
      }
      if (!optionB) {
        message.warning('请选择方案B');
        return;
      }
      if (schemeSource.value === 'MIXED') {
        if (optionA.type !== 'EVOLUTION') {
          message.warning('混合对比中方案A必须是演化方案');
          return;
        }
        if (optionB.type !== 'REAL') {
          message.warning('混合对比中方案B必须是真实工况');
          return;
        }
      }

      const handleResult = async (res: any) => {
        const result = res?.data || {};
        const rows = (result.rows || []) as Array<any>;
        comparisonData.value = rows.map((row) => ({
          ...row,
          baseline:
            row.baseline !== null && row.baseline !== undefined ? Number(row.baseline) : null,
        }));
        baselineTime.value = result.baselineTime;
        schemeAScore.value = typeof result.scoreA === 'number' ? Math.round(result.scoreA) : null;
        schemeBScore.value = typeof result.scoreB === 'number' ? Math.round(result.scoreB) : null;
        schemeAFitness.value = typeof result.fitnessA === 'number' ? Math.round(result.fitnessA) : null;
        schemeBFitness.value = typeof result.fitnessB === 'number' ? Math.round(result.fitnessB) : null;
        scoreType.value = result.scoreType || 'USER_SCORE';
        analysisConclusionText.value = result.analysisConclusion || '';
        recommendedScheme.value =
          result.recommended ||
          (schemeAScore.value !== null && schemeBScore.value !== null
            ? schemeAScore.value >= schemeBScore.value
              ? '方案A'
              : '方案B'
            : '');
        await nextTick();
        updateChart();
        if (historyDateRange.value && historyDateRange.value.length === 2) {
          historyDateRange.value = [historyDateRange.value[0], new Date()];
        }
        await fetchCompareHistory();
      };

      // Case 1: Evolution (A) vs Evolution (B)
      if (optionA.type === 'EVOLUTION' && optionB?.type === 'EVOLUTION') {
        if (!optionA.historyId || !optionB.historyId) {
          message.error('无效的演化方案数据');
          return;
        }
        const result = await comparisonApi.compareEvolution({
          historyIdA: optionA.historyId,
          indexA: optionA.solutionIndex,
          historyIdB: optionB.historyId,
          indexB: optionB.solutionIndex,
          furnaceId: selectedFurnace.value,
        });
        await handleResult(result);
        return;
      }

      // Case 2: Real (A) vs Real (B)
      if (optionA.type === 'REAL' && optionB?.type === 'REAL') {
        const res = await comparisonApi.compareProduction({
          dataIdA: optionA.dataId,
          dataIdB: optionB.dataId,
        });
        await handleResult(res);
        return;
      }

      // Case 3: Evolution (A) vs (Real or Baseline) (B)
      if (optionA.type === 'EVOLUTION') {
        let baselineId: number | undefined = undefined;

        if (optionB?.type === 'REAL') {
          baselineId = Number(optionB.dataId);
        } else if (baselineMode.value === 'MANUAL') {
          if (!selectedBaselineId.value) {
            message.warning('请先选择基准数据');
            return;
          }
          baselineId = Number(selectedBaselineId.value);
        }
        // 如果未选择真实工况，baselineId 为空时由后端自动选择基准

        const res = await comparisonApi.compareEvolutionBaseline({
          historyId: optionA.historyId,
          index: optionA.solutionIndex,
          furnaceId: selectedFurnace.value,
          baselineId: baselineId,
        });
        await handleResult(res);
        return;
      }

      message.warning('不支持的对比组合，请重新选择');
    } catch (error: any) {
      message.error(error?.message || '方案比较失败');
    }
  };

  const resetComparisonConfig = () => {
    const options = schemeOptions.value;
    schemeA.value = options[0]?.value || '';
    schemeB.value = options[1]?.value || options[0]?.value || '';
    selectedCompareParams.value = ['production', 'energy', 'temperature', 'pressure', 'gasFlow'];
    schemeAScore.value = null;
    schemeBScore.value = null;
    schemeAFitness.value = null;
    schemeBFitness.value = null;
    scoreType.value = 'USER_SCORE';
    recommendedScheme.value = '';
    analysisConclusionText.value = '';
    message.success('重置比较配置成功');
  };

  const queryComparisonHistory = () => {
    fetchEvolutionHistory();
    fetchCompareHistory();
  };

  const normalizeEvolutionPayload = (payload: any) => {
    if (Array.isArray(payload)) {
      return { solutions: payload, evolutionProcess: null, searchFeatures: [], ranges: null, baselineGenes: null };
    }
    if (!payload || typeof payload !== 'object') {
      return { solutions: [], evolutionProcess: null, searchFeatures: [], ranges: null, baselineGenes: null };
    }
    const solutions = Array.isArray(payload.topSolutions)
      ? payload.topSolutions
      : Array.isArray(payload.solutions)
      ? payload.solutions
      : [];
    const evolutionProcess =
      payload.evolutionProcess && typeof payload.evolutionProcess === 'object'
        ? payload.evolutionProcess
        : Array.isArray(payload.maxFitnessHistory) || Array.isArray(payload.avgFitnessHistory)
        ? {
            maxFitness: payload.maxFitnessHistory || [],
            avgFitness: payload.avgFitnessHistory || [],
            bestSolutions: payload.bestSolutions || [],
          }
        : null;
    const searchFeatures = Array.isArray(payload.searchFeatures) ? payload.searchFeatures : [];
    const ranges = payload.ranges && typeof payload.ranges === 'object' ? payload.ranges : null;
    const baselineGenes = payload.baselineGenes && typeof payload.baselineGenes === 'object' ? payload.baselineGenes : null;
    return { solutions, evolutionProcess, searchFeatures, ranges, baselineGenes };
  };

  const resolveRequestErrorMessage = (error: any, fallback: string) => {
    const responseData = error?.response?.data;
    const messageText =
      responseData?.message ||
      responseData?.msg ||
      responseData?.error ||
      error?.message ||
      error?.msg;
    if (typeof messageText === 'string' && messageText.trim().length > 0) {
      return messageText.trim();
    }
    return fallback;
  };

  const waitEvolutionResult = async (taskId: string) => {
    const timeoutMs = 120000;
    const intervalMs = 1200;
    const startAt = Date.now();
    while (Date.now() - startAt < timeoutMs) {
      const progressRes = await optimizationApi.getOptimizationProgress(taskId);
      const progressPayload = progressRes?.data || {};
      const status = progressPayload.status || 'running';
      if (status === 'failed') {
        throw new Error(progressPayload.message || '演化计算失败');
      }
      if (status === 'completed') {
        const resultRes = await optimizationApi.getOptimizationResult(taskId);
        const resultPayload = resultRes?.data || {};
        if (resultPayload.status === 'failed') {
          throw new Error(resultPayload.message || '演化计算失败');
        }
        return resultPayload;
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }
    throw new Error('演化计算超时，请稍后查看历史结果');
  };

  const handleStartEvolution = async () => {
    if (!selectedServiceId.value) {
      message.warning('请先选择预测模型');
      return;
    }
    if (baselineMode.value === 'MANUAL' && !selectedBaselineId.value) {
      message.warning('请先选择基准数据');
      return;
    }
    evolutionLoading.value = true;
    try {
      if (baselineMode.value === 'AUTO') {
        await fetchBaselineData();
      }
      const baselineDataId =
        baselineMode.value === 'MANUAL' ? selectedBaselineId.value : baselineSnapshot.value?.id;
      const res = await optimizationApi.startEvolutionaryOptimization({
        runId: resolveRunId(),
        mode: evolutionMode.value,
        generations: 40,
        populationSize: 50,
        serviceId: selectedServiceId.value,
        furnaceId: selectedFurnace.value,
        baselineDataId,
      });
      const taskPayload = res?.data || {};
      const taskId = taskPayload.runId || resolveRunId();
      const payload = await waitEvolutionResult(taskId);
      const { solutions, evolutionProcess: process } = normalizeEvolutionPayload(payload);
      if (solutions.length < 2) {
        message.error('未获取到足够的优化方案');
        return;
      }
      if (process) {
        evolutionProcess.value = {
          maxFitness: process.maxFitness || [],
          avgFitness: process.avgFitness || [],
          bestSolutions: process.bestSolutions || [],
        };
        updateEvolutionChart();
      }
      message.success('演化方案已生成');
      if (historyDateRange.value && historyDateRange.value.length === 2) {
        historyDateRange.value = [historyDateRange.value[0], new Date()];
      }
      await fetchEvolutionHistory();
      await nextTick();
    } catch (error: any) {
      const errorMessage = resolveRequestErrorMessage(error, '演化计算失败');
      message.error(errorMessage, { duration: 5000 });
    } finally {
      evolutionLoading.value = false;
    }
  };

  const buildServiceLabel = (service: any) => {
    // 优先显示服务名称，如果有模型名称则在括号中补充
    const name = service.name || '未命名服务';
    const model = service.modelName ? ` (${service.modelName})` : '';
    return `${name}${model}`;
  };

  const fetchRunningServices = async () => {
    serviceLoading.value = true;
    try {
      const res = await optimizationApi.deployment.getRunningServices();
      const services = (res?.data || []) as Array<any>;
      targetVariableSupported.value = services.some(
        (service) => service && Object.prototype.hasOwnProperty.call(service, 'targetVariable')
      );
      const running = services.filter((service) => service.status === 'running');
      const expectedTarget =
        comparisonType.value === 'HIGH_YIELD'
          ? 'productionRate'
          : comparisonType.value === 'LOW_ENERGY'
          ? 'energyConsumption'
          : '';
      const ordered =
        targetVariableSupported.value && expectedTarget
          ? [
              ...running.filter((service) => service.targetVariable === expectedTarget),
              ...running.filter((service) => service.targetVariable !== expectedTarget),
            ]
          : running;
      serviceOptions.value = ordered.map((service) => ({
        label: buildServiceLabel(service),
        value: service.id,
      }));
      if (
        !selectedServiceId.value ||
        !serviceOptions.value.some((item) => item.value === selectedServiceId.value)
      ) {
        selectedServiceId.value = serviceOptions.value[0]?.value ?? null;
      }
      if (targetVariableSupported.value && expectedTarget) {
        const preferredCount = running.filter((service) => service.targetVariable === expectedTarget).length;
        if (running.length > 0 && preferredCount === 0) {
          const hint =
            expectedTarget === 'energyConsumption'
              ? '当前运行中服务的主模型目标不含能耗，演化计算将尝试使用其它已训练的能耗模型'
              : '当前运行中服务的主模型目标不含产量，演化计算将尝试使用其它已训练的产量模型';
          message.warning(hint);
        }
      }
    } catch (error: any) {
      message.error(resolveRequestErrorMessage(error, '获取模型服务失败'));
    } finally {
      serviceLoading.value = false;
    }
  };

  watch(
    comparisonType,
    () => {
      fetchRunningServices();
    },
    { flush: 'sync' }
  );

  const formatNumber = (value: number) => {
    return formatNumericDisplay(value, { decimals: 2 });
  };

  const formatScoreLabel = (value: any) => {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '-';
    }
    return formatNumber(Number(value));
  };

  const getModeLabel = (mode: string) => {
    const option = comparisonTypeOptions.find((opt) => opt.value === mode);
    return option ? option.label : mode;
  };

  const formatDateTime = (value: any) => {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return date.toLocaleString();
  };

  const formatDateParam = (value: any) => {
    if (!value) {
      return null;
    }
    const date =
      typeof value === 'number' ? new Date(value) : value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return null;
    }
    return date.toISOString();
  };

  const normalizeFurnaceId = (value: any) => String(value || '').trim().toUpperCase();

  const extractFurnaceIdFromScheme = (schemeName: any) => {
    const text = String(schemeName || '');
    const match = text.match(/BF-\d+/i);
    return match ? normalizeFurnaceId(match[0]) : '';
  };

  const buildSchemeOptionsFromHistory = (rows: Array<any>) => {
    const options: Array<any> = [];

    rows.forEach((row) => {
      const timeLabel = formatDateTime(row.createdAt || row.time);
      const scoreA = formatScoreLabel(row.scoreA);
      let label = row.schemeA || '未知方案';
      label = label.replace('演化方案-', '');

      options.push({
        label: `🤖 ${label} | 评分: ${scoreA}`,
        value: `history-${row.id}-0`,
        type: 'EVOLUTION',
        historyId: row.id,
        solutionIndex: 0,
      });
    });
    schemeOptions.value = options;
    schemeA.value = options[0]?.value || '';
    schemeB.value = options[1]?.value || options[0]?.value || '';
  };

  const buildSchemeOptionsForMixed = async () => {
    // Combine evolution history and production data
    const options: Array<any> = [];

    // 1. Add Evolution Schemes
    if (historyList.value.length > 0) {
      historyList.value.forEach((row) => {
        const timeLabel = formatDateTime(row.createdAt || row.time);
        const scoreA = formatScoreLabel(row.scoreA);
        let label = row.schemeA || '未知方案';
        label = label.replace('演化方案-', '');

        options.push({
          label: `🤖 ${label} | 评分: ${scoreA}`,
          value: `history-${row.id}-0`,
          type: 'EVOLUTION',
          historyId: row.id,
          solutionIndex: 0,
        });
      });
    }

    // 2. Add Real Production Data (fetch latest if not loaded)
    try {
      const res = await request({
        url: '/api/data/candidates',
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
        },
      });
      const rows = (res?.data || []) as Array<any>;
      rows.forEach((row) => {
        const timeLabel = formatDateTime(row.timestamp);
        const yieldValue = row.productionRate !== null ? formatNumber(row.productionRate) : null;
        const yieldText = yieldValue !== null ? `${yieldValue}t` : '-';
        options.push({
          label: `🔥 真实采集 | ${timeLabel} | 产量: ${yieldText}`,
          value: String(row.id),
          type: 'REAL',
          dataId: row.id,
        });
      });
    } catch (e) {
      // Ignore error
    }

    schemeOptions.value = options;
    if (options.length === 0) {
      schemeA.value = '';
      schemeB.value = '';
      return;
    }
    // Default selection
    // Find first EVOLUTION option for Scheme A if possible
    const firstEvo = options.find((o) => o.type === 'EVOLUTION');
    if (!schemeA.value || !options.some((o) => o.value === schemeA.value)) {
      schemeA.value = firstEvo ? firstEvo.value : options.length > 0 ? options[0].value : '';
    }

    // Find first REAL option for Scheme B if possible
    const firstReal = options.find((o) => o.type === 'REAL');
    if (
      !schemeB.value ||
      !options.some((o) => o.value === schemeB.value)
    ) {
      schemeB.value = firstReal
        ? firstReal.value
        : options.length > 1
        ? options[1].value
        : options[0].value;
    }
  };

  const updateEvolutionProcessFromHistory = async (historyId: number) => {
    try {
      const res = await comparisonApi.getEvolutionHistoryDetail(historyId);
      const detail = res?.data || {};
      let payload: any = {};
      if (detail.payload) {
        try {
          payload = JSON.parse(detail.payload);
        } catch (error: any) {
          payload = {};
        }
      }
      const { evolutionProcess: process } = normalizeEvolutionPayload(payload);
      if (process) {
        evolutionProcess.value = {
          maxFitness: process.maxFitness || [],
          avgFitness: process.avgFitness || [],
          bestSolutions: process.bestSolutions || [],
        };
      } else {
        evolutionProcess.value = null;
      }
      updateEvolutionChart();
    } catch (error: any) {
      evolutionProcess.value = null;
      updateEvolutionChart();
    }
  };

  const fetchEvolutionHistory = async () => {
    try {
      const range = historyDateRange.value || [];
      const params = {
        mode: comparisonType.value || undefined,
        startDate: formatDateParam(range[0]),
        endDate: formatDateParam(range[1]),
        furnaceId: selectedFurnace.value, // Filter by furnace
      };
      const res = await comparisonApi.getEvolutionHistory(params);
      const rows = (res?.data || []) as Array<any>;
      const targetFurnaceId = normalizeFurnaceId(selectedFurnace.value);
      const filteredRows = targetFurnaceId
        ? rows.filter((row) => {
            const schemeAFurnaceId = extractFurnaceIdFromScheme(row.schemeA);
            const schemeBFurnaceId = extractFurnaceIdFromScheme(row.schemeB);
            return schemeAFurnaceId === targetFurnaceId || schemeBFurnaceId === targetFurnaceId;
          })
        : rows;

      historyList.value = filteredRows.map((row) => ({
        id: row.id,
        time: formatDateTime(row.createdAt),
        createdAt: row.createdAt,
        schemeA: row.schemeA,
        schemeB: row.schemeB,
        type: row.mode,
        result: row.result,
        scoreA: row.scoreA,
        scoreB: row.scoreB,
        baselineTime: row.baselineTime || null,
      }));
      if (schemeSource.value === 'EVOLUTION') {
        buildSchemeOptionsFromHistory(historyList.value);
      }
      if (historyList.value.length > 0) {
        await updateEvolutionProcessFromHistory(historyList.value[0].id);
      } else {
        evolutionProcess.value = null;
        updateEvolutionChart();
      }
    } catch (error: any) {
      message.error(error?.message || '获取演化历史失败');
    }
  };

  const fetchCompareHistory = async () => {
    try {
      const range = historyDateRange.value || [];
      const params = {
        mode: comparisonType.value || undefined,
        startDate: formatDateParam(range[0]),
        endDate: formatDateParam(range[1]),
      };
      const res = await comparisonApi.getCompareHistory(params);
      const rows = (res?.data || []) as Array<any>;
      compareHistoryList.value = rows.map((row) => ({
        id: row.id,
        time: formatDateTime(row.createdAt),
        schemeA: row.schemeA,
        schemeB: row.schemeB,
        type: row.mode,
        result: row.result,
        scoreA: row.scoreA,
        scoreB: row.scoreB,
      }));
    } catch (error: any) {
      message.error(error?.message || '查询比较历史失败');
    }
  };

  const fetchProductionSchemes = async () => {
    try {
      const res = await request({
        url: '/api/data/candidates',
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
        },
      });
      const rows = (res?.data || []) as Array<any>;
      // buildSchemeOptionsFromProduction(rows); // Removed
      if (rows.length === 0) {
        message.warning('暂无真实工况方案，请先导入或采集生产数据');
      }
    } catch (error: any) {
      message.error(error?.message || '查询真实工况方案失败');
    }
  };

  const buildBaselineCandidates = (rows: Array<any>) => {
    baselineCandidates.value = rows;
    baselineCandidateOptions.value = rows.map((row) => {
      const timeLabel = formatDateTime(row.timestamp);
      const yieldValue =
        row.productionRate !== null && row.productionRate !== undefined
          ? formatNumber(row.productionRate)
          : null;
      const yieldText = yieldValue !== null ? `${yieldValue}t` : '-';
      return {
        label: `📌 ${timeLabel} | 产量: ${yieldText}`,
        value: row.id,
      };
    });
    if (rows.length === 0) {
      selectedBaselineId.value = null;
      if (baselineMode.value === 'MANUAL') {
        baselineSnapshot.value = null;
      }
      return;
    }
    const optionIds = new Set(rows.map((row) => row.id));
    if (!selectedBaselineId.value || !optionIds.has(selectedBaselineId.value)) {
      selectedBaselineId.value = rows[0]?.id ?? null;
    }
    if (baselineMode.value === 'MANUAL') {
      baselineSnapshot.value =
        baselineCandidates.value.find((row) => row.id === selectedBaselineId.value) || null;
    }
  };

  const fetchBaselineCandidates = async () => {
    baselineCandidateLoading.value = true;
    try {
      const res = await request({
        url: '/api/data/candidates',
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
        },
      });
      const rows = (res?.data || []) as Array<any>;
      buildBaselineCandidates(rows);
    } catch (error: any) {
      baselineCandidateOptions.value = [];
      baselineCandidates.value = [];
      selectedBaselineId.value = null;
      if (baselineMode.value === 'MANUAL') {
        baselineSnapshot.value = null;
      }
      message.error(error?.message || '查询基准数据失败');
    } finally {
      baselineCandidateLoading.value = false;
    }
  };

  const handleViewHistory = async (row: any) => {
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const res = await comparisonApi.getEvolutionHistoryDetail(row.id);
      const detail = res?.data || {};
      detailMeta.value = {
        time: formatDateTime(detail.createdAt),
        mode: getModeLabel(detail.mode),
        schemeA: getSchemeLabel(detail.schemeA),
        schemeB: getSchemeLabel(detail.schemeB),
        result: detail.result,
        scoreA: formatNumber(detail.scoreA),
        scoreB: formatNumber(detail.scoreB),
      };
      let payload: any = {};
      if (detail.payload) {
        try {
          payload = JSON.parse(detail.payload);
        } catch (error: any) {
          payload = {};
        }
      }
      const {
        solutions,
        evolutionProcess: process,
        searchFeatures,
        ranges,
        baselineGenes,
      } = normalizeEvolutionPayload(payload);
      if (process) {
        evolutionProcess.value = {
          maxFitness: process.maxFitness || [],
          avgFitness: process.avgFitness || [],
        };
        updateEvolutionChart();
      }
      detailSearchFeatures.value = searchFeatures || [];
      detailRanges.value = ranges;
      detailBaselineGenes.value = baselineGenes;
      detailSolutions.value = solutions.slice(0, 2).map((solution, index) => ({
        ...solution,
        name: index === 0 ? '智能优化方案' : '次优候选方案',
      }));
      const optimized = solutions[0] || {};
      const baselineSol = solutions[1] || {};
      const optimizedGenes: Record<string, any> = optimized.genes || {};
      const baselineGeneMap: Record<string, any> = baselineGenes || baselineSol.genes || {};
      const featureKeys = (searchFeatures && searchFeatures.length
        ? searchFeatures
        : Array.from(new Set([...Object.keys(optimizedGenes), ...Object.keys(baselineGeneMap)])).sort()) as string[];
      detailGeneRows.value = featureKeys
        .map((feature) => {
          const baseVal = baselineGeneMap?.[feature];
          const optVal = optimizedGenes?.[feature];
          const delta =
            optimized?.deltas?.[feature] ??
            (typeof baseVal === 'number' && typeof optVal === 'number' ? optVal - baseVal : null);
          const r = ranges?.[feature];
          return {
            feature,
            baseline: baseVal,
            optimized: optVal,
            delta,
            min: r?.min,
            max: r?.max,
          };
        })
        .filter((row) => row.baseline !== undefined || row.optimized !== undefined);
    } catch (error: any) {
      message.error(error?.message || '获取详情失败');
    } finally {
      detailLoading.value = false;
    }
  };

  const handleViewCompareHistory = async (row: any) => {
    compareDetailVisible.value = true;
    compareDetailLoading.value = true;
    try {
      const res = await comparisonApi.getCompareHistoryDetail(row.id);
      const detail = res?.data || {};
      compareDetailMeta.value = {
        time: formatDateTime(detail.createdAt),
        mode: getModeLabel(detail.mode),
        schemeA: detail.schemeA,
        schemeB: detail.schemeB,
        result: detail.result,
        scoreA: formatNumber(detail.scoreA),
        scoreB: formatNumber(detail.scoreB),
      };
      let payload: any = {};
      if (detail.payload) {
        try {
          payload = JSON.parse(detail.payload);
        } catch (error: any) {
          payload = {};
        }
      }
      const rows = (payload.rows || []) as Array<any>;
      compareDetailRows.value = rows.map((item: any) => ({
        ...item,
        label: item.label || getCompareParamLabel(item.param),
      }));
    } catch (error: any) {
      message.error(error?.message || '获取比较详情失败');
    } finally {
      compareDetailLoading.value = false;
    }
  };

  const handleDeleteHistory = (row: any) => {
    dialog.warning({
      title: '删除确认',
      content: '确定删除该历史记录吗？',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await comparisonApi.deleteHistory(row.id);
          message.success('删除成功');
          if (historyTab.value === 'evolution') {
            await fetchEvolutionHistory();
          } else {
            await fetchCompareHistory();
          }
        } catch (error: any) {
          message.error(error?.message || '删除失败');
        }
      },
    });
  };

  const handleBatchSelect = (key: string) => {
    if (key === 'delete') {
      handleBatchDelete();
    }
  };

  const batchOptions = [
    {
      label: '批量删除',
      key: 'delete',
      icon: () => h(NIcon, null, { default: () => h(DeleteOutlined) }),
    },
  ];

  const handleBatchDelete = () => {
    if (selectedHistoryRowKeys.value.length === 0) {
      return;
    }
    dialog.warning({
      title: '批量删除确认',
      content: `确定删除选中的 ${selectedHistoryRowKeys.value.length} 条记录吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await comparisonApi.batchDeleteHistory(selectedHistoryRowKeys.value);
          message.success('批量删除成功');
          selectedHistoryRowKeys.value = [];
          if (historyTab.value === 'evolution') {
            await fetchEvolutionHistory();
          } else {
            await fetchCompareHistory();
          }
        } catch (error: any) {
          message.error(error?.message || '批量删除失败');
        }
      },
    });
  };

  const chartRef = ref<HTMLDivElement | null>(null);
  const evolutionChartRef = ref<HTMLDivElement | null>(null);

  const applyDarkChartOption = (options: any) => {
    const patchAxis = (axis: any) => {
      if (!axis) return axis;
      const base = { ...(axis || {}) };
      base.axisLabel = { ...(base.axisLabel || {}), color: '#94a3b8' };
      base.splitLine = {
        ...(base.splitLine || {}),
        lineStyle: { ...((base.splitLine || {}).lineStyle || {}), color: '#1e293b' },
      };
      return base;
    };

    const patchLegend = (legend: any) => {
      if (!legend) return legend;
      const base = { ...(legend || {}) };
      base.textStyle = { ...(base.textStyle || {}), color: '#cbd5e1' };
      return base;
    };

    const patched = { ...(options || {}), backgroundColor: 'transparent' };
    if (Array.isArray(patched.xAxis)) patched.xAxis = patched.xAxis.map(patchAxis);
    else if (patched.xAxis) patched.xAxis = patchAxis(patched.xAxis);

    if (Array.isArray(patched.yAxis)) patched.yAxis = patched.yAxis.map(patchAxis);
    else if (patched.yAxis) patched.yAxis = patchAxis(patched.yAxis);

    if (Array.isArray(patched.legend)) patched.legend = patched.legend.map(patchLegend);
    else if (patched.legend) patched.legend = patchLegend(patched.legend);

    return patched;
  };

  const setOptions = (options: any) => {
    if (!chartRef.value) return;
    let instance = echarts.getInstanceByDom(chartRef.value);
    if (!instance) {
      instance = echarts.init(chartRef.value);
    }
    instance.resize();
    instance.setOption(applyDarkChartOption(options));
  };

  const disposeInstance = () => {
    if (chartRef.value) {
      const instance = echarts.getInstanceByDom(chartRef.value);
      if (instance) instance.dispose();
    }
  };

  const getInstance = () => {
    if (chartRef.value) {
      return echarts.getInstanceByDom(chartRef.value) || echarts.init(chartRef.value);
    }
    return null;
  };

  const resize = () => {
    const instance = getInstance();
    if (instance) instance.resize();
  };

  const setEvolutionOptions = (options: any) => {
    if (!evolutionChartRef.value) return;
    let instance = echarts.getInstanceByDom(evolutionChartRef.value);
    if (!instance) {
      instance = echarts.init(evolutionChartRef.value);
    }
    instance.resize();
    instance.setOption(applyDarkChartOption(options), true); // true = not merge, clear old
  };

  const filteredComparisonData = computed(() => {
    if (!selectedCompareParams.value || selectedCompareParams.value.length === 0) {
      return comparisonData.value;
    }
    return comparisonData.value.filter((row) => selectedCompareParams.value.includes(row.param));
  });

  const schemeAScoreText = computed(() => {
    if (schemeAScore.value === null) {
      return '暂无评分';
    }
    return `${schemeAScore.value}/100`;
  });

  const schemeBScoreText = computed(() => {
    if (schemeBScore.value === null) {
      return '暂无评分';
    }
    return `${schemeBScore.value}/100`;
  });

  const recommendedSchemeDisplay = computed(() => {
    if (!recommendedScheme.value) {
      return '暂无推荐';
    }
    if (recommendedScheme.value === '方案A') {
      return getSchemeAlias('A');
    }
    if (recommendedScheme.value === '方案B') {
      return getSchemeAlias('B');
    }
    return recommendedScheme.value;
  });
  const schemeAFitnessText = computed(() => {
    if (schemeAFitness.value === null) {
      return '-';
    }
    return `${schemeAFitness.value}/100`;
  });
  const schemeBFitnessText = computed(() => {
    if (schemeBFitness.value === null) {
      return '-';
    }
    return `${schemeBFitness.value}/100`;
  });
  const scoreTypeText = computed(() => {
    if (scoreType.value === 'USER_SCORE') {
      return '用户评分（产量/能耗/稳定性/成本）用于决策；Fitness 仅作搜索参考';
    }
    return scoreType.value || '-';
  });
  const constraintRows = computed(() => {
    return filteredComparisonData.value.map((row) => {
      const range = getDynamicRange(row.param);
      const min = Number(range.min);
      const max = Number(range.max);
      const a = Number(row.schemeA);
      const b = Number(row.schemeB);
      const hasA = !Number.isNaN(a) && row.schemeA !== null && row.schemeA !== undefined;
      const hasB = !Number.isNaN(b) && row.schemeB !== null && row.schemeB !== undefined;
      const feasibleA = hasA ? a >= min && a <= max : false;
      const feasibleB = hasB ? b >= min && b <= max : false;
      const unit = row.unit ? ` ${row.unit}` : '';
      return {
        param: row.param,
        label: row.label || getCompareParamLabel(row.param),
        rangeText: `${formatNumber(min)} ~ ${formatNumber(max)}${unit}`,
        schemeAText: hasA ? `${formatNumber(a)}${unit}` : '-',
        schemeBText: hasB ? `${formatNumber(b)}${unit}` : '-',
        feasibleA,
        feasibleB,
        feasibility: feasibleA && feasibleB ? '双方案可行' : feasibleA ? '仅方案A可行' : feasibleB ? '仅方案B可行' : '均超出约束',
      };
    });
  });
  const feasibilityReasons = computed(() => {
    if (!comparisonData.value.length) {
      return ['暂无比较结果，无法生成可行性原因。'];
    }
    const recommendA = recommendedScheme.value !== '方案B';
    const primary = recommendA ? getSchemeAlias('A') : getSchemeAlias('B');
    const secondary = recommendA ? getSchemeAlias('B') : getSchemeAlias('A');
    const favoredRows = filteredComparisonData.value
      .filter((row) => {
        if (row.better === undefined || row.better === null) {
          return false;
        }
        return recommendA ? row.better > 0 : row.better < 0;
      })
      .slice(0, 3)
      .map((row) => `${row.label || getCompareParamLabel(row.param)}更优`);
    const weakRows = filteredComparisonData.value
      .filter((row) => {
        if (row.better === undefined || row.better === null) {
          return false;
        }
        return recommendA ? row.better < 0 : row.better > 0;
      })
      .slice(0, 3)
      .map((row) => `${row.label || getCompareParamLabel(row.param)}偏弱`);
    const reasons: string[] = [];
    reasons.push(`入选方案：${primary}，主要依据为用户评分口径（产量/能耗/稳定性/成本）。`);
    reasons.push(`次优方案：${secondary}，虽在部分指标有优势，但综合得分未超过${primary}。`);
    if (favoredRows.length) {
      reasons.push(`${primary}入选原因：${favoredRows.join('、')}。`);
    }
    if (weakRows.length) {
      reasons.push(`${secondary}淘汰原因：${weakRows.join('、')}。`);
    }
    if (scoreType.value === 'USER_SCORE') {
      reasons.push('说明：演化 Fitness 用于搜索过程，用户评分用于展示与决策，二者口径已分离。');
    }
    return reasons;
  });

  const exportComparisonReport = () => {
    if (!comparisonData.value.length) {
      message.warning('暂无可导出的方案报告');
      return;
    }
    const now = new Date();
    const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;
    const lines: string[] = [];
    lines.push('# 方案对比报告');
    lines.push('');
    lines.push(`- 导出时间：${now.toLocaleString()}`);
    lines.push(`- 方案A：${getSchemeLabel(schemeA.value)}`);
    lines.push(`- 方案B：${getSchemeLabel(schemeB.value)}`);
    lines.push(`- 用户评分：A=${schemeAScoreText.value}，B=${schemeBScoreText.value}`);
    lines.push(`- 演化Fitness：A=${schemeAFitnessText.value}，B=${schemeBFitnessText.value}`);
    lines.push(`- 推荐方案：${recommendedSchemeDisplay.value}`);
    lines.push(`- 评分口径：${scoreTypeText.value}`);
    lines.push('');
    lines.push('## 分析结论');
    lines.push(analysisConclusion.value);
    lines.push('');
    lines.push('## 方案可行性原因');
    feasibilityReasons.value.forEach((item) => lines.push(`- ${item}`));
    lines.push('');
    lines.push('## 参数变化');
    lines.push('| 参数 | 方案A | 方案B | 差异 |');
    lines.push('|---|---:|---:|---:|');
    filteredComparisonData.value.forEach((row) => {
      const label = row.label || getCompareParamLabel(row.param);
      const a = formatDisplayValue(row.schemeA, row.unit);
      const b = formatDisplayValue(row.schemeB, row.unit);
      const diff = formatDisplayValue(row.difference, row.unit);
      lines.push(`| ${label} | ${a} | ${b} | ${diff} |`);
    });
    lines.push('');
    lines.push('## 约束条件可视化');
    lines.push('| 参数 | 约束范围 | 方案A | 方案B | 可行性 |');
    lines.push('|---|---|---:|---:|---|');
    constraintRows.value.forEach((row) => {
      lines.push(`| ${row.label} | ${row.rangeText} | ${row.schemeAText} | ${row.schemeBText} | ${row.feasibility} |`);
    });
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `方案对比报告_${stamp}.md`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    message.success('方案报告已导出');
  };

  const analysisConclusion = computed(() => {
    if (comparisonData.value.length === 0) {
      return analysisConclusionText.value || '暂无分析结论';
    }

    const schemeALabel = getSchemeAlias('A');
    const schemeBLabel = getSchemeAlias('B');

    const getRow = (param: string) => comparisonData.value.find((row) => row.param === param);
    const getNumberValue = (value: any) => {
      if (value === null || value === undefined || Number.isNaN(value)) {
        return null;
      }
      return Number(value);
    };

    const buildChangeSummary = (
      current: number | null,
      baseline: number | null,
      unit: string,
      increaseWord: string,
      decreaseWord: string,
      withAbs = true
    ) => {
      if (current === null || baseline === null) {
        return null;
      }
      const diff = current - baseline;
      const percent = baseline !== 0 ? (diff / baseline) * 100 : null;
      const trendWord = diff >= 0 ? increaseWord : decreaseWord;
      const arrow = diff > 0 ? '↑' : diff < 0 ? '↓' : '→';
      const percentText = percent === null ? '-' : `${Math.abs(percent).toFixed(1)}%`;
      const diffText = withAbs ? `（${arrow}${formatNumber(Math.abs(diff))}${unit}）` : '';
      return { trendWord, percentText, diff, diffText };
    };

    const productionRow = getRow('production');
    const energyRow = getRow('energy');
    const stabilityRow = getRow('stability');

    const productionChange = buildChangeSummary(
      getNumberValue(productionRow?.schemeA),
      getNumberValue(productionRow?.schemeB),
      productionRow?.unit || 't',
      '提升',
      '下降'
    );
    const energyChange = buildChangeSummary(
      getNumberValue(energyRow?.schemeA),
      getNumberValue(energyRow?.schemeB),
      energyRow?.unit || '',
      '上升',
      '下降',
      true
    );
    const stabilityCurrent = getNumberValue(stabilityRow?.schemeA);
    const stabilityBaseline = getNumberValue(stabilityRow?.schemeB);
    const stabilityDiff =
      stabilityCurrent !== null && stabilityBaseline !== null
        ? stabilityCurrent - stabilityBaseline
        : null;

    const productionText = productionChange
      ? `产量${productionChange.trendWord}${productionChange.percentText}${productionChange.diffText}`
      : '产量数据不足';
    const energyText = energyChange
      ? `能耗${energyChange.trendWord}${energyChange.percentText}${energyChange.diffText}`
      : '能耗数据不足';
    const stabilityText =
      stabilityDiff === null
        ? '稳定性数据不足'
        : `稳定性${stabilityDiff >= 0 ? '提升' : '下降'}${formatNumber(Math.abs(stabilityDiff))}分`;

    const productionDiffPercent = productionChange?.percentText
      ? parseFloat(productionChange.percentText.replace('%', ''))
      : 0;
    const productionDiffVal = productionChange?.diff || 0;
    const energyDiffPercent = energyChange?.percentText
      ? parseFloat(energyChange.percentText.replace('%', ''))
      : 0;
    const energyDiffVal = energyChange?.diff || 0;

    let recommendation = '';
    const scoreDiff = (schemeAScore.value || 0) - (schemeBScore.value || 0);

    // 基础判断
    if (productionDiffVal > 0 && energyDiffVal < 0) {
      recommendation = '双优方案：产量提升且能耗降低，强烈推荐采纳。';
    } else if (productionDiffVal > 0 && energyDiffVal > 0) {
      // 产量升，能耗升 -> 看效率比
      // 简单判断：如果产量提升幅度 > 能耗提升幅度，则效率提升
      if (productionDiffPercent > energyDiffPercent) {
        recommendation = '增产方案：产量显著提升，虽能耗略有增加，但整体效率更优。';
      } else {
        recommendation = '高产方案：产量提升，但能耗成本较高，请权衡经济效益。';
      }
    } else if (productionDiffVal < 0 && energyDiffVal < 0) {
      // 产量降，能耗降 -> 看节能效果
      // 如果能耗降低幅度 > 产量降低幅度
      if (Math.abs(energyDiffPercent) > Math.abs(productionDiffPercent)) {
        recommendation = '节能方案：能耗显著降低，虽产量略有减少，但节能效果突出。';
      } else {
        recommendation = '低耗方案：能耗降低，但产量损失较大，需谨慎选择。';
      }
    } else if (productionDiffVal < 0 && energyDiffVal > 0) {
      recommendation = '劣势方案：产量下降且能耗上升，不建议采纳。';
    } else {
      recommendation = '方案各项指标互有优劣，建议结合实际需求选择。';
    }

    // 结合综合评分修正建议
    if (scoreDiff >= 5) {
      recommendation += ' 综合评分显示方案A整体表现明显更佳。';
    } else if (scoreDiff <= -5) {
      recommendation += ` 综合评分显示${schemeBLabel}整体表现更佳。`;
    }

    // 极端情况告警
    if (productionDiffVal < 0 && productionDiffPercent > 15) {
      recommendation += ' ⚠️注意：预计产量将大幅下降。';
    }
    if (energyDiffVal > 0 && energyDiffPercent > 5) {
      recommendation += ' ⚠️注意：预计能耗将显著上升。';
    }

    const scoreBasis = scoreType.value === 'USER_SCORE' ? '当前推荐基于用户评分口径。' : '';
    return `与${schemeBLabel}相比，${schemeALabel}${productionText}，${energyText}，${stabilityText}。${recommendation}${scoreBasis}`;
  });

  const dataSourceSummary = computed(() => {
    const sourceLabel =
      schemeSource.value === 'EVOLUTION'
        ? '演化历史记录'
        : schemeSource.value === 'REAL'
        ? '真实工况数据（数据库）'
        : '混合对比（演化方案 + 真实工况）';
    const schemeSourceText = schemeOptions.value.length > 0 ? `方案来源：${sourceLabel}` : `方案来源：${sourceLabel}(暂无)`;
    if (schemeSource.value !== 'EVOLUTION') {
      return schemeSourceText;
    }
    const furnaceLabel =
      furnaceOptions.value.find((item) => item.value === selectedFurnace.value)?.label ||
      `高炉${selectedFurnace.value}`;
    const baselineTimeText = baselineSnapshot.value?.timestamp
      ? formatDateTime(baselineSnapshot.value.timestamp)
      : '';
    const baselineSource =
      baselineMode.value === 'MANUAL'
        ? baselineTimeText
          ? `基准=手动选择（${furnaceLabel}，${baselineTimeText}）`
          : '基准=手动选择（未选择）'
        : baselineTimeText
        ? `基准=所选高炉最新数据（${furnaceLabel}，${baselineTimeText}）`
        : `基准=所选高炉最新数据（${furnaceLabel}）`;
    return `${schemeSourceText}，${baselineSource}`;
  });

  const canAdopt = computed(() => {
    if (schemeSource.value === 'MIXED') {
      const option = getAdoptOption();
      return option && option.type === 'EVOLUTION';
    }
    return (
      schemeSource.value === 'EVOLUTION' &&
      comparisonData.value.length > 0 &&
      recommendedScheme.value === '方案A'
    );
  });

  const adoptAdjustments = computed(() => {
    if (!canAdopt.value) {
      return [];
    }
    const targetIsA = recommendedScheme.value !== '方案B';
    const rows = filteredComparisonData.value;
    return rows
      .map((row) => {
        const targetRaw = targetIsA ? row.schemeA : row.schemeB;
        const fallbackRaw = targetIsA ? row.schemeB : row.schemeA;
        if (
          targetRaw === null ||
          targetRaw === undefined ||
          Number.isNaN(targetRaw) ||
          fallbackRaw === null ||
          fallbackRaw === undefined ||
          Number.isNaN(fallbackRaw)
        ) {
          return null;
        }
        const targetValue = Number(targetRaw);
        const fallbackSource = Number(fallbackRaw);
        const base =
          row.baseline !== null && row.baseline !== undefined
            ? Number(row.baseline)
            : fallbackSource;
        const direction = targetValue > base ? 'up' : targetValue < base ? 'down' : 'flat';
        const directionLabel = direction === 'up' ? '[升]' : direction === 'down' ? '[降]' : '[平]';
        const tagType = direction === 'up' ? 'success' : direction === 'down' ? 'error' : 'default';
        return {
          label: row.label || getCompareParamLabel(row.param),
          from: formatNumber(base),
          to: formatNumber(targetValue),
          unit: row.unit ? ` ${row.unit}` : '',
          directionLabel,
          tagType,
        };
      })
      .filter((item) => item !== null);
  });

  const fetchBaselineData = async () => {
    if (baselineMode.value === 'MANUAL') {
      return;
    }
    if (!selectedFurnace.value) {
      baselineSnapshot.value = null;
      if (comparisonData.value.length === 0) {
        baselineTime.value = null;
      }
      return;
    }
    try {
      const res = await dataManagementApi.getLatestData({ furnaceId: selectedFurnace.value });
      baselineSnapshot.value = res?.data?.data || res?.data || null;
      if (comparisonData.value.length === 0) {
        baselineTime.value = baselineSnapshot.value?.timestamp || null;
      }
    } catch (error: any) {
      baselineSnapshot.value = null;
      if (comparisonData.value.length === 0) {
        baselineTime.value = null;
      }
    }
  };

  const syncManualBaseline = () => {
    if (baselineMode.value !== 'MANUAL') {
      return;
    }
    const match = baselineCandidates.value.find((row) => row.id === selectedBaselineId.value);
    baselineSnapshot.value = match || null;
    if (comparisonData.value.length === 0) {
      baselineTime.value = match?.timestamp || null;
    }
  };

  const getMetricRow = (param: string) => {
    return comparisonData.value.find((row) => row.param === param);
  };

  const normalizeScore = (value: number, min: number, max: number, higherBetter = true) => {
    if (Number.isNaN(value)) {
      return 0;
    }
    if (max <= min) {
      return 50;
    }
    const ratio = (value - min) / (max - min);
    const normalized = higherBetter ? ratio : 1 - ratio;
    return Math.max(0, Math.min(100, normalized * 100));
  };

  const getDynamicRange = (param: string) => {
    if (param === 'stability' || param === 'cost') {
      return { min: 0, max: 100 };
    }
    const row = getMetricRow(param);
    const values: number[] = [];
    if (row) {
      const a = Number(row.schemeA);
      const b = Number(row.schemeB);
      const baseline = Number(row.baseline);
      if (!Number.isNaN(a) && row.schemeA !== null && row.schemeA !== undefined) values.push(a);
      if (!Number.isNaN(b) && row.schemeB !== null && row.schemeB !== undefined) values.push(b);
      if (!Number.isNaN(baseline) && row.baseline !== null && row.baseline !== undefined) values.push(baseline);
    }
    const min = values.length ? Math.min(...values) : NaN;
    const max = values.length ? Math.max(...values) : NaN;
    if (Number.isFinite(min) && Number.isFinite(max) && max > min) {
      return { min, max };
    }
    const snapshot: any = baselineSnapshot.value || {};
    const fallbackValue =
      param === 'production'
        ? Number(snapshot.productionRate)
        : param === 'energy'
          ? Number(snapshot.energyConsumption)
          : Number(snapshot[param]);
    if (Number.isFinite(fallbackValue) && fallbackValue > 0) {
      const span = Math.max(1, Math.abs(fallbackValue) * 0.2);
      return { min: fallbackValue - span, max: fallbackValue + span };
    }
    return { min: 0, max: 1 };
  };

  const normalizeByRange = (
    value: number,
    range: { min: number; max: number },
    higherBetter = true
  ) => {
    return normalizeScore(value, range.min, range.max, higherBetter);
  };

  const resolveMetricRange = (row: any, fallback: { min: number; max: number }) => {
    if (!row) {
      return fallback;
    }
    const a = Number(row.schemeA);
    const b = Number(row.schemeB);
    const hasA = !Number.isNaN(a) && row.schemeA !== null && row.schemeA !== undefined;
    const hasB = !Number.isNaN(b) && row.schemeB !== null && row.schemeB !== undefined;
    if (!hasA || !hasB) {
      return fallback;
    }
    const min = Math.min(a, b);
    const max = Math.max(a, b);
    if (max <= min) {
      return fallback;
    }
    return { min, max };
  };

  const getRadarValue = (row: any, key: 'schemeA' | 'schemeB', param: string) => {
    if (!row) {
      return 0;
    }
    // Try to use pre-calculated score from backend first
    const scoreKey = key === 'schemeA' ? 'scoreA' : 'scoreB';
    const scoreValue = Number(row[scoreKey]);
    if (!Number.isNaN(scoreValue) && row[scoreKey] !== null && row[scoreKey] !== undefined) {
      return Math.max(0, Math.min(100, scoreValue));
    }

    // Fallback logic
    const rawValue = Number(row[key]);
    if (Number.isNaN(rawValue) || row[key] === null || row[key] === undefined) {
      return 0;
    }

    // For stability and cost, the raw value IS the score (0-100)
    if (param === 'stability' || param === 'cost') {
      return Math.max(0, Math.min(100, rawValue));
    }

    const range = resolveMetricRange(row, getDynamicRange(param));
    const higherBetter = param !== 'energy' && param !== 'cost';
    return normalizeByRange(rawValue, range, higherBetter);

    return 0;
  };

  const buildRadarScores = () => {
    const production = getMetricRow('production');
    const energy = getMetricRow('energy');
    const temperature = getMetricRow('temperature');
    const pressure = getMetricRow('pressure');
    const windVolume = getMetricRow('windVolume');
    const coalInjection = getMetricRow('coalInjection');
    const gasFlow = getMetricRow('gasFlow');
    const oxygenLevel = getMetricRow('oxygenLevel');
    const stability = getMetricRow('stability');
    const cost = getMetricRow('cost');
    return {
      schemeA: [
        getRadarValue(production, 'schemeA', 'production'),
        getRadarValue(energy, 'schemeA', 'energy'),
        getRadarValue(temperature, 'schemeA', 'temperature'),
        getRadarValue(windVolume, 'schemeA', 'windVolume'),
        getRadarValue(coalInjection, 'schemeA', 'coalInjection'),
        getRadarValue(stability, 'schemeA', 'stability'),
        getRadarValue(cost, 'schemeA', 'cost'),
      ],
      schemeB: [
        getRadarValue(production, 'schemeB', 'production'),
        getRadarValue(energy, 'schemeB', 'energy'),
        getRadarValue(temperature, 'schemeB', 'temperature'),
        getRadarValue(windVolume, 'schemeB', 'windVolume'),
        getRadarValue(coalInjection, 'schemeB', 'coalInjection'),
        getRadarValue(stability, 'schemeB', 'stability'),
        getRadarValue(cost, 'schemeB', 'cost'),
      ],
    };
  };

  const updateChart = () => {
    const dataSource = filteredComparisonData.value;
    if (!dataSource.length) {
      setOptions({
        radar: { indicator: [] },
        series: [],
      });
      return;
    }
    const radarIndicators = [
      { name: '产量', max: 100 },
      { name: '能耗', max: 100 },
      { name: '炉温', max: 100 },
      { name: '风量', max: 100 },
      { name: '喷煤量', max: 100 },
      { name: '稳定性', max: 100 },
      { name: '成本', max: 100 },
    ];
    const scores = buildRadarScores();
    setOptions({
      tooltip: {
        trigger: 'item',
        formatter: function (params: any) {
          // Custom tooltip to show real values instead of normalized scores if possible
          // But params.value contains normalized scores.
          // We can just show the normalized scores as is, or try to map back.
          // For now, let's just stick to default or simple formatter
          return `${params.name}<br/>
                     ${params.seriesName}<br/>
                     ${radarIndicators
                       .map((ind, idx) => `${ind.name}: ${params.value[idx].toFixed(1)}`)
                       .join('<br/>')}`;
        },
      },
      legend: {
        top: 0,
        data: ['方案A', '方案B'],
      },
      radar: {
        indicator: radarIndicators,
        splitNumber: 4,
        axisName: { color: '#64748b' },
      },
      series: [
        {
          name: '方案对比',
          type: 'radar',
          data: [
            { value: scores.schemeA, name: '方案A' },
            { value: scores.schemeB, name: '方案B' },
          ],
        },
      ],
    });
  };

  const updateEvolutionChart = () => {
    if (!evolutionChartRef.value) return;

    let instance = echarts.getInstanceByDom(evolutionChartRef.value);
    if (!instance) {
      instance = echarts.init(evolutionChartRef.value);
    }
    instance.resize();

    // Handle single parameter evolution chart
    if (evolutionParam.value !== 'fitness') {
      const bestSolutions = evolutionProcess.value?.bestSolutions || [];
      if (bestSolutions.length === 0) {
        setEvolutionOptions({
          title: {
            text: '暂无参数演化数据',
            left: 'center',
            top: 'center',
            textStyle: { color: '#999' },
          },
          xAxis: { show: false },
          yAxis: { show: false },
          series: [],
        });
        return;
      }
      const generations = bestSolutions.map((_, i) => `第${i + 1}代`);
      const data = bestSolutions.map((sol: any) => {
        const val = sol?.[evolutionParam.value] ?? sol?.genes?.[evolutionParam.value];
        return val !== undefined ? Number(val.toFixed(2)) : 0;
      });

      const paramLabel =
        evolutionParamOptions.value.find((o) => o.value === evolutionParam.value)?.label || '';

      setEvolutionOptions({
        title: { show: false },
        tooltip: {
          trigger: 'axis',
          formatter: `{b}<br/>${paramLabel}: {c}`,
        },
        legend: { show: false },
        grid: { left: 50, right: 30, top: 40, bottom: 30 },
        xAxis: {
          type: 'category',
          name: '代数',
          nameLocation: 'middle',
          nameGap: 25,
          data: generations,
        },
        yAxis: {
          type: 'value',
          name: paramLabel,
          scale: true, // Make y-axis adapt to data range
        },
        series: [
          {
            name: paramLabel,
            type: 'line',
            smooth: true,
            data: data,
            itemStyle: { color: '#f59e0b' }, // Amber color for parameters
            markPoint: {
              data: [
                { type: 'max', name: '最大值' },
                { type: 'min', name: '最小值' },
              ],
            },
          },
        ],
      });
      return;
    }

    // Default Fitness Chart Logic
    const rawMax = evolutionProcess.value?.maxFitness || [];
    const rawAvg = evolutionProcess.value?.avgFitness || [];

    // 检查数值量级，如果最大值 <= 1，则判定为小数形式，需要乘以 100
    const maxVal = Math.max(...rawMax, ...rawAvg);
    const needScale = maxVal <= 1.0;

    const maxFitness = needScale ? rawMax.map((v) => Number((v * 100).toFixed(2))) : rawMax;
    const avgFitness = needScale ? rawAvg.map((v) => Number((v * 100).toFixed(2))) : rawAvg;

    const generations = Array.from(
      { length: Math.max(maxFitness.length, avgFitness.length) },
      (_, i) => i + 1
    );
    setEvolutionOptions({
      title: { show: false },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          let result = `${params[0].axisValue}<br/>`;
          params.forEach((item: any) => {
            result += `${item.marker} ${item.seriesName}: <b>${item.value}%</b><br/>`;
          });
          return result;
        },
      },
      legend: { data: ['最高适应度', '平均适应度'], show: true },
      grid: { left: 50, right: 30, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        name: '代数',
        nameLocation: 'middle',
        nameGap: 25,
        data: generations.map((gen) => `第${gen}代`),
        show: true,
      },
      yAxis: {
        type: 'value',
        name: '适应度',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%' },
        show: true,
      },
      series: [
        {
          name: '最高适应度',
          type: 'line',
          smooth: true,
          data: maxFitness,
          itemStyle: { color: '#18a058' },
          markPoint: {
            data: [
              { type: 'max', name: '最大值' },
              { type: 'min', name: '最小值' },
            ],
          },
        },
        {
          name: '平均适应度',
          type: 'line',
          smooth: true,
          data: avgFitness,
          itemStyle: { color: '#2080f0' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(32, 128, 240, 0.2)' },
                { offset: 1, color: 'rgba(32, 128, 240, 0)' },
              ],
            },
          },
        },
      ],
    });
  };

  watch(evolutionParam, () => {
    updateEvolutionChart();
  });

  watch(
    [activeTab, historyTab],
    () => {
      if (activeTab.value === 'history' && historyTab.value === 'evolution') {
        const instance = evolutionChartRef.value
          ? echarts.getInstanceByDom(evolutionChartRef.value)
          : null;
        if (instance) {
          instance.dispose();
        }
        nextTick(() => {
          updateEvolutionChart();
          const newInstance = evolutionChartRef.value
            ? echarts.getInstanceByDom(evolutionChartRef.value)
            : null;
          if (newInstance) {
            newInstance.resize();
          }
        });
      }
      if (activeTab.value === 'history' && historyTab.value === 'execution') {
        fetchExecutionLogs();
      }
    },
    { immediate: true }
  );

  watch(activeTab, (newValue) => {
    if (newValue === 'comparison') {
      disposeInstance();
      nextTick(() => {
        setTimeout(() => {
          getInstance();
          resize();
          updateChart();
        }, 80);
      });
    }
  });

  watch([comparisonData, selectedCompareParams], () => updateChart(), { deep: true });

  watch(selectedFurnace, async () => {
    if (!selectedFurnace.value) {
      schemeOptions.value = [];
      schemeA.value = '';
      schemeB.value = '';
      baselineSnapshot.value = null;
      if (comparisonData.value.length === 0) {
        baselineTime.value = null;
      }
      return;
    }
    // Reload schemes when furnace changes
    if (schemeSource.value === 'EVOLUTION') {
      await fetchEvolutionHistory();
      buildSchemeOptionsFromHistory(historyList.value);
    } else if (schemeSource.value === 'REAL') {
      // ... (existing logic)
      const res = await request({
        url: '/api/data/candidates',
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
        },
      });
      // ... (rebuild options)
      const rows = (res?.data || []) as Array<any>;
      const options: Array<any> = [];
      rows.forEach((row) => {
        const timeLabel = formatDateTime(row.timestamp);
        const yieldValue = row.productionRate !== null ? formatNumber(row.productionRate) : null;
        const yieldText = yieldValue !== null ? `${yieldValue}t` : '-';
        options.push({
          label: `🔥 真实采集 | ${timeLabel} | 产量: ${yieldText}`,
          value: String(row.id),
          type: 'REAL',
          dataId: row.id,
        });
      });
      schemeOptions.value = options;
      schemeA.value = options.length > 0 ? options[0].value : '';
      schemeB.value =
        options.length > 1 ? options[1].value : options.length > 0 ? options[0].value : '';
    } else if (schemeSource.value === 'MIXED') {
      await fetchEvolutionHistory();
      await buildSchemeOptionsForMixed();
    }

    if (baselineMode.value === 'MANUAL') {
      await fetchBaselineCandidates();
      syncManualBaseline();
    } else {
      await fetchBaselineData();
    }
  });

  watch(baselineMode, async (mode) => {
    if (mode === 'MANUAL') {
      await fetchBaselineCandidates();
      syncManualBaseline();
      return;
    }
    selectedBaselineId.value = null;
    await fetchBaselineData();
  });

  watch(selectedBaselineId, () => {
    syncManualBaseline();
  });

  watch(schemeSource, async () => {
    schemeOptions.value = [];
    schemeA.value = '';
    schemeB.value = '';
    comparisonData.value = [];
    baselineTime.value = null;
    schemeAScore.value = null;
    schemeBScore.value = null;
    schemeAFitness.value = null;
    schemeBFitness.value = null;
    scoreType.value = 'USER_SCORE';
    recommendedScheme.value = '';
    analysisConclusionText.value = '';

    if (schemeSource.value === 'EVOLUTION') {
      if (historyList.value.length === 0) {
        // Only fetch if empty, avoid duplicate calls
        await fetchEvolutionHistory();
      } else {
        // Re-build options from existing history
        buildSchemeOptionsFromHistory(historyList.value);
      }
    } else if (schemeSource.value === 'REAL') {
      const res = await request({
        url: '/api/data/candidates',
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
        },
      });
      const rows = (res?.data || []) as Array<any>;
      const options: Array<any> = [];
      rows.forEach((row) => {
        const timeLabel = formatDateTime(row.timestamp);
        const yieldValue = row.productionRate !== null ? formatNumber(row.productionRate) : null;
        const yieldText = yieldValue !== null ? `${yieldValue}t` : '-';
        options.push({
          label: `🔥 真实采集 | ${timeLabel} | 产量: ${yieldText}`,
          value: String(row.id),
          type: 'REAL',
          dataId: row.id,
        });
      });
      schemeOptions.value = options;
      // Reset selection carefully
      schemeA.value = options.length > 0 ? options[0].value : '';
      schemeB.value =
        options.length > 1 ? options[1].value : options.length > 0 ? options[0].value : '';
    } else if (schemeSource.value === 'MIXED') {
      // For MIXED, we need both history and real data
      if (historyList.value.length === 0) {
        await fetchEvolutionHistory();
      }
      await buildSchemeOptionsForMixed();
    }
  });

  onMounted(async () => {
    await loadFurnaceOptions();
    fetchEvolutionHistory();
    fetchCompareHistory();
    if (baselineMode.value === 'MANUAL') {
      fetchBaselineCandidates().then(() => syncManualBaseline());
    } else {
      fetchBaselineData();
    }
    fetchRunningServices();
    if (schemeSource.value === 'REAL') {
      fetchProductionSchemes();
    }
    updateChart();
    updateEvolutionChart();
  });
</script>

<style lang="less" scoped>
  .scheme-comparison {
    padding: 20px;
  }

  .compare-main-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .compare-panel {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .compare-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .compare-inner-panel {
    padding: 14px;
    border-radius: 10px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .scheme-comparison :deep(.n-base-selection),
  .scheme-comparison :deep(.n-input),
  .scheme-comparison :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>

