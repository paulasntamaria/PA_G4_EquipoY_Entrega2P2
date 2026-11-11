# ETER · Frontend de patrimonio

Frontend web de ETER en HTML, CSS y JavaScript sin frameworks. Habla solo con el
gateway GraphQL (`ProposalBackend/services/eter-graphql-gateway`, puerto 4000); nunca
llama directo a los microservicios.

## Abrirlo

No necesita build ni `npm install`.

- **Doble clic en `ingresar.html`.** Funciona desde `file://`: los scripts son clásicos y el
  gateway acepta cualquier origen (CORS por defecto de GraphQL Yoga).
- O con un servidor estático, si lo prefieres:
  ```bash
  cd ProposalFrontend
  python -m http.server 5500     # http://localhost:5500/ingresar.html
  ```

## Dos modos

| Modo | Cómo se entra | Qué hace |
|---|---|---|
| **Gateway** | En `ingresar.html`, pega un JWT y la URL del gateway | Todas las pantallas consultan `http://127.0.0.1:4000/graphql`; la bandeja usa la suscripción `notificacionRecibida` por WebSocket |
| **Demostración** | Botón "Ver demostración", o `?demo=1` en cualquier página | Mismos datos y reglas que los microservicios, guardados en el navegador. Imita lo que hace Kafka: el kardex y la notificación aparecen unos segundos después de crear o devolver un préstamo, dar de alta o de baja un activo |

Para el modo gateway:

```bash
cd ProposalBackend/infrastructure
docker compose up -d --build          # infraestructura, 4 microservicios y gateway
cd ..
python tools/generar_token.py         # JWT de desarrollo (ADMIN, NIT 900123456-1)
```

El frontend usa `127.0.0.1` y no `localhost`, y si escribes `localhost` lo cambia solo. En Windows,
`localhost` resuelve primero a IPv6 (`::1`), y el reenvío de puertos de Docker Desktop por IPv6 a
veces acepta la conexión y no responde: la página queda cargando sin error.

## Pantallas

| Página | Para qué |
|---|---|
| `ingresar.html` | Token y URL del gateway, o modo demostración |
| `index.html` | Dashboard: cifras, acceso a los módulos, vencidos, activos por estado, acciones rápidas, últimos movimientos y avisos |
| `activos.html` | Registro de activos con filtros; alta de activos |
| `activo.html?placa=…` | Ficha del activo: placa, préstamo vigente, historial, kardex, custodios; editar y dar de baja |
| `prestamos.html` | Préstamos, calendario de vigentes, nuevo préstamo y devolución |
| `inventario.html` | Kardex de movimientos (automáticos por Kafka y manuales) y asignaciones |
| `notificaciones.html` | Bandeja del usuario del JWT, en vivo |

## Diseño

Tonos tierra y bosque: papel cálido como fondo, verde pino (`--primario`) como color de marca y
terracota (`--acento`) para contadores y avatar. Colores planos, sin degradados. Claro por defecto,
con tema oscuro. Source Sans 3 para títulos y texto, y Source Code Pro para placas y códigos. Los
íconos son [Material Symbols Outlined](https://fonts.google.com/icons): `ICONOS` en `app.js`
traduce cada nombre interno al nombre del símbolo, y `ETER.ui.icono('activos')` pinta
`<span class="icono">sell</span>`. El ícono de la página activa se muestra relleno (`FILL 1`).

Imágenes (`assets/img`):

| Archivo | Dónde |
|---|---|
| `eter-isotipo.png` / `eter-isotipo-oscuro.png` | Logo del menú lateral, según el tema (clases `solo-claro` / `solo-oscuro`) |
| `eter-logo-oscuro.png` | Logo horizontal sobre el fondo de `ingresar.html` |
| `eter_login_background.svg` | Fondo de `ingresar.html`: el formulario va en su zona lisa izquierda |
| `eter_dashboard_header.svg` / `_oscuro.svg` | Banner del dashboard; la versión oscura cambia solo los colores |
| `favicon.png` | Ícono de la pestaña en todas las páginas |

Los PNG de la web salen de los originales (`isotipo.png`, `logoHorizontal.png` y sus versiones
oscuras): se les quitó el fondo sólido, se recortaron al dibujo y se redujeron (de ~1.1 MB a 6–124 KB).

El tema, el menú colapsado y las secciones plegadas se guardan en este navegador. En pantallas
de hasta 900 px el menú lateral pasa a ser un cajón que abre la hamburguesa.

## Estructura

```
assets/css/eter.css          sistema de diseño (tokens, componentes)
assets/css/paginas/*.css     ajustes propios de cada pantalla
assets/js/tema.js            aplica tema y menú antes de pintar (va en el <head>)
assets/js/api.js             cliente del gateway + modo demostración (ETER.api, ETER.sesion)
assets/js/app.js             armazón y utilidades de interfaz (ETER.ui)
assets/js/paginas/*.js       lógica de cada pantalla
_plantilla.html              esqueleto para pantallas nuevas
```

Reglas para tocar el código:

- Las páginas usan `ETER.api.*`; el GraphQL vive solo en `api.js`.
- HTML dinámico siempre con `ETER.ui.html\`…\``, que escapa lo interpolado.
- Colores solo desde los tokens de `eter.css`. El tema claro y el oscuro salen de ahí.
- Íconos siempre con `ETER.ui.icono(nombre)` o `<i data-icono="nombre">`; si falta uno, agrégalo a
  `ICONOS` con el nombre de Material Symbols. Son `<span>`: en CSS, un selector genérico como
  `> span` también los toma (usa `span:not(.icono)`).
- La ruta de la barra superior sale del `.folio` de `.cabecera`, y `ETER.ui.montar` agrega ahí el
  ícono del módulo. Si una página reescribe `.cabecera`, ambos se reponen solos.
- Tablas largas: `ETER.ui.paginador(caja, { alCambiar })` y `.cortar(lista)` antes de pintar.
  Llámalo también con la lista vacía, para que no queden controles viejos a la vista.
