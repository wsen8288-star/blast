import axios from 'axios';
import { storage } from '@/utils/Storage';
import { ACCESS_TOKEN } from '@/store/mutation-types';

// 创建 axios 实例
const service = axios.create({
  baseURL: '/',
  timeout: 10000,
});

service.interceptors.request.use(
  (config) => {
    const token = storage.get(ACCESS_TOKEN);
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data;

    // 检查是否为 Blob 类型响应（用于文件下载）
    if (response.config.responseType === 'blob' || res instanceof Blob) {
      return res;
    }

    // 检查 res.code
    if (res.code === 200) {
      // 如果 code === 200，直接返回 res
      return res;
    } else {
      // 如果 code !== 200，使用 Promise.reject 抛出错误
      return Promise.reject(new Error(res.msg || '请求失败'));
    }
  },
  (error) => {
    // 网络错误处理
    return Promise.reject(error);
  }
);

export default service;
