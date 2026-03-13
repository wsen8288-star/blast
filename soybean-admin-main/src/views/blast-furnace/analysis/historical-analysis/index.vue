<template>
  <div
    class="historical-analysis w-full p-4 md:p-6 box-border"
  >
    <n-card title="历史数据分析" class="mb-4 border">
      <!-- 查询条件 -->
      <n-form label-placement="left" label-width="120" class="mb-4">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <n-form-item label="高炉选择">
            <n-select
              v-model:value="selectedFurnace"
              :options="furnaceOptions"
              placeholder="选择高炉"
            />
          </n-form-item>
          <n-form-item label="开始日期">
            <n-date-picker
              v-model:value="startDate"
              type="datetime"
              placeholder="选择开始日期"
            />
          </n-form-item>
          <n-form-item label="结束日期">
            <n-date-picker
              v-model:value="endDate"
              type="datetime"
              placeholder="选择结束日期"
            />
          </n-form-item>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-4 mt-2">
          <n-form-item label="参数选择">
            <n-select
              v-model:value="selectedParams"
              multiple
              :max-tag-count="3"
              :options="paramOptions"
              placeholder="选择参数"
              class="historical-param-select"
            />
          </n-form-item>
          <div class="flex justify-end items-end">
            <n-button type="primary" @click="queryData">查询数据</n-button>
            <n-button @click="resetQuery" class="ml-2">重置</n-button>
          </div>
        </div>
        <div v-if="selectedParamLabelList.length" class="selected-param-overview">
          <span class="selected-param-overview__label">已选参数</span>
          <div class="selected-param-overview__content">
            <n-tag v-for="item in selectedParamLabelList" :key="item" type="info" size="small">{{ item }}</n-tag>
          </div>
        </div>
      </n-form>

      <!-- 分析结果 -->
      <n-tabs type="line" v-model:value="activeTab" @update:value="handleTabChange">
        <n-tab-pane name="trend" tab="趋势分析">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card title="参数趋势" size="small" class="mb-4 border">
              <div style="height: 500px; min-height: 400px">
                <!-- 趋势分析图表 -->
                <n-spin :show="loading" style="width: 100%; height: 100%; min-height: 400px">
                  <div
                    style="width: 100%; height: 100%; min-height: 400px"
                    ref="chartRef"
                    id="trend-chart"
                  ></div>
                </n-spin>
              </div>
            </n-card>

            <div class="grid grid-cols-2 gap-4">
              <n-card title="温度分析" size="small" class="border">
                <div class="h-64">
                  <!-- 温度分析图表 -->
                  <div style="width: 100%; height: 100%" ref="temperatureChartRef"></div>
                </div>
              </n-card>
              <n-card title="压力分析" size="small" class="border">
                <div class="h-64">
                  <!-- 压力分析图表 -->
                  <div style="width: 100%; height: 100%" ref="pressureChartRef"></div>
                </div>
              </n-card>
            </div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="statistics" tab="统计分析">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <!-- 日期粒度选择 -->
            <div class="flex justify-end mb-4">
              <n-form-item label="数据粒度" label-placement="left" :label-width="80">
                <n-select
                  v-model:value="dateGrain"
                  :options="dateGrainOptions"
                  placeholder="选择数据粒度"
                  size="small"
                  @update:value="handleGrainChange"
                />
              </n-form-item>
            </div>
            <div class="grid grid-cols-3 gap-4 mb-4">
              <n-card size="small" class="border">
                <n-statistic label="平均温度" :value="avgTemperature" suffix="°C" />
                <n-statistic label="最高温度" :value="maxTemperature" suffix="°C" class="mt-2" />
                <n-statistic label="最低温度" :value="minTemperature" suffix="°C" class="mt-2" />
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="平均压力" :value="avgPressure" suffix="kPa" />
                <n-statistic label="最高压力" :value="maxPressure" suffix="kPa" class="mt-2" />
                <n-statistic label="最低压力" :value="minPressure" suffix="kPa" class="mt-2" />
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="平均料面高度" :value="avgMaterialLevel" suffix="m" />
                <n-statistic
                  label="最高料面高度"
                  :value="maxMaterialLevel"
                  suffix="m"
                  class="mt-2"
                />
                <n-statistic
                  label="最低料面高度"
                  :value="minMaterialLevel"
                  suffix="m"
                  class="mt-2"
                />
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="平均煤气流量" :value="avgGasComposition" suffix="%" />
                <n-statistic
                  label="最高煤气流量"
                  :value="maxGasComposition"
                  suffix="%"
                  class="mt-2"
                />
                <n-statistic
                  label="最低煤气流量"
                  :value="minGasComposition"
                  suffix="%"
                  class="mt-2"
                />
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="平均氧气含量" :value="avgOxygenLevel" suffix="%" />
                <n-statistic label="最高氧气含量" :value="maxOxygenLevel" suffix="%" class="mt-2" />
                <n-statistic label="最低氧气含量" :value="minOxygenLevel" suffix="%" class="mt-2" />
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="平均生产速率" :value="avgProductionRate" suffix="t/h" />
                <n-statistic
                  label="最高生产速率"
                  :value="maxProductionRate"
                  suffix="t/h"
                  class="mt-2"
                />
                <n-statistic
                  label="最低生产速率"
                  :value="minProductionRate"
                  suffix="t/h"
                  class="mt-2"
                />
              </n-card>
            </div>

            <n-card title="参数分布" size="small" class="border">
              <div style="height: 350px; width: 100%">
                <!-- 参数分布图表 -->
                <div ref="distributionChartRef" style="height: 100%; width: 100%"></div>
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="report" tab="报表生成">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card title="报表配置" size="small" class="mb-4 border">
              <n-form label-placement="left" label-width="120">
                <n-form-item label="报表类型">
                  <n-select
                    v-model:value="reportType"
                    :options="reportTypeOptions"
                    placeholder="选择报表类型"
                  />
                </n-form-item>
                <n-form-item label="报表格式">
                  <n-select
                    v-model:value="reportFormat"
                    :options="reportFormatOptions"
                    placeholder="选择报表格式"
                  />
                </n-form-item>
                <n-form-item label="包含参数">
                  <n-select
                    v-model:value="reportParams"
                    multiple
                    :max-tag-count="3"
                    :options="paramOptions"
                    placeholder="选择包含参数"
                    class="historical-param-select"
                  />
                </n-form-item>
                <n-form-item label="子目录" path="storagePath">
                  <n-input
                    v-model:value="storagePath"
                    placeholder="请输入子目录，如 monthly/2026-03（可留空）"
                  />
                  <template #feedback>
                    <span style="color: #94a3b8; font-size: 12px">
                      提示：报表将保存到后端统一报表根目录下的子目录，避免绝对路径权限风险
                    </span>
                  </template>
                </n-form-item>
                <n-form-item label="数据粒度">
                  <n-select
                    v-model:value="timeGrain"
                    :options="timeGrainOptions"
                    placeholder="选择数据粒度"
                  />
                </n-form-item>
                <n-form-item label="自定义文件名">
                  <n-input v-model:value="customFileName" placeholder="请输入自定义文件名前缀" />
                </n-form-item>
                <n-form-item label="覆盖同名文件">
                  <n-switch v-model:value="overwrite" />
                </n-form-item>
                <n-form-item label="包含图表">
                  <div class="flex space-x-4">
                    <n-checkbox v-model:checked="includeTrendChart" label="参数趋势图" />
                    <n-checkbox v-model:checked="includeDistributionChart" label="参数分布图" />
                  </div>
                </n-form-item>
                <n-form-item>
                  <n-button type="primary" :loading="reportGenerating" :disabled="reportGenerating" @click="generateReport">
                    生成报表
                  </n-button>
                </n-form-item>
                <n-form-item label="粒度规则说明">
                  <n-text depth="3">
                    当报表类型为日报/周报/月报且粒度选择“原始数据”时，将自动生效为“日均值(1d)”。
                  </n-text>
                </n-form-item>
                <n-form-item v-if="lastReportInfo" label="最近生成结果">
                  <div class="w-full">
                    <div class="text-sm" style="color: var(--n-text-color-3);">
                      文件：{{ lastReportInfo.fileName }} ｜ 格式：{{ lastReportInfo.reportFormat }} ｜ 生效粒度：{{ lastReportInfo.effectiveTimeGrain }}
                    </div>
                    <div class="mt-2 flex gap-2">
                      <n-button size="small" type="primary" ghost @click="handleDownload(lastReportInfo)">下载报表</n-button>
                      <n-button size="small" @click="copyStoragePath(lastReportInfo.storagePath)">复制存储路径</n-button>
                    </div>
                  </div>
                </n-form-item>
              </n-form>
            </n-card>

            <n-card title="历史报表" size="small" class="border">
              <template #header-extra>
                <n-popconfirm
                  @positive-click="handleClean"
                  positive-text="确定清理"
                  negative-text="取消"
                >
                  <template #trigger>
                    <n-button size="small" type="warning" :loading="cleanLoading">
                      清理无效记录
                    </n-button>
                  </template>
                  此操作将校验所有记录的文件状态，并删除物理文件已丢失的数据库记录，确定执行吗？
                </n-popconfirm>
              </template>
              <n-data-table :columns="reportColumns" :data="reportList" :loading="reportLoading" size="small" />
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, h, onMounted, nextTick, onBeforeUnmount } from 'vue';
  import { useMessage, NTag, NButton, NPopconfirm } from 'naive-ui';
  import { visualizationApi, dataManagementApi, systemApi } from '@/api/blast-furnace';
  import * as echarts from 'echarts';

  const message = useMessage();

  // 查询条件
  const defaultFurnaceId = ref('BF-001');
  const selectedFurnace = ref(defaultFurnaceId.value);
  const furnaceOptions = [
    { label: '高炉1', value: 'BF-001' },
    { label: '高炉2', value: 'BF-002' },
    { label: '高炉3', value: 'BF-003' },
  ];

  const startDate = ref<number>(new Date('2026-01-01').getTime());
  const endDate = ref<number>(Date.now());
  const selectedParams = ref(['temperature', 'pressure', 'materialHeight']);
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
    { label: '恒定信号', value: 'constantSignal' },
  ];
  const paramLabelMap = computed(
    () => new Map(paramOptions.map((item) => [item.value, item.label]))
  );
  const selectedParamLabelList = computed(() =>
    selectedParams.value.map((item) => paramLabelMap.value.get(item) || item)
  );

  // 统计数据
  const avgTemperature = ref(0);
  const maxTemperature = ref(0);
  const minTemperature = ref(0);
  const avgPressure = ref(0);
  const maxPressure = ref(0);
  const minPressure = ref(0);
  const avgMaterialLevel = ref(0);
  const maxMaterialLevel = ref(0);
  const minMaterialLevel = ref(0);
  const avgGasComposition = ref(0);
  const maxGasComposition = ref(0);
  const minGasComposition = ref(0);
  const avgOxygenLevel = ref(0);
  const maxOxygenLevel = ref(0);
  const minOxygenLevel = ref(0);
  const avgProductionRate = ref(0);
  const maxProductionRate = ref(0);
  const minProductionRate = ref(0);

  // 日期粒度配置
  const dateGrain = ref('day');
  const dateGrainOptions = [
    { label: '日平均值', value: 'day' },
    { label: '周平均值', value: 'week' },
    { label: '月平均值', value: 'month' },
  ];

  // 报表配置
  const reportType = ref('daily');
  const reportTypeOptions = [
    { label: '日报', value: 'daily' },
    { label: '周报', value: 'weekly' },
    { label: '月报', value: 'monthly' },
    { label: '自定义', value: 'custom' },
  ];

  // Tab状态
  const activeTab = ref('trend');

  const reportFormat = ref('excel');
  const reportFormatOptions = [
    { label: 'Excel', value: 'excel' },
    { label: 'PDF', value: 'pdf' },
    { label: 'CSV', value: 'csv' },
  ];

  const reportParams = ref(['temperature', 'pressure', 'materialHeight']);

  const storagePath = ref('');

  const timeGrain = ref('raw');
  const timeGrainOptions = [
    { label: '原始数据', value: 'raw' },
    { label: '小时均值', value: '1h' },
    { label: '日均值', value: '1d' },
  ];

  const customFileName = ref('');
  const overwrite = ref(false);
  const includeTrendChart = ref(false);
  const includeDistributionChart = ref(false);
  const reportGenerating = ref(false);

  // --- [新增] 历史报表数据逻辑 ---
  const reportList = ref([]);
  const reportLoading = ref(false);
  const lastReportInfo = ref<any | null>(null);

  // 加载历史记录
  const loadReportHistory = async () => {
    reportLoading.value = true;
    try {
      const res: any = await visualizationApi.getReportHistory();
      if (res.code === 200) {
        reportList.value = res.data;
      }
    } catch (e) {
      console.error('获取历史报表失败', e);
    } finally {
      reportLoading.value = false;
    }
  };

  const handleDelete = async (row: any) => {
    try {
      const res: any = await visualizationApi.deleteReport(row.id);
      if (res.code === 200) {
        message.success('删除成功');
        loadReportHistory(); // 刷新列表
      }
    } catch (e) {
      message.error('删除失败');
    }
  };
  const cleanLoading = ref(false);

  // [新增] 处理清理逻辑
  const handleClean = async () => {
    cleanLoading.value = true;
    try {
      const res: any = await visualizationApi.cleanReportHistory();
      if (res.code === 200) {
        message.success(res.data || '清理成功');
        // 清理完后刷新列表
        loadReportHistory();
      } else {
        message.warning(res.message || '清理未完成');
      }
    } catch (error) {
      message.error('清理请求失败');
    } finally {
      cleanLoading.value = false;
    }
  };

  const handleDownload = async (row: any) => {
    if (!row?.reportId && !row?.id) {
      message.error('无可下载报表ID');
      return;
    }
    try {
      const reportId = Number(row.reportId || row.id);
      const res: any = await visualizationApi.downloadReport(reportId);
      const payload = res?.data || {};
      const base64 = payload.contentBase64;
      const fileName = payload.fileName || `report_${reportId}.xlsx`;
      if (!base64) {
        message.error('下载数据为空');
        return;
      }
      const binary = atob(base64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      const blob = new Blob([bytes]);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
      message.success('下载成功');
    } catch (e: any) {
      message.error(`下载失败: ${e?.message || '未知错误'}`);
    }
  };

  const copyStoragePath = async (path: string) => {
    if (!path) {
      message.warning('无可复制路径');
      return;
    }
    try {
      await navigator.clipboard.writeText(path);
      message.success('路径已复制');
    } catch (e) {
      message.error('复制失败');
    }
  };

  const reportColumns = [
    { title: '报表名称', key: 'reportName', width: 200 },
    {
      title: '报表类型',
      key: 'reportType',
      width: 100,
      render: (row: any) => {
        const typeMap: any = { daily: '日报', weekly: '周报', monthly: '月报', custom: '自定义' };
        return h(
          NTag,
          { type: 'info', size: 'small' },
          { default: () => typeMap[row.reportType] || row.reportType }
        );
      },
    },
    {
      title: '文件大小',
      key: 'fileSize',
      width: 100,
      render: (row: any) => {
        if (!row.fileSize) return '-';
        const kb = row.fileSize / 1024;
        return kb > 1024 ? `${(kb / 1024).toFixed(2)} MB` : `${kb.toFixed(2)} KB`;
      },
    },
    {
      title: '创建时间',
      key: 'createTime',
      width: 180,
      render: (row: any) => {
        return row.createTime ? new Date(row.createTime).toLocaleString() : '-';
      },
    },
    {
      title: '下载',
      key: 'download',
      width: 90,
      render(row: any) {
        return h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            tertiary: true,
            onClick: () => handleDownload(row),
          },
          { default: () => '下载' }
        );
      },
    },
    { title: '创建人', key: 'creator', width: 100 },
    {
      title: '操作',
      key: 'actions',
      width: 100, // 宽度调小，因为按钮少了
      render(row: any) {
        return h(
          NPopconfirm,
          {
            onPositiveClick: () => handleDelete(row),
          },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'small',
                  type: 'error',
                  secondary: true,
                },
                { default: () => '删除' } // 只保留删除按钮
              ),
            default: () => '确定要删除该报表记录及文件吗？',
          }
        );
      },
    },
  ];
  // 数据加载状态
  const loading = ref(false);

  // 定义图表数据类型
  interface MetricData {
    metric: string;
    data: Array<{
      timestamp: string;
      value: number;
    }>;
  }

  const chartData = ref<MetricData[]>([]);

  // 图表相关
  const chartRef = ref<HTMLElement | null>(null);
  let chartInstance: echarts.ECharts | null = null;

  // 温度分析图表
  const temperatureChartRef = ref<HTMLElement | null>(null);
  let temperatureChartInstance: echarts.ECharts | null = null;

  // 压力分析图表
  const pressureChartRef = ref<HTMLElement | null>(null);
  let pressureChartInstance: echarts.ECharts | null = null;

  // 参数分布图表
  const distributionChartRef = ref<HTMLElement | null>(null);
  let distributionChartInstance: echarts.ECharts | null = null;
  const resizeObserverBindings: Array<{ element: HTMLElement; observer: ResizeObserver }> = [];
  const resizeHandlerBindings: Array<{ element: HTMLElement; handler: () => void }> = [];

  // 存储完整的数据
  const fullData = ref<any[]>([]);

  const formatXAxisTime = (dateInput: string | number | Date) => {
    const date = new Date(dateInput);
    const year = String(date.getFullYear()).slice(-2);
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hour = String(date.getHours()).padStart(2, '0');
    const minute = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hour}:${minute}`;
  };

  const bindChartResize = (element: HTMLElement, resize: () => void) => {
    if (typeof ResizeObserver !== 'undefined') {
      const existingBinding = resizeObserverBindings.find((binding) => binding.element === element);
      if (existingBinding) {
        existingBinding.observer.disconnect();
        const existingIndex = resizeObserverBindings.indexOf(existingBinding);
        if (existingIndex >= 0) {
          resizeObserverBindings.splice(existingIndex, 1);
        }
      }
      const resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(element);
      resizeObserverBindings.push({ element, observer: resizeObserver });
      return;
    }
    const existingBinding = resizeHandlerBindings.find((binding) => binding.element === element);
    if (existingBinding) {
      window.removeEventListener('resize', existingBinding.handler);
      const existingIndex = resizeHandlerBindings.indexOf(existingBinding);
      if (existingIndex >= 0) {
        resizeHandlerBindings.splice(existingIndex, 1);
      }
    }
    window.addEventListener('resize', resize);
    resizeHandlerBindings.push({ element, handler: resize });
  };

  // 方法
  const queryData = async () => {
    loading.value = true;
    try {
      // 构建查询参数
      let startDateStr = '';
      let endDateStr = '';

      try {
        if (startDate.value) {
          const startDateObj = new Date(startDate.value);
          if (!isNaN(startDateObj.getTime())) {
            startDateStr = startDateObj.toISOString();
          }
        }

        if (endDate.value) {
          const endDateObj = new Date(endDate.value);
          if (!isNaN(endDateObj.getTime())) {
            endDateStr = endDateObj.toISOString();
          }
        }
      } catch (e) {
        console.error('日期转换失败:', e);
      }

      console.log('处理后的日期参数:', {
        startDateStr,
        endDateStr,
      });

      // 获取所有选择的指标
      const selectedMetrics = selectedParams.value.length > 0 ? selectedParams.value : [];

      console.log('当前选择的指标:', selectedMetrics);

      // 如果没有选择参数，显示提示信息并清除图表数据
      if (selectedMetrics.length === 0) {
        console.log('没有选择参数，显示提示信息并清除图表数据');
        message.warning('请先选择要分析的参数');

        // 清除图表数据
        chartData.value = [];

        // 更新图表显示为空状态
        nextTick(() => {
          updateChart();
          updateTemperatureChart([]);
          updatePressureChart([]);
        });

        loading.value = false;
        return;
      }

      // 清空之前的数据，确保只显示当前选择的参数
      chartData.value = [];

      console.log('开始调用API获取数据');
      const allDataResponse: any = await dataManagementApi.getDataList({
        furnaceId: selectedFurnace.value,
        startDate: startDateStr,
        endDate: endDateStr,
      });

      console.log('API调用成功');
      console.log('所有数据响应:', allDataResponse);

      // 处理API返回的数据结构
      console.log('所有数据响应格式:', allDataResponse?.data);

      const responseData = allDataResponse?.data;

      // 增强trendData提取（防御性编程），处理多个指标
      const allData = Array.isArray(responseData)
        ? responseData
        : Array.isArray(responseData?.data)
          ? responseData.data
          : Array.isArray(allDataResponse)
            ? allDataResponse
            : [];

      const multiTrendData: MetricData[] = selectedMetrics.map((metric) => {
        const metricData = allData
          .map((item: any) => {
            const timestampValue = item?.timestamp;
            const rawValue = item?.[metric];
            const numericValue = Number(rawValue);
            if (!timestampValue || rawValue === null || rawValue === undefined || Number.isNaN(numericValue)) {
              return null;
            }
            const timestamp =
              typeof timestampValue === 'string'
                ? timestampValue
                : new Date(timestampValue).toISOString();
            return {
              timestamp,
              value: numericValue,
            };
          })
          .filter((item: { timestamp: string; value: number } | null): item is { timestamp: string; value: number } => item !== null);

        console.log(`最终提取的 ${metric} 数据:`, metricData);
        return {
          metric,
          data: metricData,
        };
      });

      // 存储完整数据到状态变量，用于标签页切换时更新图表
      fullData.value = allData;

      console.log('处理后的数据:', {
        multiTrendData: multiTrendData.length,
        allData: allData.length,
        fullData: fullData.value.length,
      });

      chartData.value = multiTrendData;

      // 简单测试：直接在控制台输出数据
      console.log('图表数据:', chartData.value);

      // 优化图表更新的调用时机：添加小延时确保DOM布局完成
      nextTick(() => {
        console.log('等待DOM布局完成后更新图表');
        // 加一个小延时，等待CSS布局完成
        setTimeout(() => {
          console.log('更新图表数据');
          updateChart();

          // 更新温度分析和压力分析图表
          updateTemperatureChart(allData);
          updatePressureChart(allData);
          updateDistributionChart(allData);

          // 强制调整图表尺寸
          setTimeout(() => {
            console.log('强制调整图表尺寸');
            chartInstance?.resize();
            temperatureChartInstance?.resize();
            pressureChartInstance?.resize();
            distributionChartInstance?.resize();
          }, 50);
        }, 50);
      });

      // 更新统计数据
      updateStatsCards(allData, dateGrain.value);

      message.success('数据查询成功');
    } catch (error: any) {
      console.error('数据查询失败:', error);
      console.error('错误详情:', error.response?.data || error.message);
      message.error(
        '数据查询失败，请稍后重试: ' + (error.response?.data?.message || error.message)
      );
    } finally {
      loading.value = false;
    }
  };

  const resetQuery = () => {
    selectedFurnace.value = defaultFurnaceId.value;
    startDate.value = new Date('2026-01-01').getTime();
    endDate.value = Date.now();
    selectedParams.value = ['temperature', 'pressure', 'materialHeight'];
    message.success('重置查询条件成功');
  };

  const loadSystemDefaults = async () => {
    try {
      const res: any = await systemApi.config.getListByGroup('SYSTEM_CONFIG');
      const items: any[] = Array.isArray(res?.data) ? res.data : [];
      const configMap = new Map(items.map((item: any) => [item?.configKey, item?.configValue]));
      const furnace = String(configMap.get('system_default_furnace_id') ?? '').trim();
      if (furnace) {
        defaultFurnaceId.value = furnace;
        selectedFurnace.value = furnace;
      }
    } catch (_) {
      return;
    }
  };

  const generateReport = async () => {
    if (reportGenerating.value) return;
    let loadingMessage: { destroy: () => void } | null = null;
    reportGenerating.value = true;
    try {
      loadingMessage = message.loading('报表生成中，请稍后...', { duration: 0 });

      // --- 核心修复：日期格式化 ---
      const formatDate = (date: Date | number | string) => {
        if (!date) return null;
        const d = new Date(date);
        const pad = (n: number) => n.toString().padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(
          d.getHours()
        )}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
      };

      const startDateStr = startDate.value ? formatDate(startDate.value) : '';
      const endDateStr = endDate.value ? formatDate(endDate.value) : '';
      // ---------------------------

      console.log('发送报表请求:', {
        furnaceId: selectedFurnace.value,
        startDate: startDateStr,
        endDate: endDateStr,
        reportType: reportType.value,
        format: reportFormat.value,
        metrics: reportParams.value,
        storagePath: storagePath.value,
        timeGrain: timeGrain.value,
        customFileName: customFileName.value,
        overwrite: overwrite.value,
        includeTrendChart: includeTrendChart.value,
        includeDistributionChart: includeDistributionChart.value,
      });

      console.log('发送参数 includeTrendChart:', includeTrendChart.value);
      const response: any = await visualizationApi.generateReport({
        furnaceId: selectedFurnace.value,
        startDate: startDateStr,
        endDate: endDateStr,
        reportType: reportType.value,
        format: reportFormat.value,
        metrics: reportParams.value,
        storagePath: storagePath.value,
        timeGrain: timeGrain.value,
        customFileName: customFileName.value,
        overwrite: overwrite.value,
        includeTrendChart: includeTrendChart.value,
        includeDistributionChart: includeDistributionChart.value,
      });

      // 兼容不同的后端返回结构
      if (response && (response.code === 200 || response.success === true)) {
        const data = response.data || {};
        lastReportInfo.value = {
          reportId: data.reportId,
          fileName: data.fileName,
          reportFormat: data.reportFormat,
          effectiveTimeGrain: data.effectiveTimeGrain || timeGrain.value,
          storagePath: data.storagePath,
        };
        message.success(`报表生成成功: ${data.fileName || '未命名文件'}`);
        console.log('开始刷新历史列表...');
        await loadReportHistory();
      } else {
        message.error('报表生成失败: ' + (response?.message || '未知错误'));
      }
    } catch (error: any) {
      console.error('报表生成请求异常:', error);
      message.error('请求失败: ' + (error.response?.data?.message || error.message));
    } finally {
      loadingMessage?.destroy();
      reportGenerating.value = false;
    }
  };

  // 初始化主图表
  const initChart = () => {
    console.log('初始化图表');
    console.log('chartRef.value:', chartRef.value);

    try {
      let chartElement: HTMLElement | null = null;

      if (chartRef.value) {
        console.log('通过chartRef初始化图表');
        chartElement = chartRef.value;
      } else {
        // 备用方案：通过id获取元素
        console.log('通过id获取图表元素');
        chartElement = document.getElementById('trend-chart');
      }

      if (chartElement) {
        // 确保容器有尺寸
        const rect = chartElement.getBoundingClientRect();
        console.log('容器尺寸:', rect.width, 'x', rect.height);

        // 销毁已存在的图表实例
        if (chartInstance) {
          chartInstance.dispose();
        }

        // 关键修复：等待DOM完全渲染
        setTimeout(() => {
          chartInstance = echarts.init(chartElement);
          console.log('图表初始化成功');

          // 关键修复：监听容器大小变化，自动调整图表
          bindChartResize(chartElement, () => {
            console.log('容器大小变化，调整图表尺寸');
            chartInstance?.resize();
          });

          // 初始化空图表，等待真实数据
          chartInstance.setOption({
            backgroundColor: 'transparent',
            xAxis: {
              type: 'category',
              data: [],
              axisLabel: {
                color: '#94a3b8',
              },
            },
            yAxis: {
              type: 'value',
              axisLabel: {
                color: '#94a3b8',
              },
              splitLine: {
                lineStyle: {
                  color: '#1e293b',
                },
              },
            },
            series: [],
          });

          // 立即调整图表尺寸
          chartInstance.resize();
          console.log('图表尺寸调整完成');
        }, 50);
      } else {
        console.error('图表元素未找到');
      }
    } catch (e) {
      console.error('图表初始化失败:', e);
    }
  };

  // 初始化温度分析图表
  const initTemperatureChart = () => {
    console.log('初始化温度分析图表');
    console.log('temperatureChartRef.value:', temperatureChartRef.value);

    try {
      if (temperatureChartRef.value) {
        // 销毁已存在的图表实例
        if (temperatureChartInstance) {
          temperatureChartInstance.dispose();
        }

        temperatureChartInstance = echarts.init(temperatureChartRef.value);
        console.log('温度分析图表初始化成功');

        // 监听容器大小变化
        bindChartResize(temperatureChartRef.value, () => {
          temperatureChartInstance?.resize();
        });

        // 初始化空图表
        temperatureChartInstance.setOption({
          backgroundColor: 'transparent',
          xAxis: {
            type: 'category',
            data: [],
            axisLabel: {
              color: '#94a3b8',
            },
          },
          yAxis: {
            type: 'value',
            name: '温度 (°C)',
            axisLabel: {
              color: '#94a3b8',
            },
            splitLine: {
              lineStyle: {
                color: '#1e293b',
              },
            },
          },
          series: [],
        });

        temperatureChartInstance.resize();
      }
    } catch (e) {
      console.error('温度分析图表初始化失败:', e);
    }
  };

  // 初始化压力分析图表
  const initPressureChart = () => {
    console.log('初始化压力分析图表');
    console.log('pressureChartRef.value:', pressureChartRef.value);

    try {
      if (pressureChartRef.value) {
        // 销毁已存在的图表实例
        if (pressureChartInstance) {
          pressureChartInstance.dispose();
        }

        pressureChartInstance = echarts.init(pressureChartRef.value);
        console.log('压力分析图表初始化成功');

        // 监听容器大小变化
        bindChartResize(pressureChartRef.value, () => {
          pressureChartInstance?.resize();
        });

        // 初始化空图表
        pressureChartInstance.setOption({
          backgroundColor: 'transparent',
          xAxis: {
            type: 'category',
            data: [],
            axisLabel: {
              color: '#94a3b8',
            },
          },
          yAxis: {
            type: 'value',
            name: '压力 (kPa)',
            axisLabel: {
              color: '#94a3b8',
            },
            splitLine: {
              lineStyle: {
                color: '#1e293b',
              },
            },
          },
          series: [],
        });

        pressureChartInstance.resize();
      }
    } catch (e) {
      console.error('压力分析图表初始化失败:', e);
    }
  };

  // 更新温度分析图表
  const updateTemperatureChart = (data: any[]) => {
    console.log('更新温度分析图表');
    console.log('temperatureChartInstance:', temperatureChartInstance);
    console.log('数据:', data);

    if (!temperatureChartInstance) {
      initTemperatureChart();
      if (!temperatureChartInstance) {
        return;
      }
    }

    // 过滤温度数据
    const temperatureData = data.filter(
      (item) => item.temperature !== null && item.temperature !== undefined
    );

    // 排序数据
    const sortedData = temperatureData.sort((a, b) => {
      const timeA = new Date(a.timestamp).getTime();
      const timeB = new Date(b.timestamp).getTime();
      return timeA - timeB;
    });

    // 生成图表数据
    // 直接使用排序后的数据，保持时间顺序，确保所有时间字符串格式一致
    const chartData = sortedData.map((item) => {
      const date = new Date(item.timestamp);
      // 优化：生成格式为 MM-DD HH:MM 的时间字符串，去掉秒
      const timeStr = formatXAxisTime(date);
      return {
        timeStr,
        value: item.temperature || 0,
      };
    });

    // 直接使用排序后的数据，保持时间顺序
    const xAxisData = chartData.map((item) => item.timeStr);
    const seriesData = chartData.map((item) => item.value);

    // 设置图表选项
    temperatureChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: function (params: any) {
          return `${params[0].axisValue}<br/>温度: ${params[0].value} °C`;
        },
      },
      dataZoom: [
        {
          type: 'inside',
          start: 0,
          end: 100,
        },
        {
          type: 'slider',
          bottom: 0,
          start: 0,
          end: 100,
        },
      ],
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLabel: {
          color: '#94a3b8',
          rotate: 45,
          interval: 'auto',
          formatter: function (value: string) {
            return value;
          },
        },
        axisTick: {
          interval: 'auto',
        },
      },
      yAxis: {
        type: 'value',
        name: '温度 (°C)',
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
          data: seriesData,
          type: 'line',
          smooth: true,
          lineStyle: {
            color: '#1890ff',
            width: 2,
          },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              {
                offset: 0,
                color: 'rgba(24, 144, 255, 0.3)',
              },
              {
                offset: 1,
                color: 'rgba(24, 144, 255, 0.1)',
              },
            ]),
          },
        },
      ],
    });
  };

  // 更新压力分析图表
  const updatePressureChart = (data: any[]) => {
    console.log('更新压力分析图表');
    console.log('pressureChartInstance:', pressureChartInstance);
    console.log('数据:', data);

    if (!pressureChartInstance) {
      initPressureChart();
      if (!pressureChartInstance) {
        return;
      }
    }

    // 过滤压力数据
    const pressureData = data.filter(
      (item) => item.pressure !== null && item.pressure !== undefined
    );

    // 排序数据
    const sortedData = pressureData.sort((a, b) => {
      const timeA = new Date(a.timestamp).getTime();
      const timeB = new Date(b.timestamp).getTime();
      return timeA - timeB;
    });

    // 生成图表数据
    // 直接使用排序后的数据，保持时间顺序，确保所有时间字符串格式一致
    const chartData = sortedData.map((item) => {
      const date = new Date(item.timestamp);
      // 修复：包含日期信息的完整时间字符串，格式为 MM-DD HH:MM:SS
      const timeStr = formatXAxisTime(date);
      return {
        timeStr,
        value: item.pressure || 0,
      };
    });

    // 直接使用排序后的数据，保持时间顺序
    const xAxisData = chartData.map((item) => item.timeStr);
    const seriesData = chartData.map((item) => item.value);

    // 设置图表选项
    pressureChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: function (params: any) {
          return `${params[0].axisValue}<br/>压力: ${params[0].value} kPa`;
        },
      },
      dataZoom: [
        {
          type: 'inside',
          start: 0,
          end: 100,
        },
        {
          type: 'slider',
          bottom: 0,
          start: 0,
          end: 100,
        },
      ],
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLabel: {
          color: '#94a3b8',
          rotate: 45,
          interval: 'auto',
          formatter: function (value: string) {
            return value;
          },
        },
        axisTick: {
          interval: 'auto',
        },
      },
      yAxis: {
        type: 'value',
        name: '压力 (kPa)',
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
          data: seriesData,
          type: 'line',
          smooth: true,
          lineStyle: {
            color: '#52c41a',
            width: 2,
          },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              {
                offset: 0,
                color: 'rgba(82, 196, 26, 0.3)',
              },
              {
                offset: 1,
                color: 'rgba(82, 196, 26, 0.1)',
              },
            ]),
          },
        },
      ],
    });
  };

  // 更新图表数据
  const updateChart = () => {
    console.log('开始更新图表');
    console.log('chartInstance:', chartInstance);
    console.log('chartData.value:', chartData.value);
    console.log('selectedParams.value:', selectedParams.value);

    if (!chartInstance) {
      // 如果图表实例不存在，尝试初始化
      console.log('图表实例不存在，尝试初始化');
      initChart();
      if (!chartInstance) {
        console.error('图表初始化失败');
        return;
      }
    }

    if (chartData.value.length === 0 || selectedParams.value.length === 0) {
      // 如果没有数据或没有选择参数，显示空图表
      console.log('没有数据或没有选择参数，显示空图表');
      // 使用true参数强制覆盖所有配置，确保之前的曲线和图例被完全清除
      chartInstance.setOption(
        {
          backgroundColor: 'transparent',
          title: {
            text: '',
            left: 'center',
            top: 'center',
          },
          legend: {
            data: [],
            top: 10,
            textStyle: {
              color: '#cbd5e1',
            },
          },
          tooltip: {
            trigger: 'axis',
          },
          xAxis: {
            type: 'category',
            data: [],
            axisLabel: {
              color: '#94a3b8',
            },
          },
          yAxis: {
            type: 'value',
            axisLabel: {
              color: '#94a3b8',
            },
            splitLine: {
              lineStyle: {
                color: '#1e293b',
              },
            },
          },
          series: [],
        },
        true
      ); // true参数强制覆盖所有配置
      return;
    }

    console.log('有数据，生成图表');

    // 准备颜色数组，为不同指标使用不同颜色
    const colors = ['#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#13c2c2'];

    // 处理每个指标的数据
    const series: any[] = [];
    let allXAxisData: string[] = [];

    // 遍历每个指标
    chartData.value.forEach((item, index) => {
      const metric = item.metric;
      const metricData = item.data;

      // 只处理用户选择的参数
      if (!selectedParams.value.includes(metric)) {
        console.log(`指标 ${metric} 未被选择，跳过`);
        return;
      }

      console.log(`处理指标 ${metric}，数据长度: ${metricData.length}`);

      // 过滤异常值，确保数据格式正确
      const filteredData = metricData.filter((dataItem: any) => {
        return dataItem.value !== null && dataItem.value !== undefined && dataItem.value !== 9999;
      });

      console.log(`指标 ${metric} 过滤后的数据:`, filteredData);

      if (filteredData.length === 0) {
        console.log(`指标 ${metric} 没有有效数据`);
        return;
      }

      // 关键修复：按照时间戳排序数据
      const sortedData = filteredData.sort((a: any, b: any) => {
        const timeA = new Date(a.timestamp).getTime();
        const timeB = new Date(b.timestamp).getTime();
        return timeA - timeB;
      });

      console.log(`指标 ${metric} 排序后的数据:`, sortedData);

      // 生成该指标的x轴数据和系列数据
      // 修复：使用数组保存时间和值的映射，保持排序
      const timeValueList: Array<{ timeStr: string; value: number }> = [];
      const seenTimes = new Set<string>();

      sortedData.forEach((dataItem: any) => {
        const date = new Date(dataItem.timestamp);
        // 优化：生成格式为 MM-DD HH:MM 的时间字符串，去掉秒
        const timeStr = formatXAxisTime(date);

        // 只保留最后一个出现的时间点，确保时间顺序正确
        if (!seenTimes.has(timeStr)) {
          seenTimes.add(timeStr);
          timeValueList.push({
            timeStr,
            value: dataItem.value || 0,
          });
        }
      });

      // 创建时间到值的映射，提高查询效率
      const timeToValueMap = new Map<string, number>();
      timeValueList.forEach((item) => {
        timeToValueMap.set(item.timeStr, item.value);
      });

      // 保存x轴数据，用于后续处理
      if (index === 0) {
        // 直接使用排序后的数据，保持时间顺序
        allXAxisData = timeValueList.map((item) => item.timeStr);
      }

      // 为当前指标生成与x轴数据对应的系列数据
      const seriesData = allXAxisData.map((timeStr) => {
        return timeToValueMap.get(timeStr) || 0;
      });

      // 创建该指标的系列配置
      series.push({
        name: getMetricName(metric),
        data: seriesData,
        type: 'line',
        smooth: true,
        lineStyle: {
          color: colors[index % colors.length],
          width: 2,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            {
              offset: 0,
              color: `${colors[index % colors.length]}33`, // 33是透明度
            },
            {
              offset: 1,
              color: `${colors[index % colors.length]}11`, // 11是透明度
            },
          ]),
        },
      });
    });

    if (series.length === 0) {
      // 如果没有有效数据，显示空图表
      chartInstance.setOption({
        backgroundColor: 'transparent',
        title: {
          text: '暂无有效数据',
          left: 'center',
          top: 'center',
        },
        xAxis: {
          type: 'category',
          data: [],
          axisLabel: {
            color: '#94a3b8',
          },
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            color: '#94a3b8',
          },
          splitLine: {
            lineStyle: {
              color: '#1e293b',
            },
          },
        },
        series: [],
      });
      return;
    }

    console.log('所有系列数据:', series);
    console.log('x轴数据:', allXAxisData);

    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        formatter: function (params: any) {
          let result = `${params[0].axisValue}<br/>`;
          params.forEach((param: any) => {
            result += `${param.seriesName}: ${param.value}<br/>`;
          });
          return result;
        },
      },
      legend: {
        data: series.map((item) => item.name),
        top: 10,
        textStyle: {
          color: '#cbd5e1',
        },
      },
      dataZoom: [
        {
          type: 'inside',
          start: 0,
          end: 100,
        },
        {
          type: 'slider',
          bottom: 0,
          start: 0,
          end: 100,
        },
      ],
      xAxis: {
        type: 'category',
        data: allXAxisData,
        axisLabel: {
          color: '#94a3b8',
          rotate: 45,
          interval: 'auto',
          formatter: function (value: string) {
            return value;
          },
        },
        axisTick: {
          interval: 'auto',
        },
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          color: '#94a3b8',
        },
        splitLine: {
          lineStyle: {
            color: '#1e293b',
          },
        },
      },
      series: series,
    };

    console.log('设置图表选项:', option);
    chartInstance.setOption(option, true); // 使用true参数强制覆盖所有配置，确保之前的曲线和图例被完全清除
    console.log('图表更新成功');
  };

  // 获取指标名称
  const getMetricName = (metric: string) => {
    const metricMap: Record<string, string> = {
      temperature: '温度 (°C)',
      pressure: '压力 (kPa)',
      windVolume: '风量 (m³/h)',
      coalInjection: '喷煤量 (kg/t)',
      materialHeight: '料面高度 (m)',
      gasFlow: '煤气流量',
      oxygenLevel: '氧气浓度',
      productionRate: '生产速率',
      energyConsumption: '能耗',
      siliconContent: '铁水含硅量 (%)',
      airFlow: '风量',
      coalFlow: '煤量',
      furnaceId: '高炉ID',
      hotMetalTemperature: '铁水温度 (°C)',
      constantSignal: '恒定信号',
      status: '状态',
      operator: '操作员',
    };
    return metricMap[metric] || metric;
  };

  // 获取字段映射
  const getFieldByMetric = (metric: string) => {
    const fieldMap: Record<string, string> = {
      temperature: 'temperature',
      pressure: 'pressure',
      windVolume: 'windVolume',
      coalInjection: 'coalInjection',
      materialHeight: 'materialHeight',
      gasFlow: 'gasFlow',
      oxygenLevel: 'oxygenLevel',
      productionRate: 'productionRate',
      energyConsumption: 'energyConsumption',
      siliconContent: 'siliconContent',
      airFlow: 'airFlow',
      coalFlow: 'coalFlow',
      furnaceId: 'furnaceId',
      hotMetalTemperature: 'hotMetalTemperature',
      constantSignal: 'constantSignal',
      status: 'status',
      operator: 'operator',
    };
    return fieldMap[metric] || metric;
  };

  // 获取字段值，增强健壮性
  const getFieldVal = (item: any, metric: string) => {
    const fieldName = getFieldByMetric(metric);
    // 尝试获取字段值，支持直接字段或value字段
    return item[fieldName] !== null && item[fieldName] !== undefined && item[fieldName] !== 9999
      ? item[fieldName]
      : item.value !== null && item.value !== undefined && item.value !== 9999
      ? item.value
      : null;
  };

  // 获取周的日期范围
  const getWeekDateRange = (year: number, weekNumber: number) => {
    // 计算该年第一天
    const firstDay = new Date(year, 0, 1);
    // 计算第一周的周五是几号
    const firstWeekFriday = new Date(firstDay);
    firstWeekFriday.setDate(firstDay.getDate() + ((5 - firstDay.getDay() + 7) % 7));

    // 计算目标周的周五
    const targetWeekFriday = new Date(firstWeekFriday);
    targetWeekFriday.setDate(firstWeekFriday.getDate() + (weekNumber - 1) * 7);

    // 计算该周的周一和周日
    const monday = new Date(targetWeekFriday);
    monday.setDate(targetWeekFriday.getDate() - 4);

    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);

    // 格式化日期为 YYYY.MM.DD
    const formatDate = (date: Date) => {
      return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
        date.getDate()
      ).padStart(2, '0')}`;
    };

    return `${formatDate(monday)}-${formatDate(sunday)}`;
  };

  // 数据处理：按日期或周对比模式
  const processDistributionData = (data: any[], metric: string, grain = 'day') => {
    const metricData = data
      .map((item) => ({ timestamp: item.timestamp, value: getFieldVal(item, metric) }))
      .filter((item) => item.value !== null && item.value !== undefined && !isNaN(item.value));

    if (metricData.length === 0)
      return { dates: [], displayDates: [], seriesData: { avg: [], max: [], min: [] } };

    // 1. 按日期或周归类数据
    const groupedDataMap = new Map<string, { values: number[]; displayLabel: string }>();

    metricData.forEach((item) => {
      const date = new Date(item.timestamp);
      let groupKey = '';
      let displayLabel = '';

      if (grain === 'month') {
        // 按月处理：格式化为 YYYY-MM
        const year = date.getFullYear();
        const month = date.getMonth() + 1;
        groupKey = `${year}-${String(month).padStart(2, '0')}`;
        // 月显示标签：YYYY年MM月
        displayLabel = `${year}年${month}月`;
      } else if (grain === 'week') {
        // 按周处理：返回 YYYY-WW 格式，其中 WW 是周数
        const year = date.getFullYear();
        // 获取当前日期是一年中的第几周
        const firstDay = new Date(year, 0, 1);
        const pastDaysOfYear = (date.getTime() - firstDay.getTime()) / 86400000;
        const weekNumber = Math.ceil((pastDaysOfYear + firstDay.getDay() + 1) / 7);
        groupKey = `${year}-W${String(weekNumber).padStart(2, '0')}`;

        // 生成友好的显示标签：2026年第4周
        displayLabel = `${year}年第${weekNumber}周`;
      } else {
        // 按日处理：格式化为 YYYY-MM-DD
        groupKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
          date.getDate()
        ).padStart(2, '0')}`;
        // 日显示标签：MM-DD
        displayLabel = `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
          date.getDate()
        ).padStart(2, '0')}`;
      }

      if (!groupedDataMap.has(groupKey)) {
        groupedDataMap.set(groupKey, { values: [], displayLabel });
      }
      groupedDataMap.get(groupKey)!.values.push(item.value);
    });

    // 2. 排序日期/周/月，保证时间轴正确
    const sortedKeys = Array.from(groupedDataMap.keys()).sort((a, b) => {
      if (grain === 'month') {
        // 解析月格式：YYYY-MM
        return new Date(a + '-01').getTime() - new Date(b + '-01').getTime();
      } else if (grain === 'week') {
        // 解析周格式：YYYY-WW
        const [yearA, weekA] = a.split('-W').map(Number);
        const [yearB, weekB] = b.split('-W').map(Number);
        if (yearA !== yearB) return yearA - yearB;
        return weekA - weekB;
      } else {
        // 按日排序
        return new Date(a).getTime() - new Date(b).getTime();
      }
    });

    // 3. 计算每一天或每周的统计指标
    const periodAvgs: number[] = [];
    const periodMaxs: number[] = [];
    const periodMins: number[] = [];
    const displayLabels: string[] = [];

    sortedKeys.forEach((key) => {
      const { values, displayLabel } = groupedDataMap.get(key)!;
      const sum = values.reduce((a, b) => a + b, 0);
      // 保留3位小数，解决显示精度问题
      periodAvgs.push(parseFloat((sum / values.length).toFixed(3)));
      periodMaxs.push(parseFloat(Math.max(...values).toFixed(3)));
      periodMins.push(parseFloat(Math.min(...values).toFixed(3)));
      displayLabels.push(displayLabel);
    });

    return {
      dates: sortedKeys, // 用于排序的键
      displayDates: displayLabels, // 用于显示的友好标签
      seriesData: { avg: periodAvgs, max: periodMaxs, min: periodMins }, // Y轴数据
    };
  };

  // 初始化参数分布图表
  // 初始化参数分布图表
  const initDistributionChart = () => {
    if (!distributionChartRef.value) return;

    // 防御性编程：如果有旧实例，先销毁
    if (distributionChartInstance) {
      distributionChartInstance.dispose();
      distributionChartInstance = null;
    }

    try {
      distributionChartInstance = echarts.init(distributionChartRef.value);
      bindChartResize(distributionChartRef.value, () => {
        distributionChartInstance?.resize();
      });
    } catch (e) {
      console.error('初始化分布图表失败:', e);
    }
  };

  // 按粒度分组计算统计数据
  const calculateStatsByGrain = (data: any[], grain: string) => {
    // 按粒度分组数据
    const groupedData = new Map<string, Map<string, number[]>>();

    data.forEach((item) => {
      const date = new Date(item.timestamp);
      let groupKey = '';

      if (grain === 'month') {
        // 按月分组：YYYY-MM
        groupKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      } else if (grain === 'week') {
        // 按周分组：YYYY-WW
        const year = date.getFullYear();
        const firstDay = new Date(year, 0, 1);
        const pastDaysOfYear = (date.getTime() - firstDay.getTime()) / 86400000;
        const weekNumber = Math.ceil((pastDaysOfYear + firstDay.getDay() + 1) / 7);
        groupKey = `${year}-W${String(weekNumber).padStart(2, '0')}`;
      } else {
        // 按日分组：YYYY-MM-DD
        groupKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
          date.getDate()
        ).padStart(2, '0')}`;
      }

      if (!groupedData.has(groupKey)) {
        groupedData.set(groupKey, new Map());
      }

      const metrics = groupedData.get(groupKey)!;

      // 为每个指标添加数据
      const metricsToProcess = [
        { key: 'temperature', field: 'temperature' },
        { key: 'pressure', field: 'pressure' },
        { key: 'materialHeight', field: 'materialHeight' },
        { key: 'gasComposition', field: 'gasFlow' }, // 使用gasFlow作为煤气成分
        { key: 'oxygenLevel', field: 'oxygenLevel' },
        { key: 'productionRate', field: 'productionRate' },
      ];

      metricsToProcess.forEach((metric) => {
        if (!metrics.has(metric.key)) {
          metrics.set(metric.key, []);
        }
        const value = item[metric.field];
        if (value !== null && value !== undefined && value !== 9999) {
          metrics.get(metric.key)!.push(value);
        }
      });
    });

    // 计算每个粒度的统计数据
    const statsByMetric = new Map<string, { avg: number[]; max: number[]; min: number[] }>();

    // 初始化统计数据结构
    [
      'temperature',
      'pressure',
      'materialHeight',
      'gasComposition',
      'oxygenLevel',
      'productionRate',
    ].forEach((metric) => {
      statsByMetric.set(metric, { avg: [], max: [], min: [] });
    });

    // 计算每个分组的统计数据
    groupedData.forEach((metrics) => {
      metrics.forEach((values, metric) => {
        if (values.length > 0) {
          const avg = values.reduce((a: number, b: number) => a + b, 0) / values.length;
          const max = Math.max(...values);
          const min = Math.min(...values);

          const stats = statsByMetric.get(metric)!;
          stats.avg.push(avg);
          stats.max.push(max);
          stats.min.push(min);
        }
      });
    });

    return statsByMetric;
  };

  // 更新统计卡片数据
  const updateStatsCards = (data: any[], grain: string) => {
    if (data.length === 0) {
      // 重置统计数据
      avgTemperature.value = 0;
      maxTemperature.value = 0;
      minTemperature.value = 0;
      avgPressure.value = 0;
      maxPressure.value = 0;
      minPressure.value = 0;
      avgMaterialLevel.value = 0;
      maxMaterialLevel.value = 0;
      minMaterialLevel.value = 0;
      avgGasComposition.value = 0;
      maxGasComposition.value = 0;
      minGasComposition.value = 0;
      avgOxygenLevel.value = 0;
      maxOxygenLevel.value = 0;
      minOxygenLevel.value = 0;
      avgProductionRate.value = 0;
      maxProductionRate.value = 0;
      minProductionRate.value = 0;
      return;
    }

    // 按粒度计算统计数据（所有粒度都使用相同的逻辑，确保与图表一致）
    const statsByMetric = calculateStatsByGrain(data, grain);

    // 更新温度统计
    const tempStats = statsByMetric.get('temperature');
    if (tempStats && tempStats.avg.length > 0) {
      avgTemperature.value = Number(
        (tempStats.avg.reduce((a, b) => a + b, 0) / tempStats.avg.length).toFixed(3)
      );
      maxTemperature.value = Number(Math.max(...tempStats.max).toFixed(3));
      minTemperature.value = Number(Math.min(...tempStats.min).toFixed(3));
    }

    // 更新压力统计
    const pressureStats = statsByMetric.get('pressure');
    if (pressureStats && pressureStats.avg.length > 0) {
      avgPressure.value = Number(
        (pressureStats.avg.reduce((a, b) => a + b, 0) / pressureStats.avg.length).toFixed(3)
      );
      maxPressure.value = Number(Math.max(...pressureStats.max).toFixed(3));
      minPressure.value = Number(Math.min(...pressureStats.min).toFixed(3));
    }

    // 更新料面高度统计
    const materialStats = statsByMetric.get('materialHeight');
    if (materialStats && materialStats.avg.length > 0) {
      avgMaterialLevel.value = Number(
        (materialStats.avg.reduce((a, b) => a + b, 0) / materialStats.avg.length).toFixed(3)
      );
      maxMaterialLevel.value = Number(Math.max(...materialStats.max).toFixed(3));
      minMaterialLevel.value = Number(Math.min(...materialStats.min).toFixed(3));
    }

    // 更新煤气成分统计
    const gasStats = statsByMetric.get('gasComposition');
    if (gasStats && gasStats.avg.length > 0) {
      avgGasComposition.value = Number(
        (gasStats.avg.reduce((a, b) => a + b, 0) / gasStats.avg.length).toFixed(3)
      );
      maxGasComposition.value = Number(Math.max(...gasStats.max).toFixed(3));
      minGasComposition.value = Number(Math.min(...gasStats.min).toFixed(3));
    }

    // 更新氧气含量统计
    const oxygenStats = statsByMetric.get('oxygenLevel');
    if (oxygenStats && oxygenStats.avg.length > 0) {
      avgOxygenLevel.value = Number(
        (oxygenStats.avg.reduce((a, b) => a + b, 0) / oxygenStats.avg.length).toFixed(3)
      );
      maxOxygenLevel.value = Number(Math.max(...oxygenStats.max).toFixed(3));
      minOxygenLevel.value = Number(Math.min(...oxygenStats.min).toFixed(3));
    }

    // 更新生产速率统计
    const productionStats = statsByMetric.get('productionRate');
    if (productionStats && productionStats.avg.length > 0) {
      avgProductionRate.value = Number(
        (productionStats.avg.reduce((a, b) => a + b, 0) / productionStats.avg.length).toFixed(3)
      );
      maxProductionRate.value = Number(Math.max(...productionStats.max).toFixed(3));
      minProductionRate.value = Number(Math.min(...productionStats.min).toFixed(3));
    }
  };

  // 处理粒度变化
  const handleGrainChange = () => {
    // 重新更新参数分布图表
    updateDistributionChart(fullData.value);
    // 更新统计卡片数据
    updateStatsCards(fullData.value, dateGrain.value);
  };

  // 2. 更新图表：使用数值轴 (type: 'value')
  // 2. 更新图表：切换为【日/周统计趋势图】
  const updateDistributionChart = (allData: any[]) => {
    if (!distributionChartRef.value) return;
    // 强制修复容器高度，防止白屏
    if (distributionChartRef.value.clientHeight === 0) {
      distributionChartRef.value.style.height = '350px';
    }

    // 销毁旧实例并重建，确保配置彻底更新
    if (distributionChartInstance) {
      distributionChartInstance.dispose();
      distributionChartInstance = null;
    }
    distributionChartInstance = echarts.init(distributionChartRef.value);

    const selectedMetric = selectedParams.value[0] || 'temperature';
    const result = processDistributionData(allData, selectedMetric, dateGrain.value);
    const metricName = getMetricName(selectedMetric);
    const unitMatch = metricName.match(/\((.*?)\)/);
    const unit = unitMatch ? unitMatch[1] : '';
    // 移除名称中的单位，用于标题拼接
    const rawName = unitMatch ? metricName.replace(/\s*\(.*?\)/, '') : metricName;

    // 根据粒度更新图例和标题
    let grainText = '日';
    if (dateGrain.value === 'week') {
      grainText = '周';
    } else if (dateGrain.value === 'month') {
      grainText = '月';
    }

    const commonOption = {
      backgroundColor: 'transparent',
      title: { text: '', left: 'center' },
      // 调整边距，防止标签显示不全
      grid: { top: '15%', bottom: '15%', left: '5%', right: '5%', containLabel: true },
      legend: {
        data: [`${grainText}平均值`, `${grainText}最大值`, `${grainText}最小值`],
        top: 30,
        textStyle: {
          color: '#cbd5e1',
        },
      },
      // Tooltip 配置
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(16, 20, 29, 0.95)',
        formatter: function (params: any) {
          if (!params || params.length === 0) return '';
          // 现在的 axisValue 是日期 (如 2025-01-20) 或周 (如 2025-W01)
          const date = params[0].axisValue;
          let html = `<div style="font-weight:bold;margin-bottom:5px;border-bottom:1px solid #1e293b;padding-bottom:5px;">${date}</div>`;

          params.forEach((p: any) => {
            // p.value 是具体的数值 (如 4.9)
            const marker =
              p.marker ||
              `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${p.color};"></span>`;
            html += `<div style="margin-top:5px;font-size:13px;">
                      ${marker} ${p.seriesName}: <span style="font-weight:bold;color:${p.color}">${p.value}</span> ${unit}
                    </div>`;
          });
          return html;
        },
      },
      // *** 核心改变：X轴变成日期 ***
      xAxis: {
        type: 'category',
        data: result.displayDates,
        name: '日期',
        nameLocation: 'middle',
        nameGap: 30,
        boundaryGap: true, // true 表示柱状图居中，false 表示折线图贴边
        axisLabel: {
          color: '#94a3b8',
          rotate: 45, // 周标签较长，旋转45度显示
          interval: 0,
        },
        splitLine: {
          lineStyle: {
            color: '#1e293b',
          },
        },
      },
      // *** 核心改变：Y轴变成数值 (这样 4.9 和 5.9 就有明显高度差了) ***
      yAxis: {
        type: 'value',
        name: `${rawName} (${unit})`,
        scale: true, // 自动缩放Y轴范围，让差异更明显
        axisLabel: {
          color: '#94a3b8',
        },
        splitLine: { show: true, lineStyle: { type: 'dashed', color: '#1e293b' } },
      },
      // 保留缩放条，万一日期很多
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 0 }],
      series: [
        {
          name: `${grainText}平均值`,
          data: result.seriesData.avg,
          type: 'bar', // 改为柱状图，对比感更强
          barMaxWidth: 30,
          itemStyle: { color: '#1890ff', borderRadius: [4, 4, 0, 0] },
        },
        {
          name: `${grainText}最大值`,
          data: result.seriesData.max,
          type: 'line',
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { color: '#f5222d', width: 2, type: 'dashed' },
        },
        {
          name: `${grainText}最小值`,
          data: result.seriesData.min,
          type: 'line',
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { color: '#52c41a', width: 2, type: 'dashed' },
        },
      ],
    };

    if (result.dates.length === 0) {
      distributionChartInstance.setOption(
        {
          ...commonOption,
          title: { text: '暂无数据', left: 'center', top: 'center' },
          series: [],
        },
        true
      );
    } else {
      distributionChartInstance.setOption(commonOption, true);
    }

    setTimeout(() => {
      distributionChartInstance?.resize();
    }, 50);
  };

  // 处理标签页切换
  const handleTabChange = (value: string) => {
    console.log('标签页切换:', value);
    activeTab.value = value;

    // 趋势分析的处理 (保持你原有的逻辑不变，这里为了完整性展示)
    if (value === 'trend') {
      setTimeout(() => {
        nextTick(() => {
          initChart();
          initTemperatureChart();
          initPressureChart();

          setTimeout(() => {
            chartInstance?.resize();
            temperatureChartInstance?.resize();
            pressureChartInstance?.resize();
            if (chartData.value.length > 0) {
              updateChart();
              const actualAllData =
                fullData.value.length > 0 ? fullData.value : chartData.value[0]?.data || [];
              updateTemperatureChart(actualAllData);
              updatePressureChart(actualAllData);
            }
          }, 100);
        });
      }, 50);
    }
    // *** 统计分析的修复逻辑 ***
    else if (value === 'statistics') {
      console.log('切换到统计分析标签页，强制重新初始化');
      // 使用 setTimeout 确保 DOM 已经渲染
      setTimeout(() => {
        nextTick(() => {
          // 1. 即使实例存在，因为DOM变了，也必须销毁重建
          if (distributionChartInstance) {
            distributionChartInstance.dispose();
            distributionChartInstance = null;
          }

          // 2. 重新初始化
          initDistributionChart();

          // 3. 如果有数据，立即渲染
          // 优先使用 fullData (所有数据)，如果没有则尝试从 chartData 获取
          let dataToRender: any[] = [];
          if (fullData.value && fullData.value.length > 0) {
            dataToRender = fullData.value;
          } else if (chartData.value && chartData.value.length > 0 && chartData.value[0].data) {
            dataToRender = chartData.value[0].data;
          }

          if (dataToRender.length > 0) {
            updateDistributionChart(dataToRender);
          }

          // 4. 双重保险：再次调整大小
          setTimeout(() => {
            distributionChartInstance?.resize();
          }, 100);
        });
      }, 50); // 延时 50ms 等待 Tab 动画或 DOM 切换
    }
  };

  // 页面加载时获取数据
  onMounted(async () => {
    await loadSystemDefaults();
    console.log('页面加载，初始化组件');
    nextTick(() => {
      console.log('执行nextTick回调');
      // 初始化所有图表
      initChart();
      initTemperatureChart();
      initPressureChart();
      initDistributionChart();
      console.log('初始化图表完成');
      queryData();
      console.log('开始获取数据');
      loadReportHistory();
      console.log('开始获取历史报表数据');
    });
  });

  onBeforeUnmount(() => {
    resizeObserverBindings.forEach((binding) => binding.observer.disconnect());
    resizeObserverBindings.length = 0;
    resizeHandlerBindings.forEach((binding) => {
      window.removeEventListener('resize', binding.handler);
    });
    resizeHandlerBindings.length = 0;
    chartInstance?.dispose();
    chartInstance = null;
    temperatureChartInstance?.dispose();
    temperatureChartInstance = null;
    pressureChartInstance?.dispose();
    pressureChartInstance = null;
    distributionChartInstance?.dispose();
    distributionChartInstance = null;
  });
</script>
