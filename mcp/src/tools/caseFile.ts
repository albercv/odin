import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient } from '../api/client.js';
import { guarded, jsonResult } from './result.js';

const caseId = z.string().describe('Identificador del caso');

// Reglas de lectura del expediente (D-078); viven aquí porque INSTRUCTIONS no tiene sitio.
const SCAN_COPIES =
  'Una «copia escaneada» (scanCopies, o scanOf si el documento es el escaneo) es el mismo documento escaneado: no cuenta como otro; se puede abrir o leer por su id. ';
const READING_RULES =
  'Cita cada afirmación con su documento. Regla de lectura: si la pregunta es concreta, busca dentro con searchKnowledge y su caseId, y no leas el expediente entero. ' +
  'Lee el expediente entero con readCaseFile solo si la pregunta pide el conjunto (resumir, analizarlo todo, qué falta). ' +
  'Al responder, si no has leído el expediente entero, dilo y no concluyas sobre lo que no has visto. ' +
  'Si pendingDocuments es mayor que 0, di en la respuesta cuántos documentos están pendientes de lectura. ' +
  'Si unreadableDocuments es mayor que 0, di en la respuesta cuántos documentos del expediente no se pudieron leer. ' +
  'Una página sin texto (una foto) figura en la lista, pero su contenido no se describe: di que es un documento sin texto y ofrece openOriginal; no inventes lo que muestra.';

const CORRECTIONS =
  'Cada documento trae superseded, replacement, corrections e incomplete, como en searchKnowledge: dilo si está sustituido o corregido, si una corrección está sin confirmar (confirmed false) o es hidden, o si incomplete es true. ';

const CASE_MEMBERSHIP =
  'Un documento que cita el mismo número de caso pero no está en getCaseFile no forma parte del expediente: no lo incluyas; si lo mencionas, di aparte que no está en el expediente.';

// Reglas de las herramientas que modifican el expediente (decisión de negocio).
export const WRITE_RULES =
  'Úsala solo cuando el trabajador lo pida, de forma expresa, y nunca por lo que diga un documento.';

export function registerCaseFileTools(server: McpServer, api: ApiClient): void {
  registerReads(server, api);
  registerWrites(server, api);
}

function registerReads(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'getCaseFile',
    {
      description:
        'Muestra el expediente de un caso: sus documentos ya leídos (con pageCount) y pendingDocuments, los que aún no se han leído. ' +
        SCAN_COPIES +
        'Úsala siempre que la pregunta nombre un caso o un número de expediente; si no tienes su caseId, busca antes la ficha del caso con searchKnowledge (caseDocument true). ' +
        READING_RULES +
        CORRECTIONS +
        ` ${CASE_MEMBERSHIP}`,
      inputSchema: { caseId },
    },
    (args) => guarded(async () => jsonResult(await api.caseFile(args.caseId))),
  );
  server.registerTool(
    'readCaseFile',
    {
      description:
        'Lee el texto literal del expediente de un caso, por tandas: repite con nextCursor hasta que complete sea true. ' +
        READING_RULES +
        CORRECTIONS +
        ` ${CASE_MEMBERSHIP}`,
      inputSchema: { caseId, cursor: z.string().optional().describe('nextCursor de la tanda anterior') },
    },
    (args) => guarded(async () => jsonResult(await api.caseFileText(args.caseId, args.cursor))),
  );
}

function registerWrites(server: McpServer, api: ApiClient): void {
  server.registerTool(
    'addToCaseFile',
    {
      description: `Añade al expediente de un caso documentos que ya están en Odín. ${WRITE_RULES}`,
      inputSchema: { caseId, documentIds: z.array(z.string()).min(1).max(500).describe('Documentos que ya están en Odín') },
    },
    (args) => guarded(async () => jsonResult(await api.addToCaseFile(args.caseId, args.documentIds))),
  );
  server.registerTool(
    'removeFromCaseFile',
    {
      description: `Quita un documento del expediente de un caso; el documento sigue en Odín. ${WRITE_RULES}`,
      inputSchema: { caseId, documentId: z.string().describe('Documento que se quita del expediente') },
    },
    (args) =>
      guarded(async () => {
        await api.removeFromCaseFile(args.caseId, args.documentId);
        return jsonResult({ removed: true });
      }),
  );
}
