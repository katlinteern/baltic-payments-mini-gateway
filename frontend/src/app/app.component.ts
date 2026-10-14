import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { AuthService } from './core/auth.service';
import { clearAccounts } from './features/accounts/state/accounts.actions';
import { clearLogin } from './features/login/state/login.actions';
import { clearPayments } from './features/payment/state/payments.actions';
import { clearTransactions } from './features/transactions/state/transactions.actions';

@Component({
	selector: 'app-root',
	standalone: true,
	imports: [MatButtonModule, MatIconModule, MatToolbarModule, RouterLink, RouterLinkActive, RouterOutlet],
	templateUrl: './app.component.html',
	styleUrl: './app.component.scss',
})
export class AppComponent {
	readonly auth = inject(AuthService);
	private readonly store = inject(Store);
	private readonly router = inject(Router);

	logout(): void {
		this.auth.logout();
		this.store.dispatch(clearAccounts());
		this.store.dispatch(clearLogin());
		this.store.dispatch(clearPayments());
		this.store.dispatch(clearTransactions());
		void this.router.navigate(['/login']);
	}
}
