// Admin: sales analytics dashboard (Chart.js for the two charts)
const C = { brand: '#f2622e', ink: '#1f1a17', teal: '#0f766e', amber: '#f59e0b', soft: '#fbc3a8', grid: '#ece8e4' };
const fmtDate = (d) => new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
const hourLabel = (h) => `${h % 12 || 12}${h < 12 ? 'am' : 'pm'}`;
let days = 30, charts = [];

const kpi = (label, value, sub = '') => `<div class="card kpi"><div class="label">${label}</div><div class="value num">${value}</div><div class="xs muted">${sub}</div></div>`;
const panel = (title, sub, inner, extra = '') => `<section class="card pad" ${extra}><h3>${title}</h3>${sub ? `<p class="xs muted">${sub}</p>` : ''}<div style="margin-top:14px">${inner}</div></section>`;
const bars = (rows, label, value, fmt, color) => {
  const max = Math.max(...rows.map(value), 1);
  return `<div class="bars"><ul>${rows.map((r) => `<li><div class="row between"><span class="truncate">${esc(label(r))}</span><span class="num muted">${fmt(r)}</span></div>
    <div class="track"><div class="fill" style="width:${(value(r) / max) * 100}%;background:${color}"></div></div></li>`).join('')}</ul></div>`;
};

async function load() {
  let d;
  try { d = await api('/admin/analytics?days=' + days); } catch (e) { $('#page').innerHTML = `<p class="card pad">${esc(e.message)}</p>`; return; }
  const k = d.kpis, peak = d.hourly.reduce((a, b) => (b.orders > a.orders ? b : a));
  const ratings = d.ratings.dist.map((count, i) => ({ rating: i + 1, count })).reverse();
  $('#page').innerHTML = `
  <div class="stack">
    <div class="row between wrap">
      <div><h1>Sales analytics</h1><p class="small muted">All restaurants · last ${d.days} days · cancelled orders excluded from revenue</p></div>
      <div class="seg">${[7, 30, 60].map((n) => `<button data-days="${n}" class="${n === days ? 'on' : ''}">${n} days</button>`).join('')}</div>
    </div>
    <div class="grid g6" style="gap:12px">
      ${kpi('Revenue', inrShort(k.revenue), `${k.orders} orders`)}
      ${kpi('Avg order value', inr(Math.round(k.avgOrderValue)))}
      ${kpi('Customers', k.customers, `${k.repeatRate}% ordered again`)}
      ${kpi('Avg delivery', k.avgDeliveryMinutes + ' min', 'order to doorstep')}
      ${kpi('Cancellation', k.cancellationRate + '%', `${k.cancelled} orders`)}
      ${kpi('Avg rating', d.ratings.average + ' ★', `${d.ratings.count} reviews`)}
    </div>
    ${panel('Daily revenue, 7-day moving average and 7-day forecast',
      `Linear trend: ${d.forecast.slope >= 0 ? '+' : ''}${inr(d.forecast.slope)} per day · R² = ${d.forecast.r2} (today is excluded from the fit because it is incomplete)`,
      '<div style="height:280px"><canvas id="trend"></canvas></div>')}
    <div class="grid g2" style="gap:20px">
      ${panel('Orders by hour of day', `Peak hour: ${hourLabel(peak.hour)}–${hourLabel((peak.hour + 1) % 24)} with ${peak.orders} orders`, '<div style="height:240px"><canvas id="hours"></canvas></div>')}
      ${panel('Top dishes by quantity sold', '', bars(d.topItems, (r) => r.name, (r) => r.quantity, (r) => `${r.quantity} sold · ${inrShort(r.revenue)}`, C.brand))}
    </div>
    <div class="grid g3" style="gap:20px">
      ${panel('Revenue by restaurant', '', bars(d.restaurants, (r) => r.name, (r) => r.value, (r) => r.pct + '%', C.ink))}
      ${panel('Sales by menu category', '', bars(d.categories, (r) => r.name, (r) => r.value, (r) => r.pct + '%', C.teal))}
      ${panel('Payment methods & ratings', '', bars(d.payments, (r) => r.name, (r) => r.value, (r) => `${r.value} · ${r.pct}%`, C.brand)
        + '<div style="border-top:1px solid #f5f5f4;margin-top:16px;padding-top:14px">' + bars(ratings, (r) => r.rating + ' ★', (r) => r.count, (r) => r.count, C.amber) + '</div>')}
    </div>
    ${panel('Association rules behind “Frequently ordered together”', `Market basket analysis over ${d.baskets} non-cancelled orders · rules with at least 20 co-occurrences, ranked by lift`, `<div class="scroll-x">
      <table class="tbl"><thead><tr><th>If the cart has…</th><th>…suggest</th><th class="right">Orders together</th><th class="right">Support</th><th class="right">Confidence</th><th class="right">Lift</th></tr></thead>
      <tbody>${d.rules.map((r) => `<tr><td style="font-weight:600">${esc(r.from)}</td><td>${esc(r.to)}</td><td class="right num">${r.count}</td><td class="right num">${r.support}%</td><td class="right num">${r.confidence}%</td><td class="right num bold">${r.lift}</td></tr>`).join('')}</tbody></table></div>`)}
  </div>`;
  document.querySelectorAll('[data-days]').forEach((b) => (b.onclick = () => { days = Number(b.dataset.days); load(); }));
  drawCharts(d);
}

function drawCharts(d) {
  charts.forEach((c) => c.destroy());
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.color = '#78716c';
  const labels = [...d.daily.map((x) => fmtDate(x.date)), ...d.forecast.forecast.map((f) => fmtDate(f.date))];
  const pad = (arr, before, after) => [...Array(before).fill(null), ...arr, ...Array(after).fill(null)];
  const nf = d.forecast.forecast.length;
  charts = [
    new Chart($('#trend'), {
      data: { labels, datasets: [
        { type: 'bar', label: 'Daily revenue', data: pad(d.daily.map((x) => x.revenue), 0, nf), backgroundColor: C.soft, borderRadius: 3, order: 3 },
        { type: 'line', label: '7-day moving avg', data: pad(d.daily.map((x) => x.movingAvg), 0, nf), borderColor: C.brand, borderWidth: 2.5, pointRadius: 0, tension: 0.25, order: 1 },
        { type: 'line', label: 'Forecast', data: pad(d.forecast.forecast.map((f) => f.forecast), d.daily.length, 0), borderColor: C.ink, borderDash: [5, 4], borderWidth: 2, pointRadius: 0, order: 2 },
      ] },
      options: { maintainAspectRatio: false, animation: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { position: 'bottom', labels: { boxWidth: 12 } }, tooltip: { callbacks: { label: (c) => `${c.dataset.label}: ${inr(Math.round(c.parsed.y))}` } } },
        scales: { x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } }, y: { grid: { color: C.grid }, ticks: { callback: (v) => '₹' + v / 1000 + 'k' } } } },
    }),
    new Chart($('#hours'), {
      type: 'bar',
      data: { labels: d.hourly.filter((h) => h.hour >= 7).map((h) => hourLabel(h.hour)), datasets: [{ label: 'Orders', data: d.hourly.filter((h) => h.hour >= 7).map((h) => h.orders), backgroundColor: C.teal, borderRadius: 3 }] },
      options: { maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } }, scales: { x: { grid: { display: false } }, y: { grid: { color: C.grid } } } },
    }),
  ];
}

if (Auth.require('admin')) load();
