import { configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import auth from 'app/shared/auth/authSlice';
import search from 'app/modules/search/searchSlice';

/** A fresh store; the app uses the default one below, the page tests make their own. */
export const makeStore = () =>
  configureStore({
    reducer: { auth, search },
  });

const store = makeStore();

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
export const useAppDispatch = () => useDispatch<AppDispatch>();
export default store;
