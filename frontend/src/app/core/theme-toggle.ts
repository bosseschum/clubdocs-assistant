import { Component, Renderer2, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  template: `
    <button (click)="toggleTheme()">
      {{ isDarkMode ? '☀️' : '🌙' }}
    </button>
  `,
  styles: [
    `
      button {
        padding: 8px 16px;
        cursor: pointer;
        border-radius: 6px;
        border: 1px solid var(--color-border);
        background-color: var(--color-surface);
        color: var(--color-text-main);
      }
    `,
  ],
})
export class ThemeToggleComponent {
  isDarkMode = false;

  constructor(
    private renderer: Renderer2,
    @Inject(DOCUMENT) private document: Document,
  ) {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.isDarkMode = prefersDark;
    this.updateTheme();
  }

  toggleTheme() {
    this.isDarkMode = !this.isDarkMode;
    this.updateTheme();
  }

  private updateTheme() {
    const htmlTag = this.document.documentElement; // Holt das <html> Element

    if (this.isDarkMode) {
      this.renderer.addClass(htmlTag, 'dark');
      this.renderer.removeClass(htmlTag, 'light');
    } else {
      this.renderer.addClass(htmlTag, 'light');
      this.renderer.removeClass(htmlTag, 'dark');
    }
  }
}
