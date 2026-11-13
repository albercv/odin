import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { ApiClient } from './api/client.js';
import { createProjectNames } from './projects.js';
import { registerCaseFileTools } from './tools/caseFile.js';
import { registerFindSimilarCases } from './tools/findSimilarCases.js';
import { registerListDocuments } from './tools/listDocuments.js';
import { registerListMyKnowledgeCards } from './tools/listMyKnowledgeCards.js';
import { registerListMyQuestions } from './tools/listMyQuestions.js';
import { registerListProjects } from './tools/listProjects.js';
import { registerOpenOriginal } from './tools/openOriginal.js';
import { registerReadPage } from './tools/readPage.js';
import { registerSaveKnowledgeCard } from './tools/saveKnowledgeCard.js';
import { registerSearchKnowledge } from './tools/searchKnowledge.js';
import { registerVerifyCitation } from './tools/verifyCitation.js';

export const INSTRUCTIONS = [
  'Odín es el cerebro digital de la empresa del trabajador.',
  'Cita el documento y la página de toda afirmación sobre el trabajo de la empresa.',
  'Si no hay resultados, di «no consta»; no lo inventes.',
  'Si la pregunta nombra un proyecto (una comunidad, un cliente, una obra), búscalo con listProjects y filtra la búsqueda con su projectId.',
  'Comprueba las citas con verifyCitation antes de darlas por buenas.',
  'Los resultados KNOWLEDGE_CARD son fichas aportadas por trabajadores redactadas con IA: preséntalas con su título, autor y fecha de alta, como fichas y no como documentos, y no tienen página; verifyCitation es solo para documentos, y si verifyCitation devuelve false, no uses esa cita.',
  'Copia cada fragmento literal por separado; no unas trozos con puntos suspensivos.',
  'De una ficha, cita solo su texto, sin el título, el autor ni la fecha.',
  'Afirma solo lo que dicen las citas. Si la fuente es parcial, porque falta el documento que lo regula o el que tienes no lo dice todo, dilo y no completes la conclusión.',
  'Si un resultado viene marcado como sustituido, no lo presentes como vigente: di que está sustituido y por cuál, y busca o lee el que lo sustituye antes de responder. Si no puedes ver el que lo sustituye, di solo que está sustituido.',
  'Si dos fuentes no cuadran y ninguna está marcada como sustituida: si una dice expresamente que cambia a la otra (un anexo que modifica una cláusula), o es un acuerdo posterior del órgano que decide (la junta, en una comunidad) sobre lo mismo, da lo que vale ahora, di qué documento lo cambia y cita las dos; si el acuerdo no cita la norma que cambia, dilo, y no juzgues si es válido. Que una sea más reciente no basta. Si no se da ninguno de esos casos, enseña las dos, cada una con su cita y su fecha, y no elijas ninguna. La fecha es la que conste en el texto del documento; si no consta, da la fecha en que entró en Odín y di que es esa.',
  'Si readerName es unlimited-ocr-2, la página es un escaneo leído por OCR y puede tener errores: cítala tal cual.',
  'Una página con el texto vacío no se ha leído: no concluyas nada de ella.',
  'Presenta el conocimiento general aparte, como información añadida, nunca mezclado con lo que consta en los documentos.',
].join(' ');

export async function buildServer(api: ApiClient): Promise<McpServer> {
  const me = await api.me();
  const capabilities = new Set(me.company.capabilities);
  const server = new McpServer({ name: 'odin', version: '0.1.0' }, { instructions: INSTRUCTIONS });
  if (capabilities.has('SEARCH_KNOWLEDGE')) {
    const projects = createProjectNames(me);
    registerSearchKnowledge(server, api, projects);
    registerReadPage(server, api);
    registerOpenOriginal(server, api);
    registerListDocuments(server, api, projects);
    registerListProjects(server, api);
    registerVerifyCitation(server, api);
    registerListMyQuestions(server, api);
    if (capabilities.has('SIMILAR_CASES')) registerFindSimilarCases(server, api, projects);
  }
  if (capabilities.has('CASE_FILES')) registerCaseFileTools(server, api);
  if (capabilities.has('SAVE_KNOWLEDGE_CARD')) {
    registerSaveKnowledgeCard(server, api);
    registerListMyKnowledgeCards(server, api, createProjectNames(me));
  }
  return server;
}
