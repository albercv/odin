import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import { ApiError } from '../api/client.js';

export const NO_SOURCE_MESSAGE = 'No consta en los documentos de tu empresa.';

export const textResult = (text: string): CallToolResult => ({ content: [{ type: 'text', text }] });

// consultedAt: el reloj del servidor MCP al responder (el cliente HTTP no expone la cabecera Date de la API).
export const withConsultedAt = (result: CallToolResult, at: Date): CallToolResult => ({
  ...result,
  content: [...result.content, { type: 'text', text: JSON.stringify({ consultedAt: at.toISOString() }) }],
});

export const jsonResult = (value: unknown): CallToolResult => textResult(JSON.stringify(value, null, 2));

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Error inesperado en el servidor MCP de Odín.';
  switch (error.status) {
    case 0:
      return error.detail;
    case 401:
      return 'La clave de acceso no es válida o ha sido revocada. Pide una nueva al administrador de tu empresa.';
    case 403:
      return error.detail || 'No tienes permiso para esta operación.';
    case 404:
      return error.detail || 'No se ha encontrado lo que pides, o no pertenece a tu empresa.';
    case 400:
      return error.detail || 'La petición no es válida.';
    default:
      return `Odín no ha podido atender la petición (error ${error.status}).`;
  }
}

export async function guarded(action: () => Promise<CallToolResult>): Promise<CallToolResult> {
  try {
    return await action();
  } catch (error) {
    return { content: [{ type: 'text', text: errorMessage(error) }], isError: true };
  }
}

// Regla común de las herramientas que leen datos de Odín (D-126, criterio 13).
export const FRESH_READ =
  'Los datos de Odín no se responden de memoria: toda respuesta sobre el estado o el contenido de un documento sale de una llamada hecha para esa respuesta, aunque la conversación ya lo haya leído; lo leído antes son solo pistas, porque un compañero pudo cambiar los datos; consultedAt dice cuándo se leyó cada resultado.';

export const FRESH_ABSENCE =
  'Antes de afirmar una ausencia o un estado general («no hay otra póliza vigente», «ninguna está sustituida», «no consta»), haz una consulta nueva en esa respuesta, aunque la conversación ya haya leído algo parecido.';
