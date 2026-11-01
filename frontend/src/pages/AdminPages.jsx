import { useEffect, useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import api from "../api.js";
import { ErrorMessage, EmptyState, LoadingState, errorMessage } from "../components/Feedback.jsx";
import { formatPrice } from "../components/ProductCard.jsx";

const emptyProduct = {
  categoryId: "", sku: "", name: "", slug: "", description: "",
  price: "", discountPercent: "0", stockQuantity: "0", imageUrl: "", active: true,
};

function useAdminResource(path) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const reload = async () => {
    setError("");
    try {
      const { data: result } = await api.get(path);
      setData(result);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally { setLoading(false); }
  };
  useEffect(() => { reload(); }, [path]);
  return { data, setData, loading, error, setError, reload };
}

export function AdminLayout() {
  return (
    <main className="container admin-page">
      <div className="admin-heading"><div><p className="eyebrow">Store operations</p><h1>The <em>back room.</em></h1></div>
        <Link className="quiet-link" to="/">View storefront ↗</Link></div>
      <nav className="admin-nav" aria-label="Administration">
        <NavLink end to="/admin">Overview</NavLink>
        <NavLink to="/admin/products">Products</NavLink>
        <NavLink to="/admin/categories">Categories</NavLink>
        <NavLink to="/admin/inventory">Inventory</NavLink>
        <NavLink to="/admin/orders">Orders</NavLink>
        <NavLink to="/admin/customers">Customers</NavLink>
      </nav>
      <Outlet />
    </main>
  );
}

export function AdminDashboardPage() {
  const { data, loading, error } = useAdminResource("/admin/dashboard");
  if (loading) return <LoadingState label="Gathering the numbers…" />;
  return (
    <section className="admin-section">
      <ErrorMessage error={error} />
      {data && <div className="stat-grid">
        <article className="stat-card"><span>Customers</span><strong>{data.customers}</strong></article>
        <article className="stat-card"><span>Products</span><strong>{data.products}</strong></article>
        <article className="stat-card"><span>Orders</span><strong>{data.orders}</strong></article>
        <article className="stat-card"><span>Delivered revenue</span><strong>{formatPrice(data.revenue)}</strong><small>Based on delivered orders only</small></article>
        <article className="stat-card stat-highlight"><span>Orders to review</span><strong>{data.pendingOrders}</strong></article>
      </div>}
      <div className="admin-shortcuts">
        <Link to="/admin/products">Manage products <span>↗</span></Link>
        <Link to="/admin/orders">Review order queue <span>↗</span></Link>
        <Link to="/admin/inventory">Adjust stock <span>↗</span></Link>
      </div>
    </section>
  );
}

export function AdminCategoriesPage() {
  const resource = useAdminResource("/admin/categories");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);

  async function create(event) {
    event.preventDefault();
    resource.setError("");
    setBusy(true);
    try {
      await api.post("/admin/categories", { name, description });
      setName("");
      setDescription("");
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
    finally { setBusy(false); }
  }

  async function deactivate(category) {
    if (!window.confirm(`Deactivate “${category.name}”? It will no longer appear in the storefront.`)) return;
    try {
      await api.delete(`/admin/categories/${category.id}`);
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
  }

  return (
    <section className="admin-section">
      <div className="admin-section-heading"><div><p className="eyebrow">Product organization</p><h2>Categories</h2></div></div>
      <ErrorMessage error={resource.error} />
      <form className="inline-admin-form" onSubmit={create}>
        <label className="form-field"><span>Category name</span><input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label className="form-field"><span>Description</span><input maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <button className="button-primary" disabled={busy}>Add category</button>
      </form>
      {resource.loading ? <LoadingState /> : resource.data?.length ? (
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Name</th><th>Slug</th><th>Status</th><th /></tr></thead>
          <tbody>{resource.data.map((category) => <tr key={category.id}>
            <td><strong>{category.name}</strong><small>{category.description}</small></td><td>{category.slug}</td>
            <td><span className={`status-pill status-${category.active ? "delivered" : "cancelled"}`}>{category.active ? "Active" : "Inactive"}</span></td>
            <td>{category.active && <button className="text-button danger-text" onClick={() => deactivate(category)}>Deactivate</button>}</td>
          </tr>)}</tbody></table></div>
      ) : <EmptyState title="No categories yet">Add a category to organize your products.</EmptyState>}
    </section>
  );
}

export function AdminProductsPage() {
  const resource = useAdminResource("/admin/products?page=0&size=100&sort=newest");
  const categories = useAdminResource("/admin/categories");
  const [form, setForm] = useState(emptyProduct);
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(false);

  async function save(event) {
    event.preventDefault();
    resource.setError("");
    setBusy(true);
    const body = {
      ...form,
      categoryId: Number(form.categoryId),
      price: Number(form.price),
      discountPercent: Number(form.discountPercent),
      stockQuantity: Number(form.stockQuantity),
      imageUrl: form.imageUrl || null,
    };
    try {
      if (editing) await api.put(`/admin/products/${editing}`, body);
      else await api.post("/admin/products", body);
      setForm(emptyProduct);
      setEditing(null);
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
    finally { setBusy(false); }
  }

  function edit(product) {
    setEditing(product.id);
    setForm({
      categoryId: String(product.categoryId), sku: product.sku, name: product.name, slug: product.slug,
      description: product.description || "", price: String(product.price),
      discountPercent: String(product.discountPercent), stockQuantity: String(product.stockQuantity),
      imageUrl: product.imageUrl || "", active: product.active,
    });
    document.getElementById("admin-product-form")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function deactivate(product) {
    if (!window.confirm(`Deactivate “${product.name}”?`)) return;
    try {
      await api.delete(`/admin/products/${product.id}`);
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
  }

  const products = resource.data?.content || [];
  return (
    <section className="admin-section">
      <div className="admin-section-heading"><div><p className="eyebrow">The store shelf</p><h2>Products</h2></div><span>{resource.data?.totalElements ?? 0} items</span></div>
      <ErrorMessage error={resource.error || categories.error} />
      <form id="admin-product-form" className="admin-product-form" onSubmit={save}>
        <h3>{editing ? "Update product" : "Add a product"}</h3>
        <div className="admin-form-grid">
          <label className="form-field"><span>Name</span><input required maxLength={180} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
          <label className="form-field"><span>SKU</span><input required maxLength={64} value={form.sku} onChange={(event) => setForm({ ...form, sku: event.target.value })} /></label>
          <label className="form-field"><span>URL slug</span><input required maxLength={200} value={form.slug} onChange={(event) => setForm({ ...form, slug: event.target.value })} /></label>
          <label className="form-field"><span>Category</span><select required value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })}>
            <option value="">Choose category</option>{categories.data?.map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}
          </select></label>
          <label className="form-field"><span>Price</span><input type="number" min="0" max="9999999999.99" step="0.01" required value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} /></label>
          <label className="form-field"><span>Discount (%)</span><input type="number" min="0" max="100" step="0.01" required value={form.discountPercent} onChange={(event) => setForm({ ...form, discountPercent: event.target.value })} /></label>
          <label className="form-field"><span>Stock quantity</span><input type="number" min="0" step="1" required value={form.stockQuantity} onChange={(event) => setForm({ ...form, stockQuantity: event.target.value })} /></label>
          <label className="form-field"><span>Image URL (optional)</span><input type="url" maxLength={2048} value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} /></label>
          <label className="form-field admin-form-wide"><span>Description</span><textarea rows="3" maxLength={10000} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
          <label className="checkbox-field"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>Visible in storefront</span></label>
        </div>
        <div className="form-actions"><button className="button-primary" disabled={busy || categories.loading || !categories.data?.length}>{busy ? "Saving…" : editing ? "Save changes" : "Add product"}</button>
          {editing && <button type="button" className="button-quiet" onClick={() => { setEditing(null); setForm(emptyProduct); }}>Cancel</button>}
        </div>
      </form>
      {resource.loading ? <LoadingState /> : products.length ? (
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>{products.map((product) => <tr key={product.id}>
            <td><strong>{product.name}</strong><small>{product.sku}</small></td><td>{product.categoryName}</td>
            <td>{formatPrice(product.effectivePrice)}</td><td>{product.stockQuantity}</td>
            <td><span className={`status-pill status-${product.active ? "delivered" : "cancelled"}`}>{product.active ? "Active" : "Inactive"}</span></td>
            <td className="table-actions"><button className="text-button" onClick={() => edit(product)}>Edit</button>
              {product.active && <button className="text-button danger-text" onClick={() => deactivate(product)}>Deactivate</button>}</td>
          </tr>)}</tbody></table></div>
      ) : <EmptyState title="No products yet">Add a product after creating its category.</EmptyState>}
    </section>
  );
}

export function AdminInventoryPage() {
  const resource = useAdminResource("/admin/products?page=0&size=100&sort=name");
  const [changes, setChanges] = useState({});
  const products = resource.data?.content || [];

  async function adjust(product) {
    const form = changes[product.id];
    if (!form?.delta || Number(form.delta) === 0) {
      resource.setError("Enter a non-zero inventory adjustment.");
      return;
    }
    resource.setError("");
    try {
      await api.patch("/admin/inventory", {
        productId: product.id, quantityDelta: Number(form.delta), reason: form.reason || "CORRECTION",
      });
      setChanges({ ...changes, [product.id]: { ...form, delta: "" } });
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
  }

  return (
    <section className="admin-section">
      <div className="admin-section-heading"><div><p className="eyebrow">What’s on hand</p><h2>Inventory</h2></div></div>
      <ErrorMessage error={resource.error} />
      {resource.loading ? <LoadingState /> : <div className="admin-table-wrap"><table className="admin-table">
        <thead><tr><th>Product</th><th>Available</th><th>Adjustment</th><th>Reason</th><th /></tr></thead>
        <tbody>{products.map((product) => <tr key={product.id}>
          <td><strong>{product.name}</strong><small>{product.sku}</small></td><td className="stock-value">{product.stockQuantity}</td>
          <td><label><span className="visually-hidden">Quantity adjustment for {product.name}</span>
            <input className="table-input" type="number" step="1" value={changes[product.id]?.delta || ""}
              onChange={(event) => setChanges({ ...changes, [product.id]: { reason: "CORRECTION", ...changes[product.id], delta: event.target.value } })} />
          </label></td>
          <td><select className="table-input" value={changes[product.id]?.reason || "CORRECTION"}
            onChange={(event) => setChanges({ ...changes, [product.id]: { delta: "", ...changes[product.id], reason: event.target.value } })}>
            <option value="RESTOCK">Restock</option><option value="CORRECTION">Correction</option><option value="DAMAGE">Damage</option>
          </select></td>
          <td><button className="button-secondary button-small" onClick={() => adjust(product)}>Apply</button></td>
        </tr>)}</tbody></table></div>}
    </section>
  );
}

const nextOrderStatuses = {
  PENDING: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED"],
  DELIVERED: [],
  CANCELLED: [],
  REFUNDED: [],
};

export function AdminOrdersPage() {
  const resource = useAdminResource("/admin/orders?page=0&size=100");

  async function update(order, status) {
    if (!window.confirm(`Change order ${order.orderNumber} to ${status.toLowerCase()}?`)) return;
    try {
      await api.put(`/admin/orders/${order.id}/status`, { status });
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
  }

  const orders = resource.data?.content || [];
  return (
    <section className="admin-section">
      <div className="admin-section-heading"><div><p className="eyebrow">From checkout to doorstep</p><h2>Orders</h2></div></div>
      <ErrorMessage error={resource.error} />
      {resource.loading ? <LoadingState /> : orders.length ? (
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Order</th><th>Placed</th><th>Amount</th><th>Status</th><th>Update</th></tr></thead>
          <tbody>{orders.map((order) => <tr key={order.id}>
            <td><strong>{order.orderNumber}</strong><small>{order.items.length} line items · {order.paymentMethod.replaceAll("_", " ")}</small></td>
            <td>{new Date(order.createdAt).toLocaleDateString()}</td><td>{formatPrice(order.total)}</td>
            <td><span className={`status-pill status-${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span></td>
            <td><select aria-label={`Update status for ${order.orderNumber}`} className="table-input"
              value="" disabled={!nextOrderStatuses[order.status]?.length}
              onChange={(event) => event.target.value && update(order, event.target.value)}>
              <option value="">Choose status</option>{nextOrderStatuses[order.status]?.map((status) => <option value={status} key={status}>{status.toLowerCase()}</option>)}
            </select></td>
          </tr>)}</tbody></table></div>
      ) : <EmptyState title="Nothing in the queue">New orders will appear here.</EmptyState>}
    </section>
  );
}

export function AdminCustomersPage() {
  const resource = useAdminResource("/admin/customers?page=0&size=100");
  const customers = resource.data?.content || [];

  async function setEnabled(customer) {
    const enabled = !customer.enabled;
    if (!window.confirm(`${enabled ? "Enable" : "Disable"} access for ${customer.email}?`)) return;
    try {
      await api.patch(`/admin/customers/${customer.id}/enabled`, { enabled });
      await resource.reload();
    } catch (requestError) { resource.setError(errorMessage(requestError)); }
  }

  return (
    <section className="admin-section">
      <div className="admin-section-heading"><div><p className="eyebrow">The community</p><h2>Customers</h2></div></div>
      <ErrorMessage error={resource.error} />
      {resource.loading ? <LoadingState /> : customers.length ? (
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Name</th><th>Email</th><th>Joined</th><th>Access</th><th /></tr></thead>
          <tbody>{customers.map((customer) => <tr key={customer.id}>
            <td><strong>{customer.name}</strong></td><td>{customer.email}</td>
            <td>{new Date(customer.createdAt).toLocaleDateString()}</td>
            <td>{customer.enabled ? "Enabled" : "Disabled"}</td>
            <td><button className={`text-button ${customer.enabled ? "danger-text" : ""}`} onClick={() => setEnabled(customer)}>
              {customer.enabled ? "Disable" : "Enable"}
            </button></td>
          </tr>)}</tbody></table></div>
      ) : <EmptyState title="No customers yet">Registered customers will appear here.</EmptyState>}
    </section>
  );
}
