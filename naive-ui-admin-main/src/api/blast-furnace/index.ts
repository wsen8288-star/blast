import request from '@/utils/request';

// 数据管理API
export const dataManagementApi = {
  // 上传数据文件
  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request({
      url: '/api/data/upload',
      method: 'post',
      data: formData,
    });
  },

  // 获取单条生产数据详情
  getDataDetail: (id: number) => {
    return request({
      url: `/api/data/${id}`,
      method: 'get',
    });
  },

  // 提交生产数据
  submitData: (data: any) => {
    return request({
      url: '/api/data/submit',
      method: 'post',
      data,
    });
  },

  // 获取数据列表
  getDataList: (params: any) => {
    return request({
      url: '/api/data/list',
      method: 'get',
      params,
    });
  },
  getRecentData: (params?: any) => {
    return request({
      url: '/api/data/recent',
      method: 'get',
      params,
    });
  },

  // 更新数据
  updateData: (id: number, data: any) => {
    return request({
      url: `/api/data/${id}`,
      method: 'put',
      data,
    });
  },

  // 删除数据
  deleteData: (id: number) => {
    return request({
      url: `/api/data/${id}`,
      method: 'delete',
    });
  },

  // 导出数据
  exportData: (params: any) => {
    return request({
      url: '/api/data/export',
      method: 'get',
      params,
      responseType: 'blob',
    });
  },

  // 数据预处理
  preprocessData: (data: any) => {
    return request({
      url: '/api/data/process',
      method: 'post',
      data,
    });
  },

  getDataSpec: () => {
    return request({
      url: '/api/data/spec',
      method: 'get',
    });
  },

  getQualityMetrics: (params?: any) => {
    return request({
      url: '/api/data/quality-metrics',
      method: 'get',
      params,
    });
  },

  exportSnapshot: (data: any) => {
    return request({
      url: '/api/data/snapshot/export',
      method: 'post',
      data,
    });
  },

  importSnapshot: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request({
      url: '/api/data/snapshot/import',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  getImportTemplates: () => {
    return request({
      url: '/api/data/import/templates',
      method: 'get',
    });
  },

  previewExternalImport: (file: File, templateKey?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (templateKey) {
      formData.append('templateKey', templateKey);
    }
    return request({
      url: '/api/data/import/preview',
      method: 'post',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  confirmExternalImport: (previewId: string, fileName?: string) => {
    return request({
      url: '/api/data/import/confirm',
      method: 'post',
      data: {
        previewId,
        fileName,
      },
    });
  },

  // 更新原始文件
  updateFile: (data: any) => {
    return request({
      url: '/api/data/update-file',
      method: 'post',
      data,
    });
  },

  // 获取存储状态
  getStorageStatus: () => {
    return request({
      url: '/api/data/storage/status',
      method: 'get',
    });
  },

  // 获取存储设备列表
  getStorageDevices: () => {
    return request({
      url: '/api/data/storage/devices',
      method: 'get',
    });
  },

  // 保存存储配置
  saveStorageConfig: (config: any) => {
    return request({
      url: '/api/data/storage/config',
      method: 'post',
      data: config,
    });
  },

  // 获取存储配置
  getStorageConfig: () => {
    return request({
      url: '/api/data/storage/config',
      method: 'get',
    });
  },

  getLatestData: (params?: any) => {
    return request({
      url: '/api/data/latest',
      method: 'get',
      params,
    });
  },

  getSchemeCandidates: (params?: any) => {
    return request({
      url: '/api/data/schemes',
      method: 'get',
      params,
    });
  },

  // 开始备份
  startBackup: () => {
    return request({
      url: '/api/data/storage/backup',
      method: 'post',
    });
  },

  // 获取备份历史
  getBackupHistory: () => {
    return request({
      url: '/api/data/storage/backup/history',
      method: 'get',
    });
  },

  // 恢复数据
  restoreData: (backupPoint: string) => {
    return request({
      url: '/api/data/storage/restore',
      method: 'post',
      data: { backupPoint, backupTime: backupPoint },
    });
  },

  // 获取备份/恢复任务状态
  getStorageTaskStatus: (taskId: string) => {
    return request({
      url: `/api/data/storage/task/${encodeURIComponent(taskId)}`,
      method: 'get',
    });
  },

  // 删除备份历史
  deleteBackupHistory: (backupId: number) => {
    return request({
      url: `/api/data/storage/backup/history/${backupId}`,
      method: 'delete',
    });
  },
};

// 异常检测API
export const anomalyApi = {
  // 开始检测
  detect: (data: any) => {
    return request({
      url: '/api/anomaly/detect',
      method: 'post',
      data,
    });
  },
  startSchedule: (data: any) => {
    return request({
      url: '/api/anomaly/schedule/start',
      method: 'post',
      data,
    });
  },
  stopSchedule: () => {
    return request({
      url: '/api/anomaly/schedule/stop',
      method: 'post',
    });
  },
  getScheduleStatus: () => {
    return request({
      url: '/api/anomaly/schedule/status',
      method: 'get',
    });
  },

  // 获取实时异常
  getRealtimeAnomalies: (params: any) => {
    return request({
      url: '/api/anomaly/realtime',
      method: 'get',
      params,
    });
  },

  // 获取历史异常
  getHistoryAnomalies: (params: any) => {
    return request({
      url: '/api/anomaly/history',
      method: 'get',
      params,
    });
  },

  // 获取统计数据
  getStatistics: (params?: any) => {
    return request({
      url: '/api/anomaly/stats',
      method: 'get',
      params,
    });
  },

  // 处理异常
  handleAnomaly: (id: number, params: any) => {
    return request({
      url: `/api/anomaly/${id}/handle`,
      method: 'put',
      data: params, // Change params to data for RequestBody
    });
  },

  // 获取图表数据
  getChartData: () => {
    return request({
      url: '/api/anomaly/charts',
      method: 'get',
    });
  },
};

export const warningApi = {
  getList: (params: any) => {
    return request({
      url: '/api/warning/list',
      method: 'get',
      params,
    });
  },
  handle: (data: any) => {
    return request({
      url: '/api/warning/handle',
      method: 'put',
      data,
    });
  },
  handleBatch: (data: any) => {
    return request({
      url: '/api/warning/handle/batch',
      method: 'put',
      data,
    });
  },
  getStats: (params?: any) => {
    return request({
      url: '/api/warning/stats',
      method: 'get',
      params,
    });
  },
};

// 异常配置API
export const anomalyConfigApi = {
  getThresholds: (params: any) => {
    return request({
      url: '/api/anomaly/config/thresholds',
      method: 'get',
      params,
    });
  },
  getEffectiveThresholds: (params: any) => {
    return request({
      url: '/api/anomaly/config/thresholds/effective',
      method: 'get',
      params,
    });
  },
  saveThreshold: (data: any) => {
    return request({
      url: '/api/anomaly/config/thresholds',
      method: 'post',
      data,
    });
  },
  deleteThreshold: (id: number) => {
    return request({
      url: `/api/anomaly/config/thresholds/${id}`,
      method: 'delete',
    });
  },
};

// 可视化分析API
export const visualizationApi = {
  // 获取单指标趋势数据
  getSingleMetricData: (params: any) => {
    return request({
      url: '/api/visualization/single',
      method: 'get',
      params,
    });
  },

  // 获取多指标关联数据
  getMultiMetricData: (params: any) => {
    return request({
      url: '/api/visualization/multi',
      method: 'get',
      params,
    });
  },

  // 获取工况对比数据
  getComparisonData: (params: any) => {
    return request({
      url: '/api/visualization/comparison',
      method: 'get',
      params,
    });
  },

  // 获取高炉列表
  getFurnaceList: () => {
    return request({
      url: '/api/visualization/furnaces',
      method: 'get',
    });
  },

  // 生成报表
  generateReport: (data: any) => {
    return request({
      url: '/api/visualization/report/generate',
      method: 'post',
      data,
    });
  },
  // 获取报表历史列表
  getReportHistory: () => {
    return request({
      url: '/api/report/history/list',
      method: 'get',
    });
  },
  // [新增] 删除报表 (修复报错的关键)
  deleteReport: (id: number) => {
    return request({
      url: `/api/report/history/${id}`,
      method: 'delete',
    });
  },
  // [新增] 清理无效记录
  cleanReportHistory: () => {
    return request({
      url: '/api/report/history/clean',
      method: 'delete',
    });
  },
  downloadReport: (id: number) => {
    return request({
      url: `/api/report/history/${id}/download`,
      method: 'get',
    });
  },
};

// 优化模型API
export const optimizationApi = {
  // 开始优化
  startOptimization: (params: any) => {
    return request({
      url: '/api/optimization/start',
      method: 'post',
      data: params,
    });
  },
  startEvolutionaryOptimization: (params: any) => {
    return request({
      url: '/api/optimization/evolution/start',
      method: 'post',
      data: params,
      timeout: 60000,
    });
  },

  // 获取优化进度
  getOptimizationProgress: (taskId: string) => {
    return request({
      url: `/api/optimization/progress/${taskId}`,
      method: 'get',
    });
  },

  // 获取优化结果
  getOptimizationResult: (taskId: string) => {
    return request({
      url: `/api/optimization/result/${taskId}`,
      method: 'get',
    });
  },

  // 验证模型
  validateModel: (params: any) => {
    return request({
      url: '/api/optimization/validate',
      method: 'post',
      data: params,
    });
  },

  // 模型评估历史
  getEvaluationHistory: () => {
    return request({
      url: '/api/optimization/evaluation/history',
      method: 'get',
    });
  },

  getEvaluationDetail: (id: number) => {
    return request({
      url: `/api/optimization/evaluation/${id}`,
      method: 'get',
    });
  },

  // 删除评估历史
  deleteEvaluation: (id: number) => {
    return request({
      url: `/api/optimization/evaluation/${id}`,
      method: 'delete',
    });
  },

  // 批量删除评估历史
  deleteEvaluationBatch: (ids: number[]) => {
    return request({
      url: '/api/optimization/evaluation/batch',
      method: 'post',
      data: ids,
    });
  },

  // 保存优化方案
  saveOptimizationResult: (data: any) => {
    return request({
      url: '/api/optimization/save',
      method: 'post',
      data,
    });
  },

  deployment: {
    deploy: (data: any) => {
      return request({
        url: '/api/optimization/model/deployment/deploy',
        method: 'post',
        data,
      });
    },
    getHistory: () => {
      return request({
        url: '/api/optimization/model/deployment/history',
        method: 'get',
      });
    },
    cancel: (deploymentId: number) => {
      return request({
        url: `/api/optimization/model/deployment/${deploymentId}/cancel`,
        method: 'post',
      });
    },
    retry: (deploymentId: number) => {
      return request({
        url: `/api/optimization/model/deployment/${deploymentId}/retry`,
        method: 'post',
      });
    },
    deleteHistory: (deploymentId: number) => {
      return request({
        url: `/api/optimization/model/deployment/${deploymentId}`,
        method: 'delete',
      });
    },
    deleteHistoryBatch: (ids: number[]) => {
      return request({
        url: '/api/optimization/model/deployment/batch',
        method: 'post',
        data: ids,
      });
    },
    getServices: () => {
      return request({
        url: '/api/optimization/model/deployment/services',
        method: 'get',
      });
    },
    getRunningServices: () => {
      return request({
        url: '/api/optimization/model/deployment/services',
        method: 'get',
      });
    },
    startService: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/start`,
        method: 'post',
      });
    },
    restartService: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/restart`,
        method: 'post',
      });
    },
    stopService: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/stop`,
        method: 'post',
      });
    },
    getServiceLogs: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/logs`,
        method: 'get',
      });
    },
    getServiceHealth: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/health`,
        method: 'get',
      });
    },
    getServiceConfig: (id: number) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/config`,
        method: 'get',
      });
    },
    updateServiceConfig: (id: number, data: any) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/config`,
        method: 'post',
        data,
      });
    },
    updateServiceName: (id: number, name: string) => {
      return request({
        url: `/api/optimization/model/deployment/service/${id}/name`,
        method: 'post',
        data: { name },
      });
    },
    predict: (serviceId: number, data: any) => {
      return request({
        url: `/api/optimization/model/deployment/predict/${serviceId}`,
        method: 'post',
        data,
      });
    },
    explain: (serviceId: number, data: any) => {
      return request({
        url: `/api/optimization/model/deployment/predict/${serviceId}/explain`,
        method: 'post',
        data,
      });
    },
  },

  // 模型训练相关API
  modelTraining: {
    // 开始模型训练
    startTraining: (params: { training: Record<string, any>; config: Record<string, any> }) => {
      return request({
        url: '/api/optimization/model/train',
        method: 'post',
        data: params,
      });
    },

    // 获取训练状态
    getTrainingStatus: (trainingId: number) => {
      return request({
        url: `/api/optimization/model/training/${trainingId}`,
        method: 'get',
      });
    },

    // 获取训练日志
    getTrainingLogs: (trainingId: number) => {
      return request({
        url: `/api/optimization/model/training/${trainingId}/logs`,
        method: 'get',
      });
    },

    // 保存模型配置
    saveModelConfig: (config: any) => {
      return request({
        url: '/api/optimization/model/config',
        method: 'post',
        data: config,
      });
    },

    // 获取模型配置
    getModelConfig: (configId: number) => {
      return request({
        url: `/api/optimization/model/config/${configId}`,
        method: 'get',
      });
    },

    // 获取所有模型配置
    getAllModelConfigs: () => {
      return request({
        url: '/api/optimization/model/configs',
        method: 'get',
      });
    },

    // 获取训练历史
    getTrainingHistory: () => {
      return request({
        url: '/api/optimization/model/training-history',
        method: 'get',
      });
    },

    // 取消训练任务
    cancelTraining: (trainingId: number) => {
      return request({
        url: `/api/optimization/model/training/${trainingId}/cancel`,
        method: 'post',
      });
    },

    // 删除训练历史
    deleteTraining: (trainingId: number) => {
      return request({
        url: `/api/optimization/model/training/${trainingId}`,
        method: 'delete',
      });
    },

    // 批量删除训练历史
    deleteTrainingBatch: (trainingIds: number[]) => {
      return request({
        url: '/api/optimization/model/training/batch',
        method: 'post',
        data: trainingIds,
      });
    },

    // 导出模型
    exportModel: (trainingId: number) => {
      return request({
        url: `/api/optimization/model/export/${trainingId}`,
        method: 'get',
      });
    },
  },
};

// 方案对比与预警API
export const comparisonApi = {
  // 获取对比数据
  getComparisonData: (params: any) => {
    return request({
      url: '/api/comparison/data',
      method: 'get',
      params,
    });
  },

  // 导出对比结果
  exportComparisonData: (params: any) => {
    return request({
      url: '/api/comparison/export',
      method: 'get',
      params,
      responseType: 'blob',
    });
  },
  getEvolutionHistory: (params?: any) => {
    return request({
      url: '/api/comparison/evolution/history',
      method: 'get',
      params,
    });
  },
  getEvolutionHistoryDetail: (id: number) => {
    return request({
      url: `/api/comparison/evolution/history/${id}`,
      method: 'get',
    });
  },
  getCompareHistory: (params?: any) => {
    return request({
      url: '/api/comparison/compare/history',
      method: 'get',
      params,
    });
  },
  getCompareHistoryDetail: (id: number) => {
    return request({
      url: `/api/comparison/compare/history/${id}`,
      method: 'get',
    });
  },
  deleteHistory: (id: number) => {
    return request({
      url: `/api/comparison/history/${id}`,
      method: 'delete',
    });
  },
  batchDeleteHistory: (ids: number[]) => {
    return request({
      url: '/api/comparison/history/batch',
      method: 'delete',
      data: ids,
    });
  },
  compareEvolution: (params: any) => {
    return request({
      url: '/api/comparison/evolution/compare',
      method: 'get',
      params,
    });
  },
  compareEvolutionBaseline: (params: any) => {
    return request({
      url: '/api/comparison/evolution/baseline/compare',
      method: 'get',
      params,
    });
  },
  compareProduction: (params: any) => {
    return request({
      url: '/api/comparison/production/compare',
      method: 'get',
      params,
    });
  },
  adoptScheme: (historyId: number, schemeIndex: number) => {
    return request({
      url: `/api/comparison/adopt/${historyId}/${schemeIndex}`,
      method: 'post',
    });
  },

  getOperationLogs: (params?: any) => {
    return request({
      url: '/api/operation/logs',
      method: 'get',
      params,
    });
  },
  // 获取预警设置
  getWarningSettings: () => {
    return request({
      url: '/api/warning/settings',
      method: 'get',
    });
  },

  // 更新预警设置
  updateWarningSettings: (data: any) => {
    return request({
      url: '/api/warning/settings',
      method: 'put',
      data,
    });
  },

  // 获取预警记录
  getWarningList: (params: any) => {
    return request({
      url: '/api/warning/list',
      method: 'get',
      params,
    });
  },

  // 处理预警
  handleWarning: (id: number) => {
    return request({
      url: `/api/warning/handle/${id}`,
      method: 'put',
    });
  },
};

export const homeApi = {
  getQuickStartProgress: (params?: any) => {
    return request({
      url: '/api/home/quick-start/progress',
      method: 'get',
      params,
    });
  },
};

// 数据采集API
export const collectionApi = {
  // 获取采集设备列表
  getDevices: () => {
    return request({
      url: '/api/collection/devices',
      method: 'get',
    });
  },

  // 获取采集历史记录
  getHistory: (params?: any) => {
    return request({
      url: '/api/collection/history',
      method: 'get',
      params,
    });
  },

  // 开始采集
  startCollection: (params: any) => {
    return request({
      url: '/api/collection/start',
      method: 'post',
      data: params,
    });
  },

  // 停止采集
  stopCollection: () => {
    return request({
      url: '/api/collection/stop',
      method: 'post',
    });
  },

  // 保存采集设置
  saveSettings: (settings: any) => {
    return request({
      url: '/api/collection/settings',
      method: 'post',
      data: settings,
    });
  },

  // 获取采集设置
  getSettings: () => {
    return request({
      url: '/api/collection/settings',
      method: 'get',
    });
  },

  // 获取采集状态
  getStatus: () => {
    return request({
      url: '/api/collection/status',
      method: 'get',
    });
  },

  getScenarioTemplates: () => {
    return request({
      url: '/api/collection/scenario-templates',
      method: 'get',
    });
  },

  // 删除采集历史记录
  deleteHistory: (id: number) => {
    return request({
      url: `/api/collection/history/${id}`,
      method: 'delete',
    });
  },

  // 下载采集历史文件
  downloadHistory: (id: number) => {
    return request({
      url: `/api/collection/history/${id}/download`,
      method: 'get',
      responseType: 'blob',
    });
  },
};

// 系统管理API
export const systemApi = {
  // 用户管理
  user: {
    // 获取用户列表
    getList: (params: any) => {
      return request({
        url: '/api/system/users',
        method: 'get',
        params,
      });
    },

    // 添加用户
    add: (data: any) => {
      return request({
        url: '/api/system/users',
        method: 'post',
        data,
      });
    },

    // 更新用户
    update: (id: number, data: any) => {
      return request({
        url: `/api/system/users/${id}`,
        method: 'put',
        data,
      });
    },

    // 删除用户
    delete: (id: number) => {
      return request({
        url: `/api/system/users/${id}`,
        method: 'delete',
      });
    },

    // 更新用户状态（启用/禁用）
    updateStatus: (id: number, data: any) => {
      return request({
        url: `/api/system/users/${id}/status`,
        method: 'put',
        data,
      });
    },

    // 批量更新用户状态（启用/禁用）
    batchUpdateStatus: (data: any) => {
      return request({
        url: '/api/system/users/status/batch',
        method: 'put',
        data,
      });
    },

    // 重置密码
    resetPassword: (id: number, data?: any) => {
      return request({
        url: `/api/system/users/${id}/reset-password`,
        method: 'post',
        data,
      });
    },

    // 批量重置密码
    batchResetPassword: (data: any) => {
      return request({
        url: '/api/system/users/reset-password/batch',
        method: 'post',
        data,
      });
    },
  },

  role: {
    getList: () => {
      return request({
        url: '/api/system/roles',
        method: 'get',
      });
    },
    create: (data: any) => {
      return request({
        url: '/api/system/roles',
        method: 'post',
        data,
      });
    },
    update: (id: number, data: any) => {
      return request({
        url: `/api/system/roles/${id}`,
        method: 'put',
        data,
      });
    },
    delete: (id: number) => {
      return request({
        url: `/api/system/roles/${id}`,
        method: 'delete',
      });
    },
    batchDelete: (ids: number[]) => {
      return request({
        url: '/api/system/roles/batch-delete',
        method: 'post',
        data: { ids },
      });
    },
    getPermissions: (id: number) => {
      return request({
        url: `/api/system/roles/${id}/permissions`,
        method: 'get',
      });
    },
    setPermissions: (id: number, permissionIds: number[]) => {
      return request({
        url: `/api/system/roles/${id}/permissions`,
        method: 'post',
        data: { permissionIds },
      });
    },
  },

  permission: {
    getList: () => {
      return request({
        url: '/api/system/permissions',
        method: 'get',
      });
    },
  },

  config: {
    getListByGroup: (group: string) => {
      return request({
        url: '/api/system/configs',
        method: 'get',
        params: { group },
      });
    },
    create: (data: any) => {
      return request({
        url: '/api/system/configs',
        method: 'post',
        data,
      });
    },
    batchUpdate: (group: string, items: any[]) => {
      return request({
        url: '/api/system/configs',
        method: 'put',
        data: { group, items },
      });
    },
  },

  log: {
    getList: (params: any) => {
      return request({
        url: '/api/system/logs',
        method: 'get',
        params,
      });
    },
    getOperationList: (params: any) => {
      return request({
        url: '/api/operation/logs',
        method: 'get',
        params,
      });
    },
  },
};
