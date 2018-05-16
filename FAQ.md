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

0) At boot time, Boot.scala is run

1) For each API version that a developer might call, we only run it if is enabled in Props e.g.

enableVersionIfAllowed(ApiVersion.v3_0_0)

2) As long as its not disabled in Props we add endpoints:

case ApiVersion.v3_0_0 => LiftRules.statelessDispatch.append(v3_0_0.OBPAPI3_0_0)

In this case we look into: /src/main/scala/code/api/v3_0_0/OBPAPI3_0_0.scala

This file defines which endpoints are made available to v3.0.0
Note that a version may have endpoints from the current and also previous versions e.g.from v3.0.0 and v2.1.0 and so on.

3) Then for the total endpoints available from each version, we check which endpoints should be enabled by looking at the Props for explicitly enabled endpoints or disabled endpoints.
For this to work we must pass the resource docs also.

e.g.

routes = ...
getAllowedEndpoints(endpointsOf2_2_0, Implementations2_2_0.resourceDocs)
getAllowedEndpoints(endpointsOf3_0_0, Implementations3_0_0.resourceDocs)


4) Once we have a final list of routes we serve them:

  routes.foreach(route => {
    oauthServe(apiPrefix{route}, findResourceDoc(route))
  })







