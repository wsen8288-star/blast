<template>
  <div
    class="model-training w-full p-4 md:p-6 box-border"
  >
    <n-card title="模型训练" class="mb-4 border">
      <!-- 训练配置 -->
      <n-form label-placement="left" label-width="120" class="mb-4">
        <div class="grid grid-cols-2 gap-4">
          <n-form-item label="模型类型">
            <n-select
              v-model:value="modelType"
              :options="modelTypeOptions"
              placeholder="选择模型类型"
            />
          </n-form-item>
          <n-form-item label="训练数据">
            <n-upload
              v-model:file-list="fileList"
              @change="handleFileChange"
              action="/api/data/upload"
              accept=".csv,.xlsx,.xls"
              :auto-upload="false"
              :show-upload-list="false"
            >
              <n-button
                type="primary"
                :disabled="trainingDataUploading"
                :loading="trainingDataUploading"
                round
                size="large"
              >
                {{ trainingDataUploading ? '上传中...' : '选择文件' }}
              </n-button>
              <span class="ml-2" v-if="trainingDataFile">
                {{ trainingDataFile.name }}
              </span>
              <n-progress
                v-if="trainingDataUploading"
                type="line"
                :percentage="trainingDataUploadProgress"
                :show-text="true"
                size="small"
                class="mt-2"
              />
            </n-upload>
            <n-upload
              v-model:file-list="snapshotFileList"
              @change="handleSnapshotChange"
              accept=".json,.snapshot.json"
              :auto-upload="false"
              :show-upload-list="false"
            >
              <n-button
                class="ml-2"
                type="info"
                :disabled="snapshotImporting"
                :loading="snapshotImporting"
                round
                size="large"
              >
                {{ snapshotImporting ? '导入中...' : '导入快照' }}
              </n-button>
            </n-upload>
          </n-form-item>
          <n-form-item label="预测目标 (Target Variable)">
            <n-select
              v-model:value="targetVariable"
              :options="targetVariableOptions"
              placeholder="选择预测目标"
            />
          </n-form-item>
          <n-form-item label="数据切分">
            <n-select
              v-model:value="splitMode"
              :options="splitModeOptions"
              placeholder="选择切分方式"
            />
            <div class="mt-1">
              <n-text depth="3" size="small">
                auto：有时间戳则按时间切分，否则随机切分；time：强制按时间（无时间戳则自动降级随机）；random：强制随机
              </n-text>
            </div>
          </n-form-item>
        </div>
        <!-- 动态渲染模型特定配置项 -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 随机森林配置 -->
          <template v-if="modelType === 'random_forest'">
            <n-form-item label="树的数量">
              <n-input-number
                v-model:value="treeCount"
                :min="5"
                :max="1000"
                placeholder="输入树的数量"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐100-500棵树，当前推荐值：200 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="最大深度">
              <n-input-number
                v-model:value="maxDepth"
                :min="5"
                :max="50"
                placeholder="输入最大深度"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐10-30，当前推荐值：20 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="特征采样数">
              <n-input-number
                v-model:value="featureCount"
                :min="1"
                :max="8"
                placeholder="输入特征采样数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐总特征数的平方根，当前推荐值：4 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="叶子最小样本数">
              <n-input-number
                v-model:value="rfNodeSize"
                :min="1"
                :max="100"
                placeholder="输入叶子最小样本数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐1-10，越大越防过拟合 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="采样率">
              <n-input-number
                v-model:value="rfSubsample"
                :min="0.1"
                :max="1.0"
                :step="0.01"
                placeholder="输入采样率"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐0.8-1.0 </n-text>
              </div>
            </n-form-item>
          </template>

          <!-- 梯度提升树配置 -->
          <template v-else-if="modelType === 'gradient_boosting'">
            <n-form-item label="迭代次数">
              <n-input-number
                v-model:value="iterations"
                :min="5"
                :max="1000"
                placeholder="输入迭代次数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐100-300，当前推荐值：200 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="学习率">
              <n-input-number
                v-model:value="learningRate"
                :min="0.0001"
                :max="0.1"
                :step="0.0001"
                placeholder="输入学习率"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐0.01-0.1，当前推荐值：0.01 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="损失函数">
              <n-select
                v-model:value="gbdtLossFunction"
                :options="gbdtLossFunctionOptions"
                placeholder="选择损失函数"
              />
            </n-form-item>
            <n-form-item label="最大深度">
              <n-input-number
                v-model:value="maxDepth"
                :min="1"
                :max="20"
                placeholder="输入最大深度"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐3-8，当前推荐值：6 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="子采样率 (Subsample)">
              <n-input-number
                v-model:value="subsample"
                :min="0.1"
                :max="1.0"
                :step="0.01"
                placeholder="输入采样率"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐0.5-0.8，当前推荐值：0.8 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="最大节点数">
              <n-input-number
                v-model:value="maxNodes"
                :min="2"
                :max="1000"
                placeholder="输入最大节点数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐20-100，当前推荐值：64 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="叶子最小样本数">
              <n-input-number
                v-model:value="nodeSize"
                :min="1"
                :max="100"
                placeholder="输入叶子最小样本数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐2-20，当前推荐值：16 </n-text>
              </div>
            </n-form-item>
          </template>

          <!-- 高斯过程回归配置 -->
          <template v-else-if="modelType === 'gpr'">
            <n-form-item label="长度尺度 (Length Scale)">
              <n-input-number
                v-model:value="gprLengthScale"
                :min="0.1"
                :max="10.0"
                :step="0.1"
                placeholder="输入长度尺度"
              />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：控制高斯核的作用范围。值越小越拟合局部细节，值越大越平滑。推荐 0.5-5.0
                </n-text>
              </div>
            </n-form-item>
            <n-form-item label="噪声水平 (Noise Variance)">
              <n-input-number
                v-model:value="gprNoiseVariance"
                :min="0.01"
                :max="0.5"
                :step="0.01"
                placeholder="输入ε参数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：预估数据的噪声强度。高炉数据通常含噪，推荐 0.05-0.2
                </n-text>
              </div>
            </n-form-item>
            <n-form-item label="集成模型数量 (Ensemble Size)">
              <n-input-number
                v-model:value="iterations"
                :min="5"
                :max="1000"
                placeholder="输入迭代次数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：由于GPR计算量大，采用Bagging集成策略。推荐训练 100-300 个子模型取平均
                </n-text>
              </div>
            </n-form-item>
            <n-form-item label="子采样大小 (Subsample Size)">
              <n-input-number
                v-model:value="batchSize"
                :min="1"
                :max="1000"
                placeholder="输入批次大小"
              />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：每个子模型使用的样本数量。推荐 32-64，过大可能导致计算超时
                </n-text>
              </div>
            </n-form-item>
          </template>

          <!-- 神经网络配置 -->
          <template v-else-if="modelType === 'neural_network'">
            <n-form-item label="训练轮数">
              <n-input-number
                v-model:value="epochs"
                :min="1"
                :max="10000"
                placeholder="输入训练轮数"
              />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：对于高炉数据，推荐1000-5000轮，当前推荐值：2000
                </n-text>
              </div>
            </n-form-item>
            <n-form-item label="批次大小">
              <n-input-number
                v-model:value="batchSize"
                :min="1"
                :max="1000"
                placeholder="输入批次大小"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐8-32，当前推荐值：8 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="学习率">
              <n-input-number
                v-model:value="learningRate"
                :min="0.0001"
                :max="0.1"
                :step="0.0001"
                placeholder="输入学习率"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：{{ getLearningRateSuggestion() }} </n-text>
              </div>
            </n-form-item>
            <n-form-item label="隐藏层数量">
              <n-input-number
                v-model:value="hiddenLayers"
                :min="1"
                :max="10"
                placeholder="输入隐藏层数量"
              />
              <div class="mt-1">
                <n-text depth="3" size="small"> 建议：推荐2-5层，当前推荐值：3 </n-text>
              </div>
            </n-form-item>
            <n-form-item label="每层神经元数" class="col-span-2">
              <n-input v-model:value="neuronsPerLayer" placeholder="输入每层神经元数，用逗号分隔" />
              <div class="mt-1">
                <n-text depth="3" size="small">
                  建议：推荐逐层递减，如128,64,32，当前推荐值：128,64,32
                </n-text>
              </div>
            </n-form-item>
            <n-form-item label="激活函数">
              <n-select
                v-model:value="activationFunction"
                :options="activationFunctionOptions"
                placeholder="选择激活函数"
              />
            </n-form-item>
            <n-form-item label="损失函数">
              <n-select
                v-model:value="nnLossFunction"
                :options="lossFunctionOptions"
                placeholder="选择损失函数"
              />
            </n-form-item>
            <n-form-item label="高级参数" class="col-span-2">
              <div class="flex items-center gap-3">
                <n-button size="small" tertiary @click="nnAdvancedOpen = !nnAdvancedOpen">
                  {{ nnAdvancedOpen ? '收起' : '展开' }}
                </n-button>
                <n-text depth="3" size="small">
                  优化器：{{ nnOptimizer }}，Dropout：{{ nnDropoutRate }}
                </n-text>
              </div>
            </n-form-item>
            <n-form-item v-if="nnAdvancedOpen" label="优化器">
              <n-select
                v-model:value="nnOptimizer"
                :options="nnOptimizerOptions"
                placeholder="选择优化器"
              />
            </n-form-item>
            <n-form-item v-if="nnAdvancedOpen" label="Dropout">
              <n-input-number
                v-model:value="nnDropoutRate"
                :min="0"
                :max="0.95"
                :step="0.01"
                placeholder="输入Dropout比例"
              />
            </n-form-item>
          </template>
        </div>
        <n-form-item label="特征选择">
          <n-select
            v-model:value="selectedFeatures"
            multiple
            :options="featureOptions"
            :disabled="featureOptions.length === 0"
            placeholder="请先选择文件解析表头"
          />
        </n-form-item>
        <div class="flex justify-end gap-3">
          <n-button type="primary" @click="startTraining" round size="large">开始训练</n-button>
          <n-button @click="resetConfig" round size="large">重置配置</n-button>
          <n-button type="info" @click="resetToRecommended()" round size="large"
            >重置为推荐值</n-button
          >
        </div>
      </n-form>

      <!-- 训练过程 -->
      <n-tabs type="line" v-model:value="activeTab">
        <n-tab-pane name="process" tab="训练过程" display-directive="show">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <n-card title="训练状态" size="small" class="mb-4 border">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <n-text depth="2">当前状态: {{ trainingStatus }}</n-text>
                  <n-tag
                    :type="
                      trainingStatus === 'running'
                        ? 'warning'
                        : trainingStatus === 'completed'
                        ? 'success'
                        : 'default'
                    "
                  >
                    {{
                      trainingStatus === 'running'
                        ? '训练中'
                        : trainingStatus === 'completed'
                        ? '已完成'
                        : '未开始'
                    }}
                  </n-tag>
                </div>
                <n-text v-if="trainingStatus === 'running'" depth="2">
                  预计剩余时间: {{ estimatedTimeRemaining }}
                </n-text>
              </div>
              <n-progress
                type="line"
                :percentage="trainingProgress"
                :show-text="true"
                class="mt-3"
              />
            </n-card>

            <div class="grid grid-cols-2 gap-4 mb-4">
              <n-card title="训练过程损失 (trainLoss)" size="small" class="border">
                <div ref="lossChartRef" style="height: 300px"></div>
              </n-card>
              <n-card title="模型精度 (R²)" size="small" class="border">
                <div ref="accuracyChartRef" style="height: 300px"></div>
              </n-card>
            </div>

            <div class="grid grid-cols-3 gap-4">
              <n-card title="实时指标" size="small" class="border">
                <div class="grid grid-cols-2 gap-4">
                  <n-statistic
                    :label="modelType === 'random_forest' ? '当前树的数量' : '当前轮次'"
                    :value="currentEpoch"
                    :suffix="
                      ' / ' +
                      (modelType === 'random_forest'
                        ? treeCount
                        : modelType === 'gradient_boosting'
                        ? iterations
                        : epochs)
                    "
                  />
                  <n-statistic label="训练过程Loss" :value="trainingLoss" />
                  <n-statistic label="最终评估MSE" :value="finalMse" />
                  <n-statistic label="R²值" :value="(r2Score * 100).toFixed(2)" suffix="%">
                    <template #label>
                      <div class="flex items-center">
                        <span>R²值</span>
                        <n-tag type="info" size="small" class="ml-1">学术重点</n-tag>
                        <n-tag v-if="r2Score < 0" type="error" size="small" class="ml-1">
                          负R²
                        </n-tag>
                      </div>
                    </template>
                  </n-statistic>
                  <n-statistic label="平均绝对误差" :value="mae" suffix=" t/h">
                    <template #label>
                      <div class="flex items-center">
                        <span>平均绝对误差</span>
                        <n-tag type="error" size="small" class="ml-1">工业重点</n-tag>
                      </div>
                    </template>
                  </n-statistic>
                  <n-statistic label="均方根误差" :value="rmse" suffix=" t/h" />
                </div>
                <n-alert
                  v-if="r2Score < 0"
                  type="error"
                  class="mt-3"
                >
                  R² 为负值表示模型效果劣于“直接用均值预测”，当前模型可能失效或存在数据/特征问题。
                </n-alert>
                <n-alert
                  v-if="
                    trainingQualityInfo.droppedTargets !== null ||
                    (modelType === 'neural_network' && trainingQualityInfo.earlyStop)
                  "
                  type="warning"
                  class="mt-3"
                >
                  <div v-if="modelType === 'neural_network' && trainingQualityInfo.earlyStop">
                    早停已启用：bestEpoch={{ trainingQualityInfo.earlyStop.bestEpoch }}，valLoss={{
                      trainingQualityInfo.earlyStop.valLoss
                    }}
                  </div>
                  <div v-if="trainingQualityInfo.finalMse !== null">
                    最终评估MSE：{{ trainingQualityInfo.finalMse }}
                  </div>
                  <div v-if="trainingQualityInfo.droppedTargets !== null">
                    已丢弃目标缺失样本：{{ trainingQualityInfo.droppedTargets }}
                  </div>
                </n-alert>
              </n-card>

              <n-card title="训练日志" size="small" class="col-span-2 border">
                <div ref="logScrollRef" style="height: 300px; overflow-y: auto">
                  <div class="p-2">
                    <div v-for="(log, index) in trainingLogs" :key="index" class="mb-1">
                      <n-text>{{ log }}</n-text>
                    </div>
                  </div>
                </div>
              </n-card>
            </div>
          </div>
        </n-tab-pane>
        <n-tab-pane name="history" tab="训练历史" display-directive="show">
          <div class="p-4 rounded-lg border" style="background-color: var(--n-color); border-color: var(--n-border-color);">
            <div class="mb-4 flex justify-end">
              <n-popconfirm @positive-click="handleBatchDelete">
                <template #trigger>
                  <n-button type="error" :disabled="checkedRowKeys.length === 0">
                    批量删除
                  </n-button>
                </template>
                确定要删除选中的 {{ checkedRowKeys.length }} 条记录吗？
              </n-popconfirm>
            </div>
            <n-data-table
              v-model:checked-row-keys="checkedRowKeys"
              :columns="trainingHistoryColumns"
              :data="trainingHistoryList"
              :pagination="pagination"
              :row-key="(row) => row.id"
              size="small"
            />
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- 训练详情弹窗 -->
    <n-modal
      v-model:show="showDetailModal"
      preset="card"
      title="训练详情"
      :style="{ width: 'min(920px, 96vw)' }"
    >
      <n-scrollbar style="max-height: min(72vh, 720px); padding-right: 4px" v-if="currentDetail">
        <n-collapse :default-expanded-names="detailCollapseExpanded">
          <n-collapse-item title="基本信息" name="basic">
            <n-descriptions bordered size="small" :column="2">
              <n-descriptions-item label="记录编号">{{ currentDetail.id }}</n-descriptions-item>
              <n-descriptions-item label="模型类型">{{
                getModelTypeName(currentDetail.modelType)
              }}</n-descriptions-item>
              <n-descriptions-item label="开始时间">{{
                formatDate(currentDetail.startTime)
              }}</n-descriptions-item>
              <n-descriptions-item label="结束时间">{{
                formatDate(currentDetail.endTime)
              }}</n-descriptions-item>
              <n-descriptions-item label="状态">
                <n-tag
                  :type="
                    currentDetail.status === 'completed'
                      ? 'success'
                      : currentDetail.status === 'running'
                      ? 'warning'
                      : 'error'
                  "
                >
                  {{
                    currentDetail.status === 'completed'
                      ? '成功'
                      : currentDetail.status === 'running'
                      ? '训练中'
                      : '失败'
                  }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item v-if="currentDetail.message" label="训练信息">
                <span class="break-all">{{ currentDetail.message }}</span>
              </n-descriptions-item>
            </n-descriptions>
          </n-collapse-item>

          <n-collapse-item title="训练指标" name="metrics">
            <n-descriptions bordered size="small" :column="2">
              <n-descriptions-item label="R²值"
                >{{ ((currentDetail.r2Score || 0) * 100).toFixed(2) }}%</n-descriptions-item
              >
              <n-descriptions-item label="MSE">
                {{ Number((Number(currentDetail.rmse || 0) * Number(currentDetail.rmse || 0)).toFixed(4)) }}
              </n-descriptions-item>
              <n-descriptions-item label="MAE">{{ currentDetail.mae }} ℃</n-descriptions-item>
              <n-descriptions-item label="RMSE">{{ currentDetail.rmse }} ℃</n-descriptions-item>
              <n-descriptions-item v-if="currentDetail.trainingLoss != null" label="训练损失">
                {{ Number(currentDetail.trainingLoss).toFixed(6) }}
              </n-descriptions-item>
              <n-descriptions-item v-if="currentDetail.currentEpoch != null" label="当前轮次">
                {{ currentDetail.currentEpoch }}
              </n-descriptions-item>
            </n-descriptions>
          </n-collapse-item>

          <n-collapse-item title="数据与特征" name="data">
            <n-descriptions bordered size="small" :column="2">
              <n-descriptions-item
                v-for="([label, value], idx) in toDisplayEntries(formatTrainingDataParams(currentDetail))"
                :key="idx"
                :label="label"
              >
                <span class="break-all">{{ value }}</span>
              </n-descriptions-item>
            </n-descriptions>
            <div v-if="detailFeatureList.length" class="mt-3">
              <div class="mb-2 text-sm font-medium text-gray-600">特征</div>
              <n-space size="small" wrap>
                <n-tag v-for="f in detailFeatureList" :key="f" size="small" type="info" round>
                  {{ f }}
                </n-tag>
              </n-space>
            </div>
          </n-collapse-item>

          <n-collapse-item title="训练超参数" name="train">
            <n-descriptions bordered size="small" :column="2">
              <n-descriptions-item
                v-for="([label, value], idx) in toDisplayEntries(formatTrainingHyperParams(currentDetail))"
                :key="idx"
                :label="label"
              >
                {{ value }}
              </n-descriptions-item>
            </n-descriptions>
          </n-collapse-item>

          <n-collapse-item title="模型配置" name="config" v-if="currentDetail.modelConfig">
            <n-descriptions bordered size="small" :column="2">
              <n-descriptions-item
                v-for="([label, value], idx) in toDisplayEntries(
                  formatConfig(currentDetail.modelConfig, currentDetail.modelType)
                )"
                :key="idx"
                :label="label"
              >
                <span class="break-all">{{ value }}</span>
              </n-descriptions-item>
            </n-descriptions>
          </n-collapse-item>
        </n-collapse>
      </n-scrollbar>
      <template #footer>
        <div class="flex justify-end">
          <n-button @click="showDetailModal = false">关闭</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import {
    ref,
    h,
    computed,
    onMounted,
    onUnmounted,
    nextTick,
    watch,
    reactive,
    toRefs,
    resolveComponent,
  } from 'vue';
  import type { Ref } from 'vue';
  import { useRoute } from 'vue-router';
  import {
    useMessage,
    NButton,
    NPopconfirm,
    NModal,
    NDescriptions,
    NDescriptionsItem,
    NCollapse,
    NCollapseItem,
    NScrollbar,
    NSpace,
  } from 'naive-ui';
  import type { DataTableColumns } from 'naive-ui';
  import { dataManagementApi, optimizationApi } from '@/api/blast-furnace';
  import { useECharts } from '@/hooks/web/useECharts';
  import { ensureQuickStartRunId, setQuickStartRun } from '@/utils/quickStartRun';
  import { useDebounceFn } from '@vueuse/core';

  const message = useMessage();
  const route = useRoute();
  const nTagComponent = resolveComponent('NTag');

  const resolveRunId = () => {
    const queryRunId = String(route.query.runId || '').trim();
    const runId = queryRunId || ensureQuickStartRunId(24);
    if (queryRunId) setQuickStartRun(queryRunId);
    return runId;
  };

  // 模型类型
  const modelType = ref('neural_network');
  const suppressModelTypeRecommendedReset = ref(false);
  const modelTypeOptions = [
    { label: '神经网络', value: 'neural_network' },
    { label: '随机森林', value: 'random_forest' },
    { label: '梯度提升树', value: 'gradient_boosting' },
    { label: '高斯过程回归', value: 'gpr' },
  ];
  watch(
    modelType,
    (next, prev) => {
      if (suppressModelTypeRecommendedReset.value) return;
      if (next === prev) return;
      clearModelSpecificState(prev);
      resetToRecommended(true);
    },
    { flush: 'sync' }
  );

  // 训练数据上传
  const trainingDataFile = ref<File | null>(null);
  const trainingDataUploading = ref(false);
  const trainingDataUploadProgress = ref(0);
  const fileList = ref<any[]>([]);
  const snapshotFileList = ref<any[]>([]);
  const snapshotImporting = ref(false);
  const trainingDataId = ref<string | null>(null);
  const uploadedFileInfo = ref<UploadedFileInfo | null>(null);

  const targetVariable = ref('productionRate');
  const targetVariableOptions = [
    { label: '生产率', value: 'productionRate' },
    { label: '能耗', value: 'energyConsumption' },
    { label: '铁水温度', value: 'hotMetalTemperature' },
    { label: '硅含量', value: 'siliconContent' },
  ];

  const splitMode = ref('auto');
  const splitModeOptions = [
    { label: '自动（推荐）', value: 'auto' },
    { label: '按时间切分', value: 'time' },
    { label: '随机切分', value: 'random' },
  ];

  // 文件上传相关方法
  // 处理文件选择事件 - Naive UI的@change事件参数格式为 { fileList: any[] }
  const handleFileChange = async (changeEvent: any) => {
    const fileList = changeEvent?.fileList || [];
    if (fileList.length > 0) {
      trainingDataFile.value = fileList[fileList.length - 1].file;
      trainingDataId.value = null;
      uploadedFileInfo.value = null;
      featureOptions.value = [];
      selectedFeatures.value = [];
      await uploadTrainingDataIfNeeded();
    }
  };

  const handleSnapshotChange = async (changeEvent: any) => {
    const files = changeEvent?.fileList || [];
    if (!files.length) {
      return;
    }
    const snapshotFile: File | undefined = files[files.length - 1]?.file;
    if (!snapshotFile) {
      message.error('未读取到快照文件');
      return;
    }
    snapshotImporting.value = true;
    try {
      const res: any = await dataManagementApi.importSnapshot(snapshotFile);
      if (res.code !== 200) {
        message.error(res.msg || '导入快照失败');
        return;
      }
      const payload = res?.data || {};
      const fileInfo = payload?.fileInfo as UploadedFileInfo | undefined;
      trainingDataFile.value = null;
      trainingDataId.value = String(fileInfo?.fileId || payload?.id || '');
      uploadedFileInfo.value = fileInfo ?? null;
      applyFeatureOptionsFromUpload(fileInfo);
      const snapshotId = payload?.snapshotId ? String(payload.snapshotId) : '-';
      message.success(`快照导入成功（${snapshotId}）`);
    } catch (e: any) {
      message.error(`导入快照失败: ${e?.message || e}`);
    } finally {
      snapshotImporting.value = false;
    }
  };

  type ModelType = 'neural_network' | 'random_forest' | 'gradient_boosting' | 'gpr';
  type ModelForm = {
    epochs: number;
    batchSize: number;
    learningRate: number;
    treeCount: number;
    maxDepth: number;
    featureCount: number;
    iterations: number;
    baseComplexity: number;
    subsample: number;
    maxNodes: number;
    nodeSize: number;
    rfSubsample: number;
    rfNodeSize: number;
    gprLengthScale: number;
    gprNoiseVariance: number;
    hiddenLayers: number;
    neuronsPerLayer: string;
    activationFunction: string;
    nnAdvancedOpen: boolean;
    nnOptimizer: string;
    nnDropoutRate: number;
    nnLossFunction: string;
    gbdtLossFunction: string;
  };
  const baseModelFormDefaults: ModelForm = {
    epochs: 100,
    batchSize: 32,
    learningRate: 0.001,
    treeCount: 200,
    maxDepth: 20,
    featureCount: 4,
    iterations: 200,
    baseComplexity: 0.25,
    subsample: 0.8,
    maxNodes: 64,
    nodeSize: 16,
    rfSubsample: 1.0,
    rfNodeSize: 5,
    gprLengthScale: 1.0,
    gprNoiseVariance: 0.1,
    hiddenLayers: 3,
    neuronsPerLayer: '128,64,32',
    activationFunction: 'relu',
    nnAdvancedOpen: false,
    nnOptimizer: 'adam',
    nnDropoutRate: 0.1,
    nnLossFunction: 'mse',
    gbdtLossFunction: 'mse',
  };
  const modelSpecificDefaults: Record<ModelType, Partial<ModelForm>> = {
    neural_network: {
      hiddenLayers: 3,
      neuronsPerLayer: '128,64,32',
      activationFunction: 'relu',
      nnLossFunction: 'mse',
      nnOptimizer: 'adam',
      nnDropoutRate: 0.1,
      nnAdvancedOpen: false,
    },
    random_forest: {
      treeCount: 200,
      maxDepth: 20,
      featureCount: 4,
      rfSubsample: 1.0,
      rfNodeSize: 5,
    },
    gradient_boosting: {
      iterations: 200,
      learningRate: 0.01,
      maxDepth: 6,
      baseComplexity: 0.25,
      subsample: 0.8,
      maxNodes: 64,
      nodeSize: 16,
      gbdtLossFunction: 'mse',
    },
    gpr: {
      gprLengthScale: 1.0,
      gprNoiseVariance: 0.1,
      iterations: 200,
      batchSize: 32,
    },
  };
  const recommendedModelParams: Record<ModelType, Partial<ModelForm>> = {
    neural_network: {
      learningRate: 0.005,
      epochs: 2000,
      batchSize: 8,
      hiddenLayers: 3,
      neuronsPerLayer: '128,64,32',
      nnLossFunction: 'mse',
      nnOptimizer: 'adam',
      nnDropoutRate: 0.1,
    },
    random_forest: {
      treeCount: 200,
      maxDepth: 20,
      featureCount: 4,
      rfSubsample: 1.0,
      rfNodeSize: 5,
    },
    gradient_boosting: {
      iterations: 200,
      learningRate: 0.01,
      maxDepth: 6,
      subsample: 0.8,
      maxNodes: 64,
      nodeSize: 16,
      gbdtLossFunction: 'mse',
    },
    gpr: {
      gprLengthScale: 1.0,
      gprNoiseVariance: 0.1,
      iterations: 200,
      batchSize: 32,
    },
  };
  const modelForm = reactive<ModelForm>({ ...baseModelFormDefaults });
  const applyModelForm = (next: Partial<ModelForm>) => {
    Object.assign(modelForm, next);
  };
  const {
    epochs,
    batchSize,
    learningRate,
    treeCount,
    maxDepth,
    featureCount,
    iterations,
    baseComplexity,
    subsample,
    maxNodes,
    nodeSize,
    rfSubsample,
    rfNodeSize,
    gprLengthScale,
    gprNoiseVariance,
    hiddenLayers,
    neuronsPerLayer,
    activationFunction,
    nnAdvancedOpen,
    nnOptimizer,
    nnDropoutRate,
    nnLossFunction,
    gbdtLossFunction,
  } = toRefs(modelForm);
  const lossChartRef = ref<HTMLDivElement | null>(null);
  const accuracyChartRef = ref<HTMLDivElement | null>(null);
  const lossHistory = ref<number[]>([]);
  const r2ScoreHistory = ref<number[]>([]);
  const epochHistory = ref<number[]>([]);
  const trainingStartTime = ref<number | null>(null);
  const estimatedTimeRemaining = ref('');

  const { setOptions: setLossOptions, resize: resizeLossChart } = useECharts(
    lossChartRef as Ref<HTMLDivElement>
  );
  const { setOptions: setAccuracyOptions, resize: resizeAccuracyChart } = useECharts(
    accuracyChartRef as Ref<HTMLDivElement>
  );

  const setLossChartOptions = (
    seriesData: number[] = lossHistory.value,
    axisData: number[] = epochHistory.value
  ) => {
    setLossOptions({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', name: 'Epoch', data: axisData },
      yAxis: { type: 'value', name: 'trainLoss' },
      series: [
        {
          type: 'line',
          smooth: true,
          areaStyle: {},
          data: seriesData,
        },
      ],
    });
  };

  const setAccuracyChartOptions = (
    seriesData: number[] = r2ScoreHistory.value,
    axisData: number[] = epochHistory.value
  ) => {
    setAccuracyOptions({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', name: 'Epoch', data: axisData },
      yAxis: { type: 'value', name: 'R²' },
      series: [
        {
          type: 'line',
          smooth: true,
          areaStyle: {},
          data: seriesData,
        },
      ],
    });
  };

  const formatDuration = (milliseconds: number) => {
    if (!Number.isFinite(milliseconds) || milliseconds <= 0) {
      return '00:00:00';
    }
    const totalSeconds = Math.floor(milliseconds / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':');
  };

  const resizeProcessCharts = useDebounceFn(async () => {
    if (activeTab.value !== 'process') return;
    await nextTick();
    resizeLossChart();
    resizeAccuracyChart();
  }, 120);
  const handleWindowResize = () => {
    resizeProcessCharts();
  };
  let chartResizeObserver: ResizeObserver | null = null;
  const bindChartResizeObserver = () => {
    if (typeof ResizeObserver === 'undefined') return;
    if (chartResizeObserver) {
      chartResizeObserver.disconnect();
      chartResizeObserver = null;
    }
    chartResizeObserver = new ResizeObserver(() => {
      resizeProcessCharts();
    });
    if (lossChartRef.value) chartResizeObserver.observe(lossChartRef.value);
    if (accuracyChartRef.value) chartResizeObserver.observe(accuracyChartRef.value);
  };

  watch([lossChartRef, accuracyChartRef], () => {
    setLossChartOptions();
    setAccuracyChartOptions();
    bindChartResizeObserver();
  });

  const activeTab = ref('process');
  watch(
    activeTab,
    async (tab) => {
      if (tab !== 'process') return;
      await nextTick();
      setLossChartOptions();
      setAccuracyChartOptions();
      resizeProcessCharts();
      await loadTrainingLogs();
    },
    { flush: 'post' }
  );

  // 重置配置
  const resetConfig = () => {
    suppressModelTypeRecommendedReset.value = true;
    modelType.value = 'neural_network';
    trainingDataFile.value = null;
    snapshotFileList.value = [];
    trainingDataId.value = null;
    featureOptions.value = [];
    applyModelForm(baseModelFormDefaults);

    // 重置特征选择
    selectedFeatures.value = [];
    targetVariable.value = 'productionRate';
    suppressModelTypeRecommendedReset.value = false;
    message.success('重置训练配置成功');
  };

  const clearModelSpecificState = (type: string) => {
    if (!type) {
      return;
    }
    const defaults = modelSpecificDefaults[type as ModelType];
    if (defaults) applyModelForm(defaults);
  };

  // 特征选择
  const featureLabelMap: Record<string, string> = {
    temperature: '温度',
    pressure: '压力',
    windVolume: '风量',
    coalInjection: '喷煤量',
    materialHeight: '料面高度',
    gasFlow: '煤气流量',
    oxygenLevel: '氧气含量',
    energyConsumption: '能耗',
    hotMetalTemperature: '铁水温度',
    constantSignal: '常量信号',
    siliconContent: '硅含量',
    productionRate: '生产率',
    timestamp: '时间戳',
  };

  const defaultFeatureOptions = [
    { label: featureLabelMap.temperature, value: 'temperature' },
    { label: featureLabelMap.pressure, value: 'pressure' },
    { label: featureLabelMap.windVolume, value: 'windVolume' },
    { label: featureLabelMap.coalInjection, value: 'coalInjection' },
    { label: featureLabelMap.materialHeight, value: 'materialHeight' },
    { label: featureLabelMap.gasFlow, value: 'gasFlow' },
    { label: featureLabelMap.oxygenLevel, value: 'oxygenLevel' },
    { label: featureLabelMap.energyConsumption, value: 'energyConsumption' },
    { label: featureLabelMap.hotMetalTemperature, value: 'hotMetalTemperature' },
    { label: featureLabelMap.constantSignal, value: 'constantSignal' },
    { label: featureLabelMap.siliconContent, value: 'siliconContent' },
  ];

  const featureOptions = ref<{ label: string; value: string }[]>([]);
  const selectedFeatures = ref<string[]>([]);

  function getDefaultSelectedFeatures() {
    const preferredOrder = [
      'temperature',
      'pressure',
      'materialHeight',
      'gasFlow',
      'oxygenLevel',
      'hotMetalTemperature',
      'constantSignal',
      'siliconContent',
      'windVolume',
      'coalInjection',
    ];
    const available = new Set(featureOptions.value.map((item) => item.value));
    return preferredOrder.filter(
      (key) =>
        available.has(key) &&
        key !== targetVariable.value &&
        key !== 'productionRate' &&
        key !== 'energyConsumption'
    );
  }

  type UploadedFileInfo = {
    headers?: unknown;
    headerMapping?: unknown;
    fileId?: unknown;
  };

  function applyFeatureOptionsFromUpload(fileInfo: UploadedFileInfo | null | undefined) {
    const rawHeaders = Array.isArray(fileInfo?.headers) ? (fileInfo?.headers as unknown[]) : [];
    const headerMapping =
      fileInfo?.headerMapping && typeof fileInfo.headerMapping === 'object'
        ? (fileInfo.headerMapping as Record<string, unknown>)
        : {};

    const seen = new Set<string>();
    const nextOptions: { label: string; value: string }[] = [];

    for (const h of rawHeaders) {
      const header = String(h ?? '').trim();
      if (!header) continue;
      const mapped = headerMapping[header];
      const canonical = String(mapped ?? '').trim();
      if (!canonical) continue;
      if (
        canonical === targetVariable.value ||
        canonical === 'timestamp' ||
        canonical === 'productionRate' ||
        canonical === 'energyConsumption'
      )
        continue;
      if (!(canonical in featureLabelMap)) continue;
      if (seen.has(canonical)) continue;
      seen.add(canonical);
      nextOptions.push({ label: featureLabelMap[canonical] || header, value: canonical });
    }

    featureOptions.value = nextOptions;
    if (nextOptions.length === 0) {
      selectedFeatures.value = [];
      return;
    }

    const allowed = nextOptions.map((o) => o.value);
    const selected = selectedFeatures.value.filter((key) => allowed.includes(key));
    selectedFeatures.value = selected.length > 0 ? selected : getDefaultSelectedFeatures();
  }

  async function uploadTrainingDataIfNeeded() {
    if (!trainingDataFile.value) {
      return false;
    }
    if (trainingDataId.value) {
      return true;
    }
    trainingDataUploading.value = true;
    trainingDataUploadProgress.value = 0;
    try {
      const uploadResult: any = await dataManagementApi.uploadFile(trainingDataFile.value);
      const payload = uploadResult?.data || {};
      const fileInfo = payload?.fileInfo as UploadedFileInfo | undefined;
      trainingDataId.value = String(fileInfo?.fileId || payload?.id || '');
      uploadedFileInfo.value = fileInfo ?? null;
      applyFeatureOptionsFromUpload(fileInfo);
      trainingDataUploadProgress.value = 100;

      if (!trainingDataId.value) {
        message.error('文件解析失败');
        featureOptions.value = [];
        selectedFeatures.value = [];
        trainingDataId.value = null;
        return false;
      }

      if (featureOptions.value.length === 0) {
        message.error('文件中未识别到可用特征列');
        trainingDataId.value = null;
        return false;
      }
      return true;
    } catch (e: any) {
      message.error(`文件解析失败: ${e?.message || e}`);
      featureOptions.value = [];
      selectedFeatures.value = [];
      trainingDataId.value = null;
      return false;
    } finally {
      trainingDataUploading.value = false;
    }
  }

  watch(
    targetVariable,
    () => {
      selectedFeatures.value = selectedFeatures.value.filter(
        (k) => k !== targetVariable.value && k !== 'productionRate' && k !== 'energyConsumption'
      );
      if (uploadedFileInfo.value) {
        applyFeatureOptionsFromUpload(uploadedFileInfo.value);
      }
    },
    { flush: 'sync' }
  );

  // 训练状态
  const trainingStatus = ref('idle'); // idle, running, completed, failed
  const trainingProgress = ref(0);
  const currentEpoch = ref(0);
  const trainingLoss = ref(0.0);
  const r2Score = ref(0.0);
  const mae = ref(0.0);
  const rmse = ref(0.0);
  const finalMse = computed(() => Number((rmse.value * rmse.value).toFixed(4)));
  const trainingMessage = ref('');
  const currentTrainingId = ref<number | null>(null);
  let statusPollingTimer: number | null = null;
  const isStatusPolling = ref(false);
  const isStatusPollingInFlight = ref(false);

  // 训练日志
  const trainingLogs = ref<string[]>([]);

  const trainingQualityInfo = computed(() => {
    const msg = trainingMessage.value || '';
    const droppedMatch = msg.match(/丢弃目标缺失样本:\s*(\d+)/);
    const earlyStopMatch = msg.match(/早停:\s*bestEpoch=(\d+),\s*valLoss=([0-9.eE+\-]+)/);
    const mseMatch = msg.match(/mse=([0-9.eE+\-]+)/);
    return {
      droppedTargets: droppedMatch ? Number(droppedMatch[1]) : null,
      earlyStop: earlyStopMatch
        ? { bestEpoch: Number(earlyStopMatch[1]), valLoss: Number(earlyStopMatch[2]) }
        : null,
      finalMse: mseMatch ? Number(mseMatch[1]) : null,
    };
  });

  // 训练历史
  const trainingHistoryList = ref<any[]>([]);
  const checkedRowKeys = ref<number[]>([]);
  const showDetailModal = ref(false);
  const currentDetail = ref<any>(null);
  const detailCollapseExpanded = ['basic', 'metrics', 'data', 'train', 'config'];
  const pagination = ref({
    page: 1,
    pageSize: 10,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
    onChange: (page: number) => {
      pagination.value.page = page;
    },
    onUpdatePageSize: (pageSize: number) => {
      pagination.value.pageSize = pageSize;
      pagination.value.page = 1;
    },
  });

  // 日期格式化
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

  // 模型类型名称映射
  const getModelTypeName = (type: string) => {
    switch (type) {
      case 'neural_network':
        return '神经网络';
      case 'random_forest':
        return '随机森林';
      case 'gradient_boosting':
        return '梯度提升树';
      case 'gpr':
        return '高斯过程回归';
      default:
        return type;
    }
  };

  // 格式化配置信息
  const formatConfig = (config: any, type: string) => {
    const result: Record<string, any> = {};
    if (!config) return result;

    if (config.configName) result['配置名称'] = config.configName;

    if (type === 'neural_network') {
      if (config.activationFunction) result['激活函数'] = config.activationFunction;
      if (config.lossFunction) result['损失函数'] = config.lossFunction;
      if (config.optimizer) result['优化器'] = config.optimizer;
      if (config.hiddenLayers) result['隐藏层数'] = config.hiddenLayers;
      if (config.neuronsPerLayer) result['每层神经元'] = config.neuronsPerLayer;
      if (config.dropoutRate !== null && config.dropoutRate !== undefined) {
        result['Dropout'] = config.dropoutRate;
      }
    } else if (type === 'random_forest') {
      if (config.treeCount) result['树的数量'] = config.treeCount;
      if (config.maxDepth) result['最大深度'] = config.maxDepth;
      if (config.featureCount) result['特征采样数'] = config.featureCount;
      if (config.subsample !== null && config.subsample !== undefined) {
        result['子采样率 (Subsample)'] = config.subsample;
      }
      if (config.nodeSize) result['叶子最小样本数'] = config.nodeSize;
      if (config.maxNodes) result['最大节点数'] = config.maxNodes;
    } else if (type === 'gradient_boosting') {
      if (config.treeCount) result['迭代次数 (树的数量)'] = config.treeCount;
      if (config.maxDepth) result['最大深度'] = config.maxDepth;
      if (config.learningRate) result['学习率 (Shrinkage)'] = config.learningRate;
      if (config.baseComplexity !== null && config.baseComplexity !== undefined) {
        result['基础复杂度'] = config.baseComplexity;
      }
      result['子采样率 (Subsample)'] =
        config.subsample || (config.baseComplexity ? 0.5 + config.baseComplexity : 0.8);
      result['最大节点数'] = config.maxNodes || 64;
      result['叶子最小样本数'] = config.nodeSize || 16;
    } else if (type === 'gpr') {
      if (config.gprLengthScale) result['长度尺度'] = config.gprLengthScale;
      if (config.gprNoiseVariance) result['噪声水平'] = config.gprNoiseVariance;
    }

    return result;
  };

  const toDisplayEntries = (record: Record<string, any>) => {
    return Object.entries(record || {}).filter(([, value]) => value !== null && value !== undefined && value !== '');
  };

  const parseCsvList = (value: any) => {
    if (!value) return [];
    return String(value)
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);
  };

  const detailFeatureList = computed(() => {
    if (!currentDetail.value) return [];
    return parseCsvList(currentDetail.value.selectedFeatures);
  });

  const formatTrainingDataParams = (row: any) => {
    const result: Record<string, any> = {};
    if (!row) return result;
    if (row.targetVariable) result['目标变量'] = row.targetVariable;
    if (row.splitModeUsed || row.splitMode) result['数据切分方式'] = row.splitModeUsed || row.splitMode;
    if (row.splitRatio !== null && row.splitRatio !== undefined) result['切分比例'] = row.splitRatio;
    if (row.splitSeed !== null && row.splitSeed !== undefined) result['随机种子'] = row.splitSeed;
    if (row.splitHasTimestamp !== null && row.splitHasTimestamp !== undefined) {
      result['按时间字段切分'] = row.splitHasTimestamp ? '是' : '否';
    }
    if (row.customDataId) result['数据集ID'] = row.customDataId;
    if (row.runId) result['运行ID'] = row.runId;
    if (row.trainingData) result['数据来源'] = row.trainingData;
    const features = parseCsvList(row.selectedFeatures);
    if (features.length) result['特征数量'] = features.length;
    return result;
  };

  const formatTrainingHyperParams = (row: any) => {
    const result: Record<string, any> = {};
    if (!row) return result;
    const type = row.modelType;
    if (type === 'gpr') {
      if (row.epochs) result['迭代次数'] = row.epochs;
      if (row.batchSize) result['批次大小'] = row.batchSize;
      return result;
    }
    if (row.epochs) result['训练轮次'] = row.epochs;
    if (row.batchSize) result['批次大小'] = row.batchSize;
    if (row.learningRate !== null && row.learningRate !== undefined) result['学习率'] = row.learningRate;
    return result;
  };

  const trainingHistoryColumns: DataTableColumns<any> = [
    { type: 'selection' },
    {
      title: '序号',
      key: 'index',
      align: 'center',
      render: (_: any, index: number) => {
        return (pagination.value.page - 1) * pagination.value.pageSize + index + 1;
      },
    },
    {
      title: '模型类型',
      key: 'modelType',
      align: 'center',
      render: (row: any) =>
        h(nTagComponent, { type: 'info' }, { default: () => getModelTypeName(row.modelType) }),
    },
    {
      title: '开始时间',
      key: 'startTime',
      align: 'center',
      render: (row: any) => formatDate(row.startTime),
    },
    {
      title: '结束时间',
      key: 'endTime',
      align: 'center',
      render: (row: any) => formatDate(row.endTime),
    },
    {
      title: '状态',
      key: 'status',
      align: 'center',
      render: (row: any) =>
        h(
          nTagComponent,
          {
            type:
              row.status === 'completed'
                ? 'success'
                : row.status === 'running'
                ? 'processing'
                : 'error',
          },
          {
            default: () =>
              row.status === 'completed'
                ? '成功'
                : row.status === 'running'
                ? '训练中'
                : row.status === 'cancelled'
                ? '已取消'
                : '失败',
          }
        ),
    },
    {
      title: 'R²值(%)',
      key: 'r2Score',
      align: 'center',
      render: (row: any) => Number(((row.r2Score || 0) * 100).toFixed(2))
    },
    {
      title: 'MSE',
      key: 'mse',
      align: 'center',
      render: (row: any) => Number(((Number(row.rmse || 0) * Number(row.rmse || 0))).toFixed(4)),
    },
    { title: 'MAE(℃)', key: 'mae', align: 'center' },
    { title: 'RMSE(℃)', key: 'rmse', align: 'center' },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      render: (row: any) =>
        h(
          'div',
          {
            style: 'display: flex; justify-content: center; align-items: center; gap: 8px;',
          },
          [
            h(
              NButton,
              {
                type: 'primary',
                size: 'small',
                onClick: () => viewTrainingDetails(row),
              },
              { default: () => '详情' }
            ),
            h(
              NButton,
              {
                type: 'success',
                size: 'small',
                onClick: () => exportModel(row.id),
              },
              { default: () => '导出' }
            ),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => deleteTrainingHistory(row.id),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    {
                      size: 'small',
                      type: 'error',
                    },
                    { default: () => '删除' }
                  ),
                default: () => '确定要删除该训练历史记录吗？',
              }
            ),
          ]
        ),
    },
  ];

  const activationFunctionOptions = [
    { label: 'ReLU', value: 'relu' },
    { label: 'Sigmoid', value: 'sigmoid' },
    { label: 'Tanh', value: 'tanh' },
    { label: 'Leaky ReLU', value: 'leaky_relu' },
  ];
  const nnOptimizerOptions = [
    { label: 'Adam', value: 'adam' },
    { label: 'SGD', value: 'sgd' },
    { label: 'Nesterovs', value: 'nesterovs' },
    { label: 'RMSProp', value: 'rmsprop' },
  ];
  const lossFunctionOptions = [
    { label: '均方误差(MSE)', value: 'mse' },
    { label: '平均绝对误差(MAE)', value: 'mae' },
  ];
  const gbdtLossFunctionOptions = [
    { label: '均方误差(MSE)', value: 'mse' },
    { label: '平均绝对误差(MAE)', value: 'mae' },
    { label: 'Huber', value: 'huber' },
  ];

  // 获取学习率建议
  const getLearningRateSuggestion = () => {
    switch (activationFunction.value) {
      case 'sigmoid':
        return 'Sigmoid推荐0.001-0.005，当前推荐值：0.005';
      case 'tanh':
        return 'Tanh推荐0.001-0.01，当前推荐值：0.005';
      case 'relu':
        return 'ReLU推荐0.001-0.01，当前推荐值：0.005';
      case 'leaky_relu':
        return 'Leaky ReLU推荐0.01-0.05，当前推荐值：0.01';
      default:
        return '推荐0.001-0.01，当前推荐值：0.005';
    }
  };

  // 重置为推荐值
  const resetToRecommended = (silent = false) => {
    const next = recommendedModelParams[modelType.value as ModelType];
    if (next) applyModelForm(next);

    if (!silent) {
      message.success('已重置为推荐参数值');
    }
  };

  // 方法
  const validateNumberRange = (label: string, value: any, min: number, max?: number) => {
    const n = Number(value);
    if (Number.isNaN(n)) {
      return `${label} 不是有效数字`;
    }
    if (n < min) {
      return `${label} 不能小于 ${min}`;
    }
    if (typeof max === 'number' && n > max) {
      return `${label} 不能大于 ${max}`;
    }
    return null;
  };

  const validateModelParams = () => {
    switch (modelType.value) {
      case 'random_forest': {
        return (
          validateNumberRange('树的数量', treeCount.value, 5, 1000) ||
          validateNumberRange('最大深度', maxDepth.value, 5, 50) ||
          validateNumberRange('特征采样数', featureCount.value, 1, 8) ||
          validateNumberRange('叶子最小样本数', rfNodeSize.value, 1, 100) ||
          validateNumberRange('采样率', rfSubsample.value, 0.1, 1.0)
        );
      }
      case 'gradient_boosting': {
        return (
          validateNumberRange('迭代次数', iterations.value, 5, 1000) ||
          validateNumberRange('学习率', learningRate.value, 0.0001, 0.1) ||
          validateNumberRange('最大深度', maxDepth.value, 1, 20) ||
          validateNumberRange('子采样率', subsample.value, 0.1, 1.0) ||
          validateNumberRange('最大节点数', maxNodes.value, 2, 1000) ||
          validateNumberRange('叶子最小样本数', nodeSize.value, 1, 100) ||
          (!gbdtLossFunction.value ? '请选择损失函数' : null)
        );
      }
      case 'gpr': {
        return (
          validateNumberRange('长度尺度', gprLengthScale.value, 0.1, 10.0) ||
          validateNumberRange('噪声水平', gprNoiseVariance.value, 0.01, 0.5) ||
          validateNumberRange('集成模型数量', iterations.value, 5, 1000) ||
          validateNumberRange('子采样大小', batchSize.value, 1, 1000)
        );
      }
      case 'neural_network':
      default: {
        const base =
          validateNumberRange('训练轮数', epochs.value, 1, 10000) ||
          validateNumberRange('批次大小', batchSize.value, 1, 1000) ||
          validateNumberRange('学习率', learningRate.value, 0.0001, 0.1) ||
          validateNumberRange('隐藏层数量', hiddenLayers.value, 1, 10);
        if (base) return base;
        const layers = (neuronsPerLayer.value || '')
          .split(',')
          .map((s) => s.trim())
          .filter((s) => s.length > 0);
        if (layers.length !== Number(hiddenLayers.value)) {
          return '每层神经元数的数量需与隐藏层数量一致';
        }
        for (const item of layers) {
          const n = Number(item);
          if (!Number.isInteger(n) || n <= 0) {
            return '每层神经元数必须为正整数';
          }
        }
        if (!activationFunction.value) return '请选择激活函数';
        if (!nnLossFunction.value) return '请选择损失函数';
        if (!nnOptimizer.value) return '请选择优化器';
        return validateNumberRange('Dropout', nnDropoutRate.value, 0, 0.95);
      }
    }
  };

  const buildCleanTrainingPayload = () => {
    const trainingBaseParams: Record<string, any> = {
      modelType: modelType.value,
      trainingData: 'uploaded',
      targetVariable: targetVariable.value,
      selectedFeatures: selectedFeatures.value.join(','),
      customDataId: trainingDataId.value,
      splitMode: splitMode.value,
      runId: resolveRunId(),
    };

    const configBaseParams: Record<string, any> = {
      configName: `模型配置_${new Date().getTime()}`,
    };

    switch (modelType.value) {
      case 'random_forest':
        return {
          training: {
            ...trainingBaseParams,
          },
          config: {
            ...configBaseParams,
            treeCount: treeCount.value,
            maxDepth: maxDepth.value,
            featureCount: featureCount.value,
            subsample: rfSubsample.value,
            nodeSize: rfNodeSize.value,
          },
        };
      case 'gradient_boosting':
        return {
          training: {
            ...trainingBaseParams,
          },
          config: {
            ...configBaseParams,
            treeCount: iterations.value,
            maxDepth: maxDepth.value,
            learningRate: learningRate.value,
            subsample: subsample.value,
            maxNodes: maxNodes.value,
            nodeSize: nodeSize.value,
            lossFunction: gbdtLossFunction.value,
          },
        };
      case 'gpr':
        return {
          training: {
            ...trainingBaseParams,
            epochs: iterations.value,
            batchSize: batchSize.value,
          },
          config: {
            ...configBaseParams,
            gprLengthScale: gprLengthScale.value,
            gprNoiseVariance: gprNoiseVariance.value,
          },
        };
      case 'neural_network':
      default:
        return {
          training: {
            ...trainingBaseParams,
            epochs: epochs.value,
            batchSize: batchSize.value,
            learningRate: learningRate.value,
          },
          config: {
            ...configBaseParams,
            hiddenLayers: hiddenLayers.value,
            neuronsPerLayer: neuronsPerLayer.value,
            activationFunction: activationFunction.value,
            lossFunction: nnLossFunction.value,
            optimizer: nnOptimizer.value,
            dropoutRate: nnDropoutRate.value,
          },
        };
    }
  };

  const startTraining = async () => {
    try {
      if (!trainingDataId.value) {
        if (!trainingDataFile.value) {
          message.error('请先选择训练数据文件或导入快照');
          return;
        }
        const ok = await uploadTrainingDataIfNeeded();
        if (!ok || !trainingDataId.value) {
          return;
        }
      }
      if (selectedFeatures.value.length === 0) {
        message.error('请先选择特征');
        return;
      }

      const validationError = validateModelParams();
      if (validationError) {
        message.error(validationError);
        return;
      }

      const trainingParams = buildCleanTrainingPayload();

      // 调用后端API开始训练
      const response: any = await optimizationApi.modelTraining.startTraining(trainingParams);
      if (response.code === 200) {
        const trainingId = response.data.id;
        currentTrainingId.value = trainingId;
        trainingStatus.value = 'running';
        trainingStartTime.value = Date.now();
        lossHistory.value = [];
        r2ScoreHistory.value = [];
        epochHistory.value = [];
        trainingLogs.value = [];
        estimatedTimeRemaining.value = '00:00:00';
        setLossChartOptions([], []);
        setAccuracyChartOptions([], []);
        await nextTick();
        message.success('模型训练已开始');

        // 开始轮询训练状态
        startStatusPolling();
        await loadTrainingLogs();
        // 加载训练历史
        loadTrainingHistory();
      } else {
        message.error('模型训练启动失败: ' + response.msg);
      }
    } catch (error: any) {
      message.error('模型训练启动失败: ' + (error.message || '未知错误'));
    }
  };

  // 轮询训练状态
  const startStatusPolling = () => {
    stopStatusPolling();
    isStatusPolling.value = true;

    const pollOnce = async () => {
      if (!isStatusPolling.value) return;
      if (!currentTrainingId.value) {
        stopStatusPolling();
        return;
      }
      if (isStatusPollingInFlight.value) {
        statusPollingTimer = window.setTimeout(pollOnce, 1000);
        return;
      }
      isStatusPollingInFlight.value = true;
      try {
        const response: any = await optimizationApi.modelTraining.getTrainingStatus(
          currentTrainingId.value
        );
        if (response.code === 200) {
          const training = response.data;
          trainingStatus.value = training.status;
          trainingProgress.value = training.progress;
          currentEpoch.value = training.currentEpoch;
          trainingLoss.value = training.trainingLoss;
          r2Score.value = training.r2Score;
          mae.value = training.mae || 0.0;
          rmse.value = training.rmse || 0.0;
          trainingMessage.value = training.message || '';
          lossHistory.value.push(training.trainingLoss);
          r2ScoreHistory.value.push((training.r2Score || 0) * 100);
          epochHistory.value.push(training.currentEpoch);
          setLossChartOptions(lossHistory.value, epochHistory.value);
          setAccuracyChartOptions(r2ScoreHistory.value, epochHistory.value);

          const totalEpochs =
            modelType.value === 'random_forest'
              ? treeCount.value
              : modelType.value === 'gradient_boosting'
                ? iterations.value
                : modelType.value === 'gpr'
                  ? iterations.value
                  : epochs.value;
          const currentEpochValue = Number(training.currentEpoch) || 0;
          if (trainingStartTime.value && currentEpochValue > 0 && totalEpochs > 0) {
            const elapsed = Date.now() - trainingStartTime.value;
            const remainingMs =
              (elapsed / currentEpochValue) * Math.max(totalEpochs - currentEpochValue, 0);
            estimatedTimeRemaining.value = formatDuration(remainingMs);
          } else {
            estimatedTimeRemaining.value = '00:00:00';
          }

          if (
            training.status === 'completed' ||
            training.status === 'failed' ||
            training.status === 'cancelled'
          ) {
            await loadTrainingLogs();
            stopStatusPolling();
            loadTrainingHistory();
            return;
          }

          await loadTrainingLogs();
        }
      } catch (error) {
        console.error('获取训练状态失败:', error);
      } finally {
        isStatusPollingInFlight.value = false;
      }
      if (!isStatusPolling.value) return;
      statusPollingTimer = window.setTimeout(pollOnce, 1000);
    };

    statusPollingTimer = window.setTimeout(pollOnce, 0);
  };

  // 停止轮询
  const stopStatusPolling = () => {
    isStatusPolling.value = false;
    isStatusPollingInFlight.value = false;
    if (statusPollingTimer) {
      window.clearTimeout(statusPollingTimer);
      statusPollingTimer = null;
    }
  };

  // 加载训练日志
  const loadTrainingLogs = async () => {
    if (!currentTrainingId.value) return;

    try {
      const response: any = await optimizationApi.modelTraining.getTrainingLogs(
        currentTrainingId.value
      );
      if (response.code === 200) {
        const logs = Array.isArray(response.data) ? response.data : [];
        trainingLogs.value = logs.map((log: any) => {
          const timestamp = new Date(log.timestamp).toLocaleString('zh-CN');
          return `${timestamp} - ${log.message}`;
        });
      }
    } catch (error) {
      console.error('获取训练日志失败:', error);
    }
  };

  // 加载训练历史
  const loadTrainingHistory = async () => {
    try {
      const response: any = await optimizationApi.modelTraining.getTrainingHistory();
      if (response.code === 200) {
        trainingHistoryList.value = response.data || [];
      }
    } catch (error) {
      console.error('获取训练历史失败:', error);
    }
  };

  const restoreRunningTrainingIfNeeded = async () => {
    if (currentTrainingId.value) return;
    const list = trainingHistoryList.value || [];
    const running = list.find((item: any) => item?.status === 'running');
    if (!running?.id) return;

    currentTrainingId.value = running.id;
    trainingStatus.value = running.status;
    trainingProgress.value = Number(running.progress) || 0;
    currentEpoch.value = Number(running.currentEpoch) || 0;
    trainingLoss.value = Number(running.trainingLoss) || 0;
    r2Score.value = Number(running.r2Score) || 0;
    mae.value = Number(running.mae) || 0;
    rmse.value = Number(running.rmse) || 0;

    lossHistory.value = [];
    r2ScoreHistory.value = [];
    epochHistory.value = [];
    estimatedTimeRemaining.value = '00:00:00';

    const startTime = running.startTime ? new Date(running.startTime).getTime() : NaN;
    trainingStartTime.value = Number.isFinite(startTime) ? startTime : Date.now();

    stopStatusPolling();
    startStatusPolling();
    await loadTrainingLogs();
  };

  // 查看训练详情
  const viewTrainingDetails = (row: any) => {
    currentDetail.value = row;
    showDetailModal.value = true;
  };

  // 导出模型
  const exportModel = async (trainingId: number) => {
    try {
      const response: any = await optimizationApi.modelTraining.exportModel(trainingId);
      if (response.code === 200) {
        // 创建Blob对象
        const dataStr = JSON.stringify(response.data, null, 2);
        const blob = new Blob([dataStr], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);

        // 创建下载链接
        const link = document.createElement('a');
        link.href = url;
        link.download = `model_export_${trainingId}_${new Date().getTime()}.json`;
        document.body.appendChild(link);
        link.click();

        // 清理
        window.URL.revokeObjectURL(url);
        document.body.removeChild(link);
        message.success('模型导出成功');
      } else {
        message.error('模型导出失败: ' + (response.msg || '未知错误'));
      }
    } catch (error: any) {
      message.error('模型导出失败: ' + (error.message || '网络错误'));
    }
  };

  // 批量删除训练历史
  const handleBatchDelete = async () => {
    if (checkedRowKeys.value.length === 0) return;

    try {
      const response: any = await optimizationApi.modelTraining.deleteTrainingBatch(
        checkedRowKeys.value
      );
      if (response.code === 200) {
        message.success('批量删除成功');
        checkedRowKeys.value = []; // 清空选择
        loadTrainingHistory(); // 重新加载列表
      } else {
        message.error('批量删除失败: ' + (response.msg || '未知错误'));
      }
    } catch (error: any) {
      message.error('批量删除失败: ' + (error.message || '网络错误'));
    }
  };

  // 删除训练历史
  const deleteTrainingHistory = async (trainingId: number) => {
    try {
      const response: any = await optimizationApi.modelTraining.deleteTraining(trainingId);
      if (response.code === 200) {
        message.success('训练历史删除成功');
        // 重新加载训练历史
        loadTrainingHistory();
      } else {
        message.error('训练历史删除失败: ' + (response.msg || '未知错误'));
      }
    } catch (error: any) {
      message.error('训练历史删除失败: ' + (error.message || '网络错误'));
    }
  };

  // 组件挂载时加载训练历史
  onMounted(async () => {
    window.addEventListener('resize', handleWindowResize);
    bindChartResizeObserver();
    await loadTrainingHistory();
    await restoreRunningTrainingIfNeeded();
  });

  // 组件卸载时停止轮询
  onUnmounted(() => {
    window.removeEventListener('resize', handleWindowResize);
    if (chartResizeObserver) {
      chartResizeObserver.disconnect();
      chartResizeObserver = null;
    }
    stopStatusPolling();
  });
</script>

<style lang="less" scoped>
  .model-training {
    padding: 20px;
  }
</style>
