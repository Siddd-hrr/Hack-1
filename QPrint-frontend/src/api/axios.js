import axios from 'axios';
import { useAuthStore } from '../store/authStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://qprint-gateway.onrender.com';

const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const original = err.config;
    if (original?.url?.includes('/auth/refresh')) {
      return Promise.reject(err);
    }
    if (err.response?.status === 401 && !original._retry) {
      original._retry = true;
      try {
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, {}, { withCredentials: true });
        const accessToken = data?.data?.accessToken;
        useAuthStore.getState().setAuth(accessToken, useAuthStore.getState().user);
        original.headers.Authorization = `Bearer ${accessToken}`;
        return api(original);
      } catch (refreshErr) {
        useAuthStore.getState().clearAuth();
      }
    }
    return Promise.reject(err);
  }
);

export { BASE_URL };
export default api;
