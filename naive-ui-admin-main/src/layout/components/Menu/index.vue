<template>
  <div
    class="menu-container"
    :class="{
      'menu-container--header': mode === 'horizontal' || location === 'header',
      'menu-container--sider': mode !== 'horizontal' && location !== 'header',
    }"
  >
    <NMenu
      :options="menus"
      :inverted="inverted"
      :mode="mode"
      :collapsed="collapsed"
      :collapsed-width="64"
      :collapsed-icon-size="20"
      :indent="mode === 'horizontal' ? 0 : 24"
      :expanded-keys="openKeys"
      :value="getSelectedKeys"
      @update:value="clickMenuItem"
      @update:expanded-keys="menuExpanded"
      class="custom-menu"
      :root-indent="mode === 'horizontal' ? 0 : 24"
    />
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, reactive, computed, watch, toRefs, unref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useAsyncRouteStore } from '@/store/modules/asyncRoute';
  import { generatorMenu, generatorMenuMix } from '@/utils';
  import { useProjectSettingStore } from '@/store/modules/projectSetting';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';

  export default defineComponent({
    name: 'AppMenu',
    components: {},
    props: {
      mode: {
        // 菜单模式
        type: String,
        default: 'vertical',
      },
      collapsed: {
        // 侧边栏菜单是否收起
        type: Boolean,
      },
      //位置
      location: {
        type: String,
        default: 'left',
      },
    },
    emits: ['update:collapsed', 'clickMenuItem'],
    setup(props, { emit }) {
      // 当前路由
      const currentRoute = useRoute();
      const router = useRouter();
      const asyncRouteStore = useAsyncRouteStore();
      const settingStore = useProjectSettingStore();
      const menus = ref<any[]>([]);
      const selectedKeys = ref<string>(currentRoute.name as string);
      const headerMenuSelectKey = ref<string>('');

      const { navMode } = useProjectSetting();

      // 获取当前打开的子菜单
      const matched = currentRoute.matched;

      const getOpenKeys = matched && matched.length ? matched.map((item) => item.name) : [];

      const state = reactive({
        openKeys: getOpenKeys,
      });

      const inverted = computed(() => {
       if (props.location === 'header' || props.mode === 'horizontal') {
          return false; 
        }
        
        // 侧边栏保持原有逻辑
        return ['dark', 'header-dark'].includes(settingStore.navTheme);
      });

      const getSelectedKeys = computed(() => {
        let location = props.location;
        return location === 'left' || (location === 'header' && unref(navMode) === 'horizontal')
          ? unref(selectedKeys)
          : unref(headerMenuSelectKey);
      });

      // 监听分割菜单
      watch(
        () => settingStore.menuSetting.mixMenu,
        () => {
          updateMenu();
          if (props.collapsed) {
            emit('update:collapsed', !props.collapsed);
          }
        }
      );

      // 跟随页面路由变化，切换菜单选中状态
      watch(
        () => currentRoute.fullPath,
        () => {
          updateMenu();
        }
      );

      function updateSelectedKeys() {
        const matched = currentRoute.matched;
        state.openKeys = matched.map((item) => item.name);
        const activeMenu: string = (currentRoute.meta?.activeMenu as string) || '';
        selectedKeys.value = activeMenu ? (activeMenu as string) : (currentRoute.name as string);
      }

      function updateMenu() {
        if (!settingStore.menuSetting.mixMenu) {
          menus.value = generatorMenu(asyncRouteStore.getMenus);
        } else {
          //混合菜单
          const firstRouteName: string = (currentRoute.matched[0].name as string) || '';
          menus.value = generatorMenuMix(asyncRouteStore.getMenus, firstRouteName, props.location);
          const activeMenu: string = currentRoute?.matched[0].meta?.activeMenu as string;
          headerMenuSelectKey.value = (activeMenu ? activeMenu : firstRouteName) || '';
        }
        updateSelectedKeys();
      }

      // 点击菜单
      function clickMenuItem(key: string) {
        if (/http(s)?:/.test(key)) {
          window.open(key);
        } else {
          router.push({ name: key });
        }
        emit('clickMenuItem' as any, key);
      }

      //展开菜单
      function menuExpanded(openKeys: string[]) {
        if (!openKeys) return;
        const latestOpenKey = openKeys.find((key) => state.openKeys.indexOf(key) === -1);
        const isExistChildren = findChildrenLen(latestOpenKey as string);
        state.openKeys = isExistChildren ? (latestOpenKey ? [latestOpenKey] : []) : openKeys;
      }

      //查找是否存在子路由
      function findChildrenLen(key: string) {
        if (!key) return false;
        const subRouteChildren: string[] = [];
        for (const { children, key } of unref(menus)) {
          if (children && children.length) {
            subRouteChildren.push(key as string);
          }
        }
        return subRouteChildren.includes(key);
      }

      onMounted(() => {
        updateMenu();
      });

      return {
        ...toRefs(state),
        inverted,
        menus,
        selectedKeys,
        headerMenuSelectKey,
        getSelectedKeys,
        clickMenuItem,
        menuExpanded,
      };
    },
  });
</script>

<style lang="less">

  .menu-container {
    display: flex !important;
    flex-direction: column !important;
    justify-content: flex-start !important;
  }

  .menu-container--sider {
    width: 100% !important;
    flex: 1 1 auto !important;
    height: auto !important;
    min-height: 0 !important;
    padding: 10px 8px !important;
    overflow: auto !important;
  }

  .menu-container--header {
    width: 100% !important;
    height: 64px !important;
    min-height: 64px !important;
    padding: 0 !important;
    display: flex !important;
    flex-direction: row !important;
    align-items: center !important;
    flex: 1 1 auto !important;
    min-width: 0 !important;
    overflow-x: auto !important;
    overflow-y: hidden !important;
    scrollbar-width: none;
  }

  .menu-container--header::-webkit-scrollbar {
    width: 0;
    height: 0;
  }

  .menu-container--header .n-menu--horizontal {
    justify-content: flex-start !important;
  }

  /* 顶层菜单项胶囊形态排版 */
  .menu-container--header .n-menu-item {
    margin: 0 4px !important;
    display: flex;
    align-items: center;
    height: 64px;
  }

  .menu-container--header .n-menu-item-content {
    padding: 0 14px !important;
    height: 34px !important;
    line-height: 34px !important;
    margin: 0 !important;
    border-radius: 18px !important;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    text-align: center !important;
    gap: 0 !important;
  }

  /* 隐藏幽灵图标并修复居中 */
  .menu-container--header .n-menu-item-content__icon {
    display: none !important;
    margin: 0 !important;
    width: 0 !important;
  }

  .menu-container--header .n-menu-item-content-header {
    width: 100%;
    text-align: center;
    margin: 0 !important;
    padding: 0 !important;
    flex: 1 1 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  /* =======================================================
     2. 核心修复：完全接入 Naive UI 的菜单动态颜色变量
     ======================================================= */
  
  /* 默认状态文字颜色：不再使用全局 text-color-1，使用专属的 item-text-color */
  .menu-container--header .n-menu-item-content .n-menu-item-content-header,
  .menu-container--header .n-menu-item-content .n-menu-item-content-header a {
    color: var(--n-item-text-color) !important;
    transition: color 0.3s;
  }

  /* 悬停状态：使用自带的 hover 颜色变量 */
  .menu-container--header .n-menu-item-content:hover {
    background-color: var(--n-item-color-hover) !important;
  }
  .menu-container--header .n-menu-item-content:hover .n-menu-item-content-header,
  .menu-container--header .n-menu-item-content:hover .n-menu-item-content-header a {
    color: var(--n-item-text-color-hover) !important;
  }

  /* 选中状态：使用自带的 active 变量。
     这里是最关键的：var(--n-item-color-active) 在浅色模式下会自动变成“带透明度的主题浅色”，
     所以绝对不会出现蓝字+蓝底导致看不清的情况！ */
  .menu-container--header .n-menu-item-content--selected {
    background-color: var(--n-item-color-active) !important;
  }
  
  .menu-container--header .n-menu-item-content--selected .n-menu-item-content-header,
  .menu-container--header .n-menu-item-content--selected .n-menu-item-content-header a {
    color: var(--n-item-text-color-active) !important;
  }

  /* 去除 Naive UI 默认的底边下划线 */
  .menu-container--header .n-menu-item-content--selected::after {
    display: none !important;
  }

  /* =======================================================
     3. 侧边栏排版（不破坏颜色引擎）
     ======================================================= */
  .menu-container--sider .custom-menu {
    width: 100% !important;
    height: auto !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: flex-start !important;
    min-height: 0 !important;
  }

  .menu-container--sider .custom-menu .n-menu-item-content {
    white-space: nowrap !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;
    text-align: left !important;
    padding-left: 0 !important;
    border-radius: 10px !important;
  }

  .menu-container--sider .custom-menu .n-menu-item {
    padding: 10px 12px !important;
    min-height: 44px !important;
    text-align: left !important;
    margin: 6px 0 !important;
  }

  .menu-container--sider .custom-menu .n-menu-item-group-item {
    padding: 10px 12px 10px 28px !important;
    text-align: left !important;
    margin: 4px 0 !important;
  }

  .menu-container--sider .custom-menu .n-menu-item-arrow {
    margin-left: auto !important;
    margin-right: 10px !important;
  }

  /* 侧边栏的选中和悬停态，同样接入原生自带的计算变量 */
  .menu-container--sider .n-menu-item-content--selected {
    border-left: 4px solid var(--n-item-text-color-active) !important;
    background: var(--n-item-color-active) !important;
  }

  .menu-container--sider .n-menu-item-content:not(.n-menu-item-content--selected):hover {
    background: var(--n-item-color-hover) !important;
  }
  
</style>
