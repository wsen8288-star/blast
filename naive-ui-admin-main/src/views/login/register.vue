<template>
  <div class="relative min-h-screen overflow-hidden bg-[#050505] text-white">
    <div class="absolute inset-0 z-0 opacity-[0.15] bg-cover bg-center bg-no-repeat transition-all duration-1000" style="background-image: url('https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?q=80&w=2070&auto=format&fit=crop'); mix-blend-mode: luminosity;"></div>
    <div class="absolute inset-0 z-0 bg-gradient-to-r from-[#0A0A0A] via-[#0A0A0A]/90 to-[#0A0A0A]/60"></div>
    <div class="absolute inset-0 z-0 bg-[linear-gradient(to_right,#ffffff03_1px,transparent_1px),linear-gradient(to_bottom,#ffffff03_1px,transparent_1px)] bg-[size:40px_40px]"></div>
    <div class="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      <div class="absolute -top-[10%] -right-[5%] h-[600px] w-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
      <div class="absolute -bottom-[20%] -left-[10%] h-[600px] w-[600px] rounded-full bg-orange-600/10 blur-[130px]" />
    </div>

    <div class="relative z-10 mx-auto flex min-h-screen w-full">
      
      <div class="hidden lg:flex lg:w-1/2 flex-col justify-center items-center border-r border-white/[0.05] p-12 relative overflow-hidden backdrop-blur-[2px]">
        <div class="absolute top-12 left-12 z-20 flex items-center gap-3 transform transition-all duration-1000 ease-out" :class="isLoaded ? 'translate-y-0 opacity-100' : '-translate-y-8 opacity-0'">
          <img :src="websiteConfig.loginImage" alt="Logo" class="h-10 w-10 rounded-xl bg-black/40 border border-white/10 p-1 backdrop-blur-md" />
          <span class="text-xl font-medium tracking-wide text-white drop-shadow-md">{{ websiteConfig.loginDesc || '高炉智能监控平台' }}</span>
        </div>
        <div class="absolute bottom-10 w-[600px] h-[300px] bg-orange-500/15 blur-[100px] rounded-[100%] pointer-events-none transition-all duration-1000" :class="isTyping ? 'bg-red-500/20 scale-110' : ''"></div>
        <div class="relative z-10 transform transition-all duration-1000 delay-150 ease-[cubic-bezier(0.16,1,0.3,1)]" :class="isLoaded ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0'">
          <AnimatedCharacters :is-typing="isTyping" :show-password="showPasswordState" :password-length="passwordLength" />
        </div>
      </div>

      <div class="flex w-full lg:w-1/2 items-center justify-center p-6 sm:p-12 lg:p-24 bg-transparent relative">
        <div class="w-full max-w-[460px] rounded-[2rem] bg-black/40 p-8 sm:p-12 border border-white/[0.08] shadow-[0_20px_60px_-15px_rgba(0,0,0,0.8)] backdrop-blur-xl relative z-10 transform transition-all duration-1000 delay-300 ease-[cubic-bezier(0.16,1,0.3,1)]" :class="isLoaded ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0'">
          
          <div class="mb-8 text-center lg:text-left">
            <h2 class="text-3xl font-semibold tracking-tight text-white mb-3">创建账号</h2>
            <p class="text-sm text-neutral-400">填写详细信息以注册并获取系统访问权限</p>
          </div>

          <n-form ref="formRef" label-placement="top" size="medium" :model="formInline" :rules="rules" require-mark-placement="right-hanging">
            
            <n-form-item label="账号角色" path="role">
              <n-select
                v-model:value="formInline.role"
                :options="roleOptions"
                placeholder="请选择申请角色"
                class="saas-input transition-all duration-300 hover:shadow-[0_0_15px_rgba(59,130,246,0.1)]"
                @focus="isTyping = true"
                @blur="isTyping = false"
              />
            </n-form-item>

            <n-form-item label="用户名" path="username">
              <n-input
                v-model:value="formInline.username"
                placeholder="设置登录用户名"
                class="saas-input transition-all duration-300 hover:shadow-[0_0_15px_rgba(59,130,246,0.1)]"
                @focus="isTyping = true"
                @blur="isTyping = false"
              />
            </n-form-item>

            <n-form-item label="电子邮箱" path="email">
              <n-input
                v-model:value="formInline.email"
                type="email"
                placeholder="name@company.com"
                class="saas-input transition-all duration-300 hover:shadow-[0_0_15px_rgba(59,130,246,0.1)]"
                @focus="isTyping = true"
                @blur="isTyping = false"
              />
            </n-form-item>

            <n-form-item label="设置密码" path="password">
              <n-input
                v-model:value="formInline.password"
                type="password"
                showPasswordOn="click"
                placeholder="不少于 6 位字符"
                class="saas-input transition-all duration-300 hover:shadow-[0_0_15px_rgba(59,130,246,0.1)]"
                @focus="isTyping = true"
                @blur="isTyping = false"
                @input="handlePasswordInput"
              >
                <template #password-visible-icon>
                  <div @click="togglePasswordVisibility" class="w-full h-full text-neutral-400 hover:text-white transition-colors">
                    <n-icon size="18"><EyeOutline v-if="showPasswordState" /><EyeOffOutline v-else /></n-icon>
                  </div>
                </template>
              </n-input>
            </n-form-item>

            <n-form-item label="确认密码" path="confirmPassword">
              <n-input
                v-model:value="formInline.confirmPassword"
                type="password"
                showPasswordOn="click"
                placeholder="请再次输入密码"
                class="saas-input transition-all duration-300 hover:shadow-[0_0_15px_rgba(59,130,246,0.1)]"
                @focus="isTyping = true"
                @blur="isTyping = false"
              />
            </n-form-item>

            <div class="mt-6 mb-4">
              <button type="button" @click="handleRegister" class="interactive-btn group relative flex w-full h-12 items-center justify-center gap-2 overflow-hidden rounded-xl bg-white text-base font-semibold text-black transition-all duration-300 hover:bg-gray-200 hover:shadow-[0_0_30px_rgba(255,255,255,0.2)] mb-4" :disabled="loading">
                <div class="absolute inset-0 flex h-full w-full justify-center [transform:skew(-12deg)_translateX(-150%)] group-hover:duration-1000 group-hover:[transform:skew(-12deg)_translateX(150%)]"><div class="relative h-full w-8 bg-white/40" /></div>
                <span v-if="loading" class="animate-pulse tracking-widest">注册中...</span>
                <span v-else class="relative z-10 flex items-center gap-2 transition-transform duration-300 group-hover:-translate-x-1">
                  注 册
                  <svg class="h-4 w-4 opacity-0 transition-all duration-300 group-hover:opacity-100 group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M14 5l7 7m0 0l-7 7m7-7H3" /></svg>
                </span>
              </button>
            </div>
          </n-form>

          <div class="mt-4 text-center text-sm text-neutral-400">
            已有系统账号？
            <router-link to="/login" class="font-medium text-white hover:text-blue-400 transition-colors border-b border-transparent hover:border-blue-400 pb-0.5">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref, onMounted, nextTick } from 'vue';
  import { useRouter } from 'vue-router';
  import { useMessage } from 'naive-ui';
  import { EyeOutline, EyeOffOutline } from '@vicons/ionicons5';
  import { websiteConfig } from '@/config/website.config';
  import request from '@/utils/request';
  import AnimatedCharacters from '@/components/AnimatedCharacters.vue';

  const isLoaded = ref(false);
  const isTyping = ref(false);
  const showPasswordState = ref(false);
  const passwordLength = ref(0);

  const formRef = ref();
  const message = useMessage();
  const loading = ref(false);

  const formInline = reactive({
    username: '', password: '', confirmPassword: '', email: '', role: '',
  });

  const handlePasswordInput = (val: string) => { passwordLength.value = val ? val.length : 0; };
  const togglePasswordVisibility = () => { showPasswordState.value = !showPasswordState.value; };

  const roleOptions = ref<Array<{ label: string; value: string }>>([]);

  const fetchRoles = async () => {
    try {
      const res: any = await request({ url: '/api/auth/roles', method: 'get' });
      if (res.code === 200) {
        roleOptions.value = res.data.map((role) => ({
          label: role === 'ADMIN' ? '系统管理员' : role === 'MANAGER' ? '厂区经理' : role === 'OPERATOR' ? '操作员' : role === 'USER' ? '普通用户' : role,
          value: role.toLowerCase(),
        }));
        if (roleOptions.value.length > 0) formInline.role = roleOptions.value[0].value;
      }
    } catch (error) {
      formInline.role = 'user';
    }
  };

  onMounted(() => {
    fetchRoles();
    nextTick(() => { setTimeout(() => { isLoaded.value = true; }, 50); });
  });

  const rules = {
    role: { required: true, message: '请选择角色', trigger: 'blur' },
    username: { required: true, message: '请输入用户名', trigger: 'blur' },
    email: { required: true, message: '请输入有效邮箱', trigger: 'blur', type: 'email' },
    password: { required: true, message: '请输入密码（最少 6 位）', trigger: 'blur', min: 6 },
    confirmPassword: {
      required: true,
      message: '请再次确认密码',
      trigger: 'blur',
      validator: (rule, value) => {
        if (!value) return new Error('请确认密码');
        if (value !== formInline.password) return new Error('两次输入的密码不一致');
        return true;
      },
    },
  };

  const router = useRouter();

  const handleRegister = async (e) => {
    e.preventDefault();
    formRef.value.validate(async (errors) => {
      if (!errors) {
        const { username, password, email, role } = formInline;
        loading.value = true;
        try {
          const response: any = await request({ url: '/api/auth/register', method: 'post', data: { username, password, email, role: role.toUpperCase() } });
          message.destroyAll();
          if (response.code === 200) {
            message.success('注册成功，正在跳转登录页...');
            setTimeout(() => { router.push('/login'); }, 1000);
          } else {
            message.error(response.msg || '注册失败');
          }
        } catch (error) {
          message.destroyAll();
          message.error((error as Error).message || '注册失败');
        } finally {
          loading.value = false;
        }
      } else {
        message.error('请填写完整的注册信息');
      }
    });
  };
</script>

<style lang="less" scoped>
:deep(.n-form-item-label) { --n-label-text-color: #A3A3A3 !important; font-weight: 500 !important; font-size: 0.85rem !important; padding-bottom: 6px !important; }
:deep(.saas-input) { --n-color: rgba(255, 255, 255, 0.05) !important; --n-text-color: #FFFFFF !important; --n-border: 1px solid rgba(255, 255, 255, 0.1) !important; --n-border-hover: 1px solid rgba(255, 255, 255, 0.25) !important; --n-border-focus: 1px solid #3B82F6 !important; --n-box-shadow-focus: 0 0 0 2px rgba(59, 130, 246, 0.3) !important; --n-placeholder-color: rgba(255, 255, 255, 0.3) !important; --n-border-radius: 12px !important; --n-height: 44px !important; }
:deep(.n-input--focus) { background-color: rgba(59, 130, 246, 0.08) !important; }
</style>