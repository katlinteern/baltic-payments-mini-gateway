import { Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface NameMismatchDialogData {
  beneficiaryName: string;
  toIban: string;
  amount: number;
  currency: string;
}

/** Closes with `true` when the payer chooses to send the payment despite the name mismatch. */
@Component({
  selector: 'app-name-mismatch-dialog',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Beneficiary name does not match</h2>
    <mat-dialog-content>
      <p>
        The name <strong>{{ data.beneficiaryName }}</strong> does not match the holder of account
        <strong>{{ data.toIban }}</strong
        >. If you send {{ data.amount | number: '1.2-2' }} {{ data.currency }} anyway it may go to the wrong person and
        may not be recoverable.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false" cdkFocusInitial>Edit details</button>
      <button mat-flat-button type="button" class="risky" [mat-dialog-close]="true">Send anyway</button>
    </mat-dialog-actions>
  `,
  styles: `
    .risky {
      --mat-button-filled-container-color: var(--danger);
    }
  `,
})
export class NameMismatchDialogComponent {
  readonly data = inject<NameMismatchDialogData>(MAT_DIALOG_DATA);
}
