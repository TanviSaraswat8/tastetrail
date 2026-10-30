// TasteTrail service worker: makes the site installable as an app and keeps the pages
// available when the network is slow. API calls (/api/...) are never cached.
const CACHE = 'tastetrail-v3';
const SHELL = ['./', './index.html', './login.html', './restaurant.html', './cart.html', './orders.html', './track.html',
  './admin.html', './admin-menu.html', './admin-analytics.html', './css/style.css', './js/app.js', './js/dish-images.js', './credits.html', './explore.html', './passport.html', './js/explore.js', './js/passport.js', './js/home.js', './js/login.js',
  './js/restaurant.js', './js/cart.js', './js/orders.js', './js/track.js', './js/admin-orders.js', './js/admin-menu.js',
  './js/admin-analytics.js', './vendor/chart.umd.min.js', './vendor/qrcode.js', './icons/icon-192.png', './icons/icon-512.png', './manifest.webmanifest'];

self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== 'tastetrail-photos').map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // dish photos (Wikimedia): cache-first, so they load instantly after the first visit
  if (e.request.method === 'GET' && url.hostname.endsWith('wikimedia.org')) {
    e.respondWith(caches.open('tastetrail-photos').then((c) => c.match(e.request).then((hit) => hit ||
      fetch(e.request).then((res) => { if (res.ok || res.type === 'opaque') c.put(e.request, res.clone()); return res; }))));
    return;
  }
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.includes('/api/')) return; // live data always from the server
  // network first, so a new version shows immediately; cached copy when offline
  e.respondWith(fetch(e.request).then((res) => {
    const copy = res.clone();
    caches.open(CACHE).then((c) => c.put(e.request, copy));
    return res;
  }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});
