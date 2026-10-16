import { Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { LoginEffects } from './state/login.effects';
import { loginReducer } from './state/login.reducer';
import { LoginComponent } from './pages/login.component';

export const LOGIN_ROUTES: Routes = [
  {
    path: '',
    component: LoginComponent,
    providers: [provideState('login', loginReducer), provideEffects(LoginEffects)],
  },
];
