import { combineReducers, configureStore } from '@reduxjs/toolkit';
import { authReducer } from 'app/shared/auth/authSlice';
import { authApi } from 'app/shared/api/authApi';
import { adminApi } from 'app/shared/api/adminApi';

export const rootReducer = combineReducers({
  auth: authReducer,
  [authApi.reducerPath]: authApi.reducer,
  [adminApi.reducerPath]: adminApi.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;

export function makeStore(preloaded?: Partial<RootState>) {
  return configureStore({
    reducer: rootReducer,
    preloadedState: preloaded,
    middleware: (getDefault) => getDefault().concat(authApi.middleware, adminApi.middleware),
  });
}

export const store = makeStore();
export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore['dispatch'];
