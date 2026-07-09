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

## Google client ID policy: how many OAuth clients, and per what?

`oauth2.google.allowed_audiences` (and the Yahoo/Microsoft equivalents) is a
flat, instance-wide, comma-separated list. That flexibility raises an
operational question: should the operator register **one** Google OAuth client
and share it, or one **per app**, **per environment**, **per tenant**? The
allowlist mechanism supports all of these; the security and operational
properties differ substantially.

One implementation fact shapes the whole comparison: for public-IdP logins,
OBP auto-creates Consumers keyed by the **`<sub, azp>` pair**
(`OAuth2.scala`, `getOrCreateConsumer`). It does *not* create one Consumer
per application — it creates one Consumer per **user per Google client**, all
named "OpenID Connect". Everything OBP hangs off a Consumer (rate limits,
metrics, the enable/disable switch) therefore has per-user-per-client
granularity today. The client ID policy determines how much signal the `azp`
axis carries.

### The four policies

| | 1. Per instance (shared) | 2. Per app | 3. Per app × environment | 4. Per tenant |
|---|---|---|---|---|
| Google clients needed | 1 | 1 per app (Explorer, Manager, Portal, …) | apps × environments | apps × environments × tenants |
| `allowed_audiences` entries | 1 | one per app | one per app (each instance lists only its own environment's IDs) | per-tenant scoping **not expressible** in a flat props list |
| Revoke a single app | ✗ — removing the entry kills all apps | ✓ — remove its entry | ✓ | ✓ |
| Distinguish apps in Consumers / metrics | ✗ — same `azp` everywhere; per-user Consumers merge across apps | ✓ — distinct `azp` per app | ✓ | ✓ |
| Cross-app token replay (token minted for app A used as app B) | possible by construction — all apps share one audience | prevented at the app boundary | prevented | prevented |
| Cross-**environment** replay (test token → prod) | **open** if the same client ID is reused across environments | **open** if IDs are reused across environments | **closed** — prod allowlist never contains a test client ID | closed |
| Blast radius of a leaked client secret | every app on the instance | one app | one app in one environment | one app in one environment for one tenant |
| Google-side consent screen / redirect URIs | one shared consent screen; redirect URI list grows unbounded | per-app branding and URIs | per-app, per-env URIs (no `localhost` on the prod client) | as per app × env |
| Operational overhead | minimal | moderate | moderate (naming convention keeps it sane) | high; flat props list is the limiting factor |

### Implications per policy

**1. Per instance (one shared client).** Simplest to set up, and acceptable
only for throwaway sandboxes. Because every app presents the same `aud`/`azp`,
the audience check degenerates to "is it ours at all": an id_token obtained by
logging into the Portal is equally valid when replayed as if it came from API
Manager, there is no per-app kill switch, and the `<sub, azp>` Consumer
dedup merges all apps into a single Consumer per user — so per-Consumer rate
limits and metrics cannot tell apps apart. A leaked client secret (for
confidential flows) burns every app at once.

**2. Per app.** The intended reading of the example in the audience-allowlist
section above. Each ecosystem app (Explorer, Manager, Portal) gets its own
Google OAuth client; all are listed comma-separated. Revoking one app is
deleting one entry; distinct `azp` values yield distinct auto-created
Consumers, so per-app signal exists in metrics; each app controls its own
consent-screen branding and redirect URIs. Remaining weakness: the list is
still flat — every listed client is equally trusted for the *entire* API
surface (there is no "tokens from the Portal client may log in but not reach
admin endpoints" distinction; that would require binding client IDs to
Consumer-level policy, see "Beyond the flat list" below).

**3. Per app × environment (recommended).** Policy 2 plus the rule: **a
Google client ID is never reused across environments.** This is the axis the
replay warning at the top of this document is about — signature, issuer,
expiry and even audience validation all pass when a token minted on a test
instance is replayed against production *if both list the same client ID*.
Separate clients per environment (naming convention helps:
`obp-<env>-<app>`, e.g. `obp-prod-explorer`, `obp-test-explorer`) close this
cheaply, and also keep `localhost` redirect URIs off the production client.
Cost is bookkeeping only; the allowlist per instance stays exactly as small
as in policy 2.

**4. Per tenant.** Only relevant if a single OBP instance serves multiple
tenants/banks. The current mechanism cannot express it: the props list is
instance-global, so a client ID listed for tenant A also authenticates
against tenant B. Multi-tenant deployments should either run one instance per
tenant (reducing this to policy 3) or wait for a Consumer-bound allowlist
(below).

### Rotation

Because the allowlist holds client **IDs** (not secrets) and is
comma-separated, rotation needs no downtime and no code: add the new client
ID alongside the old one, migrate the app to the new client, then remove the
old entry. The overlap window is as long as you need. (A props change
requires an instance restart to take effect — plan the two edits around
normal restart cycles.)

### Beyond the flat list (future direction, not implemented)

The natural end-state is binding accepted client IDs to **registered
Consumers**: the operator pre-registers each Google client ID as an OBP
Consumer (storing the `azp`), and validation becomes "does an enabled
Consumer with this `azp` exist?". That would make the allowlist manageable at
runtime via API Manager instead of props + restart, give per-app rate
limits/disable switches through the existing Consumer machinery, express
per-tenant scoping, and replace the per-user `<sub, azp>` Consumer
proliferation with the one-Consumer-per-app model used by every other auth
path (the Hydra introspection path already resolves consumers by client ID
this way). Until then, the props list is the only control, and policy 3 above
is the recommended way to use it.

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
