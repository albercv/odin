import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { ApiClient, Hit } from '../api/client.js';
import type { ProjectNames } from '../projects.js';
import { NO_SOURCE_MESSAGE, guarded, jsonResult, textResult } from './result.js';

const DESCRIPTION =
  'Busca en los documentos y fichas aprobadas de la empresa. Los documentos traen documentId, página y extracto para citar; ' +
  'las fichas (KNOWLEDGE_CARD) traen cardId, autor, fecha de alta y su origen, y no tienen página. ' +
  'projectName es el proyecto: una de las unidades con las que la empresa organiza su trabajo en Odín; «Sin proyecto (general de la empresa)» si no es de ningún proyecto y lo ve toda la empresa. ' +
  'addedAt es la fecha en que entró en Odín, no la del documento. ' +
  'superseded indica que un documento está sustituido; replacement es el documento vigente que lo sustituye (null si no puedes verlo). ' +
  'Una ficha con reviewStatus PENDING es tuya y está pendiente de revisión: dilo al citarla y recuerda que tus compañeros todavía no la ven. ' +
  'Cada consulta, una idea, en 1 a 3 palabras específicas. ' +
  'Si una búsqueda no trae lo que buscas, repite en otra llamada con una sola consulta corta y específica: las consultas de una misma llamada se suman y una genérica puede tapar a la buena. ' +
  'Si el documento da una fecha o un plazo concreto, dalo con su cita y no te abstengas. ' +
  'Si una ficha dice algo distinto que un documento, cita las dos. ' +
  'title es el nombre del documento, no texto de la página: no lo cites; cita el extracto o el texto de readPage.';

// getCaseFile solo existe con la capacidad CASE_FILES: sin ella la pista llevaría a una herramienta que no está.
const CASE_FILE_HINT =
  ' caseDocument true marca el documento ficha de un caso: si la pregunta trata de un caso o de un expediente, llama a getCaseFile con ese documentId como caseId antes de responder.';

export function registerSearchKnowledge(server: McpServer, api: ApiClient, projects: ProjectNames, caseFiles: boolean): void {
  server.registerTool(
    'searchKnowledge',
    {
      description: caseFiles ? DESCRIPTION + CASE_FILE_HINT : DESCRIPTION,
      inputSchema: {
        queries: z.array(z.string()).min(1).describe('Consultas de búsqueda, con palabras clave'),
        projectId: z.string().optional().describe('Limita la búsqueda a un proyecto; también incluye lo que no tiene proyecto'),
        caseId: z.string().optional().describe('Busca solo dentro de los documentos del expediente de este caso'),
        limit: z.number().int().positive().optional().describe('Máximo de resultados'),
      },
    },
    (args) =>
      guarded(async () => {
        const result = await api.search(args);
        if (result.noSource || !result.hits.length) return textResult(NO_SOURCE_MESSAGE);
        await projects.refreshFor(result.hits.map((hit) => hit.projectId));
        return jsonResult({ hits: result.hits.map((hit) => toCitation(hit, projects)) });
      }),
  );
}

// Una ficha nunca se presenta como documento: lleva cardId y su marca de origen (spec 6.8, D-024).
const toCitation = (hit: Hit, projects: ProjectNames) =>
  hit.sourceType === 'KNOWLEDGE_CARD'
    ? {
        sourceType: hit.sourceType,
        cardId: hit.sourceId,
        title: hit.title,
        authorName: hit.authorName,
        addedAt: hit.date,
        projectId: hit.projectId ?? null,
        projectName: projects.nameOf(hit.projectId),
        excerpt: hit.excerpt,
        origin: `Ficha aportada por ${hit.authorName}, redactada con IA`,
        ...(hit.reviewStatus ? { reviewStatus: hit.reviewStatus } : {}),
      }
    : {
        sourceType: hit.sourceType,
        documentId: hit.sourceId,
        title: hit.title,
        pageNumber: hit.pageNumber ?? null,
        paginated: hit.paginated,
        caseDocument: hit.caseDocument,
        projectId: hit.projectId ?? null,
        projectName: projects.nameOf(hit.projectId),
        authorName: hit.authorName,
        addedAt: hit.date,
        superseded: hit.superseded,
        replacement: hit.replacement ?? null,
        excerpt: hit.excerpt,
      };
