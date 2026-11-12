export const STATUS: Record<string, [string, 'ok' | 'off' | 'pending']> = {
  RECEIVED: ['Recibido', 'pending'],
  PROCESSING: ['Procesando', 'pending'],
  READY: ['Listo', 'ok'],
  FAILED: ['Fallido', 'off'],
  PENDING_READER: ['Pendiente de lector', 'pending'],
};
