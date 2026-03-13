<template>
  <div
    class="anomaly-detection min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="异常检测" class="mb-4 anomaly-main-card">
      <!-- 检测配置 -->
      <n-form label-placement="left" label-width="120" class="mb-4 anomaly-form">
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="高炉选择">
            <n-select
              v-model:value="selectedFurnace"
              :options="furnaceOptions"
              placeholder="选择高炉"
            />
          </n-form-item>
          <n-form-item label="检测模式">
            <n-select
              v-model:value="detectionMode"
              :options="detectionModeOptions"
              placeholder="选择检测模式"
              @update:value="handleModeChange"
            />
          </n-form-item>
          <n-form-item label="检测算法">
            <n-select
              v-model:value="selectedAlgorithm"
              :options="algorithmOptions"
              placeholder="选择检测算法"
            />
          </n-form-item>
        </div>
        <n-form-item label="检测参数">
          <n-select
            v-model:value="selectedParams"
            multiple
            :options="paramOptions"
            placeholder="选择检测参数"
          />
        </n-form-item>
        <div class="flex justify-end">
          <n-button
            round
            :type="isScheduledRunning ? 'error' : 'primary'"
            @click="toggleDetection"
            :loading="detecting"
          >
            {{ isScheduledRunning ? '停止定时检测' : '开始检测' }}
          </n-button>
          <n-button round @click="resetDetection" class="ml-2">重置</n-button>
          <n-button round @click="testAnomaly" class="ml-2" type="error" ghost>注入单条异常</n-button>
          <n-button round @click="injectBatchData" class="ml-2" type="warning" ghost
            >注入批量数据(测试统计)</n-button
          >
          <n-button round @click="configModalRef?.open()" class="ml-2" type="info" ghost>检测配置</n-button>
        </div>
        <div class="mt-2 text-xs" style="color: var(--n-text-color-3);">
          <span>运行态：{{ isScheduledRunning ? '后端定时检测中' : '空闲' }}</span>
          <span class="ml-3">最近检测：{{ lastDetectionTime || '-' }}</span>
          <span class="ml-3">批次号：{{ lastBatchId || '-' }}</span>
        </div>
      </n-form>

      <!-- 提示信息 -->
      <n-alert title="检测说明" type="info" class="mb-4 anomaly-alert" closable>
        当前检测逻辑：分析最近50条生产数据。检测规则：1. 阈值检测（超出安全范围，可配置）；2.
        统计检测（Z-Score > 3.0）；3. IQR 四分位距检测。
      </n-alert>

      <!-- 检测结果 -->
      <n-tabs type="line" default-value="realtime" @update:value="handleTabChange">
        <n-tab-pane name="realtime" tab="实时异常监控">
          <div class="anomaly-panel">
            <div class="grid grid-cols-3 gap-4 mb-4">
              <n-card
                size="small"
                :bordered="false"
                class="anomaly-sub-card"
              >
                <n-statistic label="当前异常数" :value="currentAnomalyCount" />
                <n-text depth="3" class="mt-1">需要处理的异常数量</n-text>
              </n-card>
              <n-card
                size="small"
                :bordered="false"
                class="anomaly-sub-card"
              >
                <n-statistic label="今日异常数" :value="todayAnomalyCount" />
                <n-text depth="3" class="mt-1">今日检测到的异常数量</n-text>
              </n-card>
              <n-card
                size="small"
                :bordered="false"
                class="anomaly-sub-card"
              >
                <n-statistic label="处理率" :value="processingRate" suffix="%" />
                <n-text depth="3" class="mt-1">异常处理的成功率</n-text>
              </n-card>
            </div>

            <n-card title="实时异常列表" size="small" class="anomaly-sub-card">
              <n-data-table
                :columns="anomalyColumns"
                :data="realtimeGroupedList"
                size="small"
                :max-height="400"
                :loading="loadingRealtime"
              />
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="analysis" tab="异常分析">
          <div class="anomaly-panel">
            <n-card title="异常类型分布" size="small" class="mb-4 anomaly-sub-card">
              <div class="h-80" ref="distributionChartRef"></div>
            </n-card>

            <n-card title="异常趋势" size="small" class="anomaly-sub-card">
              <div class="h-80" ref="trendChartRef"></div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="history" tab="异常历史">
          <div class="anomaly-panel">
            <n-form label-placement="left" label-width="120" class="mb-4 anomaly-form">
              <div class="grid grid-cols-2 gap-4">
                <n-form-item label="时间范围">
                  <n-date-picker
                    v-model:value="historyDateRange"
                    type="daterange"
                    placeholder="选择时间范围"
                    clearable
                  />
                </n-form-item>
                <n-form-item label="异常类型">
                  <n-select
                    v-model:value="anomalyType"
                    :options="paramOptions"
                    placeholder="选择异常类型"
                    clearable
                  />
                </n-form-item>
              </div>
              <div class="flex justify-end">
                <n-button round type="primary" @click="queryHistory" :loading="loadingHistory"
                  >查询历史</n-button
                >
              </div>
            </n-form>

            <n-card title="异常历史记录" size="small" class="anomaly-sub-card">
              <n-data-table
                :columns="historyColumns"
                :data="historyList"
                size="small"
                :loading="loadingHistory"
              />
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- 查看详情弹窗 -->
    <n-modal
      v-model:show="detailModalVisible"
      preset="card"
      title="异常详情"
      :style="{ width: 'min(800px, 92vw)' }"
    >
      <n-descriptions bordered :column="2">
        <n-descriptions-item label="异常ID">{{ currentDetailRow?.id }}</n-descriptions-item>
        <n-descriptions-item label="高炉ID">{{ currentDetailRow?.furnaceId }}</n-descriptions-item>
        <n-descriptions-item label="检测时间">{{
          formatTime(currentDetailRow?.detectionTime)
        }}</n-descriptions-item>
        <n-descriptions-item label="异常参数">{{
          getParamLabel(currentDetailRow?.parameterName)
        }}</n-descriptions-item>
        <n-descriptions-item label="实际值">
          <n-tag type="error">{{ currentDetailRow?.actualValue }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="预期范围">{{
          currentDetailRow?.expectedRange
        }}</n-descriptions-item>
        <n-descriptions-item label="异常级别">
          <n-tag :type="levelTagType(currentDetailRow?.level)">
            {{ levelLabel(currentDetailRow?.level) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="statusTagType(currentDetailRow?.status)">
            {{ statusLabel(currentDetailRow?.status) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item
          label="处理人ID"
          v-if="currentDetailRow?.handlerUser !== null && currentDetailRow?.handlerUser !== undefined"
          >{{ currentDetailRow?.handlerUser }}</n-descriptions-item
        >
        <n-descriptions-item label="处理时间" v-if="currentDetailRow?.handleTime">{{
          formatTime(currentDetailRow?.handleTime)
        }}</n-descriptions-item>
        <n-descriptions-item label="处理备注" :span="2" v-if="currentDetailRow?.handlerContent">{{
          currentDetailRow?.handlerContent
        }}</n-descriptions-item>
        <n-descriptions-item label="异常描述" :span="2" v-if="currentDetailRow?.description">{{
          currentDetailRow?.description
        }}</n-descriptions-item>
      </n-descriptions>

      <!-- 关联的原始数据详情 -->
      <n-divider title-placement="left">关联的原始生产数据</n-divider>
      <n-spin :show="loadingSourceData">
        <div v-if="sourceData">
          <n-descriptions bordered :column="3" size="small">
            <n-descriptions-item label="时间戳">{{
              formatTime(sourceData.timestamp)
            }}</n-descriptions-item>
            <n-descriptions-item label="温度">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'temperature',
                }"
                >{{ sourceData.temperature }} °C</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="压力">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'pressure',
                }"
                >{{ sourceData.pressure }} kPa</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="料面高度">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'materialHeight',
                }"
                >{{ sourceData.materialHeight }} m</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="煤气流量">
              <span
                :class="{ 'text-red-500 font-bold': currentDetailRow?.parameterName === 'gasFlow' }"
                >{{ sourceData.gasFlow }} m³/h</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="氧气含量">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'oxygenLevel',
                }"
                >{{ sourceData.oxygenLevel }} %</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="铁水含硅量">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'siliconContent',
                }"
                >{{ sourceData.siliconContent }} %</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="能耗">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'energyConsumption',
                }"
                >{{ sourceData.energyConsumption }} kgce/t</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="生产率">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'productionRate',
                }"
                >{{ sourceData.productionRate }} t/h</span
              >
            </n-descriptions-item>
            <n-descriptions-item label="铁水温度">
              <span
                :class="{
                  'text-red-500 font-bold': currentDetailRow?.parameterName === 'hotMetalTemperature',
                }"
                >{{ sourceData.hotMetalTemperature }} °C</span
              >
            </n-descriptions-item>
          </n-descriptions>
        </div>
        <n-empty v-else description="暂无关联数据" />
      </n-spin>
    </n-modal>

    <!-- 处理异常弹窗 -->
    <n-modal v-model:show="handleModalVisible" preset="dialog" title="处理异常">
      <n-form label-placement="left" label-width="90">
        <n-form-item label="处理人ID">
          <n-input v-model:value="handleForm.handlerUser" placeholder="可选：请输入处理人ID(数字)" />
        </n-form-item>
        <n-form-item label="处理状态">
          <n-select
            v-model:value="handleForm.status"
            :options="[
              { label: '待处理', value: 'PENDING' },
              { label: '处理中', value: 'PROCESSING' },
              { label: '已解决', value: 'RESOLVED' },
              { label: '已关闭', value: 'CLOSED' },
            ]"
          />
        </n-form-item>
        <n-form-item label="处理备注">
          <n-input
            v-model:value="handleForm.handlerContent"
            type="textarea"
            placeholder="请输入处理备注"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="handleModalVisible = false">取消</n-button>
        <n-button type="primary" @click="submitHandle">确定</n-button>
      </template>
    </n-modal>

    <n-modal
      v-model:show="groupDetailModalVisible"
      preset="card"
      title="异常详情(聚合)"
      :style="{ width: 'min(1000px, 92vw)' }"
    >
      <n-spin :show="groupLoading">
        <n-descriptions bordered :column="3" size="small" v-if="groupSourceData">
          <n-descriptions-item label="生产数据ID">{{ groupSourceData.id }}</n-descriptions-item>
          <n-descriptions-item label="高炉ID">{{ groupSourceData.furnaceId }}</n-descriptions-item>
          <n-descriptions-item label="时间戳">{{ formatTime(groupSourceData.timestamp) }}</n-descriptions-item>
        </n-descriptions>
        <n-empty v-else description="暂无关联生产数据" />

        <n-divider title-placement="left">异常参数明细</n-divider>
        <n-data-table
          :columns="groupAnomalyColumns"
          :data="groupAnomalyList"
          size="small"
          :max-height="360"
        />
      </n-spin>
    </n-modal>

    <ConfigModal ref="configModalRef" />
  </div>
</template>

<script setup lang="ts">
  import { ref, h, computed, nextTick, onMounted, onUnmounted, reactive } from 'vue';
  import { useRouter } from 'vue-router';
  import { format } from 'date-fns';
  import {
    useMessage,
    NTag,
    NButton,
    NButtonGroup,
    NAlert,
    NDescriptions,
    NDescriptionsItem,
    NModal,
    NForm,
    NFormItem,
    NInput,
    NSelect,
    NCard,
    NTabs,
    NTabPane,
    NStatistic,
    NText,
    NDataTable,
    NDatePicker,
    NDivider,
    NSpin,
    NEmpty,
  } from 'naive-ui';
  import { anomalyApi, dataManagementApi, systemApi } from '@/api/blast-furnace';
  import { useECharts } from '@/hooks/web/useECharts';
  import ConfigModal from './components/ConfigModal.vue';

  const message = useMessage();
  const router = useRouter();

  type AnomalyRecord = {
    id?: number | string;
    relatedDataId?: number | null;
    furnaceId?: string | null;
    detectionTime?: string | number | Date | null;
    parameterName?: string | null;
    status?: any;
    level?: any;
    handlerContent?: string | null;
    handlerUser?: number | string | null;
    handleTime?: string | null;
    description?: string | null;
  };

  type UiStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED';

  const normalizeStatus = (raw: any): UiStatus => {
    if (raw === 0 || raw === '0' || String(raw).toUpperCase() === 'PENDING') return 'PENDING';
    if (raw === 1 || raw === '1' || String(raw).toUpperCase() === 'PROCESSING') return 'PROCESSING';
    const up = String(raw).toUpperCase();
    if (raw === 2 || raw === '2' || up === 'COMPLETED' || up === 'RESOLVED') return 'RESOLVED';
    if (raw === 3 || raw === '3' || up === 'CLOSED') return 'CLOSED';
    return 'PENDING';
  };

  const statusLabel = (raw: any) => {
    const s = normalizeStatus(raw);
    if (s === 'PENDING') return '待处理';
    if (s === 'PROCESSING') return '处理中';
    if (s === 'RESOLVED') return '已解决';
    return '已关闭';
  };

  const statusTagType = (raw: any) => {
    const s = normalizeStatus(raw);
    if (s === 'PENDING') return 'default';
    if (s === 'PROCESSING') return 'info';
    if (s === 'RESOLVED') return 'success';
    return 'warning';
  };

  const levelLabel = (raw: any) => {
    const v = String(raw ?? '');
    if (!v) return '-';
    if (v.includes('严重')) return '严重';
    if (v.includes('警告')) return '警告';
    if (v.includes('提示')) return '提示';
    const up = v.toUpperCase();
    if (up.includes('SEVERE') || up.includes('ERROR') || up.includes('URGENT')) return '严重';
    if (up.includes('WARNING') || up.includes('WARN')) return '警告';
    return '提示';
  };

  const levelTagType = (raw: any) => {
    const v = levelLabel(raw);
    if (v === '严重') return 'error';
    if (v === '警告') return 'warning';
    return 'info';
  };

  // Chart refs
  const distributionChartRef = ref<HTMLDivElement | null>(null);
  const trendChartRef = ref<HTMLDivElement | null>(null);
  const {
    setOptions: setDistributionOptions,
    resize: resizeDistributionChart,
    disposeInstance: disposeDistributionChart,
  } = useECharts(distributionChartRef);
  const {
    setOptions: setTrendOptions,
    resize: resizeTrendChart,
    disposeInstance: disposeTrendChart,
  } = useECharts(trendChartRef);

  // 检测配置
  const defaultFurnaceId = ref('BF-001');
  const selectedFurnace = ref(defaultFurnaceId.value);
  const furnaceOptions = [
    { label: '高炉1 (BF-001)', value: 'BF-001' },
    { label: '高炉2 (BF-002)', value: 'BF-002' },
    { label: '高炉3 (BF-003)', value: 'BF-003' },
  ];

  const detectionMode = ref('realtime');
  const detectionModeOptions = [
    { label: '实时检测', value: 'realtime' },
    { label: '批量检测', value: 'batch' },
    { label: '定时检测', value: 'scheduled' },
  ];

  const selectedAlgorithm = ref('ALL');
  const algorithmOptions = [
    { label: '综合检测 (推荐)', value: 'ALL' },
    { label: '阈值检测 (Threshold)', value: 'THRESHOLD' },
    { label: 'Z-Score 统计检测', value: 'Z_SCORE' },
    { label: 'IQR 四分位距检测', value: 'IQR' },
  ];

  const selectedParams = ref([
    'temperature',
    'pressure',
    'materialHeight',
    'gasFlow',
    'oxygenLevel',
    'productionRate',
    'energyConsumption',
    'hotMetalTemperature',
    'siliconContent',
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

  // 状态
  const detecting = ref(false);
  const isScheduledRunning = ref(false);
  const uiRefreshSeconds = ref(15);
  const FAST_REFRESH_MS = computed(() => Math.max(1000, Math.floor(uiRefreshSeconds.value * 1000)));
  const SLOW_REFRESH_MS = computed(() => Math.max(2000, FAST_REFRESH_MS.value * 4));
  let refreshTimer: ReturnType<typeof setTimeout> | null = null;
  const lastDetectionTime = ref('');
  const lastBatchId = ref('');
  const loadingRealtime = ref(false);
  const loadingHistory = ref(false);

  // 异常统计
  const currentAnomalyCount = ref(0);
  const todayAnomalyCount = ref(0);
  const processingRate = ref(0);

  // 异常列表
  const anomalyList = ref<AnomalyRecord[]>([]);
  const sourceCache = ref<Record<number, any>>({});

  const anomalyColumns = [
    { title: '生产数据ID', key: 'relatedDataId' },
    {
      title: '生产时间',
      key: 'productionTime',
      render: (row: any) => formatTime(row.productionTime || row.detectionTime),
    },
    {
      title: '异常数量',
      key: 'anomalyCount',
      render: (row: any) =>
        h(
          NTag,
          { type: row.anomalyCount >= 3 ? 'error' : 'warning' },
          { default: () => String(row.anomalyCount) }
        ),
    },
    {
      title: '异常参数',
      key: 'parameters',
      render: (row: any) => row.parametersLabel,
    },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: statusTagType(row.status) },
          { default: () => statusLabel(row.status) }
        ),
    },
    {
      title: '操作',
      key: 'action',
      render: (row: any) =>
        h(
          NButton,
          { size: 'small', onClick: () => openGroupDetailModal(row) },
          { default: () => '查看详情' }
        ),
    },
  ];

  const groupDetailModalVisible = ref(false);
  const groupAnomalyList = ref<any[]>([]);
  const groupSourceData = ref<any>(null);
  const groupLoading = ref(false);

  const groupAnomalyColumns = [
    { title: '异常ID', key: 'id' },
    {
      title: '异常参数',
      key: 'parameterName',
      render: (row: any) => getParamLabel(row.parameterName),
    },
    { title: '实际值', key: 'actualValue' },
    { title: '预期范围', key: 'expectedRange' },
    {
      title: '异常级别',
      key: 'level',
      render: (row: any) =>
        h(
          NTag,
          { type: levelTagType(row.level) },
          { default: () => levelLabel(row.level) }
        ),
    },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: statusTagType(row.status) },
          { default: () => statusLabel(row.status) }
        ),
    },
    {
      title: '操作',
      key: 'action',
      render: (row: any) =>
        h(NButtonGroup, null, {
          default: () => [
            h(
              NButton,
              { size: 'small', onClick: () => openDetailModal(row) },
              { default: () => '查看详情' }
            ),
            h(
              NButton,
              {
                size: 'small',
                type: 'primary',
                onClick: () => gotoWarning(row),
              },
              { default: () => '去预警处理' }
            ),
          ],
        }),
    },
  ];

  const gotoWarning = (row: any) => {
    router.push({
      path: '/blast-furnace/monitoring/early-warning',
      query: {
        highlightId: row?.id == null ? undefined : String(row.id),
        _hl: String(Date.now()),
      },
    });
  };

  // 历史查询
  const historyDateRange = ref<[number, number] | null>(null);
  const anomalyType = ref(null);

  // 历史记录
  const historyList = ref([]);

  const historyColumns = [
    { title: '异常ID', key: 'id' },
    {
      title: '检测时间',
      key: 'detectionTime',
      render: (row: any) => formatTime(row.detectionTime),
    },
    {
      title: '异常参数',
      key: 'parameterName',
      render: (row: any) => getParamLabel(row.parameterName),
    },
    { title: '实际值', key: 'actualValue' },
    { title: '预期范围', key: 'expectedRange' },
    {
      title: '异常级别',
      key: 'level',
      render: (row: any) =>
        h(
          NTag,
          { type: levelTagType(row.level) },
          { default: () => levelLabel(row.level) }
        ),
    },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: statusTagType(row.status) },
          { default: () => statusLabel(row.status) }
        ),
    },
    {
      title: '处理人',
      key: 'handlerUser',
      render: (row: any) => (row.handlerUser === null || row.handlerUser === undefined ? '-' : String(row.handlerUser)),
    },
    {
      title: '操作',
      key: 'action',
      render: (row: any) =>
        h(
          NButton,
          { size: 'small', onClick: () => openDetailModal(row) },
          { default: () => '查看详情' }
        ),
    },
  ];

  // 处理异常相关
  const handleModalVisible = ref(false);
  const detailModalVisible = ref(false);
  const currentHandleRow = ref<any>(null);
  const currentDetailRow = ref<any>(null);
  const sourceData = ref<any>(null);
  const loadingSourceData = ref(false);
  const handleForm = reactive({
    handlerUser: '',
    status: 'RESOLVED' as UiStatus,
    handlerContent: '',
  });

  // 方法
  const getParamLabel = (param: string) => {
    const option = paramOptions.find((opt) => opt.value === param);
    return option ? option.label : param;
  };

  const formatTime = (time: string) => {
    if (!time) return '';
    try {
      const date = new Date(time);
      return format(date, 'yyyy-MM-dd HH:mm:ss');
    } catch (e) {
      return time.replace('T', ' ').substring(0, 19);
    }
  };

  const loadStatistics = async () => {
    try {
      const res: any = await anomalyApi.getStatistics({ furnaceId: selectedFurnace.value });
      if (res.code === 200) {
        currentAnomalyCount.value = res.data.currentAnomalyCount;
        todayAnomalyCount.value = res.data.todayAnomalyCount;
        processingRate.value = res.data.processingRate;
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadRealtimeData = async () => {
    loadingRealtime.value = true;
    try {
      const res: any = await anomalyApi.getRealtimeAnomalies({
        furnaceId: selectedFurnace.value,
        status: 'PENDING,PROCESSING',
        page: 0,
        size: 500,
      });
      if (res.code === 200) {
        anomalyList.value = res.data.content;
        currentAnomalyCount.value = res.data.totalElements;
        await hydrateSourceCache(anomalyList.value);
      }
    } catch (e) {
      message.error('加载实时数据失败');
    } finally {
      loadingRealtime.value = false;
    }
  };

  const syncScheduleStatus = async () => {
    try {
      const res: any = await anomalyApi.getScheduleStatus();
      const data = res?.data || {};
      isScheduledRunning.value = Boolean(data.running);
      lastDetectionTime.value = data?.lastRunTime ? formatTime(data.lastRunTime) : lastDetectionTime.value;
      lastBatchId.value = data?.lastBatchId ? String(data.lastBatchId) : lastBatchId.value;
    } catch (_) {
      return;
    }
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
      const refreshSec = Number(configMap.get('system_ui_refresh_seconds'));
      if (Number.isFinite(refreshSec)) {
        uiRefreshSeconds.value = Math.max(1, refreshSec);
      }
    } catch (_) {
      return;
    }
  };

  const getRefreshInterval = () =>
    isScheduledRunning.value || detectionMode.value === 'scheduled' ? FAST_REFRESH_MS.value : SLOW_REFRESH_MS.value;

  const restartRefreshLoop = () => {
    stopRefreshLoop();
    startRefreshLoop();
  };

  const startRefreshLoop = () => {
    if (refreshTimer) {
      return;
    }
    const tick = async () => {
      await syncScheduleStatus();
      await Promise.all([loadStatistics(), loadRealtimeData()]);
      refreshTimer = setTimeout(tick, getRefreshInterval());
    };
    refreshTimer = setTimeout(tick, 0);
  };

  const stopRefreshLoop = () => {
    if (!refreshTimer) {
      return;
    }
    clearTimeout(refreshTimer);
    refreshTimer = null;
  };

  const hydrateSourceCache = async (rows: AnomalyRecord[]) => {
    const ids = Array.from(
      new Set(
        (rows || [])
          .map((r) => r?.relatedDataId)
          .filter((v) => typeof v === 'number' && v > 0)
      )
    ) as number[];

    const missing = ids.filter((id) => !sourceCache.value[id]);
    if (missing.length === 0) return;

    await Promise.all(
      missing.map(async (id) => {
        try {
          const res: any = await dataManagementApi.getDataDetail(id);
          if (res.code === 200) {
            sourceCache.value[id] = res.data;
          }
        } catch (_) {
          return;
        }
      })
    );
  };

  const realtimeGroupedList = computed(() => {
    const groups: Record<string, any> = {};
    for (const row of anomalyList.value) {
      const relatedDataId = row?.relatedDataId;
      const key =
        typeof relatedDataId === 'number' && relatedDataId > 0
          ? String(relatedDataId)
          : `${row?.furnaceId || ''}-${row?.detectionTime || ''}`;
      if (!groups[key]) {
        const cached = typeof relatedDataId === 'number' ? sourceCache.value[relatedDataId] : null;
        groups[key] = {
          groupKey: key,
          furnaceId: row?.furnaceId,
          relatedDataId: relatedDataId,
          productionTime: cached?.timestamp,
          detectionTime: row?.detectionTime,
          anomalyCount: 0,
          parameters: [] as string[],
          status: 'CLOSED' as UiStatus,
          rows: [] as any[],
        };
      }
      groups[key].rows.push(row);
      groups[key].anomalyCount += 1;
      if (row?.parameterName) groups[key].parameters.push(row.parameterName);

      const s = normalizeStatus(row?.status);
      if (s === 'PENDING') {
        groups[key].status = 'PENDING';
      } else if (s === 'PROCESSING' && groups[key].status !== 'PENDING') {
        groups[key].status = 'PROCESSING';
      } else if (s === 'RESOLVED' && groups[key].status === 'CLOSED') {
        groups[key].status = 'RESOLVED';
      }
    }

    return Object.values(groups)
      .map((g: any) => {
        const uniqueParams = Array.from(new Set<string>(g.parameters as string[]));
        return {
          ...g,
          parametersLabel: uniqueParams.map((p) => getParamLabel(p)).join('、'),
        };
      })
      .sort((a: any, b: any) => {
        const ta = new Date(a.productionTime || a.detectionTime || 0).getTime();
        const tb = new Date(b.productionTime || b.detectionTime || 0).getTime();
        return tb - ta;
      });
  });

  const configModalRef = ref<any>(null);

  const executeDetection = async () => {
    detecting.value = true;
    try {
      const res: any = await anomalyApi.detect({
        furnaceId: selectedFurnace.value,
        detectionMode: detectionMode.value,
        params: selectedParams.value,
        algorithm: selectedAlgorithm.value,
      });
      if (res.code === 200) {
        const data = res?.data || {};
        if (data?.runAt) {
          lastDetectionTime.value = formatTime(data.runAt);
        } else {
          lastDetectionTime.value = formatTime(new Date().toISOString());
        }
        if (data?.batchId) {
          lastBatchId.value = String(data.batchId);
        }
        if (!isScheduledRunning.value) {
          message.success('检测完成');
        }
        loadStatistics();
        loadRealtimeData();
      } else {
        message.error(res.msg || '检测失败');
      }
    } catch (e) {
      message.error('检测请求失败');
    } finally {
      detecting.value = false;
    }
  };

  const stopScheduled = async () => {
    await anomalyApi.stopSchedule();
    isScheduledRunning.value = false;
    restartRefreshLoop();
  };

  const toggleDetection = async () => {
    if (isScheduledRunning.value) {
      await stopScheduled();
      message.info('定时检测已停止');
      return;
    }

    if (detectionMode.value === 'scheduled') {
      const res: any = await anomalyApi.startSchedule({
        furnaceId: selectedFurnace.value,
        params: selectedParams.value,
        algorithm: selectedAlgorithm.value,
        scheduleIntervalSeconds: Math.max(5, uiRefreshSeconds.value),
        batchSize: 50,
      });
      if (res?.code === 200) {
        isScheduledRunning.value = true;
        message.success('后端定时检测已启动');
        restartRefreshLoop();
      } else {
        message.error(res?.msg || '定时检测启动失败');
      }
      return;
    }

    await executeDetection();
  };

  const handleModeChange = () => {
    if (isScheduledRunning.value) {
      stopScheduled().then(() => {
        message.info('检测模式已切换，定时任务自动停止');
      });
    }
  };

  onUnmounted(() => {
    stopRefreshLoop();
  });

  const resetDetection = () => {
    selectedFurnace.value = defaultFurnaceId.value;
    detectionMode.value = 'realtime';
    selectedAlgorithm.value = 'ALL';
    selectedParams.value = [
      'temperature',
      'pressure',
      'materialHeight',
      'gasFlow',
      'oxygenLevel',
      'productionRate',
      'energyConsumption',
      'hotMetalTemperature',
      'siliconContent',
    ];
    message.success('重置检测配置成功');
  };

  const testAnomaly = async () => {
    try {
      // 生成一个明显的异常数据
      const mockData = {
        furnaceId: selectedFurnace.value,
        timestamp: new Date().toISOString(),
        temperature: 1500, // 异常高温 (正常 < 1300)
        pressure: 50, // 异常低压 (正常 > 150)
        materialHeight: 4.0,
        gasFlow: 3000,
        oxygenLevel: 23,
        productionRate: 50,
        energyConsumption: 600,
        hotMetalTemperature: 1600,
        siliconContent: 0.5,
        status: '模拟异常',
      };

      const res: any = await dataManagementApi.submitData(mockData);
      if (res.code === 200) {
        message.success('已注入一条测试异常数据，请点击“开始检测”');
      } else {
        message.error('注入失败');
      }
    } catch (e) {
      message.error('注入失败');
    }
  };

  const injectBatchData = async () => {
    try {
      message.loading('正在注入批量测试数据...');
      const baseTime = new Date().getTime();
      // Generate normal distribution data with some noise
      for (let i = 0; i < 30; i++) {
        const mockData = {
          furnaceId: selectedFurnace.value,
          timestamp: new Date(baseTime - (30 - i) * 60000).toISOString(), // 1 min interval
          temperature: 1250 + Math.random() * 20, // Normal range 1200-1300
          pressure: 175 + Math.random() * 10, // Normal 150-200
          materialHeight: 5.0 + Math.random() * 0.2, // Normal 4.5-5.5
          gasFlow: 2500 + Math.random() * 500,
          oxygenLevel: 23 + Math.random(),
          productionRate: 50,
          energyConsumption: 600,
          hotMetalTemperature: 1480 + (Math.random() - 0.5) * 10,
          siliconContent: 0.4 + Math.random() * 0.2, // Normal 0.1-1.0
          status: '批量测试数据',
        };
        await dataManagementApi.submitData(mockData);
      }

      // Add one anomaly at the end
      const anomalyData = {
        furnaceId: selectedFurnace.value,
        timestamp: new Date().toISOString(),
        temperature: 1290, // Within threshold but maybe high Z-score if variance was low?
        pressure: 220, // Threshold anomaly (> 200)
        materialHeight: 5.0,
        gasFlow: 2500,
        oxygenLevel: 23,
        productionRate: 50,
        energyConsumption: 600,
        hotMetalTemperature: 1600,
        siliconContent: 1.5, // Threshold anomaly (> 1.0)
        status: '批量测试异常',
      };
      await dataManagementApi.submitData(anomalyData);

      message.success('批量数据注入完成，请点击“开始检测”测试统计算法');
    } catch (e) {
      message.error('注入失败');
    }
  };

  const queryHistory = async () => {
    loadingHistory.value = true;
    try {
      const params: any = {
        furnaceId: selectedFurnace.value,
        parameterName: anomalyType.value,
      };

      // I'll skip parameter filtering for now as backend doesn't support it in the code I wrote.
      // I'll just filter by time.
      if (historyDateRange.value) {
        params.startTime = new Date(historyDateRange.value[0]).toISOString();
        params.endTime = new Date(historyDateRange.value[1]).toISOString();
      }

      const res: any = await anomalyApi.getHistoryAnomalies(params);
      if (res.code === 200) {
        historyList.value = res.data.content;
      }
    } catch (e) {
      message.error('查询历史记录失败');
    } finally {
      loadingHistory.value = false;
    }
  };

  const openHandleModal = (row: any) => {
    currentHandleRow.value = row;
    handleForm.status = normalizeStatus(row?.status);
    handleForm.handlerUser = row?.handlerUser !== null && row?.handlerUser !== undefined ? String(row.handlerUser) : '';
    handleForm.handlerContent = row?.handlerContent || '';
    handleModalVisible.value = true;
  };

  const openDetailModal = async (row: any) => {
    currentDetailRow.value = row;
    detailModalVisible.value = true;
    sourceData.value = null;

    if (row.relatedDataId) {
      loadingSourceData.value = true;
      try {
        const res: any = await dataManagementApi.getDataDetail(row.relatedDataId);
        if (res.code === 200) {
          sourceData.value = res.data;
        }
      } catch (e) {
        message.error('获取关联原始数据失败');
      } finally {
        loadingSourceData.value = false;
      }
    }
  };

  const openGroupDetailModal = async (groupRow: any) => {
    groupAnomalyList.value = groupRow?.rows || [];
    groupSourceData.value = null;
    groupDetailModalVisible.value = true;

    const relatedDataId = groupRow?.relatedDataId;
    if (typeof relatedDataId !== 'number' || relatedDataId <= 0) return;

    if (sourceCache.value[relatedDataId]) {
      groupSourceData.value = sourceCache.value[relatedDataId];
      return;
    }

    groupLoading.value = true;
    try {
      const res: any = await dataManagementApi.getDataDetail(relatedDataId);
      if (res.code === 200) {
        sourceCache.value[relatedDataId] = res.data;
        groupSourceData.value = res.data;
      }
    } finally {
      groupLoading.value = false;
    }
  };

  const submitHandle = async () => {
    if (!currentHandleRow.value) return;
    try {
      const handlerUser =
        handleForm.handlerUser && String(handleForm.handlerUser).trim() !== ''
          ? String(handleForm.handlerUser).trim()
          : undefined;
      const res: any = await anomalyApi.handleAnomaly(currentHandleRow.value.id, {
        status: handleForm.status,
        handlerUser,
        handlerContent: handleForm.handlerContent,
      });
      if (res.code === 200) {
        message.success('处理成功');
        handleModalVisible.value = false;
        currentHandleRow.value.status = handleForm.status;
        currentHandleRow.value.handlerUser = handlerUser ? Number(handlerUser) : currentHandleRow.value.handlerUser;
        currentHandleRow.value.handlerContent = handleForm.handlerContent;
        currentHandleRow.value.handleTime = new Date().toISOString();
        loadRealtimeData();
        loadStatistics();
      } else {
        message.error(res.msg || '处理失败');
      }
    } catch (e) {
      message.error('处理失败');
    }
  };

  const activeTab = ref<'realtime' | 'analysis' | 'history'>('realtime');
  const handleTabChange = async (value: 'realtime' | 'analysis' | 'history') => {
    if (activeTab.value === 'analysis' && value !== 'analysis') {
      disposeDistributionChart();
      disposeTrendChart();
    }

    activeTab.value = value;

    if (value === 'analysis') {
      await nextTick();
      disposeDistributionChart();
      disposeTrendChart();
      await updateCharts();
      await nextTick();
      resizeDistributionChart();
      resizeTrendChart();
      return;
    }

    if (value === 'history') {
      queryHistory();
    }
  };

  const updateCharts = async () => {
    try {
      const res: any = await anomalyApi.getChartData();
      if (res.code === 200) {
        const { distribution, trend } = res.data;

        setDistributionOptions({
          tooltip: {
            trigger: 'item',
          },
          legend: {
            top: '5%',
            left: 'center',
          },
          series: [
            {
              name: '异常类型',
              type: 'pie',
              radius: ['40%', '70%'],
              avoidLabelOverlap: false,
              itemStyle: {
                borderRadius: 10,
                borderColor: '#fff',
                borderWidth: 2,
              },
              label: {
                show: false,
                position: 'center',
              },
              emphasis: {
                label: {
                  show: true,
                  fontSize: 20,
                  fontWeight: 'bold',
                },
              },
              labelLine: {
                show: false,
              },
              data: distribution,
            },
          ],
        });

        setTrendOptions({
          xAxis: {
            type: 'category',
            data: trend.dates,
          },
          yAxis: {
            type: 'value',
          },
          series: [
            {
              data: trend.counts,
              type: 'line',
              smooth: true,
            },
          ],
        });
      }
    } catch (e) {
      console.error('Failed to load chart data', e);
    }
  };

  onMounted(async () => {
    await loadSystemDefaults();
    loadStatistics();
    loadRealtimeData();
    startRefreshLoop();
    syncScheduleStatus();
  });
</script>

<style lang="less" scoped>
  .anomaly-detection {
    padding: 20px;
  }

  .anomaly-main-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .anomaly-panel {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .anomaly-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .anomaly-alert {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
  }

  .anomaly-form :deep(.n-base-selection),
  .anomaly-form :deep(.n-input),
  .anomaly-form :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>


