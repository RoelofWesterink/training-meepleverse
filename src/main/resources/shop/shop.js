import {gameImages} from './game-images.js';

const main = document.querySelector('main');
const html = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = value => new Intl.NumberFormat('en-IE', {style:'currency', currency:'EUR'}).format(value);
const title = value => String(value).replaceAll('-', ' ').replace(/\b\w/g, c => c.toUpperCase());
let initialBasket = [];
try { const saved = JSON.parse(sessionStorage.getItem('meepleverse.basket')); if (Array.isArray(saved)) initialBasket = saved.filter(i => typeof i === 'string'); } catch { /* Storage is optional. */ }
const state = {items:[], categories:{}, loading:true, catalogError:false, category:'', term:'', basket:initialBasket, account:null, auth:'loading'};
let pollTimer, pollController, routeVersion = 0, toastTimer;
const stageColors = ['#e7e9d9', '#ecdfce', '#e7e9df', '#e9ddcf', '#dfe8e2', '#e8e1d6', '#ece5ce', '#e5e4ed'];
const knownItems = new Map();
function itemFor(id) { return knownItems.get(id); }
function nameFor(id) { return itemFor(id)?.details.name || title(id); }
function persist() {
  try { sessionStorage.setItem('meepleverse.basket', JSON.stringify(state.basket)); } catch { /* In-memory basket still works. */ }
}
function toast(message) {
  const node = document.querySelector('#toast');
  node.textContent = message; node.classList.add('visible'); clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove('visible'), 3500);
}
async function request(path, options = {}) {
  const response = await fetch(path, {cache:'no-store', ...options});
  if (!response.ok) { const error = new Error(`Request failed (${response.status})`); error.status = response.status; throw error; }
  return response.json();
}
function header() {
  document.querySelector('#basket-count').textContent = state.basket.length;
  const link = document.querySelector('#account-link');
  link.textContent = state.account ? 'My profile' : 'Sign in'; link.href = state.account ? '#/profile' : '/shop-account/login';
  const route = location.hash.slice(1);
  document.querySelectorAll('[data-nav]').forEach(a => {
    const current = a.dataset.nav === (route.startsWith('/orders') ? 'orders' : route === '/basket' ? 'basket' : route === '/profile' ? 'profile' : 'shop');
    if (current) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
}
function empty(heading, description, action = '<a class="btn" href="#/">Explore the games <span aria-hidden="true">→</span></a>') {
  return `<div class="empty"><h2>${heading}</h2><p>${description}</p>${action}</div>`;
}
function cover(item, small = false) {
  const id = item.itemId;
  const color = stageColors[[...id].reduce((sum, c) => sum + c.charCodeAt(0), 0) % stageColors.length];
  const imageUrl = gameImages.get(id);
  const photo = imageUrl
    ? `<img class="game-photo" src="${html(imageUrl)}" alt="" loading="lazy" decoding="async">` : '';
  return `<div class="${small ? 'mini-cover' : 'product-stage'}" style="--stage:${color}" aria-hidden="true">
    ${photo}<span class="cover-title">${html(item.details?.name || title(id))}</span></div>`;
}
function shop() {
  main.innerHTML = `<section class="hero"><div><div class="eyebrow">For the love of the game</div><h1>Your next game night<br>starts <em>here.</em></h1><p>Big adventures. Friendly rivalries. One more round.<br>Find something worth gathering around.</p><a class="btn" href="#games" data-action="browse">Find your next game <span aria-hidden="true">↘</span></a></div><div class="hero-art"><img src="/shop/assets/meeples.png" alt="A colourful collection of wooden meeples, arranged as one large meeple"><span class="hero-caption">Good things come together.</span></div></section>
  <section id="games" aria-labelledby="catalog-title"><div class="section-heading"><div><div class="eyebrow">Make room at the table</div><h2 id="catalog-title">Find your kind of fun.</h2></div><span id="result-count" class="muted" role="status"></span></div>
  <div class="catalog-toolbar"><div class="categories" aria-label="Game categories"></div><label><span class="visually-hidden">Search games</span><input class="search" id="search" type="search" placeholder="Search for a game…" value="${html(state.term)}" aria-label="Search games"></label></div><p class="shop-account-note">${state.account ? `Buy now uses your saved <a href="#/profile">Meeple Pay and delivery address</a>.` : `<a href="/shop-account/login">Sign in</a> to buy a game in one click. Use a fictional local username.`}</p><div id="products"></div><p class="catalog-note"><a href="/shop/image-credits.html">Game image credits</a></p></section>`;
  updateCategories(); updateProducts();
}
function updateCategories() {
  const bar = document.querySelector('.categories');
  if (!bar) return;
  const categories = Object.entries(state.categories).sort(([a], [b]) => a.localeCompare(b));
  if (state.category && !(state.category in state.categories)) categories.push([state.category, 0]);
  const buttons = [['', 'All games'], ...categories.map(([name, count]) => [name, `${title(name)} (${count})`])];
  const focused = bar.contains(document.activeElement) ? document.activeElement.dataset.category : undefined;
  bar.innerHTML = buttons.map(([value, label]) => `<button class="category" data-category="${html(value)}" aria-pressed="${state.category === value}">${html(label)}</button>`).join('');
  if (focused !== undefined) [...bar.children].find(b => b.dataset.category === focused)?.focus({preventScroll:true});
}
function updateProducts() {
  const target = document.querySelector('#products'); if (!target) return;
  if (state.loading) { target.innerHTML = '<div class="loading" role="status">Finding the games…</div>'; return; }
  if (state.catalogError) { target.innerHTML = empty('The shop is taking a moment.', 'We could not load the catalog. Check that your course environment is running.', '<button class="btn" data-action="reload-catalog">Try again</button>'); return; }
  const filtered = state.items;
  document.querySelector('#result-count').textContent = `${filtered.length} game${filtered.length === 1 ? '' : 's'}`;
  target.innerHTML = filtered.length ? `<div class="catalog-grid">${filtered.map(item => `<article class="product">${cover(item)}<span class="eyebrow">${html(item.details.category)}</span><h3>${html(item.details.name)}</h3><div class="product-bottom"><span class="price">${money(item.details.price)}</span><button class="add-button" data-add="${html(item.itemId)}" aria-label="${state.basket.includes(item.itemId) ? 'Remove' : 'Add'} ${html(item.details.name)} ${state.basket.includes(item.itemId) ? 'from' : 'to'} basket" aria-pressed="${state.basket.includes(item.itemId)}">${state.basket.includes(item.itemId) ? '✓ In basket' : '+ Add to basket'}</button></div>${state.account ? `<button class="btn buy" data-buy="${html(item.itemId)}" aria-label="Buy ${html(item.details.name)} now for ${money(item.details.price)}">Buy now <span aria-hidden="true">→</span></button>` : ''}</article>`).join('')}</div>` : empty('No games found.', 'Try another name or category.', '<button class="btn secondary" data-action="clear-search">Show all games</button>');
}
let catalogController;
async function loadCatalog() {
  catalogController?.abort();
  const controller = new AbortController();
  catalogController = controller;
  const params = new URLSearchParams();
  if (state.term.trim()) params.set('term', state.term.trim());
  if (state.category) params.set('category', state.category);
  state.loading = true; state.catalogError = false; updateProducts();
  try {
    const page = await request(`/catalog/browse?${params}`, {
      signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)])
    });
    if (controller !== catalogController) return;
    const featured = ['ark-nova', 'gloomhaven', 'wingspan', 'terraforming-mars'];
    state.items = page.items.sort((a, b) => (featured.includes(a.itemId) ? featured.indexOf(a.itemId) : 100)
      - (featured.includes(b.itemId) ? featured.indexOf(b.itemId) : 100) || a.details.name.localeCompare(b.details.name));
    for (const item of state.items) knownItems.set(item.itemId, item);
    state.categories = page.categories;
  } catch {
    if (controller !== catalogController) return;
    state.catalogError = true;
  }
  state.loading = false;
  if (document.querySelector('#products')) { updateCategories(); updateProducts(); }
  else render(false);
}
function itemLines(ids, removable = false) {
  return ids.map(id => {
    const item = itemFor(id) || {itemId:id, details:{name:title(id)}};
    return `<div class="line-item">${cover(item, true)}<div class="item-copy"><h3>${html(item.details.name)}</h3><small>1 copy${item.details.category ? ` · ${html(title(item.details.category))}` : ''}</small>${removable ? `<br><button class="text-button" data-remove="${html(id)}">Remove</button>` : ''}</div>${item.details.price !== undefined ? `<span class="price">${money(item.details.price)}</span>` : ''}</div>`;
  }).join('');
}
function total() { return state.basket.reduce((sum, id) => sum + Number(itemFor(id)?.details.price ?? 0), 0); }
function basketReady() { return !state.loading && !state.catalogError && state.basket.every(id => itemFor(id)); }
function basket() {
  main.innerHTML = `<div class="page-heading"><a class="back-link" href="#/">← Back to the games</a><div class="eyebrow">Save a seat</div><h1>Your basket.</h1></div>${state.basket.length ? `<div class="two-columns"><section class="panel">${itemLines(state.basket, true)}</section><aside class="panel summary"><h2>Game night, sorted.</h2><div class="totals"><span>Catalog total</span><strong>${money(total())}</strong></div>${state.account ? `<p>Pay with ${html(state.account.paymentMethod)}. Deliver to ${html(state.account.delivery.street)} ${html(state.account.delivery.houseNumber)}.</p><button class="btn full" data-action="buy-basket" ${!basketReady() ? 'disabled' : ''}>Buy these games →</button><a class="text-button" href="#/profile">View your profile</a>` : signIn()}<p class="fine-print">Demo purchases only. No money changes hands.</p></aside></div>` : empty('Your table is still empty.', 'Pick a game or two to get things started.')}`;
}
const fields = [
  ['fullName','Full name','name','text',true], ['email','Email address','email','email',true],
  ['street','Street','address-line1','text'], ['houseNumber','House number','address-line2','text'],
  ['postalCode','Postal code','postal-code','text'], ['city','City','address-level2','text'],
  ['country','Country','country-name','text'], ['phone','Phone (optional)','tel','tel']
];
function signIn() {
  if (state.auth === 'loading') return '<p role="status">Checking your session…</p>';
  if (state.auth === 'error') return '<p class="notice">We could not check your session. <button class="text-button" data-action="retry-account">Try again</button></p>';
  return '<a class="btn" href="/shop-account/login">Sign in to buy →</a>';
}
async function loadAccount() {
  try { const profile = await request('/shop-account/profile'); state.account = profile.signedIn === false ? null : profile; state.auth = state.account ? 'member' : 'guest'; }
  catch (e) { state.account = null; state.auth = e.status === 401 ? 'guest' : 'error'; }
  render(false); resumeCheckout();
}
function profile() {
  const a = state.account;
  if (!a) { main.innerHTML = empty('Your profile.', 'Sign in with a fictional name on the local login screen.', signIn()); return; }
  main.innerHTML = `<div class="page-heading"><div class="eyebrow">My profile</div><h1>Hello, ${html(a.name)}.</h1><p>Your next order is ready for one-click checkout.</p></div><div class="two-columns"><form id="delivery-form" class="panel"><h2>Delivery address</h2><p class="fine-print">Use fictional details only.</p><div class="fields">${fields.map(([key,label,,type,wide]) => `<label class="${wide ? 'wide' : ''}">${label}<input name="${key}" type="${type}" maxlength="200" ${key !== 'phone' ? 'required' : ''} value="${html(a.delivery[key])}"></label>`).join('')}</div><button class="btn" type="submit">Save address</button></form><aside class="panel summary"><h2>Payment method</h2><p><strong>${html(a.paymentMethod)}</strong></p><p>Your demo account is ready to use. No bank details or top-up required.</p><a class="btn" href="#/">Browse games →</a><button class="text-button" data-action="logout">Sign out</button></aside></div>`;
}
const checkout = document.querySelector('#checkout');
const checkoutActions = checkout.querySelector('.checkout-actions');
const checkoutOrder = document.querySelector('#checkout-order');
let checkoutKey;

function checkoutMessage(heading, detail, link, label = 'View order') {
  document.querySelector('#checkout-title').textContent = heading;
  document.querySelector('#checkout-detail').textContent = detail;
  checkoutActions.hidden = !link;
  if (link) { checkoutOrder.href = link; checkoutOrder.textContent = label; }
  if (!checkout.open) {
    checkout.showModal();
    document.querySelector('#checkout-title').focus();
  }
}
function rememberCheckout(purchase) {
  try {
    if (purchase) sessionStorage.setItem(checkoutKey, JSON.stringify(purchase));
    else sessionStorage.removeItem(checkoutKey);
  } catch { /* Optional storage. */ }
}
function closeCheckout() {
  rememberCheckout(null);
  checkout.close();
}
checkout.addEventListener('cancel', event => {
  event.preventDefault();
  if (!checkoutActions.hidden) closeCheckout();
});
checkoutOrder.addEventListener('click', closeCheckout);
document.querySelector('#checkout-continue').addEventListener('click', closeCheckout);

function unknownReceipt() {
  checkoutMessage('Order status unknown', 'Check My orders before trying again. Your order may already have been placed.', '#/orders', 'My orders');
}
function resumeCheckout() {
  if (!state.account || checkout.open) return;
  checkoutKey = 'meepleverse.checkout.' + state.account.name;
  let purchase;
  try { purchase = JSON.parse(sessionStorage.getItem(checkoutKey)); } catch { return; }
  if (!purchase) return;
  if (purchase.id) orderReceived(purchase.id);
  else unknownReceipt();
}
function orderReceived(id) {
  checkoutMessage('Order received', 'Order number: ' + id, '#/orders/' + encodeURIComponent(id));
}
async function buy(ids, fromBasket = false) {
  if (!state.account || checkout.open) return;
  checkoutKey = 'meepleverse.checkout.' + state.account.name;
  rememberCheckout({id:null});
  checkoutMessage('Placing your order…', ids.map(nameFor).join(', '));
  let receipt;
  try {
    receipt = await memberPost('/shop-account/orders', {itemIds:ids});
  } catch (error) {
    if (error.status === 400 || error.status === 401) rememberCheckout(null);
    if (error.status === 401) checkoutMessage('Sign in to buy', 'Your session has expired.', '/shop-account/login', 'Sign in');
    else if (error.status === 400) checkoutMessage('Order could not be placed', 'Check the games in your basket.', '#/basket', 'View basket');
    else unknownReceipt();
    return;
  }
  if (!receipt?.orderId) { unknownReceipt(); return; }
  rememberCheckout({id:receipt.orderId});
  if (fromBasket) {
    state.basket = state.basket.filter(id => !ids.includes(id));
    persist(); render(false);
  }
  orderReceived(receipt.orderId);
}
async function memberPost(path, body) {
  const result = await fetch(path, {method:'POST', cache:'no-store', signal:AbortSignal.timeout(90000), headers:{'Content-Type':'application/json','X-Shop-CSRF':state.account?.csrf || ''}, body:JSON.stringify(body)});
  if (!result.ok) {
    if (result.status === 401) { state.account = null; state.auth = 'guest'; render(false); }
    throw Object.assign(new Error('Request failed'), {status:result.status});
  }
  return result.json();
}
async function orders() {
  const version = routeVersion;
  main.innerHTML = `<div class="page-heading"><div class="eyebrow">On its way to game night</div><h1>Your orders.</h1><p>Games ordered with your Meepleverse profile.</p></div><div id="account-orders">${state.account ? 'Loading orders…' : signIn()}</div>`;
  if (!state.account) return;
  try {
    const result = await request('/shop-account/orders');
    if (version !== routeVersion) return;
    document.querySelector('#account-orders').innerHTML = result.length ? `<div class="recent-orders">${result.map(o => `<a class="recent-order" href="#/orders/${encodeURIComponent(o.orderId)}"><div><strong>${o.itemIds.map(i => html(nameFor(i))).join(', ')}</strong><span class="order-id">${html(o.orderId)}</span></div><span>View order →</span></a>`).join('')}</div>` : empty('Your shelf is waiting.', 'Choose a game to place your first order.');
  } catch (e) { if (version === routeVersion) document.querySelector('#account-orders').innerHTML = e.status === 401 ? '<a href="/shop-account/login">Sign in again</a>' : '<button class="btn secondary" data-action="reload-orders">Try loading your orders again</button>'; }
}
const eventLabels = {
  ORDER_RECEIVED:'Order received', PAYMENT_ACCEPTED:'Demo payment accepted', PAYMENT_REJECTED:'Demo payment rejected',
  ITEM_ARRIVED:'Game arrived from supplier', BACKORDER_FAILED:'Supplier could not complete the backorder',
  SHIPMENT_RECORDED:'Shipment request recorded', SHIPPING_FAILED:'Shipment failed', ORDER_ABORTED:'Order stopped'
};
function orderView(order) {
  const arrived = new Set(order.observations.filter(o => o.kind === 'ITEM_ARRIVED').flatMap(o => o.itemIds));
  const recorded = new Set(order.shipments.flatMap(s => s.itemIds));
  const failedCount = order.shipments.filter(s => s.status === 'FAILED').length;
  const failed = order.aborted || order.paymentStatus === 'REJECTED' || order.observations.some(o => ['BACKORDER_FAILED','SHIPPING_FAILED'].includes(o.kind));
  const allRecorded = order.itemIds.length > 0 && order.itemIds.every(id => recorded.has(id));
  let heading, description;
  if (failed) { heading = 'This order needs attention.'; description = order.abortReason || 'A step could not be completed. The latest confirmed information is shown below.'; }
  else if (order.projectionPending) { heading = 'Your order is in.'; description = 'We are waiting for its first progress update. You can keep this page open or return with your order number.'; }
  else if (allRecorded) { heading = 'Shipment requests recorded.'; description = 'All games have a shipment request. This demo does not provide a carrier confirmation or delivery tracking.'; }
  else if (arrived.size) { heading = 'Your game night is coming together.'; description = 'Games are arriving from the supplier. Each game can move through the order at its own pace.'; }
  else if (order.paymentStatus === 'ACCEPTED') { heading = 'Waiting for your games.'; description = 'Your demo payment was accepted. We are waiting for the supplier to send the games.'; }
  else { heading = 'Your order is in.'; description = 'The demo payment is still awaiting a result. There is no confirmed outcome yet.'; }
  return `<section class="order-summary"><span class="status-pill ${failed ? 'failed' : ''}">${failed ? 'Needs attention' : allRecorded ? 'Awaiting carrier confirmation' : 'In progress'}</span><h2>${heading}</h2><p>${html(description)}</p><div class="steps"><div class="step done"><strong>01 · Received</strong>Order accepted</div><div class="step ${order.paymentStatus === 'ACCEPTED' ? 'done' : 'current'}"><strong>02 · Payment</strong>${order.paymentStatus === 'ACCEPTED' ? 'Demo payment accepted' : order.paymentStatus === 'REJECTED' ? 'Rejected' : 'Awaiting result'}</div><div class="step ${arrived.size === order.itemIds.length ? 'done' : arrived.size ? 'current' : ''}"><strong>03 · Supplier</strong>${arrived.size} of ${order.itemIds.length} games arrived</div><div class="step ${recorded.size ? 'current' : ''}"><strong>04 · Shipping</strong>${failedCount ? `${failedCount} failed` : `${order.shipments.length} request${order.shipments.length === 1 ? '' : 's'} recorded`}</div></div></section>
  <div class="two-columns"><div><section class="panel order-items"><h2>Your games</h2>${order.itemIds.map(id => `<div class="line-item">${cover(itemFor(id) || {itemId:id}, true)}<div class="item-copy"><h3>${html(nameFor(id))}</h3><span class="item-state">${recorded.has(id) ? 'Shipment request recorded — see shipment below' : arrived.has(id) ? 'Arrived from supplier' : failed ? 'No arrival confirmed before the order stopped' : 'Awaiting supplier arrival'}</span></div></div>`).join('')}</section><section class="panel"><h2>Shipments</h2>${order.shipments.length ? order.shipments.map((s, index) => `<div class="shipment"><strong>Shipment ${index + 1} · ${s.status === 'FAILED' ? 'Failed' : 'Request recorded'}</strong><p>${s.itemIds.length ? s.itemIds.map(id => html(nameFor(id))).join(', ') : 'Item details were not recorded.'}</p><p class="order-id">${html(s.shipmentId)}</p></div>`).join('') : '<p class="muted">No shipment requests have been recorded yet.</p>'}<p class="fine-print">A recorded request does not confirm handover to a carrier. The course backend does not report delivery.</p></section></div>
  <aside class="panel"><h2>The story so far</h2>${order.observations.length ? `<ol class="timeline">${[...order.observations].reverse().map(o => `<li><time datetime="${html(o.at)}">${html(new Date(o.at).toLocaleTimeString('en-GB'))}</time><strong>${html(eventLabels[o.kind] || title(o.kind))}</strong>${o.itemIds.length && o.kind !== 'ORDER_RECEIVED' ? `<small>${o.itemIds.map(id => html(nameFor(id))).join(', ')}</small>` : ''}${o.reason ? `<small>${html(o.reason)}</small>` : ''}</li>`).join('')}</ol>` : '<p class="muted">Waiting for the first update…</p>'}</aside></div>`;
}
function orderPage(id, version) {
  main.innerHTML = `<div class="page-heading order-heading"><div><a class="back-link" href="#/orders">← All your orders</a><div class="eyebrow">Something to look forward to</div><h1>Your game night.</h1><p class="order-id">Order ${html(id)}</p></div><button class="btn secondary" data-copy="${html(id)}">Copy order number</button></div><div id="order-content"><div class="loading" role="status">Finding your order…</div></div><div class="live-note"><span id="connection-status" role="status">Connecting…</span><button class="text-button" data-action="refresh-order">Refresh now</button></div>`;
  let failures = 0, previous = '', found = false, signInRequired = false, requestNumber = 0;
  const refresh = async () => {
    clearTimeout(pollTimer);
    if (version !== routeVersion || document.hidden) return;
    const number = ++requestNumber;
    pollController?.abort();
    const controller = new AbortController();
    pollController = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const order = await request(`/shop-account/orders/${encodeURIComponent(id)}`, {signal:controller.signal});
      if (version !== routeVersion || number !== requestNumber) return;
      found = true; failures = 0;
      const serialized = JSON.stringify(order);
      if (serialized !== previous) { document.querySelector('#order-content').innerHTML = orderView(order); previous = serialized; }
      document.querySelector('#connection-status').textContent = `Live updates · checked at ${new Date().toLocaleTimeString('en-GB')}`;
    } catch (error) {
      if (version !== routeVersion || number !== requestNumber || document.hidden) return;
      failures++;
      if (error.status === 401) {
        document.querySelector('#order-content').innerHTML = empty('Sign in to view this order.', 'Use the same local username that placed the order.', '<a class="btn" href="/shop-account/login">Sign in →</a>');
        signInRequired = true; document.querySelector('#connection-status').textContent = 'Sign-in required'; return;
      }
      if (!found) document.querySelector('#order-content').innerHTML = empty(error.status === 404 ? 'Order not found here.' : 'We cannot reach your order.', error.status === 404 ? 'Check the order number and course environment. Recently placed orders can take a moment to appear.' : 'Your order may still be progressing. We will keep trying; there is no need to place it again.', '<a class="btn secondary" href="#/orders">Back to your orders</a>');
      document.querySelector('#connection-status').textContent = found ? 'Connection interrupted · showing the last confirmed update. Retrying…' : error.status === 404 ? 'Order not found · checking again shortly' : 'Waiting for a connection · retrying automatically';
    } finally {
      clearTimeout(timeout);
      if (version === routeVersion && number === requestNumber && !document.hidden && !signInRequired) pollTimer = setTimeout(refresh, Math.min(30000, 3000 * 2 ** Math.min(failures, 4)));
    }
  };
  main.querySelector('[data-action=refresh-order]').onclick = refresh;
  currentRefresh = refresh; refresh();
}
let currentRefresh;
function render(moveFocus = true) {
  routeVersion++; clearTimeout(pollTimer); pollController?.abort(); currentRefresh = null;
  header();
  const route = location.hash.slice(1) || '/';
  if (route === '/basket') basket();
  else if (route === '/profile') profile();
  else if (route === '/login-error') main.innerHTML = empty('We could not sign you in.', 'The login may have expired. Start again to return to the shop.', '<a class="btn" href="/shop-account/login">Try signing in again →</a>');
  else if (route === '/orders') orders();
  else if (route.startsWith('/orders/')) {
    try { orderPage(decodeURIComponent(route.slice(8)), routeVersion); }
    catch { main.innerHTML = empty('That order link is not valid.', 'Open My orders to look up your order.'); }
  } else shop();
  if (moveFocus) { main.focus({preventScroll:true}); window.scrollTo(0,0); }
}
main.addEventListener('error', event => {
  if (event.target.matches?.('.game-photo')) event.target.remove();
}, true);
main.addEventListener('input', event => {
  if (event.target.id === 'search') { state.term = event.target.value; loadCatalog(); }

});
main.addEventListener('click', async event => {
  const button = event.target.closest('button, a'); if (!button) return;
  if (button.dataset.buy) { if (event.detail < 2) buy([button.dataset.buy]); return; }
  if (button.dataset.add) {
    const id = button.dataset.add, included = state.basket.includes(id);
    state.basket = included ? state.basket.filter(i => i !== id) : [...state.basket, id];
    persist(); header();
    button.setAttribute('aria-pressed', String(!included)); button.textContent = included ? '+ Add to basket' : '✓ In basket';
    button.setAttribute('aria-label', `${included ? 'Add' : 'Remove'} ${nameFor(id)} ${included ? 'to' : 'from'} basket`);
    toast(`${nameFor(id)} ${included ? 'removed' : 'added to your basket'}.`);
  }
  if (button.dataset.remove) { state.basket = state.basket.filter(i => i !== button.dataset.remove); persist(); render(false); }
  if (button.hasAttribute('data-category')) {
    state.category = button.dataset.category;
    document.querySelectorAll('[data-category]').forEach(b => b.setAttribute('aria-pressed', String(b === button)));
    loadCatalog();
  }
  if (button.dataset.copy) {
    try { await navigator.clipboard.writeText(button.dataset.copy); toast('Order number copied.'); }
    catch { toast('Select the order number above to copy it.'); }
  }
  switch (button.dataset.action) {
    case 'browse': event.preventDefault(); document.querySelector('#games').scrollIntoView(); document.querySelector('#search').focus({preventScroll:true}); break;
    case 'reload-catalog': loadCatalog(); break;
    case 'clear-search': state.category = ''; state.term = ''; shop(); loadCatalog(); document.querySelector('#search').focus(); break;
    case 'buy-basket': if (basketReady()) buy([...state.basket], true); break;
    case 'retry-account': loadAccount(); break;
    case 'reload-orders': orders(); break;
    case 'logout': button.disabled = true; try { const {next} = await memberPost('/shop-account/logout', {}); state.basket = []; persist(); location.assign(next); } catch { button.disabled = false; toast('Could not sign out. Try again.'); } break;
  }
});
main.addEventListener('submit', async event => {
  event.preventDefault();
  if (event.target.id !== 'delivery-form') return;
  const form = event.target, address = Object.fromEntries(new FormData(form)), button = form.querySelector('[type=submit]');
  button.disabled = true;
  try { await memberPost('/shop-account/delivery', address); state.account.delivery = address; toast('Delivery address saved.'); }
  catch { toast('Your address was not saved. Check your details and session.'); }
  finally { button.disabled = false; }
});
window.addEventListener('hashchange', () => render());
document.querySelector('.skip-link').addEventListener('click', event => {
  event.preventDefault(); main.focus();
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { clearTimeout(pollTimer); pollController?.abort(); }
  else currentRefresh?.();
});
render(false); loadAccount(); loadCatalog();
