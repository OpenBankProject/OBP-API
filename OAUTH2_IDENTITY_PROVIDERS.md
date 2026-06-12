# OAuth2 / OIDC Identity Providers: Public vs. Operator-Controlled

OBP-API accepts OAuth2/OIDC Bearer tokens (JWTs) from two kinds of identity
providers, and they have different trust properties.

## Public IdPs (Google, Yahoo, Microsoft)

Anyone can register an application with these providers and obtain valid,
correctly signed id_tokens. Signature + issuer + expiry validation therefore
only proves *"Google signed this token for someone"* — not that it was issued
for **your** application. Without further checks, an id_token minted for any
third-party app (or for another OBP environment, e.g. a token obtained on a
test instance and replayed against production) would authenticate and
auto-create a user and consumer.

Two controls close this:

### 1. Enablement — `oauth2.oidc_provider`

```
oauth2.oidc_provider=obp-oidc,keycloak,google
```

This props is what API Explorer II / API Manager II / OBP-Portal read (via the
`/well-known` endpoint) to offer login options, and it is also enforced during
token validation: when set, id_tokens from public providers **not** listed are
rejected with `401 OBP-20218`, even if their JWKS URL is configured in
`oauth2.jwk_set.url`.

| `oauth2.oidc_provider` value | Effect on public-IdP tokens |
|---|---|
| (props missing) | no restriction (backward compatible) |
| `` (empty string) | all providers enabled |
| `none` | all public IdPs rejected |
| `obp-oidc,keycloak,google` | only listed public IdPs accepted |

### 2. Audience allowlist — `oauth2.<provider>.allowed_audiences`

```
oauth2.google.allowed_audiences=explorer-client-id.apps.googleusercontent.com,manager-client-id.apps.googleusercontent.com
oauth2.yahoo.allowed_audiences=your-yahoo-client-id
oauth2.microsoft.allowed_audiences=your-azure-application-id
```

Binds accepted tokens to your own OAuth client(s): the token's `aud` claim must
contain one of the listed client IDs, otherwise `401 OBP-20217`. Multiple
applications (Explorer, Manager, Portal) are listed comma-separated. Entries
are trimmed; matching is case-sensitive (OAuth2 client IDs are case-sensitive).
Unset or empty = no restriction, but a warning is logged at boot.

### Boot-time configuration warnings

Misconfigurations are surfaced at startup (`OAuth2Login.logConfigWarnings()`,
called from Boot):

- An **enabled** public provider without an audience allowlist:
  *"id_tokens issued to ANY &lt;provider&gt; OAuth client will be accepted"*.
- A JWKS URL configured for a provider that is **not enabled** in
  `oauth2.oidc_provider`: flagged as a likely leftover — its tokens will be
  rejected with OBP-20218.

## Operator-controlled IdPs (Keycloak, OBP-OIDC, Hydra)

These run under the API operator's control: only applications the operator
registers can obtain tokens at all, so the "any stranger's app gets valid
tokens" problem does not exist. They are exempt from audience-allowlist and
enablement enforcement (Hydra additionally uses token introspection rather
than JWT validation).

## Client requirement (all providers)

The Bearer value sent to OBP must be a **JWT**. Public providers like Google
issue *opaque* access tokens (`ya29...`) — clients must send the **id_token**
instead. JWT access tokens (Keycloak, OBP-OIDC) are sent as-is. The provider's
JWKS URL must be present in `oauth2.jwk_set.url` for signature validation.

## Related error codes

| Code | Meaning |
|---|---|
| `OBP-20214` | Bearer token not recognised (e.g. not a JWT, or no matching provider) |
| `OBP-20217` | Audience (`aud`) claim not in the provider's allowlist |
| `OBP-20218` | Provider not enabled in `oauth2.oidc_provider` |

## See also

- `obp-api/src/main/resources/props/sample.props.template` — the
  `oauth2.oidc_provider` and `oauth2.*.allowed_audiences` sections
- `obp-api/src/main/scala/code/api/OAuth2.scala` — `OAuth2Util.validateAudience`
  and `OAuth2Util.validateProviderEnabled`
- `obp-api/src/test/scala/code/api/OAuth2AudienceValidationTest.scala` —
  executable specification of both checks
