import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'documents' },
  {
    path: 'documents',
    // Lazy-loaded: the page's code is only fetched when the route is opened.
    loadComponent: () => import('./documents/documents-page').then((m) => m.DocumentsPage),
  },
  {
    path: 'chat',
    loadComponent: () => import('./chat/chat-page').then((m) => m.ChatPage),
  },
];
