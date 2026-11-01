import { useEffect, useState } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import api from "../api.js";
import { useAuth } from "../auth/AuthProvider.jsx";

export function Layout() {
  const { user, isAdmin, logout } = useAuth();
  const [cartCount, setCartCount] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    let active = true;
    const loadCart = () => {
      if (!user) {
        setCartCount(0);
        return;
      }
      api.get("/cart")
        .then(({ data }) => {
          if (active) setCartCount(data.itemCount);
        })
        .catch(() => {
          if (active) setCartCount(0);
        });
    };
    loadCart();
    window.addEventListener("cart:changed", loadCart);
    return () => {
      active = false;
      window.removeEventListener("cart:changed", loadCart);
    };
  }, [user, location.pathname]);

  async function handleLogout() {
    await logout();
    setMenuOpen(false);
  }

  return (
    <div className="app-shell">
      <div className="announcement">
        <span>Thoughtful finds for everyday living</span>
        <span className="announcement-note">Complimentary delivery over $75</span>
      </div>
      <header className="site-header container">
        <Link className="brand" to="/" aria-label="Common Goods home">
          common<span>goods</span>
        </Link>
        <button
          className="mobile-menu-button"
          type="button"
          aria-expanded={menuOpen}
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span />
          <span />
        </button>
        <nav className={`main-nav ${menuOpen ? "open" : ""}`} aria-label="Main navigation">
          <NavLink to="/products" onClick={() => setMenuOpen(false)}>Shop all</NavLink>
          <a href="/#story" onClick={() => setMenuOpen(false)}>Our approach</a>
          {user && <NavLink to="/orders" onClick={() => setMenuOpen(false)}>Orders</NavLink>}
          {isAdmin && <NavLink to="/admin" onClick={() => setMenuOpen(false)}>Admin</NavLink>}
        </nav>
        <div className="header-actions">
          {user ? (
            <>
              <Link className="account-link" to="/account" aria-label="Your account">
                <span className="account-name">{user.name.split(" ")[0]}</span>
                <span className="account-icon" aria-hidden="true">⌂</span>
              </Link>
              <button className="text-button signout-link" type="button" onClick={handleLogout}>Sign out</button>
            </>
          ) : (
            <Link className="account-link" to="/login">Sign in</Link>
          )}
          <Link className="cart-button" to="/cart" aria-label={`Shopping bag, ${cartCount} items`}>
            Bag <span>{cartCount}</span>
          </Link>
        </div>
      </header>
      <Outlet />
      <footer className="site-footer container">
        <Link className="footer-wordmark" to="/">common<span className="footer-brand-accent">goods</span></Link>
        <span>Made for the moments that make a day.</span>
        <span className="footer-legal">Thoughtfully chosen. Made to be lived with.</span>
      </footer>
    </div>
  );
}

export function ProtectedRoute({ admin = false }) {
  const { user, ready, isAdmin } = useAuth();
  const location = useLocation();
  if (!ready) return <div className="container page-state"><span className="spinner-border" role="status" /><p>Getting things ready…</p></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  if (admin && !isAdmin) return <Navigate to="/access-denied" replace />;
  return <Outlet />;
}
