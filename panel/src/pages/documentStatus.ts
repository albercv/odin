export const STATUS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  RECEIVED: ['Recibido', 'pending'],
  PROCESSING: ['Procesando', 'pending'],
  READY: ['Listo', 'ok'],
  FAILED: ['Fallido', 'off'],
  PENDING_READER: ['Esperando al lector OCR', 'pending'],
  READER_PROCESSING: ['Leyendo con OCR', 'pending'],
};

export const NO_READER_LABEL = 'Esta instalación no lee documentos escaneados';

/** Status words on the case screen: a photo waiting for the OCR reader never disappears silently. */
export const CASE_STATUS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  ...STATUS,
  PENDING_READER: ['Espera al lector OCR', 'pending'],
  READER_PROCESSING: ['Espera al lector OCR', 'pending'],
  RECEIVED: ['En lectura', 'pending'],
  PROCESSING: ['En lectura', 'pending'],
  FAILED: ['No se pudo leer', 'off'],
};
