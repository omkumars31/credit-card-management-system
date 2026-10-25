// Administrator console pages (all calls go to /api/admin/**, which the server restricts to ROLE_ADMIN).
import { api } from './api.js';
import { h, replace, money, num, date, dateTime, query, todayUtc, refreshPage } from './dom.js';
import { badge, card, confirmDialog, dataTable, filterBar, formModal, kv, reportError, simpleTable, tile, toast } from './ui.js';
import { PAYMENT_STATUSES, STATEMENT_STATUSES, balanceCard, cardTile, isOverdue, statementModal } from './components.js';

const nav = (hash) => { location.hash = hash; };

export const adminRoutes = [
  { path: '/a/overview', title: 'Overview', label: 'Overview', render: overview },
  { path: '/a/customers', title: 'Customers', label: 'Customers', render: customersPage },
  { path: '/a/bins', title: 'BIN configurations', label: 'BIN rules', render: binsPage },
  { path: '/a/cards', title: 'Cards', label: 'Cards', render: cardsPage },
  { path: '/a/cards/:id', title: 'Card', render: cardDetail },
  { path: '/a/statements', title: 'Statements', label: 'Statements', render: statementsPage },
  { path: '/a/payments', title: 'Payments', label: 'Payments', render: paymentsPage },
  { path: '/a/late-fees', title: 'Late-fee ledger', label: 'Late fees', render: lateFeesPage },
  { path: '/a/audit', title: 'Audit log', label: 'Audit log', render: auditPage },
  { path: '/a/admins', title: 'Administrators', label: 'Administrators', render: adminsPage },
];

const AUDIT_ACTIONS = ['LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'CUSTOMER_REGISTERED', 'CUSTOMER_UPDATED', 'CUSTOMER_STATUS_CHANGED',
  'ADMIN_CREATED', 'ADMIN_ENABLED_CHANGED', 'BIN_CONFIG_CREATED', 'BIN_CONFIG_UPDATED', 'CARD_CREATED', 'CARD_STATUS_CHANGED',
  'CARD_LIMIT_CHANGED', 'BILLING_ENTRY_POSTED', 'STATEMENT_GENERATED', 'PAYMENT_METHOD_ADDED', 'PAYMENT_METHOD_DEACTIVATED',
  'PAYMENT_COMPLETED', 'PAYMENT_REVERSED', 'LATE_FEE_ASSESSED'];

const opts = (values, anyLabel) => [{ value: '', label: anyLabel }, ...values.map((v) => ({ value: v, label: v.replaceAll('_', ' ') }))];
const link = (hash, text) => h('a', { href: hash }, text);

async function customerOptions() {
  const page = await api.get('/api/admin/customers?size=100&sort=id');
  return page.content.map((c) => ({ value: c.id, label: `#${c.id} ${c.fullName} <${c.email}>` }));
}

async function binOptions() {
  const page = await api.get('/api/admin/bin-configurations?size=100&sort=id');
  return page.content.map((b) => ({ value: b.id, label: `${b.binCode} – ${b.name} (cycle day ${b.rules.billingCycleDay})` }));
}

// ---------- overview ----------
async function overview() {
  const count = async (url) => (await api.get(url + (url.includes('?') ? '&' : '?') + 'size=1')).totalElements;
  const [customers, cards, statements, payments, lateFees, bins] = await Promise.all([
    count('/api/admin/customers'), count('/api/admin/cards'), count('/api/admin/statements'),
    count('/api/admin/payments'), count('/api/admin/late-fees'), count('/api/admin/bin-configurations')]);
  const recent = (await api.get('/api/admin/audit-logs?size=8')).content;
  return h('div', { class: 'stack-lg' },
    h('div', { class: 'tiles' },
      tile('Customers', customers), tile('BIN configurations', bins), tile('Cards', cards),
      tile('Statements', statements), tile('Payments', payments), tile('Late fees assessed', lateFees)),
    card('Quick start', h('ol', { class: 'steps' },
      h('li', null, 'Customers register themselves (or use the Register tab on the sign-in page).'),
      h('li', null, link('#/a/bins', 'BIN rules'), ' – review or create billing rules (two seed BINs exist).'),
      h('li', null, link('#/a/cards', 'Cards'), ' – issue a card to a customer, then open it to post transactions.'),
      h('li', null, 'Generate a statement from the card page once the billing day has passed.'),
      h('li', null, 'Customers pay from their own login; overdue statements can receive a late fee under Statements.'))),
    card('Recent activity', simpleTable(auditColumns(), recent, 'No activity yet.'), link('#/a/audit', 'Full audit log')));
}

// ---------- customers ----------
async function customersPage() {
  let filters = {};
  const table = dataTable({
    load: (p, s) => api.get('/api/admin/customers' + query({ ...filters, page: p, size: s, sort: 'id' })),
    columns: [
      { label: 'ID', render: (c) => c.id }, { label: 'Name', render: (c) => c.fullName }, { label: 'Email', render: (c) => c.email },
      { label: 'Phone', render: (c) => c.phone }, { label: 'Status', render: (c) => badge(c.status) },
      { label: 'Registered', render: (c) => dateTime(c.createdAt) },
      { label: '', render: (c) => h('span', { class: 'row-actions' },
        h('button', { class: 'btn small', type: 'button', onclick: () => nav('#/a/cards?customerId=' + c.id) }, 'Cards'),
        h('button', { class: 'btn small ' + (c.status === 'ACTIVE' ? 'danger' : ''), type: 'button', onclick: async () => {
          const target = c.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
          if (!await confirmDialog({ title: target === 'DISABLED' ? 'Disable customer' : 'Enable customer', message: `${c.fullName} will ${target === 'DISABLED' ? 'no longer be able to sign in' : 'be able to sign in again'}.`, confirmLabel: target === 'DISABLED' ? 'Disable' : 'Enable', danger: target === 'DISABLED' })) return;
          try { await api.patch(`/api/admin/customers/${c.id}/status`, { status: target }); toast('Customer ' + target.toLowerCase() + '.', 'success'); table.reload(); } catch (e) { reportError(e); }
        } }, c.status === 'ACTIVE' ? 'Disable' : 'Enable')) },
    ],
  });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'email', label: 'Email contains' }, { name: 'status', type: 'select', label: 'Status', options: opts(['ACTIVE', 'DISABLED'], 'Any status') }],
      (v) => { filters = v; table.reload(true); }),
    card(null, table.el));
}

// ---------- BIN configurations ----------
const BIN_FIELDS = (editing) => [
  { name: 'binCode', label: 'BIN (6 digits)', required: true, readonly: editing, maxLength: 6, hint: editing ? 'Cannot be changed' : 'First 6 digits of generated card numbers' },
  { name: 'name', label: 'Name', required: true, maxLength: 100 },
  { name: 'billingCycleDay', label: 'Billing cycle day (1–28)', type: 'int', required: true, min: 1, max: 28 },
  { name: 'paymentDueDays', label: 'Payment due days (1–27)', type: 'int', required: true, min: 1, max: 27, hint: 'Days after statement date' },
  { name: 'minDuePercentage', label: 'Minimum due %', type: 'decimal', step: '0.0001', required: true, min: 0.0001, max: 100 },
  { name: 'minDueFloor', label: 'Minimum due floor', type: 'decimal', required: true, min: 0 },
  { name: 'financeChargeMonthlyRate', label: 'Finance charge % / month', type: 'decimal', step: '0.0001', required: true, min: 0, max: 25 },
  { name: 'gstRate', label: 'GST %', type: 'decimal', step: '0.0001', required: true, min: 0, max: 50 },
  { name: 'lateFeePercentage', label: 'Late fee %', type: 'decimal', step: '0.0001', required: true, min: 0, max: 100 },
  { name: 'lateFeeFloor', label: 'Late fee floor', type: 'decimal', required: true, min: 0 },
  { name: 'lateFeeCap', label: 'Late fee cap', type: 'decimal', required: true, min: 0, hint: 'Must be ≥ floor' },
  { name: 'lateFeeThreshold', label: 'Late fee threshold', type: 'decimal', required: true, min: 0, hint: 'No fee if statement total is below this' },
];

function binForm(existing, onDone) {
  const initial = existing ? { binCode: existing.binCode, name: existing.name, ...existing.rules } : {
    billingCycleDay: 15, paymentDueDays: 20, minDuePercentage: 5, minDueFloor: 200, financeChargeMonthlyRate: 3.5, gstRate: 18,
    lateFeePercentage: 10, lateFeeFloor: 100, lateFeeCap: 1000, lateFeeThreshold: 500 };
  formModal({
    title: existing ? 'Edit BIN ' + existing.binCode : 'New BIN configuration', wide: true, initial, fields: BIN_FIELDS(!!existing),
    intro: existing ? 'Changing a rule creates a new rule version. Existing statements keep the rules they were generated with.' : null,
    submitLabel: existing ? 'Save changes' : 'Create',
    onSubmit: async (v) => {
      const saved = existing ? await api.put('/api/admin/bin-configurations/' + existing.id, v) : await api.post('/api/admin/bin-configurations', v);
      toast('BIN ' + saved.binCode + ' saved (rule version ' + saved.ruleVersion + ').', 'success');
      onDone();
    },
  });
}

async function binsPage() {
  const table = dataTable({
    load: (p, s) => api.get('/api/admin/bin-configurations' + query({ page: p, size: s, sort: 'id' })),
    columns: [
      { label: 'BIN', render: (b) => b.binCode }, { label: 'Name', render: (b) => b.name },
      { label: 'Cycle / due', render: (b) => `day ${b.rules.billingCycleDay} / +${b.rules.paymentDueDays}d` },
      { label: 'Min due', render: (b) => `${num(b.rules.minDuePercentage)}% (≥ ${money(b.rules.minDueFloor)})` },
      { label: 'Finance', render: (b) => num(b.rules.financeChargeMonthlyRate) + '% / mo' },
      { label: 'GST', render: (b) => num(b.rules.gstRate) + '%' },
      { label: 'Late fee', render: (b) => `${num(b.rules.lateFeePercentage)}% [${money(b.rules.lateFeeFloor)}–${money(b.rules.lateFeeCap)}], ≥ ${money(b.rules.lateFeeThreshold)}` },
      { label: 'Version', render: (b) => 'v' + b.ruleVersion },
      { label: '', render: (b) => h('button', { class: 'btn small', type: 'button', onclick: () => binForm(b, () => table.reload()) }, 'Edit') },
    ],
  });
  return card('BIN billing rules', table.el, h('button', { class: 'btn primary small', type: 'button', onclick: () => binForm(null, () => table.reload()) }, 'New BIN'));
}

// ---------- cards ----------
function cardColumns() {
  return [
    { label: 'ID', render: (c) => c.id }, { label: 'Card', render: (c) => c.maskedNumber }, { label: 'Customer', render: (c) => '#' + c.customerId },
    { label: 'BIN', render: (c) => c.binCode }, { label: 'Limit', align: 'right', render: (c) => money(c.creditLimit) },
    { label: 'Status', render: (c) => badge(c.status) }, { label: 'Expires', render: (c) => date(c.expiresOn) },
    { label: '', render: (c) => h('button', { class: 'btn small', type: 'button', onclick: () => nav('#/a/cards/' + c.id) }, 'Open') },
  ];
}

async function cardsPage(ctx) {
  let filters = { customerId: ctx.params.get('customerId') || '' };
  const table = dataTable({ load: (p, s) => api.get('/api/admin/cards' + query({ ...filters, page: p, size: s, sort: 'id' })), columns: cardColumns() });
  const issue = async () => {
    let customers; let bins;
    try { [customers, bins] = await Promise.all([customerOptions(), binOptions()]); } catch (e) { reportError(e); return; }
    if (!customers.length) { toast('No customers yet. Customers register on the sign-in page.', 'warn'); return; }
    formModal({
      title: 'Issue card', intro: 'A synthetic card number is generated. Only a masked form is stored and shown.',
      fields: [
        { name: 'customerId', label: 'Customer', type: 'select', numeric: true, required: true, options: customers },
        { name: 'binConfigurationId', label: 'BIN configuration', type: 'select', numeric: true, required: true, options: bins },
        { name: 'creditLimit', label: 'Credit limit (INR)', type: 'decimal', required: true, min: 1, value: '50000.00' },
      ],
      submitLabel: 'Issue card',
      onSubmit: async (v) => { const c = await api.post('/api/admin/cards', v); toast('Card ' + c.maskedNumber + ' issued.', 'success'); table.reload(); },
    });
  };
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'customerId', type: 'number', label: 'Customer id' }, { name: 'status', type: 'select', label: 'Status', options: opts(['ACTIVE', 'BLOCKED', 'CLOSED'], 'Any status') }],
      (v) => { filters = v; table.reload(true); }),
    card(null, table.el, h('button', { class: 'btn primary small', type: 'button', onclick: issue }, 'Issue card')));
}

function latestCycleDate(cycleDay) {
  const now = new Date();
  let y = now.getUTCFullYear(); let m = now.getUTCMonth();
  if (now.getUTCDate() < cycleDay) { m -= 1; if (m < 0) { m = 11; y -= 1; } }
  return new Date(Date.UTC(y, m, cycleDay)).toISOString().slice(0, 10);
}

async function cardDetail({ id }) {
  const [c, balance] = await Promise.all([api.get('/api/admin/cards/' + id), api.get(`/api/admin/cards/${id}/balance`)]);
  const stmts = dataTable({ size: 5, load: (p, s) => api.get('/api/admin/statements' + query({ cardId: id, page: p, size: s })), columns: statementColumns(() => stmts.reload()), emptyText: 'No statements yet.' });
  const entries = dataTable({
    load: (p, s) => api.get(`/api/admin/cards/${id}/entries` + query({ page: p, size: s })),
    columns: [
      { label: 'Date', render: (e) => date(e.transactionDate) }, { label: 'Type', render: (e) => badge(e.entryType) },
      { label: 'Description', render: (e) => e.description }, { label: 'Reference', render: (e) => e.referenceId },
      { label: 'Amount', align: 'right', render: (e) => (e.entryType === 'REFUND' ? '− ' : '') + money(e.amount) },
      { label: 'Statement', render: (e) => (e.statementId ? '#' + e.statementId : 'unbilled') },
    ],
    emptyText: 'No transactions yet.',
  });
  const changeStatus = async (target) => {
    if (!await confirmDialog({ title: 'Change card status', message: `Set card ${c.maskedNumber} to ${target}?` + (target === 'CLOSED' ? ' This cannot be undone.' : ''), confirmLabel: target, danger: target !== 'ACTIVE' })) return;
    try { await api.patch(`/api/admin/cards/${id}/status`, { status: target }); toast('Card is now ' + target + '.', 'success'); refreshPage(); } catch (e) { reportError(e); }
  };
  const postEntry = () => formModal({
    title: 'Post transaction', intro: 'Simulated card transaction feed. Purchases are checked against the available credit.',
    fields: [
      { name: 'entryType', label: 'Type', type: 'select', required: true, options: [{ value: 'PURCHASE', label: 'Purchase' }, { value: 'FEE', label: 'Fee' }, { value: 'REFUND', label: 'Refund (credit)' }] },
      { name: 'amount', label: 'Amount (INR)', type: 'decimal', required: true, min: 0.01 },
      { name: 'description', label: 'Description', required: true, maxLength: 255 },
      { name: 'transactionDate', label: 'Transaction date', type: 'date', value: todayUtc(), hint: 'Not in the future' },
      { name: 'referenceId', label: 'Reference id (optional)', hint: 'Makes the request idempotent per card' },
    ],
    submitLabel: 'Post',
    onSubmit: async (v) => { await api.post(`/api/admin/cards/${id}/entries`, v); toast('Transaction posted.', 'success'); refreshPage(); },
  });
  const generate = async () => {
    let cycleDay = 15;
    try { cycleDay = (await api.get('/api/admin/bin-configurations/' + c.binConfigurationId)).rules.billingCycleDay; } catch (e) { reportError(e); }
    formModal({
      title: 'Generate statement', intro: 'Bills all unbilled transactions up to the period end. Running it twice for the same period is rejected.',
      fields: [{ name: 'periodEnd', label: 'Period end', type: 'date', required: true, value: latestCycleDate(cycleDay), hint: `Must be the BIN billing cycle day (${cycleDay}), not in the future` }],
      submitLabel: 'Generate',
      onSubmit: async (v) => { const s = await api.post('/api/admin/statements/generate', { cardId: Number(id), periodEnd: v.periodEnd }); toast('Statement #' + s.id + ' generated: total ' + money(s.totalAmountDue) + '.', 'success'); refreshPage(); },
    });
  };
  const limit = () => formModal({
    title: 'Change credit limit', fields: [{ name: 'creditLimit', label: 'New limit (INR)', type: 'decimal', required: true, min: 1, value: c.creditLimit }], submitLabel: 'Update',
    onSubmit: async (v) => { await api.put(`/api/admin/cards/${id}/credit-limit`, v); toast('Credit limit updated.', 'success'); refreshPage(); },
  });
  const closed = c.status === 'CLOSED';
  return h('div', { class: 'stack-lg' },
    h('a', { href: '#/a/cards', class: 'back' }, '← All cards'),
    h('div', { class: 'two-col' }, cardTile(c, balance),
      h('div', { class: 'stack' }, balanceCard(balance), kv([['Customer', link('#/a/customers', '#' + c.customerId)], ['BIN', c.binCode]]))),
    card('Actions', h('div', { class: 'btn-row' },
      h('button', { class: 'btn primary', type: 'button', disabled: closed, onclick: postEntry }, 'Post transaction'),
      h('button', { class: 'btn primary', type: 'button', disabled: closed, onclick: generate }, 'Generate statement'),
      h('button', { class: 'btn', type: 'button', disabled: closed, onclick: limit }, 'Change limit'),
      c.status === 'ACTIVE' ? h('button', { class: 'btn', type: 'button', onclick: () => changeStatus('BLOCKED') }, 'Block') : null,
      c.status === 'BLOCKED' ? h('button', { class: 'btn', type: 'button', onclick: () => changeStatus('ACTIVE') }, 'Unblock') : null,
      !closed ? h('button', { class: 'btn danger', type: 'button', onclick: () => changeStatus('CLOSED') }, 'Close card') : null)),
    card('Statements', stmts.el), card('Transactions', entries.el));
}

// ---------- statements ----------
async function assessLateFee(s) {
  if (!await confirmDialog({ title: 'Assess late fee', message: `Assess a late fee for statement #${s.id}? This is recorded in the append-only ledger and can happen only once per statement.`, confirmLabel: 'Assess' })) return;
  try {
    const f = await api.post(`/api/admin/statements/${s.id}/late-fee`, {});
    toast('Late fee ' + money(f.totalAmount) + ' (fee ' + money(f.feeAmount) + ' + GST ' + money(f.gstAmount) + ') posted.', 'success');
    refreshPage();
  } catch (e) { reportError(e); }
}

function statementColumns(reload) {
  return [
    { label: '#', render: (s) => s.id }, { label: 'Card', render: (s) => link('#/a/cards/' + s.cardId, s.maskedCardNumber) },
    { label: 'Period', render: (s) => date(s.periodStart) + ' – ' + date(s.periodEnd) },
    { label: 'Due', render: (s) => [date(s.dueDate), isOverdue(s) ? h('span', { class: 'badge bad' }, 'overdue') : null] },
    { label: 'Total', align: 'right', render: (s) => money(s.totalAmountDue) }, { label: 'Min due', align: 'right', render: (s) => money(s.minimumAmountDue) },
    { label: 'Outstanding', align: 'right', render: (s) => money(s.outstandingAmount) }, { label: 'Status', render: (s) => badge(s.paymentStatus) },
    { label: '', render: (s) => h('button', { class: 'btn small', type: 'button', onclick: () => {
      const m = statementModal(s, isOverdue(s) ? [h('button', { class: 'btn danger', type: 'button', onclick: () => { m.close(); assessLateFee(s); } }, 'Assess late fee')] : []);
    } }, 'View') },
  ];
}

async function statementsPage(ctx) {
  let filters = { cardId: ctx.params.get('cardId') || '' };
  const table = dataTable({ load: (p, s) => api.get('/api/admin/statements' + query({ ...filters, page: p, size: s })), columns: statementColumns() });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'cardId', type: 'number', label: 'Card id' }, { name: 'status', type: 'select', label: 'Status', options: STATEMENT_STATUSES }], (v) => { filters = v; table.reload(true); }),
    card(null, table.el));
}

// ---------- payments ----------
async function paymentsPage() {
  let filters = {};
  const table = dataTable({
    load: (p, s) => api.get('/api/admin/payments' + query({ ...filters, page: p, size: s })),
    columns: [
      { label: '#', render: (p) => p.id }, { label: 'When', render: (p) => dateTime(p.completedAt) }, { label: 'Statement', render: (p) => '#' + p.statementId },
      { label: 'Amount', align: 'right', render: (p) => money(p.amount) }, { label: 'Status', render: (p) => badge(p.status) },
      { label: 'Reversal', render: (p) => (p.status === 'REVERSED' ? dateTime(p.reversedAt) + ' – ' + (p.reversalReason || '') : '') },
      { label: '', render: (p) => (p.status === 'COMPLETED' ? h('button', { class: 'btn small danger', type: 'button', onclick: () => formModal({
        title: 'Reverse payment #' + p.id, intro: 'Only possible while the statement is still the card’s latest. The balance is restored and a reversal entry is recorded.',
        fields: [{ name: 'reason', label: 'Reason', type: 'textarea', required: true, maxLength: 255 }], submitLabel: 'Reverse payment',
        onSubmit: async (v) => { await api.post(`/api/admin/payments/${p.id}/reverse`, v); toast('Payment reversed.', 'success'); table.reload(); },
      }) }, 'Reverse') : '') },
    ],
  });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'customerId', type: 'number', label: 'Customer id' }, { name: 'statementId', type: 'number', label: 'Statement id' }, { name: 'status', type: 'select', label: 'Status', options: PAYMENT_STATUSES }],
      (v) => { filters = v; table.reload(true); }),
    card(null, table.el));
}

async function lateFeesPage() {
  let filters = {};
  const table = dataTable({
    load: (p, s) => api.get('/api/admin/late-fees' + query({ ...filters, page: p, size: s })),
    columns: [
      { label: '#', render: (f) => f.id }, { label: 'Assessed', render: (f) => dateTime(f.assessedAt) }, { label: 'Statement', render: (f) => '#' + f.statementId },
      { label: 'Card', render: (f) => link('#/a/cards/' + f.cardId, '#' + f.cardId) }, { label: 'Unpaid min', align: 'right', render: (f) => money(f.unpaidMinimumDue) },
      { label: 'Fee', align: 'right', render: (f) => money(f.feeAmount) }, { label: 'GST', align: 'right', render: (f) => money(f.gstAmount) },
      { label: 'Total', align: 'right', render: (f) => money(f.totalAmount) }, { label: 'Rules', render: (f) => 'v' + f.ruleVersion },
      { label: 'By', render: (f) => f.assessedBy }, { label: 'Status', render: (f) => badge(f.status) },
    ],
  });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'statementId', type: 'number', label: 'Statement id' }, { name: 'cardId', type: 'number', label: 'Card id' }], (v) => { filters = v; table.reload(true); }),
    card('Append-only late-fee ledger', table.el));
}

// ---------- audit ----------
function auditColumns() {
  return [
    { label: 'Time', render: (a) => dateTime(a.occurredAt) }, { label: 'Actor', render: (a) => a.actorType + (a.actorName ? ': ' + a.actorName : '') },
    { label: 'Action', render: (a) => badge(a.action) }, { label: 'Entity', render: (a) => a.entityType + (a.entityId ? ' #' + a.entityId : '') },
    { label: 'Details', render: (a) => h('span', { class: 'mono small' }, a.details) },
  ];
}

async function auditPage() {
  let filters = {};
  const table = dataTable({ load: (p, s) => api.get('/api/admin/audit-logs' + query({ ...filters, page: p, size: s })), columns: auditColumns(), size: 20 });
  return h('div', { class: 'stack-lg' },
    filterBar([{ name: 'action', type: 'select', label: 'Action', options: opts(AUDIT_ACTIONS, 'Any action') }, { name: 'entityType', label: 'Entity type' }, { name: 'entityId', label: 'Entity id' },
      { name: 'actorType', type: 'select', label: 'Actor', options: opts(['CUSTOMER', 'ADMIN', 'SYSTEM'], 'Any actor') }], (v) => { filters = v; table.reload(true); }),
    card('Append-only audit trail', table.el));
}

// ---------- administrators ----------
async function adminsPage() {
  const table = dataTable({
    load: (p, s) => api.get('/api/admin/administrators' + query({ page: p, size: s })),
    columns: [
      { label: 'ID', render: (a) => a.id }, { label: 'Username', render: (a) => a.username }, { label: 'Name', render: (a) => a.fullName },
      { label: 'Status', render: (a) => badge(a.enabled ? 'ACTIVE' : 'DISABLED') }, { label: 'Created', render: (a) => dateTime(a.createdAt) },
      { label: '', render: (a) => h('button', { class: 'btn small', type: 'button', onclick: async () => {
        try { await api.patch(`/api/admin/administrators/${a.id}/enabled`, { enabled: !a.enabled }); toast('Administrator ' + (a.enabled ? 'disabled' : 'enabled') + '.', 'success'); table.reload(); } catch (e) { reportError(e); }
      } }, a.enabled ? 'Disable' : 'Enable') },
    ],
  });
  const create = () => formModal({
    title: 'New administrator',
    fields: [
      { name: 'username', label: 'Username', required: true, hint: '3–60 characters: letters, digits, . _ -' },
      { name: 'fullName', label: 'Full name', required: true },
      { name: 'password', label: 'Password', type: 'password', required: true, hint: 'At least 12 characters' },
    ],
    submitLabel: 'Create', onSubmit: async (v) => { await api.post('/api/admin/administrators', v); toast('Administrator created.', 'success'); table.reload(); },
  });
  return card('Administrator accounts', table.el, h('button', { class: 'btn primary small', type: 'button', onclick: create }, 'New administrator'));
}
