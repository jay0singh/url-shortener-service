import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { UrlShortenerService, UrlStats } from '../../services/url-shortener.service';

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './stats.component.html',
  styleUrl: './stats.component.scss'
})
export class StatsComponent {
  shortCodeInput = '';

  stats: UrlStats | null = null;
  errorMessage: string | null = null;
  loading = false;

  constructor(private urlShortenerService: UrlShortenerService) { }

  onSubmit(): void {
    this.errorMessage = null;
    this.stats = null;
    this.loading = true;

    const shortCode = this.extractShortCode(this.shortCodeInput);

    this.urlShortenerService.getStats(shortCode).subscribe({
      next: (stats) => {
        this.stats = stats;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage = err.error?.message ?? 'Something went wrong. Please try again.';
        this.loading = false;
      }
    });
  }

  private extractShortCode(input: string): string {
    const trimmed = input.trim();
    const segments = trimmed.split('/');
    return segments[segments.length - 1];
  }
}
