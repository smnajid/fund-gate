import { Routes } from '@angular/router';
import { DealerScreen } from './dealer-screen/dealer-screen';
import { ValidationScreen } from './validation-screen/validation-screen';

export const routes: Routes = [
  { path: '', component: DealerScreen, title: 'FundEli5' },
  { path: 'validation', component: ValidationScreen, title: 'FundEli5 · Rule Card validation' }
];
