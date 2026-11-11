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
| **Gateway** | En `ingresar.html`, pega un JWT y la URL del gateway | Todas las pantallas consultan `http://localhost:4000/graphql`; la bandeja usa la suscripción `notificacionRecibida` por WebSocket |
| **Demostración** | Botón "Ver demostración", o `?demo=1` en cualquier página | Mismos datos y reglas que los microservicios, guardados en el navegador. Imita lo que hace Kafka: el kardex y la notificación aparecen unos segundos después de crear o devolver un préstamo, dar de alta o de baja un activo |

Para el modo gateway:

```bash
cd ProposalBackend/infrastructure
docker compose up -d --build          # infraestructura, 4 microservicios y gateway
cd ..
python tools/generar_token.py         # JWT de desarrollo (ADMIN, NIT 900123456-1)
```

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

Sigue el estilo de `Eter-frontend-main.zip`: oscuro por defecto con tema claro, violeta como
acento, Fjalla One para títulos y cifras, Inter para texto y JetBrains Mono para códigos. Los
íconos son SVG propios (`ICONOS` en `app.js`).

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
- `--f-titulo` (Fjalla One) siempre con peso 400: la fuente no tiene otros.
- La ruta de la barra superior sale del `.folio` de `.cabecera`, y `ETER.ui.montar` agrega ahí el
  ícono del módulo. Si una página reescribe `.cabecera`, ambos se reponen solos.
- Tablas largas: `ETER.ui.paginador(caja, { alCambiar })` y `.cortar(lista)` antes de pintar.
  Llámalo también con la lista vacía, para que no queden controles viejos a la vista.
