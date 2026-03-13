<template>
  <div class="page-footer">
    <div class="page-footer-link">
      <span>基于进化计算的高炉铁水质量智能监控与优化平台 v1.0.0</span>
    </div>
    <div class="copyright">Copyright © 2026 | 智能制造与工业数据分析课题组</div>
  </div>
</template>

<script>
  export default {
    name: 'PageFooter',
    components: {},
    props: {
      collapsed: {
        type: Boolean,
      },
    },
  };
</script>

<style lang="less" scoped>
  .page-footer {
    padding: 12px 16px 10px;
    text-align: center;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-card-color);

    a {
      font-size: 14px;
      color: var(--n-text-color-3);
      -webkit-transition: all 0.2s ease-in-out;
      transition: all 0.2s ease-in-out;

      &:hover {
        color: var(--n-text-color-2);
      }
    }

    &-link {
      display: flex;
      justify-content: center;
      margin-bottom: 8px;

      a:not(:last-child) {
        margin-right: 40px;
      }
    }

    .copyright {
      color: var(--n-text-color-3);
      font-size: 13px;
    }
  }
</style>
