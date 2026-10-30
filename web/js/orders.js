// Customer order history
if (Auth.require('customer')) {
  api('/orders').then((list) => {
    const spent = list.filter((o) => o.status !== 'CANCELLED').reduce((s, o) => s + o.total, 0);
    $('#page').innerHTML = `
    <div style="max-width:760px;margin:0 auto">
      <div class="row between"><h1>My orders</h1><p class="small muted">${list.length} orders · ${inr(Math.round(spent))} spent</p></div>
      <div class="stack" style="margin-top:18px">${list.map((o) => `
        <a class="card order-row row" href="track.html?id=${o.id}" style="padding:14px;gap:16px;margin-top:12px">
          <span class="emoji-tile t-${o.theme} has-cover" style="width:56px;height:56px;font-size:26px">${RESTAURANT_EMOJI[o.restaurant] || '🍽️'}${coverImg(o.restaurant, 160)}</span>
          <div style="flex:1;min-width:0">
            <div class="row" style="gap:8px"><b>${esc(o.restaurant)}</b>${statusBadge(o.status)}</div>
            <p class="small truncate" style="color:#57534e">${esc(o.summary)}</p>
            <p class="xs muted">#${o.id} · ${when(o.created_at)} · ${o.payment_method}${o.rating ? ` · rated ${o.rating}★` : ''}</p>
          </div>
          <b>${inr(o.total)}</b>
        </a>`).join('') || '<p class="card pad muted" style="text-align:center">No orders yet.</p>'}</div>
    </div>`;
  });
}
