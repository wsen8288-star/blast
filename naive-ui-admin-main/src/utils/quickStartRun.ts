type QuickStartRun = {
  runId: string;
  startedAt: number;
};

const STORAGE_KEY = 'quick_start_run_v1';

const now = () => Date.now();

const createRunId = () => {
  const c: any = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  return `run_${now()}_${Math.random().toString(16).slice(2)}`;
};

export const getQuickStartRun = (windowHours = 24): QuickStartRun | null => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<QuickStartRun>;
    if (!parsed.runId || !parsed.startedAt) return null;
    const ageMs = now() - Number(parsed.startedAt);
    if (!Number.isFinite(ageMs) || ageMs < 0) return null;
    if (ageMs > windowHours * 3600_000) return null;
    return { runId: String(parsed.runId), startedAt: Number(parsed.startedAt) };
  } catch {
    return null;
  }
};

export const setQuickStartRun = (runId: string, startedAt = now()) => {
  const payload: QuickStartRun = { runId, startedAt };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
};

export const ensureQuickStartRunId = (windowHours = 24) => {
  const existing = getQuickStartRun(windowHours);
  if (existing?.runId) return existing.runId;
  const runId = createRunId();
  setQuickStartRun(runId);
  return runId;
};

export const clearQuickStartRun = () => {
  localStorage.removeItem(STORAGE_KEY);
};

