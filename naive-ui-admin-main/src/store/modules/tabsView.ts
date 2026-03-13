import { defineStore } from 'pinia';
import type {
  LocationQuery,
  RouteLocationNormalizedLoaded,
  RouteMeta,
  RouteParamsGeneric,
  RouteRecordName,
} from 'vue-router';
import { storage } from '@/utils/Storage';
import { TABS_ROUTES } from '@/store/mutation-types';

export type TabItem = {
  name: RouteRecordName | null | undefined;
  path: string;
  fullPath: string;
  meta: RouteMeta;
  params: RouteParamsGeneric;
  query: LocationQuery;
  title?: string;
};

export interface ITabsViewState {
  tabsList: TabItem[];
}

// 不需要在这里定义 IStore 接口，因为它是外部引用的

// 保留白名单
const whiteList = ['Redirect', 'login'];

function toTabItem(route: RouteLocationNormalizedLoaded): TabItem {
  return {
    name: route.name,
    path: route.path,
    fullPath: route.fullPath,
    meta: route.meta,
    params: route.params,
    query: route.query,
    title: String(route.meta?.title ?? 'no-name'),
  };
}

export const useTabsViewStore = defineStore({
  id: 'app-tabs-view',
  state: (): ITabsViewState => ({
    tabsList: storage.get(TABS_ROUTES, []) as TabItem[],
  }),
  getters: {
    getTabsList(): TabItem[] {
      return this.tabsList;
    },
  },
  actions: {
    initTabs(routes: TabItem[]) {
      this.tabsList = routes;
    },
    addVisitedView(route: RouteLocationNormalizedLoaded) {
      if (whiteList.includes(route.name as string)) return;
      if (this.tabsList.some((item) => item.fullPath === route.fullPath)) return;

      this.tabsList.push(toTabItem(route));
      this.updateStorage();
    },
    delVisitedView(route: { fullPath: string }) {
      const index = this.tabsList.findIndex((item) => item.fullPath === route.fullPath);
      if (index > -1) {
        this.tabsList.splice(index, 1);
        this.updateStorage();
      }
    },
    delOthersVisitedViews(route: { fullPath: string }) {
      this.tabsList = this.tabsList.filter(
        (item) => item.meta?.affix || item.fullPath === route.fullPath
      );
      this.updateStorage();
    },
    delAllVisitedViews() {
      const affixTags = this.tabsList.filter((item) => item.meta?.affix);
      this.tabsList = affixTags;
      this.updateStorage();
    },
    updateVisitedView(route: RouteLocationNormalizedLoaded) {
      const index = this.tabsList.findIndex((item) => item.fullPath === route.fullPath);
      if (index > -1) {
        Object.assign(this.tabsList[index], toTabItem(route));
        this.updateStorage();
      }
    },
    // 关闭左侧
    delLeftVisitedViews(route: { fullPath: string }) {
      const index = this.tabsList.findIndex((item) => item.fullPath === route.fullPath);
      if (index > -1) {
        const leftTabs = this.tabsList.filter((item, i) => i < index && !item.meta?.affix);
        const leftPaths = leftTabs.map((item) => item.fullPath);
        this.tabsList = this.tabsList.filter((item) => !leftPaths.includes(item.fullPath));
        this.updateStorage();
      }
    },
    // 关闭右侧
    delRightVisitedViews(route: { fullPath: string }) {
      const index = this.tabsList.findIndex((item) => item.fullPath === route.fullPath);
      if (index > -1) {
        const rightTabs = this.tabsList.filter((item, i) => i > index && !item.meta?.affix);
        const rightPaths = rightTabs.map((item) => item.fullPath);
        this.tabsList = this.tabsList.filter((item) => !rightPaths.includes(item.fullPath));
        this.updateStorage();
      }
    },
    updateStorage() {
      storage.set(TABS_ROUTES, this.tabsList);
    },
  },
});
