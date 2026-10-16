import { Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { AuthService } from '@core/auth.service';
import { sessionEnded } from '@core/session';
import { LogoComponent } from '@shared/ui';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [LogoComponent, MatIconModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.store.dispatch(sessionEnded());
    void this.router.navigate(['/login']);
  }
}
