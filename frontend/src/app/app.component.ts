import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from './core/auth.service';
import { ApiService } from './core/api.service';
import { Account, Transaction } from './core/models';
import { ibanValidator } from './core/iban.validator';
import { uuid } from './core/uuid';

type View = 'accounts' | 'payment' | 'transactions';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, ReactiveFormsModule, MatButtonModule, MatCardModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule, MatProgressBarModule,
    MatSelectModule, MatTableModule, MatToolbarModule],
  template: `
    <mat-toolbar class="topbar">
      <a class="brand" (click)="view.set('accounts')" aria-label="Baltic Payments home">
        <span class="brand-mark"><mat-icon>account_balance</mat-icon></span>
        <span>Baltic<span class="brand-light">Payments</span></span>
      </a>
      @if (auth.isLoggedIn()) {
        <nav class="main-nav" aria-label="Main navigation">
          <button mat-button [class.active]="view() === 'accounts'" (click)="show('accounts')">Accounts</button>
          <button mat-button [class.active]="view() === 'payment'" (click)="show('payment')">Payments</button>
          <button mat-button [class.active]="view() === 'transactions'" (click)="show('transactions')">Activity</button>
        </nav>
        <span class="grow"></span>
        <span class="identity"><span class="avatar">{{ auth.username().slice(0, 1).toUpperCase() }}</span><span>{{ auth.username() }}<small>{{ auth.role() }}</small></span></span>
        <button mat-icon-button aria-label="Sign out" title="Sign out" (click)="logout()"><mat-icon>logout</mat-icon></button>
      }
    </mat-toolbar>
    @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
    @if (!auth.isLoggedIn()) {
      <main class="login-layout">
        <section class="login-art">
          <div class="art-top"><span>PAYMENTS PLATFORM</span><span class="art-index">LT / 01</span></div>
          <div class="art-copy"><span class="eyebrow">BALTIC FINANCIAL NETWORK</span><h1>Money moves<br>with <em>purpose.</em></h1><p>A clear view of every account, transfer and transaction.</p></div>
          <div class="art-footer"><span>SECURE ACCESS</span><span>EST. 2025</span></div>
          <div class="art-grid"></div>
        </section>
        <section class="login-panel">
          <div class="login-box">
            <span class="eyebrow">WELCOME BACK</span><h2>Sign in to your account</h2><p class="subcopy">Use your assigned gateway credentials.</p>
            <form [formGroup]="loginForm" (ngSubmit)="login()">
              <mat-form-field appearance="outline"><mat-label>Username</mat-label><input matInput formControlName="username" autocomplete="username"><mat-icon matPrefix>person_outline</mat-icon></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Password</mat-label><input matInput type="password" formControlName="password" autocomplete="current-password"><mat-icon matPrefix>lock_outline</mat-icon></mat-form-field>
              @if (error()) { <p class="error-message" role="alert">{{ error() }}</p> }
              <button mat-flat-button color="primary" class="submit-button" [disabled]="loginForm.invalid || busy()">Continue <mat-icon>arrow_forward</mat-icon></button>
            </form>
            <div class="login-foot"><mat-icon>verified_user</mat-icon><span>Encrypted session · JWT authorization</span></div>
          </div>
        </section>
      </main>
    } @else {
      <main class="workspace">
        <header class="page-heading">
          <div><span class="eyebrow">PAYMENTS / {{ view() === 'accounts' ? 'OVERVIEW' : view() === 'payment' ? 'TRANSFER' : 'HISTORY' }}</span>
            <h1>{{ view() === 'accounts' ? 'Your accounts' : view() === 'payment' ? 'Make a payment' : 'Transaction activity' }}</h1>
            <p>{{ view() === 'accounts' ? 'Balances and account details at a glance.' : view() === 'payment' ? 'Transfer funds securely between Baltic accounts.' : 'Search and review posted payments.' }}</p>
          </div>
          @if (view() === 'accounts') { <button mat-flat-button color="primary" (click)="show('payment')"><mat-icon>north_east</mat-icon> New payment</button> }
        </header>
        @if (error()) { <div class="notice" role="alert"><mat-icon>error_outline</mat-icon>{{ error() }}<button mat-icon-button aria-label="Dismiss" (click)="error.set('')"><mat-icon>close</mat-icon></button></div> }
        @if (message()) { <div class="success-notice" role="status"><mat-icon>check_circle</mat-icon>{{ message() }}<button mat-icon-button aria-label="Dismiss" (click)="message.set('')"><mat-icon>close</mat-icon></button></div> }

        @if (view() === 'accounts') {
          <section class="summary-grid">
            <mat-card class="summary-card"><div class="summary-label">TOTAL ACCOUNTS</div><div class="summary-value">{{ accounts().length.toString().padStart(2, '0') }}</div><div class="summary-note">Available to your profile</div><mat-icon class="summary-icon">account_balance_wallet</mat-icon></mat-card>
            <mat-card class="summary-card balance-card"><div class="summary-label">TOTAL EUR BALANCE</div><div class="summary-value">{{ euroTotal() | number:'1.2-2' }} <small>EUR</small></div><div class="summary-note">Across your EUR accounts</div><mat-icon class="summary-icon">payments</mat-icon></mat-card>
            <mat-card class="summary-card"><div class="summary-label">ACCOUNT STATUS</div><div class="status-value"><span></span> Operational</div><div class="summary-note">All services available</div><mat-icon class="summary-icon">verified</mat-icon></mat-card>
          </section>
          <mat-card class="table-card"><div class="section-head"><div><h2>Account directory</h2><p>Accounts linked to your profile</p></div><span class="count-label">{{ accounts().length }} ACCOUNTS</span></div>
            <div class="table-scroll"><table mat-table [dataSource]="accounts()">
              <ng-container matColumnDef="account"><th mat-header-cell *matHeaderCellDef>ACCOUNT</th><td mat-cell *matCellDef="let account"><div class="account-cell"><span class="currency-mark">{{ account.currency }}</span><span><strong>{{ account.iban }}</strong><small>Personal account</small></span></div></td></ng-container>
              <ng-container matColumnDef="currency"><th mat-header-cell *matHeaderCellDef>CURRENCY</th><td mat-cell *matCellDef="let account"><span class="currency-pill">{{ account.currency }}</span></td></ng-container>
              <ng-container matColumnDef="balance"><th mat-header-cell *matHeaderCellDef class="align-right">AVAILABLE BALANCE</th><td mat-cell *matCellDef="let account" class="align-right balance-cell">{{ account.balance | number:'1.2-2' }} <span>{{ account.currency }}</span></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="accountColumns"></tr><tr mat-row *matRowDef="let row; columns: accountColumns"></tr>
            </table></div>
            @if (accounts().length === 0) { <div class="empty-state">No accounts are linked to this user.</div> }
          </mat-card>
        }

        @if (view() === 'payment') {
          <mat-card class="form-card"><div class="section-head"><div><h2>Transfer details</h2><p>Funds are transferred immediately after confirmation.</p></div><span class="secure-chip"><mat-icon>lock</mat-icon> SECURE TRANSFER</span></div>
            <form [formGroup]="paymentForm" (ngSubmit)="pay()" class="payment-form">
              <mat-form-field appearance="outline"><mat-label>Pay from</mat-label><mat-select formControlName="fromIban" (selectionChange)="updateCurrency($event.value)">@for (account of accounts(); track account.id) { <mat-option [value]="account.iban">{{ account.iban }} · {{ account.balance | number:'1.2-2' }} {{ account.currency }}</mat-option> }</mat-select></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Beneficiary IBAN</mat-label><input matInput formControlName="toIban" autocomplete="off">@if (paymentForm.controls.toIban.hasError('iban')) { <mat-error>Enter a valid IBAN</mat-error> }</mat-form-field>
              <div class="form-row"><mat-form-field appearance="outline"><mat-label>Amount</mat-label><input matInput type="number" min="0.01" step="0.01" formControlName="amount"><mat-error>Amount must be at least 0.01</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Currency</mat-label><input matInput [value]="selectedCurrency()" readonly></mat-form-field></div>
              <mat-form-field appearance="outline"><mat-label>Payment reference</mat-label><input matInput formControlName="reference" maxlength="140"><mat-hint align="end">{{ paymentForm.controls.reference.value.length }}/140</mat-hint></mat-form-field>
              <div class="payment-note"><mat-icon>info_outline</mat-icon><span>Transfers are final once submitted. Confirm the beneficiary IBAN before sending.</span></div>
              <button mat-flat-button color="primary" class="submit-button" type="submit" [disabled]="paymentForm.invalid || busy() || !accounts().length">Review and send <mat-icon>arrow_forward</mat-icon></button>
            </form>
          </mat-card>
        }

        @if (view() === 'transactions') {
          <mat-card class="table-card"><div class="section-head"><div><h2>Payment history</h2><p>Incoming and outgoing activity</p></div></div>
            <form [formGroup]="filterForm" (ngSubmit)="applyFilters()" class="filter-row">
              <mat-form-field appearance="outline" class="iban-filter"><mat-label>Filter by IBAN</mat-label><input matInput formControlName="iban"></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>Status</mat-label><mat-select formControlName="status"><mat-option value="">Any status</mat-option><mat-option value="COMPLETED">Completed</mat-option></mat-select></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>From date</mat-label><input matInput type="date" formControlName="dateFrom"></mat-form-field>
              <mat-form-field appearance="outline"><mat-label>To date</mat-label><input matInput type="date" formControlName="dateTo"></mat-form-field>
              <button mat-stroked-button type="submit"><mat-icon>filter_alt</mat-icon> Filter</button>
            </form>
            <div class="table-scroll"><table mat-table [dataSource]="transactions()">
              <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>DATE</th><td mat-cell *matCellDef="let tx">{{ tx.createdAt | date:'yyyy-MM-dd HH:mm' }}</td></ng-container>
              <ng-container matColumnDef="direction"><th mat-header-cell *matHeaderCellDef>FROM / TO</th><td mat-cell *matCellDef="let tx"><span class="tx-iban">{{ tx.fromIban }}</span><span class="tx-arrow">→</span><span class="tx-iban">{{ tx.toIban }}</span></td></ng-container>
              <ng-container matColumnDef="reference"><th mat-header-cell *matHeaderCellDef>REFERENCE</th><td mat-cell *matCellDef="let tx">{{ tx.reference || '—' }}</td></ng-container>
              <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="align-right">AMOUNT</th><td mat-cell *matCellDef="let tx" class="align-right balance-cell">{{ tx.amount | number:'1.2-2' }} <span>{{ tx.currency }}</span></td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>STATUS</th><td mat-cell *matCellDef="let tx"><span class="completed-pill">{{ tx.status }}</span></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="transactionColumns"></tr><tr mat-row *matRowDef="let row; columns: transactionColumns"></tr>
            </table></div>
            @if (!transactions().length && !busy()) { <div class="empty-state">No payments match these filters.</div> }
            <mat-paginator [length]="totalTransactions()" [pageSize]="pageSize()" [pageIndex]="page()" [pageSizeOptions]="[5, 10, 25]" (page)="changePage($event)" />
          </mat-card>
        }
        <footer class="workspace-footer"><span>MINI-GATEWAY · SECURE PAYMENTS</span><span>SESSION ENCRYPTED <mat-icon>lock</mat-icon></span></footer>
      </main>
    }
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly view = signal<View>('accounts');
  readonly accounts = signal<Account[]>([]);
  readonly transactions = signal<Transaction[]>([]);
  readonly totalTransactions = signal(0);
  readonly page = signal(0);
  readonly pageSize = signal(10);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly selectedCurrency = signal('EUR');
  readonly euroTotal = computed(() => this.accounts().filter(account => account.currency === 'EUR').reduce((sum, account) => sum + Number(account.balance), 0));
  readonly accountColumns = ['account', 'currency', 'balance'];
  readonly transactionColumns = ['date', 'direction', 'reference', 'amount', 'status'];
  readonly loginForm = this.fb.nonNullable.group({ username: ['', Validators.required], password: ['', Validators.required] });
  readonly paymentForm = this.fb.nonNullable.group({ fromIban: ['', Validators.required], toIban: ['', [Validators.required, ibanValidator]], amount: [0, [Validators.required, Validators.min(0.01)]], reference: ['', Validators.maxLength(140)] });
  readonly filterForm = this.fb.nonNullable.group({ iban: [''], status: [''], dateFrom: [''], dateTo: [''] });
  private idempotencyKey = uuid();

  constructor() { if (this.auth.isLoggedIn()) this.loadAccounts(); }
  login(): void {
    if (this.loginForm.invalid) return;
    this.busy.set(true); this.error.set('');
    const { username, password } = this.loginForm.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => { this.busy.set(false); this.show('accounts'); },
      error: () => { this.error.set('Login failed. Check your credentials and try again.'); this.busy.set(false); },
    });
  }
  logout(): void { this.auth.logout(); this.accounts.set([]); this.transactions.set([]); this.error.set(''); this.view.set('accounts'); }
  show(view: View): void {
    this.view.set(view); this.error.set(''); this.message.set('');
    if (view === 'accounts' || view === 'payment') this.loadAccounts();
    if (view === 'transactions') { this.page.set(0); this.loadTransactions(); }
  }
  updateCurrency(iban: string): void { this.selectedCurrency.set(this.accounts().find(account => account.iban === iban)?.currency ?? 'EUR'); }
  pay(): void {
    if (this.paymentForm.invalid) return;
    const value = this.paymentForm.getRawValue();
    this.busy.set(true); this.error.set('');
    this.api.pay({ ...value, toIban: value.toIban.replace(/\s/g, '').toUpperCase(), currency: this.selectedCurrency() }, this.idempotencyKey).subscribe({
      next: () => {
        this.message.set('Payment completed successfully.'); this.idempotencyKey = uuid();
        this.paymentForm.patchValue({ toIban: '', amount: 0, reference: '' }); this.paymentForm.markAsPristine();
        this.loadAccounts(); this.busy.set(false);
      },
      error: (failure: { status: number; error?: { detail?: string } }) => {
        this.error.set(failure.error?.detail ?? 'Payment could not be completed.');
        if (failure.status !== 0 && failure.status < 500) this.idempotencyKey = uuid();
        this.busy.set(false);
      },
    });
  }
  applyFilters(): void { this.page.set(0); this.loadTransactions(); }
  changePage(event: PageEvent): void { this.page.set(event.pageIndex); this.pageSize.set(event.pageSize); this.loadTransactions(); }
  private loadAccounts(): void {
    this.api.accounts().subscribe({ next: rows => this.accounts.set(rows), error: () => this.error.set('Could not load accounts.') });
  }
  private loadTransactions(): void {
    this.busy.set(true);
    this.api.transactions({ ...this.filterForm.getRawValue(), page: this.page(), size: this.pageSize() }).subscribe({
      next: result => { this.transactions.set(result.content); this.totalTransactions.set(result.totalElements); this.busy.set(false); },
      error: () => { this.error.set('Could not load transaction history.'); this.busy.set(false); },
    });
  }
}
