<template>
  <div class="logs-page min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border space-y-4">
    <n-card :bordered="false" size="small" class="logs-head-card">
      <div class="flex items-start justify-between">
        <div>
          <div class="text-base font-semibold logs-title">系统日志</div>
          <div class="mt-1 text-xs logs-subtitle">异常请求日志与用户操作审计日志查询</div>
        </div>
        <n-button round secondary :loading="loading" @click="fetchLogs">
          刷新
        </n-button>
      </div>
    </n-card>

    <n-card :bordered="false" size="small" class="logs-filter-card">
      <div class="flex flex-wrap items-center gap-3 logs-filter-wrap">
        <n-select v-model:value="filters.logType" class="w-40" :options="logTypeOptions" />
        <n-input
          v-model:value="filters.keyword"
          clearable
          class="w-56"
          :placeholder="filters.logType === 'request' ? '按路径关键词搜索' : '按模块/动作关键词搜索'"
          @keyup.enter="search"
        />
        <n-input
          v-model:value="filters.username"
          clearable
          class="w-40"
          :placeholder="filters.logType === 'request' ? '用户名' : '操作人'"
          @keyup.enter="search"
        />
        <n-select
          v-if="filters.logType === 'request'"
          v-model:value="filters.level"
          clearable
          class="w-32"
          placeholder="级别"
          :options="levelOptions"
        />
        <n-select
          v-if="filters.logType === 'request'"
          v-model:value="filters.method"
          clearable
          class="w-32"
          placeholder="方法"
          :options="methodOptions"
        />
        <div class="flex items-center gap-2">
          <n-button size="small" round type="primary" :loading="loading" @click="search">查询</n-button>
          <n-button size="small" round @click="resetFilters">重置</n-button>
        </div>
      </div>

      <div class="mt-3 logs-table-wrap">
        <n-data-table
          remote
          size="small"
          :columns="columns"
          :data="logs"
          :loading="loading"
          :pagination="pagination"
          :max-height="640"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { NTag, useMessage } from 'naive-ui';
  import type { DataTableColumns, PaginationProps } from 'naive-ui';
  import { systemApi } from '@/api/blast-furnace';

  const message = useMessage();
  const loading = ref(false);
  const logs = ref<any[]>([]);
  const total = ref(0);

  const filters = reactive({
    logType: 'request' as 'request' | 'operation',
    keyword: '',
    username: '',
    level: null as string | null,
    method: null as string | null,
  });

  const logTypeOptions = [
    { label: '异常请求日志', value: 'request' },
    { label: '操作审计日志', value: 'operation' },
  ];

  const levelOptions = [
    { label: 'INFO', value: 'INFO' },
    { label: 'WARN', value: 'WARN' },
    { label: 'ERROR', value: 'ERROR' },
  ];

  const methodOptions = ['GET', 'POST', 'PUT', 'DELETE'].map((item) => ({ label: item, value: item }));

  const pagination = reactive<PaginationProps>({
    page: 1,
    pageSize: 20,
    pageCount: 1,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50, 100],
    onChange: (page: number) => {
      pagination.page = page;
      fetchLogs();
    },
    onUpdatePageSize: (pageSize: number) => {
      pagination.pageSize = pageSize;
      pagination.page = 1;
      fetchLogs();
    },
  });

  const levelTagType = (level: string) => {
    if (level === 'ERROR') return 'error';
    if (level === 'WARN') return 'warning';
    return 'success';
  };

  const columns = computed<DataTableColumns<any>>(() => {
    if (filters.logType === 'operation') {
      return [
        { title: 'ID', key: 'id', width: 90 },
        { title: '操作人', key: 'operator', width: 140, ellipsis: { tooltip: true } },
        { title: '模块', key: '__module', width: 130, ellipsis: { tooltip: true } },
        { title: '动作', key: '__action', width: 160, ellipsis: { tooltip: true } },
        { title: '路径', key: '__uri', minWidth: 260, ellipsis: { tooltip: true } },
        { title: '来源IP', key: '__ip', width: 150, ellipsis: { tooltip: true } },
        { title: '方案ID', key: 'schemeId', width: 100 },
        {
          title: '时间',
          key: 'executionTime',
          width: 180,
          render: (row) => formatDateTime(row?.executionTime),
        },
      ];
    }
    return [
      { title: 'ID', key: 'id', width: 90 },
      {
        title: '级别',
        key: 'level',
        width: 100,
        render: (row) => h(NTag, { type: levelTagType(row.level), size: 'small' }, { default: () => row.level || '-' }),
      },
      { title: '方法', key: 'requestMethod', width: 90 },
      { title: '状态码', key: 'statusCode', width: 100 },
      { title: '耗时(ms)', key: 'durationMs', width: 110 },
      { title: '用户', key: 'username', width: 140, ellipsis: { tooltip: true } },
      { title: 'IP', key: 'clientIp', width: 150, ellipsis: { tooltip: true } },
      { title: '路径', key: 'requestUri', minWidth: 260, ellipsis: { tooltip: true } },
      { title: '查询参数', key: 'queryString', minWidth: 180, ellipsis: { tooltip: true } },
      { title: '异常信息', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true } },
      {
        title: '时间',
        key: 'createdAt',
        width: 180,
        render: (row) => formatDateTime(row?.createdAt),
      },
    ];
  });

  const formatDateTime = (value: any) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
  };

  const parseAdjustments = (raw: any) => {
    if (!raw || typeof raw !== 'string') {
      return {};
    }
    try {
      return JSON.parse(raw);
    } catch {
      return {};
    }
  };

  const fetchLogs = async () => {
    loading.value = true;
    try {
      const params: any = {
        page: Math.max((pagination.page || 1) - 1, 0),
        size: pagination.pageSize || 20,
        sort: 'id,desc',
      };
      let res: any;
      if (filters.logType === 'operation') {
        if (filters.username?.trim()) params.operator = filters.username.trim();
        if (filters.keyword?.trim()) params.keyword = filters.keyword.trim();
        res = await systemApi.log.getOperationList(params);
      } else {
        if (filters.keyword?.trim()) params.keyword = filters.keyword.trim();
        if (filters.username?.trim()) params.username = filters.username.trim();
        if (filters.level) params.level = filters.level;
        if (filters.method) params.method = filters.method;
        res = await systemApi.log.getList(params);
      }
      if (res.code === 200 && res.data) {
        let rows = Array.isArray(res.data.content) ? res.data.content : [];
        if (filters.logType === 'operation') {
          rows = rows.map((item: any) => {
            const parsed = parseAdjustments(item?.adjustments);
            return {
              ...item,
              __module: parsed.module || '-',
              __action: parsed.action || '-',
              __uri: parsed.uri || '-',
              __ip: parsed.ip || '-',
            };
          });
        }
        logs.value = rows;
        total.value = Number(res.data.totalElements || 0);
        pagination.itemCount = total.value;
        pagination.pageCount = Math.max(Number(res.data.totalPages || 1), 1);
      } else {
        logs.value = [];
        pagination.itemCount = 0;
        pagination.pageCount = 1;
        message.error(res.msg || '获取系统日志失败');
      }
    } catch (error: any) {
      logs.value = [];
      pagination.itemCount = 0;
      pagination.pageCount = 1;
      message.error('获取系统日志失败: ' + (error?.message || '未知错误'));
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    pagination.page = 1;
    fetchLogs();
  };

  const resetFilters = () => {
    filters.logType = 'request';
    filters.keyword = '';
    filters.username = '';
    filters.level = null;
    filters.method = null;
    pagination.page = 1;
    fetchLogs();
  };

  onMounted(() => {
    fetchLogs();
  });
</script>

<style scoped>
  .logs-page {
    color: var(--n-text-color-2);
  }

  .logs-head-card,
  .logs-filter-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .logs-title {
    color: var(--n-text-color-1);
  }

  .logs-subtitle {
    color: var(--n-text-color-3);
  }

  .logs-filter-wrap :deep(.n-base-selection),
  .logs-filter-wrap :deep(.n-input),
  .logs-filter-wrap :deep(.n-input-number) {
    border-radius: 10px;
  }

  .logs-table-wrap {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    padding: 2px 2px 0;
    background-color: var(--n-card-color);
  }

  .logs-table-wrap :deep(.n-data-table-wrapper) {
    border-radius: 8px;
    overflow: hidden;
  }
</style>
