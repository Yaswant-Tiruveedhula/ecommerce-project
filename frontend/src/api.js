import axios from "axios";

let accessToken = null;
let csrfToken = null;
let refreshRequest = null;

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  headers: {
    Accept: "application/json",
  },
  timeout: 10000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  if (csrfToken && !["get", "head", "options"].includes(config.method?.toLowerCase())) {
    config.headers["X-XSRF-TOKEN"] = csrfToken;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config;
    const path = request?.url || "";
    const isAuthRequest = path.includes("/auth/login")
      || path.includes("/auth/register")
      || path.includes("/auth/refresh");

    if (error.response?.status !== 401 || !request || request._retry || isAuthRequest) {
      return Promise.reject(error);
    }

    request._retry = true;
    try {
      refreshRequest ??= api.post("/auth/refresh").finally(() => {
        refreshRequest = null;
      });
      const { data } = await refreshRequest;
      accessToken = data.accessToken;
      request.headers.Authorization = `Bearer ${accessToken}`;
      return api(request);
    } catch (refreshError) {
      accessToken = null;
      window.dispatchEvent(new Event("auth:expired"));
      return Promise.reject(refreshError);
    }
  },
);

export function setAccessToken(token) {
  accessToken = token;
}

export function setCsrfToken(token) {
  csrfToken = token;
}

export default api;
