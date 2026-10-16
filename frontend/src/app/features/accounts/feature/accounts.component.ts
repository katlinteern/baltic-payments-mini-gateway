import { Component, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { loadAccounts } from '../state/accounts.actions';
import {
  selectAccounts,
  selectAccountsError,
  selectAccountsLoading,
  selectEuroTotal,
} from '../state/accounts.selectors';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly store = inject(Store);
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly euroTotal = this.store.selectSignal(selectEuroTotal);
  readonly loading = this.store.selectSignal(selectAccountsLoading);
  readonly error = this.store.selectSignal(selectAccountsError);
  readonly columns = ['account', 'currency', 'balance'];

  ngOnInit(): void {
    this.store.dispatch(loadAccounts());
  }
}
