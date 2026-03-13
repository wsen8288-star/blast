<template>
  <div class="home-portal min-h-[calc(100vh-80px)] p-4 lg:p-6">
    <div class="flex flex-col gap-6">
      <n-card
        size="small"
        :bordered="false"
        class="rounded-xl shadow-lg bg-gradient-to-r from-blue-500/10 to-cyan-500/10 border border-blue-500/20"
      >
        <div class="flex items-center justify-between gap-4">
          <div class="min-w-0">
            <div
              class="text-xl font-bold tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-blue-500 to-cyan-500"
            >
              高炉生产参数优化系统
            </div>
            <div class="mt-1 text-sm opacity-70">
              数据采集、监控预警、分析建模与参数优化一体化
            </div>
          </div>
          <div class="flex gap-2 shrink-0">
            <n-button type="primary" @click="go('/blast-furnace/data-management/data-collection')">
              去数据采集
            </n-button>
            <n-button @click="go('/blast-furnace/optimization/model-training')">去模型训练</n-button>
          </div>
        </div>
      </n-card>

      <n-grid :x-gap="12" :y-gap="12" cols="1 s:2 m:4" responsive="screen">
        <n-grid-item v-for="item in menuItems" :key="item.path">
          <n-card
            size="small"
            :bordered="true"
            class="group rounded-xl hover:-translate-y-1 hover:border-primary hover:shadow-lg transition-all duration-300"
          >
            <n-statistic :label="item.label" :value="item.value" />
            <div class="mt-3">
              <n-button
                size="small"
                ghost
                type="primary"
                @click="go(item.path)"
              >
                打开
              </n-button>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-card
        size="small"
        title="快速开始"
        class="rounded-xl shadow-lg"
      >
        <n-steps size="small" :current="quickStartCurrent">
          <n-step title="采集数据" :description="quickStartDesc.collect" />
          <n-step title="预处理" :description="quickStartDesc.preprocess" />
          <n-step title="训练模型" :description="quickStartDesc.train" />
          <n-step title="参数优化" :description="quickStartDesc.optimize" />
        </n-steps>
        <div class="mt-3 flex items-center gap-2">
          <n-button size="small" type="primary" @click="continueQuickStart">继续本轮</n-button>
          <n-button size="small" secondary @click="startNewQuickStart">开始新一轮</n-button>
        </div>
      </n-card>

      <n-card
        size="small"
        title="系统运行态势概览"
        class="rounded-xl shadow-lg"
      >
        <n-grid :x-gap="12" :y-gap="12" cols="2 s:4 m:4" responsive="screen">
          <n-grid-item><n-statistic label="未处理预警(严重)" :value="severeCount" /></n-grid-item>
          <n-grid-item><n-statistic label="未处理预警(警告)" :value="warningCount" /></n-grid-item>
          <n-grid-item><n-statistic label="提示预警" :value="tipCount" /></n-grid-item>
          <n-grid-item><n-statistic label="采集任务" :value="collectionStatusText" /></n-grid-item>
          <n-grid-item><n-statistic label="存储使用率" :value="storageUsageText" /></n-grid-item>
          <n-grid-item><n-statistic label="存储剩余" :value="storageRemainingText" /></n-grid-item>
          <n-grid-item><n-statistic label="异常检测任务" :value="anomalyScheduleText" /></n-grid-item>
          <n-grid-item><n-statistic label="最近刷新" :value="lastRefreshText" /></n-grid-item>
        </n-grid>
      </n-card>

      <n-grid :x-gap="16" :y-gap="16" cols="1 m:10" responsive="screen">
        <n-grid-item span="1 m:6">
          <n-card
            size="small"
            title="系统实时预警日志"
            class="rounded-xl shadow-lg"
          >
            <n-spin :show="logsLoading">
              <n-empty v-if="!logsLoading && logs.length === 0" description="暂无预警日志" />
              <n-timeline v-else>
                <n-timeline-item
                  v-for="log in logs"
                  :key="log.id"
                  :type="log.type"
                  :content="log.content"
                  :time="log.time"
                />
              </n-timeline>
            </n-spin>
          </n-card>
        </n-grid-item>

        <n-grid-item span="1 m:4">
          <n-card
            size="small"
            title="核心算法支撑与参考文献"
            class="rounded-xl shadow-lg"
          >
            <n-list hoverable clickable>
              <n-list-item v-for="(paper, index) in papers" :key="index" @click="viewPaper(paper)">
                <div class="flex items-center justify-between">
                  <div class="text-sm opacity-90 truncate mr-2">
                    [{{ index + 1 }}] {{ paper.title }}
                  </div>
                  <n-button text type="primary" size="tiny" class="shrink-0">查看</n-button>
                </div>
              </n-list-item>
            </n-list>
          </n-card>
        </n-grid-item>
      </n-grid>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import {
    NButton, NCard, NGrid, NGridItem, NSteps, NStep, NStatistic,
    NTimeline, NTimelineItem, NList, NListItem, useMessage, NEmpty, NSpin,
  } from 'naive-ui';
  import { anomalyApi, collectionApi, dataManagementApi, homeApi, warningApi } from '@/api/blast-furnace';
  import { clearQuickStartRun, ensureQuickStartRunId, getQuickStartRun } from '@/utils/quickStartRun';

  const router = useRouter();
  const message = useMessage();
  const go = (path: string) => router.push(path);

  // 菜单数据抽离
  const menuItems = [
    { label: '推荐入口', value: '数据采集', path: '/blast-furnace/data-management/data-collection' },
    { label: '监控预警', value: '实时监控', path: '/blast-furnace/monitoring/real-time-monitoring' },
    { label: '数据分析', value: '相关性分析', path: '/blast-furnace/analysis/correlation-analysis' },
    { label: '优化建模', value: '模型训练', path: '/blast-furnace/optimization/model-training' },
  ];
  const goQuickStart = (runId: string) =>
    router.push({ path: '/blast-furnace/data-management/data-collection', query: { runId } });

  interface LogItem {
    id: string | number;
    type: 'warning' | 'info' | 'success' | 'error' | 'default';
    content: string;
    time: string;
  }

  const logs = ref<LogItem[]>([]);
  const logsLoading = ref(false);

  const tipCount = ref(0);
  const warningCount = ref(0);
  const severeCount = ref(0);

  const collectionRunning = ref(false);
  const collectionTaskCount = ref(0);

  const storageUsage = ref<number | null>(null);
  const storageRemaining = ref<number | null>(null);

  const anomalyScheduleRunning = ref<boolean | null>(null);

  const lastRefreshAt = ref<Date | null>(null);
  const quickStartProgress = ref<any>(null);

  const statusLabel = (status: any) => {
    if (status === 'completed') return '已完成';
    if (status === 'running') return '进行中';
    if (status === 'failed') return '失败';
    return '未开始';
  };

  const quickStartCurrent = computed(() => {
    const current = Number(quickStartProgress.value?.current ?? 1);
    if (!Number.isFinite(current) || current < 1) return 1;
    return Math.min(Math.max(current, 1), 4);
  });

  const quickStartDesc = computed(() => {
    const collect = quickStartProgress.value?.collect?.status;
    const preprocess = quickStartProgress.value?.preprocess?.status;
    const train = quickStartProgress.value?.train?.status;
    const optimize = quickStartProgress.value?.optimize?.status;
    return {
      collect: `启动采集任务，积累生产数据（${statusLabel(collect)}）`,
      preprocess: `缺失处理、异常剔除、标准化（${statusLabel(preprocess)}）`,
      train: `选择目标与特征，训练并评估（${statusLabel(train)}）`,
      optimize: `根据模型输出进行优化决策（${statusLabel(optimize)}）`,
    };
  });

  const formatHm = (value: any) => {
    if (!value) return '';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value).replace('T', ' ');
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
  };

  const warningLevelToType = (level: any): LogItem['type'] => {
    if (String(level || '') === '严重') return 'error';
    if (String(level || '') === '警告') return 'warning';
    return 'info';
  };

  const buildWarningText = (row: any) => {
    const level = row?.level ? `[${row.level}] ` : '';
    const furnace = row?.furnaceId ? `${row.furnaceId} ` : '';
    const param = row?.parameterName ? `${row.parameterName}` : '';
    const val = row?.actualValue != null ? `=${row.actualValue}` : '';
    const range = row?.expectedRange ? ` (${row.expectedRange})` : '';
    const desc = row?.description ? ` - ${row.description}` : '';
    return `${level}${furnace}${param}${val}${range}${desc}`.trim() || '预警事件';
  };

  const fetchWarningLogs = async () => {
    logsLoading.value = true;
    try {
      const res: any = await warningApi.getList({ page: 0, size: 6 });
      const page = res?.data;
      const rows = Array.isArray(page?.content) ? page.content : [];
      logs.value = rows.map((row: any) => ({
        id: row?.id ?? `${row?.furnaceId ?? 'BF'}-${row?.detectionTime ?? Math.random()}`,
        type: warningLevelToType(row?.level),
        content: buildWarningText(row),
        time: formatHm(row?.detectionTime),
      }));
    } catch (_) {
      logs.value = [];
    } finally {
      logsLoading.value = false;
    }
  };

  const fetchOverview = async () => {
    try {
      const res: any = await warningApi.getStats();
      tipCount.value = Number(res?.data?.tipCount ?? 0);
      warningCount.value = Number(res?.data?.warningCount ?? 0);
      severeCount.value = Number(res?.data?.severeCount ?? 0);
    } catch (_) {
      tipCount.value = 0;
      warningCount.value = 0;
      severeCount.value = 0;
    }

    try {
      const res: any = await collectionApi.getStatus();
      collectionRunning.value = Boolean(res?.data?.isRunning);
      collectionTaskCount.value = Number(res?.data?.taskCount ?? 0);
    } catch (_) {
      collectionRunning.value = false;
      collectionTaskCount.value = 0;
    }

    try {
      const res: any = await dataManagementApi.getStorageStatus();
      const usage = res?.data?.usedStoragePercentage;
      const remaining = res?.data?.remainingStorage;
      storageUsage.value = usage == null ? null : Number(usage);
      storageRemaining.value = remaining == null ? null : Number(remaining);
    } catch (_) {
      storageUsage.value = null;
      storageRemaining.value = null;
    }

    try {
      const res: any = await anomalyApi.getScheduleStatus();
      anomalyScheduleRunning.value = Boolean(res?.data?.running);
    } catch (_) {
      anomalyScheduleRunning.value = null;
    }
  };

  const fetchQuickStartProgress = async () => {
    try {
      const res: any = await homeApi.getQuickStartProgress({ hours: 24 });
      quickStartProgress.value = res?.data || null;
    } catch (_) {
      quickStartProgress.value = null;
    }
  };

  const continueQuickStart = () => {
    const run = getQuickStartRun(24);
    const runId = run?.runId || ensureQuickStartRunId(24);
    goQuickStart(runId);
  };

  const startNewQuickStart = () => {
    clearQuickStartRun();
    const runId = ensureQuickStartRunId(24);
    goQuickStart(runId);
  };

  let refreshTimer: ReturnType<typeof setInterval> | undefined;
  const refreshing = ref(false);

  const refreshAll = async () => {
    if (refreshing.value) return;
    refreshing.value = true;
    await Promise.allSettled([fetchWarningLogs(), fetchOverview(), fetchQuickStartProgress()]);
    lastRefreshAt.value = new Date();
    refreshing.value = false;
  };

  onMounted(() => {
    refreshAll();
    refreshTimer = setInterval(refreshAll, 15000);
  });

  onBeforeUnmount(() => {
    if (refreshTimer) clearInterval(refreshTimer);
  });

  // 2. 参考文献相关逻辑
  const papers = ref([
    {
      title: '基于进化算法的复杂工业过程多目标优化研究 (《自动化学报》)',
      url: 'https://xueshu.baidu.com/s?wd=基于进化算法的复杂工业过程多目标优化研究',
    },
    {
      title: '数据驱动的高炉铁水质量预测与智能控制技术',
      url: 'https://xueshu.baidu.com/s?wd=数据驱动的高炉铁水质量预测与智能控制技术',
    },
    {
      title: '高炉冶炼过程关键参数动态建模方法',
      url: 'https://xueshu.baidu.com/s?wd=高炉冶炼过程关键参数动态建模方法',
    },
  ]);

  const viewPaper = (paper: any) => {
    const opened = window.open(paper.url, '_blank');
    if (opened) {
      message.success('已在新窗口打开');
      return;
    }
    message.warning('浏览器拦截了弹窗，请允许后重试');
  };

  const collectionStatusText = computed(() => {
    if (!collectionRunning.value) return '未运行';
    const count = Number(collectionTaskCount.value ?? 0);
    return count > 0 ? `运行中(${count})` : '运行中';
  });

  const storageUsageText = computed(() => {
    if (storageUsage.value == null || Number.isNaN(storageUsage.value)) return '-';
    return `${storageUsage.value.toFixed(1)}%`;
  });

  const storageRemainingText = computed(() => {
    if (storageRemaining.value == null || Number.isNaN(storageRemaining.value)) return '-';
    return `${storageRemaining.value.toFixed(0)} GB`;
  });

  const anomalyScheduleText = computed(() => {
    if (anomalyScheduleRunning.value == null) return '-';
    return anomalyScheduleRunning.value ? '运行中' : '未运行';
  });

  const lastRefreshText = computed(() => {
    if (!lastRefreshAt.value) return '-';
    return formatHm(lastRefreshAt.value);
  });
</script>


