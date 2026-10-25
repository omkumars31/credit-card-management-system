// Customer portal pages. Customer id never appears in URLs: the server derives it from the session.
import { api } from './api.js';
import { h, replace, money, date, dateTime, query, newKey, refreshPage } from './dom.js';
import { badge, card, confirmDialog, dataTable, errorMessage, fieldRow, filterBar, formModal, kv, openModal, reportError,
  simpleTable, tile, toast } from './ui.js';
import { PAYMENT_STATUSES, STATEMENT_STATUSES, balanceCard, cardTile, isOverdue, statementModal } from './components.js';

const nav = (hash) => { location.hash = hash; };

export const customerRoutes = [
  { path: '/c/overview', title: 'Overview', label: 'Overview', render: overview },
  { path: '/c/cards', title: 'My cards', label: 'Cards', render: cardsPage },
  { path: '/c/cards/:id', title: 'Card details', render: cardDetail },
  { path: '/c/statements', title: 'Statements', label: 'Statements', render: statementsPage },
  { path: '/c/payments', title: 'Payments', label: 'Payments', render: paymentsPage },
  { path: '/c/methods', title: 'Payment methods', label: 'Payment methods', render: methodsPage },
  { path: '/c/late-fees', title: 'Late fees', label: 'Late fees', render: lateFeesPage },
  { path: '/c/profile', title: 'Profile', label: 'Profile', render: profilePage },
];

// ---------- shared ----------
async function cardsWithBalances() {
  const cards = await api.get('/api/customers/me/cards');
  const balances = await Promise.all(cards.map((c) => api.get(`/api/customers/me/cards/${c.id}/balance`).catch(() => null)));
  return cards.map((c, i) => ({ card: c, balance: balances[i] }));
}

export function statementColumns(onView, onPay) {
  return [
    { label: '#', render: (s) => s.id },
    { label: 'Card', render: (s) => s.maskedCardNumber },
    { label: 'Period', render: (s) => date(s.periodStart) + ' – ' + date(s.periodEnd) },
    { label: 'Due', render: (s) => [date(s.dueDate), isOverdue(s) ? h('span', { class: 'badge bad' }, 'overdue') : null] },
    { label: 'Total', align: 'right', render: (s) => money(s.totalAmountDue) },
    { label: 'Min due', align: 'right', render: (s) => money(s.minimumAmountDue) },
    { label: 'Outstanding', align: 'right', render: (s) => money(s.outstandingAmount) },
    { label: 'Status', render: (s) => badge(s.paymentStatus) },
    { label: '', render: (s) => h('span', { class: 'row-actions' },
      h('button', { class: 'btn small', type: 'button', onclick: () => onView(s) }, 'View'),
      Number(s.outstandingAmount) > 0 ? h('button', { class: 'btn small primary', type: 'button', onclick: () => onPay(s) }, 'Pay') : null) },
  ];
}

function viewStatement(s, refresh) {
  const modal = statementModal(s, Number(s.outstandingAmount) > 0
    ? [h('button', { class: 'btn primary', type: 'button', onclick: () => { modal.close(); payFlow(s, refresh); } }, 'Pay this statement')] : []);
}

// ---------- pay ----------
export async function payFlow(statement, refresh) {
  let methods;
  try {
    methods = (await api.get('/api/customers/me/payment-methods')).filter((m) => m.active);
  } catch (e) { reportError(e); return; }
  if (!methods.length) {
    toast('Add a payment method first.', 'warn');
    nav('#/c/methods');
    return;
  }
  const key = newKey(); // one key per payment attempt: retrying after a network error can never double-charge
  const outstanding = Number(statement.outstandingAmount).toFixed(2);
  const select = h('select', { name: 'method' }, methods.map((m) => h('option', { value: m.id }, `${m.displayName} (${m.type.replaceAll('_', ' ')} ${m.maskedIdentifier})`)));
  const amount = h('input', { name: 'amount', type: 'number', step: '0.01', min: '0.01', max: outstanding, value: statement.minimumAmountDue > 0 ? Number(statement.minimumAmountDue).toFixed(2) : outstanding, required: true });
  const err = h('div', { class: 'form-error', role: 'alert' });
  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Pay now');
  const quick = (label, v) => h('button', { class: 'btn small', type: 'button', onclick: () => { amount.value = Number(v).toFixed(2); } }, label);
  const form = h('form', { class: 'form' },
    kv([['Statement', '#' + statement.id + ' · ' + statement.maskedCardNumber], ['Total due', money(statement.totalAmountDue)],
      ['Minimum due', money(statement.minimumAmountDue)], ['Outstanding', money(statement.outstandingAmount)]]),
    fieldRow('Pay with', select),
    fieldRow('Amount (INR)', amount, 'Between 0.01 and the outstanding amount'),
    h('div', { class: 'quick' }, quick('Minimum due', Math.min(Number(statement.minimumAmountDue), Number(outstanding))), quick('Full outstanding', outstanding)),
    h('p', { class: 'muted small' }, 'Payments are simulated. This request carries an Idempotency-Key, so it can safely be retried.'),
    err,
    h('div', { class: 'actions' }, h('button', { class: 'btn', type: 'button', onclick: () => modal.close() }, 'Cancel'), submit));
  const modal = openModal({ title: 'Pay statement #' + statement.id, content: form });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    submit.disabled = true;
    try {
      const res = await api.post('/api/customers/me/payments',
        { statementId: statement.id, paymentMethodId: Number(select.value), amount: amount.value.trim() },
        { headers: { 'Idempotency-Key': key }, full: true });
      toast(res.status === 200 ? 'This payment was already recorded (no second charge).' : 'Payment of ' + money(res.data.amount) + ' recorded.', 'success');
      modal.close();
      if (refresh) refresh();
    } catch (ex) {
      err.textContent = errorMessage(ex);
    } finally {
      submit.disabled = false;
    }
  });
}

// ---------- pages ----------
async function overview() {
  const [items, stmts, profile] = await Promise.all([
    cardsWithBalances(),
    api.get('/api/customers/me/statements?size=5'),
    api.get('/api/customers/me'),
  ]);
  const totalLimit = items.reduce((a, i) => a + Number(i.card.creditLimit), 0);
  const used = items.reduce((a, i) => a + (i.balance ? Number(i.balance.totalExposure) : 0), 0);
  const due = stmts.content.filter((s) => Number(s.outstandingAmount) > 0);
  return h('div', { class: 'stack-lg' },
    h('div', { class: 'tiles' },
      tile('Cards', items.length, items.length ? 'Total limit ' + money(totalLimit) : 'No card issued yet'),
      tile('Total used', money(used), items.length ? Math.round((used / (totalLimit || 1)) * 100) + '% of limit' : null),
      tile('Available credit', money(totalLimit - used)),
      tile('Statements to pay', due.length, due.length ? 'Outstanding ' + money(due.reduce((a, s) => a + Number(s.outstandingAmount), 0)) : 'All settled')),
    items.length
      ? h('div', { class: 'cc-grid' }, items.map((i) => cardTile(i.card, i.balance, (c) => nav('#/c/cards/' + c.id))))
      : card(null, h('div', { class: 'empty' }, 'Welcome, ' + profile.fullName + '. An administrator will issue your first card.')),
    card('Latest statements', simpleTable(statementColumns((s) => viewStatement(s, refreshPage), (s) => payFlow(s, refreshPage)), stmts.content, 'No statements yet.'),
      h('a', { class: 'btn small', href: '#/c/statements' }, 'All statements')));
}

async function cardsPage() {
  const items = await cardsWithBalances();
  return items.length
    ? h('div', { class: 'cc-grid' }, items.map((i) => cardTile(i.card, i.balance, (c) => nav('#/c/cards/' + c.id))))
    : card(null, h('div', { class: 'empty' }, 'You do not have any card yet.'));
}

async function cardDetail({ id }) {
  const [c, balance] = await Promise.all([api.get(`/api/customers/me/cards/${id}`), api.get(`/api/customers/me/cards/${id}/balance`)]);
  const stmtTable = dataTable({
    size: 5,
    load: (p, s) => api.get('/api/customers/me/statements' + query({ cardId: id, page: p, size: s })),
    columns: statementColumns((s) => viewStatement(s, () => stmtTable.reload()), (s) => payFlow(s, () => stmtTable.reload())),
    emptyText: 'No statements for this card yet.',
  });
  const entries = dataTable({
    load: (p, s) => api.get(`/api/customers/me/cards/${id}/entries` + query({ page: p, size: s })),
    columns: [
      { label: 'Date', render: (e) => date(e.transactionDate) },
      { label: 'Type', render: (e) => badge(e.entryType) },
      { label: 'Description', render: (e) => e.description },
      { label: 'Amount', align: 'right', render: (e) => (e.entryType === 'REFUND' ? '− ' : '') + money(e.amount) },
      { label: 'Billed', render: (e) => (e.statementId ? 'Statement #' + e.statementId : 'Not yet') },
    ],
    emptyText: 'No transactions yet.',
  });
  return h('div', { class: 'stack-lg' },
    h('a', { href: '#/c/cards', class: 'back' }, '← All cards'),
    h('div', { class: 'two-col' }, cardTile(c, balance), balanceCard(balance)),
    card('Statements', stmtTable.el),
    card('Transactions', entries.el));
}

async function statementsPage() {
  const cards = await api.get('/api/customers/me/cards');
  let filters = {};
  const table = dataTable({
    load: (p, s) => api.get('/api/customers/me/statements' + query({ ...filters, page: p, size: s })),
    columns: statementColumns((s) => viewStatement(s, () => table.reload()), (s) => payFlow(s, () => table.reload())),
    emptyText: 'No statements yet. They appear after your card’s billing date.',
  });
  return h('div', { class: 'stack-lg' },
    filterBar([
      { name: 'cardId', type: 'select', label: 'Card', options: [{ value: '', label: 'All cards' }, ...cards.map((c) => ({ value: c.id, label: c.maskedNumber }))] },
      { name: 'status', type: 'select', label: 'Status', options: STATEMENT_STATUSES }],
    (v) => { filters = v; table.reload(true); }),
    card(null, table.el));
}

async function paymentsPage() {
  let filters = {};
  const table = dataTable({
    load: (p, s) => api.get('/api/customers/me/payments' + query({ ...filters, page: p, size: s })),
    columns: [
      { label: '#', render: (p) => p.id },
      { label: 'When', render: (p) => dateTime(p.completedAt) },
      { label: 'Statement', render: (p) => '#' + p.statementId },
      { label: 'Amount', align: 'right', render: (p) => money(p.amount) },
      { label: 'Status', render: (p) => badge(p.status) },
      { label: 'Note', render: (p) => (p.status === 'REVERSED' ? 'Reversed ' + dateTime(p.reversedAt) + (p.reversalReason ? ' – ' + p.reversalReason : '') : '') },
    ],
    emptyText: 'No payments yet.',
  });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'status', type: 'select', label: 'Status', options: PAYMENT_STATUSES }], (v) => { filters = v; table.reload(true); }),
    card(null, table.el));
}

async function methodsPage() {
  const body = h('div');
  async function load() {
    const methods = await api.get('/api/customers/me/payment-methods');
    replace(body, simpleTable([
      { label: 'Name', render: (m) => m.displayName },
      { label: 'Type', render: (m) => m.type.replaceAll('_', ' ') },
      { label: 'Identifier', render: (m) => m.maskedIdentifier },
      { label: 'Status', render: (m) => badge(m.active ? 'ACTIVE' : 'DISABLED') },
      { label: '', render: (m) => (m.active ? h('button', { class: 'btn small danger', type: 'button', onclick: async () => {
        if (await confirmDialog({ title: 'Remove payment method', message: `Deactivate “${m.displayName}”? Past payments are kept.`, confirmLabel: 'Deactivate', danger: true })) {
          try { await api.del('/api/customers/me/payment-methods/' + m.id); toast('Payment method deactivated.', 'success'); load(); } catch (e) { reportError(e); }
        } } }, 'Deactivate') : '') },
    ], methods, 'No payment methods yet.'));
  }
  await load();
  const add = () => formModal({
    title: 'Add payment method',
    intro: 'Simulated instrument. Only a masked form is stored.',
    fields: [
      { name: 'type', label: 'Type', type: 'select', required: true, options: ['UPI', 'NET_BANKING', 'DEBIT_CARD', 'BANK_TRANSFER'].map((t) => ({ value: t, label: t.replaceAll('_', ' ') })) },
      { name: 'displayName', label: 'Display name', required: true, maxLength: 80, placeholder: 'e.g. My UPI' },
      { name: 'identifier', label: 'Identifier (UPI id / account / card)', required: true, hint: 'Only the last 4 characters are kept.' },
    ],
    submitLabel: 'Add',
    onSubmit: async (v) => { await api.post('/api/customers/me/payment-methods', v); toast('Payment method added.', 'success'); await load(); },
  });
  return card('Saved payment methods', body, h('button', { class: 'btn primary small', type: 'button', onclick: add }, 'Add method'));
}

async function lateFeesPage() {
  const table = dataTable({
    load: (p, s) => api.get('/api/customers/me/late-fees' + query({ page: p, size: s })),
    columns: [
      { label: 'Assessed', render: (f) => dateTime(f.assessedAt) },
      { label: 'Statement', render: (f) => '#' + f.statementId },
      { label: 'Unpaid minimum', align: 'right', render: (f) => money(f.unpaidMinimumDue) },
      { label: 'Fee', align: 'right', render: (f) => money(f.feeAmount) },
      { label: 'GST', align: 'right', render: (f) => money(f.gstAmount) + ' (' + Number(f.gstRate) + '%)' },
      { label: 'Total', align: 'right', render: (f) => money(f.totalAmount) },
      { label: 'Status', render: (f) => badge(f.status) },
    ],
    emptyText: 'No late fees. Pay at least the minimum due before the due date to avoid them.',
  });
  return card('Late fee ledger', table.el);
}

async function profilePage() {
  const p = await api.get('/api/customers/me');
  const root = h('div');
  const show = (profile) => replace(root, kv([['Name', profile.fullName], ['Email', profile.email], ['Phone', profile.phone], ['Status', badge(profile.status)], ['Member since', dateTime(profile.createdAt)]]));
  show(p);
  return card('Your profile', root, h('button', { class: 'btn small', type: 'button', onclick: () => formModal({
    title: 'Edit profile',
    fields: [{ name: 'fullName', label: 'Full name', required: true, maxLength: 120 }, { name: 'phone', label: 'Phone', placeholder: '+919876543210' }],
    initial: { fullName: p.fullName, phone: p.phone },
    onSubmit: async (v) => { const u = await api.put('/api/customers/me', v); Object.assign(p, u); show(p); toast('Profile updated.', 'success'); },
  }) }, 'Edit'));
}
