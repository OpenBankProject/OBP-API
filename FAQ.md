# FAQ


## How do I create new endpoints in OBP API?

See lazy val getCustomersForUser in /src/main/scala/code/api/v3_0_0/APIMethods300.scala as a code example


## How do I minimise merge conflicts in my fork?

Note: If you want to maintain a fork of OBP-API, we suggest you place your custom code in folders named "custom"
e.g. /src/main/scala/code/api/custom to reduce the possibility of merge conflicts
Please make sure you follow the terms of the AGPL or obtain a proprietary license from TESOBE or our partners.


## How do endpoints become available to the OBP API?

In summary, we:

1) Check if the Version should be enabled by looking at the Props file.
2) Check which endpoints are included in each Version. (Probably contains endpoints that are also available in previous Versions).
3) Check which endpoints from this list should be enabled by looking at the Props.
4) Serve them

In more detail:

0) At boot time, `Boot.scala` initialises Lift Mapper (the database / ORM layer) and OBP configuration. It no longer registers any HTTP routes — endpoint dispatch is served entirely by native http4s.

1) For each API version, `enableVersionIfAllowed(ApiVersion.v3_0_0)` gates the version against the Props (`api_enabled_versions` / `api_disabled_versions`). This now only records and logs whether the version is enabled.

2) Each version's endpoints are defined as native http4s `HttpRoutes[IO]` in `code.api.vX.Http4sXxx` (e.g. `Http4s300`) and exposed via `wrappedRoutesVXxxServices`. These are wired, in priority order, into `Http4sApp.baseServices` (see `obp-api/src/main/scala/code/api/util/http4s/Http4sApp.scala`). Each version's routes are wrapped by `Http4sApp.gate`, which returns `HttpRoutes.empty` when the version is disabled.

The matching Lift `OBPAPI3_0_0.scala` / `APIMethods300.scala` files are retained only as commented-out source-of-truth and as the `allResourceDocs` registry used by the resource-docs aggregation — they no longer serve requests.

3) Per-endpoint enable/disable is enforced at request time by `ResourceDocMiddleware`, which reads the Props for explicitly enabled/disabled endpoints (`api_enabled_endpoints` / `api_disabled_endpoints`) and also performs authentication, role checks, and entity resolution.

4) Any request that matches no route falls through to `notFoundCatchAll`, which returns a JSON 404. There is no Lift fallback.







