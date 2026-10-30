// Admin: live order board
const NEXT = { PLACED: ['CONFIRMED', 'Accept'], CONFIRMED: ['PREPARING', 'Start preparing'], PREPARING: ['OUT_FOR_DELIVERY', 'Hand to rider'], OUT_FOR_DELIVERY: ['DELIVERED', 'Mark delivered'] };
const COLUMNS = [['PLACED', 'New'], ['CONFIRMED', 'Accepted'], ['PREPARING', 'In kitchen'], ['OUT_FOR_DELIVERY', 'Out for delivery']];

async function load() {
  let list;
  try { list = await api('/admin/orders?status=ACTIVE'); } catch (e) { return showError($('#err'), e.message); }
  $('#sub').textContent = `${list.length} active · refreshes every 8 seconds`;
  $('#board').innerHTML = COLUMNS.map(([st, title]) => {
    const items = list.filter((o) => o.status === st);
    return `<section class="col"><h3>${title}<span>${items.length}</span></h3>${items.map((o) => `
      <div class="card ticket" data-order="${o.id}">
        <div class="row between"><a href="track.html?id=${o.id}" class="bold">#${o.id}</a>${statusBadge(o.status)}</div>
        <p class="bold" style="margin-top:4px">${esc(o.restaurant)}</p>
        <p style="color:#57534e">${esc(o.summary)}</p>
        <p class="xs muted" style="margin-top:4px">${esc(o.customer)} · ${when(o.created_at)}</p>
        <p class="bold" style="margin-top:6px">${inr(o.total)} <span class="xs muted" style="font-weight:500">${o.payment_method}</span></p>
        <div class="row" style="margin-top:10px;gap:8px">
          <button class="btn btn-primary" style="flex:1;padding:8px;font-size:12.5px" data-move="${o.id}" data-to="${NEXT[o.status][0]}">${NEXT[o.status][1]}</button>
          ${['PLACED', 'CONFIRMED'].includes(o.status) ? `<button class="btn btn-ghost" style="padding:8px 12px;font-size:12.5px" data-move="${o.id}" data-to="CANCELLED">Reject</button>` : ''}
        </div>
      </div>`).join('') || '<p class="xs muted" style="text-align:center;padding:24px 0">No orders</p>'}</section>`;
  }).join('');
}

if (Auth.require('admin')) {
  $('#page').innerHTML = `<h1>Live orders</h1><p class="small muted" id="sub">Loading…</p><div id="err"></div><div class="board" id="board" style="margin-top:18px"></div>`;
  $('#board').onclick = async (e) => {
    const b = e.target.closest('[data-move]'); if (!b) return;
    b.disabled = true;
    try { await api(`/admin/orders/${b.dataset.move}/status`, { method: 'PATCH', body: { status: b.dataset.to } }); showError($('#err'), ''); }
    catch (err) { showError($('#err'), err.message); }
    load();
  };
  load();
  setInterval(load, 8000);
}
