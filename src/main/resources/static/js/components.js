// Pieces shared by the customer and administrator views.
import { h, money, num, date, dateTime, todayUtc } from './dom.js';
import { badge, kv, openModal, card } from './ui.js';

export const STATEMENT_STATUSES = [{ value: '', label: 'Any status' }, { value: 'UNPAID', label: 'Unpaid' },
  { value: 'PARTIALLY_PAID', label: 'Partially paid' }, { value: 'PAID', label: 'Paid' }];

export const PAYMENT_STATUSES = [{ value: '', label: 'Any status' }, { value: 'COMPLETED', label: 'Completed' }, { value: 'REVERSED', label: 'Reversed' }];

export function isOverdue(statement) {
  return statement.paymentStatus !== 'PAID' && statement.dueDate < todayUtc();
}

/** Full statement breakdown: how the total was built, plus the billing rules that were applied. */
export function statementModal(s, actions = []) {
  const r = s.rulesApplied || {};
  const lines = h('table', { class: 'table compact' },
    h('tbody', null,
      line('Previous balance', s.previousBalance),
      line('+ Purchases', s.purchasesTotal),
      line('+ Fees (incl. late fee + GST)', s.feesTotal),
      line('+ Finance charge', s.financeCharge),
      line('+ GST on finance charge', s.gstOnFinanceCharge),
      line('− Credits / refunds', s.creditsTotal),
      h('tr', { class: 'total' }, h('td', null, 'Total amount due'), h('td', { class: 'num' }, money(s.totalAmountDue))),
      line('Minimum amount due', s.minimumAmountDue),
      line('Paid so far', s.paidAmount),
      h('tr', { class: 'total' }, h('td', null, 'Outstanding'), h('td', { class: 'num' }, money(s.outstandingAmount)))));
  const rules = kv([
    ['Billing cycle day', r.billingCycleDay], ['Payment due days', r.paymentDueDays],
    ['Minimum due', num(r.minDuePercentage) + '% (floor ' + money(r.minDueFloor) + ')'],
    ['Finance charge / month', num(r.financeChargeMonthlyRate) + '%'], ['GST', num(r.gstRate) + '%'],
    ['Late fee', num(r.lateFeePercentage) + '% of unpaid minimum, ' + money(r.lateFeeFloor) + ' – ' + money(r.lateFeeCap)],
    ['Late fee threshold', money(r.lateFeeThreshold)],
  ]);
  return openModal({
    title: 'Statement #' + s.id,
    wide: true,
    content: [
      kv([
        ['Card', s.maskedCardNumber], ['Status', badge(s.paymentStatus)],
        ['Period', date(s.periodStart) + ' – ' + date(s.periodEnd)],
        ['Due date', date(s.dueDate) + (isOverdue(s) ? ' (overdue)' : '')],
        ['Generated', dateTime(s.generatedAt)], ['Rule version', s.ruleVersion],
      ]),
      lines,
      h('details', null, h('summary', null, 'Billing rules applied to this statement (snapshot)'), rules),
      actions.length ? h('div', { class: 'actions' }, actions) : null,
    ],
  });
  function line(label, value) {
    return h('tr', null, h('td', null, label), h('td', { class: 'num' }, money(value)));
  }
}

/** Card summary tile with an exposure bar. `balance` is optional. */
export function cardTile(c, balance, onOpen) {
  const used = balance ? Math.min(100, Math.round((Number(balance.totalExposure) / Number(balance.creditLimit)) * 100)) : 0;
  const bar = h('div', { class: 'bar' }, h('span', { class: 'bar-fill' + (used > 85 ? ' hot' : '') }));
  bar.firstChild.style.width = used + '%';
  return h('article', { class: 'credit-card' + (c.status !== 'ACTIVE' ? ' dim' : '') },
    h('div', { class: 'cc-top' }, h('span', { class: 'cc-chip' }), badge(c.status)),
    h('div', { class: 'cc-number' }, c.maskedNumber.replace(/(.{4})(?=.)/g, '$1 ')),
    h('div', { class: 'cc-row' }, h('span', null, 'Limit'), h('strong', null, money(c.creditLimit))),
    balance ? [bar,
      h('div', { class: 'cc-row' }, h('span', null, 'Used'), h('strong', null, money(balance.totalExposure))),
      h('div', { class: 'cc-row' }, h('span', null, 'Available'), h('strong', null, money(balance.availableCredit)))] : null,
    h('div', { class: 'cc-foot' }, h('span', { class: 'muted' }, 'Expires ' + date(c.expiresOn)),
      onOpen ? h('button', { class: 'btn small', type: 'button', onclick: () => onOpen(c) }, 'Open') : null));
}

export function balanceCard(b) {
  return card('Balance', kv([
    ['Credit limit', money(b.creditLimit)],
    ['Latest statement outstanding', money(b.statementOutstanding)],
    ['Unbilled transactions', money(b.unbilledNet)],
    ['Total exposure', money(b.totalExposure)],
    ['Available credit', money(b.availableCredit)],
  ]));
}
