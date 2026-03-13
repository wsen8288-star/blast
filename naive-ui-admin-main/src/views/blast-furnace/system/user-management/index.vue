<template>
  <div class="user-management min-h-[calc(100vh-80px)] w-full flex-1 p-4 md:p-6 overflow-hidden box-border space-y-4">
    <n-card :bordered="false" size="small" class="user-main-card">
      <div class="flex items-start justify-between">
        <div>
          <div class="text-base font-semibold" style="color: var(--n-text-color-1);">用户管理</div>
          <div class="mt-1 text-xs transition-colors duration-300" style="color: var(--n-text-color-3);">管理系统账号、角色与启用状态</div>
        </div>
        <div class="flex items-center gap-2">
          <n-button round secondary :loading="loading" @click="refresh">
            <template #icon>
              <n-icon><refresh-outline /></n-icon>
            </template>
            刷新
          </n-button>
          <n-button round type="primary" @click="openCreate">
            <template #icon>
              <n-icon><person-add /></n-icon>
            </template>
            新增用户
          </n-button>
        </div>
      </div>

      <div class="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
        <n-card size="small" :bordered="false" class="user-stat-card">
          <n-statistic label="用户总数" :value="pagination.itemCount" />
        </n-card>
        <n-card size="small" :bordered="false" class="user-stat-card">
          <n-statistic label="当前页启用" :value="enabledInPage" />
        </n-card>
        <n-card size="small" :bordered="false" class="user-stat-card">
          <n-statistic label="当前页禁用" :value="disabledInPage" />
        </n-card>
      </div>
    </n-card>

    <n-card :bordered="false" size="small" class="user-main-card">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="searchKeyword"
          clearable
          class="w-56"
          placeholder="搜索用户名/邮箱"
          @keyup.enter="searchUser"
        />
        <n-select
          v-model:value="roleFilter"
          clearable
          class="w-40"
          placeholder="角色"
          :options="roleOptions"
        />
        <n-select
          v-model:value="enabledFilter"
          clearable
          class="w-40"
          placeholder="状态"
          :options="enabledOptions"
        />
        <div class="flex items-center gap-2">
          <n-button round size="small" type="primary" :loading="loading" @click="searchUser">查询</n-button>
          <n-button round size="small" @click="resetFilters">重置</n-button>
        </div>
      </div>

      <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2 text-sm transition-colors duration-300" style="color: var(--n-text-color-3);">
          <n-tag v-if="checkedRowKeys.length" size="small" type="info">
            已选择 {{ checkedRowKeys.length }} 项
          </n-tag>
            <n-button v-if="checkedRowKeys.length" round size="small" quaternary @click="clearSelection">清空选择</n-button>
        </div>
        <div class="flex items-center gap-2">
          <n-dropdown
            v-if="checkedRowKeys.length"
            size="small"
            :options="batchActionOptions"
            @select="handleBatchAction"
          >
            <n-button size="small" secondary>
              批量操作
              <template #icon>
                <n-icon><chevron-down /></n-icon>
              </template>
            </n-button>
          </n-dropdown>
            <n-popconfirm v-if="checkedRowKeys.length" @positive-click="batchDelete">
            <template #trigger>
              <n-button round size="small" type="error" ghost>批量删除</n-button>
            </template>
            确认删除所选用户？
          </n-popconfirm>
        </div>
      </div>

      <div class="mt-3">
        <n-data-table
          remote
          size="small"
          :row-key="rowKey"
          :checked-row-keys="checkedRowKeys"
          @update:checked-row-keys="handleCheckedRowKeys"
          :columns="userColumns"
          :data="userList"
          :loading="loading"
          :pagination="pagination"
          :max-height="560"
        />
      </div>
    </n-card>

    <n-drawer v-model:show="showCreateDrawer" :width="460" placement="right">
      <n-drawer-content title="新增用户" closable>
        <n-alert type="info" :bordered="false" class="user-tip-alert">
          密码可留空，系统将默认设置为 123456；建议首次登录后修改密码
        </n-alert>
        <div class="mt-4">
          <n-form ref="createFormRef" :model="createForm" :rules="createRules" label-placement="top">
            <div class="grid grid-cols-1 gap-3">
              <n-form-item label="用户名" path="username">
                <n-input v-model:value="createForm.username" placeholder="例如：admin" />
              </n-form-item>
              <n-form-item label="邮箱" path="email">
                <n-auto-complete
                  v-model:value="createForm.email"
                  clearable
                  :options="buildEmailOptions(createForm.email)"
                  placeholder="例如：admin@example.com"
                />
              </n-form-item>
              <n-form-item label="角色" path="role">
                <n-select v-model:value="createForm.role" :options="roleOptions" placeholder="选择角色" />
              </n-form-item>
              <n-form-item label="初始密码" path="password">
                <n-input-group>
                  <n-input
                    v-model:value="createForm.password"
                    type="password"
                    placeholder="留空默认 123456"
                    show-password-on="click"
                  />
                  <n-button round @click="generatePassword">生成</n-button>
                </n-input-group>
              </n-form-item>
              <n-form-item label="启用状态" path="enabled">
                <div class="flex items-center justify-between w-full">
                  <div class="text-sm transition-colors duration-300" style="color: var(--n-text-color-3);">禁用后该用户无法登录</div>
                  <n-switch v-model:value="createForm.enabled" />
                </div>
              </n-form-item>
            </div>
          </n-form>
        </div>
        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button round @click="showCreateDrawer = false">取消</n-button>
            <n-button round type="primary" :loading="saving" @click="submitCreate">创建</n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="showEditDrawer" :width="460" placement="right">
      <n-drawer-content title="编辑用户" closable>
        <div class="flex items-center gap-3 rounded-lg border px-3 py-3 transition-colors duration-300" style="background-color: var(--n-color); border-color: var(--n-border-color);">
          <n-avatar :size="36" round>
            {{ String(editForm.username || '?').slice(0, 1).toUpperCase() }}
          </n-avatar>
          <div class="min-w-0">
            <div class="truncate text-sm font-medium" style="color: var(--n-text-color-1);">{{ editForm.username || '-' }}</div>
            <div class="truncate text-xs transition-colors duration-300" style="color: var(--n-text-color-3);">ID：{{ editForm.id }}</div>
          </div>
        </div>

        <div class="mt-4">
          <n-form ref="editFormRef" :model="editForm" :rules="editRules" label-placement="top">
            <div class="grid grid-cols-1 gap-3">
              <n-form-item label="邮箱" path="email">
                <n-auto-complete
                  v-model:value="editForm.email"
                  clearable
                  :options="buildEmailOptions(editForm.email)"
                  placeholder="例如：admin@example.com"
                />
              </n-form-item>
              <n-form-item label="角色" path="role">
                <n-select v-model:value="editForm.role" :options="roleOptions" placeholder="选择角色" />
              </n-form-item>
              <n-form-item label="启用状态" path="enabled">
                <div class="flex items-center justify-between w-full">
                  <div class="text-sm transition-colors duration-300" style="color: var(--n-text-color-3);">禁用后该用户无法登录</div>
                  <n-switch v-model:value="editForm.enabled" />
                </div>
              </n-form-item>
            </div>
          </n-form>
        </div>

        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button round @click="showEditDrawer = false">取消</n-button>
            <n-button round type="primary" :loading="editing" @click="submitEdit">保存</n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal
      v-model:show="showResetPasswordModal"
      preset="card"
      title="重置密码"
      class="w-[520px]"
    >
      <n-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-placement="top">
        <n-form-item label="用户" path="username">
          <n-input :value="resetForm.username" disabled />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input v-model:value="resetForm.newPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="确认密码" path="confirmPassword">
          <n-input v-model:value="resetForm.confirmPassword" type="password" show-password-on="click" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button round @click="showResetPasswordModal = false">取消</n-button>
          <n-button round secondary @click="fillDefaultPassword">默认 123456</n-button>
          <n-button round type="primary" :loading="resetting" @click="submitResetPassword">确认</n-button>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showBatchResetPasswordModal"
      preset="card"
      title="批量重置密码"
      class="w-[520px]"
    >
      <n-form ref="batchResetFormRef" :model="batchResetForm" :rules="batchResetRules" label-placement="top">
        <n-form-item label="已选择用户数">
          <n-input :value="String(checkedRowKeys.length)" disabled />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input v-model:value="batchResetForm.newPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="确认密码" path="confirmPassword">
          <n-input v-model:value="batchResetForm.confirmPassword" type="password" show-password-on="click" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button round @click="showBatchResetPasswordModal = false">取消</n-button>
          <n-button round secondary @click="fillBatchDefaultPassword">默认 123456</n-button>
          <n-button round type="primary" :loading="batchResetting" @click="submitBatchResetPassword">确认</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import type { DataTableColumns, FormInst, FormRules } from 'naive-ui';
  import {
    NAlert,
    NAutoComplete,
    NAvatar,
    NButton,
    NButtonGroup,
    NCard,
    NDataTable,
    NDrawer,
    NDrawerContent,
    NDropdown,
    NForm,
    NFormItem,
    NIcon,
    NInput,
    NInputGroup,
    NModal,
    NPopconfirm,
    NSelect,
    NStatistic,
    NSwitch,
    NTag,
    useMessage,
  } from 'naive-ui';
  import { PersonAdd, RefreshOutline, KeyOutline, ChevronDown, CreateOutline, TrashOutline } from '@vicons/ionicons5';
  import { systemApi } from '@/api/blast-furnace';

  const message = useMessage();

  type UserRow = {
    id: number;
    username: string;
    email: string;
    role: string;
    enabled: boolean;
  };

  type PageResult<T> = {
    content: T[];
    totalElements: number;
  };

  const loading = ref(false);
  const saving = ref(false);
  const resetting = ref(false);
  const editing = ref(false);
  const batchResetting = ref(false);
  const userList = ref<UserRow[]>([]);
  const checkedRowKeys = ref<number[]>([]);

  const roleOptions = ref<Array<{ label: string; value: string }>>([]);

  const searchKeyword = ref('');
  const roleFilter = ref<string | null>(null);
  const enabledFilter = ref<number | null>(null);
  const enabledOptions = [
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 },
  ];

  const showCreateDrawer = ref(false);
  const showEditDrawer = ref(false);
  const showResetPasswordModal = ref(false);
  const showBatchResetPasswordModal = ref(false);

  const createFormRef = ref<FormInst | null>(null);
  const createForm = ref<{
    username: string;
    password: string;
    email: string;
    role: string;
    enabled: boolean;
  }>({
    username: '',
    password: '',
    email: '',
    role: '',
    enabled: true,
  });

  const createRules: FormRules = {
    username: [{ required: true, message: '请输入用户名', trigger: ['blur', 'input'] }],
    email: [
      { required: true, message: '请输入邮箱', trigger: ['blur'] },
      {
        validator: (_rule, value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || '')),
        message: '邮箱格式不正确',
        trigger: ['blur'],
      },
    ],
    role: [{ required: true, message: '请选择角色', trigger: ['change'] }],
  };

  const emailDomains = [
    'qq.com',
    '163.com',
    '126.com',
    'gmail.com',
    'outlook.com',
    'hotmail.com',
    'icloud.com',
    'yahoo.com',
    'foxmail.com',
    'sina.com',
  ];

  const buildEmailOptions = (raw: string) => {
    const v = String(raw || '').trim();
    if (!v) return [];
    const lower = v.toLowerCase();

    const atIndex = lower.indexOf('@');
    const local = (atIndex === -1 ? v : v.slice(0, atIndex)).trim();
    const domainPart = (atIndex === -1 ? '' : lower.slice(atIndex + 1)).trim();
    if (!local) return [];

    const domains = emailDomains
      .filter((d) => (domainPart ? d.startsWith(domainPart) : true))
      .slice(0, 8);

    return domains.map((d) => {
      const value = `${local}@${d}`;
      return { label: value, value };
    });
  };

  const resetFormRef = ref<FormInst | null>(null);
  const resetForm = ref({
    id: 0,
    username: '',
    newPassword: '',
    confirmPassword: '',
  });

  const resetRules: FormRules = {
    newPassword: [{ required: true, message: '请输入新密码', trigger: ['blur', 'input'] }],
    confirmPassword: [
      { required: true, message: '请确认密码', trigger: ['blur', 'input'] },
      {
        validator: (_rule, value: string) => value === resetForm.value.newPassword,
        message: '两次输入的密码不一致',
        trigger: ['blur', 'input'],
      },
    ],
  };

  const pagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
    onChange: (page: number) => {
      pagination.page = page;
      fetchList();
    },
    onUpdatePageSize: (pageSize: number) => {
      pagination.pageSize = pageSize;
      pagination.page = 1;
      fetchList();
    },
  });

  const fetchList = async () => {
    loading.value = true;
    try {
      const keyword = searchKeyword.value.trim();
      const res = await systemApi.user.getList({
        page: pagination.page - 1,
        size: pagination.pageSize,
        keyword: keyword || undefined,
        role: roleFilter.value || undefined,
        enabled: enabledFilter.value === null ? undefined : (enabledFilter.value === 1),
      });
      const pageData = res.data as PageResult<UserRow>;
      userList.value = Array.isArray(pageData?.content) ? pageData.content : [];
      pagination.itemCount = Number(pageData?.totalElements || 0);
    } catch (e: any) {
      message.error(e?.msg || e?.message || '获取用户列表失败');
    } finally {
      loading.value = false;
    }
  };

  const fetchRoles = async () => {
    try {
      const res: any = await systemApi.role.getList();
      const roles = Array.isArray(res?.data) ? res.data : [];
      roleOptions.value = roles
        .filter((r) => r && r.roleCode && r.roleName)
        .map((r) => ({ label: String(r.roleName), value: String(r.roleCode) }));
    } catch (e: any) {
      roleOptions.value = [];
    }
  };

  const rowKey = (row: UserRow) => row.id;
  const handleCheckedRowKeys = (keys: Array<string | number>) => {
    checkedRowKeys.value = keys.map((k) => Number(k)).filter((n) => Number.isFinite(n));
  };
  const clearSelection = () => {
    checkedRowKeys.value = [];
  };

  const enabledInPage = computed(() => userList.value.filter((it) => it.enabled).length);
  const disabledInPage = computed(() => userList.value.filter((it) => !it.enabled).length);

  const roleLabel = (role: string) => roleOptions.value.find((it) => it.value === role)?.label || role || '-';
  const roleTagType = (role: string) => (role === 'ADMIN' ? 'warning' : 'info');

  const userColumns: DataTableColumns<UserRow> = [
    { type: 'selection' },
    { title: 'ID', key: 'id', width: 72 },
    {
      title: '用户',
      key: 'username',
      minWidth: 180,
      render: (row) =>
        h('div', { class: 'flex items-center gap-3' }, [
          h(NAvatar, { size: 32, round: true }, { default: () => String(row.username || '?').slice(0, 1).toUpperCase() }),
          h('div', { class: 'min-w-0' }, [
            // 修改点：去除了 text-slate-800，自适应字体颜色
            h('div', { class: 'truncate text-sm font-medium' }, row.username || '-'),
            h('div', { class: 'truncate text-xs transition-colors duration-300', style: 'color: var(--n-text-color-3);' }, row.email || '-'),
          ]),
        ]),
    },
    {
      title: '角色',
      key: 'role',
      width: 120,
      render: (row) => h(NTag, { size: 'small', type: roleTagType(row.role) }, { default: () => roleLabel(row.role) }),
    },
    {
      title: '状态',
      key: 'enabled',
      width: 120,
      render: (row) => h(NTag, { type: row.enabled ? 'success' : 'error', size: 'small' }, { default: () => (row.enabled ? '启用' : '禁用') }),
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      render: (row) =>
        h(NButtonGroup, null, {
          default: () => [
            h(
              NButton,
              { size: 'small', onClick: () => openEdit(row) },
              { default: () => [h(NIcon, null, { default: () => h(CreateOutline) }), h('span', { class: 'ml-1' }, '编辑')] }
            ),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => updateUserStatus(row),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    { size: 'small', type: row.enabled ? 'warning' : 'success' },
                    { default: () => (row.enabled ? '禁用' : '启用') }
                  ),
                default: () => `确认${row.enabled ? '禁用' : '启用'}用户 ${row.username}？`,
              }
            ),
            h(NButton, { size: 'small', onClick: () => openResetPassword(row) }, { default: () => [h(NIcon, null, { default: () => h(KeyOutline) }), h('span', { class: 'ml-1' }, '重置密码')] }),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => deleteUser(row),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    { size: 'small', type: 'error' },
                    { default: () => [h(NIcon, null, { default: () => h(TrashOutline) }), h('span', { class: 'ml-1' }, '删除')] }
                  ),
                default: () => `确认删除用户 ${row.username}？`,
              }
            ),
          ],
        }),
    },
  ];

  const searchUser = () => {
    pagination.page = 1;
    fetchList();
  };

  const resetFilters = () => {
    searchKeyword.value = '';
    roleFilter.value = null;
    enabledFilter.value = null;
    searchUser();
  };

  const refresh = () => {
    fetchList();
  };

  const openCreate = () => {
    createForm.value = { username: '', password: '', email: '', role: '', enabled: true };
    showCreateDrawer.value = true;
  };

  const generatePassword = () => {
    const p = `${Math.random().toString(36).slice(2, 6)}${Math.random().toString(36).slice(2, 6)}`;
    createForm.value.password = p;
  };

  const submitCreate = async () => {
    const ok = await createFormRef.value?.validate().then(() => true).catch(() => false);
    if (!ok) return;

    saving.value = true;
    try {
      await systemApi.user.add({
        username: createForm.value.username.trim(),
        password: createForm.value.password || undefined,
        email: createForm.value.email.trim(),
        role: createForm.value.role,
        enabled: createForm.value.enabled,
      });
      message.success('创建成功');
      showCreateDrawer.value = false;
      pagination.page = 1;
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '创建失败');
    } finally {
      saving.value = false;
    }
  };

  const updateUserStatus = async (row: UserRow) => {
    try {
      await systemApi.user.updateStatus(row.id, { enabled: !row.enabled });
      message.success('更新用户状态成功');
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '更新用户状态失败');
    }
  };

  const editFormRef = ref<FormInst | null>(null);
  const editForm = ref({
    id: 0,
    username: '',
    email: '',
    role: '',
    enabled: true,
  });

  const editRules: FormRules = {
    email: [
      { required: true, message: '请输入邮箱', trigger: ['blur'] },
      {
        validator: (_rule, value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || '')),
        message: '邮箱格式不正确',
        trigger: ['blur'],
      },
    ],
    role: [{ required: true, message: '请选择角色', trigger: ['change'] }],
  };

  const openEdit = (row: UserRow) => {
    editForm.value = { id: row.id, username: row.username, email: row.email, role: row.role, enabled: row.enabled };
    showEditDrawer.value = true;
  };

  const submitEdit = async () => {
    const ok = await editFormRef.value?.validate().then(() => true).catch(() => false);
    if (!ok) return;
    editing.value = true;
    try {
      await systemApi.user.update(editForm.value.id, {
        email: editForm.value.email.trim(),
        role: editForm.value.role,
        enabled: editForm.value.enabled,
      });
      message.success('保存成功');
      showEditDrawer.value = false;
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '保存失败');
    } finally {
      editing.value = false;
    }
  };

  const deleteUser = async (row: UserRow) => {
    try {
      await systemApi.user.delete(row.id);
      message.success('删除成功');
      clearSelection();
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '删除失败');
    }
  };

  const openResetPassword = (row: UserRow) => {
    resetForm.value = { id: row.id, username: row.username, newPassword: '', confirmPassword: '' };
    showResetPasswordModal.value = true;
  };

  const fillDefaultPassword = () => {
    resetForm.value.newPassword = '123456';
    resetForm.value.confirmPassword = '123456';
  };

  const submitResetPassword = async () => {
    const ok = await resetFormRef.value?.validate().then(() => true).catch(() => false);
    if (!ok) return;
    resetting.value = true;
    try {
      await systemApi.user.resetPassword(resetForm.value.id, { newPassword: resetForm.value.newPassword });
      message.success('密码已重置');
      showResetPasswordModal.value = false;
    } catch (e: any) {
      message.error(e?.msg || e?.message || '重置失败');
    } finally {
      resetting.value = false;
    }
  };

  const batchActionOptions = [
    { label: '批量启用', key: 'enable' },
    { label: '批量禁用', key: 'disable' },
    { label: '批量重置密码', key: 'resetPassword' },
  ];

  const handleBatchAction = (key: string) => {
    if (key === 'enable') batchEnable();
    if (key === 'disable') batchDisable();
    if (key === 'resetPassword') openBatchResetPassword();
  };

  const batchEnable = async () => {
    if (!checkedRowKeys.value.length) return;
    try {
      await systemApi.user.batchUpdateStatus({ ids: checkedRowKeys.value, enabled: true });
      message.success('批量启用成功');
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '批量启用失败');
    }
  };

  const batchDisable = async () => {
    if (!checkedRowKeys.value.length) return;
    try {
      await systemApi.user.batchUpdateStatus({ ids: checkedRowKeys.value, enabled: false });
      message.success('批量禁用成功');
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '批量禁用失败');
    }
  };

  const batchDelete = async () => {
    if (!checkedRowKeys.value.length) return;
    try {
      await Promise.all(checkedRowKeys.value.map((id) => systemApi.user.delete(id)));
      message.success('批量删除成功');
      clearSelection();
      fetchList();
    } catch (e: any) {
      message.error(e?.msg || e?.message || '批量删除失败');
    }
  };

  const batchResetFormRef = ref<FormInst | null>(null);
  const batchResetForm = ref({
    newPassword: '',
    confirmPassword: '',
  });

  const batchResetRules: FormRules = {
    newPassword: [{ required: true, message: '请输入新密码', trigger: ['blur', 'input'] }],
    confirmPassword: [
      { required: true, message: '请确认密码', trigger: ['blur', 'input'] },
      {
        validator: (_rule, value: string) => value === batchResetForm.value.newPassword,
        message: '两次输入的密码不一致',
        trigger: ['blur', 'input'],
      },
    ],
  };

  const openBatchResetPassword = () => {
    batchResetForm.value = { newPassword: '', confirmPassword: '' };
    showBatchResetPasswordModal.value = true;
  };

  const fillBatchDefaultPassword = () => {
    batchResetForm.value.newPassword = '123456';
    batchResetForm.value.confirmPassword = '123456';
  };

  const submitBatchResetPassword = async () => {
    if (!checkedRowKeys.value.length) return;
    const ok = await batchResetFormRef.value?.validate().then(() => true).catch(() => false);
    if (!ok) return;
    batchResetting.value = true;
    try {
      await systemApi.user.batchResetPassword({
        ids: checkedRowKeys.value,
        newPassword: batchResetForm.value.newPassword,
      });
      message.success('批量重置成功');
      showBatchResetPasswordModal.value = false;
    } catch (e: any) {
      message.error(e?.msg || e?.message || '批量重置失败');
    } finally {
      batchResetting.value = false;
    }
  };

  onMounted(async () => {
    await fetchRoles();
    fetchList();
  });
</script>

<style scoped>
  .user-main-card,
  .user-stat-card {
    border: 1px solid var(--n-border-color);
    border-radius: 12px;
    box-shadow: var(--n-box-shadow-1);
  }

  .user-tip-alert {
    border: 1px solid var(--n-border-color);
    border-radius: 10px;
  }

  .user-management :deep(.n-base-selection),
  .user-management :deep(.n-input),
  .user-management :deep(.n-auto-complete) {
    border-radius: 10px;
  }

  .user-management :deep(.n-drawer-content),
  .user-management :deep(.n-modal) {
    border-radius: 12px;
  }
</style>
