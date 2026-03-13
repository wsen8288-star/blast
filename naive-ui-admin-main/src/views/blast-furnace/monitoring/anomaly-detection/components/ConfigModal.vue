<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="异常检测阈值配置"
    style="width: 800px"
  >
    <div class="mb-4 rounded-lg border p-4" style="background-color: var(--n-color); border-color: var(--n-border-color);">
      <n-form inline label-placement="left">
        <n-form-item label="高炉">
          <n-select
            v-model:value="queryFurnaceId"
            :options="furnaceOptions"
            placeholder="选择高炉"
            style="width: 200px"
            @update:value="loadThresholds"
          />
        </n-form-item>
        <n-form-item>
          <n-button type="primary" @click="openEditModal(null)">新增配置</n-button>
        </n-form-item>
      </n-form>
    </div>

    <n-data-table
      :columns="columns"
      :data="thresholdList"
      :loading="loading"
      :pagination="{ pageSize: 10 }"
    />

    <!-- 编辑/新增弹窗 -->
    <n-modal v-model:show="editVisible" preset="dialog" :title="editingId ? '编辑配置' : '新增配置'">
      <n-form
        ref="formRef"
        :model="formModel"
        :rules="rules"
        label-placement="left"
        label-width="100"
        class="mt-4"
      >
        <n-form-item label="高炉" path="furnaceId">
          <n-select
            v-model:value="formModel.furnaceId"
            :options="furnaceOptions"
            placeholder="选择高炉"
          />
        </n-form-item>
        <n-form-item label="参数" path="parameterName">
          <n-select
            v-model:value="formModel.parameterName"
            :options="paramOptions"
            placeholder="选择检测参数"
          />
        </n-form-item>
        <n-form-item label="最小值" path="minVal">
          <n-input-number v-model:value="formModel.minVal" placeholder="请输入最小值" />
        </n-form-item>
        <n-form-item label="最大值" path="maxVal">
          <n-input-number v-model:value="formModel.maxVal" placeholder="请输入最大值" />
        </n-form-item>
        <n-form-item label="提示偏移" path="tipOffsetPct">
          <n-input-number
            v-model:value="formModel.tipOffsetPct"
            :min="0"
            :max="100"
            placeholder="百分比"
          />
        </n-form-item>
        <n-form-item label="警告偏移" path="warningOffsetPct">
          <n-input-number
            v-model:value="formModel.warningOffsetPct"
            :min="0"
            :max="100"
            placeholder="百分比"
          />
        </n-form-item>
        <n-form-item label="严重偏移" path="severeOffsetPct">
          <n-input-number
            v-model:value="formModel.severeOffsetPct"
            :min="0"
            :max="100"
            placeholder="百分比"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="editVisible = false">取消</n-button>
        <n-button type="primary" @click="submitSave">保存</n-button>
      </template>
    </n-modal>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive, h } from 'vue';
  import {
    NModal,
    NForm,
    NFormItem,
    NSelect,
    NButton,
    NDataTable,
    NInputNumber,
    useMessage,
    NPopconfirm,
    type FormRules,
  } from 'naive-ui';
  import { anomalyConfigApi } from '@/api/blast-furnace';

  const message = useMessage();
  const visible = ref(false);
  const loading = ref(false);
  const thresholdList = ref([]);
  const queryFurnaceId = ref('BF-001');

  const furnaceOptions = [
    { label: '全局配置 (GLOBAL)', value: 'GLOBAL' },
    { label: '高炉1 (BF-001)', value: 'BF-001' },
    { label: '高炉2 (BF-002)', value: 'BF-002' },
    { label: '高炉3 (BF-003)', value: 'BF-003' },
  ];

  const paramOptions = [
    { label: '温度', value: 'temperature' },
    { label: '压力', value: 'pressure' },
    { label: '风量', value: 'windVolume' },
    { label: '喷煤量', value: 'coalInjection' },
    { label: '料面高度', value: 'materialHeight' },
    { label: '煤气流量', value: 'gasFlow' },
    { label: '氧气含量', value: 'oxygenLevel' },
    { label: '生产率', value: 'productionRate' },
    { label: '能耗', value: 'energyConsumption' },
    { label: '铁水温度', value: 'hotMetalTemperature' },
    { label: '铁水含硅量', value: 'siliconContent' },
  ];

  const columns = [
    { title: '高炉', key: 'furnaceId' },
    {
      title: '参数',
      key: 'parameterName',
      render: (row: any) => {
        const opt = paramOptions.find((p) => p.value === row.parameterName);
        return opt ? opt.label : row.parameterName;
      },
    },
    { title: '最小值', key: 'minVal' },
    { title: '最大值', key: 'maxVal' },
    {
      title: '提示偏移(%)',
      key: 'tipOffsetPct',
      render: (row: any) => (row.tipOffsetPct === null || row.tipOffsetPct === undefined ? '-' : row.tipOffsetPct),
    },
    {
      title: '警告偏移(%)',
      key: 'warningOffsetPct',
      render: (row: any) =>
        row.warningOffsetPct === null || row.warningOffsetPct === undefined ? '-' : row.warningOffsetPct,
    },
    {
      title: '严重偏移(%)',
      key: 'severeOffsetPct',
      render: (row: any) =>
        row.severeOffsetPct === null || row.severeOffsetPct === undefined ? '-' : row.severeOffsetPct,
    },
    {
      title: '更新时间',
      key: 'updateTime',
      render: (row: any) => (row.updateTime ? row.updateTime.replace('T', ' ') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      render: (row: any) => {
        return [
          h(
            NButton,
            {
              size: 'small',
              type: 'primary',
              ghost: true,
              style: 'margin-right: 8px',
              onClick: () => openEditModal(row),
            },
            { default: () => '编辑' }
          ),
          h(
            NPopconfirm,
            {
              onPositiveClick: () => handleDelete(row.id),
            },
            {
              trigger: () =>
                h(
                  NButton,
                  {
                    size: 'small',
                    type: 'error',
                    ghost: true,
                  },
                  { default: () => '删除' }
                ),
              default: () => '确认删除该配置吗？',
            }
          ),
        ];
      },
    },
  ];

  // 编辑/新增相关
  const editVisible = ref(false);
  const editingId = ref<number | null>(null);
  const formRef = ref<any>(null);
  const formModel = reactive({
    furnaceId: 'BF-001',
    parameterName: 'temperature',
    minVal: 0,
    maxVal: 0,
    tipOffsetPct: 0,
    warningOffsetPct: 10,
    severeOffsetPct: 20,
  });

  const rules: FormRules = {
    furnaceId: [{ required: true, message: '请选择高炉', trigger: ['blur', 'change'] }],
    parameterName: [{ required: true, message: '请选择参数', trigger: ['blur', 'change'] }],
    minVal: [{ required: true, type: 'number', message: '请输入最小值', trigger: ['blur', 'change'] }],
    maxVal: [{ required: true, type: 'number', message: '请输入最大值', trigger: ['blur', 'change'] }],
    tipOffsetPct: [{ required: true, type: 'number', message: '请输入提示偏移', trigger: ['blur', 'change'] }],
    warningOffsetPct: [{ required: true, type: 'number', message: '请输入警告偏移', trigger: ['blur', 'change'] }],
    severeOffsetPct: [{ required: true, type: 'number', message: '请输入严重偏移', trigger: ['blur', 'change'] }],
  };

  const open = () => {
    visible.value = true;
    loadThresholds();
  };

  const loadThresholds = async () => {
    loading.value = true;
    try {
      const res: any = await anomalyConfigApi.getThresholds({ furnaceId: queryFurnaceId.value });
      if (res.code === 200) {
        thresholdList.value = res.data;
      }
    } catch (e) {
      message.error('加载配置失败');
    } finally {
      loading.value = false;
    }
  };

  const openEditModal = (row: any) => {
    if (row) {
      editingId.value = row.id;
      formModel.furnaceId = row.furnaceId;
      formModel.parameterName = row.parameterName;
      formModel.minVal = row.minVal;
      formModel.maxVal = row.maxVal;
      formModel.tipOffsetPct = row.tipOffsetPct ?? 0;
      formModel.warningOffsetPct = row.warningOffsetPct ?? 10;
      formModel.severeOffsetPct = row.severeOffsetPct ?? 20;
    } else {
      editingId.value = null;
      // Default to current query furnace
      formModel.furnaceId = queryFurnaceId.value;
      formModel.parameterName = 'temperature';
      formModel.minVal = 0;
      formModel.maxVal = 100;
      formModel.tipOffsetPct = 0;
      formModel.warningOffsetPct = 10;
      formModel.severeOffsetPct = 20;
    }
    editVisible.value = true;
  };

  const submitSave = async () => {
    formRef.value?.validate(async (errors: any) => {
      if (!errors) {
        try {
          if (formModel.tipOffsetPct > formModel.warningOffsetPct || formModel.warningOffsetPct > formModel.severeOffsetPct) {
            message.error('偏移百分比需满足：提示 ≤ 警告 ≤ 严重');
            return;
          }
          const res: any = await anomalyConfigApi.saveThreshold({
            id: editingId.value,
            ...formModel,
          });
          if (res.code === 200) {
            message.success('保存成功');
            editVisible.value = false;
            loadThresholds();
          } else {
            message.error(res.msg || '保存失败');
          }
        } catch (e) {
          message.error('保存失败');
        }
      }
    });
  };

  const handleDelete = async (id: number) => {
    try {
      const res: any = await anomalyConfigApi.deleteThreshold(id);
      if (res.code === 200) {
        message.success('删除成功');
        loadThresholds();
      } else {
        message.error(res.msg || '删除失败');
      }
    } catch (e) {
      message.error('删除失败');
    }
  };

  defineExpose({ open });
</script>


