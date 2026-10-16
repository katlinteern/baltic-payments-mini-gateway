import { Component, computed, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AuthService } from '@core/auth.service';
import { AlertComponent, EmptyStateComponent, PageComponent, SectionHeadComponent } from '@shared/ui';
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
    AlertComponent,
    DecimalPipe,
    EmptyStateComponent,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    PageComponent,
    RouterLink,
    SectionHeadComponent,
  ],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly auth = inject(AuthService);
  readonly accounts = this.store.selectSignal(selectAccounts);
  readonly euroTotal = this.store.selectSignal(selectEuroTotal);
  readonly loading = this.store.selectSignal(selectAccountsLoading);
  readonly error = this.store.selectSignal(selectAccountsError);
  readonly columns = ['account', 'holder', 'balance'];
  readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const part = hour >= 5 && hour < 12 ? 'Good morning' : hour >= 12 && hour < 18 ? 'Good afternoon' : 'Good evening';
    const name = this.auth.username();
    return name ? `${part}, ${name.charAt(0).toUpperCase()}${name.slice(1)}` : part;
  });

  ngOnInit(): void {
    this.store.dispatch(loadAccounts());
  }
}
