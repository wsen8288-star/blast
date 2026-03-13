import type { ECharts, EChartsOption } from 'echarts';
import type { Ref } from 'vue';
import * as echarts from 'echarts';
import { nextTick, onUnmounted, unref, watch } from 'vue';

export function useECharts(elRef: Ref<HTMLDivElement | null>, theme: 'light' | 'dark' | 'default' = 'default') {
  let chartInstance: ECharts | null = null;
  let resizeTimer: number | null = null;

  function normalizeTheme(t: 'light' | 'dark' | 'default') {
    return t === 'dark' ? 'dark' : undefined;
  }

  function initCharts(t = theme) {
    const el = unref(elRef);
    if (!el) return;
    if (!chartInstance) {
      chartInstance = echarts.init(el, normalizeTheme(t));
      window.addEventListener('resize', resize);
    }
  }

  function setOptions(options: EChartsOption, clear = true) {
    nextTick(() => {
      initCharts(theme);
      if (!chartInstance) return;
      if (clear) chartInstance.clear();
      chartInstance.setOption({ backgroundColor: 'transparent', ...options });
    });
  }

  function resize() {
    if (resizeTimer) window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(() => {
      chartInstance?.resize();
    }, 120);
  }

  function getInstance() {
    initCharts(theme);
    return chartInstance;
  }

  function disposeInstance() {
    if (resizeTimer) {
      window.clearTimeout(resizeTimer);
      resizeTimer = null;
    }
    if (chartInstance) {
      chartInstance.dispose();
      chartInstance = null;
    }
    window.removeEventListener('resize', resize);
  }

  watch(
    () => theme,
    () => {
      const oldOpts = chartInstance?.getOption() as EChartsOption | undefined;
      disposeInstance();
      initCharts(theme);
      if (oldOpts) setOptions(oldOpts);
    }
  );

  onUnmounted(disposeInstance);

  return {
    setOptions,
    resize,
    echarts,
    getInstance,
    disposeInstance
  };
}
