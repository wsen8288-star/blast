<template>
  <n-drawer v-model:show="isDrawer" :width="width" :placement="placement">
    <n-drawer-content :native-scrollbar="false">
      <template #header>
        <span style="color: var(--n-text-color-1); font-weight: 600;">{{ title }}</span>
      </template>

      <div class="drawer">
        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">主题</div>
          <div class="line"></div>
        </div>

        <div class="justify-center drawer-setting-item dark-switch">
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-switch v-model:value="designStore.darkTheme" class="dark-theme-switch">
                <template #checked>
                  <n-icon size="14" color="#ffd93b">
                    <SunnySharp />
                  </n-icon>
                </template>
                <template #unchecked>
                  <n-icon size="14" color="#ffd93b">
                    <Moon />
                  </n-icon>
                </template>
              </n-switch>
            </template>
            <span>{{ designStore.darkTheme ? '深' : '浅' }}色主题</span>
          </n-tooltip>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">系统主题</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item align-items-top">
          <span
            class="theme-item"
            v-for="(item, index) in appThemeList"
            :key="index"
            :style="{ 'background-color': item }"
            @click="togTheme(item)"
          >
            <n-icon size="12" v-if="item === designStore.appTheme">
              <CheckOutlined />
            </n-icon>
          </span>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">导航栏模式</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item align-items-top">
          <div class="drawer-setting-item-style align-items-top">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-mode-icon" @click="togNavMode('vertical')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <line x1="3" y1="12" x2="21" y2="12" />
                      <line x1="3" y1="6" x2="21" y2="6" />
                      <line x1="3" y1="18" x2="21" y2="18" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>左侧菜单模式</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-show="settingStore.navMode === 'vertical'" />
          </div>

          <div class="drawer-setting-item-style">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-mode-icon" @click="togNavMode('horizontal')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <line x1="3" y1="12" x2="21" y2="12" />
                      <line x1="3" y1="12" x2="3" y2="6" />
                      <line x1="3" y1="12" x2="3" y2="18" />
                      <line x1="21" y1="12" x2="21" y2="6" />
                      <line x1="21" y1="12" x2="21" y2="18" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>顶部菜单模式</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-show="settingStore.navMode === 'horizontal'" />
          </div>

          <div class="drawer-setting-item-style">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-mode-icon" @click="togNavMode('horizontal-mix')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <line x1="3" y1="12" x2="21" y2="12" />
                      <line x1="3" y1="6" x2="21" y2="6" />
                      <line x1="3" y1="12" x2="3" y2="18" />
                      <line x1="21" y1="12" x2="21" y2="18" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>顶部菜单混合模式</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-show="settingStore.navMode === 'horizontal-mix'" />
          </div>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">导航栏风格</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item align-items-top">
          <div class="drawer-setting-item-style align-items-top">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-theme-icon" @click="togNavTheme('dark')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <rect x="2" y="2" width="20" height="20" rx="5" ry="5" />
                      <path d="M12 8v4l3 3" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>暗色侧边栏</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-if="settingStore.navTheme === 'dark'" />
          </div>

          <div class="drawer-setting-item-style">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-theme-icon" @click="togNavTheme('light')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <rect x="2" y="2" width="20" height="20" rx="5" ry="5" />
                      <path d="M12 8v4l3 3" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>白色侧边栏</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-if="settingStore.navTheme === 'light'" />
          </div>

          <div class="drawer-setting-item-style">
            <n-tooltip placement="top">
              <template #trigger>
                <div class="nav-theme-icon" @click="togNavTheme('header-dark')">
                  <n-icon size="24" class="theme-icon">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <rect x="2" y="2" width="20" height="20" rx="5" ry="5" />
                      <path d="M12 8v4l3 3" />
                    </svg>
                  </n-icon>
                </div>
              </template>
              <span>暗色顶栏</span>
            </n-tooltip>
            <n-badge dot color="#19be6b" v-if="settingStore.navTheme === 'header-dark'" />
          </div>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">界面功能</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 分割菜单 </div>
          <div class="drawer-setting-item-action">
            <n-switch
              :disabled="settingStore.navMode !== 'horizontal-mix'"
              v-model:value="settingStore.menuSetting.mixMenu"
            />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 固定顶栏 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.headerSetting.fixed" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 固定侧边栏 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.menuSetting.fixed" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 固定多页签 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.multiTabsSetting.fixed" />
          </div>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">界面显示</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 显示重载页面按钮 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.headerSetting.isReload" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 显示面包屑导航 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.crumbsSetting.show" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 显示面包屑显示图标 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.crumbsSetting.showIcon" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 显示多页签 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.multiTabsSetting.show" />
          </div>
        </div>
        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 显示页脚 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.showFooter" />
          </div>
        </div>

        <div class="setting-divider">
          <div class="line"></div>
          <div class="text">动画</div>
          <div class="line"></div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 启用动画 </div>
          <div class="drawer-setting-item-action">
            <n-switch v-model:value="settingStore.isPageAnimate" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <div class="drawer-setting-item-title"> 动画类型 </div>
          <div class="drawer-setting-item-select">
            <n-select v-model:value="settingStore.pageAnimateType" :options="animateOptions" />
          </div>
        </div>

        <div class="drawer-setting-item">
          <n-alert type="warning" :showIcon="false">
            <p style="color: var(--n-text-color-1);">{{ alertText }}</p>
          </n-alert>
        </div>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<script lang="ts">
  import { defineComponent, reactive, toRefs, unref, watch, computed } from 'vue';
  import { useProjectSettingStore } from '@/store/modules/projectSetting';
  import { useDesignSettingStore } from '@/store/modules/designSetting';
  import { CheckOutlined } from '@vicons/antd';
  import { Moon, SunnySharp } from '@vicons/ionicons5';
  import { darkTheme } from 'naive-ui';
  import { animates as animateOptions } from '@/settings/animateSetting';

  export default defineComponent({
    name: 'ProjectSetting',
    components: { CheckOutlined, Moon, SunnySharp },
    props: {
      title: {
        type: String,
        default: '项目配置',
      },
      width: {
        type: Number,
        default: 280,
      },
    },
    setup(props) {
      const settingStore = useProjectSettingStore();
      const designStore = useDesignSettingStore();
      const state = reactive({
        width: props.width,
        title: props.title,
        isDrawer: false,
        placement: 'right',
        alertText: '该功能主要实时预览各种布局效果，更多完整配置在 projectSetting.ts 中设置',
        appThemeList: designStore.appThemeList,
      });

      watch(
        () => designStore.darkTheme,
        (to) => {
          settingStore.navTheme = to ? 'header-dark' : 'dark';
        }
      );

      const directionsOptions = computed(() => {
        return animateOptions.find((item) => item.value == unref(settingStore.pageAnimateType));
      });

      function openDrawer() {
        state.isDrawer = true;
      }

      function closeDrawer() {
        state.isDrawer = false;
      }

      function togNavTheme(theme) {
        settingStore.navTheme = theme;
      }

      function togTheme(color) {
        designStore.appTheme = color;
      }

      function togNavMode(mode) {
        settingStore.navMode = mode;
        if (mode !== 'horizontal-mix') {
           settingStore.menuSetting.mixMenu = false;
        }
      }

      return {
        ...toRefs(state),
        settingStore,
        designStore,
        togNavTheme,
        togNavMode,
        togTheme,
        darkTheme,
        openDrawer,
        closeDrawer,
        animateOptions,
        directionsOptions,
      };
    },
  });
</script>

<style lang="less" scoped>
  .drawer {
    color: var(--n-text-color-1);

    /* --- 终极杀手锏：自定义分割线，彻底抛弃 Naive UI 的 n-divider --- */
    .setting-divider {
      display: flex;
      align-items: center;
      margin: 20px 0 12px 0;

      .line {
        flex: 1;
        height: 1px;
        background-color: var(--n-border-color);
        transition: background-color 0.3s;
      }

      .text {
        padding: 0 16px;
        font-size: 14px;
        font-weight: 500;
        /* 强制接管颜色，免疫所有干扰 */
        color: var(--n-text-color-1);
        transition: color 0.3s;
      }
    }
    /* -------------------------------------------------------- */

    &-setting-item {
      display: flex;
      align-items: center;
      padding: 12px 0;
      flex-wrap: wrap;

      &-style {
        display: inline-block;
        position: relative;
        margin-right: 16px;
        cursor: pointer;
        text-align: center;
        transition: all 0.2s;
        
        &:hover {
          transform: translateY(-2px);
        }
      }

      &-title {
        flex: 1 1;
        font-size: 14px;
        color: var(--n-text-color-1);
      }

      &-action {
        flex: 0 0 auto;
      }

      &-select {
        flex: 1;
        min-width: 100px;
      }

      .theme-item {
        width: 20px;
        min-width: 20px;
        height: 20px;
        cursor: pointer;
        border: 1px solid var(--n-border-color);
        border-radius: 2px;
        margin: 0 5px 5px 0;
        text-align: center;
        line-height: 14px;
        transition: all 0.2s;

        &:hover {
          transform: scale(1.1);
          box-shadow: 0 0 5px rgba(0,0,0,0.2);
        }

        .n-icon {
          color: #fff;
        }
      }
    }

    .theme-icon {
      color: var(--n-text-color-3);
      transition: color 0.3s;
    }

    .drawer-setting-item-style:hover .theme-icon {
      color: var(--n-primary-color);
    }

    .align-items-top {
      align-items: flex-start;
      padding: 2px 0;
    }

    .justify-center {
      justify-content: center;
    }
  }
</style>