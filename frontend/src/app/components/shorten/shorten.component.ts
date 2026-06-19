import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { UrlShortenerService } from '../../services/url-shortener.service';

@Component({
  selector: 'app-shorten',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shorten.component.html',
  styleUrl: './shorten.component.scss'
})
export class ShortenComponent {
  url = '';
  ttlDays: number | null = null;

  shortUrl: string | null = null;
  expiresAt: string | null = null;
  errorMessage: string | null = null;
  loading = false;

  constructor(private urlShortenerService: UrlShortenerService) { }

  onSubmit(): void {
    this.errorMessage = null;
    this.shortUrl = null;
    this.expiresAt = null;
    this.loading = true;

    this.urlShortenerService
      .shorten({ url: this.url, ttlDays: this.ttlDays ?? undefined })
      .subscribe({
        next: (response) => {
          this.shortUrl = response.shortUrl;
          this.expiresAt = response.expiresAt ?? null;
          this.loading = false;
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage = err.error?.message ?? 'Something went wrong. Please try again.';
          this.loading = false;
        }
      });
  }
}
