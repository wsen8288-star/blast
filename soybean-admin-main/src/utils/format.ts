export interface NumberFormatOptions {
  decimals?: number;
  fallback?: string;
  scientificUpper?: number;
  scientificLower?: number;
}

export function formatNumericDisplay(value: unknown, options: NumberFormatOptions = {}): string {
  const { decimals = 2, fallback = '-', scientificUpper = 1e9, scientificLower = 1e-4 } = options;
  if (value === null || value === undefined) {
    return fallback;
  }
  const numberValue = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(numberValue)) {
    return fallback;
  }
  const abs = Math.abs(numberValue);
  if (abs >= scientificUpper || (abs > 0 && abs < scientificLower)) {
    return numberValue.toExponential(Math.max(0, Math.min(8, decimals)));
  }
  return numberValue.toFixed(Math.max(0, Math.min(8, decimals)));
}
