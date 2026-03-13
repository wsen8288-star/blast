<template>
  <div class="data-storage w-full p-4 md:p-6 box-border">
    <n-card title="数据存储" class="mb-4 border">
      <n-tabs type="line" default-value="status">
        <n-tab-pane name="status" tab="存储状态">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <div class="grid grid-cols-2 gap-4 mb-4">
              <n-card size="small" class="border">
                <n-statistic label="总存储容量" :value="totalStorage" suffix="GB" />
                <n-progress
                  type="line"
                  :percentage="usedStoragePercentage"
                  :show-text="true"
                  class="mt-2"
                />
                <n-text depth="3" class="mt-1"
                  >已使用 {{ usedStorage }} GB，剩余 {{ remainingStorage }} GB</n-text
                >
              </n-card>
              <n-card size="small" class="border">
                <n-statistic label="数据文件数" :value="fileCount" />
                <n-statistic label="最近备份时间" :value="lastBackupTime" class="mt-2" />
                <n-tag
                  :type="backupStatus === 'success' ? 'success' : 'warning'"
                  size="small"
                  class="mt-2"
                >
                  备份状态: {{ backupStatus === 'success' ? '正常' : '需要备份' }}
                </n-tag>
              </n-card>
            </div>

            <n-card title="存储设备状态" size="small" class="mb-4 border">
              <div>
                <p>存储设备列表：</p>
                <n-data-table :columns="storageDeviceColumns" :data="storageDeviceList" size="small" />
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="configuration" tab="存储配置">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card title="存储路径设置" size="small" class="mb-4 border">
              <n-form label-placement="left" label-width="150">
                <n-form-item label="主存储路径">
                  <n-input v-model:value="mainStoragePath" placeholder="输入主存储路径" />
                </n-form-item>
                <n-form-item label="备份存储路径">
                  <n-input v-model:value="backupStoragePath" placeholder="输入备份存储路径" />
                </n-form-item>
                <n-form-item label="存储格式">
                  <n-select
                    v-model:value="storageFormat"
                    :options="storageFormatOptions"
                    placeholder="选择存储格式"
                  />
                </n-form-item>
                <n-form-item label="自动备份">
                  <n-switch v-model:value="autoBackup" />
                </n-form-item>
                <n-form-item label="备份频率" :disabled="!autoBackup">
                  <n-select
                    v-model:value="backupFrequency"
                    :options="backupFrequencyOptions"
                    placeholder="选择备份频率"
                  />
                </n-form-item>
                <n-form-item>
                  <n-button type="primary" @click="saveStorageConfig">保存配置</n-button>
                  <n-button @click="resetStorageConfig" class="ml-2">重置配置</n-button>
                </n-form-item>
              </n-form>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="backup" tab="数据备份">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <div class="flex items-center justify-between mb-4">
              <n-text depth="2">备份管理</n-text>
              <n-button type="primary" @click="startManualBackup">手动备份</n-button>
            </div>

            <n-card title="备份历史" size="small" class="mb-4 border">
              <n-data-table
                :columns="backupHistoryColumns"
                :data="backupHistoryList"
                size="small"
              />
            </n-card>

            <n-card title="备份恢复" size="small" class="border">
              <div class="flex items-center mb-4">
                <n-select
                  v-model:value="restorePoint"
                  :options="restorePointOptions"
                  placeholder="选择恢复点"
                  class="mr-2"
                />
                <n-button type="error" @click="restoreFromBackup">恢复数据</n-button>
              </div>
              <n-text depth="3" type="warning">注意：恢复操作会覆盖当前数据，请谨慎操作！</n-text>
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, h, onMounted } from 'vue';
  import { useDialog, useMessage, NTag, NDataTable, NButton, NInput } from 'naive-ui';
  import { dataManagementApi } from '@/api/blast-furnace/index';

  const message = useMessage();
  const dialog = useDialog();
  const restoreConfirmInput = ref('');

  // 存储状态
  const totalStorage = ref(0);
  const usedStorage = ref(0);
  const remainingStorage = ref(0);
  const usedStoragePercentage = ref(0);
  const fileCount = ref(0);
  const lastBackupTime = ref('');
  const backupStatus = ref('');
  const isLoading = ref(false);

  // 存储设备列表
  interface StorageDevice {
    id: number;
    name: string;
    type: string;
    status: string;
    capacity: number;
    used: number;
    remaining: number;
    usage: number;
  }
  const storageDeviceList = ref<StorageDevice[]>([]);

  // 获取存储状态
  const getStorageStatus = async () => {
    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.getStorageStatus();
      if (res.code === 200) {
        const data = res.data;
        totalStorage.value = data.totalStorage;
        usedStorage.value = data.usedStorage;
        remainingStorage.value = data.remainingStorage;
        usedStoragePercentage.value = data.usedStoragePercentage;
        fileCount.value = data.fileCount;
        lastBackupTime.value = data.lastBackupTime;
        backupStatus.value = data.backupStatus;

        // 更新存储设备列表
        if (data.storageDevices && data.storageDevices.length > 0) {
          storageDeviceList.value = data.storageDevices.map((device: any) => ({
            id: device.id,
            name: device.name,
            type: device.type,
            status: device.status,
            capacity: device.capacity,
            used: device.used,
            remaining: device.remaining,
            usage: device.usagePercentage,
          }));
        }
      } else {
        message.error('获取存储状态失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('获取存储状态失败');
      console.error('获取存储状态失败:', error);
    } finally {
      isLoading.value = false;
    }
  };

  // 组件挂载时获取存储状态、存储配置和备份历史
  onMounted(() => {
    getStorageStatus();
    getStorageConfig();
    getBackupHistory();
  });

  const storageDeviceColumns = [
    { title: '设备ID', key: 'id' },
    { title: '设备名称', key: 'name' },
    { title: '设备类型', key: 'type' },
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
    { title: '容量(GB)', key: 'capacity' },
    { title: '已使用(GB)', key: 'used' },
    { title: '剩余(GB)', key: 'remaining' },
    { title: '使用率(%)', key: 'usage' },
  ];

  // 存储配置
  const mainStoragePath = ref('D:/blast-furnace/data/main');
  const backupStoragePath = ref('D:/blast-furnace/data/backup');
  const storageFormat = ref('parquet');
  const storageFormatOptions = [
    { label: 'Parquet', value: 'parquet' },
    { label: 'CSV', value: 'csv' },
    { label: 'JSON', value: 'json' },
    { label: 'Excel', value: 'excel' },
  ];

  const autoBackup = ref(true);
  const backupFrequency = ref('daily');
  const backupFrequencyOptions = [
    { label: '每天', value: 'daily' },
    { label: '每周', value: 'weekly' },
    { label: '每月', value: 'monthly' },
  ];

  // 获取存储配置
  const getStorageConfig = async () => {
    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.getStorageConfig();
      if (res.code === 200) {
        const data = res.data;
        mainStoragePath.value = data.mainStoragePath || 'D:/blast-furnace/data/main';
        backupStoragePath.value = data.backupStoragePath || 'D:/blast-furnace/data/backup';
        storageFormat.value = data.storageFormat || 'parquet';
        autoBackup.value = !!data.autoBackup;
        backupFrequency.value = data.backupFrequency || 'daily';
      } else {
        message.error('获取存储配置失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('获取存储配置失败');
      console.error('获取存储配置失败:', error);
    } finally {
      isLoading.value = false;
    }
  };

  // 备份历史
  interface BackupHistoryItem {
    id: number;
    backupTime: string;
    backupSize: string;
    status: string;
    backupType: string;
    backupPath?: string;
    restoreSourceBackupId?: number | null;
    operatorName?: string;
    sourceIp?: string;
  }

  interface RestorePointOption {
    label: string;
    value: string;
  }

  const backupHistoryList = ref<BackupHistoryItem[]>([]);

  // 时间格式化函数
  const formatDateTime = (dateString: string): string => {
    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  };

  const backupHistoryColumns = [
    {
      title: '序号',
      key: 'displayId',
      render: (row: any, index: number) => index + 1,
    },
    {
      title: '备份时间',
      key: 'backupTime',
      render: (row: any) => formatDateTime(row.backupTime),
    },
    { title: '备份大小', key: 'backupSize' },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: row.status === 'success' ? 'success' : 'error' },
          { default: () => (row.status === 'success' ? '成功' : '失败') }
        ),
    },
    {
      title: '备份类型',
      key: 'backupType',
      render: (row: any) => {
        const typeMap: Record<string, { type: 'default' | 'success' | 'warning' | 'error' | 'info'; label: string }> = {
          auto: { type: 'info', label: '自动备份' },
          manual: { type: 'warning', label: '手动备份' },
          restore: { type: 'success', label: '恢复记录' },
        };
        const backupType = typeMap[String(row.backupType || '').toLowerCase()] || { type: 'default', label: '其他' };
        return h(NTag, { type: backupType.type }, { default: () => backupType.label });
      },
    },
    {
      title: '恢复来源',
      key: 'restoreSourceBackupId',
      render: (row: any) => (row.backupType === 'restore' ? `#${row.restoreSourceBackupId || '-'}` : '-'),
    },
    {
      title: '操作人',
      key: 'operatorName',
      render: (row: any) => row.operatorName || '-',
    },
    {
      title: '来源IP',
      key: 'sourceIp',
      render: (row: any) => row.sourceIp || '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (row: any) =>
        h(
          NButton,
          {
            type: 'error',
            size: 'small',
            onClick: () => deleteBackupHistory(row.id),
          },
          { default: () => '删除' }
        ),
    },
  ];

  // 恢复点
  const restorePoint = ref('');
  const restorePointOptions = ref<RestorePointOption[]>([]);

  // 从备份历史生成恢复点选项
  const generateRestorePointOptions = () => {
    restorePointOptions.value = backupHistoryList.value
      .filter((item) => item.status === 'success')
      .map((item) => ({
        label: `${formatDateTime(item.backupTime)} ｜ ${item.backupSize || '未知大小'}`,
        value: String(item.id),
      }));
  };

  // 方法
  const saveStorageConfig = async () => {
    try {
      isLoading.value = true;
      const config = {
        mainStoragePath: mainStoragePath.value || 'D:/blast-furnace/data/main',
        backupStoragePath: backupStoragePath.value || 'D:/blast-furnace/data/backup',
        storageFormat: storageFormat.value || 'parquet',
        autoBackup: !!autoBackup.value,
        backupFrequency: backupFrequency.value || 'daily',
      };
      const res: any = await dataManagementApi.saveStorageConfig(config);
      if (res.code === 200) {
        message.success('保存存储配置成功');
      } else {
        message.error('保存存储配置失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('保存存储配置失败');
      console.error('保存存储配置失败:', error);
    } finally {
      isLoading.value = false;
    }
  };

  const resetStorageConfig = () => {
    mainStoragePath.value = 'D:/blast-furnace/data/main';
    backupStoragePath.value = 'D:/blast-furnace/data/backup';
    storageFormat.value = 'parquet';
    autoBackup.value = true;
    backupFrequency.value = 'daily';
    message.success('重置存储配置成功');
  };

  const waitStorageTask = async (taskId: string, pendingText: string) => {
    if (!taskId) {
      throw new Error('任务ID为空');
    }
    message.loading(pendingText, { duration: 0 });
    return await new Promise<any>((resolve, reject) => {
      let done = false;
      const timer = window.setInterval(async () => {
        if (done) return;
        try {
          const res: any = await dataManagementApi.getStorageTaskStatus(taskId);
          if (res?.code !== 200) {
            return;
          }
          const data = res?.data || {};
          if (!data?.exists) {
            return;
          }
          const status = String(data.status || '');
          if (status === 'SUCCESS') {
            done = true;
            window.clearInterval(timer);
            resolve(data);
          } else if (status === 'FAILED') {
            done = true;
            window.clearInterval(timer);
            reject(new Error(data?.message || '任务执行失败'));
          }
        } catch (_) {
        }
      }, 1000);
      window.setTimeout(() => {
        if (done) return;
        done = true;
        window.clearInterval(timer);
        reject(new Error('任务执行超时'));
      }, 10 * 60 * 1000);
    });
  };

  const startManualBackup = async () => {
    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.startBackup();
      if (res.code === 200) {
        const taskId = res?.data?.taskId;
        await waitStorageTask(taskId, '备份中，请稍候...');
        message.success('手动备份完成');
        // 刷新备份历史
        getBackupHistory();
      } else {
        message.error('备份失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('备份失败');
      console.error('备份失败:', error);
    } finally {
      isLoading.value = false;
      message.destroyAll();
    }
  };

  const getBackupHistory = async () => {
    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.getBackupHistory();
      if (res.code === 200) {
        backupHistoryList.value = res.data;
        // 生成恢复点选项
        generateRestorePointOptions();
      } else {
        message.error('获取备份历史失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('获取备份历史失败');
      console.error('获取备份历史失败:', error);
    } finally {
      isLoading.value = false;
    }
  };

  const restoreFromBackup = async () => {
    if (!restorePoint.value) {
      message.error('请选择恢复点');
      return;
    }
    const selected = backupHistoryList.value.find((item) => String(item.id) === restorePoint.value);
    const backupLabel = selected ? `${formatDateTime(selected.backupTime)} / ${selected.backupSize}` : restorePoint.value;
    restoreConfirmInput.value = '';
    const confirmed = await new Promise<boolean>((resolve) => {
      const d = dialog.warning({
        title: '确认恢复数据',
        content: () =>
          h('div', { style: 'display:flex;flex-direction:column;gap:10px;' }, [
            h('div', null, `恢复会覆盖当前数据。即将恢复版本：${backupLabel}`),
            h('div', null, '请输入 RESTORE 确认继续：'),
            h(NInput, {
              value: restoreConfirmInput.value,
              placeholder: 'RESTORE',
              onUpdateValue: (v: string) => (restoreConfirmInput.value = v),
            }),
          ]),
        positiveText: '继续恢复',
        negativeText: '取消',
        onPositiveClick: () => {
          const ok = restoreConfirmInput.value === 'RESTORE';
          if (!ok) {
            message.error('请输入 RESTORE 确认');
            return false;
          }
          resolve(true);
          return true;
        },
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
      });
      void d;
    });
    if (!confirmed) {
      message.warning('已取消恢复');
      return;
    }

    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.restoreData(restorePoint.value);
      if (res.code === 200) {
        const taskId = res?.data?.taskId;
        await waitStorageTask(taskId, '恢复中，请稍候...');
        message.success('恢复数据成功');
      } else {
        message.error('恢复数据失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('恢复数据失败');
      console.error('恢复数据失败:', error);
    } finally {
      isLoading.value = false;
      message.destroyAll();
    }
  };

  // 删除备份历史
  const deleteBackupHistory = async (backupId: number) => {
    try {
      isLoading.value = true;
      const res: any = await dataManagementApi.deleteBackupHistory(backupId);
      if (res.code === 200) {
        message.success('删除备份历史成功');
        // 刷新备份历史
        getBackupHistory();
      } else {
        message.error('删除备份历史失败: ' + (res.msg || '未知错误'));
      }
    } catch (error) {
      message.error('删除备份历史失败');
      console.error('删除备份历史失败:', error);
    } finally {
      isLoading.value = false;
    }
  };
</script>
