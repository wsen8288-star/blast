import type { RouteRecordRaw } from 'vue-router';
import { PageEnum } from '@/enums/pageEnum';

const BLAST_FURNACE_ROUTE_PATH = '/blast-furnace';

const routes: RouteRecordRaw = {
  path: BLAST_FURNACE_ROUTE_PATH,
  name: 'BlastFurnace',
  component: () => import('@/layout/index.vue'),
  meta: {
    title: '高炉生产参数优化系统',
    icon: 'ic:outline-industry',
    orderNo: 10,
  },
  children: [
    {
      path: 'data-management',
      name: 'DataManagement',
      component: () => import('@/views/blast-furnace/data-management/data-management-main.vue'),
      meta: {
        title: '数据资源管理',
        icon: 'ic:outline-database',
      },
      children: [
        {
          path: 'data-collection',
          name: 'DataCollection',
          component: () =>
            import('@/views/blast-furnace/data-management/data-collection/index.vue'),
          meta: {
            title: '数据采集',
            icon: 'ic:outline-collect',
          },
        },
        {
          path: 'data-preprocessing',
          name: 'DataPreprocessing',
          component: () =>
            import('@/views/blast-furnace/data-management/data-preprocessing/index.vue'),
          meta: {
            title: '数据预处理',
            icon: 'ic:outline-auto-fix-high',
          },
        },
        {
          path: 'data-storage',
          name: 'DataStorage',
          component: () => import('@/views/blast-furnace/data-management/data-storage/index.vue'),
          meta: {
            title: '数据存储',
            icon: 'ic:outline-sd-storage',
          },
        },
      ],
    },
    {
      path: 'monitoring',
      name: 'Monitoring',
      component: () => import('@/views/blast-furnace/monitoring/monitoring-main.vue'),
      meta: {
        title: '实时监控与预警',
        icon: 'ic:outline-equalizer',
      },
      children: [
        {
          path: 'real-time-monitoring',
          name: 'RealTimeMonitoring',
          component: () =>
            import('@/views/blast-furnace/monitoring/real-time-monitoring/index.vue'),
          meta: {
            title: '实时监控',
            icon: 'ic:outline-monitor',
          },
        },
        {
          path: 'anomaly-detection',
          name: 'AnomalyDetection',
          component: () => import('@/views/blast-furnace/monitoring/anomaly-detection/index.vue'),
          meta: {
            title: '异常检测',
            icon: 'ic:outline-warning',
          },
        },
        {
          path: 'early-warning',
          name: 'EarlyWarning',
          component: () => import('@/views/blast-furnace/monitoring/early-warning/index.vue'),
          meta: {
            title: '预警管理',
            icon: 'ic:outline-notifications',
          },
        },
        {
          path: 'quality-dashboard',
          name: 'QualityDashboard',
          component: () => import('@/views/blast-furnace/monitoring/quality-dashboard/index.vue'),
          meta: {
            title: '数据质量看板',
            icon: 'ic:outline-query-stats',
          },
        },
      ],
    },
    {
      path: 'analysis',
      name: 'Analysis',
      component: () => import('@/views/blast-furnace/analysis/analysis-main.vue'),
      meta: {
        title: '多维数据分析',
        icon: 'ic:outline-insert-chart',
      },
      children: [
        {
          path: 'historical-analysis',
          name: 'HistoricalAnalysis',
          component: () => import('@/views/blast-furnace/analysis/historical-analysis/index.vue'),
          meta: {
            title: '历史数据分析',
            icon: 'ic:outline-history',
          },
        },
        {
          path: 'correlation-analysis',
          name: 'CorrelationAnalysis',
          component: () => import('@/views/blast-furnace/analysis/correlation-analysis/index.vue'),
          meta: {
            title: '相关性分析',
            icon: 'ic:outline-link',
          },
        },
      ],
    },
    {
      path: 'optimization',
      name: 'Optimization',
      component: () => import('@/views/blast-furnace/optimization/optimization-main.vue'),
      meta: {
        title: '智能模型与优化',
        icon: 'ic:outline-ai',
      },
      children: [
        {
          path: 'model-training',
          name: 'ModelTraining',
          component: () => import('@/views/blast-furnace/optimization/model-training/index.vue'),
          meta: {
            title: '模型训练',
            icon: 'ic:outline-brain',
          },
        },
        {
          path: 'model-evaluation',
          name: 'ModelEvaluation',
          component: () => import('@/views/blast-furnace/optimization/model-evaluation/index.vue'),
          meta: {
            title: '模型评估',
            icon: 'ic:outline-assessment',
          },
        },
        {
          path: 'model-deployment',
          name: 'ModelDeployment',
          component: () => import('@/views/blast-furnace/optimization/model-deployment/index.vue'),
          meta: {
            title: '模型部署',
            icon: 'ic:outline-cloud-upload',
          },
        },
        {
          path: 'scheme-comparison',
          name: 'SchemeComparison',
          component: () => import('@/views/blast-furnace/optimization/scheme-comparison/index.vue'),
          meta: {
            title: '方案对比',
            icon: 'ic:outline-compare-arrows',
          },
        },
      ],
    },
    {
      path: 'system',
      name: 'BlastFurnaceSystem',
      component: () => import('@/views/blast-furnace/system/system-main.vue'),
      meta: {
        title: '系统与运维管理',
        icon: 'ic:outline-settings',
        permissions: ['menu:system'],
      },
      children: [
        {
          path: 'user-management',
          name: 'UserManagement',
          component: () => import('@/views/blast-furnace/system/user-management/index.vue'),
          meta: {
            title: '用户管理',
            icon: 'ic:outline-people',
          },
        },
        {
          path: 'role-management',
          name: 'RoleManagement',
          component: () => import('@/views/blast-furnace/system/role-management/index.vue'),
          meta: {
            title: '角色管理',
            icon: 'ic:outline-supervised-user-circle',
            permissions: ['menu:system:role'],
          },
        },
        {
          path: 'system-settings',
          name: 'SystemSettings',
          component: () => import('@/views/blast-furnace/system/system-settings/index.vue'),
          meta: {
            title: '系统设置',
            icon: 'ic:outline-tune',
            permissions: ['menu:system:config'],
          },
        },
        {
          path: 'system-logs',
          name: 'SystemLogs',
          component: () => import('@/views/blast-furnace/system/system-logs/index.vue'),
          meta: {
            title: '系统日志',
            icon: 'ic:outline-receipt-long',
          },
        },
      ],
    },
  ],
};

export default routes;
