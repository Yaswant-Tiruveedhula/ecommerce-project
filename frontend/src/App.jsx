import { Route, Routes, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { ProtectedRoute, Layout } from "./components/Layout.jsx";
import { AdminLayout, AdminDashboardPage, AdminCategoriesPage, AdminProductsPage, AdminInventoryPage, AdminOrdersPage, AdminCustomersPage } from "./pages/AdminPages.jsx";
import { CartPage, CheckoutPage, AccountPage, OrdersPage, OrderDetailPage } from "./pages/CustomerPages.jsx";
import { AccessDeniedPage, LoginPage, NotFoundPage, RegisterPage } from "./pages/AuthPages.jsx";
import { HomePage, ProductsPage, ProductPage } from "./pages/StorePages.jsx";

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "instant" });
  }, [pathname]);
  return null;
}

function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="products/:slug" element={<ProductPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="access-denied" element={<AccessDeniedPage />} />

          <Route element={<ProtectedRoute />}>
            <Route path="account" element={<AccountPage />} />
            <Route path="cart" element={<CartPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/:id" element={<OrderDetailPage />} />
            <Route path="orders/:id/confirmation" element={<OrderDetailPage confirmation />} />
          </Route>

          <Route element={<ProtectedRoute admin />}>
            <Route path="admin" element={<AdminLayout />}>
              <Route index element={<AdminDashboardPage />} />
              <Route path="products" element={<AdminProductsPage />} />
              <Route path="categories" element={<AdminCategoriesPage />} />
              <Route path="inventory" element={<AdminInventoryPage />} />
              <Route path="orders" element={<AdminOrdersPage />} />
              <Route path="customers" element={<AdminCustomersPage />} />
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </>
  );
}

export default App;
