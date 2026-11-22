import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { CallToolResult } from '@modelcontextprotocol/sdk/types.js';
import type { ApiClient } from './api/client.js';
import { createProjectNames } from './projects.js';
import { DEFAULT_TERMS, projectTerms, type ProjectTerms } from './terms.js';
import { withConsultedAt } from './tools/result.js';
import { registerCaseFileTools } from './tools/caseFile.js';
import { registerFindSimilarCases } from './tools/findSimilarCases.js';
import { registerListActivity } from './tools/listActivity.js';
import { registerListDocuments } from './tools/listDocuments.js';
import { registerListMyKnowledgeCards } from './tools/listMyKnowledgeCards.js';
import { registerListMyQuestions } from './tools/listMyQuestions.js';
import { registerListProjects } from './tools/listProjects.js';
import { registerUploadLink } from './tools/uploadLink.js';
import { registerOpenOriginal } from './tools/openOriginal.js';
import { registerReadPage } from './tools/readPage.js';
import { registerSaveKnowledgeCard } from './tools/saveKnowledgeCard.js';
import { registerSearchKnowledge } from './tools/searchKnowledge.js';
import { registerVerifyCitation } from './tools/verifyCitation.js';

const instructionLines = (t: ProjectTerms): string[] => [
  'Odín es el cerebro digital de la empresa del trabajador.',
  'Cita el documento y la página de toda afirmación sobre el trabajo de la empresa.',
  'Si no hay resultados, di «no consta»; no lo inventes.',
  `Si la pregunta nombra ${t.one} (el nombre de un cliente, un contrato, una obra…), búscalo con listProjects y filtra la búsqueda con su projectId.`,
  'Comprueba las citas con verifyCitation antes de darlas por buenas.',
  'Los resultados KNOWLEDGE_CARD son fichas aportadas por trabajadores redactadas con IA: preséntalas con su título, autor y fecha de alta, como fichas y no como documentos, y no tienen página; verifyCitation es solo para documentos, y si verifyCitation devuelve false, no uses esa cita.',
  'Afirma solo lo que dicen las citas. Si la fuente es parcial, porque falta el documento que lo regula o el que tienes no lo dice todo, dilo y no completes la conclusión.',
  'Responde primero lo preguntado y después el contexto. Si un resultado trae versions o corrections, da el valor vigente con su documento, página y la fecha que conste en el texto, y luego los anteriores en orden, cada uno con su cita; lo sustituido nunca es vigente: di por cuál, y búscalo o léelo antes de responder. Si una corrección no está confirmada, dilo; si no la ves o incomplete es true, di que falta un eslabón y no lo rellenes. Compara dos documentos solo tras leer los dos, sin señalar diferencias que no hayas comprobado.',
  'Si dos fuentes no cuadran y ninguna está marcada como sustituida: si una dice expresamente que cambia a la otra (un anexo que modifica una cláusula), o es un acuerdo posterior del órgano que decide (la junta o el consejo) sobre lo mismo, da lo que vale ahora, di qué documento lo cambia y cita las dos; si el acuerdo no cita la norma que cambia, dilo, y no juzgues si es válido. Que una sea más reciente no basta. Si no se da ninguno de esos casos, enseña las dos, cada una con su cita y su fecha, y no elijas ninguna.',
  'Presenta el conocimiento general aparte, como información añadida, nunca mezclado con lo que consta en los documentos.',
];

// Con la palabra de siempre no se añade nada; con otra, se dice cuál usar con el trabajador.
export function instructions(t: ProjectTerms): string {
  const lines = instructionLines(t);
  if (t.plural !== DEFAULT_TERMS.plural) lines.splice(5, 0, `En esta empresa, los proyectos se llaman «${t.plural}»: usa esa palabra con el trabajador.`);
  return lines.join(' ');
}

// Un solo sitio: toda herramienta registrada responde con la hora de la consulta (D-126, criterio 14).
function stampResults(server: McpServer, now: () => Date): McpServer {
  const register = server.registerTool.bind(server) as (name: string, config: unknown, cb: (...a: unknown[]) => unknown) => unknown;
  (server as unknown as { registerTool: typeof register }).registerTool = (name, config, cb) =>
    register(name, config, async (...args) => withConsultedAt((await cb(...args)) as CallToolResult, now()));
  return server;
}

export async function buildServer(api: ApiClient, now: () => Date = () => new Date()): Promise<McpServer> {
  const me = await api.me();
  if (me.kind === 'ADMIN_CHAT') {
    const server = stampResults(new McpServer({ name: 'odin', version: '0.1.0' }, { instructions: instructions(DEFAULT_TERMS) }), now);
    registerListActivity(server, api);
    return server;
  }
  const capabilities = new Set(me.company?.capabilities ?? []);
  const terms = me.company ? projectTerms(me.company.projectLabel) : DEFAULT_TERMS;
  const server = stampResults(new McpServer({ name: 'odin', version: '0.1.0' }, { instructions: instructions(terms) }), now);
  const projects = createProjectNames(me, () => api.me(), terms);
  if (capabilities.has('SEARCH_KNOWLEDGE')) {
    registerSearchKnowledge(server, api, projects, capabilities.has('CASE_FILES'), terms);
    registerReadPage(server, api);
    registerOpenOriginal(server, api);
    registerListDocuments(server, api, projects, terms);
    registerListProjects(server, api, terms);
    registerVerifyCitation(server, api);
    registerListMyQuestions(server, api);
    if (capabilities.has('SIMILAR_CASES')) registerFindSimilarCases(server, api, projects, terms);
  }
  if (capabilities.has('CASE_FILES')) registerCaseFileTools(server, api);
  registerUploadLink(server, api, capabilities.has('CASE_FILES'), capabilities.has('UPLOAD_DOCUMENTS'), terms);
  if (capabilities.has('SAVE_KNOWLEDGE_CARD')) {
    registerSaveKnowledgeCard(server, api, terms);
    registerListMyKnowledgeCards(server, api, projects);
  }
  return server;
}
