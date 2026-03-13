import type { GeneratedRoute } from '@elegant-router/types';

export const generatedRoutes: GeneratedRoute[] = [
  {
    name: '403',
    path: '/403',
    component: 'layout.blank$view.403',
    meta: {
      title: '403',
      i18nKey: 'route.403',
      constant: true,
      hideInMenu: true
    }
  },
  {
    name: 'blast-furnace',
    path: '/blast-furnace',
    component: 'layout.base',
    meta: {
      title: 'blast-furnace',
      i18nKey: 'route.blast-furnace'
    },
    children: [
      {
        name: 'blast-furnace_analysis',
        path: '/blast-furnace/analysis',
        meta: {
          title: 'blast-furnace_analysis',
          i18nKey: 'route.blast-furnace_analysis',
          icon: 'mdi:chart-timeline-variant'
        },
        children: [
          {
            name: 'blast-furnace_analysis_correlation-analysis',
            path: '/blast-furnace/analysis/correlation-analysis',
            component: 'view.blast-furnace_analysis_correlation-analysis',
            meta: {
              title: 'blast-furnace_analysis_correlation-analysis',
              i18nKey: 'route.blast-furnace_analysis_correlation-analysis',
              icon: 'mdi:chart-scatter-plot'
            }
          },
          {
            name: 'blast-furnace_analysis_historical-analysis',
            path: '/blast-furnace/analysis/historical-analysis',
            component: 'view.blast-furnace_analysis_historical-analysis',
            meta: {
              title: 'blast-furnace_analysis_historical-analysis',
              i18nKey: 'route.blast-furnace_analysis_historical-analysis',
              icon: 'mdi:chart-line'
            }
          }
        ]
      },
      {
        name: 'blast-furnace_data-management',
        path: '/blast-furnace/data-management',
        meta: {
          title: 'blast-furnace_data-management',
          i18nKey: 'route.blast-furnace_data-management',
          icon: 'mdi:database-outline'
        },
        children: [
          {
            name: 'blast-furnace_data-management_data-collection',
            path: '/blast-furnace/data-management/data-collection',
            component: 'view.blast-furnace_data-management_data-collection',
            meta: {
              title: 'blast-furnace_data-management_data-collection',
              i18nKey: 'route.blast-furnace_data-management_data-collection',
              icon: 'mdi:database-import-outline'
            }
          },
          {
            name: 'blast-furnace_data-management_data-preprocessing',
            path: '/blast-furnace/data-management/data-preprocessing',
            component: 'view.blast-furnace_data-management_data-preprocessing',
            meta: {
              title: 'blast-furnace_data-management_data-preprocessing',
              i18nKey: 'route.blast-furnace_data-management_data-preprocessing',
              icon: 'mdi:tune-variant'
            }
          },
          {
            name: 'blast-furnace_data-management_data-storage',
            path: '/blast-furnace/data-management/data-storage',
            component: 'view.blast-furnace_data-management_data-storage',
            meta: {
              title: 'blast-furnace_data-management_data-storage',
              i18nKey: 'route.blast-furnace_data-management_data-storage',
              icon: 'mdi:harddisk'
            }
          }
        ]
      },
      {
        name: 'blast-furnace_monitoring',
        path: '/blast-furnace/monitoring',
        meta: {
          title: 'blast-furnace_monitoring',
          i18nKey: 'route.blast-furnace_monitoring',
          icon: 'mdi:radar'
        },
        children: [
          {
            name: 'blast-furnace_monitoring_anomaly-detection',
            path: '/blast-furnace/monitoring/anomaly-detection',
            component: 'view.blast-furnace_monitoring_anomaly-detection',
            meta: {
              title: 'blast-furnace_monitoring_anomaly-detection',
              i18nKey: 'route.blast-furnace_monitoring_anomaly-detection',
              icon: 'mdi:alert-decagram-outline'
            }
          },
          {
            name: 'blast-furnace_monitoring_early-warning',
            path: '/blast-furnace/monitoring/early-warning',
            component: 'view.blast-furnace_monitoring_early-warning',
            meta: {
              title: 'blast-furnace_monitoring_early-warning',
              i18nKey: 'route.blast-furnace_monitoring_early-warning',
              icon: 'mdi:bell-ring-outline'
            }
          },
          {
            name: 'blast-furnace_monitoring_quality-dashboard',
            path: '/blast-furnace/monitoring/quality-dashboard',
            component: 'view.blast-furnace_monitoring_quality-dashboard',
            meta: {
              title: 'blast-furnace_monitoring_quality-dashboard',
              i18nKey: 'route.blast-furnace_monitoring_quality-dashboard',
              icon: 'mdi:view-dashboard-outline'
            }
          },
          {
            name: 'blast-furnace_monitoring_real-time-monitoring',
            path: '/blast-furnace/monitoring/real-time-monitoring',
            component: 'view.blast-furnace_monitoring_real-time-monitoring',
            meta: {
              title: 'blast-furnace_monitoring_real-time-monitoring',
              i18nKey: 'route.blast-furnace_monitoring_real-time-monitoring',
              icon: 'mdi:monitor-dashboard'
            }
          }
        ]
      },
      {
        name: 'blast-furnace_optimization',
        path: '/blast-furnace/optimization',
        meta: {
          title: 'blast-furnace_optimization',
          i18nKey: 'route.blast-furnace_optimization',
          icon: 'mdi:tune-variant'
        },
        children: [
          {
            name: 'blast-furnace_optimization_model-deployment',
            path: '/blast-furnace/optimization/model-deployment',
            component: 'view.blast-furnace_optimization_model-deployment',
            meta: {
              title: 'blast-furnace_optimization_model-deployment',
              i18nKey: 'route.blast-furnace_optimization_model-deployment',
              icon: 'mdi:cloud-upload-outline'
            }
          },
          {
            name: 'blast-furnace_optimization_model-evaluation',
            path: '/blast-furnace/optimization/model-evaluation',
            component: 'view.blast-furnace_optimization_model-evaluation',
            meta: {
              title: 'blast-furnace_optimization_model-evaluation',
              i18nKey: 'route.blast-furnace_optimization_model-evaluation',
              icon: 'mdi:chart-box-outline'
            }
          },
          {
            name: 'blast-furnace_optimization_model-training',
            path: '/blast-furnace/optimization/model-training',
            component: 'view.blast-furnace_optimization_model-training',
            meta: {
              title: 'blast-furnace_optimization_model-training',
              i18nKey: 'route.blast-furnace_optimization_model-training',
              icon: 'mdi:brain'
            }
          },
          {
            name: 'blast-furnace_optimization_scheme-comparison',
            path: '/blast-furnace/optimization/scheme-comparison',
            component: 'view.blast-furnace_optimization_scheme-comparison',
            meta: {
              title: 'blast-furnace_optimization_scheme-comparison',
              i18nKey: 'route.blast-furnace_optimization_scheme-comparison',
              icon: 'mdi:compare-horizontal'
            }
          }
        ]
      },
      {
        name: 'blast-furnace_system',
        path: '/blast-furnace/system',
        meta: {
          title: 'blast-furnace_system',
          i18nKey: 'route.blast-furnace_system',
          icon: 'mdi:cog-outline'
        },
        children: [
          {
            name: 'blast-furnace_system_role-management',
            path: '/blast-furnace/system/role-management',
            component: 'view.blast-furnace_system_role-management',
            meta: {
              title: 'blast-furnace_system_role-management',
              i18nKey: 'route.blast-furnace_system_role-management',
              icon: 'mdi:account-key-outline'
            }
          },
          {
            name: 'blast-furnace_system_system-logs',
            path: '/blast-furnace/system/system-logs',
            component: 'view.blast-furnace_system_system-logs',
            meta: {
              title: 'blast-furnace_system_system-logs',
              i18nKey: 'route.blast-furnace_system_system-logs',
              icon: 'mdi:file-document-outline'
            }
          },
          {
            name: 'blast-furnace_system_system-settings',
            path: '/blast-furnace/system/system-settings',
            component: 'view.blast-furnace_system_system-settings',
            meta: {
              title: 'blast-furnace_system_system-settings',
              i18nKey: 'route.blast-furnace_system_system-settings',
              icon: 'mdi:cog-outline'
            }
          },
          {
            name: 'blast-furnace_system_user-management',
            path: '/blast-furnace/system/user-management',
            component: 'view.blast-furnace_system_user-management',
            meta: {
              title: 'blast-furnace_system_user-management',
              i18nKey: 'route.blast-furnace_system_user-management',
              icon: 'mdi:account-group-outline'
            }
          }
        ]
      }
    ]
  },
  {
    name: 'home',
    path: '/home',
    component: 'layout.base$view.home',
    meta: {
      title: 'home',
      i18nKey: 'route.home',
      icon: 'mdi:monitor-dashboard',
      order: 1
    }
  },
  {
    name: 'login',
    path: '/login/:module(pwd-login|code-login|register|reset-pwd|bind-wechat)?',
    component: 'layout.blank$view.login',
    props: true,
    meta: {
      title: 'login',
      i18nKey: 'route.login',
      constant: true,
      hideInMenu: true
    }
  },
  {
    name: 'redirect',
    path: '/redirect',
    component: 'layout.base$view.redirect',
    meta: {
      title: 'redirect',
      i18nKey: 'route.redirect'
    }
  }
];
