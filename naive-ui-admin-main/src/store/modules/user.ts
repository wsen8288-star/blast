import { defineStore } from 'pinia';
import { store } from '@/store';
import { ACCESS_TOKEN, CURRENT_USER } from '@/store/mutation-types';
import { ResultEnum } from '@/enums/httpEnum';
import { storage } from '@/utils/Storage';
import request from '@/utils/request'; // 使用封装好的 request 工具

export type UserInfoType = {
  // TODO: add your own data
  username: string;
  email: string;
};

export interface IUserState {
  token: string;
  username: string;
  welcome: string;
  avatar: string;
  permissions: any[];
  info: UserInfoType;
}

export const useUserStore = defineStore({
  id: 'app-user',
  state: (): IUserState => ({
    token: storage.get(ACCESS_TOKEN, ''),
    username: '',
    welcome: '',
    avatar: '',
    permissions: [],
    info: storage.get(CURRENT_USER, {}),
  }),
  getters: {
    getToken(): string {
      return this.token;
    },
    getAvatar(): string {
      return this.avatar;
    },
    getNickname(): string {
      return this.username;
    },
    getPermissions(): [any][] {
      return this.permissions;
    },
    getUserInfo(): UserInfoType {
      return this.info;
    },
  },
  actions: {
    setToken(token: string) {
      this.token = token;
    },
    setAvatar(avatar: string) {
      this.avatar = avatar;
    },
    setPermissions(permissions) {
      this.permissions = permissions;
    },
    setUserInfo(info: UserInfoType) {
      this.info = info;
    },
    // 登录
    async login(params: any) {
      try {
        // 使用封装好的 request 工具发送请求
        const json: any = await request({
          url: '/api/auth/login',
          method: 'post',
          data: {
            username: params.username,
            password: params.password,
            role: params.role,
          },
        });

        // 处理登录成功
        const { code, data } = json;
        if (code === ResultEnum.SUCCESS) {
          const ex = 7 * 24 * 60 * 60;
          storage.set(ACCESS_TOKEN, data.token, ex);
          storage.set(CURRENT_USER, data, ex);
          this.setToken(data.token);
          this.setUserInfo({
            username: data.username,
            email: data.email,
          });
        }

        return json;
      } catch (error) {
        console.error('登录错误:', error);
        throw error;
      }
    },

    // 获取用户信息
    async getInfo() {
      // 从存储中获取用户信息
      const userInfo = storage.get(CURRENT_USER, {});
      if (userInfo.username) {
        try {
          const res: any = await request({
            url: '/api/auth/authorities',
            method: 'get',
          });
          const list: string[] = res.data || [];
          this.setPermissions(list.map((v) => ({ label: v, value: v })));
        } catch (e) {
          this.setPermissions([]);
        }
        this.setUserInfo({
          username: userInfo.username,
          email: userInfo.email,
        });
        this.setAvatar('');
        return { ...userInfo, permissions: this.permissions };
      } else {
        throw new Error('getInfo: userInfo must be a non-null object !');
      }
    },

    // 登出
    async logout() {
      this.setPermissions([]);
      this.setUserInfo({ username: '', email: '' });
      storage.remove(ACCESS_TOKEN);
      storage.remove(CURRENT_USER);
    },
  },
});

// Need to be used outside the setup
export function useUser() {
  return useUserStore(store);
}
