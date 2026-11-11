import { config } from './config.js';
import { crearServidor } from './server.js';

crearServidor().listen(config.port, () => {
  console.log('ETER GraphQL gateway: http://localhost:' + config.port + '/graphql (ws: mismo path)');
});
