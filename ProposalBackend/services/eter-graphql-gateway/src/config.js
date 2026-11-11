const env = process.env;

export const config = {
  port: Number(env.PORT ?? 4000),
  jwtSecret:
    env.ETER_JWT_SECRET ??
    'eter-jwt-secret-development-only-CHANGE-IN-PRODUCTION-9f4a8c7e',
  timeoutMs: Number(env.ETER_DOWNSTREAM_TIMEOUT_MS ?? 5000),
  pollNotificacionesMs: Number(env.ETER_POLL_NOTIFICACIONES_MS ?? 5000),
  servicios: {
    activos: env.ETER_ACTIVOS_BASE_URL ?? 'http://localhost:8087',
    prestamos: env.ETER_PRESTAMOS_BASE_URL ?? 'http://localhost:8088',
    inventario: env.ETER_INVENTARIO_BASE_URL ?? 'http://localhost:8089',
    notificaciones: env.ETER_NOTIFICACIONES_BASE_URL ?? 'http://localhost:8090',
  },
};
