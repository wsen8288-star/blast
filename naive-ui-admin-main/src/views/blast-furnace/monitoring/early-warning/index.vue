<template>
  <div
    class="early-warning min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="预警管理" class="mb-4 warning-main-card">
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <n-card size="small" :bordered="false" class="warning-stat-card">
          <n-statistic label="提示" :value="tipCount" />
          <n-text depth="3" class="mt-1">轻微偏离阈值的提示信息</n-text>
        </n-card>
        <n-card size="small" :bordered="false" class="warning-stat-card">
          <n-statistic label="警告" :value="warningCount" />
          <n-text depth="3" class="mt-1">需要关注并尽快处理的预警</n-text>
        </n-card>
        <n-card size="small" :bordered="false" class="warning-stat-card">
          <n-statistic label="严重" :value="severeCount" />
          <n-text depth="3" class="mt-1">需要立即处理的严重预警</n-text>
        </n-card>
      </div>

      <n-card title="预警列表" size="small" class="mt-4 warning-list-card">
        <div class="mb-4 flex flex-wrap items-center gap-3">
          <n-select
            v-model:value="statusFilter"
            :options="statusOptions"
            clearable
            size="small"
            class="w-32"
            placeholder="状态"
          />
          <n-select
            v-model:value="levelFilter"
            :options="levelOptions"
            clearable
            size="small"
            class="w-32"
            placeholder="级别"
          />
          <n-button round size="small" type="primary" :loading="loading" @click="reloadAll">查询</n-button>
          <n-button round size="small" @click="resetFilters">重置</n-button>
          <n-button round size="small" type="warning" :loading="batchHandling" @click="handleBatch">
            一键处理(提示/警告)
          </n-button>
        </div>

        <div ref="tableWrapRef">
          <n-data-table
            remote
            size="small"
            :columns="columns"
            :data="tableData"
            :loading="loading"
            :pagination="pagination"
            :row-class-name="rowClassName"
          />
        </div>
      </n-card>
    </n-card>

    <n-modal v-model:show="handleModalVisible" :mask-closable="false">
      <n-card
        title="处理预警"
        size="small"
        class="w-[560px] warning-handle-card"
        :bordered="false"
        role="dialog"
        aria-modal="true"
      >
        <div class="space-y-3">
          <div class="warning-handle-tip">
            {{ handleTitle }}
          </div>
          <n-select
            v-model:value="handleStatus"
            :options="handleStatusOptions"
            size="small"
            placeholder="处理结果"
          />
          <n-input
            v-model:value="handlerContent"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
            placeholder="填写处理意见"
          />
        </div>
        <div class="mt-4 flex justify-end gap-2">
          <n-button round size="small" @click="handleModalVisible = false">取消</n-button>
          <n-button round size="small" type="primary" :loading="handleSubmitting" @click="submitHandle">
            提交
          </n-button>
        </div>
      </n-card>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import type { DataTableColumns } from 'naive-ui';
  import { NButton, NTag, useDialog, useMessage } from 'naive-ui';
  import { useRoute } from 'vue-router';
  import { warningApi } from '@/api/blast-furnace';
  import { storage } from '@/utils/Storage';
  import { CURRENT_USER } from '@/store/mutation-types';

  type WarningRecord = {
    id: number;
    furnaceId?: string | null;
    detectionTime?: string | null;
    parameterName?: string | null;
    actualValue?: number | null;
    expectedRange?: string | null;
    level?: string | null;
    status?: number | null;
    handlerContent?: string | null;
    handlerUser?: number | null;
    handleTime?: string | null;
  };

  const message = useMessage();
  const dialog = useDialog();

  const tipCount = ref(0);
  const warningCount = ref(0);
  const severeCount = ref(0);

  const loading = ref(false);
  const batchHandling = ref(false);
  const tableData = ref<WarningRecord[]>([]);

  const route = useRoute();
  const tableWrapRef = ref<HTMLElement | null>(null);
  const highlightId = ref<number | null>(null);
  const highlightActive = ref(false);
  let highlightTimer: number | null = null;
  let warningRefreshTimer: number | null = null;

  const statusFilter = ref<number | null>(0);
  const levelFilter = ref<string | null>(null);

  const statusOptions = [
    { label: '待处理', value: 0 },
    { label: '处理中', value: 1 },
    { label: '已解决', value: 2 },
    { label: '已关闭', value: 3 },
  ];
  const handleStatusOptions = [
    { label: '处理中', value: 1 },
    { label: '已解决', value: 2 },
    { label: '已关闭', value: 3 },
  ];

  const levelOptions = [
    { label: '提示', value: '提示' },
    { label: '警告', value: '警告' },
    { label: '严重', value: '严重' },
  ];

  const pagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
    onChange: (page: number) => {
      pagination.page = page;
      fetchList();
    },
    onUpdatePageSize: (pageSize: number) => {
      pagination.pageSize = pageSize;
      pagination.page = 1;
      fetchList();
    },
  });

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
    hotMetalTemperature: '铁水温度',
  };

  const getParamLabel = (value: any) => {
    const k = String(value || '');
    return paramLabelMap[k] || k || '-';
  };

  const pad2 = (n: number) => String(n).padStart(2, '0');
  const formatDateTime = (value: any) => {
    if (!value) return '-';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    const y = d.getFullYear();
    const m = pad2(d.getMonth() + 1);
    const day = pad2(d.getDate());
    const hh = pad2(d.getHours());
    const mm = pad2(d.getMinutes());
    const ss = pad2(d.getSeconds());
    return `${y}-${m}-${day} ${hh}:${mm}:${ss}`;
  };

  const levelTagType = (level: any) => {
    const v = String(level || '');
    if (v === '严重') return 'error';
    if (v === '警告') return 'warning';
    return 'info';
  };

  const statusText = (status: any) => {
    const v = Number(status);
    if (v === 0) return '待处理';
    if (v === 1) return '处理中';
    if (v === 2) return '已解决';
    if (v === 3) return '已关闭';
    return '-';
  };

  const statusTagType = (status: any) => {
    const v = Number(status);
    if (v >= 2) return 'success';
    if (v === 1) return 'warning';
    return 'default';
  };

  const handleModalVisible = ref(false);
  const handleSubmitting = ref(false);
  const handlerContent = ref('');
  const handleStatus = ref<number>(2);
  const currentRow = ref<WarningRecord | null>(null);

  const handleTitle = computed(() => {
    if (!currentRow.value) return '';
    const name = getParamLabel(currentRow.value.parameterName);
    const time = formatDateTime(currentRow.value.detectionTime);
    const lv = currentRow.value.level || '-';
    return `${name} · ${lv} · ${time}`;
  });

  const getHandlerUserId = () => {
    const info: any = storage.get(CURRENT_USER, null);
    const candidate = info?.userId ?? info?.id ?? info?.data?.userId ?? info?.data?.id;
    if (candidate === null || candidate === undefined) return null;
    const n = Number(candidate);
    return Number.isFinite(n) ? n : null;
  };

  const openHandleModal = (row: WarningRecord) => {
    currentRow.value = row;
    handlerContent.value = '';
    handleStatus.value = Number(row.status) <= 1 ? 2 : Number(row.status);
    handleModalVisible.value = true;
  };

  const submitHandle = async () => {
    if (!currentRow.value?.id) return;
    if (!handlerContent.value.trim()) {
      message.warning('请填写处理意见');
      return;
    }
    handleSubmitting.value = true;
    try {
      await warningApi.handle({
        id: currentRow.value.id,
        handlerUser: getHandlerUserId(),
        handlerContent: handlerContent.value.trim(),
        status: handleStatus.value,
      });
      message.success('处理成功');
      handleModalVisible.value = false;
      await reloadAll();
    } catch (e: any) {
      message.error(e?.message || '处理失败');
    } finally {
      handleSubmitting.value = false;
    }
  };

  const handleBatch = async () => {
    if (batchHandling.value) return;
    const candidates = tableData.value.filter((row) => Number(row.status) < 2 && String(row.level || '') !== '严重');
    if (candidates.length === 0) {
      message.info('当前无可一键处理的提示/警告');
      return;
    }
    const ids = candidates.map((item) => item.id).filter((id) => Number.isFinite(Number(id)));
    if (!ids.length) {
      message.warning('可处理数据为空');
      return;
    }
    const content = `将批量处理 ${ids.length} 条提示/警告，状态置为“已解决”，并写入统一处理意见。是否继续？`;
    dialog.warning({
      title: '批量处理确认',
      content,
      positiveText: '确认处理',
      negativeText: '取消',
      onPositiveClick: async () => {
        batchHandling.value = true;
        try {
          const res: any = await warningApi.handleBatch({
            ids,
            handlerUser: getHandlerUserId(),
            handlerContent: '批量处置：一键处理提示/警告',
            status: 2,
            allowSevere: false,
          });
          const done = Number(res?.data?.success ?? 0);
          const skipped = Array.isArray(res?.data?.skipped) ? res.data.skipped.length : 0;
          const failed = Array.isArray(res?.data?.failed) ? res.data.failed.length : 0;
          message.success(`批量处理完成：成功${done}，跳过${skipped}，失败${failed}`);
          pagination.page = 1;
          await reloadAll();
        } catch (e: any) {
          message.error(e?.message || '批量处理失败');
        } finally {
          batchHandling.value = false;
        }
      },
      onNegativeClick: () => {},
    });
  };

  const columns: DataTableColumns<WarningRecord> = [
    {
      title: '异常指标名称',
      key: 'parameterName',
      render: (row) => getParamLabel(row.parameterName),
    },
    {
      title: '当前值',
      key: 'actualValue',
      render: (row) => (row.actualValue === null || row.actualValue === undefined ? '-' : row.actualValue),
    },
    {
      title: '阈值范围',
      key: 'expectedRange',
      render: (row) => row.expectedRange || '-',
    },
    {
      title: '预警级别',
      key: 'level',
      render: (row) => h(NTag, { type: levelTagType(row.level), size: 'small', strong: true }, { default: () => row.level || '-' }),
    },
    {
      title: '发生时间',
      key: 'detectionTime',
      render: (row) => formatDateTime(row.detectionTime),
    },
    {
      title: '状态',
      key: 'status',
      render: (row) =>
        h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => statusText(row.status) }),
    },
    {
      title: '处理意见',
      key: 'handlerContent',
      render: (row) => row.handlerContent || '-',
    },
    {
      title: '负责人',
      key: 'handlerUser',
      render: (row) => (row.handlerUser == null ? '-' : String(row.handlerUser)),
    },
    {
      title: '处理时间',
      key: 'handleTime',
      render: (row) => formatDateTime(row.handleTime),
    },
    {
      title: '操作',
      key: 'action',
      render: (row) =>
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            secondary: true,
            disabled: Number(row.status) >= 2,
            onClick: () => openHandleModal(row),
          },
          { default: () => '处理' }
        ),
    },
  ];

  const fetchStats = async () => {
    try {
      const res: any = await warningApi.getStats({
        status: statusFilter.value ?? undefined,
        level: levelFilter.value ?? undefined,
      });
      tipCount.value = Number(res?.data?.tipCount ?? 0);
      warningCount.value = Number(res?.data?.warningCount ?? 0);
      severeCount.value = Number(res?.data?.severeCount ?? 0);
    } catch (_) {
      tipCount.value = 0;
      warningCount.value = 0;
      severeCount.value = 0;
    }
  };

  const fetchList = async () => {
    loading.value = true;
    try {
      const res: any = await warningApi.getList({
        page: Math.max(0, pagination.page - 1),
        size: pagination.pageSize,
        status: statusFilter.value ?? undefined,
        level: levelFilter.value ?? undefined,
      });
      const page = res?.data;
      const rows = Array.isArray(page?.content) ? page.content : [];
      tableData.value = rows.slice().sort((a: any, b: any) => {
        const statusA = Number(a?.status ?? 9);
        const statusB = Number(b?.status ?? 9);
        if (statusA !== statusB) return statusA - statusB;
        const levelRank = (lv: any) => (String(lv || '') === '严重' ? 3 : String(lv || '') === '警告' ? 2 : 1);
        const lr = levelRank(b?.level) - levelRank(a?.level);
        if (lr !== 0) return lr;
        const ta = new Date(a?.detectionTime || 0).getTime();
        const tb = new Date(b?.detectionTime || 0).getTime();
        return tb - ta;
      });
      pagination.itemCount = Number(page?.totalElements ?? rows.length);
    } catch (e: any) {
      message.error(e?.message || '加载预警列表失败');
    } finally {
      loading.value = false;
    }
  };

  const rowClassName = (row: WarningRecord) => {
    if (!highlightActive.value || highlightId.value == null) return '';
    return Number(row.id) === Number(highlightId.value) ? 'warning-highlight' : '';
  };

  const scrollToHighlight = async () => {
    if (!highlightActive.value || highlightId.value == null) return;
    await nextTick();
    const el = tableWrapRef.value?.querySelector?.('tr.warning-highlight') as HTMLElement | null;
    if (el?.scrollIntoView) {
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  };

  const reloadAll = async () => {
    await Promise.all([fetchStats(), fetchList()]);
    await scrollToHighlight();
  };

  const scheduleWarningRefresh = () => {
    if (warningRefreshTimer) return;
    warningRefreshTimer = window.setTimeout(async () => {
      warningRefreshTimer = null;
      pagination.page = 1;
      await reloadAll();
    }, 300);
  };

  const onWarningEvent = () => {
    scheduleWarningRefresh();
  };

  const resetFilters = () => {
    statusFilter.value = 0;
    levelFilter.value = null;
    pagination.page = 1;
    reloadAll();
  };

  const applyRouteHighlight = () => {
    const raw = route.query?.highlightId;
    const id = raw == null ? NaN : Number(Array.isArray(raw) ? raw[0] : raw);
    if (!Number.isFinite(id)) return;
    highlightId.value = id;
    highlightActive.value = true;
    if (highlightTimer) window.clearTimeout(highlightTimer);
    highlightTimer = window.setTimeout(() => {
      highlightActive.value = false;
      highlightTimer = null;
    }, 4000);
  };

  watch(
    () => route.fullPath,
    () => {
      applyRouteHighlight();
      reloadAll();
    }
  );

  onMounted(() => {
    applyRouteHighlight();
    reloadAll();
    window.addEventListener('warning:new', onWarningEvent as EventListener);
  });

  onBeforeUnmount(() => {
    if (highlightTimer) window.clearTimeout(highlightTimer);
    if (warningRefreshTimer) window.clearTimeout(warningRefreshTimer);
    window.removeEventListener('warning:new', onWarningEvent as EventListener);
  });
</script>

<style lang="less" scoped>
  .early-warning {
    padding: 20px;
  }

  .warning-main-card,
  .warning-list-card,
  .warning-stat-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .warning-handle-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .warning-handle-tip {
    border-radius: 10px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
    color: var(--n-text-color-3);
    padding: 10px 12px;
    font-size: 13px;
  }

  .early-warning :deep(.n-base-selection),
  .early-warning :deep(.n-input) {
    border-radius: 10px;
  }
</style>
