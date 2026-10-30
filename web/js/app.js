// TasteTrail – shared front-end code used by every page (plain JavaScript, no framework).

// ---------- small helpers ----------
const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: Number.isInteger(+n) ? 0 : 2 });
const inrShort = (n) => (n >= 1e5 ? `₹${(n / 1e5).toFixed(2)} L` : n >= 1e3 ? `₹${(n / 1e3).toFixed(1)}k` : inr(n));
const when = (iso) => new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
const qs = (key) => new URLSearchParams(location.search).get(key);
const vegMark = (veg) => `<span class="vegmark ${veg ? 'v' : 'nv'}" title="${veg ? 'Veg' : 'Non-veg'}"><i></i></span>`;
const statusBadge = (s) => `<span class="status s-${s}">${s.replaceAll('_', ' ')}</span>`;
const RESTAURANT_EMOJI = { 'Biryani Bazaar': '🍛', 'Pizza Planet': '🍕', 'Punjabi Tadka': '🫓', 'Dosa Junction': '🥞', 'Wok This Way': '🍜', 'Burger Barn': '🍔', 'Chaat Corner': '🥙', 'Sweet Tooth': '🧁',
  'Roll Republic': '🌯', 'Green Bowl Co.': '🥗', 'Kebab Kingdom': '🍢', 'Tokyo Tiffin': '🍣', 'Taco Tierra': '🌮', 'Thali House': '🍱' };

// Sign-ins are kept per tab in the online demo, so one tab can be the customer and another the admin.
const AUTH_STORE = window.TT_MOCK ? sessionStorage : localStorage;

// ---------- API ----------
async function api(path, { method = 'GET', body } = {}) {
  const token = AUTH_STORE.getItem('tt_token');
  if (window.TT_MOCK) { // online demo build: same API, answered inside the browser
    const r = window.TT_MOCK.handle(method, path, body, token);
    await new Promise((ok) => setTimeout(ok, 60));
    if (r.status === 401 && token && path !== '/auth/login') Auth.logout(false);
    if (r.status >= 400) throw new Error(r.data.error || `Request failed (${r.status})`);
    return r.data;
  }
  const res = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && token && path !== '/auth/login') { Auth.logout(false); }
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

// ---------- session ----------
const Auth = {
  user() { return JSON.parse(AUTH_STORE.getItem('tt_user') || 'null'); },
  save({ token, user }) { AUTH_STORE.setItem('tt_token', token); AUTH_STORE.setItem('tt_user', JSON.stringify(user)); },
  logout(redirect = true) { AUTH_STORE.removeItem('tt_token'); AUTH_STORE.removeItem('tt_user'); if (redirect) location.href = 'login.html'; },
  /** Redirects away if the visitor is not signed in or has the wrong role. */
  require(role) {
    const u = Auth.user();
    if (!u) { location.href = 'login.html?next=' + encodeURIComponent(location.pathname.slice(1) + location.search); return null; }
    if (role && u.role !== role) { location.href = u.role === 'admin' ? 'admin.html' : 'index.html'; return null; }
    return u;
  },
};

// ---------- cart: one restaurant at a time, kept in localStorage ----------
const Cart = {
  get() { return JSON.parse(localStorage.getItem('tt_cart') || '{"restaurant":null,"items":[]}'); },
  set(c) { localStorage.setItem('tt_cart', JSON.stringify(c)); renderHeader(); document.dispatchEvent(new Event('cartchange')); },
  clear() { Cart.set({ restaurant: null, items: [] }); },
  qtyOf(id) { return Cart.get().items.find((i) => i.id === id)?.quantity || 0; },
  count() { return Cart.get().items.reduce((s, i) => s + i.quantity, 0); },
  subtotal() { return Cart.get().items.reduce((s, i) => s + i.quantity * i.price, 0); },
  setQty(item, restaurant, qty) {
    const c = Cart.get();
    if (c.restaurant && c.restaurant.id !== restaurant.id && qty > 0) return confirmReplace(item, restaurant);
    let items = c.items.filter((i) => i.id !== item.id);
    if (qty > 0) {
      const line = { id: item.id, name: item.name, price: item.price, is_veg: item.is_veg, emoji: item.emoji, quantity: Math.min(qty, 20) };
      const pos = c.items.findIndex((i) => i.id === item.id);
      if (pos >= 0) { items = [...c.items]; items[pos] = line; } else items.push(line);
    }
    Cart.set({ restaurant: items.length ? { id: restaurant.id, name: restaurant.name, area: restaurant.area, theme: restaurant.theme } : null, items });
  },
};

/** Puts a whole planned meal in the cart (asks first if the cart already has other food). */
Cart.replaceWith = (restaurant, items, then) => {
  const apply = () => {
    Cart.set({ restaurant: { id: restaurant.id, name: restaurant.name, area: restaurant.area, theme: restaurant.theme },
      items: items.map((i) => ({ id: i.id, name: i.name, price: i.price, is_veg: i.is_veg, emoji: i.emoji, quantity: i.quantity || 1 })) });
    if (then) then();
  };
  const c = Cart.get();
  if (!c.items.length) return apply();
  const m = document.createElement('div');
  m.className = 'modal';
  m.innerHTML = `<div class="card"><h3>Replace your cart?</h3>
    <p class="small muted" style="margin-top:8px">Your cart has items from <b>${esc(c.restaurant.name)}</b>. Replace them with this meal from <b>${esc(restaurant.name)}</b>?</p>
    <div class="row" style="justify-content:flex-end;margin-top:18px"><button class="btn btn-ghost" data-no>Keep my cart</button><button class="btn btn-primary" data-yes>Replace</button></div></div>`;
  document.body.append(m);
  m.querySelector('[data-no]').onclick = () => m.remove();
  m.querySelector('[data-yes]').onclick = () => { m.remove(); apply(); };
};

function toast(msg) {
  const t = document.createElement('div');
  t.className = 'toast'; t.textContent = msg;
  document.body.append(t);
  setTimeout(() => t.classList.add('show'), 10);
  setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); }, 2600);
}

function confirmReplace(item, restaurant) {
  const m = document.createElement('div');
  m.className = 'modal';
  m.innerHTML = `<div class="card"><h3>Replace cart items?</h3>
    <p class="small muted" style="margin-top:8px">Your cart has items from <b>${esc(Cart.get().restaurant.name)}</b>. Start a new cart with ${esc(item.name)} from <b>${esc(restaurant.name)}</b>?</p>
    <div class="row" style="justify-content:flex-end;margin-top:18px"><button class="btn btn-ghost" data-no>No</button><button class="btn btn-primary" data-yes>Yes, start fresh</button></div></div>`;
  document.body.append(m);
  m.querySelector('[data-no]').onclick = () => m.remove();
  m.querySelector('[data-yes]').onclick = () => {
    m.remove();
    Cart.set({ restaurant: { id: restaurant.id, name: restaurant.name, area: restaurant.area, theme: restaurant.theme },
      items: [{ id: item.id, name: item.name, price: item.price, is_veg: item.is_veg, emoji: item.emoji, quantity: 1 }] });
  };
}

// Quantity buttons: pages register the dishes and restaurant they show, then render qtyButton(id).
const Registry = { items: {}, restaurant: null };
function qtyButton(id, disabled = false) {
  if (disabled) return '<span class="unavail">Unavailable</span>';
  const q = Cart.qtyOf(id);
  if (!q) return `<button class="add" data-add="${id}">ADD</button>`;
  return `<div class="qty"><button data-dec="${id}" aria-label="Decrease">−</button><span>${q}</span><button data-inc="${id}" aria-label="Increase">+</button></div>`;
}
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-add],[data-inc],[data-dec]');
  if (!b || !Registry.restaurant) return;
  const id = Number(b.dataset.add || b.dataset.inc || b.dataset.dec);
  const item = Registry.items[id];
  if (!item) return;
  const q = Cart.qtyOf(id);
  Cart.setQty(item, Registry.restaurant, b.dataset.dec ? q - 1 : q + 1);
});

// ---------- header (desktop top bar + mobile bottom tab bar) ----------
function renderHeader() {
  const el = $('#header');
  if (!el) return;
  const u = Auth.user();
  const page = location.pathname.split('/').pop() || 'index.html';
  const count = Cart.count();
  const tabs = u?.role === 'admin'
    ? [['admin.html', '📋', 'Live orders'], ['admin-menu.html', '📖', 'Menu'], ['admin-analytics.html', '📊', 'Analytics']]
    : [['index.html', '🏠', 'Restaurants'], ['explore.html', '✨', 'Explore'], ...(u ? [['passport.html', '🎖️', 'Passport'], ['orders.html', '🧾', 'Orders']] : []), ['cart.html', '🛍️', 'Cart']];
  const badge = (href) => (href === 'cart.html' && count ? `<span class="count">${count}</span>` : '');
  el.innerHTML = `<div class="inner">
      <a class="logo" href="${u?.role === 'admin' ? 'admin.html' : 'index.html'}"><span class="mark">🍴</span>Taste<span style="margin-left:-8px">Trail</span></a>
      ${u?.role === 'admin' ? '<span class="badge-admin">ADMIN</span>' : ''}
      <nav class="nav">
        <span class="desk">${tabs.map(([h, i, l]) => `<a href="${h}" class="${page === h ? 'active' : ''}">${i} ${l}${badge(h)}</a>`).join('')}</span>
        <button class="appbtn" id="getapp" title="Open on your phone or install the app">📲 <span class="desk-inline">Get the app</span></button>
        ${u ? `<div class="me"><span class="avatar">${esc(u.name[0])}</span><span class="desk-inline">${esc(u.name.split(' ')[0])}</span><button class="linkbtn" id="logout" title="Sign out">Sign out</button></div>`
            : '<a href="login.html" class="btn btn-primary" style="margin-left:8px;color:#fff">Sign in</a>'}
      </nav></div>`;
  let bar = $('#tabbar');
  if (!bar) { bar = document.createElement('nav'); bar.id = 'tabbar'; bar.className = 'tabbar'; document.body.append(bar); }
  bar.innerHTML = tabs.map(([h, i, l]) => `<a href="${h}" class="${page === h ? 'active' : ''}"><span class="ti">${i}${badge(h)}</span>${l}</a>`).join('');
  $('#logout')?.addEventListener('click', () => Auth.logout());
  $('#getapp').addEventListener('click', showAppModal);
}
document.addEventListener('DOMContentLoaded', renderHeader);

// ---------- installable app (PWA) ----------
let installPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installPrompt = e; });
if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
const isInstalled = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;

function loadScript(src) {
  return new Promise((ok, fail) => { if (window.qrcode) return ok(); const s = document.createElement('script'); s.src = src; s.onload = ok; s.onerror = fail; document.head.append(s); });
}

/** Popup with a QR code of this site (scan it with a phone camera) and an install button. */
async function showAppModal() {
  const url = new URL('./', location.href).href;
  const m = document.createElement('div');
  m.className = 'modal';
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  m.innerHTML = `<div class="card" style="max-width:360px;text-align:center">
      <h3>📲 TasteTrail on your phone</h3>
      <p class="small muted" style="margin-top:6px">Scan with your phone camera to open the app</p>
      <div id="qrbox" style="margin:14px auto 6px;width:220px;height:220px;display:grid;place-items:center"></div>
      <p class="xs muted" style="word-break:break-all">${esc(url)}</p>
      ${isInstalled() ? '<p class="alert alert-ok small" style="margin-top:12px">You are using the installed app ✓</p>'
        : `<button class="btn btn-primary btn-block" id="install" style="margin-top:14px">Install app on this device</button>
           <p class="xs muted" id="installhelp" style="margin-top:8px">${ios ? 'On iPhone: tap Share ⬆️ then “Add to Home Screen”.' : 'On Android: Chrome menu ⋮ → “Install app” / “Add to Home screen”.'}</p>`}
      <button class="btn btn-ghost btn-block" data-close style="margin-top:10px">Close</button></div>`;
  document.body.append(m);
  m.onclick = (e) => { if (e.target === m || e.target.closest('[data-close]')) m.remove(); };
  $('#install', m)?.addEventListener('click', async () => {
    if (installPrompt) { installPrompt.prompt(); const r = await installPrompt.userChoice; if (r.outcome === 'accepted') m.remove(); installPrompt = null; }
    else $('#installhelp', m).classList.add('bold');
  });
  try {
    await loadScript('vendor/qrcode.js');
    const qr = qrcode(0, 'M'); qr.addData(url); qr.make();
    $('#qrbox', m).innerHTML = qr.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
    const svg = $('#qrbox svg', m); svg.style.width = '220px'; svg.style.height = '220px';
  } catch { $('#qrbox', m).textContent = url; }
}

function showError(el, msg) { el.innerHTML = msg ? `<p class="alert alert-error">${esc(msg)}</p>` : ''; }
