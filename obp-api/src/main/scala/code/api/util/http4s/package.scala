package code.api.util

/**
 * Http4s support package for OBP API.
 * 
 * This package provides http4s-specific utilities for:
 * - Building CallContext from http4s requests
 * - Storing validated objects in request attributes (Vault keys)
 * - Matching requests to ResourceDoc entries
 * - ResourceDoc-driven validation middleware
 * - Error response conversion
 * 
 * Usage:
 * {{{
 * import code.api.util.http4s._
 * 
 * // Build CallContext from request
 * val cc = Http4sCallContextBuilder.fromRequest(request, "v7.0.0")
 * 
 * // Access validated objects from request attributes
 * val user = Http4sVaultKeys.getUser(request)
 * val bank = Http4sVaultKeys.getBank(request)
 * 
 * // Apply middleware to routes
 * val wrappedRoutes = ResourceDocMiddleware.apply(resourceDocs)(routes)
 * 
 * // Convert errors to http4s responses
 * ErrorResponseConverter.unknownErrorToResponse(error, callContext)
 * }}}
 */
package object http4s {
  // Re-export types for convenience
  type SharedCallContext = code.api.util.CallContext
}
