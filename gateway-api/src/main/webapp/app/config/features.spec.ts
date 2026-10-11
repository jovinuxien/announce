import { DEFAULT_FEATURES, readFeatures } from './features';

describe('features', () => {
  it('defaults to trade and chat on, mocks off', () => {
    expect(DEFAULT_FEATURES).toEqual({
      trade: true,
      chat: true,
      mocks: false,
    });
    expect(readFeatures({ getItem: () => null })).toEqual(DEFAULT_FEATURES);
  });

  it('merges a browser override and survives bad JSON', () => {
    expect(readFeatures({ getItem: () => '{"mocks":true,"chat":false}' })).toEqual({ trade: true, chat: false, mocks: true });
    expect(readFeatures({ getItem: () => '{nope' })).toEqual(DEFAULT_FEATURES);
  });
});
