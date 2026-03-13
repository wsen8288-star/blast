<template>
  <div
    class="box-border tabs-view transition-colors duration-300"
    :class="{
      'tabs-view-fix': multiTabsSetting.fixed,
      'tabs-view-fixed-header': isMultiHeaderFixed,
    }"
    :style="[getChangeStyle]"
  >
    <div class="tabs-view-main">
      <div ref="navWrap" class="tabs-card" :class="{ 'tabs-card-scrollable': scrollable }">
        <span
          class="tabs-card-prev"
          :class="{ 'tabs-card-prev-hide': !scrollable }"
          @click="scrollPrev"
        >
          <n-icon size="16">
            <LeftOutlined />
          </n-icon>
        </span>
        <span
          class="tabs-card-next"
          :class="{ 'tabs-card-next-hide': !scrollable }"
          @click="scrollNext"
        >
          <n-icon size="16">
            <RightOutlined />
          </n-icon>
        </span>
        <div ref="navScroll" class="tabs-card-scroll">
          <div class="flex gap-2">
            <div
              v-for="element in tabsList"
              :key="element.fullPath"
              :id="`tag${element.fullPath.split('/').join('\/')}`"
              class="tabs-card-scroll-item group"
              :class="{ 'active-item': activeKey === element.fullPath }"
              :style="getTabItemStyle(element)"
              @click.stop="goPage(element)"
              @contextmenu="handleContextMenu($event, element)"
            >
              <span>{{ element.meta.title }}</span>
              <n-icon 
                size="14" 
                class="close-icon"
                @click.stop="closeTabItem(element)" 
                v-if="!element.meta.affix"
              >
                <CloseOutlined />
              </n-icon>
            </div>
          </div>
        </div>
      </div>
      <div class="tabs-close">
        <n-dropdown
          trigger="hover"
          @select="closeHandleSelect"
          placement="bottom-end"
          :options="TabsMenuOptions"
        >
          <div class="tabs-close-btn">
            <n-icon size="16">
              <DownOutlined />
            </n-icon>
          </div>
        </n-dropdown>
      </div>
      <n-dropdown
        :show="showDropdown"
        :x="dropdownX"
        :y="dropdownY"
        @clickoutside="onClickOutside"
        placement="bottom-start"
        @select="closeHandleSelect"
        :options="TabsMenuOptions"
      />
    </div>
  </div>
</template>

<script lang="ts">
  import {
    defineComponent,
    reactive,
    computed,
    ref,
    toRefs,
    provide,
    watch,
    onMounted,
    nextTick,
  } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { storage } from '@/utils/Storage';
  import { TABS_ROUTES } from '@/store/mutation-types';
  import { useAsyncRouteStore } from '@/store/modules/asyncRoute';
  import { useProjectSetting } from '@/hooks/setting/useProjectSetting';
  import { useMessage } from 'naive-ui';
  import { PageEnum } from '@/enums/pageEnum';
  import {
    DownOutlined,
    ReloadOutlined,
    CloseOutlined,
    ColumnWidthOutlined,
    MinusOutlined,
    LeftOutlined,
    RightOutlined,
  } from '@vicons/antd';
  import { renderIcon } from '@/utils';
  import elementResizeDetectorMaker from 'element-resize-detector';
  import { useDesignSetting } from '@/hooks/setting/useDesignSetting';
  import { useProjectSettingStore } from '@/store/modules/projectSetting';
  import { useThemeVars } from 'naive-ui';
  import { useGo } from '@/hooks/web/usePage';

  import { useTabsViewStore } from '@/store/modules/tabsView';

  export default defineComponent({
    name: 'TabsView',
    components: {
      DownOutlined,
      CloseOutlined,
      LeftOutlined,
      RightOutlined,
    },
    props: {
      collapsed: {
        type: Boolean,
      },
    },
    setup(props) {
      //const { getDarkTheme, getAppTheme } = useDesignSetting();
      const { navMode, headerSetting, menuSetting, multiTabsSetting, isMobile } =
        useProjectSetting();
      const settingStore = useProjectSettingStore();
      const tabsViewStore = useTabsViewStore();

      const message = useMessage();
      const route = useRoute();
      const router = useRouter();
      const asyncRouteStore = useAsyncRouteStore();
      const navScroll: any = ref(null);
      const navWrap: any = ref(null);
      const isCurrent = ref(false);
      const go = useGo();

      const themeVars = useThemeVars();

      const state = reactive({
        activeKey: route.fullPath,
        scrollable: false,
        dropdownX: 0,
        dropdownY: 0,
        showDropdown: false,
        isMultiHeaderFixed: false,
        multiTabsSetting: multiTabsSetting,
      });

      // 标签页列表
      const tabsList: any = computed(() => tabsViewStore.getTabsList);
      const whiteList: string[] = [
        PageEnum.BASE_LOGIN_NAME,
        PageEnum.REDIRECT_NAME,
        `${PageEnum.REDIRECT_NAME}Son`,
        PageEnum.ERROR_PAGE_NAME,
      ];

      watch(
        () => route.fullPath,
        (to) => {
          if (whiteList.includes(route.name as string)) return;
          state.activeKey = to;
          tabsViewStore.addVisitedView(route);
          updateNavScroll(true);
        },
        { immediate: true }
      );

      const isMixMenuNoneSub = computed(() => {
        const mixMenu = settingStore.menuSetting.mixMenu;
        const currentRoute = useRoute();
        if (navMode.value != 'horizontal-mix') return true;
        return !(navMode.value === 'horizontal-mix' && mixMenu && currentRoute.meta.isRoot);
      });

      //动态组装样式 菜单缩进
      const getChangeStyle = computed(() => {
        const { fixed }: any = multiTabsSetting.value;
        const { fixed: headerFixed }: any = headerSetting.value;
        let style: any = {
          left: '0px',
          width: '100%',
        };

        if (fixed && headerFixed) {
          style.top = '64px';
        }

        return style;
      });

      //tags 右侧下拉菜单
      const TabsMenuOptions = computed(() => {
        const isDisabled = tabsList.value.length <= 1;
        return [
          {
            label: '刷新当前',
            key: '1',
            icon: renderIcon(ReloadOutlined),
          },
          {
            label: '关闭当前',
            key: '2',
            icon: renderIcon(CloseOutlined),
            disabled: isDisabled,
          },
          {
            label: '关闭其他',
            key: '3',
            icon: renderIcon(ColumnWidthOutlined),
            disabled: isDisabled,
          },
          {
            label: '关闭全部',
            key: '4',
            icon: renderIcon(MinusOutlined),
            disabled: isDisabled,
          },
        ];
      });

      //监听滚动条
      function onScroll(e) {
        let scrollTop =
          e.target.scrollTop ||
          document.documentElement.scrollTop ||
          window.pageYOffset ||
          document.body.scrollTop; // 滚动条偏移量
        state.isMultiHeaderFixed = !!(
          !headerSetting.value.fixed &&
          multiTabsSetting.value.fixed &&
          scrollTop >= 64
        );
      }

      window.addEventListener('scroll', onScroll, true);

      // 移除缓存组件名称
      const delKeepAliveCompName = () => {
        if (route.meta.keepAlive) {
          const name = router.currentRoute.value.matched.find((item) => item.name == route.name)
            ?.components?.default.name;
          if (name) {
            asyncRouteStore.keepAliveComponents = asyncRouteStore.keepAliveComponents.filter(
              (item) => item != name
            );
          }
        }
      };

      // 刷新页面
      const reloadPage = () => {
        delKeepAliveCompName();
        router.push({
          path: '/redirect' + route.fullPath,
        });
      };

      // 注入刷新页面方法
      provide('reloadPage', reloadPage);

      //tab 操作
      const closeHandleSelect = (key) => {
        switch (key) {
          //刷新
          case '1':
            reloadPage();
            break;
          //关闭当前
          case '2':
            tabsViewStore.delVisitedView(route);
            if (tabsList.value.length === 0) {
              router.push('/');
            } else {
              const latest = tabsList.value[tabsList.value.length - 1];
              router.push(latest.fullPath);
            }
            break;
          //关闭其他
          case '3':
            tabsViewStore.delOthersVisitedViews(route);
            break;
          //关闭全部
          case '4':
            tabsViewStore.delAllVisitedViews();
            if (tabsList.value.length === 0) {
              router.push('/');
            } else {
              const latest = tabsList.value[tabsList.value.length - 1];
              router.push(latest.fullPath);
            }
            break;
        }
        updateNavScroll();
        state.showDropdown = false;
      };

      /**
       * @param value 要滚动到的位置
       * @param amplitude 每次滚动的长度
       */
      function scrollTo(value: number, amplitude: number) {
        const currentScroll = navScroll.value.scrollLeft;
        const scrollWidth =
          (amplitude > 0 && currentScroll + amplitude >= value) ||
          (amplitude < 0 && currentScroll + amplitude <= value)
            ? value
            : currentScroll + amplitude;
        navScroll.value && navScroll.value.scrollTo(scrollWidth, 0);
        if (scrollWidth === value) return;
        return window.requestAnimationFrame(() => scrollTo(value, amplitude));
      }

      function scrollPrev() {
        const containerWidth = navScroll.value.offsetWidth;
        const currentScroll = navScroll.value.scrollLeft;

        if (!currentScroll) return;
        const scrollLeft = currentScroll > containerWidth ? currentScroll - containerWidth : 0;
        scrollTo(scrollLeft, (scrollLeft - currentScroll) / 20);
      }

      function scrollNext() {
        const containerWidth = navScroll.value.offsetWidth;
        const navWidth = navScroll.value.scrollWidth;
        const currentScroll = navScroll.value.scrollLeft;

        if (navWidth - currentScroll <= containerWidth) return;
        const scrollLeft =
          navWidth - currentScroll > containerWidth * 2
            ? currentScroll + containerWidth
            : navWidth - containerWidth;
        scrollTo(scrollLeft, (scrollLeft - currentScroll) / 20);
      }

      /**
       * @param autoScroll 是否开启自动滚动功能
       */
      async function updateNavScroll(autoScroll?: boolean) {
        await nextTick();
        if (!navScroll.value) return;
        const containerWidth = navScroll.value.offsetWidth;
        const navWidth = navScroll.value.scrollWidth;

        if (containerWidth < navWidth) {
          state.scrollable = true;
          if (autoScroll) {
            let tagList = navScroll.value.querySelectorAll('.tabs-card-scroll-item') || [];
            [...tagList].forEach((tag: HTMLElement) => {
              // fix SyntaxError
              if (tag.id === `tag${state.activeKey.split('/').join('\/')}`) {
                tag.scrollIntoView && tag.scrollIntoView();
              }
            });
          }
        } else {
          state.scrollable = false;
        }
      }

      function handleResize() {
        updateNavScroll(true);
      }

      function handleContextMenu(e, item) {
        e.preventDefault();
        isCurrent.value = PageEnum.BASE_HOME_REDIRECT === item.path;
        state.showDropdown = false;
        nextTick().then(() => {
          state.showDropdown = true;
          state.dropdownX = e.clientX;
          state.dropdownY = e.clientY;
        });
      }

      function onClickOutside() {
        state.showDropdown = false;
      }

      //tags 跳转页面
      function goPage(e) {
        const { fullPath } = e;
        if (fullPath === route.fullPath) return;
        state.activeKey = fullPath;
        go(e, true);
      }

      function getTabItemStyle(element) {
        if (state.activeKey !== element.fullPath) return {};
        return {
          backgroundColor: themeVars.value.primaryColor,
          borderColor: themeVars.value.primaryColor,
        };
      }

      //删除tab
      function closeTabItem(e) {
        const { fullPath } = e;
        tabsViewStore.delVisitedView(e);
        if (fullPath === route.fullPath) {
          const latest = tabsList.value[tabsList.value.length - 1];
          if (latest) {
            router.push(latest.fullPath);
          } else {
            router.push('/');
          }
        }
      }

      onMounted(() => {
        onElementResize();
      });

      function onElementResize() {
        let observer;
        observer = elementResizeDetectorMaker();
        observer.listenTo(navWrap.value, handleResize);
      }

      return {
        ...toRefs(state),
        navWrap,
        navScroll,
        route,
        tabsList,
        goPage,
        getTabItemStyle,
        closeTabItem,
        reloadPage,
        getChangeStyle,
        TabsMenuOptions,
        closeHandleSelect,
        scrollNext,
        scrollPrev,
        handleContextMenu,
        onClickOutside,
      };
    },
  });
</script>

<style lang="less" scoped>
  .tabs-view {
    width: 100%;
    padding: 8px 6px;
    display: flex;
    background-color: var(--n-body-color); /* 由 Naive UI 的背景色接管 */

    &-main {
      height: 34px;
      display: flex;
      max-width: 100%;
      min-width: 100%;

      .tabs-card {
        -webkit-box-flex: 1;
        flex-grow: 1;
        flex-shrink: 1;
        overflow: hidden;
        position: relative;

        .tabs-card-prev,
        .tabs-card-next {
          width: 32px;
          text-align: center;
          position: absolute;
          line-height: 34px;
          cursor: pointer;
          color: var(--n-text-color-3);

          .n-icon {
            display: flex;
            align-items: center;
            justify-content: center;
            height: 34px;
            width: 32px;
            transition: color 0.3s;
          }
          &:hover .n-icon {
            color: var(--n-text-color-1);
          }
        }

        .tabs-card-prev {
          left: 0;
        }

        .tabs-card-next {
          right: 0;
        }

        .tabs-card-next-hide,
        .tabs-card-prev-hide {
          display: none;
        }

        &-scroll {
          white-space: nowrap;
          overflow: hidden;

          /* 胶囊标签核心样式 */
          &-item {
            background-color: var(--n-card-color);
            border: 1px solid var(--n-border-color);
            color: var(--n-text-color-2);
            height: 32px;
            padding: 0 10px;
            border-radius: 16px; /* 圆润的胶囊圆角 */
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: flex-start;
            position: relative;
            flex: 0 0 auto;
            transition: all 0.3s;
            font-size: 14px;

            span {
              text-align: left;
            }

            &:hover {
              color: var(--n-primary-color);
              border-color: var(--n-primary-color);
            }

            .close-icon {
              margin-left: 6px;
              margin-right: -4px;
              color: var(--n-text-color-3);
              font-size: 12px;
              border-radius: 50%;
              transition: all 0.3s;

              &:hover {
                background-color: var(--n-border-color);
                color: var(--n-text-color-1);
              }
            }
          }

          /* 激活状态：实心科技蓝背景，纯白文字 */
          .active-item {
            font-weight: 500;

            /* 新增：必须直接靶向 span 强制文字为白色，解决白色留白问题 */
            span {
              color: #ffffff !important;
            }

            .close-icon {
              color: rgba(255, 255, 255, 0.7);
              &:hover {
                background-color: rgba(0, 0, 0, 0.1);
                color: #ffffff;
              }
            }
          }
        }
      }

      .tabs-card-scrollable {
        padding: 0 32px;
        overflow: hidden;
      }
    }

    .tabs-close {
      min-width: 32px;
      width: 32px;
      height: 32px;
      line-height: 32px;
      text-align: center;
      background: var(--n-card-color);
      border: 1px solid var(--n-border-color);
      border-radius: 4px;
      cursor: pointer;
      margin-left: 8px;
      transition: all 0.3s;

      &:hover {
        border-color: var(--n-primary-color);
      }

      &-btn {
        color: var(--n-text-color-3);
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: color 0.3s;

        &:hover {
          color: var(--n-primary-color);
        }
      }
    }
  }

  .tabs-view-fix {
    position: sticky;
    top: var(--app-header-height, 64px);
    z-index: 9;
    padding: 6px 4px;
  }

  .tabs-view-fixed-header {
    top: 0;
  }
</style>
