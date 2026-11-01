import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider.jsx";
import { ErrorMessage, errorMessage } from "../components/Feedback.jsx";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const user = await login(values);
      navigate(location.state?.from?.pathname || (user.roles.includes("ADMIN") ? "/admin" : "/account"), { replace: true });
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="container auth-page">
      <div className="auth-card">
        <p className="eyebrow">Welcome back</p>
        <h1>Good to see<br /><em>you again.</em></h1>
        <p className="auth-intro">Sign in to pick up where you left off.</p>
        <ErrorMessage error={error} />
        <form onSubmit={submit} className="form-stack">
          <label className="form-label" htmlFor="login-email">Email address</label>
          <input id="login-email" type="email" autoComplete="email" required maxLength={254}
            value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} />
          <label className="form-label" htmlFor="login-password">Password</label>
          <input id="login-password" type="password" autoComplete="current-password" required
            value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} />
          <button className="button-primary" type="submit" disabled={busy}>
            {busy ? "Signing you in…" : "Sign in"}
          </button>
        </form>
        <p className="auth-switch">New around here? <Link to="/register">Create an account</Link></p>
      </div>
    </main>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({ name: "", email: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await register(values);
      navigate("/account", { replace: true });
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="container auth-page">
      <div className="auth-card">
        <p className="eyebrow">Come on in</p>
        <h1>A better day<br /><em>starts here.</em></h1>
        <p className="auth-intro">Create your account to save the things you love.</p>
        <ErrorMessage error={error} />
        <form onSubmit={submit} className="form-stack">
          <label className="form-label" htmlFor="register-name">Your name</label>
          <input id="register-name" autoComplete="name" required maxLength={120}
            value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} />
          <label className="form-label" htmlFor="register-email">Email address</label>
          <input id="register-email" type="email" autoComplete="email" required maxLength={254}
            value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} />
          <label className="form-label" htmlFor="register-password">Password</label>
          <input id="register-password" type="password" autoComplete="new-password" required minLength={10} maxLength={72}
            value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} />
          <span className="field-hint">Use at least 10 characters with an uppercase letter, a lowercase letter, and a number.</span>
          <button className="button-primary" type="submit" disabled={busy}>
            {busy ? "Creating your account…" : "Create account"}
          </button>
        </form>
        <p className="auth-switch">Already a member? <Link to="/login">Sign in</Link></p>
      </div>
    </main>
  );
}

export function AccessDeniedPage() {
  return (
    <main className="container page-state access-denied">
      <p className="eyebrow">Not this time</p>
      <h1>This space is<br /><em>for our team.</em></h1>
      <p>You do not have permission to view this page.</p>
      <Link className="button-primary button-inline" to="/">Back to the shop</Link>
    </main>
  );
}

export function NotFoundPage() {
  return (
    <main className="container page-state access-denied">
      <p className="eyebrow">A wrong turn</p>
      <h1>We can’t find<br /><em>that page.</em></h1>
      <p>The page may have moved or the link may be incorrect.</p>
      <Link className="button-primary button-inline" to="/products">Browse the collection</Link>
    </main>
  );
}
