import { useDispatch, useSelector, type TypedUseSelectorHook } from 'react-redux';
import type { AppDispatch, RootState } from 'app/config/store';
import { ROLE_SUPER } from 'app/config/constants';
import { selectAuth } from './authSlice';

export const useAppDispatch: () => AppDispatch = useDispatch;
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;

export function useAuth() {
  const auth = useAppSelector(selectAuth);
  const roles = auth.admin?.roles ?? [];
  return {
    ...auth,
    roles,
    isAuthenticated: auth.status === 'authenticated',
    isRestoring: auth.status === 'restoring',
    isSuper: roles.includes(ROLE_SUPER),
    hasRole: (role: string) => roles.includes(role),
  };
}
