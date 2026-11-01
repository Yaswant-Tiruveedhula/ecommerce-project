import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import api, { setAccessToken, setCsrfToken } from "../api.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const acceptSession = useCallback((data) => {
    setAccessToken(data.accessToken);
    setUser(data.user);
    window.dispatchEvent(new Event("cart:changed"));
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    window.dispatchEvent(new Event("cart:changed"));
  }, []);

  useEffect(() => {
    let active = true;
    const expire = () => clearSession();
    window.addEventListener("auth:expired", expire);

    api.get("/auth/csrf")
      .then(({ data }) => {
        setCsrfToken(data.token);
      })
      .then(() => api.post("/auth/refresh"))
      .then(({ data }) => {
        if (active) acceptSession(data);
      })
      .catch(() => {
        if (active) clearSession();
      })
      .finally(() => {
        if (active) setReady(true);
      });

    return () => {
      active = false;
      window.removeEventListener("auth:expired", expire);
    };
  }, [acceptSession, clearSession]);

  const login = useCallback(async (credentials) => {
    const { data: csrf } = await api.get("/auth/csrf");
    setCsrfToken(csrf.token);
    const { data } = await api.post("/auth/login", credentials);
    acceptSession(data);
    return data.user;
  }, [acceptSession]);

  const register = useCallback(async (details) => {
    const { data: csrf } = await api.get("/auth/csrf");
    setCsrfToken(csrf.token);
    const { data } = await api.post("/auth/register", details);
    acceptSession(data);
    return data.user;
  }, [acceptSession]);

  const logout = useCallback(async () => {
    try {
      const { data: csrf } = await api.get("/auth/csrf");
      setCsrfToken(csrf.token);
      await api.post("/auth/logout");
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo(() => ({
    user,
    ready,
    isAdmin: user?.roles?.includes("ADMIN") ?? false,
    login,
    register,
    logout,
  }), [user, ready, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return value;
}
