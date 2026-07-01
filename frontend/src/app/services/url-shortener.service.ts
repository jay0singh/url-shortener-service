import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UrlRequest {
  url: string;
  ttlDays?: number;
}

export interface UrlResponse {
  shortUrl: string;
  expiresAt?: string;
}

export interface UrlStats {
  shortCode: string;
  longUrl: string;
  hitCount: number;
  createdAt?: string;
  lastAccessedAt?: string;
  expiresAt?: string;
}

const API_BASE_URL = environment.apiUrl;

@Injectable({
  providedIn: 'root'
})
export class UrlShortenerService {

  constructor(private http: HttpClient) { }

  shorten(request: UrlRequest): Observable<UrlResponse> {
    return this.http.post<UrlResponse>(`${API_BASE_URL}/shorten`, request);
  }

  deactivate(shortCode: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/shorten/${shortCode}`);
  }

  getStats(shortCode: string): Observable<UrlStats> {
    return this.http.get<UrlStats>(`${API_BASE_URL}/stats/${shortCode}`);
  }
}
