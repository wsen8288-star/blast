import { request } from '../request';
import { localStg } from '@/utils/storage';

function parseTokenUserName(token: string) {
  try {
    const payload = token.split('.')[1];
    if (!payload) return '';
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const base64 = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map(char => `%${`00${char.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    );
    const data = JSON.parse(json) as { sub?: string };
    return data.sub || '';
  } catch {
    return '';
  }
}

/**
 * Login
 *
 * @param userName User name
 * @param password Password
 */
export async function fetchLogin(userName: string, password: string, role = 'admin') {
  const result = await request<any>({
    url: '/api/auth/login',
    method: 'post',
    data: {
      username: userName,
      password,
      ...(role ? { role } : {})
    }
  });

  if (result.error) {
    return result;
  }

  return {
    ...result,
    data: {
      token: result.data?.token || result.data?.accessToken || '',
      refreshToken: result.data?.refreshToken || result.data?.token || result.data?.accessToken || ''
    } satisfies Api.Auth.LoginToken
  };
}

/** Get user info */
export async function fetchGetUserInfo() {
  const [authoritiesResult, rolesResult] = await Promise.all([
    request<string[]>({ url: '/api/auth/authorities' }),
    request<any>({ url: '/api/auth/roles' }).catch(() => null)
  ]);

  if (authoritiesResult.error) {
    return {
      data: null,
      error: authoritiesResult.error,
      response: authoritiesResult.response
    };
  }

  const token = localStg.get('token') || '';
  const userName = parseTokenUserName(token);
  const all = Array.isArray(authoritiesResult.data) ? authoritiesResult.data : [];
  const roleSet = new Set<string>();
  all.forEach(item => {
    if (item.startsWith('ROLE_')) {
      roleSet.add(item);
    }
  });
  if (rolesResult && !rolesResult.error && Array.isArray(rolesResult.data)) {
    rolesResult.data.forEach((item: any) => {
      if (typeof item === 'string') roleSet.add(item);
    });
  }
  const roles = Array.from(roleSet);
  const buttons = all.filter(item => !item.startsWith('ROLE_'));

  return {
    data: {
      userId: userName || 'admin',
      userName: userName || 'admin',
      roles,
      buttons
    } satisfies Api.Auth.UserInfo,
    error: null,
    response: authoritiesResult.response
  };
}

/**
 * Refresh token
 *
 * @param refreshToken Refresh token
 */
export function fetchRefreshToken(refreshToken: string) {
  return request<Api.Auth.LoginToken>({
    url: '/api/auth/refreshToken',
    method: 'post',
    data: {
      refreshToken
    }
  });
}

/**
 * return custom backend error
 *
 * @param code error code
 * @param msg error message
 */
export function fetchCustomBackendError(code: string, msg: string) {
  return request({ url: '/auth/error', params: { code, msg } });
}
