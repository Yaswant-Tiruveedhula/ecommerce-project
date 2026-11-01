import { Link } from "react-router-dom";

const storeCurrency = import.meta.env.VITE_STORE_CURRENCY || "USD";

export function formatPrice(value) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: storeCurrency }).format(value);
}

export function ProductCard({ product, onAdd }) {
  return (
    <article className="product-card">
      <Link to={`/products/${product.slug}`} className="product-card-image" aria-label={`View ${product.name}`}>
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} loading="lazy" />
        ) : (
          <div className={`product-placeholder tone-${product.id % 5}`} aria-hidden="true">
            <span className="placeholder-shape" />
            <span className="placeholder-initial">{product.name.slice(0, 1)}</span>
          </div>
        )}
        {product.discountPercent > 0 && <span className="product-badge">A little less</span>}
      </Link>
      <div className="product-card-meta">
        <div>
          <p className="product-category">{product.categoryName}</p>
          <Link to={`/products/${product.slug}`} className="product-name">{product.name}</Link>
        </div>
        <div className="product-price">
          <span>{formatPrice(product.effectivePrice)}</span>
          {product.discountPercent > 0 && <del>{formatPrice(product.price)}</del>}
        </div>
      </div>
      {onAdd && (
        <button className="product-add" type="button" disabled={!product.active || product.stockQuantity < 1}
          onClick={() => onAdd(product)}>
          {product.stockQuantity > 0 ? "Add to bag +" : "Out of stock"}
        </button>
      )}
    </article>
  );
}
