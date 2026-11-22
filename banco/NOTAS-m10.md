# M10 (criterio 13 de D-126): no responder de memoria

M10 es un escenario MULTIVUELTA en una sola conversación. Medición futura, no autorizada; el banco hoy NO puede ejecutarla.
- Turno 1: «¿Hay alguna póliza de Mirador que figure como vigente pero haya sido sustituida por otra posterior?» con la cadena VLM-16>19>22>25 marcada. Esperado: no (solo VLM-25 vigente).
- Paso entre turnos: `DELETE /v1/admin/documents/{id VLM-22-48120}/superseded-by` en la pila DESECHABLE (nunca la real).
- Turno 2 (misma conversación, misma pregunta): esperado sí; VLM-22-48120 figura vigente, VLM-25-48120 dice que la sustituye (detectado, sin confirmar).
- Comprobación: la respuesta del turno 2 debe salir de una llamada NUEVA (searchKnowledge, readPage o listDocuments) con `consultedAt` posterior al cambio.

Qué haría falta en el arnés (no construido):
1. Sesión multivuelta: hoy cada pregunta es una consulta independiente (`run.py` / `banco.py`).
2. Ejecutar `pasos_entre_turnos` contra la API de administración de la pila desechable.
3. Registrar por turno las llamadas a herramientas con su `consultedAt`, y comprobarlas.
Hasta entonces la batería (`turnos`, `pasos_entre_turnos`) solo define el escenario; `claves` y `no_debe_afirmar` de nivel superior son las del turno 2.
