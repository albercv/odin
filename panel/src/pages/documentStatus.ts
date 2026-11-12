export const STATUS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  RECEIVED: ['Recibido', 'pending'],
  PROCESSING: ['Procesando', 'pending'],
  READY: ['Listo', 'ok'],
  FAILED: ['Fallido', 'off'],
  PENDING_READER: ['Esperando al lector OCR', 'pending'],
  READER_PROCESSING: ['Leyendo con OCR', 'pending'],
};

export const NO_READER_LABEL = 'Esta instalación no lee documentos escaneados';
