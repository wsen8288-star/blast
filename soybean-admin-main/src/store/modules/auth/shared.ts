import { localStg } from '@/utils/storage';
import { ACCESS_TOKEN, CURRENT_USER } from '@/store/mutation-types';

/** Get token */
export function getToken() {
  return localStg.get('token') || '';
}

/** Clear auth storage */
export function clearAuthStorage() {
  localStg.remove('token');
  localStg.remove('refreshToken');
  localStg.remove(ACCESS_TOKEN);
  localStg.remove(CURRENT_USER);
}
