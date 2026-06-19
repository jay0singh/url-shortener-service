import { Routes } from '@angular/router';

import { ShortenComponent } from './components/shorten/shorten.component';
import { StatsComponent } from './components/stats/stats.component';

export const routes: Routes = [
  { path: '', component: ShortenComponent },
  { path: 'stats', component: StatsComponent },
  { path: '**', redirectTo: '' }
];
