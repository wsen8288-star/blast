<template>
  <div class="logo" @click="handleClick">
    <img :src="websiteConfig.logo" alt="" :class="{ 'mr-2': !collapsed }" />
    <h2
      v-show="!collapsed"
      class="title bg-gradient-to-r from-blue-400 to-cyan-300 bg-clip-text text-transparent font-semibold tracking-wide"
    >
      {{ systemTitle }}
    </h2>
  </div>
</template>

<script lang="ts">
  import { websiteConfig } from '@/config/website.config';
  import { systemApi } from '@/api/blast-furnace';
  export default {
    name: 'Index',
    props: {
      collapsed: {
        type: Boolean,
      },
    },
    emits: ['click'],
    data() {
      return {
        websiteConfig,
        systemTitle: localStorage.getItem('system_name_runtime') || websiteConfig.title,
      };
    },
    mounted() {
      this.loadSystemTitle();
    },
    methods: {
      async loadSystemTitle() {
        try {
          const res: any = await systemApi.config.getListByGroup('SYSTEM_CONFIG');
          const items: any[] = Array.isArray(res?.data) ? res.data : [];
          const configMap = new Map(items.map((item: any) => [item?.configKey, item?.configValue]));
          const name = String(configMap.get('system_name') ?? '').trim();
          if (name) {
            this.systemTitle = name;
            localStorage.setItem('system_name_runtime', name);
          }
        } catch (_) {
          return;
        }
      },
      handleClick() {
        this.$emit('click');
      },
    },
  };
</script>

<style lang="less" scoped>
  .logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 64px;
    line-height: 64px;
    overflow: hidden;
    white-space: nowrap;
    cursor: pointer;
    border-bottom: 1px solid var(--n-border-color);

    img {
      width: auto;
      height: 32px;
    }

    .title {
      margin: 0;
      max-width: 170px;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
</style>
