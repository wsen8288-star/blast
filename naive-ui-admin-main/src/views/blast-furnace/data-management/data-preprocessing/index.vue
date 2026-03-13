<template>
  <div
    class="data-preprocessing min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="数据预处理" class="mb-4 preprocess-main-card">
      <n-tabs ref="tabsRef" type="line" v-model:value="activeTab">
        <n-tab-pane name="import" tab="数据导入">
          <div class="preprocess-panel">
            <div class="mb-4 flex items-center gap-2">
              <n-select
                v-model:value="selectedImportTemplate"
                :options="importTemplateOptions"
                style="width: 280px"
                placeholder="选择外部导入模板"
              />
              <n-button round @click="loadImportTemplates">刷新模板</n-button>
              <n-button round type="success" :disabled="!importPreviewId" @click="confirmImport">确认导入</n-button>
            </div>
            <n-upload :default-upload="true" :custom-request="customUploadRequest" class="mb-4">
              <n-button round type="primary">
                <template #icon>
                  <n-icon><cloud-upload /></n-icon>
                </template>
                上传数据文件
              </n-button>
              <n-text depth="3" class="ml-2">支持 Excel/Csv 格式</n-text>
            </n-upload>

            <n-card
              title="模板映射预览"
              size="small"
              class="mb-4 preprocess-sub-card"
            >
              <div class="grid grid-cols-2 gap-4">
                <n-statistic label="匹配字段数" :value="importMatchedFieldCount" />
                <n-statistic label="单位换算单元格数" :value="importConvertedCellCount" />
              </div>
              <div class="mt-2 text-sm" style="color: var(--n-text-color-3);">
                未匹配字段: {{ importUnmatchedHeaders.length ? importUnmatchedHeaders.join('、') : '无' }}
              </div>
              <div class="mt-1 text-sm" style="color: var(--n-text-color-3);">
                缺失关键字段: {{ importMissingRequiredFields.length ? importMissingRequiredFields.join('、') : '无' }}
              </div>
            </n-card>

            <n-card
              title="数据预览"
              size="small"
              class="mb-4 overflow-x-auto preprocess-sub-card"
            >
              <div :style="{ minWidth: `${previewTableMinWidth}px` }">
                <n-data-table
                  v-if="previewAllData.length > 0"
                  :columns="previewColumns"
                  :data="previewAllData"
                  size="small"
                  :bordered="true"
                  :pagination="previewPagination"
                  :scroll-x="previewTableMinWidth"
                />
                <n-empty v-else description="请上传数据文件以预览" />
              </div>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="process" tab="预处理设置">
          <div class="preprocess-panel">
            <n-card title="预处理步骤" size="small" class="mb-4 preprocess-sub-card">
              <n-form label-placement="left" label-width="120">
                <n-form-item label="指定主键/ID列">
                  <n-select
                    v-model:value="idColumn"
                    :options="columnOptions"
                    placeholder="请选择不参与运算的ID列（如序号、时间等）"
                    clearable
                  />
                </n-form-item>
                <n-form-item label="缺失值处理">
                  <n-select
                    v-model:value="missingValueStrategy"
                    :options="missingValueOptions"
                    placeholder="选择处理策略"
                  />
                </n-form-item>
                <n-form-item label="异常值检测">
                  <n-checkbox-group v-model:value="outlierDetectionMethods">
                    <n-checkbox value="iqr">IQR 方法</n-checkbox>
                    <n-checkbox value="zscore">Z-Score 方法</n-checkbox>
                    <n-checkbox value="isolation">鲁棒偏差(MAD)</n-checkbox>
                  </n-checkbox-group>
                </n-form-item>
                <n-form-item label="异常值处理">
                  <n-select
                    v-model:value="outlierHandlingStrategy"
                    :options="outlierHandlingOptions"
                    placeholder="选择处理策略"
                  />
                </n-form-item>
                <n-form-item label="工艺参数范围">
                  <n-select
                    v-model:value="processParameterRangeStrategy"
                    :options="processParameterRangeOptions"
                    placeholder="选择处理策略"
                  />
                </n-form-item>
                <n-form-item label="数据标准化">
                  <n-select
                    v-model:value="normalizationMethod"
                    :options="normalizationOptions"
                    placeholder="选择标准化方法"
                  />
                </n-form-item>
                <n-form-item label="特征选择">
                  <n-checkbox-group v-model:value="featureSelectionMethods">
                    <n-checkbox value="correlation">相关性分析</n-checkbox>
                    <n-checkbox value="importance">特征重要性</n-checkbox>
                    <n-checkbox value="pca">主成分分析</n-checkbox>
                  </n-checkbox-group>
                </n-form-item>
                <n-form-item>
                  <n-button round type="primary" @click="startPreprocessing">开始预处理</n-button>
                  <n-button round @click="resetSettings" class="ml-2">重置设置</n-button>
                </n-form-item>
              </n-form>
            </n-card>

            <n-card title="预处理进度" size="small" v-if="isProcessing" class="preprocess-sub-card">
              <n-progress type="line" :percentage="processingProgress" :show-text="true" />
              <n-text depth="3" class="mt-2">{{ processingMessage }}</n-text>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="result" tab="处理结果">
          <div class="preprocess-panel">
            <div class="flex items-center justify-between mb-4">
              <n-text depth="2">处理结果统计</n-text>
              <div class="flex items-center gap-2">
                <n-select
                  v-model:value="anomalyFilter"
                  :options="anomalyFilterOptions"
                  style="width: 220px"
                />
                <n-button round type="primary" @click="exportResult">导出处理结果</n-button>
                <n-button round type="info" @click="exportSnapshot">导出快照包</n-button>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4 mb-4">
              <n-card size="small" class="preprocess-sub-card">
                <n-statistic label="原始数据量" :value="originalDataCount" />
              </n-card>
              <n-card size="small" class="preprocess-sub-card">
                <n-statistic label="处理后数据量" :value="processedDataCount" />
              </n-card>
              <n-card size="small" class="preprocess-sub-card">
                <n-statistic label="缺失值数量" :value="missingValueCount" />
              </n-card>
              <n-card size="small" class="preprocess-sub-card">
                <n-statistic label="超出范围数量" :value="outOfRangeCount" />
              </n-card>
              <n-card size="small" class="preprocess-sub-card">
                <n-statistic label="异常值数量" :value="outlierCount" />
              </n-card>
            </div>

            <n-card title="处理后数据预览" size="small" class="overflow-x-auto preprocess-sub-card">
              <div :style="{ minWidth: `${processedTableMinWidth}px` }">
                <n-data-table
                  v-if="processedData.length > 0"
                  :columns="processedColumns"
                  :data="filteredProcessedData"
                  size="small"
                  :bordered="true"
                  :pagination="processedPagination"
                  :scroll-x="processedTableMinWidth"
                />
                <n-empty v-else description="请先进行数据预处理" />
              </div>
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, h, nextTick, computed, onMounted } from 'vue';
  import { useRoute } from 'vue-router';
  import { NIcon, useMessage, NDataTable, NTabs } from 'naive-ui';
  import { CloudUpload } from '@vicons/ionicons5';
  import { dataManagementApi } from '@/api/blast-furnace/index';
  import { ensureQuickStartRunId, setQuickStartRun } from '@/utils/quickStartRun';

  const message = useMessage();
  const route = useRoute();
  const PREVIEW_LIMIT = 200;

  const toRawKey = (tableKey: string) => (tableKey.startsWith('col_') ? tableKey.substring(4) : tableKey);
  const toTableKey = (rawKey: string) => `col_${rawKey}`;
  const pickDefaultIdColumn = (keys: string[]) => {
    const preferred = ['id', 'ID', 'Id', '序号', '编号', 'recordId'];
    const keySet = new Set(keys);
    for (const candidate of preferred) {
      if (keySet.has(candidate)) {
        return toTableKey(candidate);
      }
    }
    return null;
  };

  const importTemplateOptions = ref<Array<{ label: string; value: string }>>([]);
  const selectedImportTemplate = ref<string | null>(null);
  const importPreviewId = ref<string | null>(null);
  const importUnmatchedHeaders = ref<string[]>([]);
  const importMissingRequiredFields = ref<string[]>([]);
  const importMatchedFieldCount = ref(0);
  const importConvertedCellCount = ref(0);
  const pendingImportFileName = ref('');

  const resetImportPreviewMeta = () => {
    importPreviewId.value = null;
    importUnmatchedHeaders.value = [];
    importMissingRequiredFields.value = [];
    importMatchedFieldCount.value = 0;
    importConvertedCellCount.value = 0;
    pendingImportFileName.value = '';
  };

  const fillPreviewTable = (rawList: any[]) => {
    const limited = Array.isArray(rawList) ? rawList.slice(0, PREVIEW_LIMIT) : [];
    if (!limited.length) {
      previewAllData.value = [];
      previewColumns.value = [];
      return;
    }
    const firstDataRow = limited[0];
    const columns = Object.keys(firstDataRow).map((key) => {
      let width = 120;
      if (key.includes('time') || key.includes('timestamp') || key.includes('时间')) {
        width = 180;
      } else if (key.includes('status') || key.includes('状态')) {
        width = 100;
      } else if (key.includes('production') || key.includes('productivity') || key.includes('生产')) {
        width = 90;
      }
      return {
        title: getColumnTitle(key),
        key: toTableKey(key),
        width,
        ellipsis: { tooltip: true },
      };
    });
    const data = limited.map((row: any, index: number) => {
      const newRow: any = { key: index };
      Object.keys(row).forEach((colKey) => {
        newRow[toTableKey(colKey)] = row[colKey];
      });
      return newRow;
    });
    previewColumns.value = columns;
    previewAllData.value = data;
    idColumn.value = pickDefaultIdColumn(Object.keys(firstDataRow));
  };

  const loadImportTemplates = async () => {
    try {
      const res: any = await dataManagementApi.getImportTemplates();
      if (res.code === 200 && Array.isArray(res.data)) {
        importTemplateOptions.value = res.data.map((item: any) => ({
          label: item.label || item.key,
          value: item.key,
        }));
        if (!selectedImportTemplate.value && importTemplateOptions.value.length > 0) {
          selectedImportTemplate.value = importTemplateOptions.value[0].value;
        }
      }
    } catch (error) {
      console.error('加载导入模板失败', error);
    }
  };

  // 文件上传
  const customUploadRequest = async (options: any) => {
    const { file, onFinish, onError } = options;
    try {
      message.loading('正在生成导入预览...');
      const rawFile = file.file;
      if (!rawFile) {
        throw new Error('获取文件对象失败');
      }
      const res: any = await dataManagementApi.previewExternalImport(
        rawFile,
        selectedImportTemplate.value || undefined
      );

      if (res.code === 200) {
        uploadedFileInfo.value = null;
        importPreviewId.value = res.data.previewId || null;
        importUnmatchedHeaders.value = res.data.unmatchedHeaders || [];
        importMissingRequiredFields.value = res.data.missingRequiredFields || [];
        importMatchedFieldCount.value = Number(res.data.matchedFieldCount || 0);
        importConvertedCellCount.value = Number(res.data.convertedCellCount || 0);
        pendingImportFileName.value = rawFile.name || 'external_import.csv';
        fillPreviewTable(res.data.previewRows || []);
        if (importMissingRequiredFields.value.length > 0) {
          message.warning(`预览完成，但缺失关键字段: ${importMissingRequiredFields.value.join(', ')}`);
        } else {
          message.success('导入预览已生成，请确认导入');
        }
        onFinish();
      } else {
        message.error(res.msg || '导入预览失败');
        onError(new Error(res.msg || '导入预览失败'));
      }
    } catch (error: any) {
      message.error('导入预览失败: ' + (error.message || '未知错误'));
      onError(error);
    }
  };

  const confirmImport = async () => {
    if (!importPreviewId.value) {
      message.error('请先上传并生成预览');
      return;
    }
    try {
      const res: any = await dataManagementApi.confirmExternalImport(
        importPreviewId.value,
        pendingImportFileName.value || undefined
      );
      if (res.code === 200) {
        uploadedFileInfo.value = res.data.fileInfo || null;
        fillPreviewTable(res.data.data || []);
        resetImportPreviewMeta();
        message.success('导入确认成功，可进入预处理流程');
      } else {
        message.error(res.msg || '导入确认失败');
      }
    } catch (error: any) {
      message.error('导入确认失败: ' + (error.message || '未知错误'));
    }
  };

  // 获取列标题的中文名称
  const getColumnTitle = (key: string) => {
    const titleMap: Record<string, string> = {
      id: 'ID',
      furnaceId: '高炉编号',
      windVolume: '风量(m³/h)',
      coalInjection: '喷煤量(kg/t)',
      windPressure: '风压',
      cokeRatio: '焦炭配比',
      furnaceTemperature: '炉温',
      siliconContent: '铁水含硅量',
      timestamp: '时间戳',
      temperature: '温度',
      pressure: '压力',
      level: '料面高度',
      materialHeight: '料面高度',
      gasFlow: '煤气流量',
      oxygenContent: '氧气含量',
      oxygenLevel: '氧气含量',
      productivity: '生产率',
      productionRate: '生产率',
      energyConsumption: '能耗',
      hotMetalTemperature: '铁水温度',
      constantSignal: '常量信号',
      status: '状态',
    };
    return titleMap[key] || key;
  };

  // 数据预览
  type TableColumn = { title: string; key: string; width?: number; ellipsis?: any; render?: any };
  const previewAllData = ref<any[]>([]);
  const previewColumns = ref<TableColumn[]>([]);

  const previewPagination = {
    defaultPageSize: 20,
    showSizePicker: true,
    pageSizes: [20, 50, 100, 200],
  };
  const previewTableMinWidth = computed(() => {
    const total = previewColumns.value.reduce((sum, col) => sum + Number(col.width || 120), 0);
    return Math.max(1200, total + 120);
  });

  // 上传文件信息
  const uploadedFileInfo = ref<any>(null);

  // tabs引用
  const tabsRef = ref<InstanceType<typeof NTabs> | null>(null);

  // 当前激活的标签页
  const activeTab = ref('import');

  // ID列选择
  const idColumn = ref<string | null>(null);
  const columnOptions = computed(() => {
    return previewColumns.value.map((col) => ({
      label: col.title,
      value: col.key,
    }));
  });

  // 预处理设置
  const missingValueStrategy = ref('mean');
  const missingValueOptions = [
    { label: '均值填充', value: 'mean' },
    { label: '中位数填充', value: 'median' },
    { label: '众数填充', value: 'mode' },
    { label: '删除记录', value: 'drop' },
  ];

  const outlierDetectionMethods = ref(['iqr', 'zscore']);
  const outlierHandlingStrategy = ref('mark');
  const outlierHandlingOptions = [
    { label: '删除异常值', value: 'drop' },
    { label: '标记异常值', value: 'mark' },
    { label: '替换异常值', value: 'replace' },
  ];

  const processParameterRangeStrategy = ref('mark');
  const processParameterRangeOptions = [
    { label: '删除超出范围数据', value: 'drop' },
    { label: '标记超出范围数据', value: 'mark' },
    { label: '替换为范围内值', value: 'replace' },
  ];

  const normalizationMethod = ref('minmax');
  const normalizationOptions = [
    { label: 'Min-Max 标准化', value: 'minmax' },
    { label: 'Z-Score 标准化', value: 'zscore' },
    { label: 'Robust 标准化', value: 'robust' },
    { label: '不标准化', value: 'none' },
  ];

  const featureSelectionMethods = ref(['correlation']);

  // 预处理进度
  const isProcessing = ref(false);
  const processingProgress = ref(0);
  const processingMessage = ref('准备开始预处理');

  // 处理结果
  const originalDataCount = ref(1000);
  const processedDataCount = ref(980);
  const missingValueCount = ref(20);
  const outOfRangeCount = ref(0);
  const outlierCount = ref(15);

  // 详细统计信息
  const detailedStats = ref({
    fixedRangeOutliers: 0,
    statisticalOutliers: 0,
    explanation: '',
  });

  const processedData = ref<any[]>([]);
  const processedColumns = ref<TableColumn[]>([]);
  const anomalyFilter = ref('all');
  const anomalyFilterOptions = [
    { label: '全部结果', value: 'all' },
    { label: '仅范围异常', value: 'out_of_range' },
    { label: '仅统计异常', value: 'outlier' },
  ];
  const filteredProcessedData = computed(() => {
    if (anomalyFilter.value === 'all') {
      return processedData.value;
    }
    if (anomalyFilter.value === 'out_of_range') {
      return processedData.value.filter((row) => !!row.__hasOutOfRange);
    }
    return processedData.value.filter((row) => !!row.__hasOutlier);
  });

  const processedPagination = {
    defaultPageSize: 20,
    showSizePicker: true,
    pageSizes: [20, 50, 100, 200],
  };
  const processedTableMinWidth = computed(() => {
    const total = processedColumns.value.reduce((sum, col) => sum + Number(col.width || 120), 0);
    return Math.max(1200, total + 120);
  });

  // 异常信息缓存，用于存储每个单元格的异常详情
  const anomalyInfoCache = ref<Map<string, string>>(new Map());

  // 参数范围映射，用于检查异常和生成提示信息
  const paramRangesMap = new Map<string, { min: number; max: number; name: string }>();

  // 初始化参数范围映射
  const initParamRanges = () => {
    paramRangesMap.clear();

    // 温度参数范围
    paramRangesMap.set('温度', { min: 1100, max: 1400, name: '温度' });
    paramRangesMap.set('铁水温度', { min: 1420, max: 1560, name: '铁水温度' });
    paramRangesMap.set('temperature', { min: 1100, max: 1400, name: '温度' });
    paramRangesMap.set('hotMetalTemperature', { min: 1420, max: 1560, name: '铁水温度' });

    // 压力参数范围
    paramRangesMap.set('压力', { min: 100, max: 300, name: '压力' });
    paramRangesMap.set('pressure', { min: 100, max: 300, name: '压力' });

    // 料面高度参数范围
    paramRangesMap.set('料面高度', { min: 3.0, max: 7.0, name: '料面高度' });
    paramRangesMap.set('料位高度', { min: 3.0, max: 7.0, name: '料面高度' });
    paramRangesMap.set('materialHeight', { min: 3.0, max: 7.0, name: '料面高度' });
    paramRangesMap.set('materialheight', { min: 3.0, max: 7.0, name: '料面高度' });
    paramRangesMap.set('level', { min: 3.0, max: 7.0, name: '料面高度' });

    // 煤气流量参数范围
    paramRangesMap.set('煤气流量', { min: 1300, max: 4000, name: '煤气流量' });
    paramRangesMap.set('gasFlow', { min: 1300, max: 4000, name: '煤气流量' });
    paramRangesMap.set('gasflow', { min: 1300, max: 4000, name: '煤气流量' });

    // 氧气含量参数范围
    paramRangesMap.set('氧气含量', { min: 18.0, max: 25.0, name: '氧气含量' });
    paramRangesMap.set('oxygenLevel', { min: 18.0, max: 25.0, name: '氧气含量' });
    paramRangesMap.set('oxygenlevel', { min: 18.0, max: 25.0, name: '氧气含量' });
    paramRangesMap.set('oxygenContent', { min: 18.0, max: 25.0, name: '氧气含量' });

    // 生产率参数范围
    paramRangesMap.set('生产率', { min: 20, max: 80, name: '生产率' });
    paramRangesMap.set('productionRate', { min: 20, max: 80, name: '生产率' });
    paramRangesMap.set('productionrate', { min: 20, max: 80, name: '生产率' });
    paramRangesMap.set('productivity', { min: 20, max: 80, name: '生产率' });

    // 能耗参数范围
    paramRangesMap.set('能耗', { min: 800, max: 2000, name: '能耗' });
    paramRangesMap.set('energyConsumption', { min: 800, max: 2000, name: '能耗' });
    paramRangesMap.set('energyconsumption', { min: 800, max: 2000, name: '能耗' });

    // 硅含量参数范围
    paramRangesMap.set('铁水含硅量', { min: 0.1, max: 1.0, name: '铁水含硅量' });
    paramRangesMap.set('siliconContent', { min: 0.1, max: 1.0, name: '铁水含硅量' });
    paramRangesMap.set('siliconcontent', { min: 0.1, max: 1.0, name: '铁水含硅量' });
  };

  const loadIndustrialSpecRanges = async () => {
    try {
      const res: any = await dataManagementApi.getDataSpec();
      const list = res?.data?.parameters;
      if (!Array.isArray(list) || !list.length) {
        return;
      }
      initParamRanges();
      list.forEach((item: any) => {
        const key = item?.key;
        const label = item?.label;
        const min = Number(item?.min);
        const max = Number(item?.max);
        if (!key || Number.isNaN(min) || Number.isNaN(max)) {
          return;
        }
        const payload = { min, max, name: label || key };
        paramRangesMap.set(key, payload);
        paramRangesMap.set(String(key).toLowerCase(), payload);
        if (label) {
          paramRangesMap.set(label, payload);
        }
      });
    } catch (error) {
      initParamRanges();
    }
  };

  onMounted(() => {
    initParamRanges();
    loadIndustrialSpecRanges();
    loadImportTemplates();
  });

  // 生成异常信息
  const generateAnomalyInfo = (
    rowId: any,
    param: string,
    value: any,
    type: string,
    details?: any
  ) => {
    const cacheKey = `${rowId}-${param}`;
    let info = '';

    // 获取参数名称和范围
    const paramTitle = getColumnTitle(param);
    const range = paramRangesMap.get(param) || paramRangesMap.get(paramTitle);
    const paramName = range?.name || paramTitle || param;
    const detailMin = Number(details?.min);
    const detailMax = Number(details?.max);
    const hasDetailRange = !Number.isNaN(detailMin) && !Number.isNaN(detailMax);
    const detailSource = String(details?.source || '').trim();
    const sourceText =
      detailSource === 'FURNACE'
        ? '高炉配置'
        : detailSource === 'GLOBAL'
        ? '全局配置'
        : detailSource === 'DEFAULT'
        ? '默认阈值'
        : detailSource === 'CONTRACT'
        ? '工业字典'
        : '';

    // 确保值是数字类型
    const numValue = parseFloat(String(value));
    const isNumber = !isNaN(numValue);

    switch (type) {
      case '9999':
        info = `${paramName}：该值为默认异常值（9999），表示数据缺失或无效`;
        break;
      case 'outlier':
        info = `${paramName}：该值被统计方法检测为异常值`;
        break;
      case 'out_of_range':
        if (hasDetailRange && isNumber) {
          if (numValue < detailMin) {
            info = `${paramName}安全范围：${detailMin}~${detailMax}，当前值${numValue}低于安全下限${detailMin}`;
          } else if (numValue > detailMax) {
            info = `${paramName}安全范围：${detailMin}~${detailMax}，当前值${numValue}高于安全上限${detailMax}`;
          } else {
            info = `${paramName}：该值超出工艺参数安全范围`;
          }
          if (sourceText) {
            info += `（阈值来源：${sourceText}）`;
          }
        } else if (range && isNumber) {
          if (numValue < range.min) {
            info = `${paramName}安全范围：${range.min}~${range.max}，当前值${numValue}低于安全下限${range.min}`;
          } else if (numValue > range.max) {
            info = `${paramName}安全范围：${range.min}~${range.max}，当前值${numValue}高于安全上限${range.max}`;
          } else {
            info = `${paramName}：该值超出工艺参数安全范围`;
          }
          if (sourceText) {
            info += `（阈值来源：${sourceText}）`;
          }
        } else {
          info = `${paramName}：该值超出工艺参数安全范围`;
          if (sourceText) {
            info += `（阈值来源：${sourceText}）`;
          }
        }
        break;
      default:
        if (range && isNumber) {
          if (numValue < range.min) {
            info = `${paramName}安全范围：${range.min}~${range.max}，当前值${numValue}低于安全下限${range.min}`;
          } else if (numValue > range.max) {
            info = `${paramName}安全范围：${range.min}~${range.max}，当前值${numValue}高于安全上限${range.max}`;
          } else {
            info = `${paramName}：该值被标记为异常`;
          }
        } else {
          info = `${paramName}：该值被标记为异常`;
        }
    }

    anomalyInfoCache.value.set(cacheKey, info);
    return info;
  };

  // 方法
  const startPreprocessing = async () => {
    // 检查是否有数据
    if (previewAllData.value.length === 0) {
      message.error('请先上传数据文件');
      return;
    }

    isProcessing.value = true;
    processingProgress.value = 0;
    processingMessage.value = '准备开始预处理';

    try {
      // 校验ID列
      if (!idColumn.value) {
        message.error('请先选择主键列');
        return;
      }

      // 构建请求参数 - 转换字段名为原始格式
      const transformedData = previewAllData.value.map((row: any) => {
        const newRow: any = {};
        Object.keys(row).forEach((key) => {
          if (key === 'key') return;
          newRow[toRawKey(key)] = row[key];
        });
        return newRow;
      });

      const fileId = uploadedFileInfo.value?.fileId;
      const requestParams = {
        runId: (() => {
          const queryRunId = String(route.query.runId || '').trim();
          const id = queryRunId || ensureQuickStartRunId(24);
          if (queryRunId) setQuickStartRun(queryRunId);
          return id;
        })(),
        fileId: fileId || undefined,
        importPreviewId: importPreviewId.value || undefined,
        data: fileId ? [] : transformedData,
        missingValueStrategy: missingValueStrategy.value,
        outlierDetectionMethods: outlierDetectionMethods.value,
        outlierHandlingStrategy: outlierHandlingStrategy.value,
        processParameterRangeStrategy: processParameterRangeStrategy.value,
        normalizationMethod: normalizationMethod.value,
        featureSelectionMethods: featureSelectionMethods.value,
        idColumn: idColumn.value ? toRawKey(idColumn.value) : null,
        missingSentinelValues: ['9999', '9999.0', '9999.00'],
      };

      console.log('预处理请求参数:', requestParams);

      processingProgress.value = 25;
      processingMessage.value = '阶段 1/3：提交处理任务';

      const res: any = await dataManagementApi.preprocessData(requestParams);
      processingProgress.value = 75;
      processingMessage.value = '阶段 2/3：解析处理结果';
      processingProgress.value = 100;
      processingMessage.value = '阶段 3/3：渲染预览完成';

      if (res.code === 200) {
        message.success('数据预处理完成');

        // 获取处理后的数据
        const processedDataList = res.data.processedData || [];
        const stats = res.data.stats || {};

        console.log('处理后的数据:', processedDataList);
        console.log('统计信息:', stats);
        console.log('第一个数据项的结构:', processedDataList[0]);
        console.log(
          '数据字段类型:',
          Object.keys(processedDataList[0] || {}).map((key) => ({
            key,
            type: typeof (processedDataList[0] || {})[key],
            value: (processedDataList[0] || {})[key],
          }))
        );

        // 更新统计卡片数据
        originalDataCount.value = stats.originalCount || 0;
        processedDataCount.value = stats.processedCount || 0;
        missingValueCount.value = stats.missingCount || 0;
        outOfRangeCount.value = stats.outOfRangeCount || 0;
        outlierCount.value = stats.outlierCount || 0;

        // 更新详细统计信息
        const anomalyDomain = String(stats.anomalyDomain || 'raw');
        detailedStats.value.fixedRangeOutliers = outOfRangeCount.value;
        detailedStats.value.statisticalOutliers = Math.max(
          0,
          outlierCount.value - outOfRangeCount.value
        );
        detailedStats.value.explanation =
          anomalyDomain === 'normalized'
            ? '当前为标准化量纲检测：不再使用原始工艺范围阈值，仅保留统计异常判定'
            : '当前为原始量纲检测：统计异常与工艺范围异常同时生效';

        if (processedDataList.length > 0) {
          const firstRow = processedDataList[0];
          const internalKeys = new Set([
            'key',
            'is_outlier',
            'outlier_columns',
            'is_out_of_range',
            'out_of_range_columns',
            'out_of_range_meta',
          ]);
          const isDisplayKey = (key: string) => !internalKeys.has(key) && !key.startsWith('__');
          const firstRowKeys = Object.keys(firstRow).filter(isDisplayKey);
          const previewKeyOrder = previewColumns.value.map((col) => toRawKey(col.key)).filter(isDisplayKey);
          const orderedKeys = previewKeyOrder.filter((key) => firstRowKeys.includes(key));
          firstRowKeys.forEach((key) => {
            if (!orderedKeys.includes(key)) {
              orderedKeys.push(key);
            }
          });
          const columns = orderedKeys.map((key) => {
            const previewCol = previewColumns.value.find((col) => toRawKey(col.key) === key);
            const title = previewCol?.title || getColumnTitle(key);
            let width = 120;
            if (key.includes('time') || key.includes('timestamp') || key.includes('时间')) {
              width = 180;
            } else if (key.includes('status') || key.includes('状态')) {
              width = 100;
            } else if (key.includes('production') || key.includes('productivity') || key.includes('生产')) {
              width = 90;
            }
            return { title, key, width, ellipsis: { tooltip: true } };
          });

          console.log('生成的列定义:', columns);

          // 为异常值添加标记并生成异常信息
          const markedData = processedDataList.slice(0, PREVIEW_LIMIT).map((row) => {
            const newRow = { ...row };
            const rowId = row.id || row.key || Math.random().toString(36).slice(2, 11);
            newRow.__rowKey = rowId;
            const hasOutlier = !!row.is_outlier;
            const hasOutOfRange = !!row.is_out_of_range;
            let hasSentinel = false;

            // 1. 检查并标记9999异常值
            Object.keys(row).forEach((key) => {
              const value = row[key];
              if (value === '9999.00' || value === 9999) {
                hasSentinel = true;
                // 生成异常信息
                generateAnomalyInfo(rowId, key, value, '9999');
                // 在异常值后面添加标记
                newRow[key] = value + ' ⚠️';
              }
            });

            // 2. 检查后端标记的异常值（保持与后端统计一致）
            if (row.is_outlier) {
              const outlierColumns = row.outlier_columns ? row.outlier_columns.split(',') : [];
              outlierColumns.forEach((col) => {
                if (row[col] != null && row[col] !== '') {
                  // 生成异常信息
                  generateAnomalyInfo(rowId, col, row[col], 'outlier');
                  // 在异常值后面添加标记
                  newRow[col] = row[col] + ' ⚠️';
                }
              });
            }

            // 3. 检查后端标记的超出范围值
            if (row.is_out_of_range) {
              const outOfRangeColumns = row.out_of_range_columns
                ? row.out_of_range_columns.split(',')
                : [];
              outOfRangeColumns.forEach((col) => {
                if (row[col] != null && row[col] !== '') {
                  const rangeMeta = row.out_of_range_meta?.[col];
                  // 生成异常信息
                  generateAnomalyInfo(rowId, col, row[col], 'out_of_range', rangeMeta);
                  // 在异常值后面添加标记
                  newRow[col] = row[col] + ' ⚠️';
                }
              });
            }

            newRow.__hasOutlier = hasOutlier;
            newRow.__hasOutOfRange = hasOutOfRange;
            newRow.__anomalyTag = hasOutOfRange
              ? '范围异常'
              : hasOutlier
              ? '统计异常'
              : hasSentinel
              ? '哨兵值'
              : '-';

            return newRow;
          });

          // 更新列定义，添加render函数用于显示带悬停提示的异常标记
          const columnsWithRender = columns.map((col) => {
            return {
              ...col,
              render: (row: any, index: number) => {
                const value = row[col.key];
                const rowId = row.__rowKey || row.id || row.key || index;
                const cacheKey = `${rowId}-${col.key}`;

                // 直接检查原始数据，而不仅仅是显示值
                let hasAnomaly = false;
                let anomalyInfo = '';

                // 检查1：9999异常值
                if (value === '9999.00' || value === 9999 || String(value).includes('9999')) {
                  hasAnomaly = true;
                  anomalyInfo = generateAnomalyInfo(rowId, col.key, value, '9999');
                }

                // 检查2：后端标记的异常值
                if (row.is_outlier) {
                  const outlierColumns = row.outlier_columns ? row.outlier_columns.split(',') : [];
                  if (outlierColumns.includes(col.key)) {
                    hasAnomaly = true;
                    anomalyInfo = generateAnomalyInfo(rowId, col.key, row[col.key], 'outlier');
                  }
                }

                // 检查3：后端标记的超出范围值
                if (row.is_out_of_range) {
                  const outOfRangeColumns = row.out_of_range_columns
                    ? row.out_of_range_columns.split(',')
                    : [];
                  if (outOfRangeColumns.includes(col.key)) {
                    hasAnomaly = true;
                    anomalyInfo = generateAnomalyInfo(
                      rowId,
                      col.key,
                      row[col.key],
                      'out_of_range',
                      row.out_of_range_meta?.[col.key]
                    );
                  }
                }

                // 检查4：显示值中包含警告标记
                if (!hasAnomaly && value && typeof value === 'string' && value.includes('⚠️')) {
                  hasAnomaly = true;
                  // 尝试从缓存获取异常信息
                  anomalyInfo =
                    anomalyInfoCache.value.get(cacheKey) ||
                    generateAnomalyInfo(rowId, col.key, value.replace(' ⚠️', ''), 'out_of_range');
                }

                // 显示带悬停提示的异常标记
                if (hasAnomaly) {
                  return h(
                    'div',
                    {
                      class: 'cursor-help',
                      title: anomalyInfo,
                    },
                    typeof value === 'string' ? value : value + ' ⚠️'
                  );
                }

                return value;
              },
            };
          });

          columnsWithRender.push({
            title: '异常标记',
            key: '__anomalyTag',
            width: 120,
            ellipsis: { tooltip: true },
            render: (row: any) => row.__anomalyTag || '-',
          });

          // 更新处理后的数据和列
          processedColumns.value = columnsWithRender;
          processedData.value = markedData;
        } else {
          processedData.value = [];
          processedColumns.value = [];
          message.warning('预处理成功，但未返回数据');
        }

        // 自动跳转到"result"标签页
        activeTab.value = 'result';
        message.success('预处理完成，已自动跳转到结果页面');
      } else {
        message.error('预处理失败: ' + (res.message || '未知错误'));
      }
    } catch (error: any) {
      console.error('预处理请求失败:', error);
      message.error('预处理请求失败: ' + (error.message || '网络错误'));
    } finally {
      isProcessing.value = false;
    }
  };

  const resetSettings = () => {
    missingValueStrategy.value = 'mean';
    outlierDetectionMethods.value = ['iqr', 'zscore'];
    outlierHandlingStrategy.value = 'mark';
    processParameterRangeStrategy.value = 'mark';
    normalizationMethod.value = 'minmax';
    featureSelectionMethods.value = ['correlation'];
    anomalyFilter.value = 'all';
    message.success('重置设置成功');
  };

  // 将CSV内容保存为文件
  const saveCsvFile = (content: string, fileName: string) => {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', fileName);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const saveTextFile = (content: string, fileName: string, mimeType = 'application/json;charset=utf-8;') => {
    const blob = new Blob([content], { type: mimeType });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', fileName);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const buildPreprocessingConfig = () => {
    return {
      missingValueStrategy: missingValueStrategy.value,
      outlierDetectionMethods: outlierDetectionMethods.value,
      outlierHandlingStrategy: outlierHandlingStrategy.value,
      processParameterRangeStrategy: processParameterRangeStrategy.value,
      normalizationMethod: normalizationMethod.value,
      featureSelectionMethods: featureSelectionMethods.value,
      idColumn: idColumn.value ? toRawKey(idColumn.value) : null,
      anomalyFilter: anomalyFilter.value,
      missingSentinelValues: ['9999', '9999.0', '9999.00'],
    };
  };

  const exportResult = async () => {
    if (!uploadedFileInfo.value) {
      message.error('请先上传文件');
      return;
    }

    if (processedData.value.length === 0) {
      message.error('没有处理结果数据');
      return;
    }

    try {
      message.loading('正在生成处理结果...');

      // 构建请求参数
      const requestParams = {
        fileInfo: uploadedFileInfo.value,
        processedData: processedData.value,
      };

      console.log('更新文件请求参数:', requestParams);

      // 调用API更新文件
      const res: any = await dataManagementApi.updateFile(requestParams);

      if (res.code === 200) {
        // 获取CSV内容和文件名
        const csvContent = res.data.csvContent;
        const fileName = res.data.fileName;

        if (csvContent && fileName) {
          // 保存CSV文件
          saveCsvFile(csvContent, fileName);
          message.success('处理结果已保存为文件');
        } else {
          message.error('获取文件内容失败');
        }
      } else {
        message.error('生成处理结果失败: ' + (res.message || '未知错误'));
      }
    } catch (error: any) {
      console.error('生成处理结果失败:', error);
      message.error('生成处理结果失败: ' + (error.message || '未知错误'));
    } finally {
      message.destroyAll();
    }
  };

  const exportSnapshot = async () => {
    if (!uploadedFileInfo.value) {
      message.error('请先上传文件');
      return;
    }
    if (processedData.value.length === 0) {
      message.error('没有可导出的处理结果');
      return;
    }
    try {
      message.loading('正在生成快照包...');
      const requestParams = {
        fileInfo: uploadedFileInfo.value,
        processedData: processedData.value,
        preprocessingConfig: buildPreprocessingConfig(),
        contractVersion: 'v1',
      };
      const res: any = await dataManagementApi.exportSnapshot(requestParams);
      if (res.code === 200 && res.data?.snapshotContent && res.data?.snapshotFileName) {
        saveTextFile(res.data.snapshotContent, res.data.snapshotFileName);
        message.success('快照包导出成功');
      } else {
        message.error('快照包导出失败');
      }
    } catch (error: any) {
      console.error('快照包导出失败:', error);
      message.error('快照包导出失败: ' + (error.message || '未知错误'));
    } finally {
      message.destroyAll();
    }
  };
</script>

<style scoped>
  .preprocess-main-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .preprocess-panel {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .preprocess-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .data-preprocessing :deep(.n-base-selection),
  .data-preprocessing :deep(.n-input),
  .data-preprocessing :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>
