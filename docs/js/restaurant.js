// Restaurant menu page
const ORDER = ['Mains', 'Starters', 'Breads', 'Sides', 'Beverages', 'Desserts'];
let R = null, vegOnly = false;

async function load() {
  try { R = await api('/restaurants/' + qs('id')); } catch (e) { $('#page').innerHTML = `<p class="card pad">${esc(e.message)}</p>`; return; }
  Registry.restaurant = R;
  R.items.forEach((it) => (Registry.items[it.id] = it));
  document.title = R.name + ' – TasteTrail';
  render();
}

function render() {
  const groups = {};
  R.items.filter((i) => !vegOnly || i.is_veg).forEach((i) => (groups[i.category] ||= []).push(i));
  const sections = Object.entries(groups).sort((a, b) => ORDER.indexOf(a[0]) - ORDER.indexOf(b[0]));
  $('#page').innerHTML = `
  <div class="side">
    <div>
      <div class="card pad row" style="gap:20px">
        <div class="emoji-tile t-${R.theme}" style="width:92px;height:92px;font-size:48px">${RESTAURANT_EMOJI[R.name] || '🍽️'}</div>
        <div><h1>${esc(R.name)}</h1><p class="muted">${esc(R.cuisine)}</p>
          <p class="small" style="margin-top:8px;color:#57534e">⭐ <b>${R.rating}</b> (${R.rating_count} ratings) &nbsp;·&nbsp; 📍 ${esc(R.area)} · ${R.distance_km} km &nbsp;·&nbsp; 🕒 Prep ~${R.prep_minutes} min</p></div>
      </div>
      ${R.is_open ? '' : '<p class="alert alert-warn" style="margin-top:14px">This restaurant is not accepting orders right now.</p>'}
      <label class="row small bold" style="margin-top:20px;gap:8px;width:fit-content;cursor:pointer"><input type="checkbox" id="veg" ${vegOnly ? 'checked' : ''}> Veg only</label>
      ${sections.map(([cat, items]) => `
        <h2 style="margin:22px 0 10px">${cat} <span class="small muted" style="font-weight:400">(${items.length})</span></h2>
        <div class="card">${items.map((it) => `
          <div class="menu-item">
            <div style="flex:1">
              <div class="row" style="gap:8px">${vegMark(it.is_veg)}${it.bestseller ? '<span class="best">🔥 Bestseller</span>' : ''}</div>
              <h3 style="margin-top:4px">${esc(it.name)}</h3>
              <p class="small bold">${inr(it.price)}</p>
              <p class="small muted" style="margin-top:3px">${esc(it.description)}</p>
            </div>
            <div style="display:flex;flex-direction:column;align-items:center;gap:8px">
              <span class="emoji-tile" style="width:64px;height:64px;font-size:30px">${it.emoji}</span>
              ${qtyButton(it.id, !it.is_available || !R.is_open)}
            </div>
          </div>`).join('')}</div>`).join('')}
      ${R.reviews.length ? `<h2 style="margin:28px 0 10px">Recent reviews</h2><div class="grid g2">${R.reviews.map((rv) => `
        <div class="card pad small"><div class="row between"><b>${esc(rv.name)}</b><span style="color:var(--amber)">${'★'.repeat(rv.rating)}<span style="color:#d6d3d1">${'★'.repeat(5 - rv.rating)}</span></span></div>
        <p class="muted" style="margin-top:4px">${esc(rv.comment)}</p></div>`).join('')}</div>` : ''}
    </div>
    <aside class="sticky" id="mini"></aside>
  </div>`;
  $('#veg').onchange = (e) => { vegOnly = e.target.checked; render(); };
  renderMini();
}

function renderMini() {
  const c = Cart.get();
  $('#mini').innerHTML = `<div class="card pad"><h3>Your cart</h3>${!c.items.length ? '<p class="small muted" style="margin-top:8px">Add dishes to start an order.</p>' : `
    <p class="xs muted">from ${esc(c.restaurant.name)}</p>
    <ul style="padding:0;margin:12px 0 0;list-style:none" class="small">${c.items.map((i) => `<li class="row between" style="margin-bottom:6px"><span class="truncate">${i.quantity} × ${esc(i.name)}</span><span>${inr(i.price * i.quantity)}</span></li>`).join('')}</ul>
    <div class="row between bold" style="border-top:1px solid var(--line);margin-top:10px;padding-top:10px"><span>Subtotal</span><span>${inr(Cart.subtotal())}</span></div>
    <a href="cart.html" class="btn btn-primary btn-block" style="margin-top:14px">Checkout (${Cart.count()})</a>`}</div>`;
}

document.addEventListener('cartchange', () => R && render());
load();
