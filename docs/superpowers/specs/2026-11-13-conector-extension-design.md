# El conector de Odín como extensión de Claude Desktop (D-091) · diseño

- Fecha: 2026-11-13. Autor: CTO. Encargo del CEO (D-091).
- **Estado:** propuesta para aprobar. No se ha tocado la instalación del propietario ni su Claude Desktop.
- **Fuentes oficiales:**
  - el repositorio de MCPB: `github.com/modelcontextprotocol/mcpb` (README, `MANIFEST.md` y `CLI.md`);
  - el artículo de Anthropic «Desktop Extensions» (`anthropic.com/engineering/desktop-extensions`);
  - el centro de ayuda de Claude Desktop (`support.claude.com/en/articles/10949351`).
  - Lo que no consta en ellas se marca como «sin documentar», y se comprueba en el prototipo.

## 1. Problema

- **Hoy el conector es una entrada `odin` en `claude_desktop_config.json`,** que escribe `make mcp-claude-desktop`. Con esa forma pasa esto:
  - **En el chat, sus 15 herramientas salen agrupadas en «Other tools»,** sin nombre.
  - **La IA no encuentra «Odín» por su nombre:** busca entre los conectores de la cuenta y responde que no existe. En `main.log` aparece «Extension odin not found in installed extensions».
  - **Claude Desktop reescribe ese fichero desde memoria.** El 13 de noviembre a las 13:01 devolvió la entrada a una ruta y una clave antiguas, y el conector dejó de responder.
  - **La clave del trabajador está en claro** en ese fichero.
- **A un cliente le pasaría lo mismo.** Además, instalar el conector le exigiría editar un fichero de configuración y tener Node en cada equipo.

## 2. Propuesta

**Empaquetar `mcp/` como una extensión de Claude Desktop** (formato MCPB, `manifest_version` 0.3, servidor de tipo `node`). Cada trabajador la instala con doble clic. El diálogo de instalación le pide la dirección de Odín y su clave.

```json
{
  "manifest_version": "0.3",
  "name": "odin",
  "display_name": "Odín",
  "version": "0.2.0",
  "description": "El cerebro digital de tu empresa: busca en sus documentos, casos y fichas, y responde citando la fuente.",
  "author": { "name": "Evolve2Digital" },
  "icon": "icon.png",
  "server": {
    "type": "node",
    "entry_point": "dist/stdio.js",
    "mcp_config": {
      "command": "node",
      "args": ["${__dirname}/dist/stdio.js"],
      "env": { "ODIN_API_URL": "${user_config.api_url}", "ODIN_ACCESS_KEY": "${user_config.access_key}" }
    }
  },
  "user_config": {
    "api_url": { "type": "string", "title": "Dirección de Odín", "description": "La que te da tu empresa", "default": "http://127.0.0.1:8080", "required": true },
    "access_key": { "type": "string", "title": "Tu clave de Odín", "description": "La clave personal que te ha dado el administrador", "sensitive": true, "required": true }
  },
  "tools": [ { "name": "searchKnowledge", "description": "…" } ],
  "compatibility": { "platforms": ["darwin", "win32"], "runtimes": { "node": ">=24.0.0" } }
}
```

- **El código del MCP no cambia:** `dist/stdio.js` ya lee `ODIN_API_URL` y `ODIN_ACCESS_KEY` del entorno, y el manifiesto se las pasa.
- **Node:** Claude Desktop trae su propio Node para las extensiones de tipo `node`. En este Mac, Claude Desktop 5.11492.2 lleva Node 24.22.1, y `mcp/` pide Node 24 o superior. En el equipo del trabajador no hace falta instalar Node.
- **`tools`:** son las herramientas que salen en la página de instalación: 9 en `develop` y 15 con el expediente (encargo 13). Al fusionar con `feature/casos-expediente`, la prueba obliga a añadir las 6 suyas al manifiesto. Una prueba en `make check` comprobará que la lista del manifiesto coincide con las que registra el servidor, para que no se desfasen. Las de expediente solo aparecen si la empresa tiene esa capacidad; la descripción lo dirá.

## 3. Cómo encaja con lo que hay

- **`make mcp-extension`, nueva:**
  - compila `mcp/` de la copia desde la que se lanza;
  - instala solo las dependencias de producción y empaqueta con `mcpb pack` (`mcp/scripts/pack-extension.sh`);
  - deja `mcp/dist-extension/odin-<versión>.mcpb`, ignorado por git.
  - Al integrarlo con las órdenes de la instalación, `instalacion-actualizar` lo copiará a `data/`.
- **Hecho el 13 de noviembre (paso 1, D-092):**
  - el manifiesto en `mcp/extension/manifest.json`, con el nombre visible solo en `display_name`;
  - el icono `mcp/extension/icon.png`: la insignia de D-039 tal cual, a 340 px dentro de 512×512, con su espacio libre;
  - la CLI `@anthropic-ai/mcpb` 2.1.2, fija, como dependencia de desarrollo de `mcp/`;
  - `mcpb validate` en `make check`;
  - las pruebas de `mcp/test/extension.test.ts`:
    - la lista de herramientas del manifiesto es la que registra el servidor con todas las capacidades de `openapi.json`;
    - la versión del manifiesto es la de `package.json`;
    - la clave se pide como dato sensible y solo pasa por el entorno;
    - el nombre del producto solo aparece en `display_name`.
- **La CLI de MCPB** (`@anthropic-ai/mcpb`) va como dependencia de desarrollo de `mcp/` y se usa con `npx`, sin instalar nada global.
- **La ruta estable `data/mcp-claude-desktop` deja de hacer falta para el conector.** Claude Desktop copia la extensión en su propia carpeta. En este Mac existe `~/Library/Application Support/Claude/Claude Extensions`; la ruta exacta está sin documentar.
- **`make mcp-claude-desktop` se queda,** con su aviso de Claude Desktop abierto. Sirve para pruebas y para quien no pueda instalar extensiones.
- **Paso único de migración:** con Claude Desktop cerrado, se quita la entrada `odin` de `claude_desktop_config.json`, con copia previa. Así no conviven dos servidores llamados `odin`. Se haría con una opción nueva de `claude_desktop.py`.
- **`make instalacion-actualizar`** generaría también el `.mcpb` nuevo y diría al usuario que lo instale.

## 4. La clave

- **Va en el llavero del sistema operativo.** Con `sensitive: true`, Claude Desktop la cifra en el almacén seguro del sistema: Llavero en macOS y Credential Manager en Windows, según el centro de ayuda.
- **Mejora frente a hoy,** que está en claro en `claude_desktop_config.json`.
- **Sin documentar:** si algún fichero de Claude Desktop la guarda también en claro. En el prototipo se busca la clave en sus ficheros después de instalar.
- **Nunca se pega en el chat.** El trabajador la copia de donde se la dé el administrador. En las pruebas, de `data/datos-prueba/credenciales.local.md`.

## 5. Actualización

- **Una extensión distribuida en privado se actualiza a mano:** se instala el `.mcpb` nuevo. Solo las del directorio oficial se actualizan solas.
- **Sin documentar:** si al instalar una versión mayor se conservan la dirección y la clave. Se comprueba en el prototipo, instalando una versión con número mayor encima de la anterior.
- **Si no se conservan,** cada actualización pediría la clave otra vez. Sería una molestia asumible con pocas actualizaciones.
- **A más largo plazo,** una empresa con plan Team o Enterprise puede subir la extensión a su organización (ver el apartado 6).

## 6. Qué cambia para el cliente

- **Instalación:** doble clic en `odin.mcpb` y «Install», escribir la dirección y la clave. Sin Terminal, sin Node y sin editar ficheros. Vale en Mac y en Windows.
- **Se ve como «Odín»,** con su icono y su descripción, en Ajustes > Extensiones. Desde ahí se activa o desactiva, y se deja cada herramienta en «Needs approval» o con permiso.
- **Empresas con Claude Team o Enterprise:** sus administradores pueden permitir o bloquear extensiones, subir las suyas y usar una lista de permitidas. En ese cliente, su administrador tendría que subir o permitir la extensión de Odín.
- **D-019 se mantiene:** la extensión solo habla con el servidor de Odín del cliente. E2D no ve nada.
- **Varios equipos:** cada trabajador pone la dirección del servidor en su red. Hoy `compose.yaml` publica la API solo en 127.0.0.1, así que hace falta la variable que ya está anotada como pendiente (encargo 13, «Instalación en un cliente»).

## 7. Riesgos

| Riesgo | Qué se sabe | Cómo se cubre |
|---|---|---|
| Que con la extensión la IA siga sin encontrar «Odín» por su nombre | Se espera que salga con su nombre, pero no está probado | Primera comprobación del prototipo |
| Un `.mcpb` sin firma o autofirmado | Lo que hace Claude Desktop con él está sin documentar; puede avisar o pedir confirmación | Autofirmado para las pruebas. Para clientes, certificado de firma de código: coste, lo decide el propietario |
| Valores pedidos que se pierden al actualizar | Sin documentar | Prototipo |
| El Node de Claude Desktop cambia de versión | Hoy 24.22.1 | `compatibility.runtimes.node` y comprobación en cada versión nueva de Claude Desktop |
| Dos servidores `odin` a la vez (extensión y entrada del fichero) | La entrada del fichero sigue mientras no se quite | Paso de migración con Claude Desktop cerrado |
| Políticas de empresa que bloquean extensiones | Documentado para Team y Enterprise | Lo sube el administrador del cliente. Hay que contarlo en la venta |
| `privacy_policies` | Obligatorio si la extensión conecta con «servicios externos» | Odín es el servidor del propio cliente. Lo decide el CEO |

## 8. Plan, cuando se apruebe

1. **Manifiesto, icono y `make mcp-extension`,** con prueba previa:
   - la lista de herramientas del manifiesto, igual que la del servidor;
   - `mcpb validate` en `make check`.
2. **Prototipo en el Claude Desktop del propietario,** con su permiso y con Claude Desktop cerrado para quitar la entrada antigua:
   - instalar el `.mcpb` y escribir la dirección y la clave de Marta;
   - comprobar que sale «Odín» con su icono;
   - comprobar que la IA lo encuentra por su nombre;
   - comprobar que la clave no está en claro en ningún fichero;
   - comprobar que al instalar una versión mayor se conservan los valores.
3. **Integrarlo en `instalacion-actualizar`,** en el README y en el documento del propietario.

## 9. Lo que necesito que se decida

- **El icono:** un PNG de 512×512 con la marca de Odín. Lo decide el CEO.
- **La firma:** autofirmado para las pruebas. Para los clientes, un certificado de firma de código, que tiene coste. Lo decide el propietario.
- **`privacy_policies`:** si hace falta un enlace. Lo decide el CEO.
- **El permiso para el prototipo** en el Claude Desktop del propietario. Lo da el propietario.
