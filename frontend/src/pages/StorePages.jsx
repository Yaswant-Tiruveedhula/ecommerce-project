import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import api from "../api.js";
import { useAuth } from "../auth/AuthProvider.jsx";
import { EmptyState, ErrorMessage, LoadingState, errorMessage } from "../components/Feedback.jsx";
import { formatPrice, ProductCard } from "../components/ProductCard.jsx";

function useAddToBag() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function add(product) {
    if (!user) {
      navigate("/login", { state: { from: { pathname: `/products/${product.slug}` } } });
      return;
    }
    setError("");
    try {
      await api.post("/cart/items", { productId: product.id, quantity: 1 });
      window.dispatchEvent(new Event("cart:changed"));
      setNotice(`${product.name} is in your bag.`);
      window.setTimeout(() => setNotice(""), 3000);
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }
  return { add, notice, error };
}

export function HomePage() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { add, notice, error: bagError } = useAddToBag();

  useEffect(() => {
    let active = true;
    Promise.all([api.get("/products", { params: { page: 0, size: 4, sort: "newest" } }), api.get("/categories")])
      .then(([productResponse, categoryResponse]) => {
        if (!active) return;
        setProducts(productResponse.data.content);
        setCategories(categoryResponse.data);
      })
      .catch((requestError) => {
        if (active) setError(errorMessage(requestError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, []);

  return (
    <main>
      <section className="hero container" id="story">
        <div className="hero-copy">
          <p className="eyebrow">Good things, made useful</p>
          <h1>Make room for<br />the <em>everyday</em>.</h1>
          <p className="hero-description">
            Considered essentials for slower mornings, softer landings, and
            all the little moments in between.
          </p>
          <Link className="shop-link" to="/products">
            Explore the collection <span aria-hidden="true">↗</span>
          </Link>
        </div>
        <div className="hero-art" role="img" aria-label="Abstract warm-toned product illustration">
          <div className="sun-disc" />
          <div className="arch arch-back" />
          <div className="arch arch-front" />
          <div className="vase"><span /></div>
          <div className="art-caption">Objects for the everyday, thoughtfully chosen.</div>
        </div>
      </section>

      <section className="category-strip container" aria-label="Shop by category">
        <div className="section-heading">
          <div><p className="eyebrow">A thoughtful edit</p><h2>Shop by <em>feeling.</em></h2></div>
          <Link className="quiet-link" to="/products">See everything <span aria-hidden="true">↗</span></Link>
        </div>
        {categories.length > 0 ? (
          <div className="category-list">
            {categories.map((category, index) => (
              <Link className={`category-tile category-tone-${index % 4}`} key={category.id}
                to={`/products?categoryId=${category.id}`}>
                <span className="category-count">0{index + 1}</span>
                <span className="category-title">{category.name}</span>
                <span className="category-arrow" aria-hidden="true">↗</span>
              </Link>
            ))}
          </div>
        ) : (
          <div className="category-placeholder">
            <span>Small comforts</span><span>Objects for home</span><span>Everyday rituals</span>
          </div>
        )}
      </section>

      <section className="featured-section">
        <div className="container">
          <div className="section-heading">
            <div><p className="eyebrow">A good place to begin</p><h2>Little things, <em>well made.</em></h2></div>
            <Link className="quiet-link" to="/products">Shop the edit <span aria-hidden="true">↗</span></Link>
          </div>
          <ErrorMessage error={error || bagError} />
          {notice && <div className="notice-success" role="status">{notice}</div>}
          {loading ? <LoadingState label="Finding the good things…" /> : products.length ? (
            <div className="product-grid">
              {products.map((product) => <ProductCard key={product.id} product={product} onAdd={add} />)}
            </div>
          ) : (
            <EmptyState title="A good thing takes time">
              Our first collection is being carefully put together. Please check back soon.
            </EmptyState>
          )}
        </div>
      </section>

      <section className="collection-section" id="collections">
        <div className="container collection-inner">
          <div><p className="eyebrow">The Common Goods promise</p>
            <h2>Everyday objects.<br /><em>Extraordinary care.</em></h2></div>
          <div className="collection-note">
            <p>We believe the things you use every day deserve a little more thought. Useful, lasting, and a pleasure to live with.</p>
            <Link className="shop-link" to="/products">Find your everyday <span aria-hidden="true">↗</span></Link>
          </div>
        </div>
      </section>
    </main>
  );
}

export function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const paramsKey = searchParams.toString();
  const initial = useMemo(() => new URLSearchParams(paramsKey), [paramsKey]);
  const [form, setForm] = useState({
    q: initial.get("q") || "",
    categoryId: initial.get("categoryId") || "",
    minPrice: initial.get("minPrice") || "",
    maxPrice: initial.get("maxPrice") || "",
    sort: initial.get("sort") || "newest",
  });
  const [page, setPage] = useState(null);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { add, notice, error: bagError } = useAddToBag();

  useEffect(() => {
    setForm({
      q: initial.get("q") || "",
      categoryId: initial.get("categoryId") || "",
      minPrice: initial.get("minPrice") || "",
      maxPrice: initial.get("maxPrice") || "",
      sort: initial.get("sort") || "newest",
    });
  }, [initial]);

  useEffect(() => {
    let active = true;
    const query = new URLSearchParams(paramsKey);
    const pageNumber = Number(query.get("page") || 0);
    setLoading(true);
    setError("");
    Promise.all([
      api.get("/products", { params: Object.fromEntries(query.entries()) }),
      api.get("/categories"),
    ])
      .then(([productResponse, categoryResponse]) => {
        if (!active) return;
        setPage(productResponse.data);
        setCategories(categoryResponse.data);
        if (productResponse.data.number !== pageNumber) {
          query.set("page", String(productResponse.data.number));
          setSearchParams(query, { replace: true });
        }
      })
      .catch((requestError) => {
        if (active) setError(errorMessage(requestError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [paramsKey, setSearchParams]);

  function applyFilters(event) {
    event.preventDefault();
    const next = new URLSearchParams();
    Object.entries(form).forEach(([key, value]) => {
      if (value) next.set(key, value);
    });
    next.set("page", "0");
    setSearchParams(next);
  }

  function changePage(nextPage) {
    const next = new URLSearchParams(paramsKey);
    next.set("page", String(nextPage));
    setSearchParams(next);
  }

  return (
    <main className="container catalog-page">
      <div className="catalog-heading">
        <p className="eyebrow">The good things</p>
        <h1>Made for <em>every day.</em></h1>
        <p>Objects with a purpose, chosen with care.</p>
      </div>
      <form className="catalog-filters" onSubmit={applyFilters}>
        <label className="filter-search">
          <span className="visually-hidden">Search products</span>
          <input value={form.q} placeholder="What are you looking for?" maxLength={100}
            onChange={(event) => setForm({ ...form, q: event.target.value })} />
          <span aria-hidden="true">⌕</span>
        </label>
        <label>
          <span className="visually-hidden">Category</span>
          <select value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })}>
            <option value="">All categories</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
        </label>
        <label>
          <span className="visually-hidden">Minimum price</span>
          <input type="number" min="0" step="0.01" value={form.minPrice} placeholder="Min $"
            onChange={(event) => setForm({ ...form, minPrice: event.target.value })} />
        </label>
        <label>
          <span className="visually-hidden">Maximum price</span>
          <input type="number" min="0" step="0.01" value={form.maxPrice} placeholder="Max $"
            onChange={(event) => setForm({ ...form, maxPrice: event.target.value })} />
        </label>
        <label>
          <span className="visually-hidden">Sort products</span>
          <select value={form.sort} onChange={(event) => setForm({ ...form, sort: event.target.value })}>
            <option value="newest">Just in</option>
            <option value="price-asc">Price: low to high</option>
            <option value="price-desc">Price: high to low</option>
            <option value="name">Name</option>
          </select>
        </label>
        <button className="button-primary filter-submit" type="submit">Find it</button>
      </form>

      <ErrorMessage error={error || bagError} />
      {notice && <div className="notice-success" role="status">{notice}</div>}
      {loading ? <LoadingState label="Looking through the collection…" /> : page?.content?.length ? (
        <>
          <div className="catalog-result-count">{page.totalElements} {page.totalElements === 1 ? "good thing" : "good things"}</div>
          <div className="product-grid">
            {page.content.map((product) => <ProductCard key={product.id} product={product} onAdd={add} />)}
          </div>
          {page.totalPages > 1 && (
            <nav className="pagination-row" aria-label="Product pages">
              <button type="button" disabled={page.first} onClick={() => changePage(page.number - 1)}>Previous</button>
              <span>Page {page.number + 1} of {page.totalPages}</span>
              <button type="button" disabled={page.last} onClick={() => changePage(page.number + 1)}>Next</button>
            </nav>
          )}
        </>
      ) : (
        <EmptyState title="Nothing here just yet" action={<Link className="quiet-link" to="/products">Clear filters ↗</Link>}>
          Try a different search, or adjust the filters to find what you need.
        </EmptyState>
      )}
    </main>
  );
}

export function ProductPage() {
  const { slug } = useParams();
  const [product, setProduct] = useState(null);
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [added, setAdded] = useState(false);
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([api.get(`/products/${encodeURIComponent(slug)}`),
      api.get(`/products/${encodeURIComponent(slug)}/reviews`)])
      .then(([productResponse, reviewResponse]) => {
        if (active) {
          setProduct(productResponse.data);
          setReviews(reviewResponse.data.content || []);
        }
      })
      .catch((requestError) => {
        if (active) setError(errorMessage(requestError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [slug]);

  async function addToBag() {
    if (!user) {
      navigate("/login", { state: { from: { pathname: `/products/${slug}` } } });
      return;
    }
    try {
      await api.post("/cart/items", { productId: product.id, quantity: 1 });
      window.dispatchEvent(new Event("cart:changed"));
      setAdded(true);
    } catch (requestError) {
      setError(errorMessage(requestError));
    }
  }

  if (loading) return <main className="container"><LoadingState label="Finding your good thing…" /></main>;
  if (error && !product) return <main className="container catalog-page"><ErrorMessage error={error} /><Link to="/products">Back to the collection</Link></main>;
  if (!product) return null;

  return (
    <main className="container product-detail-page">
      <div className="breadcrumbs"><Link to="/">Home</Link><span>/</span><Link to="/products">Collection</Link><span>/</span><span>{product.name}</span></div>
      <div className="product-detail-grid">
        <div className="product-detail-image">
          {product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : (
            <div className={`product-placeholder tone-${product.id % 5}`} aria-hidden="true">
              <span className="placeholder-shape" /><span className="placeholder-initial">{product.name.slice(0, 1)}</span>
            </div>
          )}
        </div>
        <div className="product-detail-copy">
          <p className="eyebrow">{product.categoryName}</p>
          <h1>{product.name}</h1>
          <div className="detail-price">{formatPrice(product.effectivePrice)}
            {product.discountPercent > 0 && <del>{formatPrice(product.price)}</del>}
          </div>
          <p className="detail-description">{product.description || "A considered everyday essential, chosen for its useful design and lasting quality."}</p>
          <p className={`stock-note ${product.stockQuantity > 0 ? "" : "out-of-stock"}`}>
            {product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : "Currently out of stock"}
          </p>
          <button type="button" className="button-primary detail-add" disabled={!product.active || product.stockQuantity < 1}
            onClick={addToBag}>{added ? "Added to your bag ✓" : "Add to bag"}</button>
          {error && <ErrorMessage error={error} />}
          <div className="detail-assurance"><span>Thoughtful design</span><span>Secure checkout</span><span>Easy order tracking</span></div>
        </div>
      </div>
      <section className="reviews-section">
        <div className="section-heading"><div><p className="eyebrow">Notes from the community</p><h2>Good words.</h2></div></div>
        {reviews.length ? <div className="review-list">{reviews.map((review) => (
          <article className="review-card" key={review.id}>
            <div className="review-stars" aria-label={`${review.rating} out of 5 stars`}>{"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}</div>
            <p>{review.body || "A lovely everyday find."}</p>
            <span>{review.customerName}</span>
          </article>
        ))}</div> : <p className="muted-copy">No reviews yet. Verified buyers can leave a note after delivery.</p>}
      </section>
    </main>
  );
}
