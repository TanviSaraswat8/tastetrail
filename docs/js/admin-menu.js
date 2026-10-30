// Admin: menu, price, stock and open/closed management
const CATS = ['Mains', 'Starters', 'Breads', 'Sides', 'Beverages', 'Desserts'];
let rid = 1, data = null;

async function load() {
  try { data = await api('/admin/menu?restaurantId=' + rid); } catch (e) { return showError($('#err'), e.message); }
  const rest = data.restaurants.find((r) => r.id === rid);
  $('#rest').innerHTML = data.restaurants.map((r) => `<option value="${r.id}" ${r.id === rid ? 'selected' : ''}>${esc(r.name)}</option>`).join('');
  $('#open').textContent = rest.is_open ? '● Open' : '○ Closed';
  $('#open').style.cssText = rest.is_open ? 'background:#dcfce7;color:#166534' : 'background:#e7e5e4;color:#57534e';
  $('#rows').innerHTML = data.items.map((m) => `
    <tr class="${m.is_available ? '' : 'off'}">
      <td><div class="row" style="gap:8px"><span style="font-size:20px">${m.emoji}</span>${vegMark(m.is_veg)}<span style="font-weight:600">${esc(m.name)}</span></div></td>
      <td>${m.category}</td><td class="right num">${m.sold}</td>
      <td><input class="input" type="number" min="1" value="${m.price}" data-price="${m.id}" style="width:96px;padding:6px 10px"></td>
      <td><button class="switch ${m.is_available ? 'on' : ''}" data-stock="${m.id}" aria-label="Toggle stock"></button></td>
    </tr>`).join('');
  const avg = data.items.reduce((s, m) => s + m.price, 0) / data.items.length;
  $('#meta').textContent = `${data.items.length} dishes · avg price ${inr(Math.round(avg))}`;
}

async function patch(id, body) {
  try { await api('/admin/menu/' + id, { method: 'PATCH', body }); showError($('#err'), ''); } catch (e) { showError($('#err'), e.message); }
  load();
}

if (Auth.require('admin')) {
  $('#page').innerHTML = `
    <div class="row between wrap">
      <div><h1>Menu management</h1><p class="small muted">Change prices, mark dishes out of stock and add new items</p></div>
      <div class="row"><select class="input" id="rest" style="width:auto"></select><button class="btn" id="open"></button></div>
    </div>
    <div id="err" style="margin-top:10px"></div>
    <div class="side" style="margin-top:18px">
      <div class="card scroll-x"><table class="tbl">
        <thead><tr><th>Dish</th><th>Category</th><th class="right">Sold</th><th>Price (₹)</th><th>In stock</th></tr></thead>
        <tbody id="rows"></tbody></table></div>
      <form class="card pad stack" id="add" style="--gap:10px">
        <h3>Add a dish</h3>
        <input class="input" name="name" placeholder="Dish name">
        <input class="input" name="description" placeholder="Short description">
        <div class="grid g2" style="gap:8px"><select class="input" name="category">${CATS.map((c) => `<option>${c}</option>`).join('')}</select>
          <input class="input" name="price" type="number" placeholder="Price"></div>
        <div class="row small"><label class="row" style="gap:6px"><input type="checkbox" name="isVeg" checked> Vegetarian</label>
          <input class="input" name="emoji" value="🍽️" style="width:64px;text-align:center"></div>
        <button class="btn btn-primary btn-block">Add to menu</button>
        <p class="xs muted" id="meta"></p>
      </form>
    </div>`;
  $('#rest').onchange = (e) => { rid = Number(e.target.value); load(); };
  $('#open').onclick = async () => {
    const rest = data.restaurants.find((r) => r.id === rid);
    await api('/admin/restaurants/' + rid, { method: 'PATCH', body: { isOpen: !rest.is_open } });
    load();
  };
  $('#rows').addEventListener('click', (e) => {
    const b = e.target.closest('[data-stock]'); if (!b) return;
    const m = data.items.find((x) => x.id === Number(b.dataset.stock));
    patch(m.id, { isAvailable: !m.is_available });
  });
  $('#rows').addEventListener('change', (e) => {
    const inp = e.target.closest('[data-price]'); if (!inp) return;
    patch(Number(inp.dataset.price), { price: Number(inp.value) });
  });
  $('#add').onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await api('/admin/menu', { method: 'POST', body: { restaurantId: rid, name: f.get('name'), description: f.get('description'), category: f.get('category'),
        price: Number(f.get('price')), isVeg: f.get('isVeg') === 'on', emoji: f.get('emoji') } });
      e.target.reset(); showError($('#err'), ''); load();
    } catch (err) { showError($('#err'), err.message); }
  };
  load();
}
