// Cart and checkout page
const user = Auth.user();
let coupon = '', quote = null, payment = 'UPI';
const PAY = [['UPI', '📱 UPI'], ['CARD', '💳 Card'], ['COD', '💵 Cash on delivery']];

async function init() {
  const c = Cart.get();
  if (!c.items.length) {
    $('#page').innerHTML = `<div class="card pad" style="max-width:440px;margin:0 auto;text-align:center;padding:40px">
      <div style="font-size:48px">🛒</div><h2 style="margin-top:10px">Your cart is empty</h2>
      <p class="small muted" style="margin-top:4px">Browse restaurants and add something tasty.</p>
      <a class="btn btn-primary" href="index.html" style="margin-top:18px">See restaurants</a></div>`;
    return;
  }
  // load the restaurant so the quantity buttons and suggestions know the menu
  const r = await api('/restaurants/' + c.restaurant.id);
  Registry.restaurant = r;
  r.items.forEach((it) => (Registry.items[it.id] = it));

  $('#page').innerHTML = `
  <div class="side side-wide">
    <div class="stack">
      <div class="card pad" id="items"></div>
      <div class="card pad" id="recs"></div>
      <div class="card pad">
        <h3>Delivery address</h3>
        <textarea class="input" id="address" rows="2" style="margin-top:10px">B-204, Sector 125, Noida, UP 201313</textarea>
        <h3 style="margin-top:20px">Payment method</h3>
        <div class="grid g3" id="pay" style="margin-top:10px;gap:8px"></div>
        <p class="xs muted" id="paynote" style="margin-top:8px">Online payment is simulated in this prototype; no real money is charged.</p>
      </div>
    </div>
    <aside class="sticky"><div class="card pad">
      <h3>Apply coupon</h3>
      <div class="row" style="margin-top:10px;gap:8px"><input class="input" id="code" placeholder="e.g. FLAT75" style="text-transform:uppercase"><button class="btn btn-ghost" id="apply">Apply</button></div>
      <div id="couponmsg"></div>
      <div id="bill"></div>
      <div id="err"></div>
      <button class="btn btn-primary btn-block" id="place" style="margin-top:16px;padding:13px">Place order</button>
    </div></aside>
  </div>`;
  renderPay();
  $('#apply').onclick = () => {
    if (coupon) { coupon = ''; $('#code').value = ''; } else coupon = $('#code').value.trim().toUpperCase();
    refresh();
  };
  $('#place').onclick = placeOrder;
  refresh();
}

function renderPay() {
  $('#pay').innerHTML = PAY.map(([v, l]) => `<button class="btn ${payment === v ? 'btn-ghost' : 'btn-ghost'}" data-pay="${v}"
     style="justify-content:flex-start;${payment === v ? 'border-color:var(--brand);background:var(--brand-50);color:var(--brand-deep)' : ''}">${l}</button>`).join('');
  $('#pay').querySelectorAll('[data-pay]').forEach((b) => (b.onclick = () => { payment = b.dataset.pay; renderPay(); }));
  $('#paynote').classList.toggle('hidden', payment === 'COD');
}

async function refresh() {
  const c = Cart.get();
  if (!c.items.length) return init();
  $('#items').innerHTML = `
    <div class="row between"><div><h2>${esc(c.restaurant.name)}</h2><p class="small muted">${esc(c.restaurant.area)}</p></div>
      <button class="linkbtn" id="clear">Clear cart</button></div>
    <div style="margin-top:10px">${c.items.map((i) => `
      <div class="row" style="padding:12px 0;border-top:1px solid #f5f5f4">
        <span style="font-size:24px">${i.emoji}</span>
        <div style="flex:1"><div class="row" style="gap:8px">${vegMark(i.is_veg)}<b style="font-weight:600">${esc(i.name)}</b></div><span class="small muted">${inr(i.price)} each</span></div>
        ${qtyButton(i.id)}<span class="bold right" style="width:80px">${inr(i.price * i.quantity)}</span>
      </div>`).join('')}</div>`;
  $('#clear').onclick = () => Cart.clear();
  $('#apply').textContent = coupon ? 'Remove' : 'Apply';

  const body = { restaurantId: c.restaurant.id, items: c.items.map(({ id, quantity }) => ({ id, quantity })), coupon: coupon || undefined };
  try { quote = await api('/cart/quote', { method: 'POST', body }); showError($('#err'), ''); }
  catch (e) { quote = null; showError($('#err'), e.message); }

  $('#couponmsg').innerHTML = quote?.couponApplied ? `<p class="small green bold" style="margin-top:8px">✓ ${quote.couponApplied} applied — you save ${inr(quote.discount)}</p>`
    : coupon && quote?.couponError ? `<p class="small red" style="margin-top:8px">${esc(quote.couponError)}</p>` : '';
  const row = (k, v, cls = '') => `<div class="row between small" style="margin-top:8px"><span class="muted">${k}</span><span class="${cls}">${v}</span></div>`;
  $('#bill').innerHTML = quote ? `
    <div style="border-top:1px solid var(--line);margin-top:16px;padding-top:6px">
      ${row('Item total', inr(quote.subtotal))}
      ${quote.discount > 0 ? row('Coupon discount', '− ' + inr(quote.discount), 'green bold') : ''}
      ${row('Delivery fee', quote.deliveryFee ? inr(quote.deliveryFee) : 'FREE', quote.deliveryFee ? '' : 'green bold')}
      ${row('Packaging', inr(quote.packaging))}
      ${row('GST (5%)', inr(quote.tax))}
      <div class="row between" style="border-top:1px solid var(--line);margin-top:12px;padding-top:12px;font-weight:800;font-size:16px"><span>To pay</span><span>${inr(quote.total)}</span></div>
    </div>
    ${quote.freeDeliveryGap > 0 ? `<p class="alert alert-warn xs" style="margin-top:12px">Add ${inr(quote.freeDeliveryGap)} more for free delivery</p>` : ''}
    <p class="small" style="margin-top:12px;color:#57534e">🛵 Estimated delivery in <b>${quote.etaMinutes} min</b></p>` : '';
  $('#place').disabled = !quote;
  $('#place').textContent = user ? `Place order · ${quote ? inr(quote.total) : ''}` : 'Sign in to place order';

  const recs = await api(`/recommendations?restaurantId=${c.restaurant.id}&items=${c.items.map((i) => i.id).join(',')}`);
  recs.forEach((x) => (Registry.items[x.id] = x));
  $('#recs').innerHTML = !recs.length ? '<p class="small muted">No suggestions right now.</p>' : `
    <h3>✨ Frequently ordered together</h3><p class="small muted">Suggested from past orders at this restaurant</p>
    <div class="grid g3" style="margin-top:14px;gap:12px">${recs.map((x) => `
      <div style="border:1px solid var(--line);border-radius:12px;padding:12px">
        <div class="row" style="gap:8px"><span style="font-size:24px">${x.emoji}</span>${vegMark(x.is_veg)}</div>
        <div class="small bold" style="margin-top:4px">${esc(x.name)}</div>
        <div class="xs muted">${x.confidence ? `${Math.round(x.confidence * 100)}% of ${esc(x.because)} orders include it` : esc(x.reason)}</div>
        <div class="row between" style="margin-top:8px"><span class="small bold">${inr(x.price)}</span>${qtyButton(x.id)}</div>
      </div>`).join('')}</div>`;
}

async function placeOrder() {
  if (!user) { location.href = 'login.html?next=cart.html'; return; }
  const c = Cart.get();
  $('#place').disabled = true;
  try {
    const o = await api('/orders', { method: 'POST', body: { restaurantId: c.restaurant.id, items: c.items.map(({ id, quantity }) => ({ id, quantity })),
      coupon: quote?.couponApplied || undefined, paymentMethod: payment, address: $('#address').value } });
    localStorage.setItem('tt_cart', '{"restaurant":null,"items":[]}');
    location.href = `track.html?id=${o.id}&placed=1`;
  } catch (e) { showError($('#err'), e.message); $('#place').disabled = false; }
}

document.addEventListener('cartchange', () => $('#items') && refresh());
init();
