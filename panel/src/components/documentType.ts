const LABELS: Record<string, string> = {
  caso: 'Ficha de caso', anexo: 'Anexo', acta: 'Acta', contrato: 'Contrato', poliza: 'Póliza', presupuesto: 'Presupuesto',
  comunicacion: 'Comunicación', aviso: 'Aviso', norma: 'Norma interna', procedimiento: 'Procedimiento',
};

/** The name shown in the tray for the document type the classification worked out; any new rule still shows capitalised. */
export const documentTypeLabel = (type?: string | null) =>
  !type ? 'Tipo sin identificar' : LABELS[type] ?? type.charAt(0).toUpperCase() + type.slice(1);
