import { describe, expect, it } from 'vitest';
import { Transaction } from '@shared/models/transaction.model';
import { loadTransactions, loadTransactionsFailure, loadTransactionsSuccess } from './transactions.actions';
import { initialTransactionsState, transactionsReducer } from './transactions.reducer';

describe('transactionsReducer', () => {
  it('tracks loading, result and failure states', () => {
    const loading = transactionsReducer(
      initialTransactionsState,
      loadTransactions({
        filters: { query: '', dateFrom: '', dateTo: '', page: 0, size: 10 },
      }),
    );
    expect(loading.loading).toBe(true);
    const row: Transaction = {
      id: 'tx-1',
      fromIban: 'LT121000011101001000',
      fromName: 'Alice Kask',
      toName: 'Bob Tamm',
      toIban: 'EE382200221020145685',
      beneficiaryName: 'Bob Tamm',
      nameCheck: 'MATCH',
      amount: 66,
      currency: 'EUR',
      reference: 'test',
      status: 'COMPLETED',
      fromBalanceAfter: 934,
      toBalanceAfter: null,
      createdAt: '2026-01-01T00:00:00Z',
    };
    expect(
      transactionsReducer(
        loading,
        loadTransactionsSuccess({
          result: { content: [row], totalElements: 1, page: 0, size: 10 },
        }),
      ),
    ).toMatchObject({ items: [row], total: 1, loading: false });
    expect(transactionsReducer(loading, loadTransactionsFailure({ error: 'offline' }))).toMatchObject({
      loading: false,
      error: 'offline',
    });
  });
});
