import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { getToken, setToken } from "./api";
import { authApi } from "./endpoints";
import type { Role, UserView } from "./types";

interface AuthState {
  user: UserView | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  /** Convenience for the "is this worth rendering" checks scattered through the UI. */
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  // On first load, if we have a stored token, verify it and restore the user.
  // The token is checked against the server rather than decoded here: a JWT's
  // payload is readable by anyone, so believing our own copy of "role" would mean
  // trusting a value the browser can edit.
  useEffect(() => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi
      .me()
      .then((me) => setUser(me))
      .catch(() => {
        setToken(null);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  async function login(email: string, password: string): Promise<void> {
    const response = await authApi.login(email, password);
    setToken(response.token);
    setUser(response.user);
  }

  function logout(): void {
    setToken(null);
    setUser(null);
  }

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login,
      logout,
      hasRole: (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
