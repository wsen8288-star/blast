<template>
  <div class="correlation-analysis w-full p-4 md:p-6 box-border">
    <n-card title="参数相关性分析" class="mb-4" :bordered="false">
      <n-form label-placement="left" label-width="120" class="mb-4">
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="高炉选择">
            <n-select
              v-model:value="selectedFurnace"
              :options="furnaceOptions"
              placeholder="选择高炉"
            />
          </n-form-item>
          <n-form-item label="时间范围">
            <n-date-picker v-model:value="dateRange" type="daterange" placeholder="选择时间范围" />
          </n-form-item>
          <n-form-item label="算法类型">
            <n-select v-model:value="analysisMethod" :options="analysisMethodOptions" placeholder="选择相关算法" />
          </n-form-item>
          <n-form-item label="热力图阈值">
            <div class="w-full">
              <n-slider v-model:value="heatmapThreshold" :step="0.05" :min="0" :max="1" />
              <div class="text-xs mt-1 transition-colors duration-300" style="color: var(--n-text-color-3);">仅高亮 |r| ≥ {{ heatmapThreshold.toFixed(2) }}</div>
            </div>
          </n-form-item>
          <n-form-item label="滞后窗口">
            <n-input-number v-model:value="maxLag" :min="0" :max="120" placeholder="0为关闭" class="w-full" />
          </n-form-item>
          <n-form-item label="最小重叠点数">
            <n-input-number v-model:value="minOverlap" :min="2" :max="2000" class="w-full" />
          </n-form-item>
        </div>
        <n-form-item label="分析参数">
          <n-select
            v-model:value="selectedParams"
            multiple
            :options="paramOptions"
            placeholder="选择需要分析的参数"
          />
        </n-form-item>
        <div class="flex justify-end">
          <n-button type="primary" @click="startAnalysis" :loading="loading">开始分析</n-button>
          <n-button @click="resetAnalysis" class="ml-2" :disabled="loading">重置</n-button>
        </div>
      </n-form>

      <n-tabs type="line" v-model:value="activeTab">
        <n-tab-pane name="matrix" tab="相关性矩阵">
          <div class="p-4 rounded-lg border transition-colors duration-300" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card
              title="相关性系数矩阵"
              size="small"
              class="mb-4"
              :bordered="false"
            >
              <div class="overflow-x-auto">
                <table v-if="hasMatrixData" class="correlation-matrix">
                  <thead>
                    <tr>
                      <th></th>
                      <th v-for="param in selectedParams" :key="param">{{
                        getParamLabel(param)
                      }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="rowParam in selectedParams" :key="rowParam">
                      <th>{{ getParamLabel(rowParam) }}</th>
                      <td
                        v-for="colParam in selectedParams"
                        :key="`${rowParam}-${colParam}`"
                        :style="getCorrelationStyle(correlationMatrix[rowParam]?.[colParam] || 0)"
                      >
                        {{ (correlationMatrix[rowParam]?.[colParam] || 0).toFixed(2) }}
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div v-else class="correlation-matrix-placeholder">
                  <n-skeleton animated text style="width: 100%" />
                  <n-skeleton animated text style="width: 92%" />
                  <n-skeleton animated text style="width: 88%" />
                  <n-skeleton animated text style="width: 95%" />
                  <n-skeleton animated text style="width: 85%" />
                </div>
              </div>
              <div
                v-if="!hasMatrixData"
                class="p-4 text-center transition-colors duration-300"
                style="color: var(--n-text-color-3);"
              >
                请点击"开始分析"按钮生成相关性矩阵
              </div>
            </n-card>

            <n-card title="相关性热力图" size="small" :bordered="false">
              <div class="h-[600px] relative">
                <div ref="heatmapRef" class="w-full h-full"></div>
                <template v-if="Object.keys(correlationMatrix).length === 0">
                  <div
                    class="absolute inset-0 flex items-center justify-center transition-colors duration-300"
                    style="background-color: var(--n-color); opacity: 0.85;"
                  >
                    <n-skeleton animated :width="'100%'" :height="'100%'" />
                  </div>
                </template>
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="scatter" tab="散点图分析">
          <div class="p-4 rounded-lg border transition-colors duration-300" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-form label-placement="left" label-width="120" class="mb-4">
              <div class="grid grid-cols-2 gap-4">
                <n-form-item label="X轴参数">
                  <n-select
                    v-model:value="xParam"
                    :options="selectedParams.map((p) => ({ label: getParamLabel(p), value: p }))"
                    placeholder="选择X轴参数"
                  />
                </n-form-item>
                <n-form-item label="Y轴参数">
                  <n-select
                    v-model:value="yParam"
                    :options="selectedParams.map((p) => ({ label: getParamLabel(p), value: p }))"
                    placeholder="选择Y轴参数"
                  />
                </n-form-item>
              </div>
              <div class="flex justify-end">
                <n-button type="primary" @click="updateScatterPlot">更新散点图</n-button>
              </div>
            </n-form>

            <n-card title="参数散点图" size="small">
              <div class="h-80">
                <div ref="scatterRef" class="w-full h-full"></div>
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="result" tab="分析结果">
          <div class="p-4 rounded-lg border transition-colors duration-300" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card
              title="相关性分析结果"
              size="small"
              class="mb-4 result-card"
            >
              <n-skeleton
                animated
                v-if="loading || analysisResults.length === 0"
                :rows="5"
                :columns="5"
              />
              <n-data-table v-else :columns="resultColumns" :data="analysisResults" size="small" />
            </n-card>

            <n-card title="分析报告" size="small" class="mb-4 result-card">
              <div class="p-4">
                <n-skeleton animated v-if="loading || analysisResults.length === 0" :rows="8" />
                <div v-else-if="currentReport">
                  <n-text depth="2" class="mb-4">分析报告摘要</n-text>

                  <div class="mb-6">
                    <n-text depth="3" class="mb-2">
                      本次分析共涉及
                      <n-tag type="info">{{ currentReport.summary.paramCount }} 个参数</n-tag>，
                      发现了
                      <n-tag type="success"
                        >{{ currentReport.summary.totalCorrelations }} 组关联关系</n-tag
                      >，其中：
                    </n-text>
                    <div class="mt-2 text-xs transition-colors duration-300" style="color: var(--n-text-color-3);">
                      样本量 N={{ currentReport.summary.sampleSize }} ｜ 时间范围 {{ currentReport.summary.timeRange }} ｜ 算法 {{ currentReport.summary.method }} ｜ Lag窗口 ±{{ currentReport.summary.maxLag }}
                    </div>
                    <div class="flex flex-wrap gap-2 mt-2">
                      <n-tag type="success" v-if="currentReport.summary.strongCount > 0">
                        强相关：{{ currentReport.summary.strongCount }} 组
                      </n-tag>
                      <n-tag type="warning" v-if="currentReport.summary.mediumCount > 0">
                        中等相关：{{ currentReport.summary.mediumCount }} 组
                      </n-tag>
                      <n-tag type="default" v-if="currentReport.summary.weakCount > 0">
                        弱相关：{{ currentReport.summary.weakCount }} 组
                      </n-tag>
                    </div>
                  </div>

                  <div v-if="currentReport.strongCorrelations.length > 0" class="mb-6">
                    <n-text depth="2" class="mb-2">强相关关系</n-text>
                    <ul class="list-disc pl-6 mb-4">
                      <li v-for="item in currentReport.strongCorrelations" :key="item.id">
                        {{ getParamLabel(item.param1) }} 与 {{ getParamLabel(item.param2) }} 呈现
                        <n-text :type="item.level.includes('正') ? 'success' : 'error'">
                          {{ item.level }}
                        </n-text>
                        （相关系数：{{ item.correlation }}）
                      </li>
                    </ul>
                  </div>

                  <div v-if="currentReport.mediumCorrelations.length > 0" class="mb-6">
                    <n-text depth="2" class="mb-2">中等相关关系</n-text>
                    <ul class="list-disc pl-6 mb-4">
                      <li v-for="item in currentReport.mediumCorrelations" :key="item.id">
                        {{ getParamLabel(item.param1) }} 与 {{ getParamLabel(item.param2) }} 呈现
                        <n-text :type="item.level.includes('正') ? 'info' : 'warning'">
                          {{ item.level }}
                        </n-text>
                        （相关系数：{{ item.correlation }}）
                      </li>
                    </ul>
                  </div>

                  <div v-if="currentReport.weakCorrelations.length > 0" class="mb-6">
                    <n-text depth="2" class="mb-2">弱相关关系</n-text>
                    <ul class="list-disc pl-6 mb-4">
                      <li v-for="item in currentReport.weakCorrelations" :key="item.id">
                        {{ getParamLabel(item.param1) }} 与 {{ getParamLabel(item.param2) }} 呈现
                        <n-text type="default">
                          {{ item.level }}
                        </n-text>
                        （相关系数：{{ item.correlation }}）
                      </li>
                    </ul>
                  </div>

                  <div class="p-4 rounded-lg border transition-colors duration-300" style="background-color: var(--n-color); border-color: var(--n-border-color);">
                    <n-text depth="3">
                      <strong>分析建议：</strong>
                    </n-text>
                    <ul class="list-disc pl-6 mt-2">
                      <li v-if="currentReport.strongCorrelations.length > 0">
                        重点关注强相关参数的联动变化，建立精确的控制模型
                      </li>
                      <li v-if="currentReport.mediumCorrelations.length > 0">
                        将中等相关参数作为辅助控制指标，优化生产调整策略
                      </li>
                      <li v-if="currentReport.weakCorrelations.length > 0">
                        定期观察弱相关参数的变化趋势，关注异常情况
                      </li>
                      <li> 结合生产经验，综合考虑多个相关参数，制定最优生产控制策略 </li>
                      <li>{{ currentReport.summary.note }}</li>
                    </ul>
                  </div>
                </div>
              </div>
            </n-card>

            <n-card title="相关性分析解惑" size="small" class="guide-card">
              <n-collapse default-expanded-names="['concept']" accordion>
                <n-collapse-item name="concept" title="相关性概念解释">
                  <div class="p-4">
                    <n-text depth="3" class="mb-2">
                      相关性分析是一种统计方法，用于衡量两个或多个变量之间的关联程度。在高炉生产中，相关性分析可以帮助我们：
                    </n-text>
                    <ul class="list-disc pl-6 mb-4">
                      <li>理解各生产参数之间的相互影响关系</li>
                      <li>识别关键影响因素，优化生产控制</li>
                      <li>预测参数变化趋势，提前采取措施</li>
                      <li>发现异常关联，排查生产问题</li>
                    </ul>
                    <n-text depth="3">
                      <strong>重要提示：</strong
                      >相关性不等于因果关系，相关关系仅表示变量之间存在关联，并不一定意味着一个变量导致了另一个变量的变化。
                    </n-text>
                  </div>
                </n-collapse-item>

                <n-collapse-item name="coefficient" title="相关系数含义详解">
                  <div class="p-4">
                    <n-text depth="3" class="mb-2">
                      相关系数（r）是衡量两个变量线性相关程度的统计指标，取值范围为[-1, 1]：
                    </n-text>
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                      <n-card size="small" title="正相关" bordered>
                        <div class="text-center">
                          <n-text type="success" class="text-2xl font-bold">0 &lt; r ≤ 1</n-text>
                          <p class="mt-2">两个变量同向变化</p>
                        </div>
                      </n-card>
                      <n-card size="small" title="无相关" bordered>
                        <div class="text-center">
                          <n-text type="default" class="text-2xl font-bold">r = 0</n-text>
                          <p class="mt-2">两个变量无明显线性关系</p>
                        </div>
                      </n-card>
                      <n-card size="small" title="负相关" bordered>
                        <div class="text-center">
                          <n-text type="error" class="text-2xl font-bold">-1 ≤ r &lt; 0</n-text>
                          <p class="mt-2">两个变量反向变化</p>
                        </div>
                      </n-card>
                    </div>
                  </div>
                </n-collapse-item>

                <n-collapse-item name="level" title="相关程度分级说明">
                  <div class="p-4">
                    <n-text depth="3" class="mb-2">
                      根据相关系数的绝对值大小，可以将相关程度分为以下几个等级：
                    </n-text>
                    <table class="w-full border-collapse mb-4">
                      <thead>
                        <tr style="background-color: var(--n-table-header-color);">
                          <th class="border p-2 text-left">相关系数范围</th>
                          <th class="border p-2 text-left">相关程度</th>
                          <th class="border p-2 text-left">生产意义</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td class="border p-2">|r| ≥ 0.8</td>
                          <td class="border p-2 font-bold">强相关</td>
                          <td class="border p-2">生产中的直接关联因素</td>
                        </tr>
                        <tr>
                          <td class="border p-2">0.5 ≤ |r| &lt; 0.8</td>
                          <td class="border p-2 font-bold">中等相关</td>
                          <td class="border p-2">生产中的重要影响因素</td>
                        </tr>
                        <tr>
                          <td class="border p-2">0.3 ≤ |r| &lt; 0.5</td>
                          <td class="border p-2 font-bold">弱相关</td>
                          <td class="border p-2">生产中的潜在影响因素</td>
                        </tr>
                        <tr>
                          <td class="border p-2">|r| &lt; 0.3</td>
                          <td class="border p-2 font-bold">无相关</td>
                          <td class="border p-2">生产中的独立因素</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </n-collapse-item>

                <n-collapse-item name="application" title="实际应用指导">
                  <div class="p-4">
                    <n-text depth="3" class="mb-2">
                      基于相关性分析结果，在高炉生产中可以采取以下应用策略：
                    </n-text>
                    <ul class="list-disc pl-6 mb-4">
                      <li><strong>强相关参数：</strong>重点监控，建立精确的联动控制模型</li>
                      <li><strong>中等相关参数：</strong>作为辅助控制指标，优化生产调整</li>
                      <li><strong>弱相关参数：</strong>定期观察变化趋势，关注异常情况</li>
                      <li><strong>负相关参数：</strong>注意反向调整，避免顾此失彼</li>
                    </ul>
                    <n-text depth="3">
                      建议结合生产经验，综合考虑多个相关参数，制定最优的生产控制策略。
                    </n-text>
                  </div>
                </n-collapse-item>
              </n-collapse>
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, h, onMounted, watch, nextTick, onActivated, computed } from 'vue';
  import { useMessage } from 'naive-ui';
  import { request as serviceRequest } from '@/service/request';
  import * as echarts from 'echarts';
  import { useECharts } from '@/hooks/web/useECharts';

  const message = useMessage();
  const request = async (config: any) => {
    const { data, error } = await serviceRequest<any>(config);
    if (error) throw error;
    return { data };
  };

  const activeTab = ref('matrix');

  const selectedFurnace = ref('BF-001');
  const furnaceOptions = [
    { label: '高炉1', value: 'BF-001' },
    { label: '高炉2', value: 'BF-002' },
    { label: '高炉3', value: 'BF-003' },
  ];

  const dateRange = ref<[number, number]>([new Date('2026-01-01').getTime(), Date.now()]);
  const analysisMethod = ref<'pearson' | 'spearman'>('pearson');
  const analysisMethodOptions = [
    { label: 'Pearson（线性相关）', value: 'pearson' },
    { label: 'Spearman（秩相关）', value: 'spearman' },
  ];
  const heatmapThreshold = ref(0.3);
  const maxLag = ref(0);
  const minOverlap = ref(20);

  const selectedParams = ref([
    'temperature',
    'pressure',
    'materialHeight',
    'gasFlow',
    'oxygenLevel',
    'productionRate',
  ]);
  const paramOptions = [
    { label: '温度', value: 'temperature' },
    { label: '压力', value: 'pressure' },
    { label: '风量', value: 'windVolume' },
    { label: '喷煤量', value: 'coalInjection' },
    { label: '料面高度', value: 'materialHeight' },
    { label: '煤气流量', value: 'gasFlow' },
    { label: '氧气含量', value: 'oxygenLevel' },
    { label: '生产率', value: 'productionRate' },
    { label: '能耗', value: 'energyConsumption' },
    { label: '铁水温度', value: 'hotMetalTemperature' },
    { label: '铁水含硅量', value: 'siliconContent' },
  ];

  const xParam = ref('temperature');
  const yParam = ref('pressure');

  const correlationMatrix = ref<Record<string, Record<string, number>>>({});
  const analysisMeta = ref<any>({
    method: 'pearson',
    sampleSize: 0,
    maxLag: 0,
    minOverlap: 20,
    timeRange: { start: '-', end: '-' },
    note: '相关不等于因果，建议结合工艺机理与业务经验解读。',
  });
  const lagPairs = ref<Array<any>>([]);
  const lagPairMap = computed(() => {
    const map: Record<string, any> = {};
    lagPairs.value.forEach((item) => {
      const key = pairKey(item.param1, item.param2);
      map[key] = item;
    });
    return map;
  });
  const hasMatrixData = computed(() => Object.keys(correlationMatrix.value).length > 0);
  const showHeatmapCellLabel = computed(() => selectedParams.value.length > 0);
  const heatmapCellLabelFontSize = computed(() => {
    const count = selectedParams.value.length;
    if (count <= 8) return 10;
    if (count <= 12) return 9;
    if (count <= 16) return 8;
    return 7;
  });
  const heatmapCellLabelPrecision = computed(() => {
    const count = selectedParams.value.length;
    if (count <= 12) return 2;
    if (count <= 18) return 1;
    return 0;
  });
  const loading = ref(false);
  const heatmapRef = ref<HTMLDivElement>(null!);
  const scatterRef = ref<HTMLDivElement>(null!);
  const {
    setOptions: setHeatmapOptions,
    getInstance: getHeatmapInstance,
    disposeInstance: disposeHeatmapInstance,
  } = useECharts(heatmapRef);
  const {
    setOptions: setScatterOptions,
    getInstance: getScatterInstance,
    disposeInstance: disposeScatterInstance,
  } = useECharts(scatterRef);

  const analysisResults = ref<
    Array<{
      id: number;
      param1: string;
      param2: string;
      correlation: string;
      level: string;
    }>
  >([]);

  const generateAnalysisReport = () => {
    if (analysisResults.value.length === 0) return null;

    const strongCorrelations = analysisResults.value.filter((item) => item.level.includes('强'));
    const mediumCorrelations = analysisResults.value.filter((item) => item.level.includes('中等'));
    const weakCorrelations = analysisResults.value.filter((item) => item.level.includes('弱'));

    return {
      summary: {
        paramCount: selectedParams.value.length,
        totalCorrelations: analysisResults.value.length,
        strongCount: strongCorrelations.length,
        mediumCount: mediumCorrelations.length,
        weakCount: weakCorrelations.length,
        method: String(analysisMeta.value?.method || 'pearson').toUpperCase(),
        sampleSize: Number(analysisMeta.value?.sampleSize || 0),
        maxLag: Number(analysisMeta.value?.maxLag || 0),
        timeRange: `${analysisMeta.value?.timeRange?.start || '-'} ~ ${analysisMeta.value?.timeRange?.end || '-'}`,
        note: analysisMeta.value?.note || '相关不等于因果，建议结合工艺机理与业务经验解读。',
      },
      strongCorrelations,
      mediumCorrelations,
      weakCorrelations,
    };
  };

  const currentReport = computed(() => generateAnalysisReport());

  const resultColumns = [
    { title: '序号', key: 'id' },
    {
      title: '参数1',
      key: 'param1',
      render: (row: { param1: string }) => getParamLabel(row.param1),
    },
    {
      title: '参数2',
      key: 'param2',
      render: (row: { param2: string }) => getParamLabel(row.param2),
    },
    { title: '相关系数', key: 'correlation' },
    { title: '峰值滞后', key: 'bestLag' },
    { title: '峰值相关', key: 'bestLagCorrelation' },
    {
      title: '相关程度',
      key: 'level',
      render: (row: { level: string }) =>
        h('n-tag', { type: getCorrelationLevelType(row.level) }, { default: () => row.level }),
    },
  ];

  const getParamLabel = (param: string) => {
    const option = paramOptions.find((opt) => opt.value === param);
    return option ? option.label : param;
  };

  const getCorrelationStyle = (value: number) => {
    const absValue = Math.abs(value);

    if (absValue < heatmapThreshold.value) {
      return {
        backgroundColor: 'transparent',
        color: 'var(--n-text-color-3)',
      };
    }

    const minOpacity = 0.3;
    const maxOpacity = 1.0;
    const range = 1 - heatmapThreshold.value;
    const normalized = range > 0 ? (absValue - heatmapThreshold.value) / range : 0;
    const opacity = minOpacity + (maxOpacity - minOpacity) * normalized;

    const isPositive = value >= 0;
    const r = isPositive ? 35 : 194;
    const g = isPositive ? 162 : 59;
    const b = isPositive ? 122 : 74;

    return {
      backgroundColor: `rgba(${r}, ${g}, ${b}, ${opacity})`,
      color: opacity > 0.5 ? '#FFFFFF' : '#E2E8F0',
      fontWeight: opacity > 0.6 ? '600' : '400',
    };
  };

  const pairKey = (param1: string, param2: string) => {
    return [param1, param2].sort().join('::');
  };

  const getCorrelationLevelType = (level: string) => {
    if (level.includes('强正')) return 'success';
    if (level.includes('中等正')) return 'info';
    if (level.includes('弱正')) return 'default';
    if (level.includes('强负')) return 'error';
    if (level.includes('中等负')) return 'warning';
    if (level.includes('弱负')) return 'default';
    return 'default';
  };

  const getCorrelationLevel = (correlation: number) => {
    if (correlation >= 0.8) return '强正相关';
    if (correlation >= 0.5) return '中等正相关';
    if (correlation >= 0.3) return '弱正相关';
    if (correlation <= -0.8) return '强负相关';
    if (correlation <= -0.5) return '中等负相关';
    if (correlation <= -0.3) return '弱负相关';
    return '无相关';
  };

  const startAnalysis = async () => {
    if (selectedParams.value.length < 2) {
      message.error('请至少选择两个参数进行相关性分析');
      return;
    }

    loading.value = true;
    try {
      const [startDate, endDate] = dateRange.value;
      const response = await request({
        url: `/api/correlation/matrix`,
        method: 'post',
        params: {
          furnaceId: selectedFurnace.value,
          startTime: new Date(startDate).toISOString(),
          endTime: new Date(endDate).toISOString(),
          method: analysisMethod.value,
          maxLag: maxLag.value,
          minOverlap: minOverlap.value,
        },
        data: selectedParams.value,
      });

      const payload = response.data || {};
      correlationMatrix.value = payload.matrix || payload || {};
      analysisMeta.value = payload.meta || analysisMeta.value;
      lagPairs.value = Array.isArray(payload.lagPairs) ? payload.lagPairs : [];

      generateAnalysisResults();
      updateHeatmap();

      message.success('相关性分析完成');
    } catch (error) {
      message.error('分析失败：' + (error as any).message || '未知错误');
    } finally {
      loading.value = false;
    }
  };

  const generateAnalysisResults = () => {
    const results: any[] = [];
    let id = 1;

    selectedParams.value.forEach((param1) => {
      selectedParams.value.forEach((param2) => {
        if (param1 < param2) {
          const correlation = correlationMatrix.value[param1]?.[param2] || 0;
          const lagInfo = lagPairMap.value[pairKey(param1, param2)];
          const bestLag =
            Number(analysisMeta.value?.maxLag || 0) > 0 && lagInfo
              ? Number(lagInfo.bestLag || 0)
              : 0;
          const bestLagCorrelation =
            Number(analysisMeta.value?.maxLag || 0) > 0 && lagInfo
              ? Number(lagInfo.bestCorrelation || 0)
              : correlation;
          results.push({
            id: id++,
            param1,
            param2,
            correlation: correlation.toFixed(2),
            bestLag: Number(analysisMeta.value?.maxLag || 0) > 0 ? `${bestLag}` : '-',
            bestLagCorrelation: bestLagCorrelation.toFixed(2),
            level: getCorrelationLevel(correlation),
          });
        }
      });
    });

    results.sort((a, b) => Math.abs(b.correlation) - Math.abs(a.correlation));
    analysisResults.value = results;
  };

  const resetAnalysis = () => {
    selectedFurnace.value = 'BF-001';
    dateRange.value = [new Date('2026-01-01').getTime(), Date.now()];
    selectedParams.value = [
      'temperature',
      'pressure',
      'materialHeight',
      'gasFlow',
      'oxygenLevel',
      'productionRate',
    ];
    xParam.value = 'temperature';
    yParam.value = 'pressure';
    correlationMatrix.value = {};
    lagPairs.value = [];
    maxLag.value = 0;
    minOverlap.value = 20;
    analysisResults.value = [];
    analysisMeta.value = {
      method: analysisMethod.value,
      sampleSize: 0,
      maxLag: 0,
      minOverlap: 20,
      timeRange: { start: '-', end: '-' },
      note: '相关不等于因果，建议结合工艺机理与业务经验解读。',
    };
    message.success('重置分析参数成功');
  };

  const sampleScatterData = (scatterData: number[][], maxPoints: number) => {
    if (scatterData.length <= maxPoints) {
      return scatterData;
    }
    if (maxPoints <= 1) {
      return [scatterData[0]];
    }
    const step = (scatterData.length - 1) / (maxPoints - 1);
    const sampled: number[][] = [];
    for (let i = 0; i < maxPoints; i += 1) {
      const index = Math.round(i * step);
      sampled.push(scatterData[index]);
    }
    return sampled;
  };

  const updateScatterPlot = async () => {
    if (!getScatterInstance() || !xParam.value || !yParam.value) return;

    loading.value = true;
    try {
      const [startDate, endDate] = dateRange.value;
      const response = await request({
        url: `/api/data/list`,
        method: 'get',
        params: {
          furnaceId: selectedFurnace.value,
          startDate: new Date(startDate).toISOString(),
          endDate: new Date(endDate).toISOString(),
        },
      });

      const productionData = response.data;
      const scatterData = productionData
        .filter((item: any) => item[xParam.value] !== null && item[yParam.value] !== null)
        .map((item: any) => [item[xParam.value], item[yParam.value]]);
      const data = sampleScatterData(scatterData, 2000);

      if (scatterData.length > 2000) {
        message.warning(`散点数据过多，已从 ${scatterData.length} 个点降采样至 2000 个点`);
      }

      const option: echarts.EChartsOption = {
        backgroundColor: 'transparent',
        tooltip: {
          formatter: (params: any) => {
            return `${getParamLabel(xParam.value)}: ${params.value[0].toFixed(
              2
            )}<br/>${getParamLabel(yParam.value)}: ${params.value[1].toFixed(2)}`;
          },
        },
        xAxis: {
          type: 'value' as const,
          name: getParamLabel(xParam.value),
          nameLocation: 'middle' as const,
          nameGap: 30,
          axisLabel: {
            color: '#94a3b8',
          },
          splitLine: {
            lineStyle: {
              color: '#1e293b',
            },
          },
        },
        yAxis: {
          type: 'value' as const,
          name: getParamLabel(yParam.value),
          nameLocation: 'middle' as const,
          nameGap: 50,
          axisLabel: {
            color: '#94a3b8',
          },
          splitLine: {
            lineStyle: {
              color: '#1e293b',
            },
          },
        },
        series: [
          {
            name: '散点数据',
            type: 'scatter' as const,
            data: data,
            symbolSize: 6,
            itemStyle: {
              color: '#3b82f6',
            },
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowColor: 'rgba(0, 0, 0, 0.5)',
              },
            },
          },
        ],
      };

      setScatterOptions(option, true);

      nextTick(() => {
        const instance = getScatterInstance();
        instance?.resize();
      });

      message.success('散点图更新成功');
    } catch (error) {
      message.error('获取散点图数据失败：' + (error as any).message || '未知错误');
    } finally {
      loading.value = false;
    }
  };

  const updateHeatmap = () => {
    if (activeTab.value !== 'matrix') return;

    if (!getHeatmapInstance() || selectedParams.value.length === 0) return;
    if (Object.keys(correlationMatrix.value).length === 0) return;

    const data: [number, number, number][] = [];
    const categories = selectedParams.value.map((param) => getParamLabel(param));

    selectedParams.value.forEach((rowParam, i) => {
      selectedParams.value.forEach((colParam, j) => {
        const correlation = correlationMatrix.value[rowParam]?.[colParam] || 0;
        data.push([j, i, correlation]);
      });
    });

    const option: echarts.EChartsOption = {
      backgroundColor: 'transparent',
      tooltip: {
        position: 'top',
        formatter: (params: any) => {
          const [x, y, value] = params.value;
          return `${categories[y]} × ${categories[x]}<br/>相关系数 r = ${value.toFixed(2)}`;
        },
        backgroundColor: 'transparent',
        textStyle: {
          color: '#94a3b8',
        },
      },
      grid: {
        height: '70%',
        top: '10%',
        bottom: '15%',
        containLabel: true,
      },
      dataZoom: [
        {
          type: 'inside',
          xAxisIndex: 0,
          filterMode: 'empty',
        },
        {
          type: 'inside',
          yAxisIndex: 0,
          filterMode: 'empty',
        },
      ],
      xAxis: {
        type: 'category' as const,
        data: categories,
        splitArea: {
          show: true,
        },
        axisLabel: {
          color: '#94a3b8',
          rotate: 45,
          interval: 0,
          overflow: 'truncate',
          width: 80,
        },
        splitLine: {
          lineStyle: {
            color: '#1e293b',
          },
        },
      },
      yAxis: {
        type: 'category' as const,
        data: categories,
        splitArea: {
          show: true,
        },
        axisLabel: {
          color: '#94a3b8',
        },
        splitLine: {
          lineStyle: {
            color: '#1e293b',
          },
        },
      },
      visualMap: {
        min: -1,
        max: 1,
        calculable: true,
        orient: 'horizontal' as const,
        left: 'center' as const,
        bottom: '2%',
        inRange: {
          color: ['#C23B4A', '#2B3A42', '#23A27A'],
        },
        text: ['1', '-1'],
        textStyle: {
          color: '#94a3b8',
        },
      },
      series: [
        {
          name: '相关系数',
          type: 'heatmap' as const,
          data: data,
          label: {
            show: showHeatmapCellLabel.value,
            formatter: (params: any) => {
              const val = params.value[2];
              return val.toFixed(heatmapCellLabelPrecision.value);
            },
            color: '#E8ECEF',
            fontSize: heatmapCellLabelFontSize.value,
          },
          itemStyle: {
            borderColor: 'transparent',
            borderWidth: 1,
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(0, 0, 0, 0.5)',
              borderColor: '#fff',
              borderWidth: 2,
            },
          },
        },
      ],
    };

    setHeatmapOptions(option, true);

    nextTick(() => {
      const instance = getHeatmapInstance();
      instance?.resize();
    });
  };

  watch(activeTab, (val) => {
    if (val === 'matrix') {
      nextTick(() => {
        disposeHeatmapInstance();
        if (Object.keys(correlationMatrix.value).length > 0) {
          updateHeatmap();
        }
      });
    } else if (val === 'scatter') {
      nextTick(() => {
        disposeScatterInstance();
        updateScatterPlot();
      });
    }
  });

  watch(
    correlationMatrix,
    () => {
      updateHeatmap();
    },
    { deep: true }
  );
  watch(heatmapThreshold, () => {
    updateHeatmap();
  });

  onMounted(() => {
    nextTick(() => {
      if (Object.keys(correlationMatrix.value).length > 0) {
        updateHeatmap();
      }
      if (activeTab.value === 'scatter') {
        updateScatterPlot();
      }
    });
  });

  onActivated(() => {
    nextTick(() => {
      if (activeTab.value === 'matrix' && Object.keys(correlationMatrix.value).length > 0) {
        const instance = getHeatmapInstance();
        instance?.resize();
        updateHeatmap();
      } else if (activeTab.value === 'scatter') {
        const instance = getScatterInstance();
        instance?.resize();
        updateScatterPlot();
      }
    });
  });
</script>

<style scoped>
/* 所有强行覆盖 Naive UI 的穿透样式已被清理干净 */
table.correlation-matrix {
  width: 100%;
  border-collapse: collapse;
  text-align: center;
}
table.correlation-matrix th, table.correlation-matrix td {
  padding: 8px;
  border: 1px solid var(--n-border-color);
  color: var(--n-text-color-1);
}
</style>
