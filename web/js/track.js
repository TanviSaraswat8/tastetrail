// Live order tracking, cancellation and rating
const me = Auth.require();
const STEPS = [['PLACED', 'Order placed', '📝'], ['CONFIRMED', 'Confirmed', '✓'], ['PREPARING', 'Preparing', '👨‍🍳'], ['OUT_FOR_DELIVERY', 'On the way', '🛵'], ['DELIVERED', 'Delivered', '📦']];
let order = null, stars = 0;

async function load() {
  try { order = await api('/orders/' + qs('id')); render(); }
  catch (e) { $('#page').innerHTML = `<p class="card pad">${esc(e.message)}</p>`; }
}

function render() {
  const o = order, idx = STEPS.findIndex((s) => s[0] === o.status), cancelled = o.status === 'CANCELLED';
  const etaAt = new Date(new Date(o.created_at).getTime() + o.eta_minutes * 60000);
  const keepComment = $('#comment')?.value || '';
  $('#page').innerHTML = `
  <div class="stack" style="max-width:760px;margin:0 auto">
    ${qs('placed') ? `<p class="alert alert-ok"><b>Order placed!</b> ${o.payment_method === 'COD' ? 'Please keep cash ready at delivery.' : 'Payment received.'}</p>` : ''}
    <div class="card pad" style="padding:24px">
      <div class="row between" style="align-items:flex-start">
        <div><p class="small muted">Order #${o.id} · ${when(o.created_at)}</p><h1>${esc(o.restaurant)}</h1></div>
        ${!cancelled && o.status !== 'DELIVERED' ? `<div class="eta"><div class="xs">Arriving by</div><div style="font-size:19px;font-weight:800">${etaAt.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}</div></div>` : ''}
      </div>
      ${cancelled ? `<p class="alert alert-error" style="margin-top:20px">This order was cancelled${o.payment_status === 'REFUNDED' ? ' and your payment has been refunded.' : '.'}</p>` : `
      <div class="steps">${STEPS.map(([k, label, icon], i) => `
        <div class="step ${i <= idx ? 'done' : ''} ${i === idx && o.status !== 'DELIVERED' ? 'current' : ''}">
          ${i > 0 ? '<span class="bar"></span>' : ''}<span class="dot">${icon}</span><label>${label}</label></div>`).join('')}</div>`}
      ${me.role === 'customer' && ['PLACED', 'CONFIRMED'].includes(o.status) ? '<button class="linkbtn red bold" id="cancel" style="margin-top:22px;color:var(--red)">Cancel order</button>' : ''}
      <div id="err"></div>
    </div>
    <div class="card pad" style="padding:24px">
      <h3>Bill details</h3>
      ${o.items.map((i) => `<div class="row between small" style="margin-top:8px"><span>${i.quantity} × ${esc(i.name)}</span><span>${inr(i.price * i.quantity)}</span></div>`).join('')}
      <div style="border-top:1px solid var(--line);margin-top:14px;padding-top:6px" class="small">
        <div class="row between muted" style="margin-top:6px"><span>Item total</span><span>${inr(o.subtotal)}</span></div>
        ${o.discount > 0 ? `<div class="row between green" style="margin-top:6px"><span>Coupon ${esc(o.coupon_code)}</span><span>− ${inr(o.discount)}</span></div>` : ''}
        <div class="row between muted" style="margin-top:6px"><span>Delivery</span><span>${o.delivery_fee ? inr(o.delivery_fee) : 'FREE'}</span></div>
        <div class="row between muted" style="margin-top:6px"><span>Packaging + GST</span><span>${inr(o.packaging_fee + o.tax)}</span></div>
        <div class="row between" style="margin-top:10px;font-size:16px;font-weight:800"><span>Total (${o.payment_method}, ${o.payment_status.toLowerCase()})</span><span>${inr(o.total)}</span></div>
      </div>
      <p class="small muted" style="margin-top:14px">Delivering to: ${esc(o.address)}</p>
    </div>
    ${o.status === 'DELIVERED' && me.role === 'customer' ? `<div class="card pad" style="padding:24px">${o.review
      ? `<p>You rated this order <span style="color:var(--amber)">${'★'.repeat(o.review.rating)}</span>${o.review.comment ? ` — “${esc(o.review.comment)}”` : ''}</p>`
      : `<h3>How was your food?</h3>
         <div class="stars" style="margin-top:6px">${[1, 2, 3, 4, 5].map((n) => `<button data-star="${n}" class="${n <= stars ? 'on' : ''}">★</button>`).join('')}</div>
         <textarea class="input" id="comment" rows="2" placeholder="Tell us more (optional)" style="margin-top:10px">${esc(keepComment)}</textarea>
         <button class="btn btn-primary" id="rate" style="margin-top:12px" ${stars ? '' : 'disabled'}>Submit rating</button>`}</div>` : ''}
    <a href="${me.role === 'admin' ? 'admin.html' : 'orders.html'}" class="small bold" style="color:var(--brand-dark)">← Back to orders</a>
  </div>`;
  $('#cancel')?.addEventListener('click', () => act(() => api(`/orders/${o.id}/cancel`, { method: 'POST' })));
  document.querySelectorAll('[data-star]').forEach((b) => (b.onclick = () => { stars = Number(b.dataset.star); render(); }));
  $('#rate')?.addEventListener('click', () => act(() => api(`/orders/${o.id}/review`, { method: 'POST', body: { rating: stars, comment: $('#comment').value } })));
}

async function act(fn) { try { order = await fn(); render(); } catch (e) { showError($('#err'), e.message); } }

if (me) { load(); setInterval(() => { if (!order || !['DELIVERED', 'CANCELLED'].includes(order.status)) load(); }, 5000); } // live status every 5 s
