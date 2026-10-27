import { Component, input, output } from '@angular/core';

/**
 * Presentational component: lets the user pick a file and reports it upwards.
 * It knows nothing about HTTP. Data comes in through input(), events go out through output().
 */
@Component({
  selector: 'app-document-upload',
  templateUrl: './document-upload.html',
  styleUrls: ['./styles/document-upload.css'],
})
export class DocumentUpload {
  readonly disabled = input(false);
  readonly fileSelected = output<File>();

  protected onFileChange(event: Event): void {
    const fileInput = event.target as HTMLInputElement;
    const file = fileInput.files?.[0];
    if (!file) {
      return;
    }

    this.fileSelected.emit(file);

    // Without this, selecting the same file again would not fire a change event.
    fileInput.value = '';
  }
}
