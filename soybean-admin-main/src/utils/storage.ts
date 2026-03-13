type StoreValue = string | number | boolean | object | null;

function encode(value: StoreValue) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
}

function decode<T = any>(raw: string | null, defaultValue?: T): T {
  if (raw === null || raw === '') return (defaultValue as T) ?? (raw as T);
  try {
    return JSON.parse(raw) as T;
  } catch {
    return raw as T;
  }
}

export const storage = {
  set(key: string, value: StoreValue, _expireSeconds?: number) {
    localStorage.setItem(key, encode(value));
  },
  get<T = any>(key: string, defaultValue?: T): T {
    return decode<T>(localStorage.getItem(key), defaultValue);
  },
  remove(key: string) {
    localStorage.removeItem(key);
  }
};

export const localStg = storage;
