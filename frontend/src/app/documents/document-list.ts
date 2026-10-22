import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';

import { DocumentResponse } from '../core/document.model';
import { formatSize } from './format-size';

/**
 * Presentational component: renders documents and reports delete clicks upwards.
 * It does not delete anything itself and does not know the API exists.
 */
@Component({
  selector: 'app-document-list',
  imports: [DatePipe],
  templateUrl: './document-list.html',
})
export class DocumentList {
  readonly documents = input.required<DocumentResponse[]>();
  readonly deleteRequested = output<string>();

  // Makes the plain function available in the template.
  protected readonly formatSize = formatSize;
}
