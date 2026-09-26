import { NAMESPACES, resources } from './index';

function keys(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([k, v]) => (v && typeof v === 'object' ? keys(v as Record<string, unknown>, `${prefix}${k}.`) : [`${prefix}${k}`]));
}

describe('translations', () => {
  it.each(NAMESPACES)('da and sv hold the same keys as en in %s, with no missing marker', ns => {
    const en = keys(resources.en[ns] as Record<string, unknown>).sort();
    expect(keys(resources.da[ns] as Record<string, unknown>).sort()).toEqual(en);
    expect(keys(resources.sv[ns] as Record<string, unknown>).sort()).toEqual(en);
    for (const lang of ['en', 'da', 'sv'] as const) {
      expect(JSON.stringify(resources[lang][ns])).not.toContain('__MISSING__');
    }
  });
});
