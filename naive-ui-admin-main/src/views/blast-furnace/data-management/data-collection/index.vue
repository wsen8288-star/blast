<template>
  <div
    class="data-collection min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="数据采集" class="mb-4 collect-head-card">
      <n-tabs type="line" default-value="realtime">
        <n-tab-pane name="realtime" tab="实时采集">
          <div class="collect-panel">
            <div class="flex items-center justify-between mb-4">
              <div>
                <n-tag type="info" size="small">采集状态: {{ collectionStatus }}</n-tag>
                <n-tag
                  :type="connectionStatus === 'connected' ? 'success' : 'error'"
                  size="small"
                  class="ml-2"
                >
                  连接状态: {{ connectionStatus === 'connected' ? '已连接' : '未连接' }}
                </n-tag>
              </div>
              <div>
                <n-button round :disabled="connectionStatus !== 'connected'" @click="startCollection"
                  >开始采集</n-button
                >
                <n-button
                  :disabled="!isCollecting"
                  type="error"
                  @click="stopCollection"
                  class="ml-2"
                  round
                  >停止采集</n-button
                >
              </div>
            </div>

            <n-card title="采集设备列表" size="small" class="mb-4 collect-sub-card">
              <n-data-table :columns="deviceColumns" :data="deviceList" size="small" />
            </n-card>

            <n-card title="采集参数设置" size="small" class="collect-sub-card">
              <n-form label-placement="left" label-width="120">
                <n-form-item label="高炉选择">
                  <n-select
                    v-model:value="selectedFurnace"
                    :options="furnaceOptions"
                    placeholder="选择高炉"
                  />
                </n-form-item>
                <n-form-item label="采集频率">
                  <n-select
                    v-model:value="collectionFrequency"
                    :options="frequencyOptions"
                    placeholder="选择采集频率"
                  />
                </n-form-item>
                <n-form-item label="数据存储路径">
                  <n-input v-model:value="storagePath" placeholder="输入数据存储路径" />
                </n-form-item>
                <n-form-item label="采集点数">
                  <n-input-number v-model:value="collectionPoints" placeholder="输入采集点数" />
                </n-form-item>
                <n-form-item label="工况脚本模板">
                  <n-select
                    v-model:value="selectedScenarioTemplate"
                    :options="scenarioTemplateOptions"
                    placeholder="选择工况模板"
                  />
                </n-form-item>
                <n-form-item label="脚本种子">
                  <n-input-number
                    v-model:value="scriptSeed"
                    placeholder="可选：固定随机种子以复现结果"
                  />
                </n-form-item>
                <n-form-item>
                  <n-button round type="primary" @click="saveSettings">保存设置</n-button>
                </n-form-item>
              </n-form>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="history" tab="采集历史">
          <div class="collect-panel">
            <div class="flex items-center mb-4">
              <n-input v-model:value="historySearch" placeholder="搜索采集任务" class="mr-2" />
              <n-date-picker
                v-model:formatted-value="historyDateRange"
                type="daterange"
                placeholder="选择时间范围"
                format="yyyy-MM-dd"
                value-format="yyyy-MM-dd"
                class="mr-2"
              />
              <n-button round type="primary" @click="searchHistory">搜索</n-button>
              <n-button round @click="resetHistorySearch" class="ml-2">重置</n-button>
            </div>
            <n-data-table :columns="historyColumns" :data="historyList" size="small" />
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, h, onMounted } from 'vue';
  import { useRoute } from 'vue-router';
  import {
    NTag,
    NDataTable,
    NCard,
    NButton,
    NTabs,
    NTabPane,
    NForm,
    NFormItem,
    NSelect,
    NInput,
    NInputNumber,
    useMessage,
    useDialog,
  } from 'naive-ui';
  import { collectionApi } from '@/api/blast-furnace';
  import { ensureQuickStartRunId, setQuickStartRun } from '@/utils/quickStartRun';

  const message = useMessage();
  const dialog = useDialog();
  const route = useRoute();

  // 采集状态
  const collectionStatus = ref('就绪');
  const connectionStatus = ref('connected');
  const isCollecting = ref(false);

  const selectedFurnace = ref('BF-001');
  const furnaceOptions = [
    { label: '高炉1 (BF-001)', value: 'BF-001' },
    { label: '高炉2 (BF-002)', value: 'BF-002' },
    { label: '高炉3 (BF-003)', value: 'BF-003' },
    { label: '随机三炉混合采集', value: 'RANDOM_THREE' },
  ];

  // 采集设备列表
  const deviceList = ref<any[]>([]);

  const deviceColumns = [
    { title: '设备ID', key: 'id' },
    { title: '设备名称', key: 'name' },
    { title: '设备类型', key: 'type' },
    { title: '设备描述', key: 'description' },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: row.status === 'online' ? 'success' : 'error' },
          { default: () => (row.status === 'online' ? '在线' : '离线') }
        ),
    },
    { title: 'IP地址', key: 'ip' },
  ];

  // 采集参数设置
  const collectionFrequency = ref('10s');
  const storagePath = ref('D:/blast-furnace/data');
  const collectionPoints = ref(100);

  const frequencyOptions = [
    { label: '1秒', value: '1s' },
    { label: '5秒', value: '5s' },
    { label: '10秒', value: '10s' },
    { label: '30秒', value: '30s' },
    { label: '1分钟', value: '1m' },
  ];

  const selectedScenarioTemplate = ref('steady_day');
  const scriptSeed = ref<number | null>(20260304);
  const scenarioTemplateOptions = ref<Array<{ label: string; value: string }>>([
    { label: '稳态日', value: 'steady_day' },
    { label: '扰动日', value: 'disturbed_day' },
  ]);

  // 采集历史
  const historySearch = ref('');
  const historyDateRange = ref<string[] | null>(null);
  const historyList = ref<any[]>([]);

  const historyColumns = [
    {
      title: '序号',
      key: 'displayId',
      render: (row: any, index: number) => index + 1,
    },
    { title: '任务名称', key: 'taskName' },
    {
      title: '开始时间',
      key: 'startTime',
      render: (row: any) => {
        if (row.startTime) {
          // 只保留年月日部分（前10位）
          return typeof row.startTime === 'string'
            ? row.startTime.substring(0, 10)
            : String(row.startTime).substring(0, 10);
        }
        return '';
      },
    },
    {
      title: '结束时间',
      key: 'endTime',
      render: (row: any) => {
        if (row.endTime) {
          // 只保留年月日部分（前10位）
          return typeof row.endTime === 'string'
            ? row.endTime.substring(0, 10)
            : String(row.endTime).substring(0, 10);
        }
        return '';
      },
    },
    {
      title: '状态',
      key: 'status',
      render: (row: any) => {
        const statusMap: Record<string, { type: 'default' | 'success' | 'warning' | 'error' | 'info'; label: string }> = {
          completed: { type: 'success', label: '已完成' },
          running: { type: 'info', label: '进行中' },
          failed: { type: 'error', label: '失败' },
          stopped: { type: 'warning', label: '已停止' },
        };
        const status = statusMap[String(row.status || '').toLowerCase()] || { type: 'default', label: '未知状态' };
        return h(NTag, { type: status.type }, { default: () => status.label });
      },
    },
    { title: '记录数', key: 'recordCount' },
    {
      title: '操作',
      key: 'actions',
      render: (row: any) =>
        h(
          'div',
          { class: 'flex gap-2' },
          [
            h(
              NButton,
              {
                type: 'primary',
                size: 'small',
                onClick: () => downloadHistoryFile(row.id, row.taskName || '采集数据'),
              },
              { default: () => '下载' }
            ),
            h(
              NButton,
              {
                type: 'error',
                size: 'small',
                onClick: () => handleDeleteHistory(row.id),
              },
              { default: () => '删除' }
            ),
          ]
        ),
    },
  ];

  // 加载设备列表
  const loadDevices = async () => {
    console.log('开始加载设备列表');
    try {
      const res: any = await collectionApi.getDevices();
      console.log('获取设备列表响应:', res);
      if (res.code === 200) {
        deviceList.value = res.data;
        console.log('设备列表数据:', deviceList.value);
      }
    } catch (error) {
      message.error('获取设备列表失败');
      console.error('获取设备列表失败:', error);
    }
  };

  // 加载历史记录
  const loadHistory = async (params?: any) => {
    console.log('开始加载历史记录', params);
    try {
      const res: any = await collectionApi.getHistory(params);
      console.log('获取历史记录响应:', res);
      if (res.code === 200) {
        historyList.value = res.data;
        console.log('历史记录数据:', historyList.value);
      }
    } catch (error) {
      message.error('获取历史记录失败');
      console.error('获取历史记录失败:', error);
    }
  };

  // 方法
  const startCollection = async () => {
    try {
      const queryRunId = String(route.query.runId || '').trim();
      const runId = queryRunId || ensureQuickStartRunId(24);
      if (queryRunId) {
        setQuickStartRun(queryRunId);
      }
      const res: any = await collectionApi.startCollection({
        runId,
        furnaceId: selectedFurnace.value,
        frequency: collectionFrequency.value,
        storagePath: storagePath.value,
        points: collectionPoints.value,
        scriptTemplateKey: selectedScenarioTemplate.value,
        scriptSeed: scriptSeed.value,
      });
      if (res.code === 200) {
        isCollecting.value = true;
        collectionStatus.value = '采集ing';
        message.success('开始采集数据');
        loadHistory();
      } else {
        message.error(res.msg || '开始采集失败');
      }
    } catch (error) {
      message.error('开始采集失败');
      console.error('开始采集失败:', error);
    }
  };

  const loadScenarioTemplates = async () => {
    try {
      const res: any = await collectionApi.getScenarioTemplates();
      if (res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
        scenarioTemplateOptions.value = res.data.map((item: any) => ({
          label: item.label || item.key,
          value: item.key,
        }));
        if (!scenarioTemplateOptions.value.find((item) => item.value === selectedScenarioTemplate.value)) {
          selectedScenarioTemplate.value = scenarioTemplateOptions.value[0].value;
        }
      }
    } catch (error) {
      console.error('获取工况脚本模板失败:', error);
    }
  };

  const stopCollection = async () => {
    try {
      const res: any = await collectionApi.stopCollection();
      if (res.code === 200) {
        isCollecting.value = false;
        collectionStatus.value = '已停止';
        message.success('停止采集数据');
        
        // 刷新历史记录并获取最新一条
        await loadHistory();
        if (historyList.value && historyList.value.length > 0) {
          const latestHistory = historyList.value[0]; // 假设最新的在最前面，如果不是请调整排序逻辑
          
          dialog.success({
            title: '采集完成',
            content: '数据采集已完成，是否立即下载数据文件？',
            positiveText: '下载',
            negativeText: '取消',
            onPositiveClick: () => {
              downloadHistoryFile(latestHistory.id, latestHistory.taskName || '采集数据');
            }
          });
        }
      }
    } catch (error) {
      message.error('停止采集失败');
      console.error('停止采集失败:', error);
    }
  };

  const downloadHistoryFile = async (id: number, filename: string) => {
    try {
      const res = await collectionApi.downloadHistory(id);
      if (!res) return;
      
      const blob = new Blob([res as any]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      // 生成带时间戳的文件名
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
      link.download = `${filename}_${timestamp}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
      message.success('开始下载文件');
    } catch (error) {
      message.error('下载文件失败');
      console.error('下载文件失败:', error);
    }
  };

  const saveSettings = async () => {
    try {
      const res: any = await collectionApi.saveSettings({
        frequency: collectionFrequency.value,
        storagePath: storagePath.value,
        points: collectionPoints.value,
        furnaceId: selectedFurnace.value,
        scriptTemplateKey: selectedScenarioTemplate.value,
        scriptSeed: scriptSeed.value,
      });
      if (res.code === 200) {
        message.success('保存设置成功');
      }
    } catch (error) {
      message.error('保存设置失败');
      console.error('保存设置失败:', error);
    }
  };

  // 加载设置
  const loadSettings = async () => {
    try {
      const res: any = await collectionApi.getSettings();
      if (res.code === 200 && res.data) {
        collectionFrequency.value = res.data.frequency;
        storagePath.value = res.data.storagePath;
        collectionPoints.value = res.data.points;
        if (res.data.furnaceId) {
          selectedFurnace.value = res.data.furnaceId;
        }
        if (res.data.scriptTemplateKey) {
          selectedScenarioTemplate.value = res.data.scriptTemplateKey;
        }
        if (res.data.scriptSeed !== null && res.data.scriptSeed !== undefined && !Number.isNaN(Number(res.data.scriptSeed))) {
          scriptSeed.value = Number(res.data.scriptSeed);
        }
        console.log('加载设置成功:', res.data);
      }
    } catch (error) {
      message.error('获取设置失败');
      console.error('获取设置失败:', error);
    }
  };

  // 检查采集状态
  const checkCollectionStatus = async () => {
    try {
      const res: any = await collectionApi.getStatus();
      if (res.code === 200 && res.data) {
        if (res.data.isRunning) {
          isCollecting.value = true;
          collectionStatus.value = '采集ing';
          console.log('检测到正在运行的采集任务');
        } else {
          isCollecting.value = false;
          collectionStatus.value = '就绪';
        }
      }
    } catch (error) {
      console.error('获取采集状态失败:', error);
    }
  };

  const searchHistory = () => {
    // 构造搜索参数
    const params: any = {};
    if (historySearch.value) {
      params.taskName = historySearch.value;
    }
    if (historyDateRange.value && historyDateRange.value.length === 2) {
      params.startDate = historyDateRange.value[0];
      params.endDate = historyDateRange.value[1];
    }
    // 调用加载历史记录函数，传入搜索参数
    loadHistory(params);
  };

  // 删除采集历史记录
  const handleDeleteHistory = async (id: number) => {
    try {
      const res: any = await collectionApi.deleteHistory(id);
      if (res.code === 200) {
        message.success('删除采集历史记录成功');
        // 重新加载历史记录
        loadHistory();
      }
    } catch (error) {
      message.error('删除采集历史记录失败');
      console.error('删除采集历史记录失败:', error);
    }
  };

  const resetHistorySearch = () => {
    historySearch.value = '';
    historyDateRange.value = null;
    console.log('重置搜索条件');
    // 重置后立即重新加载全部数据
    loadHistory();
  };

  // 组件挂载时加载数据
  onMounted(() => {
    loadDevices();
    loadHistory();
    loadSettings();
    loadScenarioTemplates();
    checkCollectionStatus();
  });
</script>

<style scoped>
  .collect-head-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .collect-panel {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .collect-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .data-collection :deep(.n-base-selection),
  .data-collection :deep(.n-input),
  .data-collection :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>
