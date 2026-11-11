import { GraphQLError } from 'graphql';
import { config } from './config.js';

const CODIGOS = {
  400: 'BAD_USER_INPUT',
  401: 'UNAUTHENTICATED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  422: 'BAD_USER_INPUT',
};

/** Llamada REST a un microservicio, reenviando el JWT del usuario. */
export async function llamar(servicio, ruta, { metodo = 'GET', query, cuerpo, headers, token } = {}) {
  const url = new URL(ruta, config.servicios[servicio]);
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
  }

  let respuesta;
  try {
    respuesta = await fetch(url, {
      method: metodo,
      headers: {
        Accept: 'application/json',
        ...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined,
      signal: AbortSignal.timeout(config.timeoutMs),
    });
  } catch (err) {
    throw new GraphQLError(`El servicio de ${servicio} no responde.`, {
      extensions: { code: 'SERVICE_UNAVAILABLE', servicio, causa: err.name },
    });
  }

  if (respuesta.status === 204) return null;
  const texto = await respuesta.text();
  const datos = texto ? safeJson(texto) : null;

  if (!respuesta.ok) {
    // Errores en problem+json; los de validacion traen "errores".
    const detalle = datos?.detail ?? datos?.title ?? `Error ${respuesta.status} en ${servicio}`;
    const errores = datos?.errores ?? datos?.errors;
    throw new GraphQLError(detalle, {
      extensions: {
        code: CODIGOS[respuesta.status] ?? (respuesta.status >= 500 ? 'SERVICE_UNAVAILABLE' : 'DOWNSTREAM_ERROR'),
        servicio,
        status: respuesta.status,
        ...(errores ? { errores } : {}),
      },
    });
  }
  return datos;
}

function safeJson(texto) {
  try {
    return JSON.parse(texto);
  } catch {
    return null;
  }
}
