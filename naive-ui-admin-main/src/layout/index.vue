<template>
  <n-layout class="layout-shell" :position="fixedMenu" has-sider>
    <n-layout-sider
      v-if="
        !isMobile && isMixMenuNoneSub && (navMode === 'vertical' || navMode === 'horizontal-mix')
      "
      @collapse="collapsed = true"
      :position="fixedMenu"
      @expand="collapsed = false"
      :collapsed="collapsed"
      collapse-mode="width"
      :collapsed-width="64"
      :width="leftMenuWidth"
      :native-scrollbar="false"
      :inverted="inverted"
      class="layout-shell-sider"
    >
      <Logo :collapsed="collapsed" @click="collapsed = !collapsed" />
      <AsideMenu v-model:collapsed="collapsed" v-model:location="getMenuLocation" />
    </n-layout-sider>

    <n-drawer
      v-model:show="showSideDrawer"
      :width="menuWidth"
      :placement="'left'"
      class="layout-shell-drawer"
    >
      <n-layout-sider
        :position="fixedMenu"
        :collapsed="false"
        :width="menuWidth"
        :native-scrollbar="false"
        :inverted="inverted"
        class="layout-shell-sider"
      >
        <Logo :collapsed="collapsed" />
        <AsideMenu v-model:location="getMenuLocation" />
      </n-layout-sider>
    </n-drawer>

    <n-layout class="layout-shell-main" :inverted="inverted">
      <n-layout-header :inverted="getHeaderInverted" :position="fixedHeader">
        <PageHeader v-model:collapsed="collapsed" :inverted="inverted" />
      </n-layout-header>

      <n-layout-content
        class="layout-shell-content transition-colors duration-300"
        style="background-color: var(--n-body-color);"
      >
        <div
          class="layout-shell-content-main"
          :class="{
            'layout-shell-content-main-fix': fixedMulti,
            'fluid-header': fixedHeader === 'static',
          }"
        >
          <TabsView v-if="isMultiTabs" v-model:collapsed="collapsed" />
          <div
            class="layout-shell-view"
            :class="{
              'layout-shell-view-fix': fixedMulti,
              noMultiTabs: !isMultiTabs,
            }"
          >
            <MainView />
          </div>
        </div>
        <n-layout-footer v-if="getShowFooter">
          <PageFooter />
        </n-layout-footer>
      </n-layout-content>
      <n-back-top :right="100" />
    </n-layout>
  </n-layout>
</template>

<script lang="ts" setup>
  import { ref, unref, computed, onMounted, onUnmounted, h } from 'vue';
  import { Logo } from './components/Logo';
  import { TabsView } from './components/TagsView';
  import { MainView } from './components/Main';
  import { AsideMenu } from './components/Menu';
  import { PageHeader } from './components/Header';
  import { PageFooter } from './components/Footer';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
  import { useRoute, useRouter } from 'vue-router';
  import { useProjectSettingStore } from '@/store/modules/projectSetting';
  import { useNotification } from 'naive-ui';

  const {
    showFooter,
    navMode,
    navTheme,
    headerSetting,
    menuSetting,
    multiTabsSetting,
  } = useProjectSetting();

  const settingStore = useProjectSettingStore();
  const router = useRouter();
  const notification = useNotification();

  const collapsed = ref<boolean>(false);

  const { mobileWidth, menuWidth } = unref(menuSetting);

  const isMobile = computed<boolean>({
    get: () => settingStore.getIsMobile,
    set: (val) => settingStore.setIsMobile(val),
  });

  const fixedHeader = computed(() => {
    const { fixed } = unref(headerSetting);
    return fixed ? 'absolute' : 'static';
  });

  const currentRoute = useRoute();
  const isMixMenuNoneSub = computed(() => {
    const mixMenu = unref(menuSetting).mixMenu;
    if (unref(navMode) != 'horizontal-mix') return true;
    if (unref(navMode) === 'horizontal-mix' && mixMenu && currentRoute.meta.isRoot) {
      // 如果是混合菜单模式，且当前是Root（一级），检查是否有子菜单，如果有则显示Sidebar
      // TODO: 暂时无法准确判断是否有子菜单，先默认显示
      // return false; 
    }
    return true;
  });

  const fixedMenu = computed(() => {
    const { fixed } = unref(menuSetting);
    return fixed ? 'absolute' : 'static';
  });

  const isMultiTabs = computed(() => {
    return unref(multiTabsSetting).show;
  });

  const fixedMulti = computed(() => {
    return unref(multiTabsSetting).fixed;
  });

  const getShowFooter = computed(() => {
    return unref(showFooter);
  });

  const inverted = computed(() => {
    return ['dark', 'header-dark'].includes(unref(navTheme));
  });

  const getHeaderInverted = computed(() => {
    return ['light', 'header-dark'].includes(unref(navTheme)) ? unref(inverted) : !unref(inverted);
  });

  const leftMenuWidth = computed(() => {
    const { minMenuWidth, menuWidth } = unref(menuSetting);
    return collapsed.value ? minMenuWidth : menuWidth;
  });

  const getMenuLocation = computed(() => {
    return 'left';
  });

  // 控制显示或隐藏移动端侧边栏
  const showSideDrawer = computed({
    get: () => isMobile.value && collapsed.value,
    set: (val) => (collapsed.value = val),
  });

  //判断是否触发移动端模式
  const checkMobileMode = () => {
    if (document.body.clientWidth <= mobileWidth) {
      isMobile.value = true;
    } else {
      isMobile.value = false;
    }
    collapsed.value = false;
  };

  const watchWidth = () => {
    const Width = document.body.clientWidth;
    if (Width <= 950) {
      collapsed.value = true;
    } else collapsed.value = false;

    checkMobileMode();
  };

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
    hotMetalTemperature: '铁水温度',
  };

  const buildWsUrl = () => {
    const origin = window.location.origin;
    const isHttps = origin.startsWith('https://');
    const wsOrigin = origin.replace(/^http/, isHttps ? 'wss' : 'ws');
    return `${wsOrigin}/api/ws/warnings`;
  };

  const scheduleWarningReconnect = () => {
    if (warningReconnectTimer) window.clearTimeout(warningReconnectTimer);
    warningReconnectTimer = window.setTimeout(() => {
      if (warningWsClosed) return;
      connectWarningWs();
    }, warningReconnectDelay);
    warningReconnectDelay = Math.min(30000, Math.floor(warningReconnectDelay * 1.6));
  };

  const connectWarningWs = () => {
    if (warningWsClosed) return;
    try {
      warningWs?.close();
    } catch (_) {
    }
    try {
      warningWs = new WebSocket(buildWsUrl());
    } catch (_) {
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
    warningWs.onmessage = (evt) => {
      try {
        const payload = JSON.parse(String(evt.data || ''));
        if (payload?.type === 'WARNING_STATUS_CHANGED') {
          const data = payload?.data || {};
          const status = Number(data.status);
          const statusText = status === 3 ? '已关闭' : status === 2 ? '已解决' : status === 1 ? '处理中' : '待处理';
          notification.create({
            title: `预警状态更新 · #${data?.id ?? '-'}`,
            content: `状态 ${statusText}，处理人 ${data?.handlerUser ?? '-'} `,
            type: status >= 2 ? 'success' : 'info',
            duration: 4000,
          });
          return;
        }
        if (payload?.type !== 'NEW_WARNING') return;
        const data = payload?.data || {};
          window.dispatchEvent(
            new CustomEvent('warning:new', {
              detail: data,
            })
          );
        const level = String(data.level || '');
        const furnaceId = String(data.furnaceId || '');
        const param = String(data.parameterName || '');
        const label = paramLabelMap[param] || param || '未知参数';
        const title = `${level || '新预警'} · ${furnaceId || '未知高炉'}`;
        const content = `${label}  值 ${data.actualValue ?? '-'}  范围 ${data.expectedRange ?? '-'}`;
        const type = level.includes('严重') ? 'error' : level.includes('警告') ? 'warning' : 'info';
        notification.create({
          title,
          content,
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
                      _hl: String(Date.now()),
                    },
                  }),
              },
              '查看'
            ),
        });
      } catch (_) {
      }
    };
  };

  onMounted(() => {
    checkMobileMode();
    window.addEventListener('resize', watchWidth);
    connectWarningWs();
  });

  onUnmounted(() => {
    warningWsClosed = true;
    if (warningReconnectTimer) window.clearTimeout(warningReconnectTimer);
    try {
      warningWs?.close();
    } catch (_) {
    }
    window.removeEventListener('resize', watchWidth);
  });
</script>

<style lang="less">
  .layout-shell-drawer {
    background-color: var(--n-color);

    .layout-shell-sider {
      min-height: 100vh;
      box-shadow: 2px 0 8px 0 rgb(29 35 41 / 5%);
      position: relative;
      z-index: 13;
      transition: all 0.2s ease-in-out;
      overflow: visible !important;
    }
  }
</style>
<style lang="less" scoped>
  .layout-shell {
    display: flex;
    flex-direction: row;
    flex: auto;
    background-color: var(--n-body-color);

    .layout-shell-sider {
      min-height: 100vh;
      box-shadow: 2px 0 8px 0 rgb(29 35 41 / 5%);
      position: relative;
      z-index: 13;
      transition: all 0.2s ease-in-out;
      overflow: visible !important;
    }

    .layout-shell-sider :deep(.n-layout-sider__content) {
      height: 100vh;
      display: flex;
      flex-direction: column;
      min-height: 0;
      background-color: var(--n-color);
      border-right: 1px solid var(--n-border-color);
    }

    .layout-shell-main {
      overflow: hidden;
    }

    .layout-shell-content {
      flex: auto;
      min-height: 100vh;
      background-color: var(--n-body-color);
    }

    .n-layout-header.n-layout-header--absolute-positioned {
      z-index: 11;
    }

    .n-layout-footer {
      background: none;
    }
  }

  .layout-shell-content-main {
    margin: 0;
    position: relative;
    --app-header-height: 64px;
    --app-tabs-height: 46px;
    padding-top: var(--app-header-height);
    display: flex;
    flex-direction: column;
    min-height: calc(100vh - var(--app-header-height));
  }

  .n-layout-footer {
    background: none;
    margin-top: auto !important;
    padding: 0 12px 12px;
  }

  .layout-shell-content-main-fix {
    padding-top: var(--app-header-height);
  }

  .fluid-header {
    padding-top: 0;
  }

  .layout-shell-view {
    margin: 10px 12px 0;
    border-radius: 12px;
    min-height: 0;
    background-color: transparent;
  }

  .layout-shell-view-fix {
    margin-top: 10px;
  }

  .noMultiTabs {
    padding-top: 0;
  }
</style>
