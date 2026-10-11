import { DEFAULT_FEATURES, parseFeatureList, resolveFeatures, shouldStartMocksFor } from './features';

describe('features', () => {
  it('defaults trade and chat on, mocks off', () => {
    expect(DEFAULT_FEATURES).toEqual({ trade: true, chat: true, mocks: false });
    expect(resolveFeatures('', null)).toEqual({ trade: true, chat: true, mocks: false });
  });

  it('the stored value and the query override the defaults, the query last', () => {
    expect(resolveFeatures('', '{"trade":false,"chat":false}')).toEqual({ trade: false, chat: false, mocks: false });
    expect(resolveFeatures('?features=trade,mocks', '{"trade":false,"chat":false}')).toEqual({ trade: true, chat: false, mocks: true });
    expect(resolveFeatures('?features=', '{"trade":true}')).toEqual({ trade: false, chat: false, mocks: false });
    expect(resolveFeatures('', 'not json')).toEqual(DEFAULT_FEATURES);
  });

  it('parses a feature list and ignores unknown names', () => {
    expect(parseFeatureList(null)).toBeUndefined();
    expect(parseFeatureList('trade, bogus ,chat')).toEqual({ trade: true, chat: true, mocks: false });
  });

  it('starts the mock worker only with the explicit mocks flag, whatever the other flags say', () => {
    expect(shouldStartMocksFor({ trade: true, chat: false, mocks: false })).toBe(false);
    expect(shouldStartMocksFor({ trade: true, chat: true, mocks: false })).toBe(false);
    expect(shouldStartMocksFor({ trade: false, chat: false, mocks: true })).toBe(true);
  });
});
