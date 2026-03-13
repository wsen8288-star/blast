<template>
  <div class="monitor-shell relative overflow-hidden rounded-xl bg-slate-950 text-slate-100">
    <div
      class="pointer-events-none absolute inset-0 opacity-70 [background-image:radial-gradient(circle_at_12%_18%,rgba(34,211,238,0.18),transparent_42%),radial-gradient(circle_at_86%_24%,rgba(167,139,250,0.16),transparent_40%),radial-gradient(circle_at_50%_92%,rgba(16,185,129,0.12),transparent_45%)]"
    ></div>
    <div
      class="pointer-events-none absolute inset-0 opacity-25 [background-image:linear-gradient(rgba(148,163,184,0.10)_1px,transparent_1px),linear-gradient(90deg,rgba(148,163,184,0.10)_1px,transparent_1px)] [background-size:56px_56px]"
    ></div>
    <div class="relative z-10 min-h-[calc(100vh-120px)] px-4 py-4 lg:px-6 lg:py-5">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex items-center gap-3">
          <div
            class="h-10 w-10 rounded-lg border border-cyan-400/25 bg-slate-900/60 shadow-[0_0_28px_rgba(34,211,238,0.10)]"
          >
            <div
              class="h-full w-full rounded-lg [background-image:linear-gradient(135deg,rgba(34,211,238,0.22),rgba(167,139,250,0.10),rgba(15,23,42,0.10))]"
            ></div>
          </div>
          <div>
            <div class="text-lg font-semibold tracking-wide text-slate-100">实时监控大屏</div>
            <div class="text-xs text-slate-400">
              {{ selectedFurnace }} · {{ nowText }} · 刷新频率 {{ refreshHz }} Hz · 数据源 {{ dataSourceText }}
            </div>
          </div>
          <div
            class="ml-2 hidden items-center gap-2 rounded-full border px-3 py-1 text-xs lg:flex"
            :class="statusBadgeClass"
          >
            <span class="h-1.5 w-1.5 rounded-full" :class="statusDotClass"></span>
            <span class="tracking-wide">{{ statusText }}</span>
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <div
            class="rounded-lg border border-slate-700/60 bg-slate-900/50 px-3 py-2 shadow-[0_0_22px_rgba(148,163,184,0.06)]"
          >
            <n-select
              v-model:value="selectedFurnace"
              :options="furnaceOptions"
              size="small"
              placeholder="选择高炉"
              class="w-40"
            />
          </div>
          <n-button round size="small" :type="preferLive ? 'primary' : 'default'" @click="switchToLive">
            实时数据
          </n-button>
          <n-button round size="small" :type="preferLive ? 'default' : 'primary'" @click="switchToMock">
            演示模拟
          </n-button>
          <n-button round size="small" :type="effectiveStrictLive ? 'info' : 'warning'" @click="toggleStrictLive">
            {{ effectiveStrictLive ? '严格实时' : '宽松回滚' }}
          </n-button>
          <n-button round size="small" secondary @click="burstAnomaly">注入异常</n-button>
        </div>
      </div>
      <div v-if="preferLive && showFallbackOptions" class="mt-3 rounded-lg border border-amber-300/25 bg-amber-500/10 p-3">
        <div class="text-sm text-amber-100">实时链路连续失败，当前保持冻结显示，请选择后续动作</div>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <n-button round size="small" type="warning" @click="retryLive">立即重试</n-button>
          <n-button round size="small" @click="keepFrozen">保持冻结</n-button>
          <n-button round size="small" type="error" ghost @click="switchToMock">切换模拟</n-button>
        </div>
      </div>
      <div class="mt-3 rounded-lg border border-slate-700/50 bg-slate-900/45 px-3 py-2">
        <div class="text-xs text-slate-400">
          当前阈值来源优先级：炉号配置 → GLOBAL → DEFAULT（工业数据契约基线）
        </div>
        <div class="mt-2 flex flex-wrap gap-2">
          <n-tag
            v-for="item in thresholdSourceItems"
            :key="item.key"
            size="small"
            :bordered="false"
            :type="item.source === 'FURNACE' ? 'success' : item.source === 'GLOBAL' ? 'warning' : 'default'"
          >
            {{ item.label }}: {{ item.source }}
          </n-tag>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-4">
        <div class="relative overflow-hidden rounded-xl border border-slate-700/50 bg-slate-900/35 p-4">
          <div class="text-xs text-slate-400">铁水温度（预测）</div>
          <div class="mt-2 flex items-end gap-3">
            <div class="font-mono text-5xl font-semibold tracking-tight" :class="kpiGlowClass">
              {{ hotMetalTemp.toFixed(0) }}
            </div>
            <div class="pb-1 text-sm text-slate-400">℃</div>
          </div>
          <div class="mt-3 flex items-center justify-between text-xs">
            <div class="text-slate-400">阈值区间</div>
            <div class="text-slate-200">{{ hotMetalRangeText }}</div>
          </div>
          <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-800/70">
            <div class="h-full rounded-full" :class="kpiBarClass" :style="{ width: `${hotMetalTempPct}%` }"></div>
          </div>
        </div>

        <div class="relative overflow-hidden rounded-xl border border-slate-700/50 bg-slate-900/35 p-4">
          <div class="text-xs text-slate-400">含硅量（预测）</div>
          <div class="mt-2 flex items-end gap-3">
            <div class="font-mono text-5xl font-semibold tracking-tight" :class="kpiGlowClass">
              {{ siliconContent.toFixed(3) }}
            </div>
            <div class="pb-1 text-sm text-slate-400">%</div>
          </div>
          <div class="mt-3 flex items-center justify-between text-xs">
            <div class="text-slate-400">阈值区间</div>
            <div class="text-slate-200">{{ siliconRangeText }}</div>
          </div>
          <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-800/70">
            <div class="h-full rounded-full" :class="kpiBarClass" :style="{ width: `${siliconPct}%` }"></div>
          </div>
        </div>

        <div class="rounded-xl border border-slate-700/50 bg-slate-900/35 p-4">
          <div class="flex items-center justify-between">
            <div class="text-sm font-medium text-slate-200">风温/风压（当前）</div>
            <div class="text-xs text-slate-400">℃ / kPa</div>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3">
            <div class="rounded-xl border border-slate-700/40 bg-slate-950/35 px-4 py-3">
              <div class="text-xs text-slate-400">风温</div>
              <div class="mt-1 flex items-end gap-2">
                <div class="font-mono text-2xl text-slate-100">{{ windTemp.toFixed(0) }}</div>
                <div class="pb-0.5 text-xs text-slate-400">℃</div>
              </div>
            </div>
            <div class="rounded-xl border border-slate-700/40 bg-slate-950/35 px-4 py-3">
              <div class="text-xs text-slate-400">风压</div>
              <div class="mt-1 flex items-end gap-2">
                <div class="font-mono text-2xl text-slate-100">{{ windPressure.toFixed(0) }}</div>
                <div class="pb-0.5 text-xs text-slate-400">kPa</div>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-slate-700/50 bg-slate-900/35 p-4">
          <div class="flex items-center justify-between">
            <div class="text-sm font-medium text-slate-200">系统健康度</div>
            <div class="flex items-center gap-2">
              <n-tag size="small" :bordered="false" :type="dataSourceTagType">{{ dataSourceBadgeText }}</n-tag>
              <n-tag size="small" :bordered="false" :type="statusTagType">{{ statusText }}</n-tag>
            </div>
          </div>
          <div class="mt-3 flex items-end gap-2">
            <div class="font-mono text-5xl font-semibold tracking-tight" :class="healthGlowClass">
              {{ healthScore }}
            </div>
            <div class="pb-1 text-sm text-slate-400">/ 100</div>
          </div>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-12 gap-4">
        <div class="col-span-12 lg:col-span-9">
          <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div
              class="h-full rounded-xl border border-cyan-400/20 bg-slate-900/50 p-3 shadow-[0_0_30px_rgba(34,211,238,0.08)]"
            >
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-200">实时风温趋势</div>
                <div class="text-xs text-slate-400">℃</div>
              </div>
              <div class="mt-2 h-[260px] w-full" ref="windTempChartRef"></div>
            </div>
            <div
              class="h-full rounded-xl border border-violet-400/20 bg-slate-900/50 p-3 shadow-[0_0_30px_rgba(167,139,250,0.08)]"
            >
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-200">实时风压波动</div>
                <div class="text-xs text-slate-400">kPa</div>
              </div>
              <div class="mt-2 h-[260px] w-full" ref="windPressureChartRef"></div>
            </div>
            <div
              class="h-full rounded-xl border border-emerald-400/18 bg-slate-900/50 p-3 shadow-[0_0_30px_rgba(16,185,129,0.08)]"
            >
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-200">实时风量趋势</div>
                <div class="text-xs text-slate-400">m³/h</div>
              </div>
              <div class="mt-2 h-[260px] w-full" ref="windVolumeChartRef"></div>
            </div>
            <div
              class="h-full rounded-xl border border-amber-300/18 bg-slate-900/50 p-3 shadow-[0_0_30px_rgba(251,191,36,0.10)]"
            >
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-200">实时喷煤量趋势</div>
                <div class="text-xs text-slate-400">kg/t</div>
              </div>
              <div class="mt-2 h-[260px] w-full" ref="coalInjectionChartRef"></div>
            </div>
          </div>
        </div>

        <div class="col-span-12 lg:col-span-3">
          <div
            class="flex h-full flex-col rounded-xl border border-rose-400/18 bg-slate-900/50 p-3 shadow-[0_0_30px_rgba(244,63,94,0.10)]"
          >
            <div class="flex items-center justify-between">
              <div class="text-sm font-medium text-slate-200">实时异常预警</div>
              <div class="text-xs text-slate-400">联动异常检测概念</div>
            </div>

            <div
              class="mt-3 flex flex-1 flex-col overflow-hidden rounded-xl border border-slate-700/40 bg-slate-950/40"
            >
              <div class="flex items-center justify-between border-b border-slate-700/40 px-3 py-2">
                <div class="text-xs text-slate-400">滚动列表</div>
                <div class="flex items-center gap-2 text-xs">
                  <span class="text-slate-400">队列</span>
                  <span class="font-mono text-slate-200">{{ alarmQueue.length }}</span>
                </div>
              </div>
              <div class="flex-1 overflow-hidden">
                <div
                  v-if="alarmQueue.length === 0"
                  class="flex h-full items-center justify-center px-6 text-center"
                >
                  <div class="space-y-2">
                    <div class="text-sm text-slate-200">当前无待处理异常</div>
                    <div class="text-xs text-slate-400">
                      {{ preferLive ? (collectionRunning ? '异常检测返回为空' : '未采集：异常列表已冻结') : '演示模式下将模拟预警' }}
                    </div>
                  </div>
                </div>
                <div
                  v-else
                  class="will-change-transform"
                  :class="tickerAnimating ? 'transition-transform duration-500 ease-out' : ''"
                  :style="{ transform: `translateY(${tickerOffset}px)` }"
                  @transitionend="onTickerTransitionEnd"
                >
                  <div
                    v-for="row in tickerRows"
                    :key="row.id"
                    class="flex h-16 items-center gap-3 border-b border-slate-800/60 px-3"
                  >
                    <div
                      class="h-9 w-9 shrink-0 rounded-lg border"
                      :class="rowBadgeClass(row.level)"
                    >
                      <div class="flex h-full w-full items-center justify-center font-mono text-xs">
                        {{ row.levelText }}
                      </div>
                    </div>
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center justify-between gap-2">
                        <div class="truncate text-sm text-slate-100">{{ row.title }}</div>
                        <div class="shrink-0 font-mono text-[11px] text-slate-400">{{ row.time }}</div>
                      </div>
                      <div class="mt-0.5 truncate text-xs text-slate-400">{{ row.detail }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="mt-4 grid grid-cols-3 gap-2">
              <div class="rounded-lg border border-slate-700/40 bg-slate-950/40 px-3 py-2">
                <div class="text-[11px] text-slate-400">紧急</div>
                <div class="mt-0.5 font-mono text-lg text-rose-300">{{ urgentCount }}</div>
              </div>
              <div class="rounded-lg border border-slate-700/40 bg-slate-950/40 px-3 py-2">
                <div class="text-[11px] text-slate-400">警告</div>
                <div class="mt-0.5 font-mono text-lg text-amber-200">{{ warnCount }}</div>
              </div>
              <div class="rounded-lg border border-slate-700/40 bg-slate-950/40 px-3 py-2">
                <div class="text-[11px] text-slate-400">提示</div>
                <div class="mt-0.5 font-mono text-lg text-cyan-200">{{ infoCount }}</div>
              </div>
            </div>
            <div class="mt-3 rounded-lg border border-slate-700/40 bg-slate-950/40 px-3 py-2">
              <div class="flex items-center justify-between text-[11px] text-slate-400">
                <span>数据源审计</span>
                <span class="font-mono text-slate-300">{{ sourceAuditList.length }}</span>
              </div>
              <div class="mt-2 space-y-1.5">
                <div v-if="sourceAuditList.length === 0" class="text-[11px] text-slate-500">暂无</div>
                <div v-for="item in sourceAuditList" :key="item.id" class="text-[11px]">
                  <span class="font-mono text-slate-400">{{ item.time }}</span>
                  <span class="ml-2 text-slate-300">{{ item.event }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
  import { anomalyApi, anomalyConfigApi, collectionApi, dataManagementApi, systemApi } from '@/api/blast-furnace';
  import * as echarts from 'echarts';

  type TrendPoint = { t: string; v: number };
  type AlarmLevel = 'urgent' | 'warning' | 'info';
  type AlarmRow = { id: string; time: string; level: AlarmLevel; title: string; detail: string };
  type AnomalyPlotPoint = { id: string; t: string; v: number; level: AlarmLevel };
  type ParamRange = { min: number; max: number; warningMin: number; warningMax: number; label: string; source: string };
  type SourceAuditItem = { id: string; time: string; event: string };

  const refreshHz = 1;
  const furnaceIds = ['BF-001', 'BF-002', 'BF-003'] as const;
  const furnaceOptions = furnaceIds.map((id) => ({ label: id, value: id }));
  const selectedFurnace = ref<(typeof furnaceIds)[number]>('BF-001');
  const preferLive = ref(true);
  const liveFailCount = ref(0);
  const showFallbackOptions = ref(false);
  const liveFrozen = ref(false);
  const latestSource = ref<'LIVE' | 'MOCK' | 'NO_LIVE_DATA'>('LIVE');
  const latestStale = ref(false);
  const strictLiveFromConfig = ref(true);
  const strictLiveOverride = ref<boolean | null>(null);
  const liveFailThreshold = ref(3);
  const anomalySyncDelayMs = ref(300);
  const liveOk = computed(() => preferLive.value && liveFailCount.value === 0);
  const effectiveStrictLive = computed(() =>
    strictLiveOverride.value === null ? strictLiveFromConfig.value : strictLiveOverride.value
  );
  const collectionRunning = ref(false);
  let collectionStatusTimer: number | null = null;
  const dataSourceText = computed(() => {
    if (!preferLive.value) return '演示模拟';
    if (latestSource.value === 'NO_LIVE_DATA') return '无实时数据';
    if (latestSource.value === 'MOCK') return '演示回退';
    if (liveFrozen.value) return '实时冻结';
    return collectionRunning.value ? '采集中' : '未采集(冻结)';
  });
  const dataSourceBadgeText = computed(() => {
    if (!preferLive.value) return '演示·模拟';
    if (latestSource.value === 'NO_LIVE_DATA') return '实时·无数据';
    if (latestSource.value === 'MOCK') return '演示·回退';
    if (latestStale.value) return '实时·陈旧';
    return collectionRunning.value ? '实时·采集中' : '实时·监控中';
  });
  const dataSourceTagType = computed(() =>
    !preferLive.value || latestSource.value === 'MOCK'
      ? 'warning'
      : latestSource.value === 'NO_LIVE_DATA' || latestStale.value
      ? 'error'
      : collectionRunning.value
      ? 'success'
      : 'info'
  );

  const windTempChartRef = ref<HTMLDivElement | null>(null);
  const windPressureChartRef = ref<HTMLDivElement | null>(null);
  const windVolumeChartRef = ref<HTMLDivElement | null>(null);
  const coalInjectionChartRef = ref<HTMLDivElement | null>(null);

  let windTempChart: echarts.ECharts | null = null;
  let windPressureChart: echarts.ECharts | null = null;
  let windVolumeChart: echarts.ECharts | null = null;
  let coalInjectionChart: echarts.ECharts | null = null;
  let chartResizeObserver: ResizeObserver | null = null;
  let runtimeTimer: number | null = null;
  let clockTimer: number | null = null;
  let tickerTimer: number | null = null;
  let anomalyRefreshTimer: number | null = null;

  const nowText = ref('');

  const windTempSeries = ref<TrendPoint[]>([]);
  const windPressureSeries = ref<TrendPoint[]>([]);
  const windVolumeSeries = ref<TrendPoint[]>([]);
  const coalInjectionSeries = ref<TrendPoint[]>([]);

  const windTemp = ref(1120);
  const windPressure = ref(190);
  const windVolume = ref(5000);
  const coalInjection = ref(150);
  const hotMetalTemp = ref(1485);
  const siliconContent = ref(0.46);

  const alarmQueue = ref<AlarmRow[]>([]);
  const sourceAuditList = ref<SourceAuditItem[]>([]);
  const paramLabels: Record<string, string> = {
    hotMetalTemperature: '铁水温度',
    siliconContent: '铁水含硅量',
    temperature: '温度',
    pressure: '压力',
    windVolume: '风量',
    coalInjection: '喷煤量',
  };
  const specRanges = ref<Record<string, ParamRange>>({});
  const tickerOffset = ref(0);
  const tickerAnimating = ref(true);
  const tickerItemHeight = 64;
  const tickerVisibleCount = 7;

  const clamp = (n: number, min: number, max: number) => Math.max(min, Math.min(max, n));
  const pad2 = (n: number) => String(n).padStart(2, '0');
  const formatClock = (d: Date) =>
    `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;

  const formatClockFromAny = (value: any) => {
    if (!value) return formatClock(new Date());
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return formatClock(new Date());
    return formatClock(d);
  };

  const seedSeries = (base: number, amplitude: number, count: number) => {
    const now = new Date();
    const out: TrendPoint[] = [];
    let v = base;
    for (let i = count - 1; i >= 0; i--) {
      const t = new Date(now.getTime() - i * 1000);
      v = v + (Math.random() - 0.5) * amplitude;
      out.push({ t: formatClock(t), v: Number(v.toFixed(2)) });
    }
    return out;
  };

  const makeId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const fallbackRange = (key: string): ParamRange => {
    const current =
      key === 'hotMetalTemperature'
        ? hotMetalTemp.value
        : key === 'siliconContent'
        ? siliconContent.value
        : key === 'temperature'
        ? windTemp.value
        : key === 'pressure'
        ? windPressure.value
        : key === 'windVolume'
        ? windVolume.value
        : key === 'coalInjection'
        ? coalInjection.value
        : 1;
    const center = Number.isFinite(current) ? Number(current) : 1;
    const absCenter = Math.abs(center);
    const span = absCenter > 0 ? absCenter * 0.2 : 1;
    const min = center - span;
    const max = center + span;
    const warningMin = center - span * 0.5;
    const warningMax = center + span * 0.5;
    return {
      min,
      max,
      warningMin,
      warningMax,
      label: paramLabels[key] || key,
      source: 'DEFAULT',
    };
  };
  const getRange = (key: string) => specRanges.value[key] || fallbackRange(key);
  const thresholdSourceItems = computed(() =>
    Object.keys(paramLabels).map((key) => ({
      key,
      label: paramLabels[key],
      source: getRange(key).source || 'DEFAULT',
    }))
  );
  const rangeText = (key: string, digits = 0) => {
    const r = getRange(key);
    if (!r) return '--';
    return `${r.warningMin.toFixed(digits)} ~ ${r.warningMax.toFixed(digits)}`;
  };

  const pushAlarm = (level: AlarmLevel, title: string, detail: string) => {
    const d = new Date();
    alarmQueue.value.unshift({
      id: makeId(),
      time: formatClock(d),
      level,
      title,
      detail,
    });
    if (alarmQueue.value.length > 60) alarmQueue.value.splice(60);
  };

  const pushSourceAudit = (event: string) => {
    sourceAuditList.value.unshift({
      id: makeId(),
      time: formatClock(new Date()),
      event,
    });
    if (sourceAuditList.value.length > 30) sourceAuditList.value.splice(30);
  };

  let sourceAuditState = '';
  const syncSourceAudit = () => {
    const state = [
      preferLive.value ? 'REALTIME' : 'MOCK',
      effectiveStrictLive.value ? 'STRICT' : 'LENIENT',
      latestSource.value,
      latestStale.value ? 'STALE' : 'FRESH',
      collectionRunning.value ? 'RUNNING' : 'STOPPED',
      liveFrozen.value ? 'FROZEN' : 'FLOWING',
    ].join('|');
    if (state === sourceAuditState) return;
    sourceAuditState = state;
    pushSourceAudit(state);
  };

  const loadRuntimePolicies = async () => {
    try {
      const res: any = await systemApi.config.getListByGroup('SYSTEM_CONFIG');
      const items: any[] = Array.isArray(res?.data) ? res.data : [];
      const map = new Map(items.map((item: any) => [String(item?.configKey || ''), item?.configValue]));
      const strictRaw =
        map.get('monitor_strict_live_enable') ??
        map.get('system_live_strict_mode') ??
        map.get('realtime_strict_live_enable');
      if (strictRaw !== undefined && strictRaw !== null) {
        const text = String(strictRaw).trim().toLowerCase();
        strictLiveFromConfig.value = text === 'true' || text === '1';
      }
      const failRaw =
        Number(
          map.get('monitor_live_fail_threshold') ??
            map.get('system_live_fail_threshold') ??
            map.get('realtime_live_fail_threshold')
        );
      if (Number.isFinite(failRaw)) {
        liveFailThreshold.value = Math.max(1, Math.floor(failRaw));
      }
      const delayRaw =
        Number(
          map.get('monitor_anomaly_sync_delay_ms') ??
            map.get('system_anomaly_sync_delay_ms') ??
            map.get('realtime_anomaly_sync_delay_ms')
        );
      if (Number.isFinite(delayRaw)) {
        anomalySyncDelayMs.value = Math.max(50, Math.floor(delayRaw));
      }
    } catch (_) {
      return;
    }
  };

  const toggleStrictLive = () => {
    strictLiveOverride.value = !effectiveStrictLive.value;
    pushSourceAudit(strictLiveOverride.value ? 'OVERRIDE_STRICT' : 'OVERRIDE_LENIENT');
    syncSourceAudit();
  };

  const isHotMetalOk = computed(() => {
    const r = getRange('hotMetalTemperature');
    return hotMetalTemp.value >= r.warningMin && hotMetalTemp.value <= r.warningMax;
  });
  const isSiliconOk = computed(() => {
    const r = getRange('siliconContent');
    return siliconContent.value >= r.warningMin && siliconContent.value <= r.warningMax;
  });
  const isWindTempOk = computed(() => {
    const r = getRange('temperature');
    return windTemp.value >= r.warningMin && windTemp.value <= r.warningMax;
  });
  const isWindPressureOk = computed(() => {
    const r = getRange('pressure');
    return windPressure.value >= r.warningMin && windPressure.value <= r.warningMax;
  });
  const hotMetalRangeText = computed(() => rangeText('hotMetalTemperature', 0));
  const siliconRangeText = computed(() => rangeText('siliconContent', 3));

  const statusText = computed(() => {
    if (preferLive.value && showFallbackOptions.value) return '链路异常';
    return urgentCount.value > 0 || !(isHotMetalOk.value && isSiliconOk.value) ? '异常' : '正常';
  });
  const statusTagType = computed(() => (statusText.value === '正常' ? 'success' : 'error'));
  const statusBadgeClass = computed(() =>
    statusText.value === '正常'
      ? 'border-emerald-400/25 bg-emerald-500/10 text-emerald-200'
      : 'border-rose-400/25 bg-rose-500/10 text-rose-200'
  );
  const statusDotClass = computed(() =>
    statusText.value === '正常' ? 'bg-emerald-300' : 'bg-rose-300'
  );

  const kpiGlowClass = computed(() =>
    statusText.value === '正常'
      ? 'text-cyan-200 drop-shadow-[0_0_18px_rgba(34,211,238,0.55)]'
      : 'text-rose-200 drop-shadow-[0_0_18px_rgba(244,63,94,0.55)]'
  );
  const kpiBarClass = computed(() =>
    statusText.value === '正常'
      ? 'bg-gradient-to-r from-cyan-400 to-emerald-300 shadow-[0_0_18px_rgba(34,211,238,0.35)]'
      : 'bg-gradient-to-r from-rose-500 to-amber-300 shadow-[0_0_18px_rgba(244,63,94,0.35)]'
  );

  const hotMetalTempPct = computed(() => {
    const r = getRange('hotMetalTemperature');
    return clamp(((hotMetalTemp.value - r.min) / (r.max - r.min || 1)) * 100, 0, 100);
  });
  const siliconPct = computed(() => {
    const r = getRange('siliconContent');
    return clamp(((siliconContent.value - r.min) / (r.max - r.min || 1)) * 100, 0, 100);
  });

  const loadResolvedRanges = async () => {
    try {
      const res: any = await anomalyConfigApi.getEffectiveThresholds({ furnaceId: selectedFurnace.value });
      const payload = res?.data;
      if (!payload || Array.isArray(payload)) return;
      const next: Record<string, ParamRange> = {};
      Object.keys(payload).forEach((key) => {
        const item = payload[key];
        const min = Number(item?.min);
        const max = Number(item?.max);
        const warningMin = Number(item?.warningMin);
        const warningMax = Number(item?.warningMax);
        if ([min, max, warningMin, warningMax].some((v) => Number.isNaN(v))) return;
        next[key] = {
          min,
          max,
          warningMin,
          warningMax,
          label: paramLabels[key] || key,
          source: String(item?.source || 'DEFAULT').toUpperCase(),
        };
      });
      if (Object.keys(next).length > 0) {
        specRanges.value = next;
      }
    } catch (error) {
      return;
    }
  };

  const healthScore = computed(() => {
    const parts = [
      isHotMetalOk.value ? 25 : 0,
      isSiliconOk.value ? 25 : 0,
      isWindTempOk.value ? 25 : 0,
      isWindPressureOk.value ? 25 : 0,
    ];
    return parts.reduce((a, b) => a + b, 0);
  });

  const healthGlowClass = computed(() =>
    healthScore.value >= 75
      ? 'text-emerald-200 drop-shadow-[0_0_14px_rgba(16,185,129,0.45)]'
      : healthScore.value >= 50
      ? 'text-cyan-200 drop-shadow-[0_0_14px_rgba(34,211,238,0.45)]'
      : 'text-rose-200 drop-shadow-[0_0_14px_rgba(244,63,94,0.45)]'
  );

  const buildAxis = () => ({
    axisLine: { lineStyle: { color: 'rgba(148,163,184,0.35)' } },
    axisTick: { show: false },
    axisLabel: { color: 'rgba(226,232,240,0.70)', fontFamily: 'ui-monospace, SFMono-Regular' },
    splitLine: { lineStyle: { color: 'rgba(148,163,184,0.10)' } },
  });

  const severeWarnings = ref<Record<string, { actual: number; min: number; max: number }>>({});
  const anomalyPlotPoints = ref<Record<string, AnomalyPlotPoint[]>>({});

  const parseRange = (raw: any) => {
    const s = String(raw ?? '').trim();
    if (!s) return null;
    const m = s.match(/(-?\d+(?:\.\d+)?)\s*[-~]\s*(-?\d+(?:\.\d+)?)/);
    if (m) {
      const min = Number(m[1]);
      const max = Number(m[2]);
      if (Number.isFinite(min) && Number.isFinite(max)) return { min: Math.min(min, max), max: Math.max(min, max) };
    }
    const b = s.match(/\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]/);
    if (b) {
      const min = Number(b[1]);
      const max = Number(b[2]);
      if (Number.isFinite(min) && Number.isFinite(max)) return { min: Math.min(min, max), max: Math.max(min, max) };
    }
    return null;
  };

  const computeBounds = (ys: number[], extra: number[] = []) => {
    const nums = [...ys, ...extra].filter((v) => Number.isFinite(v));
    if (!nums.length) return { min: 0, max: 1 };
    const min = Math.min(...nums);
    const max = Math.max(...nums);
    const span = Math.max(1e-6, max - min);
    const pad = span * 0.08;
    return { min: min - pad, max: max + pad };
  };

  const buildSevereMarkArea = (paramKey: string, xs: string[], ys: number[]) => {
    const sw = severeWarnings.value[paramKey];
    if (!sw) return undefined;
    if (!xs.length || !ys.length) return undefined;
    const bounds = computeBounds(ys, [sw.min, sw.max, sw.actual]);
    const isHigh = sw.actual > sw.max;
    const isLow = sw.actual < sw.min;
    if (!isHigh && !isLow) return undefined;
    const y0 = isHigh ? sw.max : bounds.min;
    const y1 = isHigh ? bounds.max : sw.min;
    return {
      silent: true,
      itemStyle: { color: 'rgba(239,68,68,0.16)' },
      data: [[{ xAxis: xs[0], yAxis: y0 }, { xAxis: xs[xs.length - 1], yAxis: y1 }]],
    };
  };

  const buildAnomalyMarkPoints = (paramKey: string, xs: string[]) => {
    const points = anomalyPlotPoints.value[paramKey] || [];
    if (!points.length) return undefined;
    const allowedX = new Set(xs);
    const colorByLevel: Record<AlarmLevel, string> = {
      urgent: '#f43f5e',
      warning: '#fbbf24',
      info: '#22d3ee',
    };
    const data = points
      .filter((p) => Number.isFinite(p.v))
      .map((p) => ({
        name: p.id,
        coord: [allowedX.has(p.t) ? p.t : xs[xs.length - 1], p.v],
        itemStyle: { color: colorByLevel[p.level] || '#22d3ee' },
      }));
    if (!data.length) return undefined;
    return {
      symbol: 'circle',
      symbolSize: 9,
      data,
      label: { show: false },
    };
  };

  const setWindTempOption = () => {
    if (!windTempChart) return;
    const xs = windTempSeries.value.map((p) => p.t);
    const ys = windTempSeries.value.map((p) => p.v);
    windTempChart.setOption(
      {
        backgroundColor: 'transparent',
        grid: { left: 40, right: 16, top: 18, bottom: 28 },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(2,6,23,0.85)',
          borderColor: 'rgba(34,211,238,0.35)',
          textStyle: { color: '#e2e8f0' },
        },
        xAxis: {
          type: 'category',
          data: xs,
          boundaryGap: false,
          ...buildAxis(),
        },
        yAxis: {
          type: 'value',
          scale: true,
          ...buildAxis(),
        },
        series: [
          {
            type: 'line',
            data: ys,
            smooth: true,
            showSymbol: false,
            lineStyle: { width: 2, color: '#22d3ee' },
            markArea: buildSevereMarkArea('temperature', xs, ys),
            markPoint: buildAnomalyMarkPoints('temperature', xs),
            areaStyle: {
              opacity: 1,
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(34,211,238,0.25)' },
                { offset: 1, color: 'rgba(34,211,238,0.00)' },
              ]),
            },
          },
        ],
      },
      false
    );
  };

  const setWindPressureOption = () => {
    if (!windPressureChart) return;
    const xs = windPressureSeries.value.map((p) => p.t);
    const ys = windPressureSeries.value.map((p) => p.v);
    windPressureChart.setOption(
      {
        backgroundColor: 'transparent',
        grid: { left: 40, right: 16, top: 18, bottom: 28 },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(2,6,23,0.85)',
          borderColor: 'rgba(167,139,250,0.35)',
          textStyle: { color: '#e2e8f0' },
        },
        xAxis: {
          type: 'category',
          data: xs,
          boundaryGap: false,
          ...buildAxis(),
        },
        yAxis: {
          type: 'value',
          scale: true,
          ...buildAxis(),
        },
        series: [
          {
            type: 'line',
            data: ys,
            smooth: true,
            showSymbol: false,
            lineStyle: { width: 2, color: '#a78bfa' },
            markArea: buildSevereMarkArea('pressure', xs, ys),
            markPoint: buildAnomalyMarkPoints('pressure', xs),
            areaStyle: {
              opacity: 1,
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(167,139,250,0.20)' },
                { offset: 1, color: 'rgba(167,139,250,0.00)' },
              ]),
            },
          },
        ],
      },
      false
    );
  };

  const setWindVolumeOption = () => {
    if (!windVolumeChart) return;
    const xs = windVolumeSeries.value.map((p) => p.t);
    const ys = windVolumeSeries.value.map((p) => p.v);
    windVolumeChart.setOption(
      {
        backgroundColor: 'transparent',
        grid: { left: 44, right: 16, top: 18, bottom: 28 },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(2,6,23,0.85)',
          borderColor: 'rgba(16,185,129,0.35)',
          textStyle: { color: '#e2e8f0' },
        },
        xAxis: {
          type: 'category',
          data: xs,
          boundaryGap: false,
          ...buildAxis(),
        },
        yAxis: {
          type: 'value',
          scale: true,
          ...buildAxis(),
        },
        series: [
          {
            type: 'line',
            data: ys,
            smooth: true,
            showSymbol: false,
            lineStyle: { width: 2, color: '#10b981' },
            markArea: buildSevereMarkArea('windVolume', xs, ys),
            markPoint: buildAnomalyMarkPoints('windVolume', xs),
            areaStyle: {
              opacity: 1,
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(16,185,129,0.22)' },
                { offset: 1, color: 'rgba(16,185,129,0.00)' },
              ]),
            },
          },
        ],
      },
      false
    );
  };

  const setCoalInjectionOption = () => {
    if (!coalInjectionChart) return;
    const xs = coalInjectionSeries.value.map((p) => p.t);
    const ys = coalInjectionSeries.value.map((p) => p.v);
    coalInjectionChart.setOption(
      {
        backgroundColor: 'transparent',
        grid: { left: 44, right: 16, top: 18, bottom: 28 },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(2,6,23,0.85)',
          borderColor: 'rgba(251,191,36,0.35)',
          textStyle: { color: '#e2e8f0' },
        },
        xAxis: {
          type: 'category',
          data: xs,
          boundaryGap: false,
          ...buildAxis(),
        },
        yAxis: {
          type: 'value',
          scale: true,
          ...buildAxis(),
        },
        series: [
          {
            type: 'line',
            data: ys,
            smooth: true,
            showSymbol: false,
            lineStyle: { width: 2, color: '#fbbf24' },
            markArea: buildSevereMarkArea('coalInjection', xs, ys),
            markPoint: buildAnomalyMarkPoints('coalInjection', xs),
            areaStyle: {
              opacity: 1,
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(251,191,36,0.20)' },
                { offset: 1, color: 'rgba(251,191,36,0.00)' },
              ]),
            },
          },
        ],
      },
      false
    );
  };

  const initCharts = async () => {
    await nextTick();
    if (windTempChartRef.value) {
      windTempChart = echarts.init(windTempChartRef.value);
    }
    if (windPressureChartRef.value) {
      windPressureChart = echarts.init(windPressureChartRef.value);
    }
    if (windVolumeChartRef.value) {
      windVolumeChart = echarts.init(windVolumeChartRef.value);
    }
    if (coalInjectionChartRef.value) {
      coalInjectionChart = echarts.init(coalInjectionChartRef.value);
    }
    setWindTempOption();
    setWindPressureOption();
    setWindVolumeOption();
    setCoalInjectionOption();

    if (typeof ResizeObserver !== 'undefined') {
      chartResizeObserver = new ResizeObserver(() => {
        windTempChart?.resize();
        windPressureChart?.resize();
        windVolumeChart?.resize();
        coalInjectionChart?.resize();
      });
      if (windTempChartRef.value) chartResizeObserver.observe(windTempChartRef.value);
      if (windPressureChartRef.value) chartResizeObserver.observe(windPressureChartRef.value);
      if (windVolumeChartRef.value) chartResizeObserver.observe(windVolumeChartRef.value);
      if (coalInjectionChartRef.value) chartResizeObserver.observe(coalInjectionChartRef.value);
    } else {
      window.addEventListener('resize', resizeCharts);
    }
  };

  const resizeCharts = () => {
    windTempChart?.resize();
    windPressureChart?.resize();
    windVolumeChart?.resize();
    coalInjectionChart?.resize();
  };

  const tickMock = () => {
    const t = new Date();
    const label = formatClock(t);

    const walk = (current: number, step: number, min: number, max: number) =>
      clamp(current + (Math.random() - 0.5) * step * 2, min, max);

    const temperatureRange = getRange('temperature');
    const pressureRange = getRange('pressure');
    const windVolumeRange = getRange('windVolume');
    const coalInjectionRange = getRange('coalInjection');
    const hotMetalRange = getRange('hotMetalTemperature');
    const siliconRange = getRange('siliconContent');
    windTemp.value = walk(windTemp.value, 3.2, temperatureRange.min, temperatureRange.max);
    windPressure.value = walk(windPressure.value, 2.4, pressureRange.min, pressureRange.max);
    windVolume.value = walk(windVolume.value, 150, windVolumeRange.min, windVolumeRange.max);
    coalInjection.value = walk(coalInjection.value, 6, coalInjectionRange.min, coalInjectionRange.max);
    hotMetalTemp.value = walk(hotMetalTemp.value, 1.5, hotMetalRange.min, hotMetalRange.max);
    siliconContent.value = Number(walk(siliconContent.value, 0.007, siliconRange.min, siliconRange.max).toFixed(3));

    windTempSeries.value.push({ t: label, v: Number(windTemp.value.toFixed(2)) });
    windPressureSeries.value.push({ t: label, v: Number(windPressure.value.toFixed(2)) });
    windVolumeSeries.value.push({ t: label, v: Number(windVolume.value.toFixed(2)) });
    coalInjectionSeries.value.push({ t: label, v: Number(coalInjection.value.toFixed(2)) });
    if (windTempSeries.value.length > 60) windTempSeries.value.splice(0, windTempSeries.value.length - 60);
    if (windPressureSeries.value.length > 60) windPressureSeries.value.splice(0, windPressureSeries.value.length - 60);
    if (windVolumeSeries.value.length > 60) windVolumeSeries.value.splice(0, windVolumeSeries.value.length - 60);
    if (coalInjectionSeries.value.length > 60) coalInjectionSeries.value.splice(0, coalInjectionSeries.value.length - 60);

    if (!isHotMetalOk.value) {
      pushAlarm(
        hotMetalTemp.value > getRange('hotMetalTemperature').warningMax ? 'urgent' : 'warning',
        `铁水温度异常 · ${selectedFurnace.value}`,
        `预测温度 ${hotMetalTemp.value.toFixed(0)}℃，目标 ${hotMetalRangeText.value}`
      );
    }
    if (!isSiliconOk.value) {
      pushAlarm(
        siliconContent.value > getRange('siliconContent').warningMax ? 'urgent' : 'warning',
        `含硅量异常 · ${selectedFurnace.value}`,
        `预测含硅量 ${siliconContent.value.toFixed(3)}%，目标 ${siliconRangeText.value}`
      );
    }
    if (!isWindTempOk.value && Math.random() < 0.4) {
      pushAlarm(
        'info',
        `风温偏离 · ${selectedFurnace.value}`,
        `当前风温 ${windTemp.value.toFixed(0)}℃，建议联动风口/热风炉调节`
      );
    }
    if (!isWindPressureOk.value && Math.random() < 0.4) {
      pushAlarm(
        'info',
        `风压波动 · ${selectedFurnace.value}`,
        `当前风压 ${windPressure.value.toFixed(0)}kPa，建议检查管网与阀组`
      );
    }

    setWindTempOption();
    setWindPressureOption();
    setWindVolumeOption();
    setCoalInjectionOption();
  };

  const applyLatestData = (raw: any) => {
    const temperature = Number(raw?.temperature);
    const pressure = Number(raw?.pressure);
    const silicon = Number(raw?.siliconContent);
    const windVolumeRaw = raw?.windVolume ?? raw?.airFlow;
    const coalInjectionRaw = raw?.coalInjection ?? raw?.coalFlow;

    const windTemperature = raw?.windTemperature ?? raw?.windTemp ?? raw?.furnaceTemperature ?? raw?.temperature;
    const windPressureVal = raw?.windPressure ?? raw?.pressure;
    const hotMetalTemperature = raw?.hotMetalTemperature ?? raw?.hotMetalTemp;

    if (!Number.isNaN(Number(windTemperature))) {
      windTemp.value = Number(windTemperature);
    } else if (!Number.isNaN(temperature)) {
      windTemp.value = temperature;
    }

    if (!Number.isNaN(Number(windPressureVal))) {
      windPressure.value = Number(windPressureVal);
    } else if (!Number.isNaN(pressure)) {
      windPressure.value = pressure;
    }

    if (!Number.isNaN(Number(hotMetalTemperature))) {
      hotMetalTemp.value = Number(hotMetalTemperature);
    }

    if (!Number.isNaN(silicon)) {
      siliconContent.value = Number(silicon.toFixed(3));
    }

    if (!Number.isNaN(Number(windVolumeRaw))) {
      windVolume.value = Number(windVolumeRaw);
    }
    if (!Number.isNaN(Number(coalInjectionRaw))) {
      coalInjection.value = Number(coalInjectionRaw);
    }
  };

  const pushTrendPoint = (label: string) => {
    windTempSeries.value.push({ t: label, v: Number(windTemp.value.toFixed(2)) });
    windPressureSeries.value.push({ t: label, v: Number(windPressure.value.toFixed(2)) });
    windVolumeSeries.value.push({ t: label, v: Number(windVolume.value.toFixed(2)) });
    coalInjectionSeries.value.push({ t: label, v: Number(coalInjection.value.toFixed(2)) });
    if (windTempSeries.value.length > 60) {
      windTempSeries.value.splice(0, windTempSeries.value.length - 60);
    }
    if (windPressureSeries.value.length > 60) {
      windPressureSeries.value.splice(0, windPressureSeries.value.length - 60);
    }
    if (windVolumeSeries.value.length > 60) {
      windVolumeSeries.value.splice(0, windVolumeSeries.value.length - 60);
    }
    if (coalInjectionSeries.value.length > 60) {
      coalInjectionSeries.value.splice(0, coalInjectionSeries.value.length - 60);
    }
  };

  const paramLabelMap: Record<string, string> = {
    temperature: '温度',
    pressure: '压力',
    windVolume: '风量',
    coalInjection: '喷煤量',
    materialHeight: '料面高度',
    gasFlow: '煤气流量',
    oxygenLevel: '氧气含量',
    productionRate: '生产率',
    energyConsumption: '能耗',
    siliconContent: '铁水含硅量',
    windTemperature: '风温',
    windPressure: '风压',
    hotMetalTemperature: '铁水温度',
  };

  const toAlarmLevel = (rawLevel: any): AlarmLevel => {
    const lv = String(rawLevel || '');
    if (lv.includes('严重')) return 'urgent';
    if (lv.includes('警告')) return 'warning';
    if (lv.includes('提示')) return 'info';
    const up = lv.toUpperCase();
    if (up.includes('URGENT') || up.includes('ERROR')) return 'urgent';
    if (up.includes('WARNING') || up.includes('WARN')) return 'warning';
    return 'info';
  };

  const syncLatestData = async () => {
    try {
      const res: any = await dataManagementApi.getLatestData({
        furnaceId: selectedFurnace.value,
        strictLive: effectiveStrictLive.value,
      });
      const wrapper = res?.data || {};
      const data = wrapper?.data || wrapper;
      const sourceRaw = String(wrapper?.source || 'LIVE').toUpperCase();
      latestSource.value = sourceRaw === 'MOCK' ? 'MOCK' : sourceRaw === 'NO_LIVE_DATA' ? 'NO_LIVE_DATA' : 'LIVE';
      latestStale.value = Boolean(wrapper?.stale);
      syncSourceAudit();
      if (!data) {
        liveFailCount.value += 1;
        return false;
      }
      liveFailCount.value = 0;
      showFallbackOptions.value = false;

      const label = formatClockFromAny(data?.timestamp);
      applyLatestData(data);
      pushTrendPoint(label);
      setWindTempOption();
      setWindPressureOption();
      setWindVolumeOption();
      setCoalInjectionOption();
      return true;
    } catch (_) {
      liveFailCount.value += 1;
      syncSourceAudit();
      return false;
    }
  };

  const clearTrendSeries = () => {
    windTempSeries.value = [];
    windPressureSeries.value = [];
    windVolumeSeries.value = [];
    coalInjectionSeries.value = [];
  };

  const preloadHistoryData = async () => {
    try {
      const res: any = await dataManagementApi.getRecentData({ furnaceId: selectedFurnace.value, limit: 60 });
      const payload = res?.data;
      const rows: any[] = Array.isArray(payload)
        ? payload
        : Array.isArray(payload?.content)
        ? payload.content
        : Array.isArray(payload?.records)
        ? payload.records
        : Array.isArray(payload?.list)
        ? payload.list
        : [];

      if (!rows.length) return false;

      clearTrendSeries();

      rows
        .slice()
        .reverse()
        .forEach((row) => {
          applyLatestData(row);
          const label = formatClockFromAny(row?.timestamp || row?.createTime || row?.time);
          pushTrendPoint(label);
        });

      setWindTempOption();
      setWindPressureOption();
      setWindVolumeOption();
      setCoalInjectionOption();
      return true;
    } catch (_) {
      return false;
    }
  };

  const rebuildLiveSeries = async () => {
    clearTrendSeries();
    const ok = await preloadHistoryData();
    if (ok) {
      latestSource.value = 'LIVE';
      latestStale.value = false;
    }
    return ok;
  };

  const syncRealtimeAnomalies = async () => {
    try {
      const res: any = await anomalyApi.getRealtimeAnomalies({
        furnaceId: selectedFurnace.value,
        status: 'PENDING,PROCESSING',
        page: 0,
        size: 50,
      });
      const rows: any[] = res?.data?.content || [];

      const now = Date.now();
      const nextSevere: Record<string, { actual: number; min: number; max: number }> = {};
      const nextPlotPoints: Record<string, AnomalyPlotPoint[]> = {
        temperature: [],
        pressure: [],
        windVolume: [],
        coalInjection: [],
      };
      const mapped = rows
        .map((row) => {
          const id = String(row?.id ?? makeId());
          const p = String(row?.parameterName || '');
          const label = paramLabelMap[p] || p || '未知参数';
          const furnaceId = row?.furnaceId || selectedFurnace.value;
          const actual = row?.actualValue ?? '-';
          const range = row?.expectedRange ?? '-';
          const time = row?.detectionTime || row?.createTime || row?.time;
          const ts = time ? new Date(time).getTime() : 0;
          if (String(row?.level || '').includes('严重')) {
            const r = parseRange(row?.expectedRange);
            const av = Number(row?.actualValue);
            if (r && Number.isFinite(av)) {
              nextSevere[p] = { actual: av, min: r.min, max: r.max };
            }
          }
          const av = Number(row?.actualValue);
          if (Number.isFinite(av) && nextPlotPoints[p]) {
            nextPlotPoints[p].push({
              id,
              t: formatClockFromAny(time),
              v: av,
              level: toAlarmLevel(row?.level),
            });
          }
          return {
            id,
            time: formatClockFromAny(time),
            level: toAlarmLevel(row?.level),
            title: `${label}异常 · ${furnaceId}`,
            detail: `值 ${actual}，范围 ${range}`,
            _ts: Number.isFinite(ts) ? ts : 0,
          };
        })
        .filter((r) => !r._ts || now - r._ts <= 15 * 60 * 1000)
        .sort((a, b) => b._ts - a._ts)
        .slice(0, 60)
        .map(({ _ts, ...rest }) => rest);

      alarmQueue.value = mapped;
      severeWarnings.value = nextSevere;
      anomalyPlotPoints.value = Object.fromEntries(
        Object.entries(nextPlotPoints).map(([k, arr]) => [k, arr.slice(0, 20)])
      );
      setWindTempOption();
      setWindPressureOption();
      setWindVolumeOption();
      setCoalInjectionOption();
      return true;
    } catch (_) {
      return false;
    }
  };

  const scheduleAnomalySync = () => {
    if (anomalyRefreshTimer) return;
    anomalyRefreshTimer = window.setTimeout(async () => {
      anomalyRefreshTimer = null;
      await syncRealtimeAnomalies();
    }, anomalySyncDelayMs.value);
  };

  const onWarningEvent = (event: Event) => {
    const detail = (event as CustomEvent<any>)?.detail || {};
    const furnaceId = String(detail?.furnaceId || '');
    if (furnaceId && furnaceId !== selectedFurnace.value) {
      return;
    }
    scheduleAnomalySync();
  };

  const resetMock = () => {
    const base = selectedFurnace.value;
    const bias = base === 'BF-001' ? 0 : base === 'BF-002' ? 18 : -14;
    windTemp.value = 1120 + bias;
    windPressure.value = 190 + bias * 0.2;
    windVolume.value = 5000 + bias * 18;
    coalInjection.value = 150 + bias * 0.4;
    hotMetalTemp.value = 1485 + bias * 0.15;
    siliconContent.value = Number((0.46 + bias * 0.0006).toFixed(3));
    windTempSeries.value = seedSeries(windTemp.value, 4.5, 60);
    windPressureSeries.value = seedSeries(windPressure.value, 3.2, 60);
    windVolumeSeries.value = seedSeries(windVolume.value, 120, 60);
    coalInjectionSeries.value = seedSeries(coalInjection.value, 6.5, 60);
    alarmQueue.value = [
      {
        id: makeId(),
        time: nowText.value || formatClock(new Date()),
        level: 'info',
        title: `监控链路就绪 · ${base}`,
        detail: '大屏已进入实时渲染模式',
      },
      {
        id: makeId(),
        time: nowText.value || formatClock(new Date()),
        level: 'info',
        title: `异常检测联动 · ${base}`,
        detail: '预警队列将随核心指标动态变化',
      },
    ];
    tickerOffset.value = 0;
    tickerAnimating.value = false;
    nextTick(() => (tickerAnimating.value = true));
    setWindTempOption();
    setWindPressureOption();
    setWindVolumeOption();
    setCoalInjectionOption();
  };

  const burstAnomaly = () => {
    const hotMetalRange = getRange('hotMetalTemperature');
    const siliconRange = getRange('siliconContent');
    hotMetalTemp.value = clamp(hotMetalTemp.value + 55, hotMetalRange.min, hotMetalRange.max);
    siliconContent.value = Number(clamp(siliconContent.value + 0.12, siliconRange.min, siliconRange.max).toFixed(3));
    pushAlarm('urgent', `人工注入异常 · ${selectedFurnace.value}`, '用于演示预警滚动列表与发光提示');
  };

  const tickerRows = computed(() => {
    const src = alarmQueue.value;
    if (src.length === 0) return [];
    const take = Math.min(src.length, tickerVisibleCount + 1);
    return src.slice(0, take).map((row) => ({
      ...row,
      levelText: row.level === 'urgent' ? 'URG' : row.level === 'warning' ? 'WRN' : 'INF',
    }));
  });

  const urgentCount = computed(() => alarmQueue.value.filter((r) => r.level === 'urgent').length);
  const warnCount = computed(() => alarmQueue.value.filter((r) => r.level === 'warning').length);
  const infoCount = computed(() => alarmQueue.value.filter((r) => r.level === 'info').length);

  const rowBadgeClass = (level: AlarmLevel) => {
    if (level === 'urgent') return 'border-rose-400/30 bg-rose-500/12 text-rose-200';
    if (level === 'warning') return 'border-amber-300/30 bg-amber-500/12 text-amber-200';
    return 'border-cyan-300/30 bg-cyan-500/10 text-cyan-200';
  };

  const tickerShouldScroll = computed(() => {
    if (!preferLive.value) return alarmQueue.value.length > 1;
    if (!collectionRunning.value) return false;
    return alarmQueue.value.length > tickerVisibleCount;
  });

  const stopTicker = () => {
    if (tickerTimer) window.clearInterval(tickerTimer);
    tickerTimer = null;
    tickerAnimating.value = false;
    tickerOffset.value = 0;
    nextTick(() => (tickerAnimating.value = true));
  };

  const startTicker = () => {
    if (tickerTimer) window.clearInterval(tickerTimer);
    tickerTimer = window.setInterval(() => {
      if (!tickerShouldScroll.value) return;
      tickerAnimating.value = true;
      tickerOffset.value = -tickerItemHeight;
    }, 2200);
  };

  const updateTickerMode = () => {
    if (!tickerShouldScroll.value) {
      stopTicker();
      return;
    }
    if (!tickerTimer) startTicker();
  };

  const onTickerTransitionEnd = () => {
    if (tickerOffset.value !== -tickerItemHeight) return;
    const first = alarmQueue.value.shift();
    if (first) alarmQueue.value.push(first);
    tickerAnimating.value = false;
    tickerOffset.value = 0;
    nextTick(() => (tickerAnimating.value = true));
  };

  const startRuntime = () => {
    if (runtimeTimer) window.clearInterval(runtimeTimer);
    runtimeTimer = window.setInterval(runtimeTick, 1000 / refreshHz);
  };

  const startClock = () => {
    if (clockTimer) window.clearInterval(clockTimer);
    nowText.value = formatClock(new Date());
    clockTimer = window.setInterval(() => {
      nowText.value = formatClock(new Date());
    }, 500);
  };

  let ticking = false;
  let runtimeCycles = 0;
  const runtimeTick = async () => {
    if (ticking) return;
    ticking = true;
    runtimeCycles += 1;
    try {
      if (!preferLive.value) {
        latestSource.value = 'MOCK';
        latestStale.value = false;
        tickMock();
        updateTickerMode();
        return;
      }

      if (runtimeCycles % 3 === 1) {
        await syncCollectionStatus();
        updateTickerMode();
      }

      if (!collectionRunning.value) {
        liveFrozen.value = true;
        updateTickerMode();
        return;
      }
      if (liveFrozen.value) {
        updateTickerMode();
        return;
      }

      const ok = await syncLatestData();
      if (!ok && liveFailCount.value >= liveFailThreshold.value) {
        liveFrozen.value = true;
        showFallbackOptions.value = true;
        pushAlarm('warning', `数据源断开 · ${selectedFurnace.value}`, '请手动选择重试、冻结或切换模拟');
        syncSourceAudit();
        return;
      }

      if (runtimeCycles % 6 === 0) {
        const anomalyOk = await syncRealtimeAnomalies();
        if (!anomalyOk && runtimeCycles % 6 === 0) {
          pushAlarm('info', `异常接口不可用 · ${selectedFurnace.value}`, '当前仅展示核心指标趋势');
        }
      }

      updateTickerMode();
    } finally {
      ticking = false;
    }
  };

  const syncCollectionStatus = async () => {
    try {
      const res: any = await collectionApi.getStatus();
      collectionRunning.value = Boolean(res?.data?.isRunning);
      syncSourceAudit();
      updateTickerMode();
      return true;
    } catch (_) {
      collectionRunning.value = false;
      syncSourceAudit();
      updateTickerMode();
      return false;
    }
  };

  const startLive = async () => {
    const wasMockLike = !preferLive.value || latestSource.value !== 'LIVE';
    preferLive.value = true;
    liveFailCount.value = 0;
    liveFrozen.value = false;
    showFallbackOptions.value = false;
    latestSource.value = 'LIVE';
    latestStale.value = false;
    await syncCollectionStatus();
    updateTickerMode();
    if (wasMockLike) {
      await rebuildLiveSeries();
    }
    const ok = await syncLatestData();
    if (!ok && windTempSeries.value.length === 0) {
      liveFrozen.value = true;
      showFallbackOptions.value = true;
    }
    syncSourceAudit();
    await syncRealtimeAnomalies();
    updateTickerMode();
  };

  const switchToMock = () => {
    preferLive.value = false;
    liveFailCount.value = 0;
    liveFrozen.value = false;
    showFallbackOptions.value = false;
    latestSource.value = 'MOCK';
    latestStale.value = false;
    resetMock();
    syncSourceAudit();
    updateTickerMode();
  };

  const retryLive = async () => {
    showFallbackOptions.value = false;
    liveFrozen.value = false;
    liveFailCount.value = 0;
    await syncCollectionStatus();
    await syncLatestData();
    await syncRealtimeAnomalies();
    syncSourceAudit();
    updateTickerMode();
  };

  const keepFrozen = () => {
    showFallbackOptions.value = false;
    liveFrozen.value = true;
    pushAlarm('info', `数据保持冻结 · ${selectedFurnace.value}`, '当前保持最后一帧实时数据，等待人工重试');
    syncSourceAudit();
  };

  const switchToLive = async () => {
    await startLive();
  };

  watch(
    selectedFurnace,
    async () => {
      await loadResolvedRanges();
      if (preferLive.value) {
        await startLive();
        return;
      }
      resetMock();
    },
    { immediate: false }
  );

  onMounted(async () => {
    await loadRuntimePolicies();
    await loadResolvedRanges();
    startClock();
    await initCharts();
    await syncCollectionStatus();
    await preloadHistoryData();
    if (preferLive.value) {
      await startLive();
    } else {
      resetMock();
    }
    syncSourceAudit();
    startRuntime();
    updateTickerMode();
    window.addEventListener('warning:new', onWarningEvent as EventListener);
    if (collectionStatusTimer) window.clearInterval(collectionStatusTimer);
    collectionStatusTimer = window.setInterval(syncCollectionStatus, 5000);
  });

  onUnmounted(() => {
    if (runtimeTimer) window.clearInterval(runtimeTimer);
    if (clockTimer) window.clearInterval(clockTimer);
    if (tickerTimer) window.clearInterval(tickerTimer);
    if (collectionStatusTimer) window.clearInterval(collectionStatusTimer);
    if (anomalyRefreshTimer) window.clearTimeout(anomalyRefreshTimer);
    window.removeEventListener('warning:new', onWarningEvent as EventListener);

    if (chartResizeObserver) {
      if (windTempChartRef.value) chartResizeObserver.unobserve(windTempChartRef.value);
      if (windPressureChartRef.value) chartResizeObserver.unobserve(windPressureChartRef.value);
      if (windVolumeChartRef.value) chartResizeObserver.unobserve(windVolumeChartRef.value);
      if (coalInjectionChartRef.value) chartResizeObserver.unobserve(coalInjectionChartRef.value);
      chartResizeObserver.disconnect();
      chartResizeObserver = null;
    } else {
      window.removeEventListener('resize', resizeCharts);
    }

    windTempChart?.dispose();
    windTempChart = null;
    windPressureChart?.dispose();
    windPressureChart = null;
    windVolumeChart?.dispose();
    windVolumeChart = null;
    coalInjectionChart?.dispose();
    coalInjectionChart = null;
  });
</script>

<style scoped>
  .monitor-shell {
    border: 1px solid color-mix(in srgb, var(--n-border-color) 65%, transparent);
    box-shadow: var(--n-box-shadow-2);
  }
</style>
