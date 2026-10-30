// TasteTrail online demo: the same business rules as the Java backend, running in the browser.
// Generated file – data is kept in this browser (localStorage).
(function () {
"use strict";
// Bill calculation for a cart. Pure functions, no database access,
// so the same rules can be unit-tested and reused by the API.

const PRICING_RULES = {
  gstRate: 0.05,            // 5% GST on food (after discount)
  packagingFee: 15,         // flat packaging charge per order
  baseDeliveryFee: 30,      // covers the first 3 km
  freeDeliveryKm: 3,
  perKmFee: 8,              // charged for every km beyond 3 km
  freeDeliveryAbove: 499,   // free delivery when food value (after discount) reaches this
  maxQtyPerItem: 20,
};

const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;

/**
 * Validate a coupon against a subtotal and return the discount.
 * coupon: { code, type: 'percent'|'flat', value, max_discount, min_order, active }
 */
function applyCoupon(coupon, subtotal) {
  if (!coupon) return { discount: 0, error: null };
  if (!coupon.active) return { discount: 0, error: 'This coupon is no longer active' };
  if (subtotal < coupon.min_order) {
    return { discount: 0, error: `Add items worth ₹${round2(coupon.min_order - subtotal)} more to use ${coupon.code}` };
  }
  let discount = coupon.type === 'percent' ? (subtotal * coupon.value) / 100 : coupon.value;
  if (coupon.max_discount) discount = Math.min(discount, coupon.max_discount);
  discount = Math.min(discount, subtotal);
  return { discount: round2(discount), error: null };
}

function deliveryFee(distanceKm, foodValue, rules = PRICING_RULES) {
  if (foodValue >= rules.freeDeliveryAbove) return 0;
  const extraKm = Math.max(0, Math.ceil(distanceKm - rules.freeDeliveryKm));
  return rules.baseDeliveryFee + extraKm * rules.perKmFee;
}

/** Estimated delivery time in minutes: preparation + travel (avg 20 km/h) + 5 min handover. */
function estimateEtaMinutes(prepMinutes, distanceKm, itemCount = 1) {
  const extraPrep = Math.max(0, itemCount - 3) * 2; // big orders take a little longer
  return Math.round(prepMinutes + extraPrep + (distanceKm / 20) * 60 + 5);
}

/**
 * lines: [{ id, name, price, quantity }]
 * restaurant: { distance_km, prep_minutes }
 */
function computeQuote(lines, restaurant, coupon = null, rules = PRICING_RULES) {
  if (!Array.isArray(lines) || lines.length === 0) throw new Error('Cart is empty');
  for (const l of lines) {
    if (!Number.isInteger(l.quantity) || l.quantity < 1 || l.quantity > rules.maxQtyPerItem) {
      throw new Error(`Invalid quantity for ${l.name}`);
    }
  }
  const subtotal = round2(lines.reduce((s, l) => s + l.price * l.quantity, 0));
  const { discount, error: couponError } = applyCoupon(coupon, subtotal);
  const foodValue = round2(subtotal - discount);
  const delivery = deliveryFee(restaurant.distance_km, foodValue, rules);
  const tax = round2(foodValue * rules.gstRate);
  const packaging = rules.packagingFee;
  const total = round2(foodValue + tax + delivery + packaging);
  const itemCount = lines.reduce((s, l) => s + l.quantity, 0);
  return {
    subtotal, discount, couponError, couponApplied: coupon && !couponError ? coupon.code : null,
    deliveryFee: delivery, packaging, tax, total, itemCount,
    etaMinutes: estimateEtaMinutes(restaurant.prep_minutes, restaurant.distance_km, itemCount),
    freeDeliveryGap: delivery > 0 ? round2(rules.freeDeliveryAbove - foodValue) : 0,
  };
}

// "Frequently ordered together" recommendations using association rules
// (market basket analysis). Each past order is a basket of menu-item ids.
//
//   support(A,B)    = orders containing A and B / all orders
//   confidence(A→B) = orders containing A and B / orders containing A
//   lift(A→B)       = confidence(A→B) / support(B)
//
// lift > 1 means B is ordered with A more often than chance would suggest.

function buildRules(baskets, { minPairCount = 3 } = {}) {
  const n = baskets.length;
  const itemCount = new Map();
  const pairCount = new Map();
  for (const raw of baskets) {
    const items = [...new Set(raw)].sort((a, b) => a - b);
    for (const i of items) itemCount.set(i, (itemCount.get(i) || 0) + 1);
    for (let x = 0; x < items.length; x++) {
      for (let y = x + 1; y < items.length; y++) {
        const key = `${items[x]}|${items[y]}`;
        pairCount.set(key, (pairCount.get(key) || 0) + 1);
      }
    }
  }
  const rules = new Map(); // antecedent -> [{ item, support, confidence, lift, count }]
  for (const [key, count] of pairCount) {
    if (count < minPairCount) continue;
    const [a, b] = key.split('|').map(Number);
    for (const [from, to] of [[a, b], [b, a]]) {
      const confidence = count / itemCount.get(from);
      const supportTo = itemCount.get(to) / n;
      const rule = { item: to, count, support: count / n, confidence, lift: confidence / supportTo };
      if (!rules.has(from)) rules.set(from, []);
      rules.get(from).push(rule);
    }
  }
  return { n, itemCount, rules };
}

/**
 * Recommend up to k items for the current cart.
 * Scores from every cart item are combined; items already in the cart are skipped.
 * allowed: optional Set of item ids that may be suggested (e.g. same restaurant, available).
 */
function recommend(model, cartIds, { k = 3, allowed = null, minLift = 1.0 } = {}) {
  const inCart = new Set(cartIds);
  const scores = new Map();
  for (const id of inCart) {
    for (const r of model.rules.get(id) || []) {
      if (inCart.has(r.item) || r.lift < minLift) continue;
      if (allowed && !allowed.has(r.item)) continue;
      const s = scores.get(r.item) || { item: r.item, score: 0, confidence: 0, lift: 0, because: id };
      const ruleScore = r.confidence * r.lift;
      if (ruleScore > s.score) Object.assign(s, { score: ruleScore, confidence: r.confidence, lift: r.lift, because: id });
      scores.set(r.item, s);
    }
  }
  return [...scores.values()].sort((a, b) => b.score - a.score).slice(0, k);
}

/** Fallback when there is no rule: most-ordered items. */
function popular(model, { k = 6, allowed = null, exclude = [] } = {}) {
  const ex = new Set(exclude);
  return [...model.itemCount.entries()]
    .filter(([id]) => !ex.has(id) && (!allowed || allowed.has(id)))
    .sort((a, b) => b[1] - a[1])
    .slice(0, k)
    .map(([item, count]) => ({ item, count }));
}

// Descriptive analytics over order data. Pure functions that take plain arrays.
// orders: [{ id, user_id, total, payment_method, status, created_at (ISO), rating? }]
// lines:  [{ order_id, name, category, quantity, price }]


const DAY = 24 * 60 * 60 * 1000;
// Calendar day in local time (IST = UTC+5:30 by default) so late-evening orders fall on the right day.
const IST = 330;
const dayKey = (d, tz = IST) => new Date(+new Date(d) + tz * 60000).toISOString().slice(0, 10);
const addDays = (key, n) => new Date(+new Date(key + 'T00:00:00Z') + n * DAY).toISOString().slice(0, 10);

function kpis(orders) {
  const valid = orders.filter((o) => o.status !== 'CANCELLED');
  const revenue = valid.reduce((s, o) => s + o.total, 0);
  const perUser = new Map();
  for (const o of valid) perUser.set(o.user_id, (perUser.get(o.user_id) || 0) + 1);
  const customers = perUser.size;
  const repeat = [...perUser.values()].filter((c) => c > 1).length;
  const delivered = valid.filter((o) => o.status === 'DELIVERED' && o.delivered_at);
  const avgDelivery = delivered.length
    ? delivered.reduce((s, o) => s + (new Date(o.delivered_at) - new Date(o.created_at)) / 60000, 0) / delivered.length
    : 0;
  return {
    orders: valid.length,
    cancelled: orders.length - valid.length,
    cancellationRate: orders.length ? round2(((orders.length - valid.length) / orders.length) * 100) : 0,
    revenue: round2(revenue),
    avgOrderValue: valid.length ? round2(revenue / valid.length) : 0,
    customers,
    repeatRate: customers ? round2((repeat / customers) * 100) : 0,
    avgDeliveryMinutes: Math.round(avgDelivery),
  };
}

/** Daily revenue for the last `days` days ending at `end`, with a 7-day moving average. */
function dailyRevenue(orders, days, end = new Date(), tz = IST) {
  const endKey = dayKey(end, tz);
  const buckets = new Map();
  for (let i = days - 1; i >= 0; i--) { const k = addDays(endKey, -i); buckets.set(k, { date: k, revenue: 0, orders: 0 }); }
  for (const o of orders) {
    if (o.status === 'CANCELLED') continue;
    const b = buckets.get(dayKey(o.created_at, tz));
    if (b) { b.revenue += o.total; b.orders += 1; }
  }
  const series = [...buckets.values()].map((b) => ({ ...b, revenue: round2(b.revenue) }));
  return series.map((b, i) => {
    const win = series.slice(Math.max(0, i - 6), i + 1);
    return { ...b, movingAvg: round2(win.reduce((s, x) => s + x.revenue, 0) / win.length) };
  });
}

/** Ordinary least squares fit y = a + b·x over the series, projected `ahead` days. */
function linearForecast(series, ahead = 7) {
  const n = series.length;
  if (n < 2) return { slope: 0, intercept: 0, r2: 0, forecast: [] };
  const xs = series.map((_, i) => i);
  const ys = series.map((s) => s.revenue);
  const mx = xs.reduce((a, b) => a + b, 0) / n;
  const my = ys.reduce((a, b) => a + b, 0) / n;
  let sxy = 0, sxx = 0, syy = 0;
  for (let i = 0; i < n; i++) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) ** 2; syy += (ys[i] - my) ** 2; }
  const slope = sxx ? sxy / sxx : 0;
  const intercept = my - slope * mx;
  const r2 = syy && sxx ? (sxy * sxy) / (sxx * syy) : 0;
  const forecast = Array.from({ length: ahead }, (_, k) => ({
    date: addDays(series[n - 1].date, k + 1),
    forecast: round2(Math.max(0, intercept + slope * (n + k))),
  }));
  return { slope: round2(slope), intercept: round2(intercept), r2: Math.round(r2 * 1000) / 1000, forecast };
}

/** Orders by hour of day (0–23), in local time of the server (IST in the demo). */
function hourlyDistribution(orders, tzOffsetMinutes = 330) {
  const hours = Array.from({ length: 24 }, (_, h) => ({ hour: h, orders: 0 }));
  for (const o of orders) {
    if (o.status === 'CANCELLED') continue;
    const d = new Date(+new Date(o.created_at) + tzOffsetMinutes * 60000);
    hours[d.getUTCHours()].orders += 1;
  }
  return hours;
}

function topItems(lines, k = 8) {
  const m = new Map();
  for (const l of lines) {
    const e = m.get(l.name) || { name: l.name, category: l.category, quantity: 0, revenue: 0 };
    e.quantity += l.quantity; e.revenue += l.quantity * l.price;
    m.set(l.name, e);
  }
  return [...m.values()].map((e) => ({ ...e, revenue: round2(e.revenue) })).sort((a, b) => b.quantity - a.quantity).slice(0, k);
}

function shareBy(rows, keyFn, valueFn = () => 1) {
  const m = new Map();
  let total = 0;
  for (const r of rows) { const k = keyFn(r); const v = valueFn(r); m.set(k, (m.get(k) || 0) + v); total += v; }
  return [...m.entries()].map(([name, value]) => ({ name, value: round2(value), pct: total ? round2((value / total) * 100) : 0 }))
    .sort((a, b) => b.value - a.value);
}

function ratingDistribution(ratings) {
  const dist = [1, 2, 3, 4, 5].map((r) => ({ rating: r, count: 0 }));
  for (const r of ratings) if (r >= 1 && r <= 5) dist[r - 1].count++;
  const n = ratings.length;
  return { dist, average: n ? round2(ratings.reduce((a, b) => a + b, 0) / n) : 0, count: n };
}

// Order life cycle. Only the transitions listed here are allowed.
const STATUSES = ['PLACED', 'CONFIRMED', 'PREPARING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

const TRANSITIONS = {
  PLACED: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['PREPARING', 'CANCELLED'],
  PREPARING: ['OUT_FOR_DELIVERY'],
  OUT_FOR_DELIVERY: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

const canTransition = (from, to) => (TRANSITIONS[from] || []).includes(to);

/** Customers may cancel only before the kitchen starts preparing. */
const customerCanCancel = (status) => status === 'PLACED' || status === 'CONFIRMED';

const LABELS = {
  PLACED: 'Order placed', CONFIRMED: 'Confirmed by restaurant', PREPARING: 'Being prepared',
  OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', CANCELLED: 'Cancelled',
};

// ---- deterministic random numbers (mulberry32) ----
let seed = 20261029;
const rand = () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const pickWeighted = (arr, w) => { const tot = w.reduce((a, b) => a + b, 0); let r = rand() * tot; for (let i = 0; i < arr.length; i++) { r -= w[i]; if (r <= 0) return arr[i]; } return arr[arr.length - 1]; };

// ---- restaurants and menus ----
// item: [name, category, price, veg, emoji, popularity weight, description]
// pairs: [anchor, add-on, probability that add-on is ordered with the anchor]
const RESTAURANTS = [
  { name: 'Biryani Bazaar', cuisine: 'Biryani · Mughlai', area: 'Sector 18', distance_km: 2.4, prep_minutes: 25, cost_for_two: 550, veg: 0, theme: 'amber',
    items: [
      ['Chicken Dum Biryani', 'Mains', 299, 0, '🍗', 10, 'Slow-cooked basmati rice layered with marinated chicken'],
      ['Veg Hyderabadi Biryani', 'Mains', 229, 1, '🍚', 5, 'Fragrant rice with seasonal vegetables and saffron'],
      ['Mutton Biryani', 'Mains', 379, 0, '🍖', 4, 'Tender mutton pieces in dum-cooked rice'],
      ['Chicken 65', 'Starters', 249, 0, '🍢', 4, 'Spicy deep-fried chicken bites'],
      ['Burani Raita', 'Sides', 59, 1, '🥣', 1, 'Garlic-tempered yoghurt'],
      ['Mirchi ka Salan', 'Sides', 79, 1, '🌶️', 1, 'Chilli and peanut curry'],
      ['Double ka Meetha', 'Desserts', 99, 1, '🍮', 1, 'Hyderabadi bread pudding'],
      ['Masala Chaas', 'Beverages', 49, 1, '🥛', 1, 'Spiced buttermilk'],
    ],
    pairs: [['Chicken Dum Biryani', 'Burani Raita', 0.62], ['Mutton Biryani', 'Burani Raita', 0.55], ['Veg Hyderabadi Biryani', 'Mirchi ka Salan', 0.5],
      ['Chicken Dum Biryani', 'Chicken 65', 0.22], ['Mutton Biryani', 'Double ka Meetha', 0.35], ['Chicken Dum Biryani', 'Masala Chaas', 0.2]] },
  { name: 'Pizza Planet', cuisine: 'Pizza · Italian', area: 'Sector 62', distance_km: 4.8, prep_minutes: 20, cost_for_two: 600, veg: 0, theme: 'red',
    items: [
      ['Margherita Pizza', 'Mains', 249, 1, '🍕', 7, 'Classic tomato, mozzarella and basil'],
      ['Farmhouse Pizza', 'Mains', 349, 1, '🍕', 6, 'Capsicum, onion, mushroom and tomato'],
      ['Chicken Tikka Pizza', 'Mains', 399, 0, '🍕', 5, 'Tandoori chicken tikka with onions'],
      ['Penne Arrabbiata', 'Mains', 279, 1, '🍝', 3, 'Penne in spicy tomato sauce'],
      ['Garlic Breadsticks', 'Sides', 129, 1, '🥖', 1, 'Buttery breadsticks with herbs'],
      ['Cheesy Dip', 'Sides', 39, 1, '🧀', 1, 'Creamy cheese dip'],
      ['Coke (500 ml)', 'Beverages', 60, 1, '🥤', 1, 'Chilled soft drink'],
      ['Choco Lava Cake', 'Desserts', 109, 1, '🍫', 1, 'Warm cake with a molten centre'],
    ],
    pairs: [['Margherita Pizza', 'Garlic Breadsticks', 0.48], ['Farmhouse Pizza', 'Garlic Breadsticks', 0.45], ['Chicken Tikka Pizza', 'Coke (500 ml)', 0.5],
      ['Margherita Pizza', 'Coke (500 ml)', 0.35], ['Farmhouse Pizza', 'Choco Lava Cake', 0.3], ['Garlic Breadsticks', 'Cheesy Dip', 0.7], ['Penne Arrabbiata', 'Garlic Breadsticks', 0.4]] },
  { name: 'Punjabi Tadka', cuisine: 'North Indian', area: 'Sector 15', distance_km: 3.1, prep_minutes: 22, cost_for_two: 500, veg: 0, theme: 'orange',
    items: [
      ['Butter Chicken', 'Mains', 329, 0, '🍛', 8, 'Chicken in a rich tomato-butter gravy'],
      ['Paneer Butter Masala', 'Mains', 279, 1, '🧈', 7, 'Cottage cheese in creamy makhani gravy'],
      ['Dal Makhani', 'Mains', 229, 1, '🫘', 6, 'Black lentils simmered overnight'],
      ['Chole Bhature', 'Mains', 169, 1, '🫓', 5, 'Spiced chickpeas with fried bread'],
      ['Butter Naan', 'Breads', 49, 1, '🫓', 1, 'Tandoor-baked naan with butter'],
      ['Jeera Rice', 'Sides', 129, 1, '🍚', 1, 'Basmati rice tempered with cumin'],
      ['Sweet Lassi', 'Beverages', 79, 1, '🥛', 1, 'Thick Punjabi lassi'],
      ['Gulab Jamun (2 pcs)', 'Desserts', 69, 1, '🟤', 1, 'Warm milk dumplings in syrup'],
    ],
    pairs: [['Butter Chicken', 'Butter Naan', 0.75], ['Paneer Butter Masala', 'Butter Naan', 0.72], ['Dal Makhani', 'Jeera Rice', 0.55], ['Dal Makhani', 'Butter Naan', 0.4],
      ['Chole Bhature', 'Sweet Lassi', 0.45], ['Butter Chicken', 'Gulab Jamun (2 pcs)', 0.2]] },
  { name: 'Dosa Junction', cuisine: 'South Indian', area: 'Sector 50', distance_km: 3.6, prep_minutes: 15, cost_for_two: 350, veg: 1, theme: 'lime',
    items: [
      ['Masala Dosa', 'Mains', 139, 1, '🥞', 9, 'Crisp dosa with potato masala'],
      ['Mysore Masala Dosa', 'Mains', 159, 1, '🥞', 5, 'Dosa with spicy red chutney'],
      ['Idli Sambar (3 pcs)', 'Mains', 99, 1, '⚪', 6, 'Steamed rice cakes with sambar'],
      ['Medu Vada (2 pcs)', 'Starters', 89, 1, '🍩', 4, 'Crispy lentil doughnuts'],
      ['Uttapam', 'Mains', 149, 1, '🥘', 3, 'Thick dosa topped with onion and tomato'],
      ['Filter Coffee', 'Beverages', 49, 1, '☕', 2, 'South Indian decoction coffee'],
      ['Extra Sambar', 'Sides', 39, 1, '🥣', 1, 'A bowl of lentil sambar'],
      ['Rava Kesari', 'Desserts', 69, 1, '🟧', 1, 'Semolina sweet with ghee'],
    ],
    pairs: [['Masala Dosa', 'Filter Coffee', 0.55], ['Idli Sambar (3 pcs)', 'Medu Vada (2 pcs)', 0.5], ['Mysore Masala Dosa', 'Filter Coffee', 0.45],
      ['Idli Sambar (3 pcs)', 'Filter Coffee', 0.35], ['Masala Dosa', 'Extra Sambar', 0.25]] },
  { name: 'Wok This Way', cuisine: 'Chinese · Asian', area: 'Sector 44', distance_km: 5.2, prep_minutes: 18, cost_for_two: 450, veg: 0, theme: 'rose',
    items: [
      ['Veg Hakka Noodles', 'Mains', 189, 1, '🍜', 7, 'Wok-tossed noodles with vegetables'],
      ['Chicken Fried Rice', 'Mains', 219, 0, '🍚', 6, 'Egg and chicken fried rice'],
      ['Veg Manchurian', 'Starters', 199, 1, '🥟', 5, 'Vegetable balls in Manchurian sauce'],
      ['Chilli Chicken', 'Starters', 259, 0, '🌶️', 6, 'Indo-Chinese chilli chicken, dry'],
      ['Veg Momos (8 pcs)', 'Starters', 129, 1, '🥟', 6, 'Steamed dumplings with red chutney'],
      ['Hot & Sour Soup', 'Sides', 119, 1, '🍲', 2, 'Tangy, peppery soup'],
      ['Iced Lemon Tea', 'Beverages', 79, 1, '🧋', 1, 'Chilled lemon tea'],
    ],
    pairs: [['Veg Hakka Noodles', 'Veg Manchurian', 0.6], ['Chicken Fried Rice', 'Chilli Chicken', 0.55], ['Veg Momos (8 pcs)', 'Iced Lemon Tea', 0.3],
      ['Veg Hakka Noodles', 'Hot & Sour Soup', 0.25], ['Chilli Chicken', 'Veg Hakka Noodles', 0.3]] },
  { name: 'Burger Barn', cuisine: 'Burgers · Fast Food', area: 'Sector 125', distance_km: 1.2, prep_minutes: 12, cost_for_two: 400, veg: 0, theme: 'yellow',
    items: [
      ['Classic Veg Burger', 'Mains', 129, 1, '🍔', 7, 'Crispy veg patty, lettuce and mayo'],
      ['Crispy Chicken Burger', 'Mains', 179, 0, '🍔', 8, 'Fried chicken fillet with slaw'],
      ['Paneer Tikka Burger', 'Mains', 169, 1, '🍔', 5, 'Grilled paneer with mint mayo'],
      ['Peri Peri Fries', 'Sides', 109, 1, '🍟', 3, 'Fries tossed in peri peri spice'],
      ['Chicken Nuggets (6 pcs)', 'Starters', 149, 0, '🍗', 3, 'Golden fried nuggets'],
      ['Cold Coffee', 'Beverages', 119, 1, '🥤', 2, 'Blended coffee with ice cream'],
      ['Oreo Shake', 'Beverages', 139, 1, '🥤', 1, 'Thick shake with Oreo crumbs'],
    ],
    pairs: [['Classic Veg Burger', 'Peri Peri Fries', 0.58], ['Crispy Chicken Burger', 'Peri Peri Fries', 0.6], ['Crispy Chicken Burger', 'Cold Coffee', 0.3],
      ['Paneer Tikka Burger', 'Oreo Shake', 0.35], ['Classic Veg Burger', 'Cold Coffee', 0.25]] },
  { name: 'Chaat Corner', cuisine: 'Street Food', area: 'Sector 27', distance_km: 2.9, prep_minutes: 10, cost_for_two: 250, veg: 1, theme: 'emerald',
    items: [
      ['Pani Puri (8 pcs)', 'Starters', 69, 1, '🫧', 7, 'Crisp puris with tangy pani'],
      ['Aloo Tikki Chaat', 'Starters', 89, 1, '🥔', 6, 'Potato patties with chutneys and curd'],
      ['Pav Bhaji', 'Mains', 139, 1, '🍞', 7, 'Spiced vegetable mash with buttered pav'],
      ['Dahi Bhalla', 'Starters', 99, 1, '🥣', 4, 'Lentil dumplings in sweet curd'],
      ['Samosa (2 pcs)', 'Starters', 49, 1, '🥟', 5, 'Potato-filled pastry'],
      ['Masala Chai', 'Beverages', 29, 1, '🍵', 2, 'Ginger cardamom tea'],
      ['Extra Pav (2 pcs)', 'Breads', 30, 1, '🍞', 1, 'Buttered pav'],
    ],
    pairs: [['Pav Bhaji', 'Extra Pav (2 pcs)', 0.55], ['Samosa (2 pcs)', 'Masala Chai', 0.65], ['Aloo Tikki Chaat', 'Pani Puri (8 pcs)', 0.35], ['Dahi Bhalla', 'Pani Puri (8 pcs)', 0.3]] },
  { name: 'Sweet Tooth', cuisine: 'Desserts · Beverages', area: 'Sector 104', distance_km: 4.1, prep_minutes: 8, cost_for_two: 300, veg: 1, theme: 'pink',
    items: [
      ['Belgian Waffle', 'Desserts', 179, 1, '🧇', 6, 'Waffle with chocolate sauce'],
      ['Brownie with Ice Cream', 'Desserts', 159, 1, '🍨', 7, 'Warm walnut brownie and vanilla scoop'],
      ['Red Velvet Pastry', 'Desserts', 119, 1, '🍰', 4, 'Cream cheese frosted pastry'],
      ['Mango Smoothie', 'Beverages', 149, 1, '🥭', 4, 'Alphonso mango smoothie'],
      ['Hot Chocolate', 'Beverages', 139, 1, '☕', 3, 'Rich hot chocolate'],
      ['Vanilla Scoop', 'Desserts', 59, 1, '🍦', 1, 'Single scoop ice cream'],
    ],
    pairs: [['Belgian Waffle', 'Vanilla Scoop', 0.45], ['Belgian Waffle', 'Hot Chocolate', 0.3], ['Red Velvet Pastry', 'Hot Chocolate', 0.35]] },
];

const COUPONS = [
  ['WELCOME50', '50% off up to ₹100 on orders above ₹199', 'percent', 50, 100, 199, 1],
  ['FLAT75', 'Flat ₹75 off on orders above ₹399', 'flat', 75, null, 399, 1],
  ['FEAST20', '20% off up to ₹150 on orders above ₹599', 'percent', 20, 150, 599, 1],
  ['DIWALI100', 'Festive offer (expired)', 'flat', 100, null, 499, 0],
];

const FIRST = ['Aarav', 'Vivaan', 'Aditya', 'Ishaan', 'Kabir', 'Riya', 'Ananya', 'Diya', 'Saanvi', 'Meera', 'Rohan', 'Arjun', 'Kavya', 'Pooja', 'Neha', 'Rahul', 'Simran', 'Karan', 'Tanya', 'Yash'];
const LAST = ['Sharma', 'Verma', 'Gupta', 'Singh', 'Mehta', 'Iyer', 'Nair', 'Kapoor', 'Malhotra', 'Bansal', 'Chauhan', 'Reddy'];
const AREAS = ['Sector 125, Noida', 'Sector 62, Noida', 'Sector 18, Noida', 'Sector 50, Noida', 'Sector 137, Noida', 'Sector 76, Noida', 'Indirapuram, Ghaziabad'];

const ORDER_DAYS = 60;
const TOTAL_ORDERS = 1500;


// Brands onboarded recently: menus but no order history yet (inserted after the history, so earlier numbers do not change)
const NEW_BRANDS = [
  { name: "Roll Republic", cuisine: "Rolls · Wraps", area: "Sector 63", distance_km: 2.2, prep_minutes: 12, cost_for_two: 350, veg: 0, theme: "orange",
    items: [
      ["Chicken Kathi Roll", "Mains", 159, 0, "🌯", 1, "Flaky paratha rolled with chicken tikka and onions"],
      ["Paneer Tikka Roll", "Mains", 139, 1, "🌯", 1, "Smoky paneer tikka with mint chutney"],
      ["Egg Roll", "Mains", 99, 0, "🥚", 1, "Kolkata-style egg paratha roll"],
      ["Mutton Seekh Roll", "Mains", 189, 0, "🌯", 1, "Minced mutton seekh kebab in a roomali wrap"],
      ["Masala Fries", "Sides", 89, 1, "🍟", 1, "Crispy fries with chaat masala"],
      ["Nimbu Pani", "Beverages", 49, 1, "🍋", 1, "Fresh lime soda, sweet or salted"]] },
  { name: "Green Bowl Co.", cuisine: "Healthy · Salads", area: "Sector 44", distance_km: 3.4, prep_minutes: 10, cost_for_two: 500, veg: 1, theme: "emerald",
    items: [
      ["Garden Power Salad", "Mains", 279, 1, "🥗", 1, "Crunchy greens, cucumber, chickpeas and lemon-herb dressing"],
      ["Greek Salad", "Mains", 229, 1, "🥗", 1, "Cucumber, tomato, olives and feta"],
      ["Falafel Hummus Bowl", "Mains", 259, 1, "🧆", 1, "Falafel with hummus, pickles and pita"],
      ["Avocado Toast", "Starters", 199, 1, "🥑", 1, "Sourdough with smashed avocado and chilli flakes"],
      ["Fresh Fruit Bowl", "Desserts", 149, 1, "🍓", 1, "Seasonal cut fruits with honey"],
      ["Green Detox Smoothie", "Beverages", 169, 1, "🥤", 1, "Spinach, apple, banana and ginger"]] },
  { name: "Kebab Kingdom", cuisine: "Mughlai · Kebabs", area: "Sector 29", distance_km: 3.9, prep_minutes: 28, cost_for_two: 700, veg: 0, theme: "red",
    items: [
      ["Seekh Kebab (4 pcs)", "Starters", 299, 0, "🍢", 1, "Charcoal-grilled minced mutton skewers"],
      ["Galouti Kebab", "Starters", 329, 0, "🥩", 1, "Melt-in-the-mouth Lucknowi kebab"],
      ["Tandoori Chicken (Half)", "Mains", 349, 0, "🍗", 1, "Clay-oven roasted chicken with spices"],
      ["Paneer Tikka", "Starters", 259, 1, "🧀", 1, "Char-grilled cottage cheese with peppers"],
      ["Rumali Roti", "Breads", 39, 1, "🫓", 1, "Thin handkerchief bread"],
      ["Phirni", "Desserts", 99, 1, "🍮", 1, "Chilled ground-rice pudding"]] },
  { name: "Tokyo Tiffin", cuisine: "Japanese · Korean", area: "Sector 18", distance_km: 2.7, prep_minutes: 20, cost_for_two: 800, veg: 0, theme: "rose",
    items: [
      ["Veg Sushi Platter", "Mains", 399, 1, "🍣", 1, "Cucumber, avocado and carrot maki rolls"],
      ["Chicken Ramen", "Mains", 349, 0, "🍜", 1, "Rich broth, noodles, chicken and soft egg"],
      ["Kimchi Fried Rice", "Mains", 279, 1, "🍚", 1, "Spicy fermented cabbage fried rice"],
      ["Korean Fried Chicken", "Starters", 329, 0, "🍗", 1, "Double-fried chicken in gochujang glaze"],
      ["Veg Gyoza (6 pcs)", "Starters", 219, 1, "🥟", 1, "Pan-fried Japanese dumplings"],
      ["Matcha Latte", "Beverages", 189, 1, "🍵", 1, "Japanese green tea latte"]] },
  { name: "Taco Tierra", cuisine: "Mexican", area: "Sector 76", distance_km: 4.5, prep_minutes: 15, cost_for_two: 600, veg: 0, theme: "yellow",
    items: [
      ["Chicken Tacos (3 pcs)", "Mains", 269, 0, "🌮", 1, "Soft tacos with grilled chicken and salsa"],
      ["Bean Burrito", "Mains", 239, 1, "🌯", 1, "Rice, beans, cheese and pico de gallo"],
      ["Loaded Nachos", "Starters", 219, 1, "🧀", 1, "Nachos with cheese sauce, beans and jalapeños"],
      ["Cheese Quesadilla", "Mains", 229, 1, "🫓", 1, "Grilled tortilla with melted cheese and peppers"],
      ["Churros", "Desserts", 149, 1, "🍩", 1, "Cinnamon-sugar churros with chocolate dip"],
      ["Virgin Mojito", "Beverages", 129, 1, "🍹", 1, "Mint, lime and soda"]] },
  { name: "Thali House", cuisine: "Gujarati · Rajasthani", area: "Sector 50", distance_km: 3.2, prep_minutes: 18, cost_for_two: 450, veg: 1, theme: "lime",
    items: [
      ["Gujarati Thali", "Mains", 299, 1, "🍱", 1, "Dal, kadhi, two sabzis, rotli, rice and sweet"],
      ["Dal Baati Churma", "Mains", 249, 1, "🥘", 1, "Baked wheat balls with dal and sweet churma"],
      ["Khaman Dhokla", "Starters", 99, 1, "🟨", 1, "Steamed gram-flour cakes with green chutney"],
      ["Pyaaz Kachori (2 pcs)", "Starters", 79, 1, "🥟", 1, "Flaky onion-stuffed kachori"],
      ["Khandvi", "Starters", 109, 1, "🌀", 1, "Rolled gram-flour bites with mustard tadka"],
      ["Aam Panna", "Beverages", 69, 1, "🥭", 1, "Raw mango cooler with cumin"]] }];

function runSeed() {
  const insR = db.prepare('INSERT INTO restaurants (name,cuisine,area,distance_km,prep_minutes,cost_for_two,is_pure_veg,theme) VALUES (?,?,?,?,?,?,?,?)');
  const insI = db.prepare('INSERT INTO menu_items (restaurant_id,name,description,category,price,is_veg,emoji) VALUES (?,?,?,?,?,?,?)');
  for (const r of RESTAURANTS) {
    r.id = Number(insR.run(r.name, r.cuisine, r.area, r.distance_km, r.prep_minutes, r.cost_for_two, r.veg, r.theme).lastInsertRowid);
    r.byName = {};
    r.menu = r.items.map(([name, category, price, veg, emoji, w, desc]) => {
      const id = Number(insI.run(r.id, name, desc, category, price, veg, emoji).lastInsertRowid);
      const it = { id, name, category, price, w };
      r.byName[name] = it; return it;
    });
  }
  const insC = db.prepare('INSERT INTO coupons (code,description,type,value,max_discount,min_order,active) VALUES (?,?,?,?,?,?,?)');
  for (const c of COUPONS) insC.run(...c);

  const insU = db.prepare('INSERT INTO users (name,email,phone,password_hash,role,created_at) VALUES (?,?,?,?,?,?)');
  const earliest = new Date(Date.now() - (ORDER_DAYS + 5) * 864e5).toISOString();
  insU.run('Admin (Restaurant Ops)', 'admin@tastetrail.in', '9000000001', bcrypt.hashSync('admin123', 10), 'admin', earliest);
  const demoId = Number(insU.run('Tanvi', 'demo@tastetrail.in', '9000000002', bcrypt.hashSync('demo123', 10), 'customer', earliest).lastInsertRowid);
  const sharedHash = bcrypt.hashSync('password123', 8);
  const users = [{ id: demoId, w: 6, area: AREAS[0] }];
  for (let i = 0; i < 260; i++) {
    const name = `${FIRST[i % FIRST.length]} ${LAST[(i * 7) % LAST.length]}`;
    const id = Number(insU.run(name, `user${i + 1}@example.com`, `98${String(10000000 + i * 7919).slice(0, 8)}`, sharedHash, 'customer', earliest).lastInsertRowid);
    users.push({ id, w: 1 / (1 + i * 0.06) ** 1.4, area: AREAS[i % AREAS.length] }); // a few loyal customers, many occasional ones
  }

  const coupons = Object.fromEntries(COUPONS.map(([code, , type, value, max_discount, min_order, active]) => [code, { code, type, value, max_discount, min_order, active }]));
  const insO = db.prepare(`INSERT INTO orders (user_id,restaurant_id,subtotal,discount,delivery_fee,packaging_fee,tax,total,coupon_code,payment_method,payment_status,status,address,eta_minutes,created_at,delivered_at)
                           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
  const insOI = db.prepare('INSERT INTO order_items (order_id,menu_item_id,name,price,quantity) VALUES (?,?,?,?,?)');
  const insRv = db.prepare('INSERT INTO reviews (order_id,user_id,restaurant_id,rating,comment,created_at) VALUES (?,?,?,?,?,?)');

  // day weights: gentle growth + weekend boost
  const now = new Date();
  const todayIST = new Date(now.getTime() + 330 * 60000); todayIST.setUTCHours(0, 0, 0, 0);
  const days = [], dayW = [];
  for (let d = ORDER_DAYS - 1; d >= 1; d--) { // history up to yesterday
    const date = new Date(todayIST.getTime() - d * 864e5);
    const dow = date.getUTCDay();
    days.push(date); dayW.push((1 + (ORDER_DAYS - d) * 0.008) * (dow === 0 || dow === 6 ? 1.35 : dow === 5 ? 1.15 : 1));
  }
  const HOURS = [...Array(24).keys()];
  const hourW = [0.1, 0.05, 0, 0, 0, 0, 0.1, 0.4, 1.2, 1.4, 1, 1.3, 3.2, 4.2, 3, 1.6, 1.4, 1.8, 2.4, 3.6, 4.8, 4.6, 3, 1.2];
  const restW = [11, 9, 9, 7, 7, 8, 5, 4];
  const COMMENTS = { 5: ['Loved it!', 'Hot and fresh, quick delivery', 'Perfect taste', 'Will order again'], 4: ['Good food', 'Tasty, slightly late', 'Nice portion size'], 3: ['Average', 'Food was okay'], 2: ['Arrived cold', 'Too spicy'], 1: ['Very late delivery'] };

  function makeBasket(r) {
    const anchors = r.menu.filter((m) => m.w > 1);
    const anchor = pickWeighted(anchors, anchors.map((a) => a.w));
    const basket = new Map([[anchor.id, rand() < 0.18 ? 2 : 1]]);
    for (const [a, b, p] of r.pairs) if (a === anchor.name && rand() < p) basket.set(r.byName[b].id, rand() < 0.2 ? 2 : 1);
    // a second anchor sometimes, and its own add-ons
    if (rand() < 0.22) { const s = pickWeighted(anchors, anchors.map((a) => a.w)); basket.set(s.id, (basket.get(s.id) || 0) + 1);
      for (const [a, b, p] of r.pairs) if (a === s.name && rand() < p * 0.7) basket.set(r.byName[b].id, 1); }
    for (const m of r.menu) if (!basket.has(m.id) && rand() < 0.03) basket.set(m.id, 1); // random noise
    // cascade (e.g. breadsticks -> dip)
    for (const [a, b, p] of r.pairs) if (basket.has(r.byName[a].id) && !basket.has(r.byName[b].id) && r.menu.find((m) => m.name === a).w <= 1 && rand() < p) basket.set(r.byName[b].id, 1);
    return [...basket.entries()].map(([id, quantity]) => { const m = r.menu.find((x) => x.id === id); return { id, name: m.name, price: m.price, quantity }; });
  }

  function placeOrder(user, r, createdAt, status) {
    const lines = makeBasket(r);
    let q = computeQuote(lines, r, null);
    let couponCode = null;
    if (rand() < 0.28) {
      const eligible = Object.values(coupons).filter((c) => c.active && q.subtotal >= c.min_order);
      if (eligible.length) { const c = eligible[Math.floor(rand() * eligible.length)]; q = computeQuote(lines, r, c); couponCode = c.code; }
    }
    const pm = pickWeighted(['UPI', 'COD', 'CARD'], [52, 28, 20]);
    const deliveredAt = status === 'DELIVERED' ? new Date(+createdAt + (q.etaMinutes + Math.round((rand() - 0.35) * 18)) * 60000).toISOString() : null;
    const payStatus = status === 'CANCELLED' ? (pm === 'COD' ? 'PENDING' : 'REFUNDED') : pm === 'COD' && status !== 'DELIVERED' ? 'PENDING' : 'PAID';
    const oid = Number(insO.run(user.id, r.id, q.subtotal, q.discount, q.deliveryFee, q.packaging, q.tax, q.total, couponCode, pm, payStatus, status,
      user.area, q.etaMinutes, createdAt.toISOString(), deliveredAt).lastInsertRowid);
    for (const l of lines) insOI.run(oid, l.id, l.name, l.price, l.quantity);
    if (status === 'DELIVERED' && rand() < 0.55) {
      const late = (new Date(deliveredAt) - createdAt) / 60000 - q.etaMinutes;
      const rating = pickWeighted([5, 4, 3, 2, 1], late > 6 ? [20, 35, 25, 12, 8] : [52, 33, 10, 3, 2]);
      const cm = COMMENTS[rating]; insRv.run(oid, user.id, r.id, rating, rand() < 0.6 ? cm[Math.floor(rand() * cm.length)] : null, deliveredAt);
    }
    return oid;
  }

  for (let i = 0; i < TOTAL_ORDERS; i++) {
    const day = pickWeighted(days, dayW);
    const hour = pickWeighted(HOURS, hourW);
    const createdAt = new Date(+day + (hour * 60 + Math.floor(rand() * 60)) * 60000 - 330 * 60000); // IST → UTC
    const user = pickWeighted(users, users.map((u) => u.w));
    const r = pickWeighted(RESTAURANTS, restW);
    placeOrder(user, r, createdAt, rand() < 0.065 ? 'CANCELLED' : 'DELIVERED');
  }
  // a few live orders today so the admin board has work in it
  const live = ['PLACED', 'PLACED', 'CONFIRMED', 'PREPARING', 'PREPARING', 'OUT_FOR_DELIVERY'];
  live.forEach((st, k) => placeOrder(users[1 + k * 3], RESTAURANTS[k % RESTAURANTS.length], new Date(Date.now() - (40 - k * 6) * 60000), st));
  // recently onboarded brands go in last, so the ids and history above stay the same
  for (const r of NEW_BRANDS) {
    r.id = Number(insR.run(r.name, r.cuisine, r.area, r.distance_km, r.prep_minutes, r.cost_for_two, r.veg, r.theme).lastInsertRowid);
    for (const [name, category, price, veg, emoji, w, desc] of r.items) insI.run(r.id, name, desc, category, price, veg, emoji);
  }

}


// ===================== in-browser data store =====================
const DB_KEY = 'tt_demo_db_v3';
const T = { restaurants: [], menu_items: [], coupons: [], users: [], orders: [], order_items: [], reviews: [] };
let SEQ = {};

// tiny stand-in for the SQL driver used by the seeder: INSERT statements become array rows
const db = {
  prepare(sql) {
    const m = /INSERT INTO (\w+)\s*\(([^)]+)\)/i.exec(sql);
    const table = m[1], cols = m[2].split(',').map((c) => c.trim());
    return {
      run(...vals) {
        const row = {};
        cols.forEach((c, i) => (row[c] = vals[i]));
        if (table !== 'coupons') { SEQ[table] = (SEQ[table] || 0) + 1; row.id = SEQ[table]; }
        DEFAULTS[table]?.(row);
        T[table].push(row);
        return { lastInsertRowid: row.id };
      },
    };
  },
};
const nowIso = () => new Date().toISOString();
const DEFAULTS = {
  restaurants: (r) => { r.is_open = 1; r.is_pure_veg = r.is_pure_veg ? 1 : 0; },
  menu_items: (r) => { r.is_available = 1; },
  users: (r) => { r.role = r.role || 'customer'; r.created_at = r.created_at || nowIso(); },
  reviews: (r) => { r.created_at = r.created_at || nowIso(); },
  orders: (r) => { r.created_at = r.created_at || nowIso(); },
};
const bcrypt = { hashSync: (p) => 'h$' + hashStr(p) };
function hashStr(s) { let h = 5381; for (const ch of String(s)) h = ((h << 5) + h + ch.charCodeAt(0)) | 0; return (h >>> 0).toString(36); }

function seedDemo() {
  for (const k of Object.keys(T)) T[k] = [];
  SEQ = {};
  seed = 20261029;
  runSeed();
  persist();
}
function persist() { try { localStorage.setItem(DB_KEY, JSON.stringify({ T, SEQ, at: Date.now() })); } catch (e) { console.warn('demo storage full', e); } }
function load() {
  let raw = null;
  try { raw = JSON.parse(localStorage.getItem(DB_KEY) || 'null'); } catch { raw = null; }
  const stale = !raw || Date.now() - raw.at > 7 * 864e5 || /[?&]reset-demo\b/.test(location.search);
  if (stale) { seedDemo(); return; }
  Object.assign(T, raw.T); SEQ = raw.SEQ;
}

// ===================== helpers =====================
class ApiErr extends Error { constructor(status, msg) { super(msg); this.status = status; } }
const bad = (m) => new ApiErr(400, m), notFound = (m) => new ApiErr(404, m);
const B = (v) => !!v;
const byId = (t, id) => T[t].find((r) => r.id === Number(id));
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[6-9]\d{9}$/;
const userJson = (u) => ({ id: u.id, name: u.name, email: u.email, phone: u.phone || null, role: u.role });
const token = (u) => 'demo.' + btoa(unescape(encodeURIComponent(JSON.stringify({ id: u.id, role: u.role, name: u.name }))));
function whoAmI(tok) {
  if (!tok || !tok.startsWith('demo.')) return null;
  try { const c = JSON.parse(decodeURIComponent(escape(atob(tok.slice(5))))); const u = byId('users', c.id); return u && u.role === c.role ? u : null; } catch { return null; }
}
const needUser = (ctx) => { if (!ctx.user) throw new ApiErr(401, 'Please sign in'); return ctx.user; };
const needAdmin = (ctx) => { if (needUser(ctx).role !== 'admin') throw new ApiErr(403, 'Admin access only'); return ctx.user; };
const restaurantRow = (r) => ({ id: r.id, name: r.name, cuisine: r.cuisine, area: r.area, distance_km: r.distance_km, prep_minutes: r.prep_minutes,
  cost_for_two: r.cost_for_two, is_pure_veg: B(r.is_pure_veg), is_open: B(r.is_open), theme: r.theme });
const itemRow = (m) => ({ id: m.id, restaurant_id: m.restaurant_id, name: m.name, description: m.description, category: m.category, price: m.price,
  is_veg: B(m.is_veg), is_available: B(m.is_available), emoji: m.emoji });
function ratingOf(rid) {
  const rs = T.reviews.filter((v) => v.restaurant_id === rid);
  return { rating: rs.length ? Math.round((rs.reduce((s, v) => s + v.rating, 0) / rs.length) * 10) / 10 : null, rating_count: rs.length };
}
const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

let MODEL = null, MODEL_AT = -1;
function model() {
  const sig = T.orders.length + ':' + T.orders.filter((o) => o.status === 'CANCELLED').length + ':' + T.order_items.length;
  if (MODEL && MODEL_AT === sig) return MODEL;
  const cancelled = new Set(T.orders.filter((o) => o.status === 'CANCELLED').map((o) => o.id));
  const baskets = new Map();
  for (const l of T.order_items) { if (cancelled.has(l.order_id)) continue; if (!baskets.has(l.order_id)) baskets.set(l.order_id, []); baskets.get(l.order_id).push(l.menu_item_id); }
  MODEL = buildRules([...baskets.values()], { minPairCount: 5 }); MODEL_AT = sig;
  return MODEL;
}

function orderDetail(id) {
  const o = byId('orders', id); if (!o) return null;
  const r = byId('restaurants', o.restaurant_id), u = byId('users', o.user_id);
  const rv = T.reviews.find((v) => v.order_id === o.id);
  return { ...o, restaurant: r.name, restaurant_area: r.area, theme: r.theme,
    items: T.order_items.filter((l) => l.order_id === o.id).map((l) => ({ menu_item_id: l.menu_item_id, name: l.name, price: l.price, quantity: l.quantity })),
    review: rv ? { rating: rv.rating, comment: rv.comment } : null, customer: { name: u.name, phone: u.phone } };
}
const summary = (oid) => T.order_items.filter((l) => l.order_id === oid).map((l) => `${l.quantity} × ${l.name}`).join(', ');

function loadCart(body) {
  const r = byId('restaurants', body.restaurantId);
  if (!r) throw notFound('Restaurant not found');
  if (!r.is_open) throw bad('This restaurant is closed right now');
  if (!Array.isArray(body.items) || !body.items.length) throw bad('Your cart is empty');
  const lines = body.items.map((it) => {
    const m = T.menu_items.find((x) => x.id === Number(it.id) && x.restaurant_id === r.id);
    if (!m) throw bad('An item in your cart is not on this menu');
    if (!m.is_available) throw bad(`${m.name} is currently unavailable`);
    const q = Number(it.quantity);
    if (!Number.isInteger(q)) throw bad(`Invalid quantity for ${m.name}`);
    return { id: m.id, name: m.name, price: m.price, quantity: q }; // price from the menu, never from the request
  });
  return { r, lines };
}
const findCoupon = (code) => (code ? T.coupons.find((c) => c.code === String(code).trim().toUpperCase()) || null : null);
function quote(r, lines, c) {
  try { return computeQuote(lines, r, c ? { ...c, active: B(c.active) } : null); }
  catch (e) { throw bad(e.message === 'Cart is empty' ? 'Your cart is empty' : e.message); }
}

// ===================== routes (same URLs and JSON as the Java API) =====================
const routes = [];
const route = (method, path, fn) => routes.push({ method, re: new RegExp('^' + path.replace(/:\w+/g, '([^/]+)') + '$'), fn });

route('GET', '/health', () => ({ ok: true, orders: T.orders.length, mode: 'browser-demo' }));

route('POST', '/auth/register', (c) => {
  const { name, email, phone, password } = c.body;
  if (!name?.trim() || !EMAIL.test(email || '')) throw bad('Enter your name and a valid email');
  if (!password || password.length < 6) throw bad('Password must be at least 6 characters');
  if (phone && !PHONE.test(phone)) throw bad('Enter a valid 10-digit mobile number');
  const e = email.toLowerCase();
  if (T.users.some((u) => u.email === e)) throw new ApiErr(409, 'An account with this email already exists');
  const id = db.prepare('INSERT INTO users (name,email,phone,password_hash,role)').run(name.trim(), e, phone || null, bcrypt.hashSync(password), 'customer').lastInsertRowid;
  persist(); c.status = 201;
  const u = byId('users', id); return { token: token(u), user: userJson(u) };
});
route('POST', '/auth/login', (c) => {
  const u = T.users.find((x) => x.email === String(c.body.email || '').toLowerCase());
  if (!u || u.password_hash !== bcrypt.hashSync(c.body.password || '')) throw new ApiErr(401, 'Incorrect email or password');
  return { token: token(u), user: userJson(u) };
});
route('GET', '/auth/me', (c) => userJson(needUser(c)));

route('GET', '/restaurants', (c) => {
  const term = (c.q.q || '').toLowerCase().trim(), cuisine = (c.q.cuisine || '').toLowerCase(), veg = c.q.veg === '1';
  const counts = {};
  for (const o of T.orders) if (o.status !== 'CANCELLED') counts[o.restaurant_id] = (counts[o.restaurant_id] || 0) + 1;
  const hits = new Set(term ? T.menu_items.filter((m) => m.name.toLowerCase().includes(term)).map((m) => m.restaurant_id) : []);
  let rows = T.restaurants.filter((r) => (!term || r.name.toLowerCase().includes(term) || r.cuisine.toLowerCase().includes(term) || hits.has(r.id))
    && (!cuisine || r.cuisine.toLowerCase().includes(cuisine)) && (!veg || r.is_pure_veg))
    .map((r) => ({ ...restaurantRow(r), ...ratingOf(r.id), orders: counts[r.id] || 0, eta_minutes: estimateEtaMinutes(r.prep_minutes, r.distance_km, 1) }));
  const sorters = { rating: (a, b) => (b.rating || 0) - (a.rating || 0), fastest: (a, b) => a.eta_minutes - b.eta_minutes, cost: (a, b) => a.cost_for_two - b.cost_for_two };
  return rows.sort(sorters[c.q.sort] || ((a, b) => b.orders - a.orders));
});
route('GET', '/restaurants/:id', (c) => {
  const r = byId('restaurants', c.p[0]); if (!r) throw notFound('Restaurant not found');
  const items = T.menu_items.filter((m) => m.restaurant_id === r.id).sort((a, b) => cmp(a.category, b.category) || a.id - b.id).map(itemRow);
  const best = new Set(popular(model(), { k: 3, allowed: new Set(items.map((i) => i.id)) }).map((p) => p.item));
  items.forEach((it) => (it.bestseller = best.has(it.id)));
  const reviews = T.reviews.filter((v) => v.restaurant_id === r.id && v.comment).sort((a, b) => cmp(b.created_at, a.created_at)).slice(0, 4)
    .map((v) => ({ rating: v.rating, comment: v.comment, created_at: v.created_at, name: byId('users', v.user_id).name }));
  return { ...restaurantRow(r), ...ratingOf(r.id), items, reviews };
});
route('GET', '/coupons', () => T.coupons.filter((x) => x.active).sort((a, b) => a.min_order - b.min_order).map((x) => ({ code: x.code, description: x.description, min_order: x.min_order })));
route('GET', '/popular-dishes', () => popular(model(), { k: 8 }).map((p) => { const m = byId('menu_items', p.item); return { ...itemRow(m), restaurant: byId('restaurants', m.restaurant_id).name, orders: p.count }; }));
route('POST', '/cart/quote', (c) => {
  const { r, lines } = loadCart(c.body);
  const cp = findCoupon(c.body.coupon);
  const q = quote(r, lines, cp);
  if (c.body.coupon && !cp) q.couponError = 'Invalid coupon code';
  return q;
});
route('GET', '/recommendations', (c) => {
  const ids = String(c.q.items || '').split(',').map(Number).filter(Boolean);
  const avail = T.menu_items.filter((m) => m.restaurant_id === Number(c.q.restaurantId) && m.is_available);
  const allowed = new Set(avail.map((m) => m.id)), m = model();
  let recs = recommend(m, ids, { k: 3, allowed, minLift: 1.2 }).map((r) => ({ ...r, reason: 'frequently ordered together' }));
  if (recs.length < 3) recs = recs.concat(popular(m, { k: 3 - recs.length, allowed, exclude: [...ids, ...recs.map((r) => r.item)] }).map((p) => ({ item: p.item, reason: 'popular at this restaurant' })));
  return recs.map((r) => ({ ...itemRow(byId('menu_items', r.item)), confidence: r.confidence ?? null, lift: r.lift ?? null, reason: r.reason, because: r.because ? byId('menu_items', r.because).name : null }));
});

// ===================== TasteTrail-only features (same rules as the Java explore package) =====================
const has = (t, ws) => ws.some((w) => t.includes(w));
function dishTags(d) {
  const n = d.name.toLowerCase(), c = d.cuisine.toLowerCase(), t = new Set();
  if (has(n, ['chilli', 'peri', '65', 'mirchi', 'kimchi', 'seekh', 'tikka', 'tandoori', 'pani puri', 'korean', 'arrabbiata', 'hot & sour', 'masala dosa', 'mysore', 'kathi', 'kebab', 'tacos'])) t.add('SPICY');
  if (has(n, ['biryani', 'dal', 'butter', 'makhani', 'chole', 'pav bhaji', 'ramen', 'noodles', 'fried rice', 'pizza', 'thali', 'baati', 'burrito', 'lava', 'brownie', 'hot chocolate', 'chai', 'naan'])) t.add('COMFORT');
  if (has(n, ['salad', 'idli', 'dhokla', 'khandvi', 'sushi', 'smoothie', 'fruit', 'soup', 'uttapam', 'avocado', 'falafel', 'raita', 'chaas', 'lemon', 'nimbu', 'panna', 'filter coffee', 'matcha'])) t.add('LIGHT');
  if (d.category === 'Desserts' || has(n, ['shake', 'lassi', 'waffle', 'kesari', 'jamun', 'meetha', 'phirni', 'churros', 'smoothie', 'hot chocolate'])) t.add('SWEET');
  if (d.category === 'Beverages') t.add('DRINK');
  if (d.category === 'Mains' && d.price >= 199) t.add('FILLING');
  if (c.includes('street') || has(n, ['roll', 'samosa', 'chaat', 'momos', 'kachori', 'vada', 'fries', 'burger'])) t.add('STREET');
  if (has(c, ['italian', 'chinese', 'japanese', 'korean', 'mexican', 'healthy'])) t.add('GLOBAL');
  if (has(n, ['tikka', 'tandoori', 'seekh', 'kebab', 'grilled'])) t.add('GRILLED');
  return t;
}
const halfServings = (d) => (d.category === 'Mains' ? 2 : d.category === 'Starters' ? 1 : 0);
const totalFor = (food, km) => round2(food + round2(food * PRICING_RULES.gstRate) + deliveryFee(km, food) + PRICING_RULES.packagingFee);

function allDishes() {
  const counts = new Map();
  for (const l of T.order_items) counts.set(l.menu_item_id, (counts.get(l.menu_item_id) || 0) + 1);
  const out = [];
  for (const r of T.restaurants.filter((x) => x.is_open).sort((a, b) => a.id - b.id))
    for (const m of T.menu_items.filter((x) => x.restaurant_id === r.id && x.is_available).sort((a, b) => a.id - b.id))
      out.push({ id: m.id, name: m.name, category: m.category, price: m.price, veg: !!m.is_veg, emoji: m.emoji, restaurantId: r.id, restaurant: r.name,
        cuisine: r.cuisine, distanceKm: r.distance_km, prepMinutes: r.prep_minutes, orders: counts.get(m.id) || 0 });
  return out;
}
const dishJson = (d) => ({ id: d.id, name: d.name, category: d.category, price: d.price, is_veg: d.veg, emoji: d.emoji, restaurant_id: d.restaurantId, restaurant: d.restaurant, cuisine: d.cuisine });

// ---- Budget Feast Planner: bounded knapsack by dynamic programming (see FeastPlanner.java) ----
const BASE_VALUE = { Mains: 10, Starters: 6, Desserts: 5, Beverages: 4, Sides: 3, Breads: 2.5 };
function planOne(menu, people, budget, vegOnly) {
  const dishes = menu.filter((d) => !vegOnly || d.veg);
  if (!dishes.length) return null;
  const km = dishes[0].distanceKm, maxOrders = Math.max(...dishes.map((d) => d.orders));
  let C = Math.floor(budget / 1.05);
  while (C > 0 && totalFor(C, km) > budget) C--;
  if (C <= 0) return null;
  const cd = [], cv = [];
  for (const d of dishes) {
    const maxCopies = halfServings(d) > 0 ? people : Math.min(people, 3);
    let v = (BASE_VALUE[d.category] ?? 3) * (1 + 0.5 * (maxOrders === 0 ? 0 : d.orders / maxOrders));
    for (let k = 0; k < maxCopies && d.price * (k + 1) <= C; k++) { cd.push(d); cv.push(v); v *= 0.7; }
  }
  const target = 2 * people, n = cd.length, W = C + 1;
  const best = []; for (let s = 0; s <= target; s++) best.push(new Float64Array(W).fill(-Infinity));
  best[0][0] = 0;
  const from = [];
  for (let i = 0; i < n; i++) {
    const cost = Math.round(cd[i].price), hs = halfServings(cd[i]), val = cv[i];
    const f = new Int8Array((target + 1) * W).fill(-1); from.push(f);
    for (let s = target; s >= 0; s--) {
      const lo = s === target ? Math.max(0, target - hs) : s - hs, hi = s === target ? target : s - hs;
      if (hi < 0) continue;
      const row = best[s];
      for (let c = C; c >= cost; c--)
        for (let ps = lo; ps <= hi; ps++) {
          const cand = best[ps][c - cost];
          if (cand === -Infinity) continue;
          if (cand + val > row[c] + 1e-9) { row[c] = cand + val; f[s * W + c] = ps; }
        }
    }
  }
  let bc = -1;
  for (let c = 0; c <= C; c++) if (best[target][c] > -Infinity && (bc < 0 || best[target][c] > best[target][bc] + 1e-9)) bc = c;
  if (bc < 0) return null;
  const qty = new Map(); let s = target, c = bc;
  for (let i = n - 1; i >= 0; i--) { const ps = from[i][s * W + c]; if (ps < 0) continue; qty.set(cd[i], (qty.get(cd[i]) || 0) + 1); s = ps; c -= Math.round(cd[i].price); }
  const lines = [...qty.entries()].map(([d, q]) => ({ d, q })).sort((a, b) => halfServings(b.d) - halfServings(a.d) || b.d.price - a.d.price);
  const food = round2(lines.reduce((t, l) => t + l.d.price * l.q, 0)), total = totalFor(food, km);
  return { restaurant_id: dishes[0].restaurantId, restaurant: dishes[0].restaurant, items: lines.map((l) => ({ ...dishJson(l.d), quantity: l.q })),
    food, total, spare: round2(budget - total), servings: lines.reduce((t, l) => t + halfServings(l.d) * l.q, 0) / 2, value: Math.round(best[target][bc] * 10) / 10 };
}
route('GET', '/explore/plan', (c) => {
  const people = parseInt(c.q.people || '2', 10), budget = Number(c.q.budget || 600), veg = c.q.veg === '1';
  if (!(people >= 1 && people <= 12)) throw bad('Choose between 1 and 12 people');
  if (!(budget >= 100 && budget <= 20000)) throw bad('Budget must be between ₹100 and ₹20,000');
  const menus = new Map();
  for (const d of allDishes()) { if (!menus.has(d.restaurantId)) menus.set(d.restaurantId, []); menus.get(d.restaurantId).push(d); }
  const plans = [...menus.values()].map((m) => planOne(m, people, budget, veg)).filter(Boolean).sort((a, b) => b.value - a.value).slice(0, 3);
  return { people, budget, plans };
});

// ---- Mood Match (see MoodMatcher.java) ----
const MOODS = {
  COMFORT: { label: 'Comfort food', emoji: '🛋️', w: { COMFORT: 5, FILLING: 2, SWEET: 1 } },
  HEALTHY: { label: 'Light & fresh', emoji: '🥗', w: { LIGHT: 6, GRILLED: 2, FILLING: -1 } },
  ADVENTUROUS: { label: 'Adventurous', emoji: '🧭', w: { GLOBAL: 4, STREET: 1.5, SPICY: 1 } },
  CELEBRATE: { label: 'Celebration', emoji: '🎉', w: { FILLING: 3, SWEET: 3, GRILLED: 2 } },
  QUICK: { label: 'Quick bite', emoji: '⚡', w: { STREET: 4, LIGHT: 1, FILLING: -2 } },
};
const triedCuisines = (u) => new Set(u ? T.orders.filter((o) => o.user_id === u.id && o.status !== 'CANCELLED').map((o) => byId('restaurants', o.restaurant_id).cuisine) : []);
route('GET', '/explore/mood', (c) => {
  const key = String(c.q.mood || 'comfort').toUpperCase(), mood = MOODS[key] || MOODS.COMFORT;
  const spice = Math.max(0, Math.min(2, parseInt(c.q.spice || '1', 10))), maxPrice = Number(c.q.budget || 400), veg = c.q.veg === '1';
  const tried = triedCuisines(c.user), dishes = allDishes();
  const maxOrders = Math.max(1, ...dishes.map((d) => d.orders));
  const all = [];
  for (const d of dishes) {
    if (veg && !d.veg) continue;
    if (d.price > maxPrice || d.category === 'Sides' || d.category === 'Breads') continue;
    const tags = dishTags(d), why = []; let score = 0;
    for (const [t, w] of Object.entries(mood.w)) if (tags.has(t)) score += w;
    if (score > 0) why.push(`${mood.emoji} fits your ${mood.label.toLowerCase()} mood`);
    if (tags.has('SPICY')) { if (spice === 2) { score += 3; why.push('🌶️ nice and spicy'); } else if (spice === 0) score -= 4; }
    else if (spice === 0) { score += 1; why.push('😌 mild'); }
    const minutes = estimateEtaMinutes(d.prepMinutes, d.distanceKm, 1);
    if (mood === MOODS.QUICK && minutes <= 30) { score += 3; why.push(`⚡ arrives in ~${minutes} min`); }
    const isNew = tried.size > 0 && !tried.has(d.cuisine);
    if (isNew && mood === MOODS.ADVENTUROUS) { score += 4; why.push("🆕 a cuisine you haven't tried yet"); } else if (isNew) score += 0.5;
    const pop = d.orders / maxOrders;
    score += 2 * pop; if (pop > 0.5) why.push('🔥 crowd favourite');
    if (d.price <= maxPrice * 0.6) { score += 0.5; why.push('💰 well within budget'); }
    if (score > 0) all.push({ d, score: Math.round(score * 10) / 10, why, minutes });
  }
  all.sort((a, b) => b.score - a.score || a.d.price - b.d.price);
  const per = new Map(), matches = [];
  for (const m of all) {
    per.set(m.d.restaurantId, (per.get(m.d.restaurantId) || 0) + 1);
    if (per.get(m.d.restaurantId) > 2) continue;
    matches.push({ ...dishJson(m.d), score: m.score, reasons: m.why, eta_minutes: m.minutes });
    if (matches.length === 8) break;
  }
  return { mood: mood.label, emoji: mood.emoji, matches };
});

// ---- Taste Passport (see TastePassport.java) ----
const LEVELS = ['Taste Rookie', 'Food Explorer', 'Flavour Hunter', 'Cuisine Connoisseur', 'TasteTrail Legend'], LEVEL_XP = [0, 150, 600, 1800, 4000];
const TIERS = ['', 'Bronze', 'Silver', 'Gold'];
function badge(id, emoji, name, description, tiers, count) {
  let tier = 0; while (tier < tiers.length && count >= tiers[tier]) tier++;
  const target = tiers[Math.min(tier, tiers.length - 1)];
  return { id, emoji, name, description, progress: Math.min(count, target), target, earned: tier > 0, tier, tierName: TIERS[tier] };
}
const istParts = (iso) => { const d = new Date(new Date(iso).getTime() + 330 * 60000); return { hour: d.getUTCHours(), dow: d.getUTCDay() }; };
route('GET', '/me/passport', (c) => {
  const u = needUser(c);
  const dishById = new Map(allDishes().map((d) => [d.id, d]));
  const places = [...T.restaurants].sort((a, b) => b.id - a.id).map((r) => ({ id: r.id, name: r.name, cuisine: r.cuisine }));
  const cuisineOf = new Map(places.map((p) => [p.id, p.cuisine]));
  const orders = T.orders.filter((o) => o.user_id === u.id && o.status === 'DELIVERED').sort((a, b) => cmp(a.created_at, b.created_at)).map((o) => ({
    restaurantId: o.restaurant_id, ...istParts(o.created_at), rated: T.reviews.some((v) => v.order_id === o.id),
    items: T.order_items.filter((l) => l.order_id === o.id).map((l) => dishById.get(l.menu_item_id)).filter(Boolean) }));
  let xp = 0; const per = new Map(), seen = new Set();
  for (const o of orders) {
    xp += 10 + (o.rated ? 5 : 0);
    const cu = cuisineOf.get(o.restaurantId) || '?'; if (!seen.has(cu)) { seen.add(cu); xp += 25; }
    per.set(o.restaurantId, (per.get(o.restaurantId) || 0) + 1);
  }
  const stamps = places.map((p) => ({ restaurantId: p.id, restaurant: p.name, cuisine: p.cuisine, collected: per.has(p.id), orders: per.get(p.id) || 0 }));
  const collected = stamps.filter((s) => s.collected).length;
  const anyTag = (o, t) => o.items.some((d) => dishTags(d).has(t));
  const count = (f) => orders.filter(f).length;
  const badges = [
    badge('night-owl', '🦉', 'Night Owl', 'Orders after 10 pm', [3, 15, 40], count((o) => o.hour >= 22 || o.hour < 4)),
    badge('early-bird', '🌅', 'Early Bird', 'Orders before 11 am', [3, 10, 25], count((o) => o.hour >= 6 && o.hour < 11)),
    badge('spice-lord', '🌶️', 'Spice Lord', 'Orders with a spicy dish', [5, 25, 100], count((o) => anyTag(o, 'SPICY'))),
    badge('sweet-tooth', '🍰', 'Sweet Tooth', 'Orders with a dessert', [5, 20, 60], count((o) => o.items.some((d) => d.category === 'Desserts'))),
    badge('weekend', '🎉', 'Weekend Feaster', 'Orders on Saturday or Sunday', [5, 25, 80], count((o) => o.dow === 0 || o.dow === 6)),
    badge('critic', '✍️', 'Food Critic', 'Orders you rated', [5, 25, 150], count((o) => o.rated)),
    badge('healthy', '🥗', 'Green Streak', 'Orders with something light', [3, 15, 50], count((o) => anyTag(o, 'LIGHT'))),
    badge('globetrotter', '🌍', 'Globetrotter', 'Cuisine stamps collected', [3, 8, places.length], collected),
    badge('regular', '🏠', 'Loyal Regular', 'Orders from your favourite restaurant', [5, 20, 50], Math.max(0, ...per.values())),
  ];
  let k = 0; LEVEL_XP.forEach((x, i) => { if (xp >= x) k = i; });
  const challenge = places.find((p) => !per.has(p.id)) || null;
  return { xp, level: { number: k + 1, name: LEVELS[k], fromXp: LEVEL_XP[k], nextXp: k + 1 < LEVEL_XP.length ? LEVEL_XP[k + 1] : null }, ordersCount: orders.length,
    stamps, stampsCollected: collected, badges, challenge,
    challengeText: challenge ? `Order from ${challenge.name} to collect the ${challenge.cuisine} stamp and earn +35 XP` : "You've collected every stamp – legend status!" };
});

// ---- Fair Bill Split (see BillSplitter.java) ----
route('POST', '/cart/split', (c) => {
  const { r, lines } = loadCart(c.body);
  const bill = quote(r, lines, findCoupon(c.body.coupon));
  const people = (Array.isArray(c.body.people) ? c.body.people : []).map((p) => String(p ?? '').trim()).filter(Boolean);
  if (people.length < 2 || people.length > 10) throw bad('Split between 2 and 10 people');
  const n = people.length, shares = [];
  let sum = 0;
  if (c.body.mode === 'items' && c.body.assign && typeof c.body.assign === 'object') {
    const food = new Array(n).fill(0), items = people.map(() => []);
    for (const l of lines) {
      let who = [...new Set((c.body.assign[l.id] || []).map(Number).filter((p) => p >= 0 && p < n))];
      if (!who.length) who = people.map((_, i) => i);
      const part = (l.price * l.quantity) / who.length;
      for (const p of who) { food[p] += part; items[p].push(who.length === 1 ? `${l.quantity} × ${l.name}` : `${l.name} (shared ÷${who.length})`); }
    }
    const extras = bill.total - bill.subtotal;
    for (let i = 0; i < n; i++) {
      const ratio = bill.subtotal === 0 ? 1 / n : food[i] / bill.subtotal;
      const pay = i === n - 1 ? round2(bill.total - sum) : round2(food[i] + extras * ratio);
      sum += pay; shares.push({ person: people[i], food: round2(food[i]), extras: round2(pay - food[i]), pay, items: items[i] });
    }
  } else {
    const each = round2(bill.total / n);
    for (let i = 0; i < n; i++) {
      const pay = i === n - 1 ? round2(bill.total - sum) : each; sum += pay;
      shares.push({ person: people[i], food: round2(bill.subtotal / n), extras: round2(pay - bill.subtotal / n), pay, items: ['Equal share'] });
    }
  }
  return { total: bill.total, shares };
});

route('POST', '/orders', (c) => {
  const u = needUser(c);
  if (u.role === 'admin') throw new ApiErr(403, 'Administrators cannot place orders');
  const { paymentMethod, address, coupon } = c.body;
  if (!['UPI', 'CARD', 'COD'].includes(paymentMethod)) throw bad('Choose a payment method');
  if (!address || String(address).trim().length < 8) throw bad('Enter a complete delivery address');
  const { r, lines } = loadCart(c.body);
  const cp = findCoupon(coupon), q = quote(r, lines, cp);
  if (coupon && (!cp || q.couponError)) throw bad(q.couponError || 'Invalid coupon code');
  const oid = db.prepare('INSERT INTO orders (user_id,restaurant_id,subtotal,discount,delivery_fee,packaging_fee,tax,total,coupon_code,payment_method,payment_status,status,address,eta_minutes,created_at,delivered_at)')
    .run(u.id, r.id, q.subtotal, q.discount, q.deliveryFee, q.packaging, q.tax, q.total, q.couponApplied, paymentMethod, paymentMethod === 'COD' ? 'PENDING' : 'PAID', 'PLACED', String(address).trim(), q.etaMinutes, nowIso(), null).lastInsertRowid;
  const ins = db.prepare('INSERT INTO order_items (order_id,menu_item_id,name,price,quantity)');
  for (const l of lines) ins.run(oid, l.id, l.name, l.price, l.quantity);
  persist(); c.status = 201;
  return orderDetail(oid);
});
route('GET', '/orders', (c) => {
  const u = needUser(c);
  return T.orders.filter((o) => o.user_id === u.id).sort((a, b) => cmp(b.created_at, a.created_at)).slice(0, 50).map((o) => {
    const r = byId('restaurants', o.restaurant_id);
    return { id: o.id, total: o.total, status: o.status, created_at: o.created_at, payment_method: o.payment_method, restaurant: r.name, theme: r.theme,
      rating: T.reviews.find((v) => v.order_id === o.id)?.rating ?? null, summary: summary(o.id) };
  });
});
route('GET', '/orders/:id', (c) => {
  const u = needUser(c), o = orderDetail(c.p[0]);
  if (!o || (o.user_id !== u.id && u.role !== 'admin')) throw notFound('Order not found');
  return o;
});
const ownOrder = (c) => { const u = needUser(c), o = byId('orders', c.p[0]); if (!o || o.user_id !== u.id) throw notFound('Order not found'); return o; };
route('POST', '/orders/:id/cancel', (c) => {
  const o = ownOrder(c);
  if (!customerCanCancel(o.status)) throw bad('This order is already being prepared and cannot be cancelled');
  o.status = 'CANCELLED'; if (o.payment_status === 'PAID') o.payment_status = 'REFUNDED';
  persist(); return orderDetail(o.id);
});
route('POST', '/orders/:id/review', (c) => {
  const o = ownOrder(c), rating = Number(c.body.rating);
  if (o.status !== 'DELIVERED') throw bad('You can rate an order after it is delivered');
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) throw bad('Rating must be 1 to 5 stars');
  if (T.reviews.some((v) => v.order_id === o.id)) throw new ApiErr(409, 'You have already rated this order');
  db.prepare('INSERT INTO reviews (order_id,user_id,restaurant_id,rating,comment)').run(o.id, o.user_id, o.restaurant_id, rating, c.body.comment?.trim() || null);
  persist(); c.status = 201; return orderDetail(o.id);
});

route('GET', '/admin/orders', (c) => {
  needAdmin(c);
  const st = c.q.status || 'ACTIVE';
  return T.orders.filter((o) => (st === 'ACTIVE' ? !['DELIVERED', 'CANCELLED'].includes(o.status) : STATUSES.includes(st) ? o.status === st : true))
    .sort((a, b) => cmp(b.created_at, a.created_at)).slice(0, 60)
    .map((o) => ({ id: o.id, total: o.total, status: o.status, created_at: o.created_at, payment_method: o.payment_method, payment_status: o.payment_status,
      address: o.address, eta_minutes: o.eta_minutes, restaurant: byId('restaurants', o.restaurant_id).name, customer: byId('users', o.user_id).name, summary: summary(o.id) }));
});
route('PATCH', '/admin/orders/:id/status', (c) => {
  needAdmin(c);
  const o = byId('orders', c.p[0]), st = c.body.status;
  if (!o) throw notFound('Order not found');
  if (!canTransition(o.status, st)) throw bad(`Cannot move an order from ${o.status} to ${st}`);
  o.status = st;
  if (st === 'DELIVERED') { o.delivered_at = nowIso(); o.payment_status = 'PAID'; }
  if (st === 'CANCELLED' && o.payment_status === 'PAID') o.payment_status = 'REFUNDED';
  persist(); return orderDetail(o.id);
});
route('GET', '/admin/menu', (c) => {
  needAdmin(c);
  const rid = Number(c.q.restaurantId || 1), sold = {};
  const ok = new Set(T.orders.filter((o) => o.status !== 'CANCELLED' && o.restaurant_id === rid).map((o) => o.id));
  for (const l of T.order_items) if (ok.has(l.order_id)) sold[l.menu_item_id] = (sold[l.menu_item_id] || 0) + l.quantity;
  return { restaurants: T.restaurants.map((r) => ({ id: r.id, name: r.name, is_open: B(r.is_open) })),
    items: T.menu_items.filter((m) => m.restaurant_id === rid).sort((a, b) => cmp(a.category, b.category) || a.id - b.id).map((m) => ({ ...itemRow(m), sold: sold[m.id] || 0 })) };
});
route('POST', '/admin/menu', (c) => {
  needAdmin(c);
  const { restaurantId, name, description, category, price, isVeg, emoji } = c.body;
  if (!name?.trim() || !category || !(Number(price) > 0) || !byId('restaurants', restaurantId)) throw bad('Name, category and a positive price are required');
  const id = db.prepare('INSERT INTO menu_items (restaurant_id,name,description,category,price,is_veg,emoji)').run(Number(restaurantId), name.trim(), description || null, category, Number(price), isVeg === false ? 0 : 1, emoji || '🍽️').lastInsertRowid;
  persist(); c.status = 201; return itemRow(byId('menu_items', id));
});
route('PATCH', '/admin/menu/:id', (c) => {
  needAdmin(c);
  const m = byId('menu_items', c.p[0]); if (!m) throw notFound('Item not found');
  if ('price' in c.body) { if (!(Number(c.body.price) > 0)) throw bad('Price must be positive'); m.price = Number(c.body.price); }
  if (typeof c.body.isAvailable === 'boolean') m.is_available = c.body.isAvailable ? 1 : 0;
  persist(); return itemRow(m);
});
route('PATCH', '/admin/restaurants/:id', (c) => { needAdmin(c); const r = byId('restaurants', c.p[0]); if (r) { r.is_open = c.body.isOpen ? 1 : 0; persist(); } return { ok: true }; });
route('GET', '/admin/analytics', (c) => {
  needAdmin(c);
  const days = Math.min(90, Math.max(7, Number(c.q.days) || 30));
  const startKey = new Date(+new Date(dayKey(new Date()) + 'T00:00:00Z') - (days - 1) * 864e5);
  const since = new Date(+startKey - 330 * 60000).toISOString();
  const orders = T.orders.filter((o) => o.created_at >= since);
  const valid = orders.filter((o) => o.status !== 'CANCELLED'), okIds = new Set(valid.map((o) => o.id));
  const cat = Object.fromEntries(T.menu_items.map((m) => [m.id, m.category]));
  const lines = T.order_items.filter((l) => okIds.has(l.order_id)).map((l) => ({ ...l, category: cat[l.menu_item_id] }));
  const names = Object.fromEntries(T.restaurants.map((r) => [r.id, r.name])), itemNames = Object.fromEntries(T.menu_items.map((m) => [m.id, m.name]));
  const daily = dailyRevenue(orders, days), m = model();
  const rules = [...m.rules.entries()].flatMap(([from, rs]) => rs.map((r) => ({ from: itemNames[from], to: itemNames[r.item], ...r })))
    .filter((r) => r.count >= 20).sort((a, b) => b.lift - a.lift).slice(0, 8)
    .map((r) => ({ from: r.from, to: r.to, count: r.count, support: +(r.support * 100).toFixed(2), confidence: +(r.confidence * 100).toFixed(1), lift: +r.lift.toFixed(2) }));
  const rd = ratingDistribution(T.reviews.filter((v) => v.created_at >= since).map((v) => v.rating));
  return { days, kpis: kpis(orders), daily, forecast: linearForecast(daily.slice(0, -1), 7), hourly: hourlyDistribution(orders), topItems: topItems(lines, 8),
    categories: shareBy(lines, (l) => l.category, (l) => l.quantity * l.price), payments: shareBy(valid, (o) => o.payment_method),
    restaurants: shareBy(valid, (o) => names[o.restaurant_id], (o) => o.total), ratings: { ...rd, dist: rd.dist.map((x) => x.count) }, rules, baskets: m.n };
});

/** Entry point used by js/app.js in the online demo instead of fetch('/api/...'). */
window.TT_MOCK = {
  handle(method, fullPath, body, tok) {
    load();
    const [path, qs] = fullPath.split('?');
    const q = Object.fromEntries(new URLSearchParams(qs || ''));
    for (const r of routes) {
      const mm = r.method === method && r.re.exec(path);
      if (!mm) continue;
      const ctx = { body: body || {}, q, p: mm.slice(1), user: whoAmI(tok), status: 200 };
      try { const out = JSON.parse(JSON.stringify(r.fn(ctx))); return { status: ctx.status, data: out }; }
      catch (e) { if (e instanceof ApiErr) return { status: e.status, data: { error: e.message } }; console.error(e); return { status: 500, data: { error: 'Something went wrong' } }; }
    }
    return { status: 404, data: { error: 'Not found' } };
  },
  reset() { seedDemo(); },
};

// small notice on the sign-in page so visitors know this is the online demo build
document.addEventListener('DOMContentLoaded', () => {
  if (!/login\.html$/.test(location.pathname)) return;
  const n = document.createElement('p');
  n.className = 'xs muted';
  n.style.cssText = 'text-align:center;margin:16px auto 0;max-width:640px';
  n.innerHTML = 'Online demo: the app runs the same ordering, billing and analytics rules in your browser, with demo data stored on this device. The full Java server version is in the <a href="https://github.com/TanviSaraswat8/tastetrail" style="color:var(--brand-dark);font-weight:600">project code</a>.';
  setTimeout(() => document.querySelector('#page')?.append(n), 0);
});

})();
