<template>
  <div
    class="w-full text-[#CBD5E1] p-4 md:p-6 box-border space-y-4"
  >
    <n-card :bordered="false" size="small" class="border">
      <div class="flex items-start justify-between">
        <div>
          <div class="text-base font-semibold text-[#F1F5F9]">角色管理</div>
          <div class="mt-1 text-xs text-[#94A3B8]">维护角色、分配权限并联动前后端鉴权</div>
        </div>
        <div class="flex items-center gap-2">
          <n-button secondary :loading="loading" @click="refresh">
            <template #icon>
              <n-icon><refresh-outline /></n-icon>
            </template>
            刷新
          </n-button>
          <n-button v-permission="['system:role:write']" type="primary" @click="openCreate">
            <template #icon>
              <n-icon><person-add /></n-icon>
            </template>
            新增角色
          </n-button>
        </div>
      </div>
    </n-card>

    <n-card :bordered="false" size="small" class="border">
      <div class="flex flex-wrap items-center gap-3">
        <n-input
          v-model:value="searchKeyword"
          clearable
          class="w-56"
          placeholder="搜索角色名称/标识"
          @keyup.enter="applySearch"
        />
        <div class="flex items-center gap-2">
          <n-button size="small" type="primary" :loading="loading" @click="applySearch">查询</n-button>
          <n-button size="small" @click="resetSearch">重置</n-button>
        </div>
      </div>

      <div class="mt-3">
        <n-data-table
          size="small"
          :row-key="rowKey"
          :checked-row-keys="checkedRowKeys"
          @update:checked-row-keys="handleCheckedRowKeys"
          :columns="roleColumns"
          :data="filteredRoles"
          :loading="loading"
          :max-height="560"
        />
      </div>

      <div class="mt-3 flex items-center justify-between">
        <div class="text-xs text-[#94A3B8]">
          共 {{ roleList.length }} 条
        </div>
        <div class="flex items-center gap-2">
          <n-dropdown v-if="checkedRowKeys.length" size="small" :options="batchOptions" @select="handleBatchSelect">
            <n-button size="small" secondary>
              批量操作
              <template #icon>
                <n-icon><chevron-down /></n-icon>
              </template>
            </n-button>
          </n-dropdown>
        </div>
      </div>
    </n-card>

    <n-drawer v-model:show="showEditDrawer" :width="460" placement="right">
      <n-drawer-content :title="drawerTitle" closable>
        <n-form ref="formRef" :model="formModel" :rules="formRules" label-placement="top">
          <div class="grid grid-cols-1 gap-3">
            <n-form-item label="角色名称" path="roleName">
              <n-input v-model:value="formModel.roleName" placeholder="例如：管理员" />
            </n-form-item>
            <n-form-item label="角色标识" path="roleCode">
              <n-input v-model:value="formModel.roleCode" placeholder="例如：ADMIN" :disabled="editingId > 0" />
            </n-form-item>
            <n-form-item label="角色描述" path="description">
              <n-input v-model:value="formModel.description" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" />
            </n-form-item>
          </div>
        </n-form>
        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button @click="showEditDrawer = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="submitForm">保存</n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal
      v-model:show="showPermissionDialog"
      preset="card"
      title="分配权限"
      class="w-[720px] text-[#F1F5F9]"
    >
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <div class="text-sm text-[#CBD5E1]">角色：{{ currentRole?.roleName }} ({{ currentRole?.roleCode }})</div>
          <n-button size="small" secondary :loading="permissionLoading" @click="loadPermissionData">刷新权限</n-button>
        </div>
        <n-tree
          checkable
          block-line
          :data="permissionTree"
          :checked-keys="checkedPermissionKeys"
          @update:checked-keys="checkedPermissionKeys = $event"
          :default-expand-all="true"
        />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="showPermissionDialog = false">取消</n-button>
          <n-button v-permission="['system:role:write']" type="primary" :loading="permissionSaving" @click="saveRolePermissions">
            确定
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, h, onMounted, ref, withDirectives } from 'vue';
  import type { DataTableColumns, FormInst, FormRules, TreeOption } from 'naive-ui';
  import { NButton, NButtonGroup, NIcon, useDialog, useMessage } from 'naive-ui';
  import { ChevronDown, PersonAdd, RefreshOutline } from '@vicons/ionicons5';
  import { systemApi } from '@/api/blast-furnace';
  import { permission as permissionDirective } from '@/directives/permission';

  type PermissionEntity = {
    id: number;
    parentId: number | null;
    permissionName: string;
    permissionCode: string;
    type: string;
    path: string | null;
  };

  type RoleEntity = {
    id: number;
    roleName: string;
    roleCode: string;
    description: string | null;
    createTime: string;
    updateTime: string;
    permissions?: PermissionEntity[];
  };

  type RowKey = string | number;

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const saving = ref(false);
  const permissionLoading = ref(false);
  const permissionSaving = ref(false);

  const roleList = ref<RoleEntity[]>([]);
  const searchKeyword = ref('');
  const appliedKeyword = ref('');

  const checkedRowKeys = ref<RowKey[]>([]);
  const showEditDrawer = ref(false);
  const editingId = ref(0);
  const formRef = ref<FormInst | null>(null);
  const formModel = ref<{ roleName: string; roleCode: string; description: string }>({
    roleName: '',
    roleCode: '',
    description: '',
  });

  const formRules: FormRules = {
    roleName: [{ required: true, message: '请输入角色名称', trigger: ['blur', 'input'] }],
    roleCode: [{ required: true, message: '请输入角色标识', trigger: ['blur', 'input'] }],
  };

  const showPermissionDialog = ref(false);
  const currentRole = ref<RoleEntity | null>(null);
  const permissionTree = ref<TreeOption[]>([]);
  const allPermissions = ref<PermissionEntity[]>([]);
  const checkedPermissionKeys = ref<(string | number)[]>([]);

  const batchOptions = computed(() => [{ label: '批量删除', key: 'batchDelete' }]);

  const drawerTitle = computed(() => (editingId.value > 0 ? '编辑角色' : '新增角色'));

  const filteredRoles = computed(() => {
    const key = appliedKeyword.value.trim().toLowerCase();
    if (!key) return roleList.value;
    return roleList.value.filter(
      (item) =>
        (item.roleName || '').toLowerCase().includes(key) ||
        (item.roleCode || '').toLowerCase().includes(key)
    );
  });

  const rowKey = (row: RoleEntity) => row.id;

  function buildTree(items: PermissionEntity[]): TreeOption[] {
    const map = new Map<number, TreeOption & { _parentId: number | null }>();
    for (const p of items) {
      map.set(p.id, {
        key: p.id,
        label: `${p.permissionName} (${p.permissionCode})`,
        children: [],
        _parentId: p.parentId ?? null,
      });
    }
    const roots: TreeOption[] = [];
    for (const node of map.values()) {
      const parentId = (node as any)._parentId as number | null;
      if (parentId && map.has(parentId)) {
        const parent = map.get(parentId)!;
        (parent.children as TreeOption[]).push(node);
      } else {
        roots.push(node);
      }
    }
    return roots;
  }

  async function refresh() {
    loading.value = true;
    try {
      const res: any = await systemApi.role.getList();
      roleList.value = res.data || [];
    } catch (e: any) {
      message.error(e?.message || '获取角色列表失败');
    } finally {
      loading.value = false;
    }
  }

  function applySearch() {
    appliedKeyword.value = searchKeyword.value;
  }

  function resetSearch() {
    searchKeyword.value = '';
    appliedKeyword.value = '';
  }

  function openCreate() {
    editingId.value = 0;
    formModel.value = { roleName: '', roleCode: '', description: '' };
    showEditDrawer.value = true;
  }

  function openEdit(role: RoleEntity) {
    editingId.value = role.id;
    formModel.value = {
      roleName: role.roleName || '',
      roleCode: role.roleCode || '',
      description: role.description || '',
    };
    showEditDrawer.value = true;
  }

  async function submitForm() {
    const form = formRef.value;
    if (!form) return;
    try {
      await form.validate();
    } catch {
      return;
    }
    saving.value = true;
    try {
      if (editingId.value > 0) {
        await systemApi.role.update(editingId.value, {
          roleName: formModel.value.roleName,
          roleCode: formModel.value.roleCode,
          description: formModel.value.description,
        });
      } else {
        await systemApi.role.create({
          roleName: formModel.value.roleName,
          roleCode: formModel.value.roleCode,
          description: formModel.value.description,
        });
      }
      showEditDrawer.value = false;
      message.success('保存成功');
      await refresh();
    } catch (e: any) {
      message.error(e?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  }

  function confirmDelete(role: RoleEntity) {
    dialog.warning({
      title: '删除角色',
      content: `确认删除角色 ${role.roleName} (${role.roleCode})？`,
      positiveText: '删除',
      negativeText: '取消',
      async onPositiveClick() {
        try {
          await systemApi.role.delete(role.id);
          message.success('删除成功');
          await refresh();
        } catch (e: any) {
          message.error(e?.message || '删除失败');
        }
      },
    });
  }

  async function loadPermissionData() {
    if (!currentRole.value) return;
    permissionLoading.value = true;
    try {
      const [permRes, rolePermRes]: any[] = await Promise.all([
        systemApi.permission.getList(),
        systemApi.role.getPermissions(currentRole.value.id),
      ]);
      allPermissions.value = permRes.data || [];
      permissionTree.value = buildTree(allPermissions.value);
      const rolePerms: PermissionEntity[] = rolePermRes.data || [];
      checkedPermissionKeys.value = rolePerms.map((p) => p.id);
    } catch (e: any) {
      message.error(e?.message || '加载权限失败');
    } finally {
      permissionLoading.value = false;
    }
  }

  async function openPermission(role: RoleEntity) {
    currentRole.value = role;
    showPermissionDialog.value = true;
    await loadPermissionData();
  }

  async function saveRolePermissions() {
    if (!currentRole.value) return;
    permissionSaving.value = true;
    try {
      const ids = checkedPermissionKeys.value.map((k) => Number(k)).filter((v) => Number.isFinite(v));
      await systemApi.role.setPermissions(currentRole.value.id, ids);
      message.success('分配权限成功');
      showPermissionDialog.value = false;
      await refresh();
    } catch (e: any) {
      message.error(e?.message || '分配权限失败');
    } finally {
      permissionSaving.value = false;
    }
  }

  function handleCheckedRowKeys(keys: RowKey[]) {
    checkedRowKeys.value = keys;
  }

  async function batchDelete() {
    const ids = checkedRowKeys.value
      .map((k: RowKey) => Number(k))
      .filter((n: number) => Number.isFinite(n));
    if (!ids.length) return;
    try {
      await Promise.all(ids.map((id: number) => systemApi.role.delete(id)));
      message.success('批量删除成功');
      checkedRowKeys.value = [];
      await refresh();
    } catch (e: any) {
      message.error(e?.message || '批量删除失败');
    }
  }

  function handleBatchSelect(key: string) {
    if (key === 'batchDelete') {
      batchDelete();
    }
  }

  const roleColumns: DataTableColumns<RoleEntity> = [
    { type: 'selection' },
    { title: 'ID', key: 'id', width: 80 },
    { title: '角色名称', key: 'roleName', minWidth: 140 },
    { title: '角色标识', key: 'roleCode', minWidth: 140 },
    { title: '描述', key: 'description', minWidth: 160 },
    { title: '更新时间', key: 'updateTime', minWidth: 180 },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render(row) {
        const editBtn = withDirectives(
          h(
            NButton,
            { size: 'small', onClick: () => openEdit(row) },
            { default: () => '编辑' }
          ),
          [[permissionDirective as any, ['system:role:write']]]
        );
        const permBtn = withDirectives(
          h(
            NButton,
            { size: 'small', onClick: () => openPermission(row) },
            { default: () => '权限' }
          ),
          [[permissionDirective as any, ['system:role:write']]]
        );
        const deleteBtn = withDirectives(
          h(
            NButton,
            { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(row) },
            { default: () => '删除' }
          ),
          [[permissionDirective as any, ['system:role:write']]]
        );
        return h(NButtonGroup, null, { default: () => [editBtn, permBtn, deleteBtn] });
      },
    },
  ];

  onMounted(() => {
    refresh();
  });
</script>
