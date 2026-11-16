export const ACTIONS: Record<string, string> = {
  AUDIT_ENABLED: 'Registro encendido', AUDIT_DISABLED: 'Registro apagado', AUDIT_SETTINGS_UPDATED: 'Ajustes del registro cambiados',
  REAUTHENTICATION_FAILED: 'Contraseña mala al volver a identificarse', LOGIN_SUCCEEDED: 'Entrada al panel', LOGIN_FAILED: 'Entrada fallida al panel', LOGOUT: 'Salida del panel',
  INSTALLATION_SET_UP: 'Instalación configurada', ADMIN_PASSWORD_RECOVERED: 'Contraseña de administrador recuperada',
  COMPANY_CREATED: 'Empresa creada', COMPANY_UPDATED: 'Empresa cambiada', COMPANY_CAPABILITIES_CHANGED: 'Capacidades de la empresa cambiadas',
  GROUP_ADMIN_ADDED: 'Administrador de grupo añadido', GROUP_ADMIN_REVOKED: 'Administración de grupo quitada',
  MEMBER_ADDED: 'Alta de persona', MEMBER_UPDATED: 'Persona cambiada', MEMBER_REMOVED: 'Persona quitada de la empresa',
  USER_UPDATED: 'Usuario cambiado', USER_DELETED: 'Usuario borrado', PASSWORD_CHANGED: 'Contraseña cambiada',
  ACCESS_KEY_ISSUED: 'Clave emitida', ACCESS_KEY_REVOKED: 'Clave revocada',
  PROJECT_CREATED: 'Proyecto creado', PROJECT_UPDATED: 'Proyecto cambiado', PROJECT_DELETED: 'Proyecto borrado',
  PROJECT_MEMBER_ADDED: 'Persona añadida al proyecto', PROJECT_MEMBER_REMOVED: 'Persona quitada del proyecto',
  DOCUMENT_UPLOADED: 'Documento subido', DOCUMENT_RETRIED: 'Documento reintentado', DOCUMENT_WITHDRAWN: 'Documento retirado', DOCUMENT_RESTORED: 'Documento restaurado',
  DOCUMENT_SUPERSEDED: 'Documento sustituido', DOCUMENT_SUPERSEDED_CLEARED: 'Sustitución quitada', DOCUMENT_CASE_MARK_CHANGED: 'Marca de caso cambiada',
  DOCUMENTS_CASE_MARK_CHANGED: 'Marca de caso cambiada en varios documentos', DOCUMENT_ORIGINAL_DOWNLOADED: 'Original descargado',
  DOCUMENT_TOPIC_ADDED: 'Tema añadido al documento', DOCUMENT_TOPIC_REMOVED: 'Tema quitado del documento', DOCUMENT_RETAGGED: 'Documento reetiquetado',
  TOPIC_CREATED: 'Tema creado', TOPIC_UPDATED: 'Tema cambiado', TOPIC_DELETED: 'Tema borrado',
  CARD_APPROVED: 'Ficha aprobada', CARD_CORRECTED: 'Ficha corregida', CARD_REJECTED: 'Ficha rechazada', CARD_SAVED: 'Ficha guardada',
  CASE_FILE_DOCUMENTS_ADDED: 'Documentos añadidos al expediente', CASE_FILE_DOCUMENT_REMOVED: 'Documento quitado del expediente',
  INTAKE_STARTED: 'Primera carga iniciada', INTAKE_UPLOAD_CLOSED: 'Subida de la primera carga cerrada', INTAKE_PROJECT_LIST_IMPORTED: 'Lista de proyectos importada',
  INTAKE_PROJECTS_DECIDED: 'Proyectos decididos', INTAKE_PROJECT_LIST_CLOSED: 'Lista de proyectos cerrada', INTAKE_PROPOSALS_CONFIRMED: 'Propuestas confirmadas',
  INTAKE_PROPOSAL_DECIDED: 'Propuesta decidida', INTAKE_DOCUMENT_DISCARDED: 'Documento descartado', INTAKE_DOCUMENT_PLACED: 'Documento colocado', INTAKE_CLOSED: 'Primera carga cerrada', DOCUMENT_SCAN_UNLINKED: 'Copia escaneada desvinculada',
  UPLOAD_LINK_ISSUED: 'Enlace de subida emitido', ORIGINAL_LINK_ISSUED: 'Enlace al original emitido', ACTIVITY_EXPORTED: 'Registro exportado',
};

export const CHANNELS: Record<string, string> = { PANEL: 'Panel', API: 'API', CHAT: 'Chat', CHAT_ADMIN: 'Chat (administración)', SCRIPT: 'Guion', SYSTEM: 'Sistema' };
export const OUTCOMES: Record<string, string> = { DONE: 'Hecho', DENIED: 'Negado', FAILED: 'Fallido' };
export const SYSTEM_ACTORS: Record<string, string> = { SYSTEM: 'Sistema', INSTALLER: 'Instalador', LINK: 'Enlace' };
