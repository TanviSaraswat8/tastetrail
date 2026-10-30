// Home page: search, filters, offers, popular dishes and the restaurant list
const CUISINES = ['Biryani', 'Pizza', 'North Indian', 'South Indian', 'Chinese', 'Burgers', 'Street Food', 'Desserts', 'Rolls', 'Healthy', 'Kebabs', 'Japanese', 'Mexican', 'Gujarati'];
const SORTS = [['popular', 'Popularity'], ['rating', 'Rating'], ['fastest', 'Delivery time'], ['cost', 'Cost: low to high']];
const state = { q: '', cuisine: '', veg: false, sort: 'popular' };
const user = Auth.user();

$('#page').innerHTML = `
  <div class="stack">
    <section class="hero">
      <p style="opacity:.88;font-weight:500">${user ? `Hi ${esc(user.name.split(' ')[0])}, hungry?` : 'Delivering across Noida'}</p>
      <h1>Order food from the best places near you</h1>
      <div class="searchbox"><span>🔍</span><input id="q" placeholder="Search for a restaurant, cuisine or dish (try “paneer”)"></div>
    </section>
    <section class="explore-banner">
      <a href="explore.html#mood" class="eb"><span class="ebi">🎡</span><div><b>Mood Match</b><span>Can't decide? Answer 3 taps or spin the wheel</span></div></a>
      <a href="explore.html#plan" class="eb"><span class="ebi">🧮</span><div><b>Feast Planner</b><span>“Feed 4 people for ₹1,000” – we build the best meal</span></div></a>
      <a href="passport.html" class="eb"><span class="ebi">🎖️</span><div><b>Taste Passport</b><span>Collect cuisine stamps, badges and XP</span></div></a>
    </section>
    <section class="coupons" id="coupons"></section>
    <section>
      <h2>Popular dishes right now</h2><p class="small muted">Ranked by number of orders</p>
      <div class="grid g4" id="dishes" style="margin-top:14px"></div>
    </section>
    <section>
      <div class="row between wrap">
        <div><h2>Restaurants</h2><p class="small muted" id="count">Loading…</p></div>
        <select class="input" id="sort" style="width:auto">${SORTS.map(([v, l]) => `<option value="${v}">Sort: ${l}</option>`).join('')}</select>
      </div>
      <div class="row wrap" id="chips" style="margin-top:14px;gap:8px"></div>
      <div class="grid g4" id="list" style="margin-top:18px;gap:22px"></div>
    </section>
  </div>`;

function chips() {
  $('#chips').innerHTML = `<button class="chip veg ${state.veg ? 'on' : ''}" data-veg>🌿 Pure veg</button>` +
    CUISINES.map((c) => `<button class="chip ${state.cuisine === c ? 'on' : ''}" data-cuisine="${c}">${c}</button>`).join('');
}

async function loadRestaurants() {
  const p = new URLSearchParams({ q: state.q, cuisine: state.cuisine, veg: state.veg ? '1' : '', sort: state.sort });
  const list = await api('/restaurants?' + p);
  $('#count').textContent = `${list.length} places delivering to you`;
  $('#list').innerHTML = list.length ? list.map((r) => `
    <a class="rest-card ${r.is_open ? '' : 'off'}" href="restaurant.html?id=${r.id}">
      <div class="tile t-${r.theme}">${RESTAURANT_EMOJI[r.name] || '🍽️'}${coverImg(r.name, 640)}${restaurantCoverUrl(r.name) ? `<span class="brand">${RESTAURANT_EMOJI[r.name] || '🍽️'}</span>` : ''}
        ${r.is_pure_veg ? '<span class="tag">PURE VEG</span>' : ''}${r.is_open ? '' : '<span class="closed">Closed now</span>'}</div>
      <div class="row between" style="margin-top:10px"><h3 class="truncate">${esc(r.name)}</h3>${r.rating == null ? '<span class="newtag">NEW</span>' : `<span class="rating">${r.rating} ★</span>`}</div>
      <p class="small muted truncate">${esc(r.cuisine)}</p>
      <p class="small" style="margin-top:3px;color:#57534e">🕒 ${r.eta_minutes} min · ${esc(r.area)} · ${inr(r.cost_for_two)} for two</p>
    </a>`).join('') : '<p class="card pad muted" style="grid-column:1/-1;text-align:center">No restaurants match your search.</p>';
}

let t;
$('#q').oninput = (e) => { state.q = e.target.value; clearTimeout(t); t = setTimeout(loadRestaurants, 200); };
$('#sort').onchange = (e) => { state.sort = e.target.value; loadRestaurants(); };
$('#chips').onclick = (e) => {
  const b = e.target.closest('button'); if (!b) return;
  if ('veg' in b.dataset) state.veg = !state.veg; else state.cuisine = state.cuisine === b.dataset.cuisine ? '' : b.dataset.cuisine;
  chips(); loadRestaurants();
};

chips();
loadRestaurants();
api('/coupons').then((cs) => ($('#coupons').innerHTML = cs.map((c) => `
  <div class="coupon"><span style="font-size:22px">🎟️</span><div><b>${c.code}</b><div class="xs" style="color:#57534e">${esc(c.description)}</div></div></div>`).join('')));
api('/popular-dishes').then((ds) => ($('#dishes').innerHTML = ds.map((d) => `
  <a class="card dish" href="restaurant.html?id=${d.restaurant_id}">
    ${dishPic(d, 56)}
    <div style="min-width:0"><div class="row" style="gap:6px">${vegMark(d.is_veg)}<b class="small truncate">${esc(d.name)}</b></div>
      <div class="xs muted truncate">${esc(d.restaurant)} · ${inr(d.price)}</div></div>
  </a>`).join('')));
