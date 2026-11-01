import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import api from "../api.js";
import { useAuth } from "../auth/AuthProvider.jsx";
import { EmptyState, ErrorMessage, LoadingState, errorMessage } from "../components/Feedback.jsx";
import { formatPrice } from "../components/ProductCard.jsx";

const blankAddress = {
  recipientName: "", phone: "", addressLine1: "", addressLine2: "",
  city: "", region: "", postalCode: "", countryCode: "US", defaultAddress: false,
};

function AddressFields({ value, onChange }) {
  const field = (name, label, extra = {}) => (
    <label className="form-field" key={name}>
      <span>{label}</span>
      <input required={extra.required !== false} maxLength={extra.maxLength || 180}
        type={extra.type || "text"} value={value[name] ?? ""}
        onChange={(event) => onChange({ ...value, [name]: event.target.value })} />
    </label>
  );
  return (
    <div className="address-fields">
      {field("recipientName", "Recipient name", { maxLength: 120 })}
      {field("phone", "Phone number", { maxLength: 32, type: "tel" })}
      {field("addressLine1", "Address line 1")}
      {field("addressLine2", "Address line 2", { required: false })}
      {field("city", "City", { maxLength: 100 })}
      {field("region", "State / region", { maxLength: 100 })}
      {field("postalCode", "Postal code", { maxLength: 24 })}
      {field("countryCode", "Country code (2 letters)", { maxLength: 2 })}
      <label className="checkbox-field">
        <input type="checkbox" checked={value.defaultAddress}
          onChange={(event) => onChange({ ...value, defaultAddress: event.target.checked })} />
        <span>Make this my default address</span>
      </label>
    </div>
  );
}

export function AccountPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState(user.name);
  const [addresses, setAddresses] = useState([]);
  const [address, setAddress] = useState(blankAddress);
  const [editingAddress, setEditingAddress] = useState(null);
  const [password, setPassword] = useState({ currentPassword: "", newPassword: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function loadAddresses() {
    try {
      const { data } = await api.get("/addresses");
      setAddresses(data);
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  useEffect(() => { loadAddresses(); }, []);

  async function saveProfile(event) {
    event.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await api.patch("/me", { name });
      setNotice("Your profile has been updated.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally { setBusy(false); }
  }

  async function saveAddress(event) {
    event.preventDefault();
    setError("");
    try {
      if (editingAddress) {
        await api.put(`/addresses/${editingAddress}`, address);
      } else {
        await api.post("/addresses", address);
      }
      setAddress(blankAddress);
      setEditingAddress(null);
      await loadAddresses();
      setNotice("Your address has been saved.");
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  function editAddress(item) {
    setEditingAddress(item.id);
    setAddress({ ...item });
    document.getElementById("address-form")?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  async function deleteAddress(id) {
    if (!window.confirm("Remove this delivery address?")) return;
    try {
      await api.delete(`/addresses/${id}`);
      await loadAddresses();
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function changePassword(event) {
    event.preventDefault();
    setError("");
    try {
      await api.post("/me/password", password);
      setNotice("Password updated. Please sign in again.");
      await logout();
      navigate("/login", { replace: true });
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  return (
    <main className="container account-page">
      <div className="account-heading">
        <div><p className="eyebrow">Your corner</p><h1>Welcome, <em>{user.name.split(" ")[0]}.</em></h1></div>
        <Link className="quiet-link" to="/orders">View your orders ↗</Link>
      </div>
      <ErrorMessage error={error} />
      {notice && <div className="notice-success" role="status">{notice}</div>}
      <div className="account-columns">
        <section className="account-panel">
          <div className="panel-heading"><p className="eyebrow">The basics</p><h2>Your profile</h2></div>
          <form onSubmit={saveProfile} className="form-stack">
            <label className="form-field"><span>Your name</span><input required maxLength={120} value={name}
              onChange={(event) => setName(event.target.value)} /></label>
            <label className="form-field"><span>Email address</span><input type="email" value={user.email} disabled /></label>
            <button type="submit" className="button-primary" disabled={busy}>Save profile</button>
          </form>
          <hr className="soft-divider" />
          <div className="panel-heading"><p className="eyebrow">Keep it safe</p><h2>Change password</h2></div>
          <form onSubmit={changePassword} className="form-stack">
            <label className="form-field"><span>Current password</span><input type="password" autoComplete="current-password"
              required value={password.currentPassword}
              onChange={(event) => setPassword({ ...password, currentPassword: event.target.value })} /></label>
            <label className="form-field"><span>New password</span><input type="password" autoComplete="new-password"
              required minLength={10} maxLength={72} value={password.newPassword}
              onChange={(event) => setPassword({ ...password, newPassword: event.target.value })} /></label>
            <button type="submit" className="button-secondary">Update password</button>
          </form>
        </section>

        <section className="account-panel">
          <div className="panel-heading"><p className="eyebrow">For the doorstep</p><h2>Delivery addresses</h2></div>
          {addresses.map((item) => (
            <article className="saved-address" key={item.id}>
              <div><strong>{item.recipientName}</strong>{item.defaultAddress && <span className="default-pill">Default</span>}
                <p>{item.addressLine1}{item.addressLine2 ? `, ${item.addressLine2}` : ""}<br />
                  {item.city}, {item.region} {item.postalCode} · {item.countryCode}<br />{item.phone}</p>
              </div>
              <div className="address-actions">
                <button className="text-button" type="button" onClick={() => editAddress(item)}>Edit</button>
                <button className="text-button danger-text" type="button" onClick={() => deleteAddress(item.id)}>Remove</button>
              </div>
            </article>
          ))}
          <form id="address-form" onSubmit={saveAddress} className="form-stack address-form">
            <h3>{editingAddress ? "Edit this address" : "Add a new address"}</h3>
            <AddressFields value={address} onChange={setAddress} />
            <div className="form-actions">
              <button type="submit" className="button-primary">{editingAddress ? "Save address" : "Add address"}</button>
              {editingAddress && <button type="button" className="button-quiet" onClick={() => {
                setEditingAddress(null); setAddress(blankAddress);
              }}>Cancel</button>}
            </div>
          </form>
        </section>
      </div>
    </main>
  );
}

export function CartPage() {
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    try {
      const { data } = await api.get("/cart");
      setCart(data);
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function update(item, quantity) {
    setError("");
    try {
      const { data } = await api.put(`/cart/items/${item.id}`, { quantity: Number(quantity) });
      setCart(data);
      window.dispatchEvent(new Event("cart:changed"));
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function remove(item) {
    setError("");
    try {
      const { data } = await api.delete(`/cart/items/${item.id}`);
      setCart(data);
      window.dispatchEvent(new Event("cart:changed"));
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function clear() {
    if (!window.confirm("Remove everything from your bag?")) return;
    try {
      const { data } = await api.delete("/cart");
      setCart(data);
      window.dispatchEvent(new Event("cart:changed"));
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  if (loading) return <main className="container"><LoadingState label="Opening your bag…" /></main>;
  return (
    <main className="container cart-page">
      <div className="page-title-row"><div><p className="eyebrow">Your considered choices</p><h1>Your <em>bag.</em></h1></div>
        {cart?.items?.length > 0 && <button className="text-button" type="button" onClick={clear}>Clear bag</button>}
      </div>
      <ErrorMessage error={error} />
      {!cart?.items?.length ? (
        <EmptyState title="A little room for something good" action={<Link className="button-primary button-inline" to="/products">Explore the collection</Link>}>
          Your bag is empty for now.
        </EmptyState>
      ) : (
        <div className="cart-layout">
          <div className="cart-lines">
            {cart.items.map((item) => (
              <article className="cart-line" key={item.id}>
                <Link to={`/products/${item.slug}`} className="cart-line-image">
                  {item.imageUrl ? <img src={item.imageUrl} alt="" /> : <span>{item.name.slice(0, 1)}</span>}
                </Link>
                <div className="cart-line-copy">
                  <Link to={`/products/${item.slug}`} className="product-name">{item.name}</Link>
                  <span className="muted-copy">{formatPrice(item.unitPrice)} each · {item.availableStock} available</span>
                  <div className="cart-line-actions">
                    <label className="quantity-control"><span className="visually-hidden">Quantity</span>
                      <select value={item.quantity} onChange={(event) => update(item, event.target.value)}>
                        {Array.from({ length: Math.min(item.availableStock, 99) }, (_, index) => index + 1)
                          .map((quantity) => <option key={quantity} value={quantity}>{quantity}</option>)}
                      </select>
                    </label>
                    <button className="text-button" type="button" onClick={() => remove(item)}>Remove</button>
                  </div>
                </div>
                <strong className="cart-line-total">{formatPrice(item.lineTotal)}</strong>
              </article>
            ))}
          </div>
          <aside className="order-summary">
            <p className="eyebrow">The details</p><h2>Summary</h2>
            <div className="summary-row"><span>Subtotal · {cart.itemCount} items</span><strong>{formatPrice(cart.total)}</strong></div>
            <div className="summary-row"><span>Delivery</span><span>Calculated at checkout</span></div>
            <p className="muted-copy">Your total is recalculated securely at checkout.</p>
            <Link to="/checkout" className="button-primary">Continue to checkout <span aria-hidden="true">→</span></Link>
          </aside>
        </div>
      )}
    </main>
  );
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState([]);
  const [cart, setCart] = useState(null);
  const [addressId, setAddressId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([api.get("/addresses"), api.get("/cart")])
      .then(([addressResponse, cartResponse]) => {
        if (!active) return;
        const values = addressResponse.data;
        setAddresses(values);
        setAddressId(String(values.find((item) => item.defaultAddress)?.id || values[0]?.id || ""));
        setCart(cartResponse.data);
      })
      .catch((requestError) => { if (active) setError(errorMessage(requestError)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const idempotencyKey = window.crypto?.randomUUID?.()
        || `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      const { data } = await api.post("/orders/checkout", {
        addressId: Number(addressId),
        idempotencyKey,
        paymentMethod: "CASH_ON_DELIVERY",
      }, { headers: { "Idempotency-Key": idempotencyKey } });
      window.dispatchEvent(new Event("cart:changed"));
      navigate(`/orders/${data.id}/confirmation`, { state: { order: data } });
    } catch (requestError) {
      setError(errorMessage(requestError));
    } finally { setBusy(false); }
  }

  if (loading) return <main className="container"><LoadingState label="Preparing checkout…" /></main>;
  if (!cart?.items?.length) return <main className="container cart-page"><EmptyState title="Your bag is empty" action={<Link className="button-primary button-inline" to="/products">Browse the collection</Link>} /></main>;

  return (
    <main className="container checkout-page">
      <div><p className="eyebrow">The last little step</p><h1>Let’s make it <em>yours.</em></h1></div>
      <ErrorMessage error={error} />
      <div className="cart-layout">
        <form className="checkout-form" onSubmit={submit}>
          <section className="checkout-section">
            <h2>Delivery address</h2>
            {addresses.length ? (
              <div className="address-choice-list">
                {addresses.map((address) => (
                  <label className={`address-choice ${String(address.id) === addressId ? "selected" : ""}`} key={address.id}>
                    <input type="radio" name="address" value={address.id} checked={String(address.id) === addressId}
                      onChange={(event) => setAddressId(event.target.value)} />
                    <span><strong>{address.recipientName} {address.defaultAddress && <small>Default</small>}</strong>
                      <span>{address.addressLine1}{address.addressLine2 ? `, ${address.addressLine2}` : ""}</span>
                      <span>{address.city}, {address.region} {address.postalCode} · {address.countryCode}</span>
                      <span>{address.phone}</span>
                    </span>
                  </label>
                ))}
              </div>
            ) : (
              <div className="address-needed">Add a delivery address before placing your order. <Link to="/account">Add an address ↗</Link></div>
            )}
          </section>
          <section className="checkout-section">
            <h2>Payment</h2>
            <div className="cod-choice"><span className="cod-radio" /><span><strong>Cash on Delivery</strong>
              <small>Pay your delivery partner when your order arrives. No card details are collected.</small></span></div>
            <p className="field-hint">Online payment is not enabled. This order will not be marked as paid until a verified payment method is configured.</p>
          </section>
          <button type="submit" className="button-primary checkout-submit" disabled={busy || !addressId || !addresses.length}>
            {busy ? "Placing your order…" : "Place order · Cash on Delivery"}
          </button>
        </form>
        <aside className="order-summary">
          <p className="eyebrow">A final look</p><h2>Your order</h2>
          {cart.items.map((item) => <div className="summary-row" key={item.id}>
            <span>{item.name} × {item.quantity}</span><strong>{formatPrice(item.lineTotal)}</strong>
          </div>)}
          <div className="summary-row summary-total"><span>Total</span><strong>{formatPrice(cart.total)}</strong></div>
          <Link className="back-to-cart" to="/cart">← Back to your bag</Link>
        </aside>
      </div>
    </main>
  );
}

export function OrdersPage() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/orders")
      .then(({ data }) => setOrders(data.content))
      .catch((requestError) => setError(errorMessage(requestError)))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="container account-page">
      <div className="account-heading"><div><p className="eyebrow">The good things you’ve found</p><h1>Your <em>orders.</em></h1></div>
        <Link className="quiet-link" to="/products">Keep exploring ↗</Link></div>
      <ErrorMessage error={error} />
      {loading ? <LoadingState label="Finding your orders…" /> : orders.length ? (
        <div className="order-list">{orders.map((order) => (
          <Link className="order-card" to={`/orders/${order.id}`} key={order.id}>
            <span className="order-card-label">Order {order.orderNumber}</span>
            <span>{new Date(order.createdAt).toLocaleDateString()}</span>
            <span className={`status-pill status-${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span>
            <strong>{formatPrice(order.total)}</strong><span aria-hidden="true">↗</span>
          </Link>
        ))}</div>
      ) : <EmptyState title="Your story starts here" action={<Link className="button-primary button-inline" to="/products">Find something good</Link>}>No orders yet.</EmptyState>}
    </main>
  );
}

export function OrderDetailPage({ confirmation = false }) {
  const { id } = useParams();
  const { user } = useAuth();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [review, setReview] = useState({ orderItemId: "", rating: 5, body: "" });
  const [reviewNotice, setReviewNotice] = useState("");

  useEffect(() => {
    let active = true;
    api.get(`/orders/${id}`)
      .then(({ data }) => { if (active) setOrder(data); })
      .catch((requestError) => { if (active) setError(errorMessage(requestError)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id]);

  async function cancelOrder() {
    if (!window.confirm("Cancel this order? Available items will be returned to stock.")) return;
    try {
      const { data } = await api.post(`/orders/${id}/cancel`);
      setOrder(data);
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  async function submitReview(event) {
    event.preventDefault();
    try {
      await api.post("/reviews", { ...review, orderItemId: Number(review.orderItemId), rating: Number(review.rating) });
      setReviewNotice("Thanks for sharing a note.");
      setReview({ orderItemId: "", rating: 5, body: "" });
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  if (loading) return <main className="container"><LoadingState label="Finding your order…" /></main>;
  if (error && !order) return <main className="container catalog-page"><ErrorMessage error={error} /><Link to="/orders">Back to your orders</Link></main>;
  if (!order) return null;

  return (
    <main className="container order-detail-page">
      {confirmation && <div className="order-confirmation"><span className="confirmation-check">✓</span>
        <p className="eyebrow">Well chosen</p><h1>Your order is <em>in good hands.</em></h1>
        <p>We’ve received your order. Pay the delivery partner when it arrives.</p>
      </div>}
      <div className="order-detail-heading">
        <div><p className="eyebrow">Order {order.orderNumber}</p><h1>{confirmation ? "Thank you." : "Order details."}</h1>
          <p>Placed {new Date(order.createdAt).toLocaleString()}</p></div>
        <span className={`status-pill status-${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span>
      </div>
      <ErrorMessage error={error} />
      <div className="cart-layout">
        <section className="order-lines">
          <h2>What’s on its way</h2>
          {order.items.map((item) => <article className="order-line" key={item.id}>
            <div><strong>{item.productName}</strong><span>SKU {item.sku} · Qty {item.quantity}</span></div>
            <strong>{formatPrice(item.lineTotal)}</strong>
          </article>)}
        </section>
        <aside className="order-summary">
          <p className="eyebrow">The details</p><h2>Summary</h2>
          <div className="summary-row"><span>Items</span><strong>{formatPrice(order.subtotal)}</strong></div>
          {order.discountTotal > 0 && <div className="summary-row"><span>Savings</span><strong>−{formatPrice(order.discountTotal)}</strong></div>}
          <div className="summary-row"><span>Delivery</span><strong>{formatPrice(order.shippingTotal)}</strong></div>
          <div className="summary-row summary-total"><span>Total · COD</span><strong>{formatPrice(order.total)}</strong></div>
          <p className="field-hint">Payment status: {order.paymentStatus.toLowerCase()}. No online payment was processed.</p>
          <h3>Delivering to</h3>
          <p className="address-block">{order.recipientName}<br />{order.addressLine1}{order.addressLine2 ? `, ${order.addressLine2}` : ""}<br />
            {order.city}, {order.region} {order.postalCode} · {order.countryCode}<br />{order.phone}</p>
          {order.status === "PENDING" && <button className="button-quiet danger-text" type="button" onClick={cancelOrder}>Cancel order</button>}
        </aside>
      </div>
      {order.status === "DELIVERED" && user && (
        <section className="review-submit-section">
          <div><p className="eyebrow">A note for the next person</p><h2>Share your experience.</h2></div>
          <ErrorMessage error={error} />
          {reviewNotice && <div className="notice-success" role="status">{reviewNotice}</div>}
          <form className="review-form" onSubmit={submitReview}>
            <label className="form-field"><span>Purchased product</span>
              <select required value={review.orderItemId} onChange={(event) => setReview({ ...review, orderItemId: event.target.value })}>
                <option value="">Choose an item</option>
                {order.items.map((item) => <option value={item.id} key={item.id}>{item.productName}</option>)}
              </select>
            </label>
            <label className="form-field"><span>Rating</span>
              <select value={review.rating} onChange={(event) => setReview({ ...review, rating: event.target.value })}>
                {[5, 4, 3, 2, 1].map((rating) => <option key={rating} value={rating}>{rating} out of 5</option>)}
              </select>
            </label>
            <label className="form-field"><span>Your note</span><textarea rows="4" maxLength="3000" value={review.body}
              onChange={(event) => setReview({ ...review, body: event.target.value })} /></label>
            <button className="button-primary button-inline" type="submit">Share review</button>
          </form>
        </section>
      )}
    </main>
  );
}
