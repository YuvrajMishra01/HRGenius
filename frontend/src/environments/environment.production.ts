/**
 * Production environment configuration for Vercel deployment.
 * Set `apiUrl` to your deployed Render backend URL once known.
 *
 * Example:
 *   apiUrl: 'https://hrgenius-backend.onrender.com'
 *
 * Note: If apiUrl is provided with or without a trailing slash or `/api/v1`,
 * the `apiBaseUrlInterceptor` will automatically normalize it.
 */
export const environment = {
  production: true,
  apiUrl: '',
};
