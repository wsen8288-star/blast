<script setup lang="ts">
import { computed, defineAsyncComponent, h, onMounted, onUnmounted } from 'vue';
import { AdminLayout, LAYOUT_SCROLL_EL_ID } from '@sa/materials';
import type { LayoutMode } from '@sa/materials';
import { useRouter } from 'vue-router';
import { useAppStore } from '@/store/modules/app';
import { useSystemConfigStore } from '@/store/modules/system-config';
import { useThemeStore } from '@/store/modules/theme';
import { createServiceConfig } from '@/utils/service';
import GlobalHeader from '../modules/global-header/index.vue';
import GlobalSider from '../modules/global-sider/index.vue';
import GlobalTab from '../modules/global-tab/index.vue';
import GlobalContent from '../modules/global-content/index.vue';
import GlobalFooter from '../modules/global-footer/index.vue';
import ThemeDrawer from '../modules/theme-drawer/index.vue';
import { provideMixMenuContext } from '../modules/global-menu/context';

defineOptions({
  name: 'BaseLayout'
});

const appStore = useAppStore();
const systemConfigStore = useSystemConfigStore();
const themeStore = useThemeStore();
const router = useRouter();
const { secondLevelMenus, childLevelMenus, isActiveFirstLevelMenuHasChildren } = provideMixMenuContext();

const GlobalMenu = defineAsyncComponent(() => import('../modules/global-menu/index.vue'));

const layoutMode = computed(() => {
  const vertical: LayoutMode = 'vertical';
  const horizontal: LayoutMode = 'horizontal';
  return themeStore.layout.mode.includes(vertical) ? vertical : horizontal;
});

const headerProps = computed(() => {
  const { mode } = themeStore.layout;

  const headerPropsConfig: Record<UnionKey.ThemeLayoutMode, App.Global.HeaderProps> = {
    vertical: {
      showLogo: false,
      showMenu: false,
      showMenuToggler: true
    },
    'vertical-mix': {
      showLogo: false,
      showMenu: false,
      showMenuToggler: false
    },
    'vertical-hybrid-header-first': {
      showLogo: !isActiveFirstLevelMenuHasChildren.value,
      showMenu: true,
      showMenuToggler: false
    },
    horizontal: {
      showLogo: true,
      showMenu: true,
      showMenuToggler: false
    },
    'top-hybrid-sidebar-first': {
      showLogo: true,
      showMenu: true,
      showMenuToggler: false
    },
    'top-hybrid-header-first': {
      showLogo: true,
      showMenu: true,
      showMenuToggler: isActiveFirstLevelMenuHasChildren.value
    }
  };

  return headerPropsConfig[mode];
});

const siderVisible = computed(() => themeStore.layout.mode !== 'horizontal');

const isVerticalMix = computed(() => themeStore.layout.mode === 'vertical-mix');

const isVerticalHybridHeaderFirst = computed(() => themeStore.layout.mode === 'vertical-hybrid-header-first');

const isTopHybridSidebarFirst = computed(() => themeStore.layout.mode === 'top-hybrid-sidebar-first');

const isTopHybridHeaderFirst = computed(() => themeStore.layout.mode === 'top-hybrid-header-first');

const siderWidth = computed(() => getSiderAndCollapsedWidth(false));

const siderCollapsedWidth = computed(() => getSiderAndCollapsedWidth(true));

function getSiderAndCollapsedWidth(isCollapsed: boolean) {
  const {
    mixChildMenuWidth,
    collapsedWidth,
    width: themeWidth,
    mixCollapsedWidth,
    mixWidth: themeMixWidth
  } = themeStore.sider;

  const width = isCollapsed ? collapsedWidth : themeWidth;
  const mixWidth = isCollapsed ? mixCollapsedWidth : themeMixWidth;

  if (isTopHybridHeaderFirst.value) {
    return isActiveFirstLevelMenuHasChildren.value ? width : 0;
  }

  if (isVerticalHybridHeaderFirst.value && !isActiveFirstLevelMenuHasChildren.value) {
    return 0;
  }

  const isMixMode = isVerticalMix.value || isTopHybridSidebarFirst.value || isVerticalHybridHeaderFirst.value;
  let finalWidth = isMixMode ? mixWidth : width;

  if (isVerticalMix.value && appStore.mixSiderFixed && secondLevelMenus.value.length) {
    finalWidth += mixChildMenuWidth;
  }

  if (isVerticalHybridHeaderFirst.value && appStore.mixSiderFixed && childLevelMenus.value.length) {
    finalWidth += mixChildMenuWidth;
  }

  return finalWidth;
}

let warningWs: WebSocket | null = null;
let warningReconnectTimer: number | null = null;
let warningReconnectDelay = 1000;
let warningWsClosed = false;

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
  hotMetalTemperature: '铁水温度'
};

function buildWsUrl() {
  const { baseURL } = createServiceConfig(import.meta.env);
  if (baseURL) {
    const wsBase = baseURL.replace(/^http/, baseURL.startsWith('https://') ? 'wss' : 'ws').replace(/\/$/, '');
    return `${wsBase}/api/ws/warnings`;
  }
  const origin = window.location.origin;
  const wsOrigin = origin.replace(/^http/, origin.startsWith('https://') ? 'wss' : 'ws');
  return `${wsOrigin}/api/ws/warnings`;
}

function scheduleWarningReconnect() {
  if (warningReconnectTimer) window.clearTimeout(warningReconnectTimer);
  warningReconnectTimer = window.setTimeout(() => {
    if (warningWsClosed) return;
    connectWarningWs();
  }, warningReconnectDelay);
  warningReconnectDelay = Math.min(30000, Math.floor(warningReconnectDelay * 1.6));
}

function connectWarningWs() {
  if (warningWsClosed) return;
  try {
    warningWs?.close();
  } catch {}
  try {
    warningWs = new WebSocket(buildWsUrl());
  } catch {
    scheduleWarningReconnect();
    return;
  }
  warningWs.onopen = () => {
    warningReconnectDelay = 1000;
  };
  warningWs.onclose = () => {
    if (warningWsClosed) return;
    scheduleWarningReconnect();
  };
  warningWs.onerror = () => {
    if (warningWsClosed) return;
    scheduleWarningReconnect();
  };
  warningWs.onmessage = event => {
    try {
      const payload = JSON.parse(String(event.data || ''));
      if (payload?.type === 'WARNING_STATUS_CHANGED') {
        const data = payload?.data || {};
        const status = Number(data.status);
        const statusText = status === 3 ? '已关闭' : status === 2 ? '已解决' : status === 1 ? '处理中' : '待处理';
        window.$notification?.create({
          title: `预警状态更新 · #${data?.id ?? '-'}`,
          content: `状态 ${statusText}，处理人 ${data?.handlerUser ?? '-'}`,
          type: status >= 2 ? 'success' : 'info',
          duration: 4000
        });
        return;
      }
      if (payload?.type !== 'NEW_WARNING') return;
      const data = payload?.data || {};
      window.dispatchEvent(
        new CustomEvent('warning:new', {
          detail: data
        })
      );
      const level = String(data.level || '');
      const furnaceId = String(data.furnaceId || '');
      const param = String(data.parameterName || '');
      const label = paramLabelMap[param] || param || '未知参数';
      const type = level.includes('严重') ? 'error' : level.includes('警告') ? 'warning' : 'info';
      window.$notification?.create({
        title: `${level || '新预警'} · ${furnaceId || '未知高炉'}`,
        content: `${label}  值 ${data.actualValue ?? '-'}  范围 ${data.expectedRange ?? '-'}`,
        type,
        duration: 6000,
        action: () =>
          h(
            'span',
            {
              style: 'cursor:pointer;color:#18a058;',
              onClick: () =>
                router.push({
                  path: '/blast-furnace/monitoring/early-warning',
                  query: {
                    highlightId: data?.id == null ? undefined : String(data.id),
                    _hl: String(Date.now())
                  }
                })
            },
            '查看'
          )
      });
    } catch {}
  };
}

onMounted(() => {
  systemConfigStore.loadSystemConfig();
  connectWarningWs();
});

onUnmounted(() => {
  warningWsClosed = true;
  if (warningReconnectTimer) window.clearTimeout(warningReconnectTimer);
  try {
    warningWs?.close();
  } catch {}
});
</script>

<template>
  <AdminLayout
    v-model:sider-collapse="appStore.siderCollapse"
    :mode="layoutMode"
    :scroll-el-id="LAYOUT_SCROLL_EL_ID"
    :scroll-mode="themeStore.layout.scrollMode"
    :is-mobile="appStore.isMobile"
    :full-content="appStore.fullContent"
    :fixed-top="themeStore.fixedHeaderAndTab"
    :header-height="themeStore.header.height"
    :tab-visible="themeStore.tab.visible"
    :tab-height="themeStore.tab.height"
    :content-class="appStore.contentXScrollable ? 'overflow-x-hidden' : ''"
    :sider-visible="siderVisible"
    :sider-width="siderWidth"
    :sider-collapsed-width="siderCollapsedWidth"
    :footer-visible="themeStore.footer.visible"
    :footer-height="themeStore.footer.height"
    :fixed-footer="themeStore.footer.fixed"
    :right-footer="themeStore.footer.right"
  >
    <template #header>
      <GlobalHeader v-bind="headerProps" />
    </template>
    <template #tab>
      <GlobalTab />
    </template>
    <template #sider>
      <GlobalSider />
    </template>
    <GlobalMenu />
    <GlobalContent />
    <ThemeDrawer />
    <template #footer>
      <GlobalFooter />
    </template>
  </AdminLayout>
</template>

<style lang="scss">
#__SCROLL_EL_ID__ {
  @include scrollbar();
}
</style>
