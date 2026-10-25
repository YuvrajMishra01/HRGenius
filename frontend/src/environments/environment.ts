/**
 * Default environment configuration.
 * In local development, `apiUrl` is empty so requests use relative `/api/v1`
 * which are proxied to `http://localhost:8080` via `proxy.conf.json`.
 */
export const environment = {
  production: false,
  apiUrl: '',
};
