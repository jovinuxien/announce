import { DEFAULT_FEATURES, readFeatures } from './features';

describe('features', () => {
  it('defaults to chat on, trade and mocks off', () => {
    expect(DEFAULT_FEATURES).toEqual({
      trade: false,
      chat: true,
      mocks: false,
    });
    expect(readFeatures({ getItem: () => null })).toEqual(DEFAULT_FEATURES);
  });

  it('merges a browser override and survives bad JSON', () => {
    expect(readFeatures({ getItem: () => '{"mocks":true,"chat":false}' })).toEqual({ trade: false, chat: false, mocks: true });
    expect(readFeatures({ getItem: () => '{nope' })).toEqual(DEFAULT_FEATURES);
  });
});
