# Acceso a Odín desde conectores remotos sin que E2D sea intermediario (encargo 7, D-019) · diseño

- Fecha: 2026-11-13. Autor: CTO. Encargo del CEO.
- **Estado:** propuesta para decidir. Es solo diseño: no se ha construido ni instalado nada.
- **Fuentes oficiales,** leídas el 13 de noviembre:
  - Claude:
    - «Add a connector that isn't in the directory»: `claude.com/docs/connectors/custom/remote-mcp.md`;
    - autenticación de conectores: `claude.com/docs/connectors/building/authentication.md`;
    - túneles MCP: `claude.com/docs/connectors/mcp-tunnels/overview`, `setup.md` y `oauth.md`;
    - centro de ayuda: `support.claude.com/en/articles/11175166`;
    - direcciones IP de Anthropic: `platform.claude.com/docs/en/api/ip-addresses`.
  - Especificación de autorización de MCP 2025-11-25: `modelcontextprotocol.io/specification/2025-11-25/basic/authorization`.
  - OpenAI:
    - «Add custom MCP server»: `developers.openai.com/api/docs/guides/developer-mode`;
    - «Secure MCP tunnels»: `developers.openai.com/api/docs/guides/secure-mcp-tunnels`.
  - **No se pudieron leer** el artículo de ayuda de OpenAI sobre conectores (403) ni el antiguo artículo 11503834 de Anthropic (404).
  - Lo que no consta en las fuentes va marcado como «sin comprobar».

## 1. Problema

- **Hoy el conector es local.** Claude Desktop arranca el MCP de Odín en el equipo del trabajador, y la clave va en su configuración (o en la extensión, D-092).
- **Con eso pasa:**
  - solo vale en Claude Desktop, no en la web, el móvil ni otras IAs;
  - depende de que Claude Desktop pase las herramientas locales al chat. El 13 de noviembre el chat del propietario no veía Odín aunque estaba activo.
- **Lo que pide la visión total** (D-018, D-020): consultar el cerebro desde la IA que ya usa el trabajador, en cualquier dispositivo, sin que E2D esté en medio (D-019).

## 2. Lo que dicen las fuentes oficiales

**Claude:**
- **Planes:** los conectores personalizados remotos están en Free, Pro, Max, Ultra, Team y Enterprise. Free admite uno.
- **Equipos:** en Team y Enterprise lo añade el propietario de la organización, y cada miembro se conecta con su cuenta.
- **Desde dónde llama:** «Claude connects to your remote MCP server from Anthropic's cloud infrastructure, rather than from your local device».
- **Dispositivos:** el mismo conector vale en claude.ai, Claude Desktop, Cowork y las apps móviles.
- **Red:** el servidor tiene que ser accesible por internet desde las IP de Anthropic. Lo que está tras una VPN o en una red privada «won't connect».
  - Las llamadas salen de `160.79.104.0/21` y `153.61.208.0/20`, con las que se puede hacer lista de permitidas.
- **Autenticación:**
  - **OAuth por persona** («Sign in now»), con tres formas de identificarse como cliente:
    - documento de metadatos del cliente (CIMD), que es la preferida;
    - registro dinámico (DCR);
    - un cliente propio.
  - **Sin autenticación.**
  - **Cabeceras fijas** (beta, con aprobación de Anthropic): una sola credencial para toda la organización, «not a per-user one».
  - Vuelta del OAuth: `https://claude.ai/api/mcp/auth_callback`.
  - Exigencias: PKCE S256, `401` con `WWW-Authenticate` y `resource_metadata`, un `resource` igual a la URL del servidor y refresco con rotación del token de refresco.
- **Túneles MCP de Claude:**
  - conectan servidores de una red privada sin abrir puertos: en la red del cliente corren `cloudflared` y un proxy de Anthropic;
  - Cloudflare lleva el tráfico cifrado y «cannot read» las peticiones;
  - **solo Enterprise, bajo petición y en «research preview»**, sin compromisos de continuidad.

**Especificación de MCP:**
- el servidor MCP es un servidor de recursos OAuth 2.1, con metadatos del recurso protegido (RFC 9728);
- el servidor de autorización publica sus metadatos (RFC 8414);
- el token se emite para ese servidor (RFC 8707);
- el servidor no puede reenviar a otro servicio el token que recibe.

**OpenAI (ChatGPT):**
- servidores MCP personalizados desde la web, por SSE o HTTP en streaming;
- OAuth (con CIMD o DCR) o sin autenticación;
- las herramientas sin `readOnlyHint` se tratan como de escritura y piden confirmación;
- tiene su propio túnel seguro (`tunnel-client`, con salida a `api.openai.com`);
- **sin comprobar:** qué planes lo tienen.

**Otras IAs** (Gemini, Le Chat, Copilot): sin comprobar.

## 3. Lo que hay en Odín y lo que falta

- **El MCP ya habla HTTP** (`mcp/src/http.ts`, servicio `mcp-http` de Compose):
  - transporte HTTP en streaming, la clave del trabajador como `Bearer` y una sesión atada a esa clave;
  - Compose lo publica solo en `127.0.0.1:8090`.
- **Falta:**
  - **Llegar desde internet** por HTTPS (apartado 4).
  - **Un servidor de autorización OAuth 2.1** (apartado 5): Claude no manda la clave de cada trabajador, y las cabeceras fijas son de toda la organización y romperían el aislamiento por persona (D-020).
  - **Anotaciones `readOnlyHint`** en las herramientas de consulta. Hoy no hay ninguna, y ChatGPT pediría confirmación para cada búsqueda.

## 4. Cómo se publica el MCP: tres opciones

### Opción A · Túnel de salida de Cloudflare en la cuenta del cliente (recomendada por defecto)

- **Cómo funciona:**
  - en el equipo de Odín corre `cloudflared`, que abre una conexión de salida hacia Cloudflare, sin puertos abiertos ni IP fija;
  - Cloudflare publica `https://odin.<dominio-del-cliente>` y lleva las peticiones al túnel.
- **Dominio:** de Cloudflare a nombre del cliente. Con el plan gratuito, Cloudflare tiene que gestionar el DNS del dominio. Si el cliente no quiere moverlo, se usa un dominio aparte solo para Odín.
- **Certificados:** los pone y renueva Cloudflare en su borde.
- **Lo que se publica:**
  - solo `/mcp`, los metadatos `/.well-known/oauth-*` y las páginas del OAuth;
  - el panel y la API de administración siguen solo en la red local;
  - en `/mcp`, lista de permitidas con las IP de salida de Anthropic, y las de OpenAI cuando se comprueben.
- **Ventajas:**
  - sin tocar el router ni contratar IP fija;
  - funciona aunque el operador use CGNAT;
  - gratis en el plan básico.
- **Inconvenientes:**
  - **Cloudflare termina el TLS y ve las peticiones y respuestas en claro.** Es un tercero contratado por el cliente, no E2D, pero es un tercero más en el camino (decisión del propietario, apartado 9).
  - Si se cae internet en la oficina o se apaga el equipo, no hay conector.
- **Variante con cifrado de extremo a extremo:** Tailscale Funnel termina el TLS en el propio equipo, pero publica en un nombre `*.ts.net`. Sin comprobar con su documentación; se mira en el piloto si el propietario lo quiere.

### Opción B · Publicación directa desde la oficina

- **Cómo funciona:**
  - un proxy inverso (Caddy) en el equipo de Odín, con el puerto 443 abierto en el router hacia él;
  - certificado de Let's Encrypt renovado solo, y un nombre en el dominio del cliente.
- **Ventajas:** nadie más ve el tráfico. TLS de extremo a extremo entre la IA y la oficina.
- **Inconvenientes:**
  - hace falta IP pública (fija o con DNS dinámico) y sin CGNAT;
  - hay que configurar el router;
  - el equipo de Odín queda expuesto a internet. La lista de permitidas por IP se hace en el proxy.
- **Para quién:** clientes con informático, IP fija y que no acepten un tercero en el camino.

### Opción C · Túneles oficiales de cada IA

- **Claude** (túneles MCP) y **OpenAI** (Secure MCP Tunnel): conexión solo de salida, sin publicar nada en internet. En Claude, cifrado que ni Cloudflare puede leer.
- **Inconvenientes:**
  - un túnel distinto por cada IA;
  - el de Claude es solo Enterprise y está en vista previa, sin garantía de continuidad;
  - de los planes de OpenAI no hay datos.
- **Para quién:** más adelante, clientes con Claude Enterprise o con el plan de empresa de OpenAI. No sirve para la mayoría de clientes pequeños.

## 5. Cómo se autentica cada trabajador

### Opción 1 · OAuth en Odín, entrando con la clave personal (recomendada)

- **Odín añade un servidor de autorización OAuth 2.1** en la API:
  - metadatos;
  - CIMD, preferido por Claude, y DCR con límite como alternativa;
  - PKCE S256;
  - token emitido para `https://odin.<dominio>/mcp`;
  - token de acceso corto y token de refresco con rotación.
- **Entrada:** el trabajador pulsa «Conectar» en Claude o en ChatGPT y llega a una página de Odín, donde pega una vez su clave personal, la que ya le da y le retira el administrador (D-020). No hacen falta contraseñas nuevas ni correo.
- **Retirada:** si el administrador retira la clave, caen también sus tokens.
- **El MCP no reenvía el token:** lo valida con la API, que le dice qué trabajador es, y llama a la API en nombre de ese trabajador con una credencial interna. La especificación prohíbe reenviar el token.
- **Lo que no cambia:** el aislamiento por empresa y proyecto sigue en el cerebro, como ahora.

### Opción 2 · Inicio de sesión con la cuenta de empresa del cliente (Microsoft 365 o Google)

- Odín acepta los tokens del proveedor de identidad del cliente.
- Es cómodo para quien ya lo tiene, pero hay que configurarlo en cada cliente.
- En GitHub hay incidencias abiertas de Entra ID con claude.ai.
- Queda para más adelante.

### Descartada · Cabeceras fijas

- Son una credencial de toda la organización: todos los trabajadores entrarían como uno solo y se perdería el aislamiento por persona. Además, están en beta.

## 6. Qué sale de la oficina y qué no

- **Hacia la IA del trabajador** (Anthropic, OpenAI): lo mismo que hoy con el conector local, es decir, los resultados de cada herramienta (extractos, páginas, fichas y listas) que pide para responder (D-016). El archivo entero no sale.
- **Lo nuevo:**
  - las peticiones entran desde internet a la oficina;
  - con la opción A, Cloudflare las ve en claro al pasar;
  - con B y C, nadie más.
- **E2D no está en el camino** (D-019):
  - el dominio, la cuenta de Cloudflare y los tokens son del cliente;
  - si E2D ayuda a configurarlo, lo hace en la cuenta del cliente y no se queda credenciales.
- **Originales y enlaces de subida:** `openOriginal` y `requestUploadLink` dan enlaces a `ODIN_PUBLIC_URL`.
  - Si esa dirección es la local, solo se abren dentro de la oficina.
  - Publicarla en internet expondría la descarga de originales con enlace temporal. Decisión del propietario.

## 7. Cómo se añade en cada IA

- **Claude:**
  - Ajustes > Conectores > «Add custom connector», con la URL `https://odin.<dominio>/mcp`, «Sign in now» y «Use Claude's published identity» (CIMD);
  - en Team o Enterprise lo añade una vez el propietario de la organización, y cada trabajador entra con su clave;
  - en Free, solo un conector personalizado;
  - el mismo conector vale en la web, Claude Desktop, el móvil y Cowork, lo que resuelve el problema del 13 de noviembre.
- **ChatGPT:** «Add custom MCP server» en la web, con OAuth. Con `readOnlyHint`, las consultas no piden confirmación. Planes, sin comprobar.
- **Otras IAs:** sin comprobar. Se comprueban una a una antes de prometerlas en la venta.

## 8. Coste y riesgos

**Coste para el cliente:**
- A: plan gratuito de Cloudflare más un dominio, si no tiene o no quiere usar el suyo;
- B: IP fija, si su operador la cobra;
- C: plan Enterprise de la IA.
- Ninguna opción pide equipo nuevo.

**Coste de desarrollo** (lo estimo al planificar, no lo cifro sin medir):
- el servidor OAuth en Odín;
- el cambio del MCP para validar tokens;
- `readOnlyHint`;
- la publicación limitada a `/mcp` y al OAuth;
- las pruebas con Claude y ChatGPT reales.

| Riesgo | Cómo se cubre |
|---|---|
| Ataques desde internet al punto publicado | Solo `/mcp` y el OAuth. Lista de permitidas por IP en `/mcp`, límite de peticiones y Odín al día. Panel y API, solo en la red local |
| Adivinar claves en la página de entrada | Claves largas y aleatorias, límite de intentos por IP y registro |
| Robo de un token | Token de acceso corto, rotación del de refresco y caída al retirar la clave |
| Registro masivo de clientes por DCR | CIMD primero; DCR con límite y caducidad |
| Un tercero ve el tráfico (opción A) | Lo decide el propietario. Alternativas: B, C o la variante con cifrado de extremo a extremo |
| Inyección de instrucciones en documentos que piden sacar datos | Igual que hoy: el cerebro solo da lo que el trabajador puede ver |
| Sin servicio si cae la oficina | Igual que hoy. Se avisa en la instalación |
| Los túneles de Claude cambian o desaparecen | No se depende de ellos por defecto |

## 9. Lo que decide el propietario

1. **La opción por defecto para publicar:** recomiendo A, con B para quien no acepte un tercero en el camino y C más adelante para clientes Enterprise.
2. **Si se acepta que Cloudflare, en la cuenta del cliente, vea el tráfico en claro** (opción A).
3. **Cómo entra el trabajador:** recomiendo la opción 1, pegando su clave una vez. La opción 2 queda para más adelante.
4. **Si los originales y los enlaces de subida se abren también fuera de la oficina.**
5. **Qué IAs se ofrecen al principio:** Claude y ChatGPT, y el resto cuando se comprueben.
6. **Un piloto en su instalación,** que exige publicar su Mac en internet con un dominio de prueba. Lo decide él.

## 10. Plan, si se aprueba

1. **OAuth en Odín** con pruebas primero: metadatos, CIMD, PKCE, `resource`, rotación del refresco y retirada al retirar la clave. Añadir `readOnlyHint`.
2. **Publicación limitada:**
   - Compose con un perfil para `cloudflared` (opción A) o Caddy (opción B), que solo deja pasar `/mcp`, `/.well-known/*` y el OAuth;
   - prueba de que el panel y la API no responden desde fuera.
3. **Piloto con el permiso del propietario:** conector en Claude (web y móvil) y en ChatGPT con la clave de Marta, aislamiento comprobado con Javier y nada visible fuera de `/mcp`.
4. **Guía de instalación para el cliente,** con dominio, DNS, Cloudflare o router, y cómo añadirlo en cada IA.
