import { request as serviceRequest } from '@/service/request';

type RequestExtra = Record<string, unknown>;

type LegacyResponse<T = unknown> = {
  code: number;
  msg: string;
  data: T;
};

async function legacyRequest(method: 'get' | 'post' | 'put' | 'delete', url: string, payload?: unknown, extra?: RequestExtra) {
  const isBlob = extra?.responseType === 'blob';
  const config = method === 'get' ? { url, method, params: payload, ...extra } : { url, method, data: payload, ...extra };
  const { data, error } = await serviceRequest<unknown>(config);
  if (error) {
    throw error;
  }
  if (isBlob) {
    return data;
  }
  return {
    code: 200,
    msg: 'ok',
    data
  } satisfies LegacyResponse;
}

const get = (url: string, params?: unknown, extra?: RequestExtra) => legacyRequest('get', url, params, extra);
const post = (url: string, data?: unknown, extra?: RequestExtra) => legacyRequest('post', url, data, extra);
const put = (url: string, data?: unknown, extra?: RequestExtra) => legacyRequest('put', url, data, extra);
const del = (url: string, data?: unknown, extra?: RequestExtra) => legacyRequest('delete', url, data, extra);

export const dataManagementApi = {
  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return post('/api/data/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  getDataDetail: (id: number) => get(`/api/data/${id}`),
  submitData: (data: unknown) => post('/api/data/submit', data),
  getDataList: (params: unknown) => get('/api/data/list', params),
  getLatestData: (params?: unknown) => get('/api/data/latest', params),
  getRecentData: (params?: unknown) => get('/api/data/recent', params),
  preprocessData: (data: unknown) => post('/api/data/process', data),
  getDataSpec: () => get('/api/data/spec'),
  getQualityMetrics: (params?: unknown) => get('/api/data/quality-metrics', params),
  exportSnapshot: (data: unknown) => post('/api/data/snapshot/export', data),
  getStorageStatus: () => get('/api/data/storage/status'),
  getStorageDevices: () => get('/api/data/storage/devices'),
  saveStorageConfig: (config: unknown) => post('/api/data/storage/config', config),
  getStorageConfig: () => get('/api/data/storage/config'),
  startBackup: () => post('/api/data/storage/backup'),
  getBackupHistory: () => get('/api/data/storage/backup/history'),
  restoreData: (backupPoint: string) => post('/api/data/storage/restore', { backupPoint, backupTime: backupPoint }),
  getStorageTaskStatus: (taskId: string) => get(`/api/data/storage/task/${encodeURIComponent(taskId)}`),
  deleteBackupHistory: (backupId: number) => del(`/api/data/storage/backup/history/${backupId}`),
  getSchemeCandidates: (params?: unknown) => get('/api/data/schemes', params),
  getImportTemplates: () => get('/api/data/import/templates'),
  previewExternalImport: (file: File, templateKey?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (templateKey) {
      formData.append('templateKey', templateKey);
    }
    return post('/api/data/import/preview', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  confirmExternalImport: (previewId: string, fileName?: string) => post('/api/data/import/confirm', { previewId, fileName }),
  updateFile: (data: unknown) => post('/api/data/update-file', data),
  importSnapshot: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return post('/api/data/snapshot/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  }
};

export const anomalyApi = {
  getRealtimeAnomalies: (params: unknown) => get('/api/anomaly/realtime', params),
  getScheduleStatus: () => get('/api/anomaly/schedule/status'),
  getStatistics: (params?: unknown) => get('/api/anomaly/stats', params),
  detect: (data: unknown) => post('/api/anomaly/detect', data),
  stopSchedule: () => post('/api/anomaly/schedule/stop'),
  startSchedule: (data: unknown) => post('/api/anomaly/schedule/start', data),
  getHistoryAnomalies: (params: unknown) => get('/api/anomaly/history', params),
  handleAnomaly: (id: number, params: unknown) => put(`/api/anomaly/${id}/handle`, params),
  getChartData: () => get('/api/anomaly/charts')
};

export const anomalyConfigApi = {
  getThresholds: (params: unknown) => get('/api/anomaly/config/thresholds', params),
  saveThreshold: (data: unknown) => post('/api/anomaly/config/thresholds', data),
  deleteThreshold: (id: number) => del(`/api/anomaly/config/thresholds/${id}`),
  getEffectiveThresholds: (params: unknown) => get('/api/anomaly/config/thresholds/effective', params)
};

export const warningApi = {
  getList: (params: unknown) => get('/api/warning/list', params),
  getStats: (params?: unknown) => get('/api/warning/stats', params),
  handle: (data: unknown) => put('/api/warning/handle', data),
  handleBatch: (data: unknown) => put('/api/warning/handle/batch', data)
};

export const collectionApi = {
  getStatus: () => get('/api/collection/status'),
  getDevices: () => get('/api/collection/devices'),
  getHistory: (params?: unknown) => get('/api/collection/history', params),
  startCollection: (params: unknown) => post('/api/collection/start', params),
  getScenarioTemplates: () => get('/api/collection/scenario-templates'),
  stopCollection: () => post('/api/collection/stop'),
  saveSettings: (settings: unknown) => post('/api/collection/settings', settings),
  getSettings: () => get('/api/collection/settings'),
  deleteHistory: (id: number) => del(`/api/collection/history/${id}`),
  downloadHistory: (id: number) => get(`/api/collection/history/${id}/download`, undefined, { responseType: 'blob' })
};

export const visualizationApi = {
  getReportHistory: () => get('/api/report/history/list'),
  deleteReport: (id: number) => del(`/api/report/history/${id}`),
  cleanReportHistory: () => del('/api/report/history/clean'),
  downloadReport: (id: number) => get(`/api/report/history/${id}/download`),
  generateReport: (data: unknown) => post('/api/visualization/report/generate', data)
};

export const comparisonApi = {
  getOperationLogs: (params?: unknown) => get('/api/operation/logs', params),
  adoptScheme: (historyId: number, schemeIndex: number) => post(`/api/comparison/adopt/${historyId}/${schemeIndex}`),
  compareEvolution: (params: unknown) => get('/api/comparison/evolution/compare', params),
  compareProduction: (params: unknown) => get('/api/comparison/production/compare', params),
  compareEvolutionBaseline: (params: unknown) => get('/api/comparison/evolution/baseline/compare', params),
  getEvolutionHistoryDetail: (id: number) => get(`/api/comparison/evolution/history/${id}`),
  getEvolutionHistory: (params?: unknown) => get('/api/comparison/evolution/history', params),
  getCompareHistory: (params?: unknown) => get('/api/comparison/compare/history', params),
  getCompareHistoryDetail: (id: number) => get(`/api/comparison/compare/history/${id}`),
  deleteHistory: (id: number) => del(`/api/comparison/history/${id}`),
  batchDeleteHistory: (ids: number[]) => del('/api/comparison/history/batch', ids)
};

export const optimizationApi = {
  validateModel: (params: unknown) => post('/api/optimization/validate', params),
  getEvaluationHistory: () => get('/api/optimization/evaluation/history'),
  getEvaluationDetail: (id: number) => get(`/api/optimization/evaluation/${id}`),
  deleteEvaluation: (id: number) => del(`/api/optimization/evaluation/${id}`),
  deleteEvaluationBatch: (ids: number[]) => post('/api/optimization/evaluation/batch', ids),
  getOptimizationProgress: (taskId: string) => get(`/api/optimization/progress/${taskId}`),
  getOptimizationResult: (taskId: string) => get(`/api/optimization/result/${taskId}`),
  startEvolutionaryOptimization: (params: unknown) => post('/api/optimization/evolution/start', params, { timeout: 60000 }),
  modelTraining: {
    getTrainingHistory: () => get('/api/optimization/model/training-history'),
    startTraining: (params: { training: Record<string, unknown>; config: Record<string, unknown> }) =>
      post('/api/optimization/model/train', params),
    getTrainingStatus: (trainingId: number) => get(`/api/optimization/model/training/${trainingId}`),
    getTrainingLogs: (trainingId: number) => get(`/api/optimization/model/training/${trainingId}/logs`),
    exportModel: (trainingId: number) => get(`/api/optimization/model/export/${trainingId}`),
    deleteTrainingBatch: (trainingIds: number[]) => post('/api/optimization/model/training/batch', trainingIds),
    deleteTraining: (trainingId: number) => del(`/api/optimization/model/training/${trainingId}`)
  },
  deployment: {
    updateServiceName: (id: number, name: string) => post(`/api/optimization/model/deployment/service/${id}/name`, { name }),
    getServiceLogs: (id: number) => get(`/api/optimization/model/deployment/service/${id}/logs`),
    getServiceHealth: (id: number) => get(`/api/optimization/model/deployment/service/${id}/health`),
    getHistory: () => get('/api/optimization/model/deployment/history'),
    getServices: () => get('/api/optimization/model/deployment/services'),
    getRunningServices: () => get('/api/optimization/model/deployment/services'),
    cancel: (deploymentId: number) => post(`/api/optimization/model/deployment/${deploymentId}/cancel`),
    retry: (deploymentId: number) => post(`/api/optimization/model/deployment/${deploymentId}/retry`),
    deploy: (data: unknown) => post('/api/optimization/model/deployment/deploy', data),
    deleteHistoryBatch: (ids: number[]) => post('/api/optimization/model/deployment/batch', ids),
    deleteHistory: (deploymentId: number) => del(`/api/optimization/model/deployment/${deploymentId}`),
    startService: (id: number) => post(`/api/optimization/model/deployment/service/${id}/start`),
    restartService: (id: number) => post(`/api/optimization/model/deployment/service/${id}/restart`),
    stopService: (id: number) => post(`/api/optimization/model/deployment/service/${id}/stop`),
    predict: (serviceId: number, data: unknown) => post(`/api/optimization/model/deployment/predict/${serviceId}`, data),
    explain: (serviceId: number, data: unknown) => post(`/api/optimization/model/deployment/predict/${serviceId}/explain`, data)
  }
};

export const systemApi = {
  user: {
    getList: (params: unknown) => get('/api/system/users', params),
    add: (data: unknown) => post('/api/system/users', data),
    update: (id: number, data: unknown) => put(`/api/system/users/${id}`, data),
    delete: (id: number) => del(`/api/system/users/${id}`),
    updateStatus: (id: number, data: unknown) =>
      put(`/api/system/users/${id}/status`, data, { headers: { 'x-silent-error': '1' } }),
    batchUpdateStatus: (data: unknown) => put('/api/system/users/status/batch', data),
    resetPassword: (id: number, data?: unknown) => post(`/api/system/users/${id}/reset-password`, data),
    batchResetPassword: (data: unknown) => post('/api/system/users/reset-password/batch', data)
  },
  role: {
    getList: () => get('/api/system/roles'),
    create: (data: unknown) => post('/api/system/roles', data),
    update: (id: number, data: unknown) => put(`/api/system/roles/${id}`, data),
    delete: (id: number) => del(`/api/system/roles/${id}`),
    getPermissions: (id: number) => get(`/api/system/roles/${id}/permissions`),
    setPermissions: (id: number, permissionIds: number[]) => post(`/api/system/roles/${id}/permissions`, { permissionIds })
  },
  permission: {
    getList: () => get('/api/system/permissions')
  },
  config: {
    getListByGroup: (group: string) => get('/api/system/configs', { group }),
    batchUpdate: (group: string, items: unknown[]) => put('/api/system/configs', { group, items })
  },
  log: {
    getList: (params: unknown) => get('/api/system/logs', params),
    getOperationList: (params: unknown) => get('/api/operation/logs', params)
  }
};

export const homeApi = {
  getQuickStartProgress: (params?: unknown) => get('/api/home/quick-start/progress', params)
};
