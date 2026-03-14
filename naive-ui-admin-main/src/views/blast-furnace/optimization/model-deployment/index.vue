<template>
  <div
    class="model-deployment min-h-[calc(100vh-80px)] w-full flex-1  p-4 md:p-6 overflow-auto box-border"
  >
    <n-card title="模型部署" class="mb-4 deploy-main-card">
      <!-- 部署配置 -->
      <n-form label-placement="left" label-width="120" class="mb-4 deploy-form">
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="模型选择">
            <n-select
              v-model:value="selectedModel"
              :options="modelOptions"
              placeholder="选择主模型 (产量)"
            />
          </n-form-item>
          <n-form-item label="辅助模型">
            <n-select
              v-model:value="selectedSecondaryModel"
              :options="modelOptions"
              placeholder="选择辅助模型 (能耗) [可选]"
              clearable
            />
          </n-form-item>
          <n-form-item label="部署环境">
            <n-select
              v-model:value="deploymentEnvironment"
              :options="environmentOptions"
              placeholder="选择部署环境"
            />
          </n-form-item>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="部署名称">
            <n-input v-model:value="deploymentName" placeholder="输入部署名称" />
          </n-form-item>
          <n-form-item label="版本号">
            <n-input v-model:value="version" placeholder="输入版本号" />
          </n-form-item>
        </div>
        <n-divider class="my-4">运行策略配置</n-divider>
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="报警阈值">
            <div class="flex items-center gap-2 w-full">
              <n-input-number
                v-model:value="runtimeConfig.alarmLowerLimit"
                placeholder="下限"
                class="flex-1"
              />
              <n-input-number
                v-model:value="runtimeConfig.alarmUpperLimit"
                placeholder="上限"
                class="flex-1"
              />
            </div>
          </n-form-item>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="安全熔断策略">
            <div class="flex items-center gap-2">
              <n-switch v-model:value="runtimeConfig.safetyHold" />
              <n-text depth="3">异常时回退到熔断回退值</n-text>
            </div>
          </n-form-item>
        </div>
        <n-form-item label="备注">
          <n-input v-model:value="runtimeConfig.remark" placeholder="填写备注" />
        </n-form-item>
        <div class="flex justify-end">
          <n-button round type="primary" @click="deployModel">部署模型</n-button>
          <n-button round @click="resetDeployment" class="ml-2">重置</n-button>
        </div>
      </n-form>

      <!-- 部署状态 -->
      <n-tabs v-model:value="activeTab" type="line">
        <n-tab-pane name="status" tab="部署状态">
          <div class="deploy-panel">
            <n-card title="当前部署状态" size="small" class="mb-4 deploy-sub-card">
              <div class="flex items-center justify-between mb-2">
                <n-text depth="2">部署状态: {{ deploymentStatus }}</n-text>
                <n-tag
                  :type="
                    deploymentStatus === 'running'
                      ? 'info'
                      : deploymentStatus === 'completed'
                      ? 'success'
                      : deploymentStatus === 'failed'
                      ? 'error'
                      : deploymentStatus === 'canceled'
                      ? 'warning'
                      : 'default'
                  "
                >
                  {{
                    deploymentStatus === 'running'
                      ? '部署中'
                      : deploymentStatus === 'completed'
                      ? '已完成'
                      : deploymentStatus === 'failed'
                      ? '部署失败'
                      : deploymentStatus === 'canceled'
                      ? '已取消'
                      : '未部署'
                  }}
                </n-tag>
              </div>
              <n-progress
                type="line"
                :percentage="deploymentProgress"
                :show-text="true"
                class="mb-4"
              />
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <n-text depth="3">部署模型: {{ deployedModelName }}</n-text>
                  <n-text depth="3" class="mt-1">部署环境: {{ deployedEnvironment }}</n-text>
                  <n-text depth="3" class="mt-1">部署时间: {{ deploymentTime }}</n-text>
                </div>
                <div>
                  <n-text depth="3">服务地址: {{ serviceUrl }}</n-text>
                  <n-text depth="3" class="mt-1">服务状态: {{ serviceStatus }}</n-text>
                  <n-text depth="3" class="mt-1">API版本: {{ apiVersion }}</n-text>
                </div>
              </div>
            </n-card>

            <n-card title="部署日志" size="small" class="deploy-sub-card">
              <n-scrollbar :style="{ height: '300px' }">
                <div class="p-2">
                  <div v-for="(log, index) in deploymentLogs" :key="index" class="mb-1">
                    <n-text>{{ log }}</n-text>
                  </div>
                </div>
              </n-scrollbar>
            </n-card>
          </div>
        </n-tab-pane>
        <n-tab-pane name="history" tab="部署历史">
          <div class="deploy-panel">
            <div class="deploy-inner-panel">
              <div class="flex flex-wrap items-center gap-3 mb-3">
                <n-input
                  v-model:value="historyKeyword"
                  placeholder="搜索部署名称或模型名称"
                  class="w-64"
                  size="small"
                  round
                  clearable
                >
                  <template #prefix>
                    <n-icon :component="SearchOutlined" class="text-slate-500" />
                  </template>
                </n-input>
                <n-select
                  v-model:value="historyEnvFilter"
                  :options="[
                    { label: '全部环境', value: '' },
                    { label: '生产环境', value: 'production' },
                    { label: '测试环境', value: 'testing' },
                    { label: '开发环境', value: 'development' },
                  ]"
                  placeholder="过滤环境"
                  class="w-40"
                  size="small"
                  clearable
                />
                <n-select
                  v-model:value="historyStatusFilter"
                  :options="[
                    { label: '全部状态', value: '' },
                    { label: '部署中', value: 'running' },
                    { label: '已完成', value: 'completed' },
                    { label: '失败', value: 'failed' },
                    { label: '已取消', value: 'canceled' },
                  ]"
                  placeholder="过滤状态"
                  class="w-40"
                  size="small"
                  clearable
                />
                <n-select
                  v-model:value="historyArchiveFilter"
                  :options="[
                    { label: '全部记录', value: '' },
                    { label: '仅未归档', value: 'active' },
                    { label: '仅已归档', value: 'archived' },
                  ]"
                  placeholder="归档状态"
                  class="w-40"
                  size="small"
                  clearable
                />
                <n-button round type="primary" ghost size="small" @click="loadDeploymentHistory">
                  刷新
                </n-button>
                <div class="flex items-center gap-2">
                  <n-switch v-model:value="autoRefreshHistory" size="small" />
                  <n-text depth="3">自动刷新</n-text>
                </div>
                <div v-if="selectedHistoryRowKeys.length" class="flex items-center gap-2 ml-auto">
                  <n-dropdown :options="historyBatchOptions" @select="onHistoryBatchSelect">
                    <n-button round type="warning" ghost size="small">批量操作</n-button>
                  </n-dropdown>
                </div>
              </div>
              <div class="mt-4 overflow-hidden rounded-md border" style="border-color: var(--n-border-color);">
                <n-data-table
                  :columns="deploymentHistoryColumns"
                  :data="historyTableData"
                  :row-key="(row:any) => row.id"
                  v-model:checked-row-keys="selectedHistoryRowKeys"
                  size="small"
                  striped
                  :scroll-x="1200"
                />
              </div>
              <div class="mt-3 flex justify-end border-t pt-3" style="border-color: var(--n-border-color);">
                <n-pagination
                  :page="historyPagination.page"
                  :page-size="historyPagination.pageSize"
                  :page-count="historyPageCount"
                  :page-sizes="[10, 20, 30, 50]"
                  show-size-picker
                  @update:page="(p:number)=>historyPagination.page=p"
                  @update:page-size="(s:number)=>{historyPagination.pageSize=s; historyPagination.page=1}"
                />
              </div>
            </div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="service" tab="服务管理">
          <div class="deploy-panel">
            <div class="flex flex-col xl:flex-row gap-4 min-h-[520px]">
              <n-card title="服务列表" size="small" class="w-full xl:w-2/5 xl:h-full deploy-sub-card">
                <div class="min-h-[320px] xl:h-full">
                  <n-data-table
                    :columns="serviceColumns"
                    :data="serviceList"
                    :row-key="(row:any) => row.id"
                    :row-class-name="getServiceRowClass"
                    :row-props="getServiceRowProps"
                    size="small"
                    :max-height="420"
                    class="min-h-[260px]"
                  />
                </div>
              </n-card>

              <n-card title="服务控制台" size="small" class="w-full xl:w-3/5 xl:h-full deploy-sub-card">
                <div class="flex flex-col gap-4 h-full">
                  <div class="deploy-inner-panel">
                    <div class="flex items-center justify-between">
                      <div class="text-base font-medium" style="color: var(--n-text-color-3);">服务状态</div>
                      <n-tag
                        :type="activeService?.status === 'running' ? 'success' : 'error'"
                        size="small"
                      >
                        {{ activeService?.status === 'running' ? '运行中' : '已停止' }}
                      </n-tag>
                    </div>
                    <div class="mt-3 grid grid-cols-2 gap-3 text-sm" style="color: var(--n-text-color-3);">
                      <div>
                        服务名称：{{ activeService?.name || '-' }}
                        <n-button
                          size="tiny"
                          quaternary
                          circle
                          class="ml-1 align-text-bottom"
                          :disabled="!activeService?.id"
                          @click="openEditNameModal"
                        >
                          <template #icon>
                            <n-icon><EditOutlined /></n-icon>
                          </template>
                        </n-button>
                      </div>
                      <div>模型名称：{{ activeService?.modelName || '-' }}</div>
                      <div>部署环境：{{ activeService?.environment || '-' }}</div>
                      <div>版本号：{{ activeService?.version || '-' }}</div>
                      <div>启动时间：{{ activeServiceRuntime?.startTime || '-' }}</div>
                      <div>最近预测：{{ activeServiceRuntime?.lastPredictionTime || '-' }}</div>
                      <div>平均延迟：{{ activeServiceRuntime?.avgLatencyText || '-' }}</div>
                      <div>预测QPS：{{ activeServiceRuntime?.qpsText || '-' }}</div>
                      <div class="col-span-2">服务地址：{{ activeService?.url || '-' }}</div>
                    </div>
                  </div>

                  <div class="deploy-inner-panel">
                    <div class="mb-3 text-sm font-medium" style="color: var(--n-text-color-3);">快捷操作</div>
                    <div class="flex flex-wrap gap-2">
                      <n-button
                        v-if="activeService?.status !== 'running'"
                        type="primary"
                        :disabled="!activeService?.id"
                        @click="startService"
                      >
                        启动
                      </n-button>
                      <template v-else>
                        <n-button
                          type="warning"
                          :disabled="!activeService?.id"
                          @click="restartService"
                        >
                          重启
                        </n-button>
                        <n-button type="error" :disabled="!activeService?.id" @click="stopService">
                          停止
                        </n-button>
                      </template>
                      <n-button :disabled="!activeService?.id" @click="viewServiceLogs">
                        查看实时日志
                      </n-button>
                    </div>
                  </div>

                  <div class="deploy-inner-panel">
                    <div class="flex items-center justify-between">
                      <div class="text-sm font-medium" style="color: var(--n-text-color-3);">在线预测</div>
                      <n-button type="primary" secondary @click="activeTab = 'onlinePredict'">
                        前往在线预测
                      </n-button>
                    </div>
                    <n-text depth="3" class="mt-2 block text-xs">
                      目标变量：{{ targetVariableLabel }}（仅输出结果，不作为输入特征）
                    </n-text>
                  </div>
                </div>
              </n-card>
            </div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="onlinePredict" tab="在线预测">
          <div class="deploy-panel">
            <n-card title="在线预测控制台" size="small" class="deploy-sub-card">
              <div class="deploy-inner-panel">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm" style="color: var(--n-text-color-3);">
                  <div>服务名称：{{ activeService?.name || '-' }}</div>
                  <div>状态：{{ activeService?.status === 'running' ? '运行中' : '已停止' }}</div>
                  <div class="md:col-span-2">目标变量：{{ targetVariableLabel }}</div>
                </div>
              </div>
              <div class="mt-4 deploy-inner-panel">
                <n-form label-placement="left" label-width="120">
                  <div
                    v-if="activeSafetyHoldEnabled"
                    class="mb-2 rounded border bg-[#0f1422] px-3 py-2"
                    style="border-color: var(--n-border-color);"
                  >
                    <div class="text-xs" style="color: var(--n-text-color-3);">
                      熔断回退值（{{ targetVariableLabel }}）
                    </div>
                    <n-input-number
                      v-model:value="targetActualValue"
                      placeholder="必填：预测异常时将回退到该值"
                      class="mt-2 w-full"
                    />
                  </div>
                  <div v-if="expectedFeatures.length" class="grid grid-cols-1 xl:grid-cols-2 gap-4">
                    <n-form-item
                      v-for="feature in expectedFeatures"
                      :key="feature"
                      :label="getFeatureLabel(feature)"
                    >
                      <div class="w-full">
                        <n-input-number
                          v-model:value="predictionInput[feature]"
                          placeholder="输入数值"
                          class="w-full"
                        />
                        <n-text depth="3" class="mt-1 block text-xs">
                          {{ getFeatureHint(feature) }}
                        </n-text>
                      </div>
                    </n-form-item>
                  </div>
                  <div v-else class="py-2">
                    <n-text depth="3">暂无可用特征，请先选择已部署模型</n-text>
                  </div>
                  <div class="mt-2 flex flex-wrap justify-end gap-2">
                    <n-button round @click="resetPredictionInput">重置输入</n-button>
                    <n-button round type="primary" @click="testService">测试服务</n-button>
                    <n-button round :loading="explainLoading" @click="explainService">
                      解释性分析
                    </n-button>
                  </div>
                </n-form>
                <n-alert
                  v-if="
                    predictionAlert &&
                    predictionAlertMessage &&
                    predictionAlertMessage.includes('[已触发安全熔断')
                  "
                  type="error"
                  class="mt-3"
                >
                  {{ predictionAlertMessage }} 当前给出的所有建议参数都已被拒绝，必须维持现状。
                </n-alert>
                <n-alert v-if="predictionResult" :type="predictionResultStatus" class="mt-3">
                  {{ predictionResult }}
                </n-alert>
                <n-alert
                  v-if="predictionSafetyHoldTriggered && predictionFallbackSource"
                  type="warning"
                  class="mt-3"
                >
                  已触发安全熔断，本次输出已回退到“{{ getFeatureLabel(predictionFallbackSource) }}”熔断回退值。
                </n-alert>
              </div>
            </n-card>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>
    <n-drawer v-model:show="showHistoryDetail" width="520" placement="right">
      <n-drawer-content :title="historyDetailTitle">
        <n-descriptions bordered column="1" v-if="currentHistoryRow">
          <n-descriptions-item label="部署ID">
            <n-tag type="info" size="small" bordered :style="{ fontFamily: 'monospace' }">
              {{ formatDeploymentId(currentHistoryRow.id) }}
            </n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="模型名称">{{
            currentHistoryRow.modelName
          }}</n-descriptions-item>
          <n-descriptions-item label="部署名称">{{ currentHistoryRow.name }}</n-descriptions-item>
          <n-descriptions-item label="部署环境">{{
            currentHistoryRow.environment
          }}</n-descriptions-item>
          <n-descriptions-item label="运行策略配置">
            <div v-if="detailConfig" class="flex flex-col gap-2">
              <n-descriptions size="small" bordered column="2">
                <n-descriptions-item label="报警下限">
                  {{ detailConfig.alarmLowerLimit ?? '-' }}
                </n-descriptions-item>
                <n-descriptions-item label="报警上限">
                  {{ detailConfig.alarmUpperLimit ?? '-' }}
                </n-descriptions-item>
                <n-descriptions-item label="安全熔断">
                  {{
                    detailConfig.safetyHold === undefined
                      ? '-'
                      : detailConfig.safetyHold
                      ? '开启'
                      : '关闭'
                  }}
                </n-descriptions-item>
                <n-descriptions-item label="备注">
                  {{ detailConfig.remark || '-' }}
                </n-descriptions-item>
              </n-descriptions>
            </div>
            <n-text depth="3" v-else>无配置信息</n-text>
          </n-descriptions-item>
          <n-descriptions-item label="版本号">{{ currentHistoryRow.version }}</n-descriptions-item>
          <n-descriptions-item label="部署时间">{{
            currentHistoryRow.deployTime
          }}</n-descriptions-item>
          <n-descriptions-item label="状态">{{ currentHistoryRow.status }}</n-descriptions-item>
          <n-descriptions-item label="服务地址">{{
            currentHistoryRow.serviceUrl
          }}</n-descriptions-item>
        </n-descriptions>
        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button @click="handleDetailCopy">复制地址</n-button>
            <n-button @click="handleDetailLogs">查看日志</n-button>
            <n-button type="error" @click="handleDetailDelete"> 删除 </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
    <n-drawer v-model:show="showHistoryLogs" width="520" placement="right">
      <n-drawer-content :title="historyLogsTitle">
        <n-scrollbar :style="{ height: '60vh' }">
          <div class="p-2">
            <div v-if="!historyLogs.length" class="text-center text-slate-500">暂无日志</div>
            <div v-else class="space-y-1">
              <div v-for="(log, i) in historyLogs" :key="i" class="text-sm">
                <n-text>{{ log }}</n-text>
              </div>
            </div>
          </div>
        </n-scrollbar>
      </n-drawer-content>
    </n-drawer>
    <n-modal
      v-model:show="showExplain"
      preset="card"
      title="解释性分析"
      class="w-[840px]"
    >
      <div v-if="explainLoading" class="py-6 text-center text-sm text-slate-500"
        >正在生成分析...</div
      >
      <div v-else>
        <n-alert v-if="!explainResult" type="info">暂无可用结果</n-alert>
        <div v-else class="space-y-4">
          <n-descriptions bordered size="small" column="2">
            <n-descriptions-item label="模型类型">
              {{ explainResult.modelType || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="原始输出">
              {{ formatNumber(explainResult.rawPrediction) }}
            </n-descriptions-item>
            <n-descriptions-item label="结算结果">
              {{ formatNumber(explainResult.prediction) }}
            </n-descriptions-item>
            <n-descriptions-item label="报警状态">
              {{ explainResult.alert ? explainResult.alertMessage || '触发报警' : '正常' }}
            </n-descriptions-item>
          </n-descriptions>
          <div>
            <div class="mb-2 text-sm font-medium" style="color: var(--n-text-color-3);">特征重要性</div>
            <n-data-table
              :columns="explainImportanceColumns"
              :data="explainFeatureRows"
              :pagination="false"
              size="small"
            />
          </div>
          <div>
            <div class="mb-2 text-sm font-medium" style="color: var(--n-text-color-3);">单变量敏感度</div>
            <n-data-table
              :columns="explainSensitivityColumns"
              :data="explainSensitivityRows"
              :pagination="false"
              size="small"
            />
          </div>
        </div>
      </div>
    </n-modal>
    <n-modal
      v-model:show="showEditNameModal"
      preset="card"
      title="修改服务名称"
      class="w-[400px]"
    >
      <n-form label-placement="left" label-width="80">
        <n-form-item label="新名称">
          <n-input v-model:value="editingServiceName" placeholder="请输入新的服务名称" />
        </n-form-item>
        <div class="flex justify-end gap-2">
          <n-button @click="showEditNameModal = false">取消</n-button>
          <n-button type="primary" :loading="editNameSubmitting" @click="submitEditName">
            确认修改
          </n-button>
        </div>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, h, onMounted, onUnmounted, computed, watch, reactive } from 'vue';
  import { useMessage, useDialog, NIcon, NTag, NButton, NDropdown, type DropdownOption } from 'naive-ui';
  import {
    EyeOutlined,
    MoreOutlined,
    SearchOutlined,
    CopyOutlined,
    CheckCircleOutlined,
    SyncOutlined,
    CloseCircleOutlined,
    ClockCircleOutlined,
    EnvironmentOutlined,
    EditOutlined,
  } from '@vicons/antd';
  import { optimizationApi } from '@/api/blast-furnace';
  import { formatNumericDisplay } from '@/utils/format';

  const message = useMessage();
  const dialog = useDialog();
  const activeTab = ref('status');

  // 部署配置
  const selectedModel = ref<number | null>(null);
  const selectedSecondaryModel = ref<number | null>(null);
  const modelOptions = ref<{ label: string; value: number }[]>([]);
  const trainingHistoryList = ref<any[]>([]);

  const deploymentEnvironment = ref('production');
  const environmentOptions = [
    { label: '生产环境', value: 'production' },
    { label: '测试环境', value: 'testing' },
    { label: '开发环境', value: 'development' },
  ];

  const deploymentName = ref('高炉优化模型部署');
  const version = ref('1.0.0');
  const defaultRuntimeConfig = {
    alarmLowerLimit: 0,
    alarmUpperLimit: 100,
    safetyHold: true,
    remark: '',
  };

  const runtimeConfig = reactive({ ...defaultRuntimeConfig });

  // 部署状态
  const deploymentStatus = ref('idle'); // idle, running, completed, failed
  const deploymentProgress = ref(0);
  const deployedModelName = ref('');
  const deployedEnvironment = ref('');
  const deploymentTime = ref('');
  const serviceUrl = ref('');
  const serviceStatus = ref('');
  const apiVersion = ref('');

  // 部署日志
  const deploymentLogs = ref<string[]>([]);
  const pollingTimer = ref<number | null>(null);

  const showEditNameModal = ref(false);
  const editingServiceName = ref('');
  const editNameSubmitting = ref(false);

  const openEditNameModal = () => {
    if (!activeService.value) return;
    editingServiceName.value = activeService.value.name || '';
    showEditNameModal.value = true;
  };

  const submitEditName = async () => {
    if (!activeService.value?.id) return;
    if (!editingServiceName.value.trim()) {
      message.warning('请输入服务名称');
      return;
    }
    editNameSubmitting.value = true;
    try {
      const res: any = await optimizationApi.deployment.updateServiceName(
        activeService.value.id,
        editingServiceName.value.trim()
      );
      if (res.code === 200) {
        message.success('修改成功');
        showEditNameModal.value = false;
        loadServices(); // 刷新列表
        // 如果当前也在看这个服务的历史详情，也刷新一下
        if (currentHistoryRow.value && currentHistoryRow.value.id === activeService.value.deploymentId) {
           // 简单刷新一下历史列表
           loadDeploymentHistory();
        }
      } else {
        message.error(res.msg || '修改失败');
      }
    } catch (e: any) {
      message.error(e.message || '修改失败');
    } finally {
      editNameSubmitting.value = false;
    }
  };

  const loadArchivedHistoryIds = () => {
    try {
      const raw = localStorage.getItem('deploymentHistoryArchivedIds');
      if (!raw) {
        return [];
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return [];
      }
      return parsed.filter((item) => typeof item === 'number');
    } catch {
      return [];
    }
  };

  const persistArchivedHistoryIds = (ids: number[]) => {
    try {
      localStorage.setItem('deploymentHistoryArchivedIds', JSON.stringify(ids));
    } catch {}
  };

  // 部署历史
  const deploymentHistoryList = ref<any[]>([]);
  const archivedHistoryIds = ref<number[]>(loadArchivedHistoryIds());
  watch(
    archivedHistoryIds,
    (next) => {
      persistArchivedHistoryIds(next);
    },
    { deep: true }
  );

  const formatDeploymentId = (id: number | string) => {
    if (!id) return '-';
    // 找到当前 ID 在原始列表中的索引（按 ID 升序排列）
    const sortedList = [...deploymentHistoryList.value].sort((a, b) => a.id - b.id);
    const index = sortedList.findIndex((item) => item.id === id);
    // 如果找到了，显示为 1 开始的序号，否则回退显示原始 ID
    const displayId = index !== -1 ? index + 1 : id;
    return `DEP-${String(displayId).padStart(6, '0')}`;
  };

  const formatServiceId = (id: number | string) => {
    if (!id) return '-';
    const sortedList = [...serviceList.value].sort((a, b) => a.id - b.id);
    const index = sortedList.findIndex((item) => item.id === id);
    const displayId = index !== -1 ? index + 1 : id;
    return `SRV-${String(displayId).padStart(6, '0')}`;
  };

  const deploymentHistoryColumns = computed(() => [
    { type: 'selection', fixed: 'left' },
    {
      title: '部署ID',
      key: 'id',
      width: 120,
      align: 'center',
      fixed: 'left',
      sorter: (row1: any, row2: any) => row1.id - row2.id,
      defaultSortOrder: 'ascend',
      render: (row: any) => {
        return h(
          NTag,
          {
            type: 'info',
            size: 'small',
            bordered: false,
            style: { fontFamily: 'monospace' },
          },
          { default: () => formatDeploymentId(row.id) }
        );
      },
    },
    {
      title: '模型名称',
      key: 'modelName',
      minWidth: 140,
      resizable: true,
      ellipsis: { tooltip: true },
    },
    {
      title: '部署名称',
      key: 'name',
      minWidth: 140,
      resizable: true,
      ellipsis: { tooltip: true },
    },
    {
      title: '部署环境',
      key: 'environment',
      width: 110,
      resizable: true,
      render: (row: any) => {
        const type =
          row.environment === 'production'
            ? 'success'
            : row.environment === 'testing'
            ? 'warning'
            : 'info';
        const label =
          row.environment === 'production'
            ? '生产环境'
            : row.environment === 'testing'
            ? '测试环境'
            : '开发环境';
        return h(
          NTag,
          {
            type,
            size: 'small',
            bordered: false,
          },
          {
            default: () => [h(NIcon, { component: EnvironmentOutlined, class: 'mr-1' }), label],
          }
        );
      },
    },
    { title: '版本号', key: 'version', width: 70, align: 'center', resizable: true },
    {
      title: '部署时间',
      key: 'deployTime',
      width: 170,
      resizable: true,
      sorter: (row1: any, row2: any) =>
        new Date(row1.deployTime).getTime() - new Date(row2.deployTime).getTime(),
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      resizable: true,
      render: (row: any) => {
        const type: 'default' | 'success' | 'warning' | 'error' | 'info' =
          row.status === 'completed'
            ? 'success'
            : row.status === 'running'
            ? 'info'
            : row.status === 'canceled'
            ? 'warning'
            : 'error';
        const label =
          row.status === 'completed'
            ? '成功'
            : row.status === 'running'
            ? '部署中'
            : row.status === 'canceled'
            ? '已取消'
            : '失败';
        const icon =
          row.status === 'completed'
            ? CheckCircleOutlined
            : row.status === 'running'
            ? SyncOutlined
            : row.status === 'canceled'
            ? ClockCircleOutlined
            : CloseCircleOutlined;
        return h(
          NTag,
          {
            type,
            size: 'small',
            bordered: false,
          },
          {
            default: () => [h(NIcon, { component: icon, class: 'mr-1' }), label],
          }
        );
      },
    },
    {
      title: '服务地址',
      key: 'serviceUrl',
      minWidth: 200,
      resizable: true,
      ellipsis: { tooltip: true },
      render: (row: any) => {
        if (!row.serviceUrl) return '-';
        return h('div', { class: 'flex items-center gap-1 group' }, [
          h('span', { class: 'truncate flex-1' }, row.serviceUrl),
          h(
            NButton,
            {
              size: 'tiny',
              quaternary: true,
              circle: true,
              class: 'opacity-0 group-hover:opacity-100 transition-opacity',
              onClick: (e: MouseEvent) => {
                e.stopPropagation();
                copyServiceUrl(row);
              },
            },
            { icon: () => h(NIcon, { component: CopyOutlined }) }
          ),
        ]);
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      align: 'center',
      render: (row: any) => {
        const options: DropdownOption[] = [];
        if (row.status === 'running') {
          options.push({ label: '取消部署', key: 'cancel' });
        }
        if (row.status === 'failed' || row.status === 'canceled') {
          options.push({ label: '重试部署', key: 'retry' });
        }
        options.push({ label: '复用配置', key: 'clone' });
        if (archivedHistoryIds.value.includes(row.id)) {
          options.push({ label: '取消归档', key: 'unarchive' });
        } else {
          options.push({ label: '归档', key: 'archive' });
        }
        const moreButton = options.length
          ? h(
              NDropdown,
              {
                options,
                onSelect: (key: string | number) => onHistoryMoreSelect(String(key), row),
              },
              {
                default: () =>
                  h(
                    NButton,
                    { size: 'small', type: 'info', class: 'action-btn-more' },
                    {
                      default: () => '更多',
                      icon: () => h(NIcon, null, { default: () => h(MoreOutlined) }),
                    }
                  ),
              }
            )
          : h(
              NButton,
              { size: 'small', disabled: true, type: 'info', class: 'action-btn-more' },
              {
                default: () => '更多',
                icon: () => h(NIcon, null, { default: () => h(MoreOutlined) }),
              }
            );
        return h('div', { class: 'flex items-center gap-2' }, [
          h(
            NButton,
            {
              size: 'small',
              type: 'primary',
              class: 'action-btn-detail',
              onClick: () => viewDeploymentDetail(row),
            },
            {
              default: () => '查看详情',
              icon: () => h(NIcon, { component: EyeOutlined }),
            }
          ),
          moreButton,
        ]);
      },
    },
  ]);

  // 服务列表
  const serviceList = ref<any[]>([]);
  const activeServiceId = ref<number | null>(null);
  const getServiceIdFromUrl = (url: string) => {
    if (!url) {
      return null;
    }
    const match = url.match(/\/predict\/(\d+)$/);
    if (!match) {
      return null;
    }
    const id = Number(match[1]);
    return Number.isNaN(id) ? null : id;
  };
  const activeService = computed(() => {
    if (activeServiceId.value) {
      const match = serviceList.value.find((item) => item.id === activeServiceId.value);
      if (match) {
        return match;
      }
    }
    return serviceList.value.find((item) => item.status === 'running') || serviceList.value[0];
  });
  const activeTraining = computed(() => {
    const trainingId = activeService.value?.trainingId;
    if (!trainingId) {
      return null;
    }
    return trainingHistoryList.value.find((item) => item.id === trainingId) || null;
  });
  const featureAliasMap: Record<string, string> = {
    temperature: 'temperature',
    temp: 'temperature',
    温度: 'temperature',
    pressure: 'pressure',
    压力: 'pressure',
    materialheight: 'materialHeight',
    materialHeight: 'materialHeight',
    料面高度: 'materialHeight',
    gasflow: 'gasFlow',
    gasFlow: 'gasFlow',
    煤气流量: 'gasFlow',
    oxygenlevel: 'oxygenLevel',
    oxygenLevel: 'oxygenLevel',
    氧气含量: 'oxygenLevel',
    windvolume: 'windVolume',
    windVolume: 'windVolume',
    风量: 'windVolume',
    coalinjection: 'coalInjection',
    coalInjection: 'coalInjection',
    喷煤量: 'coalInjection',
    productionrate: 'productionRate',
    productionRate: 'productionRate',
    生产率: 'productionRate',
    energyconsumption: 'energyConsumption',
    energyConsumption: 'energyConsumption',
    能耗: 'energyConsumption',
    hotmetaltemperature: 'hotMetalTemperature',
    hotMetalTemperature: 'hotMetalTemperature',
    铁水温度: 'hotMetalTemperature',
    siliconcontent: 'siliconContent',
    siliconContent: 'siliconContent',
    硅含量: 'siliconContent',
    constantsignal: 'constantSignal',
    constantSignal: 'constantSignal',
    常量信号: 'constantSignal',
  };
  const normalizeFeatureKey = (raw: string) => {
    const text = String(raw || '').trim();
    if (!text) return '';
    const compact = text.replace(/[\s_\-]/g, '');
    return featureAliasMap[text] || featureAliasMap[compact] || featureAliasMap[compact.toLowerCase()] || text;
  };
  const targetVariableKey = computed(() => {
    const trainingTarget = normalizeFeatureKey(String(activeTraining.value?.targetVariable || ''));
    const serviceTarget = normalizeFeatureKey(String(activeService.value?.targetVariable || ''));
    return trainingTarget || serviceTarget || 'productionRate';
  });
  const targetVariableLabel = computed(() => getFeatureLabel(targetVariableKey.value));
  const expectedFeatures = computed<string[]>(() => {
    const selected = activeTraining.value?.selectedFeatures;
    if (!selected) {
      return [];
    }
    const target = targetVariableKey.value;
    const excluded = new Set(['productionRate', 'energyConsumption', target]);
    const normalized = selected
      .split(',')
      .map((item: string) => normalizeFeatureKey(item))
      .filter((item: string) => item && !excluded.has(item));
    return Array.from(new Set<string>(normalized));
  });

  const historyKeyword = ref('');
  const historyEnvFilter = ref<string>('');
  const historyStatusFilter = ref<string>('');
  const historyArchiveFilter = ref<'active' | 'archived' | ''>('active');
  const filteredDeploymentHistory = computed(() => {
    const keyword = historyKeyword.value.trim().toLowerCase();
    return deploymentHistoryList.value.filter((row) => {
      const archived = archivedHistoryIds.value.includes(row.id);
      const matchArchive =
        historyArchiveFilter.value === 'archived'
          ? archived
          : historyArchiveFilter.value === 'active'
          ? !archived
          : true;
      const matchKeyword =
        !keyword ||
        (row.name || '').toLowerCase().includes(keyword) ||
        (row.modelName || '').toLowerCase().includes(keyword) ||
        formatDeploymentId(row.id).toLowerCase().includes(keyword);
      const matchEnv = !historyEnvFilter.value || row.environment === historyEnvFilter.value;
      const matchStatus = !historyStatusFilter.value || row.status === historyStatusFilter.value;
      return matchKeyword && matchEnv && matchStatus && matchArchive;
    });
  });
  const historyPagination = ref({ page: 1, pageSize: 10 });
  const historyPageCount = computed(() =>
    Math.max(
      1,
      Math.ceil(filteredDeploymentHistory.value.length / historyPagination.value.pageSize)
    )
  );
  const historyTableData = computed(() => {
    const start = (historyPagination.value.page - 1) * historyPagination.value.pageSize;
    return filteredDeploymentHistory.value.slice(start, start + historyPagination.value.pageSize);
  });
  const autoRefreshHistory = ref(false);
  const showHistoryLogs = ref(false);
  const historyLogs = ref<string[]>([]);
  const historyLogsTitle = ref('');
  const selectedHistoryRowKeys = ref<number[]>([]);
  const historyBatchOptions = [{ label: '批量删除', key: 'batchDelete' }];
  const showHistoryDetail = ref(false);
  const currentHistoryRow = ref<any>(null);
  const historyDetailTitle = ref('部署详情');
  const detailConfig = computed(() => {
    if (!currentHistoryRow.value || !currentHistoryRow.value.config) {
      return null;
    }
    try {
      const parsed =
        typeof currentHistoryRow.value.config === 'string'
          ? JSON.parse(currentHistoryRow.value.config)
          : currentHistoryRow.value.config;
      return {
        alarmLowerLimit: parsed?.alarmLowerLimit ?? parsed?.lowerLimit,
        alarmUpperLimit: parsed?.alarmUpperLimit ?? parsed?.upperLimit,
        safetyHold: parsed?.safetyHold ?? parsed?.autoFallback,
        remark: parsed?.remark,
      };
    } catch (e) {
      console.error('配置解析失败:', e);
      return null;
    }
  });

  const serviceColumns = [
    {
      title: '服务ID',
      key: 'id',
      width: 120,
      render: (row: any) => {
        return h(
          NTag,
          {
            type: 'info',
            size: 'small',
            bordered: false,
            style: { fontFamily: 'monospace' },
          },
          { default: () => formatServiceId(row.id) }
        );
      },
    },
    { title: '服务名称', key: 'name' },
    { title: '模型名称', key: 'modelName' },
    {
      title: '部署环境',
      key: 'environment',
      render: (row: any) =>
        h(
          NTag,
          {
            type:
              row.environment === 'production'
                ? 'success'
                : row.environment === 'testing'
                ? 'warning'
                : 'info',
          },
          {
            default: () =>
              row.environment === 'production'
                ? '生产环境'
                : row.environment === 'testing'
                ? '测试环境'
                : '开发环境',
          }
        ),
    },
    {
      title: '状态',
      key: 'status',
      render: (row: any) =>
        h(
          NTag,
          { type: row.status === 'running' ? 'success' : 'error' },
          { default: () => (row.status === 'running' ? '运行中' : '已停止') }
        ),
    },
    { title: '服务地址', key: 'url' },
    { title: '版本号', key: 'version' },
  ];

  const featureLabelMap: Record<string, string> = {
    temperature: '温度',
    pressure: '压力',
    windVolume: '风量',
    coalInjection: '喷煤量',
    materialHeight: '料面高度',
    gasFlow: '煤气流量',
    oxygenLevel: '氧气含量',
    productionRate: '生产率',
    energyConsumption: '能耗',
    hotMetalTemperature: '铁水温度',
    siliconContent: '硅含量',
    constantSignal: '常量信号',
  };

  const defaultFeatureValues: Record<string, number> = {
    temperature: 1300,
    pressure: 50,
    materialHeight: 4,
    gasFlow: 3000,
    oxygenLevel: 19,
    energyConsumption: 1500,
    hotMetalTemperature: 1480,
    constantSignal: 1,
  };
  const featureUnitMap: Record<string, string> = {
    temperature: '℃',
    pressure: 'MPa',
    windVolume: 'm³/h',
    coalInjection: 'kg/t',
    materialHeight: 'm',
    gasFlow: 'm³/h',
    oxygenLevel: '%',
    productionRate: 't/d',
    energyConsumption: 'kgce/t',
    hotMetalTemperature: '℃',
    siliconContent: '%',
  };
  const featureRangeMap: Record<string, [number, number]> = {
    temperature: [1300, 1600],
    pressure: [100, 300],
    windVolume: [3000, 6000],
    coalInjection: [100, 200],
    materialHeight: [2, 6],
    gasFlow: [2000, 5000],
    oxygenLevel: [18, 25],
    productionRate: [30, 100],
    energyConsumption: [1000, 2000],
    hotMetalTemperature: [1350, 1550],
    siliconContent: [0, 2],
  };

  const predictionInput = ref<Record<string, number | null>>({});
  const targetActualValue = ref<number | null>(null);
  const predictionResult = ref('');
  const predictionResultStatus = ref<'success' | 'error' | 'info'>('info');
  const predictionAlert = ref(false);
  const predictionAlertMessage = ref('');
  const predictionSafetyHoldTriggered = ref(false);
  const predictionFallbackSource = ref('');
  const serviceRuntimeStats = ref<
    Record<number, { startTime: string; lastPredictionAt: number | null; predictionCount: number; avgLatencyMs: number }>
  >({});
  const showExplain = ref(false);
  const explainLoading = ref(false);
  const explainResult = ref<any | null>(null);
  const activeServiceRuntime = computed(() => {
    const serviceId = activeService.value?.id;
    if (!serviceId) {
      return null;
    }
    const stat = serviceRuntimeStats.value[serviceId];
    if (!stat) {
      return null;
    }
    const now = Date.now();
    const lastPredictionTime = stat.lastPredictionAt ? new Date(stat.lastPredictionAt).toLocaleString() : '';
    const elapsedSeconds = stat.lastPredictionAt ? Math.max((now - stat.lastPredictionAt) / 1000, 1) : 1;
    const qps = stat.predictionCount > 0 ? stat.predictionCount / elapsedSeconds : 0;
    return {
      startTime: stat.startTime || '',
      lastPredictionTime,
      avgLatencyText: `${stat.avgLatencyMs.toFixed(1)} ms`,
      qpsText: qps.toFixed(2),
    };
  });
  const explainFeatureRows = computed(() => {
    const featureImportance = explainResult.value?.featureImportance || {};
    return Object.keys(featureImportance)
      .map((feature) => ({
        feature,
        label: getFeatureLabel(feature),
        importance: featureImportance[feature],
      }))
      .sort((a, b) => (b.importance || 0) - (a.importance || 0));
  });
  const explainSensitivityRows = computed(() => {
    const list = explainResult.value?.sensitivity || [];
    return list.map((item: any) => ({
      ...item,
      label: getFeatureLabel(item.feature),
    }));
  });

  const formatNumber = (value: any) => {
    return formatNumericDisplay(value, { decimals: 4 });
  };

  const explainImportanceColumns = [
    { title: '特征', key: 'label', width: 160 },
    {
      title: '重要性',
      key: 'importance',
      render: (row: any) => formatNumber(row.importance),
    },
  ];
  const explainSensitivityColumns = [
    { title: '特征', key: 'label', width: 120 },
    { title: '基准值', key: 'baseValue', render: (row: any) => formatNumber(row.baseValue) },
    { title: '步长', key: 'step', render: (row: any) => formatNumber(row.step) },
    { title: '上调值', key: 'upValue', render: (row: any) => formatNumber(row.upValue) },
    { title: '下调值', key: 'downValue', render: (row: any) => formatNumber(row.downValue) },
    {
      title: '上调预测',
      key: 'upPrediction',
      render: (row: any) => formatNumber(row.upPrediction),
    },
    {
      title: '下调预测',
      key: 'downPrediction',
      render: (row: any) => formatNumber(row.downPrediction),
    },
    { title: '上调变化', key: 'upDelta', render: (row: any) => formatNumber(row.upDelta) },
    { title: '下调变化', key: 'downDelta', render: (row: any) => formatNumber(row.downDelta) },
  ];

  const getFeatureLabel = (feature: string) => {
    return featureLabelMap[feature] || feature;
  };
  const getFeatureHint = (feature: string) => {
    const unit = featureUnitMap[feature] || '';
    const range = featureRangeMap[feature];
    if (!range) {
      return unit ? `单位：${unit}` : '请输入数值';
    }
    const [min, max] = range;
    return unit ? `建议范围：${min} ~ ${max} ${unit}` : `建议范围：${min} ~ ${max}`;
  };

  const syncPredictionInput = (features: string[], reset = false) => {
    const next: Record<string, number | null> = {};
    features.forEach((feature) => {
      const existing = predictionInput.value[feature];
      if (!reset && existing !== undefined && existing !== null && !Number.isNaN(existing)) {
        next[feature] = existing;
        return;
      }
      const defaultValue = defaultFeatureValues[feature];
      next[feature] = defaultValue !== undefined ? defaultValue : null;
    });
    predictionInput.value = next;
  };

  const clearPredictionResult = () => {
    predictionResult.value = '';
    predictionResultStatus.value = 'info';
    predictionSafetyHoldTriggered.value = false;
    predictionFallbackSource.value = '';
  };

  const resetPredictionInput = () => {
    syncPredictionInput(expectedFeatures.value, true);
    const targetDefault = defaultFeatureValues[targetVariableKey.value];
    targetActualValue.value = targetDefault !== undefined ? targetDefault : null;
    clearPredictionResult();
  };

  const applyRuntimeConfig = (payload?: Record<string, any>) => {
    const next = { ...defaultRuntimeConfig, ...(payload || {}) };
    Object.assign(runtimeConfig, next);
  };

  const parseRuntimeConfig = (raw: any) => {
    if (!raw) {
      return null;
    }
    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw);
      } catch {
        return null;
      }
    }
    if (typeof raw === 'object') {
      return raw;
    }
    return null;
  };
  const activeServiceRuntimeConfig = computed(() => {
    const parsed = parseRuntimeConfig(activeService.value?.serviceConfig);
    const merged = { ...defaultRuntimeConfig, ...(parsed || {}) } as Record<string, any>;
    const hold = merged.safetyHold ?? merged.autoFallback;
    if (typeof hold === 'boolean') {
      merged.safetyHold = hold;
    } else if (hold == null) {
      merged.safetyHold = false;
    } else {
      merged.safetyHold = String(hold).toLowerCase() === 'true';
    }
    return merged;
  });
  const activeSafetyHoldEnabled = computed(() => !!activeServiceRuntimeConfig.value.safetyHold);

  const applyHistoryConfig = (row: any) => {
    if (!row) {
      return;
    }
    if (row.modelId || row.trainingId) {
      selectedModel.value = row.modelId || row.trainingId;
    }
    if (row.name) {
      deploymentName.value = row.name;
    }
    if (row.version) {
      version.value = row.version;
    }
    if (row.environment) {
      deploymentEnvironment.value = row.environment;
    }
    const parsed = parseRuntimeConfig(row.config);
    if (parsed) {
      applyRuntimeConfig(parsed);
    }
  };

  // 方法
  const formatDate = (date: string | Date) => {
    if (!date) return '-';
    const d = new Date(date);
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const getModelTypeName = (type: string) => {
    const types: Record<string, string> = {
      neural_network: '神经网络',
      random_forest: '随机森林',
      gradient_boosting: '梯度提升树',
      gpr: '高斯过程回归',
    };
    return types[type] || type;
  };

  const buildModelLabel = (row: any) => {
    const typeName = getModelTypeName(row.modelType);
    const timeText = row.endTime ? formatDate(row.endTime) : formatDate(row.startTime);
    return `${typeName} (${timeText})`;
  };

  const getProgressByStatus = (status: string) => {
    if (status === 'completed') return 100;
    if (status === 'running') return 60;
    return 0;
  };

  const computeProgressFromLogs = (logs: string[], status: string) => {
    if (status === 'completed') {
      return 100;
    }
    const stages = [
      { text: '部署开始', value: 10 },
      { text: '加载模型文件', value: 30 },
      { text: '配置部署环境', value: 50 },
      { text: '启动服务实例', value: 70 },
      { text: '服务健康检查', value: 90 },
      { text: '部署完成', value: 100 },
      { text: '部署失败', value: 100 },
      { text: '部署已取消', value: 100 },
    ];
    let progress = 0;
    if (logs && logs.length > 0) {
      // 优化：从后往前找最新的进度标识，避免 join 大量日志
      const searchLimit = Math.min(logs.length, 50); // 只看最后50条日志
      const recentLogs = logs.slice(-searchLimit).reverse();

      for (const stage of stages.reverse()) {
        if (recentLogs.some((log) => log && log.includes(stage.text))) {
          progress = stage.value;
          break;
        }
      }
      // 还原 stages 顺序以防后续逻辑受影响 (虽然这里是局部变量)
      stages.reverse();
    }
    if (status === 'running') {
      progress = Math.max(progress, 10);
    }
    if (status === 'failed' || status === 'canceled') {
      progress = 100;
    }
    if (progress === 0) {
      return getProgressByStatus(status);
    }
    return progress;
  };

  const applyDeploymentResponse = (data: any) => {
    deploymentStatus.value = data?.deploymentStatus || 'idle';
    const logs = Array.isArray(data?.deploymentLogs) ? data.deploymentLogs : [];
    deploymentProgress.value =
      data?.deploymentProgress ?? computeProgressFromLogs(logs, deploymentStatus.value);
    deployedModelName.value = data?.deployedModelName || '';
    deployedEnvironment.value = data?.deployedEnvironment || '';
    deploymentTime.value = data?.deploymentTime || '';
    serviceUrl.value = data?.serviceUrl || '';
    serviceStatus.value = data?.serviceStatus || '';
    apiVersion.value = data?.apiVersion || '';
    deploymentLogs.value = logs;
    const nextServiceId = getServiceIdFromUrl(serviceUrl.value);
    if (nextServiceId) {
      activeServiceId.value = nextServiceId;
    }
  };

  const refreshServiceLogs = async () => {
    const service = activeService.value;
    if (!service?.id) {
      return;
    }
    const response: any = await optimizationApi.deployment.getServiceLogs(service.id);
    if (response.code === 200) {
      deploymentLogs.value = response.data || [];
      deploymentProgress.value = computeProgressFromLogs(
        deploymentLogs.value,
        deploymentStatus.value
      );
    }
  };

  const refreshServiceHealth = async () => {
    const service = activeService.value;
    if (!service?.id) {
      return;
    }
    const response: any = await optimizationApi.deployment.getServiceHealth(service.id);
    if (response.code === 200) {
      const data = response.data || {};
      if (data.status) {
        serviceStatus.value = data.status;
      }
      if (data.health === 'unhealthy') {
        serviceStatus.value = 'stopped';
      }
    }
  };

  const isPolling = ref(false);

  const startPolling = () => {
    stopPolling();
    pollingTimer.value = window.setInterval(async () => {
      if (isPolling.value) return;
      isPolling.value = true;
      try {
        await Promise.all([
          loadDeploymentHistory(),
          loadServices(),
          refreshServiceLogs(),
          refreshServiceHealth(),
        ]);
      } catch (error) {
        console.error('Polling error:', error);
      } finally {
        isPolling.value = false;
      }
    }, 3000); // 稍微延长到3秒
  };

  const stopPolling = () => {
    if (pollingTimer.value) {
      window.clearInterval(pollingTimer.value);
      pollingTimer.value = null;
    }
    isPolling.value = false;
  };

  const loadTrainingHistory = async () => {
    try {
      const response: any = await optimizationApi.modelTraining.getTrainingHistory();
      if (response.code === 200) {
        trainingHistoryList.value = response.data || [];
        const completed = trainingHistoryList.value.filter((item) => item.status === 'completed');
        modelOptions.value = completed.map((item) => ({
          label: buildModelLabel(item),
          value: item.id,
        }));
        if (!selectedModel.value && modelOptions.value.length > 0) {
          selectedModel.value = modelOptions.value[0].value;
        }
      } else {
        message.error(response.msg || '获取模型列表失败');
      }
    } catch (error) {
      message.error('获取模型列表失败');
    }
  };

  const loadDeploymentHistory = async () => {
    try {
      const response: any = await optimizationApi.deployment.getHistory();
      if (response.code === 200) {
        deploymentHistoryList.value = response.data || [];
        const latest = deploymentHistoryList.value[0];
        if (latest) {
          deploymentStatus.value = latest.status || 'idle';
          deploymentProgress.value = computeProgressFromLogs(
            deploymentLogs.value,
            deploymentStatus.value
          );
          deployedModelName.value = latest.modelName || '';
          deployedEnvironment.value = latest.environment || '';
          deploymentTime.value = latest.deployTime || '';
          serviceUrl.value = latest.serviceUrl || '';
        } else {
          deploymentStatus.value = 'idle';
          deploymentProgress.value = 0;
          deployedModelName.value = '';
          deployedEnvironment.value = '';
          deploymentTime.value = '';
          serviceUrl.value = '';
        }
      } else {
        message.error(response.msg || '获取部署历史失败');
      }
    } catch (error) {
      message.error('获取部署历史失败');
    }
  };

  const loadServices = async () => {
    try {
      const response: any = await optimizationApi.deployment.getServices();
      if (response.code === 200) {
        serviceList.value = response.data || [];
        const fallbackId = serviceList.value[0]?.id ?? null;
        const exists = activeServiceId.value
          ? serviceList.value.some((item) => item.id === activeServiceId.value)
          : false;
        if (!exists) {
          activeServiceId.value = fallbackId;
        }
        const active = activeService.value || null;
        updateActiveServiceState(active);
        hydrateRuntimeStats(response.data || []);
      } else {
        message.error(response.msg || '获取服务列表失败');
      }
    } catch (error) {
      message.error('获取服务列表失败');
    }
  };
  const resolveServiceStartTime = (serviceId: number) => {
    const row = deploymentHistoryList.value.find((item: any) => {
      const serviceUrl = String(item?.serviceUrl || '');
      return serviceUrl.endsWith(`/${serviceId}`);
    });
    return row?.deployTime || '';
  };
  const hydrateRuntimeStats = (services: any[]) => {
    const next = { ...serviceRuntimeStats.value };
    services.forEach((service) => {
      const sid = Number(service?.id);
      if (Number.isNaN(sid) || sid <= 0) {
        return;
      }
      if (!next[sid]) {
        next[sid] = {
          startTime: resolveServiceStartTime(sid),
          lastPredictionAt: null,
          predictionCount: 0,
          avgLatencyMs: 0,
        };
      } else if (!next[sid].startTime) {
        next[sid].startTime = resolveServiceStartTime(sid);
      }
    });
    serviceRuntimeStats.value = next;
  };
  const updateRuntimeMetric = (serviceId: number, latencyMs: number) => {
    if (!serviceId) {
      return;
    }
    const current = serviceRuntimeStats.value[serviceId] || {
      startTime: resolveServiceStartTime(serviceId),
      lastPredictionAt: null,
      predictionCount: 0,
      avgLatencyMs: 0,
    };
    const nextCount = current.predictionCount + 1;
    const nextAvg =
      current.predictionCount === 0
        ? latencyMs
        : (current.avgLatencyMs * current.predictionCount + latencyMs) / nextCount;
    serviceRuntimeStats.value = {
      ...serviceRuntimeStats.value,
      [serviceId]: {
        ...current,
        lastPredictionAt: Date.now(),
        predictionCount: nextCount,
        avgLatencyMs: nextAvg,
      },
    };
  };

  const viewDeploymentDetail = async (row: any) => {
    currentHistoryRow.value = row;
    historyDetailTitle.value = row?.name ? `${row.name}详情` : '部署详情';
    showHistoryDetail.value = true;
    deploymentStatus.value = row.status || 'idle';
    deploymentProgress.value = computeProgressFromLogs(
      deploymentLogs.value,
      deploymentStatus.value
    );
    deployedModelName.value = row.modelName || '';
    deployedEnvironment.value = row.environment || '';
    deploymentTime.value = row.deployTime || '';
    serviceUrl.value = row.serviceUrl || '';
    const nextServiceId = getServiceIdFromUrl(serviceUrl.value);
    if (nextServiceId) {
      activeServiceId.value = nextServiceId;
    }
    await loadServices();
    await refreshServiceLogs();
  };

  const cancelDeployment = async (row: any) => {
    if (!row?.id) {
      return;
    }
    try {
      const response: any = await optimizationApi.deployment.cancel(row.id);
      if (response.code === 200) {
        message.success(response.msg || '部署已取消');
        await loadDeploymentHistory();
        await loadServices();
        await refreshServiceLogs();
      } else {
        message.error(response.msg || '取消部署失败');
      }
    } catch (error) {
      message.error('取消部署失败');
    }
  };

  const retryDeployment = async (row: any) => {
    if (!row?.id) {
      return;
    }
    try {
      const response: any = await optimizationApi.deployment.retry(row.id);
      if (response.code === 200) {
        applyDeploymentResponse(response.data || {});
        message.success(response.msg || '部署已重试');
        startPolling();
        await loadDeploymentHistory();
        await loadServices();
      } else {
        message.error(response.msg || '重试部署失败');
      }
    } catch (error) {
      message.error('重试部署失败');
    }
  };

  const deployModel = async () => {
    if (!selectedModel.value) {
      message.warning('请选择模型');
      return;
    }
    deploymentStatus.value = 'running';
    deploymentProgress.value = 0;
    deploymentLogs.value = [];
    try {
      const response: any = await optimizationApi.deployment.deploy({
        trainingId: selectedModel.value,
        secondaryTrainingId: selectedSecondaryModel.value,
        environment: deploymentEnvironment.value,
        name: deploymentName.value,
        version: version.value,
        config: JSON.stringify(runtimeConfig),
      });
      if (response.code === 200) {
        applyDeploymentResponse(response.data || {});
        message.success(response.msg || '部署成功');
        startPolling();
      } else {
        message.error(response.msg || '部署失败');
      }
    } catch (error) {
      message.error('部署失败');
    }
  };

  const resetDeployment = () => {
    selectedModel.value = modelOptions.value.length ? modelOptions.value[0].value : null;
    selectedSecondaryModel.value = null;
    deploymentEnvironment.value = 'production';
    deploymentName.value = '高炉优化模型部署';
    version.value = '1.0.0';
    message.success('重置部署配置成功');
  };

  const onHistoryMoreSelect = (key: string, row: any) => {
    if (key === 'cancel') {
      cancelDeployment(row);
      return;
    }
    if (key === 'retry') {
      retryDeployment(row);
      return;
    }
    if (key === 'clone') {
      applyHistoryConfig(row);
      activeTab.value = 'status';
      message.success('配置已加载');
      return;
    }
    if (key === 'archive') {
      if (row?.id && !archivedHistoryIds.value.includes(row.id)) {
        archivedHistoryIds.value = [...archivedHistoryIds.value, row.id];
        message.success('记录已归档');
      }
      return;
    }
    if (key === 'unarchive') {
      if (row?.id && archivedHistoryIds.value.includes(row.id)) {
        archivedHistoryIds.value = archivedHistoryIds.value.filter((id) => id !== row.id);
        message.success('已取消归档');
      }
      return;
    }
  };

  const onHistoryBatchSelect = (key: string) => {
    if (key === 'batchDelete') {
      batchDeleteHistory();
    }
  };

  const batchDeleteHistory = () => {
    if (!selectedHistoryRowKeys.value.length) {
      message.warning('请至少选择一条记录');
      return;
    }
    dialog.warning({
      title: '批量删除确认',
      content: `确定要删除选中的 ${selectedHistoryRowKeys.value.length} 条部署记录吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const response: any = await optimizationApi.deployment.deleteHistoryBatch(
            selectedHistoryRowKeys.value
          );
          if (response.code === 200) {
            message.success(response.msg || '批量删除成功');
            selectedHistoryRowKeys.value = [];
            if (currentHistoryRow.value?.id) {
              showHistoryDetail.value = false;
              currentHistoryRow.value = null;
            }
            await loadDeploymentHistory();
          } else {
            message.error(response.msg || '批量删除失败');
          }
        } catch (error) {
          message.error('批量删除失败');
        }
      },
    });
  };

  const copyServiceUrl = (row: any) => {
    const url = row?.serviceUrl || '';
    if (!url) {
      message.warning('暂无服务地址');
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard
        .writeText(url)
        .then(() => message.success('已复制服务地址'))
        .catch(() => message.error('复制失败'));
      return;
    }
    const textarea = document.createElement('textarea');
    textarea.value = url;
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      message.success('已复制服务地址');
    } catch {
      message.error('复制失败');
    } finally {
      document.body.removeChild(textarea);
    }
  };

  const handleDetailCopy = () => {
    if (!currentHistoryRow.value) {
      message.warning('暂无部署详情');
      return;
    }
    copyServiceUrl(currentHistoryRow.value);
  };

  const handleDetailLogs = () => {
    if (!currentHistoryRow.value) {
      message.warning('暂无部署详情');
      return;
    }
    viewHistoryLogs(currentHistoryRow.value);
  };

  const deleteHistoryRow = (row: any) => {
    if (!row?.id) {
      message.warning('暂无部署详情');
      return;
    }
    dialog.warning({
      title: '删除确认',
      content: '确定删除该部署记录吗？',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const response: any = await optimizationApi.deployment.deleteHistory(row.id);
          if (response.code === 200) {
            message.success(response.msg || '删除成功');
            if (currentHistoryRow.value?.id === row.id) {
              showHistoryDetail.value = false;
              currentHistoryRow.value = null;
            }
            selectedHistoryRowKeys.value = selectedHistoryRowKeys.value.filter(
              (id) => id !== row.id
            );
            await loadDeploymentHistory();
          } else {
            message.error(response.msg || '删除失败');
          }
        } catch (error) {
          message.error('删除失败');
        }
      },
    });
  };

  const handleDetailDelete = () => {
    if (!currentHistoryRow.value) {
      message.warning('暂无部署详情');
      return;
    }
    deleteHistoryRow(currentHistoryRow.value);
  };

  const viewHistoryLogs = async (row: any) => {
    const sid = getServiceIdFromUrl(row?.serviceUrl || '');
    if (!sid) {
      message.warning('暂无有效服务ID');
      return;
    }
    const response: any = await optimizationApi.deployment.getServiceLogs(sid);
    if (response.code === 200) {
      historyLogs.value = response.data || [];
      historyLogsTitle.value = `服务日志 #${sid}`;
      showHistoryLogs.value = true;
    } else {
      message.error(response.msg || '获取服务日志失败');
    }
  };

  const startService = () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可启动的服务');
      return;
    }
    optimizationApi.deployment
      .startService(service.id)
      .then((response: any) => {
        if (response.code === 200) {
          message.success(response.msg || '服务已启动');
          loadServices();
          loadDeploymentHistory();
        } else {
          message.error(response.msg || '启动服务失败');
        }
      })
      .catch(() => {
        message.error('启动服务失败');
      });
  };

  const restartService = () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可重启的服务');
      return;
    }
    optimizationApi.deployment
      .restartService(service.id)
      .then((response: any) => {
        if (response.code === 200) {
          message.success(response.msg || '服务已重启');
          loadServices();
          loadDeploymentHistory();
        } else {
          message.error(response.msg || '重启服务失败');
        }
      })
      .catch(() => {
        message.error('重启服务失败');
      });
  };

  const stopService = async () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可停止的服务');
      return;
    }
    try {
      const response: any = await optimizationApi.deployment.stopService(service.id);
      if (response.code === 200) {
        message.success(response.msg || '服务已停止');
        await loadServices();
        await loadDeploymentHistory();
      } else {
        message.error(response.msg || '停止服务失败');
      }
    } catch (error) {
      message.error('停止服务失败');
    }
  };

  const buildPredictionInput = () => {
    const input: Record<string, number | null> = {};
    if (activeSafetyHoldEnabled.value && targetVariableKey.value) {
      input[targetVariableKey.value] = targetActualValue.value;
    }
    expectedFeatures.value.forEach((feature) => {
      input[feature] = predictionInput.value[feature];
    });
    return input;
  };

  const testService = async () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可测试的服务');
      predictionResult.value = '暂无可测试的服务';
      predictionResultStatus.value = 'error';
      predictionAlert.value = false;
      predictionAlertMessage.value = '';
      predictionSafetyHoldTriggered.value = false;
      predictionFallbackSource.value = '';
      return;
    }
    if (!expectedFeatures.value.length) {
      message.warning('暂无可用特征，请先选择已部署模型');
      predictionResult.value = '暂无可用特征，请先选择已部署模型';
      predictionResultStatus.value = 'error';
      predictionAlert.value = false;
      predictionAlertMessage.value = '';
      predictionSafetyHoldTriggered.value = false;
      predictionFallbackSource.value = '';
      return;
    }
    const missing = expectedFeatures.value.filter((feature) => {
      const value = predictionInput.value[feature];
      return value === null || value === undefined || Number.isNaN(value);
    });
    if (missing.length) {
      const missingText = `请补全预测特征: ${missing.map(getFeatureLabel).join('、')}`;
      message.warning(missingText);
      predictionResult.value = missingText;
      predictionResultStatus.value = 'error';
      predictionAlert.value = false;
      predictionAlertMessage.value = '';
      predictionSafetyHoldTriggered.value = false;
      predictionFallbackSource.value = '';
      return;
    }
    if (
      activeSafetyHoldEnabled.value &&
      (targetActualValue.value === null ||
        targetActualValue.value === undefined ||
        Number.isNaN(targetActualValue.value))
    ) {
      const msg = `已开启安全熔断，请填写“熔断回退值（${targetVariableLabel.value}）”`;
      message.warning(msg);
      predictionResult.value = msg;
      predictionResultStatus.value = 'error';
      predictionAlert.value = false;
      predictionAlertMessage.value = '';
      predictionSafetyHoldTriggered.value = false;
      predictionFallbackSource.value = '';
      return;
    }
    try {
      const startedAt = Date.now();
      const response: any = await optimizationApi.deployment.predict(
        service.id,
        buildPredictionInput()
      );
      const latencyMs = Date.now() - startedAt;
      if (response.code === 200) {
        const data = response.data || {};
        const prediction = data.prediction ?? '-';
        predictionAlert.value = !!data.alert;
        predictionAlertMessage.value = data.alertMessage || '';
        predictionSafetyHoldTriggered.value = !!data.safetyHoldTriggered;
        predictionFallbackSource.value = String(data.fallbackSource || '');
        updateRuntimeMetric(service.id, latencyMs);

        if (data.alert) {
          predictionResultStatus.value = 'error';
          const fallbackLabel =
            predictionSafetyHoldTriggered.value && predictionFallbackSource.value
              ? `，已回退到${getFeatureLabel(predictionFallbackSource.value)}当前值`
              : '';
          predictionResult.value = `预测结果：${prediction} 【警告：${data.alertMessage}${fallbackLabel}】`;
          message.warning(`预测完成，触发报警：${data.alertMessage}`);
        } else {
          predictionResultStatus.value = 'success';
          predictionResult.value = `预测结果：${prediction}`;
          message.success(`预测完成: ${prediction}`);
        }
      } else {
        predictionResult.value = response.msg || '预测失败';
        predictionResultStatus.value = 'error';
        predictionAlert.value = false;
        predictionAlertMessage.value = '';
        predictionSafetyHoldTriggered.value = false;
        predictionFallbackSource.value = '';
        message.error(response.msg || '预测失败');
      }
    } catch (error) {
      predictionResult.value = '预测失败';
      predictionResultStatus.value = 'error';
      predictionAlert.value = false;
      predictionAlertMessage.value = '';
      predictionSafetyHoldTriggered.value = false;
      predictionFallbackSource.value = '';
      message.error('预测失败');
    }
  };

  const explainService = async () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可测试的服务');
      return;
    }
    if (!expectedFeatures.value.length) {
      message.warning('暂无可用特征，请先选择已部署模型');
      return;
    }
    const missing = expectedFeatures.value.filter((feature) => {
      const value = predictionInput.value[feature];
      return value === null || value === undefined || Number.isNaN(value);
    });
    if (missing.length) {
      const missingText = `请补全预测特征: ${missing.map(getFeatureLabel).join('、')}`;
      message.warning(missingText);
      return;
    }
    showExplain.value = true;
    explainLoading.value = true;
    explainResult.value = null;
    try {
      const response: any = await optimizationApi.deployment.explain(
        service.id,
        buildPredictionInput()
      );
      if (response.code === 200) {
        explainResult.value = response.data || {};
        message.success('解释性分析完成');
      } else {
        message.error(response.msg || '解释性分析失败');
      }
    } catch (error) {
      message.error('解释性分析失败');
    } finally {
      explainLoading.value = false;
    }
  };

  const viewServiceLogs = () => {
    const service = activeService.value;
    if (!service?.id) {
      message.warning('暂无可查看的服务');
      return;
    }

    optimizationApi.deployment
      .getServiceLogs(service.id)
      .then((response: any) => {
        if (response.code === 200) {
          const logs = response.data || [];
          deploymentLogs.value = logs;
          historyLogs.value = logs;
          historyLogsTitle.value = `实时日志 #${formatServiceId(service.id)} · ${service.name || '未命名服务'}`;
          showHistoryLogs.value = true;
          message.success('日志已更新');
        } else {
          message.error(response.msg || '获取服务日志失败');
        }
      })
      .catch(() => {
        message.error('获取服务日志失败');
      });
  };

  onMounted(async () => {
    await loadTrainingHistory();
    await loadDeploymentHistory();
    await loadServices();
    if (deploymentStatus.value === 'running') {
      startPolling();
    }
  });

  watch(expectedFeatures, (features) => {
    syncPredictionInput(features);
  });

  watch(targetVariableKey, (key) => {
    const targetDefault = defaultFeatureValues[key];
    targetActualValue.value = targetDefault !== undefined ? targetDefault : null;
  }, { immediate: true });

  const updateActiveServiceState = (service: any | null) => {
    if (!service) {
      serviceUrl.value = '';
      serviceStatus.value = '';
      apiVersion.value = '';
      return;
    }
    serviceUrl.value = service.url || '';
    serviceStatus.value = service.status || '';
    apiVersion.value = service.version || '';
  };

  const setActiveService = (service: any) => {
    if (!service?.id) {
      return;
    }
    activeServiceId.value = service.id;
    updateActiveServiceState(service);
    clearPredictionResult();
  };

  const getServiceRowProps = (row: any) => ({
    class: 'cursor-pointer',
    onClick: () => setActiveService(row),
  });

  const getServiceRowClass = (row: any) => (row?.id === activeServiceId.value ? 'bg-blue-50' : '');

  watch(deploymentStatus, (status) => {
    if (status === 'running') {
      startPolling();
    } else {
      stopPolling();
    }
  });

  watch(activeService, (service) => {
    updateActiveServiceState(service || null);
  });
  watch(activeServiceId, (next, prev) => {
    if (next === prev) {
      return;
    }
    clearPredictionResult();
  });
  let historyRefreshTimer: number | null = null;
  const startHistoryRefresh = () => {
    stopHistoryRefresh();
    historyRefreshTimer = window.setInterval(async () => {
      await loadDeploymentHistory();
    }, 5000);
  };
  const stopHistoryRefresh = () => {
    if (historyRefreshTimer) {
      window.clearInterval(historyRefreshTimer);
      historyRefreshTimer = null;
    }
  };
  watch(autoRefreshHistory, (v) => {
    if (v) {
      startHistoryRefresh();
    } else {
      stopHistoryRefresh();
    }
  });

  onUnmounted(() => {
    stopPolling();
    stopHistoryRefresh();
  });
</script>

<style scoped>
  .deploy-main-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .deploy-panel {
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .deploy-sub-card {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
    box-shadow: none;
  }

  .deploy-inner-panel {
    padding: 14px;
    border-radius: 10px;
    border: 1px solid var(--n-border-color);
    background-color: var(--n-color);
  }

  .deploy-form :deep(.n-base-selection),
  .deploy-form :deep(.n-input),
  .deploy-form :deep(.n-input-number) {
    border-radius: 10px;
  }
</style>
