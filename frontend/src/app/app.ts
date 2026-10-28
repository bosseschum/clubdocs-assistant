import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeToggleComponent } from './core/theme-toggle'; // Pfad ggf. anpassen

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ThemeToggleComponent], // <-- Hier importiert
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');
}
