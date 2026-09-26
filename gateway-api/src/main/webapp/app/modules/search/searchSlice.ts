import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { searchApi } from 'app/shared/api/searchApi';
import { OcsFacet, OcsSortOption, SearchHit } from 'app/shared/model/search';
import { describeError } from 'app/shared/util/problem';
import { SearchState, toOcsParams, toSearchHit } from './searchState';

export interface SearchResult {
  matchCount: number;
  hits: SearchHit[];
  facets: OcsFacet[];
  sortOptions: OcsSortOption[];
  tookInMillis: number;
}

export interface SearchSliceState {
  key: string; // the query that produced `result`
  result: SearchResult | null;
  loading: boolean;
  error: string | null;
}

const initialState: SearchSliceState = { key: '', result: null, loading: false, error: null };

export const runSearch = createAsyncThunk<{ key: string; result: SearchResult }, SearchState, { rejectValue: string }>(
  'search/run',
  async (state, api) => {
    const params = toOcsParams(state);
    const key = params.toString();
    try {
      const res = await searchApi.search(params);
      const slice = res.slices?.[0];
      return {
        key,
        result: {
          matchCount: slice?.matchCount ?? 0,
          hits: (slice?.hits ?? []).map(toSearchHit),
          facets: slice?.facets ?? [],
          sortOptions: res.sortOptions ?? [],
          tookInMillis: res.tookInMillis,
        },
      };
    } catch (e) {
      return api.rejectWithValue(describeError(e, 'search failed'));
    }
  }
);

const searchSlice = createSlice({
  name: 'search',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(runSearch.pending, state => {
        state.loading = true;
        state.error = null;
      })
      .addCase(runSearch.fulfilled, (state, action) => {
        state.loading = false;
        state.key = action.payload.key;
        state.result = action.payload.result;
      })
      .addCase(runSearch.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload ?? 'search failed';
      });
  },
});

export default searchSlice.reducer;
