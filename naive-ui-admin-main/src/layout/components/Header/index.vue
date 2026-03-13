<template>
  <div
    class="layout-header backdrop-blur-md shadow-sm transition-all duration-300"
    :class="{
      'layout-header--top': navMode === 'horizontal' || (navMode === 'horizontal-mix' && mixMenu)
    }"
    :style="headerBgStyle"
  >
    <div
      class="layout-header-left"
      v-if="navMode === 'horizontal' || (navMode === 'horizontal-mix' && mixMenu)"
    >
      <div class="logo" v-if="navMode === 'horizontal'">
        <img :src="websiteConfig.logo" alt="" />
        <h2 v-show="!collapsed" class="title">{{ systemTitle }}</h2>
      </div>
      <AsideMenu
        :collapsed="collapsed"
        v-model:location="getMenuLocation"
        :inverted="getInverted"
        mode="horizontal"
      />
    </div>
    <div class="layout-header-left" v-else>
      <div
        class="ml-1 layout-header-trigger layout-header-trigger-min"
        @click="handleMenuCollapsed"
      >
        <n-icon size="18" v-if="collapsed">
          <MenuUnfoldOutlined />
        </n-icon>
        <n-icon size="18" v-else>
          <MenuFoldOutlined />
        </n-icon>
      </div>
      <div
        class="mr-1 layout-header-trigger layout-header-trigger-min"
        v-if="headerSetting.isReload"
        @click="reloadPage"
      >
        <n-icon size="18">
          <ReloadOutlined />
        </n-icon>
      </div>
      <n-breadcrumb v-if="crumbsSetting.show">
        <template
          v-for="routeItem in breadcrumbList"
          :key="routeItem.name === RedirectName ? void 0 : routeItem.name"
        >
          <n-breadcrumb-item v-if="routeItem.meta.title">
            <n-dropdown
              v-if="routeItem.children.length"
              :options="routeItem.children"
              @select="dropdownSelect"
            >
              <span class="link-text">
                <component
                  v-if="crumbsSetting.showIcon && routeItem.meta.icon"
                  :is="routeItem.meta.icon"
                />
                {{ routeItem.meta.title }}
              </span>
            </n-dropdown>
            <span class="link-text" v-else>
              <component
                v-if="crumbsSetting.showIcon && routeItem.meta.icon"
                :is="routeItem.meta.icon"
              />
              {{ routeItem.meta.title }}
            </span>
          </n-breadcrumb-item>
        </template>
      </n-breadcrumb>
    </div>
    <div class="layout-header-right">
      <div
        class="layout-header-trigger layout-header-trigger-min"
        v-for="item in iconList"
        :key="item.icon"
      >
        <n-tooltip placement="bottom">
          <template #trigger>
            <n-icon size="18">
              <component :is="item.icon" v-on="item.eventObject || {}" />
            </n-icon>
          </template>
          <span>{{ item.tips }}</span>
        </n-tooltip>
      </div>
      <div class="layout-header-trigger layout-header-trigger-min">
        <n-tooltip placement="bottom">
          <template #trigger>
            <n-icon size="18">
              <component :is="fullscreenIcon" @click="toggleFullScreen" />
            </n-icon>
          </template>
          <span>全屏</span>
        </n-tooltip>
      </div>
      <div class="layout-header-trigger layout-header-trigger-min">
        <n-dropdown trigger="hover" @select="avatarSelect" :options="avatarOptions">
          <div class="avatar">
            <n-avatar :src="websiteConfig.logo">
              <template #icon>
                <UserOutlined />
              </template>
            </n-avatar>
            <n-divider vertical class="hidden sm:block" style="background-color: var(--n-border-color);" />
            <span class="hidden sm:inline">{{ username }}</span>
          </div>
        </n-dropdown>
      </div>
      <div class="layout-header-trigger layout-header-trigger-min" @click="openSetting">
        <n-tooltip placement="bottom-end">
          <template #trigger>
            <n-icon size="18" style="font-weight: bold">
              <SettingOutlined />
            </n-icon>
          </template>
          <span>项目配置</span>
        </n-tooltip>
      </div>
    </div>
  </div>
  <ProjectSetting ref="drawerSetting" />
</template>

<script lang="ts">
  import { websiteConfig } from '@/config/website.config';
  import { systemApi } from '@/api/blast-furnace';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
  import { AsideMenu } from '@/layout/components/Menu';
  import { RedirectName } from '@/router/constant';
  import { useUserStore } from '@/store/modules/user';
  import { TABS_ROUTES } from '@/store/mutation-types';
  import { NDialogProvider, useDialog, useMessage } from 'naive-ui';
  import { computed, defineComponent, onMounted, reactive, ref, toRefs, unref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useAsyncRouteStore } from '@/store/modules/asyncRoute';
  import components from './components';
  import ProjectSetting from './ProjectSetting.vue';

  export default defineComponent({
    name: 'PageHeader',
    components: { ...components, NDialogProvider, ProjectSetting, AsideMenu },
    props: {
      collapsed: {
        type: Boolean,
      },
      inverted: {
        type: Boolean,
      },
    },
    emits: ['update:collapsed'],
    setup(props, { emit }) {
      const userStore = useUserStore();
      const message = useMessage();
      const dialog = useDialog();
      const asyncRouteStore = useAsyncRouteStore();
      const { navMode, navTheme, headerSetting, menuSetting, crumbsSetting } = useProjectSetting();

      const drawerSetting = ref();

      const state = reactive({
        username: userStore?.info?.username ?? '',
        fullscreenIcon: 'FullscreenOutlined',
        navMode,
        navTheme,
      });
      const systemTitle = ref(localStorage.getItem('system_name_runtime') || websiteConfig.title);

      const headerBgStyle = computed(() => {
        // 废弃原有的深色浅色硬编码，交给 CSS 变量，只保留 zIndex
        return {
          zIndex: 11,
        };
      });

      const getInverted = computed(() => {
        const isHorizontal = unref(navMode) === 'horizontal';
        if (isHorizontal) return false;
        return ['light', 'header-dark'].includes(unref(navTheme))
          ? props.inverted
          : !props.inverted;
      });

      const mixMenu = computed(() => {
        return unref(menuSetting).mixMenu;
      });

      const getMenuLocation = computed(() => {
        return 'header';
      });

      const router = useRouter();
      const route = useRoute();

      const generator: any = (routerMap) => {
        return routerMap.map((item) => {
          const currentMenu = {
            ...item,
            label: item.meta.title,
            key: item.name,
            disabled: item.path === '/',
          };
          if (item.children && item.children.length > 0) {
            currentMenu.children = generator(item.children, currentMenu);
          }
          return currentMenu;
        });
      };

      const breadcrumbList = computed(() => {
        return generator(route.matched);
      });

      const dropdownSelect = (key) => {
        router.push({ name: key });
      };

      const reloadPage = () => {
        if (route.meta.keepAlive) {
          const name = router.currentRoute.value.matched.find((item) => item.name == route.name)?.components
            ?.default?.name;
          if (name) {
            asyncRouteStore.keepAliveComponents = asyncRouteStore.keepAliveComponents.filter(
              (item) => item != name
            );
          }
        }
        router.replace({ path: '/redirect' + unref(route).fullPath });
      };

      const doLogout = () => {
        dialog.info({
          title: '提示',
          content: '您确定要退出登录吗',
          positiveText: '确定',
          negativeText: '取消',
          onPositiveClick: () => {
            userStore.logout().then(() => {
              message.success('成功退出登录');
              localStorage.removeItem(TABS_ROUTES);
              router
                .replace({
                  name: 'Login',
                  query: {
                    redirect: route.fullPath,
                  },
                })
                .finally(() => location.reload());
            });
          },
          onNegativeClick: () => {},
        });
      };

      const toggleFullscreenIcon = () =>
        (state.fullscreenIcon =
          document.fullscreenElement !== null ? 'FullscreenExitOutlined' : 'FullscreenOutlined');

      document.addEventListener('fullscreenchange', toggleFullscreenIcon);

      const toggleFullScreen = () => {
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen();
        } else {
          if (document.exitFullscreen) {
            document.exitFullscreen();
          }
        }
      };

      const iconList: { icon: string; tips: string; eventObject?: any }[] = [];
      const avatarOptions = [
        {
          label: '个人设置',
          key: 1,
        },
        {
          label: '退出登录',
          key: 2,
        },
      ];

      const avatarSelect = (key) => {
        switch (key) {
          case 1:
            router.push({ name: 'Setting' });
            break;
          case 2:
            doLogout();
            break;
        }
      };

      function openSetting() {
        const { openDrawer } = drawerSetting.value;
        openDrawer();
      }

      function handleMenuCollapsed() {
        emit('update:collapsed', !props.collapsed);
      }

      const loadSystemTitle = async () => {
        try {
          const res: any = await systemApi.config.getListByGroup('SYSTEM_CONFIG');
          const items: any[] = Array.isArray(res?.data) ? res.data : [];
          const configMap = new Map(items.map((item: any) => [item?.configKey, item?.configValue]));
          const name = String(configMap.get('system_name') ?? '').trim();
          if (name) {
            systemTitle.value = name;
            localStorage.setItem('system_name_runtime', name);
          }
        } catch (_) {
          return;
        }
      };

      onMounted(() => {
        loadSystemTitle();
      });

      return {
        ...toRefs(state),
        headerSetting,
        crumbsSetting,
        iconList,
        toggleFullScreen,
        doLogout,
        route,
        dropdownSelect,
        avatarOptions,
        avatarSelect,
        breadcrumbList,
        reloadPage,
        drawerSetting,
        openSetting,
        getInverted,
        getMenuLocation,
        mixMenu,
        systemTitle,
        websiteConfig,
        handleMenuCollapsed,
        RedirectName,
        headerBgStyle,
      };
    },
  });
</script>

<style lang="less" scoped>
  /* 引入 Soybean 风格的亚克力效果与自适应颜色 */
  .layout-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0;
    height: 64px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    width: 100%;
    
    /* 核心修改：移除硬编码颜色，接入主题变量 */
    background-color: var(--n-color);
    border-bottom: 1px solid var(--n-border-color);
    color: var(--n-text-color-1);
    box-shadow: var(--box-shadow-1);

    &-left {
      display: flex;
      align-items: center;
      min-width: 0;
      flex: 1 1 auto;
      gap: 0;
      height: 100%;

      .logo {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        overflow: hidden;
        white-space: nowrap;
        padding: 0 14px;
        flex: 0 0 auto;

        img {
          width: auto;
          height: 32px;
          margin-right: 10px;
        }

        .title {
          margin-bottom: 0;
          font-size: 16px;
          font-weight: 600;
          max-width: 240px;
          overflow: hidden;
          text-overflow: ellipsis;
          line-height: normal;
          color: var(--n-text-color-1);
        }
      }

      :deep(.n-breadcrumb .n-breadcrumb-item:last-child .link-text) {
        color: var(--n-text-color-1);
        font-weight: 500;
      }
    }

    &-right {
      display: flex;
      align-items: center;
      margin-right: 12px;
      flex: 0 0 auto;

      .avatar {
        display: flex;
        align-items: center;
        height: 40px;
        padding: 0 10px;
        border-radius: 20px;
        color: var(--n-text-color-1);
        transition: background-color 0.2s ease-in-out;
      }

      .avatar:hover {
        background-color: var(--n-hover-color);
      }

      > * {
        cursor: pointer;
      }
    }

    &-trigger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 20px;
      cursor: pointer;
      transition: all 0.2s ease-in-out;

      .n-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        color: var(--n-text-color-3);
        transition: color 0.3s;
      }

      &:hover {
        background-color: var(--n-hover-color);
        .n-icon {
          color: var(--n-text-color-1);
        }
      }

      .anticon {
        font-size: 16px;
      }
    }

    &-trigger-min {
      width: auto;
      padding: 0 8px;
      height: 40px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
  }


</style>
