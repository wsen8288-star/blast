<template>
  <div class="model-evaluation min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border">
    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-4">
      <n-card size="small" :bordered="false" class="eval-card">
        <n-statistic label="总评估次数" :value="evaluationHistoryList.length" />
        <n-text depth="3" class="text-xs mt-1">累计执行的模型评估任务</n-text>
      </n-card>
      <n-card size="small" :bordered="false" class="eval-card">
        <n-statistic label="最佳 R² 值" :value="(bestR2 * 100).toFixed(2)" suffix="%" />
        <n-text depth="3" class="text-xs mt-1">历史评估中的最高准确度</n-text>
      </n-card>
      <n-card size="small" :bordered="false" class="eval-card">
        <n-statistic label="评估模型数" :value="modelOptions.length" />
        <n-text depth="3" class="text-xs mt-1">当前可用的已训练模型</n-text>
      </n-card>
      <n-card size="small" :bordered="false" class="eval-card">
        <n-statistic label="平均 MAE" :value="avgMae" suffix="℃" />
        <n-text depth="3" class="text-xs mt-1">所有评估任务的平均误差</n-text>
      </n-card>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-4 mb-4">
      <n-card title="评估配置" class="xl:col-span-2 eval-card">
        <n-form label-placement="left" label-width="100">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <n-form-item label="模型选择">
              <n-select
                v-model:value="selectedModel"
                :options="modelOptions"
                placeholder="选择要评估的模型"
                @update:value="handleModelChange"
              />
            </n-form-item>
            <n-form-item label="评估数据">
              <n-select
                v-model:value="evaluationData"
                :options="evaluationDataOptions"
                placeholder="选择评估数据"
              />
            </n-form-item>
          </div>
          <div class="flex justify-end gap-2">
            <n-button round type="primary" @click="startEvaluation" :loading="evaluating">
              开始评估
            </n-button>
            <n-button round @click="resetEvaluation">重置</n-button>
          </div>
        </n-form>
      </n-card>

      <n-card title="模型简述" class="eval-card">
        <div v-if="selectedModelInfo" class="space-y-2">
          <div class="flex justify-between">
            <n-text depth="3">模型类型：</n-text>
            <n-text>{{ getModelTypeName(selectedModelInfo.modelType) }}</n-text>
          </div>
          <div class="flex justify-between">
            <n-text depth="3">训练时间：</n-text>
            <n-text>{{ formatDate(selectedModelInfo.endTime) }}</n-text>
          </div>
          <div class="flex justify-between">
            <n-text depth="3">训练 R²：</n-text>
            <n-tag type="success" size="small">{{ ((selectedModelInfo.r2Score || 0) * 100).toFixed(2) }}%</n-tag>
          </div>
        </div>
        <n-empty v-else description="请选择模型" size="small" />
      </n-card>
    </div>

    <n-card title="评估结果" class="mb-4 eval-card">
      <n-tabs type="line" animated v-model:value="activeTab">
        <n-tab-pane name="metrics" tab="核心指标">
          <div class="py-4">
            <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
              <n-card size="small" :bordered="true" class="text-center eval-sub-card">
                <n-statistic label="R² (决定系数)" :value="(r2 * 100).toFixed(2)" suffix="%" />
                <n-divider />
                <n-text depth="3" class="text-xs">值越接近 100% 表示模型拟合效果越好</n-text>
              </n-card>
              <n-card size="small" :bordered="true" class="text-center eval-sub-card">
                <n-statistic label="MSE (均方误差)" :value="mse" />
                <n-divider />
                <n-text depth="3" class="text-xs">最终评估损失（与训练过程的 Loss 区分）</n-text>
              </n-card>
              <n-card size="small" :bordered="true" class="text-center eval-sub-card">
                <n-statistic label="MAE (平均绝对误差)" :value="mae" suffix="℃" />
                <n-divider />
                <n-text depth="3" class="text-xs">预测值与真实值的平均绝对距离</n-text>
              </n-card>
              <n-card size="small" :bordered="true" class="text-center eval-sub-card">
                <n-statistic label="RMSE (均方根误差)" :value="rmse" suffix="℃" />
                <n-divider />
                <n-text depth="3" class="text-xs">衡量预测值偏离真实值的程度</n-text>
              </n-card>
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="analysis" tab="结果分析" display-directive="show">
          <div class="py-4 grid grid-cols-1 xl:grid-cols-2 gap-4">
            <n-card title="预测值 vs 实际值" size="small" class="eval-sub-card">
              <div class="h-[400px] relative">
                <div
                  v-if="evaluating"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="background-color: var(--n-color); opacity: 0.8;"
                >
                  <n-spin size="medium" />
                </div>
                <div
                  v-else-if="!hasScatterData"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="color: var(--n-text-color-3);"
                >
                  暂无数据，请先点击开始评估
                </div>
                <div ref="predVsActualRef" class="w-full h-full"></div>
              </div>
            </n-card>
            <n-card title="误差分布分析" size="small" class="eval-sub-card">
              <div class="h-[400px] relative">
                <div
                  v-if="evaluating"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="background-color: var(--n-color); opacity: 0.8;"
                >
                  <n-spin size="medium" />
                </div>
                <div
                  v-else-if="!hasScatterData"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="color: var(--n-text-color-3);"
                >
                  暂无数据，请先点击开始评估
                </div>
                <div ref="errorDistRef" class="w-full h-full"></div>
              </div>
            </n-card>
            <n-card
              title="特征重要性"
              size="small"
              class="xl:col-span-2 eval-sub-card"
            >
              <div class="h-[400px] relative">
                <div
                  v-if="evaluating"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="background-color: var(--n-color); opacity: 0.8;"
                >
                  <n-spin size="medium" />
                </div>
                <div
                  v-else-if="!hasFeatureImportance"
                  class="absolute inset-0 z-10 flex items-center justify-center transition-colors duration-300"
                  style="color: var(--n-text-color-3);"
                >
                  暂无数据，请先点击开始评估
                </div>
                <div ref="featureImpRef" class="w-full h-full"></div>
              </div>
            </n-card>
          </div>
        </n-tab-pane>

        <n-tab-pane name="history" tab="评估历史">
          <div class="py-2">
            <div class="mb-4 flex justify-between items-center">
              <n-space>
                <n-button
                  round
                  type="error"
                  ghost
                  :disabled="selectedRowKeys.length === 0"
                  @click="handleBatchDelete"
                >
                  批量删除
                </n-button>
              </n-space>
            </div>
            <n-data-table
              v-model:checked-row-keys="selectedRowKeys"
              :columns="evaluationHistoryColumns"
              :data="evaluationHistoryList"
              :pagination="pagination"
              :row-key="(row: any) => row.id"
              size="small"
            />
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <n-modal
      v-model:show="showDetailModal"
      preset="card"
      title="评估详情"
      :style="{ width: 'min(600px, 92vw)' }"
    >
      <n-descriptions bordered column="1" v-if="currentDetail">
        <n-descriptions-item label="记录编号">{{ currentDetail.id }}</n-descriptions-item>
        <n-descriptions-item label="评估模型">{{ currentDetail.modelName }}</n-descriptions-item>
        <n-descriptions-item label="数据来源">
          <n-tag type="info" size="small">{{ getDataSourceName(currentDetail.dataSource) }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="执行时间">{{ currentDetail.createdAt }}</n-descriptions-item>
        <n-descriptions-item label="R² 值(%)">
          <n-tag :type="(currentDetail.r2 || 0) > 0.9 ? 'success' : 'info'" size="small">
            {{ ((currentDetail.r2 || 0) * 100).toFixed(2) }}%
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="MAE 误差(℃)">{{ currentDetail.mae }}</n-descriptions-item>
        <n-descriptions-item label="RMSE 误差(℃)">{{ currentDetail.rmse }}</n-descriptions-item>
        <n-descriptions-item label="评估特征">
          <n-space>
            <n-tag
              v-for="f in (currentDetail.features || '').split(',')"
              :key="f"
              size="small"
              v-show="f"
              type="primary"
              ghost
            >
              {{ getFeatureName(f) }}
            </n-tag>
          </n-space>
        </n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <div class="flex justify-end">
          <n-button round @click="showDetailModal = false">关闭</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, h, onMounted, computed, watch, nextTick } from 'vue';
  import { useMessage, useDialog, NButton, NTag, NSpace, NPopconfirm } from 'naive-ui';
  import { optimizationApi } from '@/api/blast-furnace';
  import { useECharts } from '@/hooks/web/useECharts';

  const message = useMessage();
  const dialog = useDialog();
  const evaluating = ref(false);
  const activeTab = ref('metrics');

  const predVsActualRef = ref<HTMLDivElement>(null!);
  const errorDistRef = ref<HTMLDivElement>(null!);
  const featureImpRef = ref<HTMLDivElement>(null!);

  const { setOptions: setPredVsActualOptions, resize: resizePredVsActual } =
    useECharts(predVsActualRef);
  const { setOptions: setErrorDistOptions, resize: resizeErrorDist } = useECharts(errorDistRef);
  const { setOptions: setFeatureImpOptions, resize: resizeFeatureImp } = useECharts(featureImpRef);

  // 选中的行
  const selectedRowKeys = ref<Array<string | number>>([]);

  // 模型选择
  const selectedModel = ref<number | null>(null);
  const modelOptions = ref<{ label: string; value: number }[]>([]);
  const trainingHistoryList = ref<any[]>([]);
  const selectedModelInfo = computed(() =>
    trainingHistoryList.value.find((m) => m.id === selectedModel.value)
  );

  // 概览指标计算
  const bestR2 = computed(() => {
    if (evaluationHistoryList.value.length === 0) return 0;
    return Math.max(...evaluationHistoryList.value.map((h) => h.r2 || 0));
  });

  const avgMae = computed(() => {
    if (evaluationHistoryList.value.length === 0) return 0;
    const sum = evaluationHistoryList.value.reduce((acc, curr) => acc + (curr.mae || 0), 0);
    return (sum / evaluationHistoryList.value.length).toFixed(2);
  });

  // 评估数据
  const evaluationData = ref('validation_data');
  const evaluationDataOptions = [
    { label: '训练集数据', value: 'train_data' },
    { label: '验证集数据', value: 'validation_data' },
    { label: '全量数据', value: 'custom_data' },
  ];

  // 当前评估指标
  const r2 = ref(0);
  const mae = ref(0);
  const rmse = ref(0);
  const mse = ref(0);

  // 核心数据存储
  const evaluationDetails = ref<{
    trueValues: number[];
    predictedValues: number[];
    featureImportance: Record<string, number>;
  } | null>(null);

  const hasScatterData = computed(() => {
    const d = evaluationDetails.value;
    return !!(
      d &&
      Array.isArray(d.trueValues) &&
      Array.isArray(d.predictedValues) &&
      d.trueValues.length > 0
    );
  });

  const hasFeatureImportance = computed(() => {
    const d = evaluationDetails.value;
    return !!(d && d.featureImportance && Object.keys(d.featureImportance).length > 0);
  });

  // 评估历史
  const evaluationHistoryList = ref<any[]>([]);
  const showDetailModal = ref(false);
  const currentDetail = ref<any>(null);
  const pagination = ref({
    page: 1,
    pageSize: 5,
    showSizePicker: true,
    pageSizes: [5, 10, 20],
    onChange: (page: number) => {
      pagination.value.page = page;
    },
    onUpdatePageSize: (pageSize: number) => {
      pagination.value.pageSize = pageSize;
      pagination.value.page = 1;
    },
  });

  const evaluationHistoryColumns = [
    { type: 'selection' },
    {
      title: '序号',
      key: 'index',
      align: 'center',
      render: (_: any, index: number) => {
        return (pagination.value.page - 1) * pagination.value.pageSize + index + 1;
      },
    },
    {
      title: '模型名称',
      key: 'modelName',
      align: 'center',
    },
    {
      title: '评估时间',
      key: 'createdAt',
      align: 'center',
      width: 180,
    },
    {
      title: 'R²(%)',
      key: 'r2',
      align: 'center',
      render: (row: any) =>
        h(
          NTag,
          { type: (row.r2 || 0) > 0.9 ? 'success' : 'info', size: 'small' },
          { default: () => Number(((row.r2 || 0) * 100).toFixed(2)) }
        ),
    },
    {
      title: 'MAE(℃)',
      key: 'mae',
      align: 'center',
    },
    {
      title: 'RMSE(℃)',
      key: 'rmse',
      align: 'center',
    },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      width: 220,
      render: (row: any) =>
        h(
          NSpace,
          { justify: 'center' },
          {
            default: () => [
              h(
                NButton,
                {
                  size: 'small',
                  type: 'primary',
                  onClick: () => viewCharts(row),
                },
                { default: () => '查看图表' }
              ),
              h(
                NButton,
                {
                  size: 'small',
                  type: 'primary',
                  onClick: () => viewDetail(row),
                },
                { default: () => '详情' }
              ),
              h(
                NPopconfirm,
                {
                  onPositiveClick: () => handleDelete(row.id),
                },
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'small',
                        type: 'error',
                      },
                      { default: () => '删除' }
                    ),
                  default: () => '确定删除该评估记录吗？',
                }
              ),
            ],
          }
        ),
    },
  ];

  // --- 核心方法 ---

  // 统一的图表刷新函数
  const refreshAllCharts = () => {
    updateCharts();
    resizePredVsActual();
    resizeErrorDist();
    resizeFeatureImp();
  };

  const refreshChartsWhenVisible = async () => {
    await nextTick();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => resolve());
      });
    });
    refreshAllCharts();
  };

  // 监听 Tab 切换
  watch(activeTab, (val) => {
    if (val === 'analysis') {
      refreshChartsWhenVisible();
    }
  });

  const startEvaluation = async () => {
    if (!selectedModel.value) {
      message.error('请选择需要评估的模型');
      return;
    }
    evaluating.value = true;

    try {
      const response: any = await optimizationApi.validateModel({
        trainingId: selectedModel.value,
        dataSource: evaluationData.value,
      });
      if (response.code === 200) {
        const result = response.data;
        const evaluation = result?.evaluation || {};

        r2.value = evaluation.r2 ?? 0;
        mae.value = evaluation.mae ?? 0;
        rmse.value = evaluation.rmse ?? 0;

        // 更新数据
        evaluationDetails.value = {
          trueValues: Array.isArray(result?.trueValues) ? result.trueValues : [],
          predictedValues: Array.isArray(result?.predictedValues) ? result.predictedValues : [],
          featureImportance: result?.featureImportance || {},
        };
        (() => {
          const tv = evaluationDetails.value.trueValues || [];
          const pv = evaluationDetails.value.predictedValues || [];
          const n = Math.min(tv.length, pv.length);
          if (n > 0) {
            let sumSE = 0;
            for (let i = 0; i < n; i++) {
              const a = Number(tv[i]);
              const p = Number(pv[i]);
              if (!Number.isNaN(a) && !Number.isNaN(p)) {
                const e = a - p;
                sumSE += e * e;
              }
            }
            mse.value = Number((sumSE / n).toFixed(4));
          } else {
            mse.value = 0;
          }
        })();

        message.success('模型评估完成');
        await loadEvaluationHistory();

        // 关键修改：评估完成后，如果当前不在分析页，自动切过去
        if (activeTab.value !== 'analysis') {
          activeTab.value = 'analysis';
        } else {
          await refreshChartsWhenVisible();
        }
      } else {
        message.error('评估失败: ' + response.msg);
      }
    } catch (error: any) {
      message.error('评估失败: ' + (error?.message || '网络错误'));
    } finally {
      evaluating.value = false;
    }
  };

  const updateCharts = () => {
    if (!predVsActualRef.value || !errorDistRef.value || !featureImpRef.value) return;

    const details = evaluationDetails.value;
    const actuals = details?.trueValues || [];
    const predictions = details?.predictedValues || [];

    // 1. 预测值 vs 实际值
    const pairs: number[][] = [];
    let minValue = Number.POSITIVE_INFINITY;
    let maxValue = Number.NEGATIVE_INFINITY;

    const length = Math.min(actuals.length, predictions.length);
    for (let i = 0; i < length; i += 1) {
      const actual = Number(actuals[i]);
      const predicted = Number(predictions[i]);

      if (!Number.isNaN(actual) && !Number.isNaN(predicted)) {
        pairs.push([actual, predicted]);
        if (actual < minValue) minValue = actual;
        if (predicted < minValue) minValue = predicted;
        if (actual > maxValue) maxValue = actual;
        if (predicted > maxValue) maxValue = predicted;
      }
    }

    // 默认兜底
    if (!pairs.length) {
      minValue = 0;
      maxValue = 100;
    } else {
      // 给一点边距，让点不贴边
      const range = maxValue - minValue;
      minValue = Math.floor(minValue - range * 0.05);
      maxValue = Math.ceil(maxValue + range * 0.05);
    }

    setPredVsActualOptions({
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          return `实际值: ${params.data[0].toFixed(2)}<br/>预测值: ${params.data[1].toFixed(2)}`;
        },
      },
      grid: { left: '10%', right: '10%', top: '10%', bottom: '15%' },
      xAxis: {
        type: 'value',
        name: '实际值',
        nameLocation: 'middle',
        nameGap: 30,
        scale: true, 
        min: minValue,
        max: maxValue,
      },
      yAxis: {
        type: 'value',
        name: '预测值',
        nameLocation: 'middle',
        nameGap: 40,
        scale: true,
        min: minValue,
        max: maxValue,
      },
      series: [
        {
          type: 'scatter',
          data: pairs,
          symbolSize: 6,
          itemStyle: {
            color: '#5470c6',
            opacity: 0.6,
          },
          markLine: {
            symbol: 'none',
            silent: true, 
            lineStyle: { type: 'dashed', color: '#e54d42', width: 2 },
            data: [[{ coord: [minValue, minValue] }, { coord: [maxValue, maxValue] }]],
          },
        },
      ],
    });

    // 2. 误差分布
    const residuals: number[] = [];
    let minResidual = Number.POSITIVE_INFINITY;
    let maxResidual = Number.NEGATIVE_INFINITY;

    for (let i = 0; i < pairs.length; i += 1) {
      const residual = pairs[i][1] - pairs[i][0];
      residuals.push(residual);
      if (residual < minResidual) minResidual = residual;
      if (residual > maxResidual) maxResidual = residual;
    }

    if (!residuals.length) {
      minResidual = -10;
      maxResidual = 10;
    }

    const binCount = 20;
    // 防止除零错误
    const range = maxResidual - minResidual;
    const binSize = range > 0 ? range / binCount : 1;

    const bins: number[] = new Array(binCount).fill(0);
    const binLabels: string[] = [];

    // 生成标签
    for (let i = 0; i < binCount; i += 1) {
      const start = minResidual + i * binSize;
      const end = start + binSize;
      binLabels.push(`${start.toFixed(1)}~${end.toFixed(1)}`);
    }

    // 填充数据
    for (let i = 0; i < residuals.length; i += 1) {
      let index = Math.floor((residuals[i] - minResidual) / binSize);
      if (index >= binCount) index = binCount - 1;
      if (index < 0) index = 0;
      bins[index] += 1;
    }

    setErrorDistOptions({
      tooltip: { trigger: 'axis' },
      grid: { left: '10%', right: '5%', bottom: '15%' },
      xAxis: {
        type: 'category',
        data: binLabels,
        axisLabel: { rotate: 30, interval: 'auto' }, 
      },
      yAxis: {
        type: 'value',
        name: '频次',
      },
      series: [
        {
          type: 'bar',
          data: bins,
          itemStyle: { color: '#91cc75' },
        },
      ],
    });

    // 3. 特征重要性
    const importanceEntries = Object.entries(details?.featureImportance || {})
      .sort((a, b) => b[1] - a[1]) 
      .slice(0, 10); 

    const featureNames = importanceEntries.map((item) => getFeatureName(item[0])).reverse();
    const featureValues = importanceEntries.map((item) => item[1]).reverse();

    setFeatureImpOptions({
      tooltip: { trigger: 'axis' },
      grid: { left: '20%', right: '10%', top: '10%', bottom: '10%' },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: featureNames },
      series: [
        {
          type: 'bar',
          data: featureValues,
          itemStyle: { color: '#fac858' },
          label: { show: true, position: 'right', formatter: '{c}' },
        },
      ],
    });
  };

  const formatDate = (date: string | Date) => {
    if (!date) return '-';
    const d = new Date(date);
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const getModelTypeName = (type: string) => {
    const types: Record<string, string> = {
      neural_network: '神经网络',
      random_forest: '随机森林',
      gradient_boosting: '梯度提升树',
      gpr: '高斯过程回归',
    };
    return types[type] || type;
  };

  const getDataSourceName = (source: string) => {
    const sources: Record<string, string> = {
      train_data: '训练集数据',
      validation_data: '验证集数据',
      custom_data: '全量数据',
      all_data: '全量数据',
      full_data: '全量数据',
      test_data: '验证集数据',
    };
    return sources[source] || source;
  };

  const getFeatureName = (feature: string) => {
    const featureMap: Record<string, string> = {
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
      siliconContent: '铁水含硅量',
      constantSignal: '常量信号',
    };
    const key = (feature || '').trim();
    return featureMap[key] || key;
  };

  const parseNumberArray = (value: any) => {
    if (!value) return [];
    if (Array.isArray(value)) {
      return value.map((item) => Number(item)).filter((item) => !Number.isNaN(item));
    }
    try {
      const parsed = JSON.parse(value);
      if (!Array.isArray(parsed)) return [];
      return parsed.map((item) => Number(item)).filter((item) => !Number.isNaN(item));
    } catch {
      return [];
    }
  };

  const parseImportanceMap = (value: any) => {
    if (!value) return {};
    if (typeof value === 'object' && !Array.isArray(value)) {
      return value as Record<string, number>;
    }
    try {
      const parsed = JSON.parse(value);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
      return parsed as Record<string, number>;
    } catch {
      return {};
    }
  };

  const buildModelLabel = (row: any) => {
    const typeName = getModelTypeName(row.modelType);
    const timeText = row.endTime ? formatDate(row.endTime) : formatDate(row.startTime);
    return `${typeName} (${timeText})`;
  };

  const handleModelChange = () => {
    r2.value = 0;
    mse.value = 0;
    mae.value = 0;
    rmse.value = 0;
    evaluationDetails.value = null;
    updateCharts();
  };

  const loadTrainingHistory = async () => {
    const response: any = await optimizationApi.modelTraining.getTrainingHistory();
    if (response.code === 200) {
      trainingHistoryList.value = response.data || [];
      const completed = trainingHistoryList.value.filter((item) => item.status === 'completed');
      modelOptions.value = completed.map((item) => ({
        label: buildModelLabel(item),
        value: item.id,
      }));
      if (!selectedModel.value && modelOptions.value.length > 0) {
        selectedModel.value = modelOptions.value[0].value;
      }
    }
  };

  const loadEvaluationHistory = async () => {
    const response: any = await optimizationApi.getEvaluationHistory();
    if (response.code === 200) {
      const trainingMap = new Map(trainingHistoryList.value.map((item) => [item.id, item]));
      evaluationHistoryList.value = (response.data || []).map((item: any) => {
        const training = trainingMap.get(item.trainingId);
        return {
          ...item,
          modelName: training ? getModelTypeName(training.modelType) : `训练ID ${item.trainingId}`,
          createdAt: formatDate(item.createdAt),
        };
      });
    }
  };

  const viewDetail = async (row: any) => {
    const response: any = await optimizationApi.getEvaluationDetail(row.id);
    if (response.code === 200) {
      const training = trainingHistoryList.value.find(
        (item) => item.id === response.data.trainingId
      );
      currentDetail.value = {
        ...response.data,
        modelName: training ? buildModelLabel(training) : `训练ID ${response.data.trainingId}`,
        createdAt: formatDate(response.data.createdAt),
      };
      showDetailModal.value = true;
    }
  };

  const viewCharts = async (row: any) => {
    evaluating.value = true;
    try {
      const response: any = await optimizationApi.getEvaluationDetail(row.id);
      if (response.code === 200) {
        const data = response.data || {};
        const trueValues = parseNumberArray(data.trueValuesJson);
        const predictedValues = parseNumberArray(data.predictedValuesJson);
        const featureImportance = parseImportanceMap(data.featureImportanceJson);
        if (!trueValues.length || !predictedValues.length) {
          message.warning('历史记录未保存图表数据，请重新评估一次');
          return;
        }
        r2.value = data.r2 ?? 0;
        mae.value = data.mae ?? 0;
        rmse.value = data.rmse ?? 0;
        evaluationDetails.value = {
          trueValues,
          predictedValues,
          featureImportance,
        };
        if (activeTab.value !== 'analysis') {
          activeTab.value = 'analysis';
        } else {
          setTimeout(() => {
            refreshAllCharts();
          }, 100);
        }
      } else {
        message.error('获取评估详情失败: ' + response.msg);
      }
    } catch (error: any) {
      message.error('获取评估详情失败: ' + (error?.message || '网络错误'));
    } finally {
      evaluating.value = false;
      setTimeout(() => {
        if (activeTab.value === 'analysis') {
          refreshAllCharts();
        }
      }, 100);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res: any = await optimizationApi.deleteEvaluation(id);
      if (res.code === 200) {
        message.success('删除成功');
        loadEvaluationHistory();
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleBatchDelete = () => {
    if (selectedRowKeys.value.length === 0) return;
    dialog.warning({
      title: '批量删除确认',
      content: `确定要删除选中的 ${selectedRowKeys.value.length} 条评估记录吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res: any = await optimizationApi.deleteEvaluationBatch(
            selectedRowKeys.value as number[]
          );
          if (res.code === 200) {
            message.success('批量删除成功');
            selectedRowKeys.value = [];
            loadEvaluationHistory();
          }
        } catch (error) {
          message.error('批量删除失败');
        }
      },
    });
  };

  const resetEvaluation = () => {
    evaluationData.value = 'validation_data';
    r2.value = 0;
    mse.value = 0;
    mae.value = 0;
    rmse.value = 0;
    evaluationDetails.value = null;
    updateCharts();
    message.success('配置已重置');
  };

  onMounted(async () => {
    await loadTrainingHistory();
    await loadEvaluationHistory();
  });
</script>

<style scoped>
  .eval-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .eval-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .model-evaluation :deep(.n-base-selection) {
    border-radius: 10px;
  }
</style>
