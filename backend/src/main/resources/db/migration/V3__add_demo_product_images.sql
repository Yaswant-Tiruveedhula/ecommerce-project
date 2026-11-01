UPDATE products
SET image_url = CASE slug
    WHEN 'stoneware-vase' THEN '/images/products/stoneware-vase.jpg'
    WHEN 'woven-cotton-throw' THEN '/images/products/woven-cotton-throw.jpg'
    WHEN 'cedar-candle' THEN '/images/products/cedar-candle.jpg'
    WHEN 'oak-serving-board' THEN '/images/products/oak-serving-board.jpg'
    WHEN 'linen-tea-towels' THEN '/images/products/linen-tea-towels.jpg'
    WHEN 'daily-hand-balm' THEN '/images/products/daily-hand-balm.jpg'
    WHEN 'daily-notebook' THEN '/images/products/daily-notebook.jpg'
    WHEN 'recycled-note-cards' THEN '/images/products/recycled-note-cards.jpg'
END
WHERE slug IN (
    'stoneware-vase',
    'woven-cotton-throw',
    'cedar-candle',
    'oak-serving-board',
    'linen-tea-towels',
    'daily-hand-balm',
    'daily-notebook',
    'recycled-note-cards'
)
AND (image_url IS NULL OR TRIM(image_url) = '');
