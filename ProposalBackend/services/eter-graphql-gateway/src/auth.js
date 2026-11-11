import jwt from 'jsonwebtoken';
import { GraphQLError } from 'graphql';
import { config } from './config.js';

/**
 * Valida el JWT (HS256, mismo secreto que los microservicios) y devuelve el
 * usuario. El token original se reenvia tal cual: cada microservicio sigue
 * aplicando sus propias reglas de rol y tenant.
 */
export function autenticar(cabecera) {
  if (!cabecera?.startsWith('Bearer ')) return null;
  const token = cabecera.slice(7).trim();
  try {
    const claims = jwt.verify(token, config.jwtSecret, { algorithms: ['HS256'] });
    return {
      token,
      email: claims.sub,
      nitUniv: claims.nitUniv,
      roles: Array.isArray(claims.roles) ? claims.roles : [],
    };
  } catch {
    return null;
  }
}

export function exigirUsuario(ctx) {
  if (!ctx.usuario) {
    throw new GraphQLError('Token ausente o invalido.', {
      extensions: { code: 'UNAUTHENTICATED', http: { status: 401 } },
    });
  }
  return ctx.usuario;
}

/** El nitUniv sale del token; si el cliente lo envia debe coincidir. */
export function resolverNit(ctx, nitUniv) {
  const usuario = exigirUsuario(ctx);
  if (nitUniv && nitUniv.trim() !== (usuario.nitUniv ?? '').trim()) {
    throw new GraphQLError(`No tiene permiso para operar sobre la universidad ${nitUniv}.`, {
      extensions: { code: 'FORBIDDEN' },
    });
  }
  return usuario.nitUniv;
}
