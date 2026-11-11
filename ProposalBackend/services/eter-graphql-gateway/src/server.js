import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { createYoga, createSchema } from 'graphql-yoga';
import { useServer } from 'graphql-ws/lib/use/ws';
import { WebSocketServer } from 'ws';
import { autenticar } from './auth.js';
import { config } from './config.js';
import { resolvers } from './resolvers.js';

const typeDefs = readFileSync(new URL('./schema.graphql', import.meta.url), 'utf8');

export const yoga = createYoga({
  schema: createSchema({ typeDefs, resolvers }),
  graphqlEndpoint: '/graphql',
  landingPage: false,
  // Solo los GraphQLError con extensions.code llegan al cliente.
  maskedErrors: true,
  // Token: header Authorization o connectionParams (WebSocket).
  context: ({ request, connectionParams }) => ({
    usuario: autenticar(
      request?.headers.get('authorization') ??
        connectionParams?.Authorization ??
        connectionParams?.authorization,
    ),
  }),
});

export function crearServidor() {
  const server = createServer(yoga);
  const wss = new WebSocketServer({ server, path: yoga.graphqlEndpoint });

  useServer(
    {
      execute: (args) => args.rootValue.execute(args),
      subscribe: (args) => args.rootValue.subscribe(args),
      onSubscribe: async (ctx, message) => {
        const params = message.payload;
        const { schema, execute, subscribe, contextFactory, parse, validate } = yoga.getEnveloped({
          ...ctx,
          req: ctx.extra.request,
          socket: ctx.extra.socket,
          params,
        });
        const args = {
          schema,
          operationName: params.operationName,
          document: parse(params.query),
          variableValues: params.variables,
          contextValue: await contextFactory(),
          rootValue: { execute, subscribe },
        };
        const errores = validate(args.schema, args.document);
        return errores.length ? errores : args;
      },
    },
    wss,
  );
  return server;
}
