# Onboarding Google OIDC (OBP-API — backend side)

How to configure OBP-API to accept **Google** id_tokens so that clients like
API Explorer II / API Manager II can offer "Sign in with Google". This is the
backend half; the client/BFF half (running the actual Google login) is covered
by `docs/GOOGLE_OIDC_ONBOARDING.md` in the API-Explorer-II repo.

For the **why** behind these controls (public vs. operator-controlled IdPs,
the threat model), read `../OAUTH2_IDENTITY_PROVIDERS.md` first — this file is the
step-by-step runbook.

---

## OBP-API's role

OBP-API is a pure OAuth2 **resource server**. It does **not** run the OIDC
login; the client/BFF does that against Google and then calls OBP with
`Authorization: Bearer <google-id-token>`. OBP validates that JWT and resolves
(or auto-creates) the user + consumer.

The validation path is `OAuth2Login.getUser` / `getUserFuture` →
`Google.applyIdTokenRules` in
`obp-api/src/main/scala/code/api/OAuth2.scala`. The `Google` provider object
is hard-coded with:
- issuer / discovery: `https://accounts.google.com/.well-known/openid-configuration`
- `oidcProviderName = "google"` (enablement enforcement)
- `allowedAudiencesPropsName = "oauth2.google.allowed_audiences"` (audience enforcement)

So onboarding is **configuration only** — no code change.

## Google Cloud Console onboarding (getting a client ID)

Before configuring OBP-API you need a Google OAuth **client ID** — that is the
value that goes into `oauth2.google.allowed_audiences`. It is created in the
Google Cloud Console, once per client app (Explorer, Manager, ...):

1. **Create / pick a project** — <https://console.cloud.google.com/>, project
   selector → *New Project* (or reuse an existing one). No Google APIs need to
   be enabled for plain OIDC sign-in.
2. **Configure the OAuth consent screen** — *APIs & Services → OAuth consent
   screen* (branding):
   - User type: **External** (any Google account) or **Internal** (only your
     Google Workspace org — a cheap allowlist if all users share one org).
   - App name, support email, developer contact — what users see on the
     Google login prompt.
   - Scopes: `openid`, `email`, `profile` are enough; they are non-sensitive,
     so **no Google verification review is needed**.
   - While *Publishing status* is **Testing**, only explicitly added test
     users can log in; switch to **In production** to open it up.
3. **Create the OAuth client** — *APIs & Services → Credentials → Create
   Credentials → OAuth client ID*:
   - Application type: **Web application**.
   - **Authorized redirect URIs**: the *client/BFF's* callback URL (e.g. the
     Explorer II BFF's `/auth/google/callback`) — **not** an OBP-API URL.
     OBP-API never appears in the Google flow; it only receives the resulting
     id_token as a Bearer. The exact URI is documented in the client repo
     (API-Explorer-II `docs/GOOGLE_OIDC_ONBOARDING.md`).
   - **Authorized JavaScript origins**: the client's origin(s), if it uses a
     browser-side flow.
4. **Copy the client ID** — it looks like
   `1234567890-abc123.apps.googleusercontent.com`. This is the token's
   `aud`/`azp`, and the value you list in `oauth2.google.allowed_audiences`
   below. The **client secret** stays with the client/BFF; OBP-API never
   needs it (signature verification uses Google's public JWKS).

Repeat step 3 for each app that should sign in via Google, and list every
resulting client ID in the audience allowlist. One client ID per app — see
`../OAUTH2_IDENTITY_PROVIDERS.md` for why sharing one ID across apps weakens the
`(azp, iss)` consumer granularity.

## Required props

Edit your `props` file (in `obp-api/src/main/resources/props/sample.props.template`,
search for the `OAuth2 Provider Selection` section, or the `oauth2.oidc_provider` prop):

```properties
# 1. OAuth2 must be enabled (default true)
allow_oauth2_login=true

# 2. Advertise + enable Google. This list is BOTH what /well-known advertises
#    to clients AND the registry of enabled public IdPs for token validation.
oauth2.oidc_provider=obp-oidc,keycloak,google

# 3. Google's JWKS so OBP can verify the id_token signature.
#    Comma-separated; keep any existing entries.
oauth2.jwk_set.url=http://localhost:9000/obp-oidc/jwks,https://www.googleapis.com/oauth2/v3/certs

# 4. Audience allowlist — bind accepted tokens to YOUR app(s)' client IDs.
#    List every app that logs in via Google (Explorer, Manager, ...).
oauth2.google.allowed_audiences=explorer-client-id.apps.googleusercontent.com,manager-client-id.apps.googleusercontent.com
```

All four matter. Missing any one produces a specific failure (table below).

## What each control does

| Props | Enforced in | Failure if violated |
|---|---|---|
| `allow_oauth2_login` | `OAuth2Login.getUser*` | `OBP-20203` (OAuth2 not allowed) |
| `oauth2.oidc_provider` contains `google` | `validateProviderEnabled` (checked **before** signature, so a disabled provider needs no JWKS fetch) | `OBP-20218` provider not enabled |
| `oauth2.jwk_set.url` contains Google JWKS | `validateIdToken` (signature) | `OBP-20208` issuer/JWKS mismatch, or token not recognised |
| `oauth2.google.allowed_audiences` contains the app's client ID | `validateAudience` (token `aud` must match) | `OBP-20217` audience not allowed |

Validation order in `Google.applyIdTokenRules`: **provider-enabled →
signature (id_token) → audience → get/create user → get/create consumer**.

## Behaviour notes

- **`oauth2.oidc_provider` semantics**: props missing = no restriction
  (backward compatible); empty string = all providers enabled; `none` = none.
  Only when the props lists explicit values is a public IdP not in the list
  rejected with `OBP-20218`. The `/well-known` endpoint
  (`Http4s510.scala`, `case GET -> ... / "well-known"`) advertises the same
  set, mapping `google` → Google's discovery URL.
- **Audience allowlist**: a multi-valued `aud` passes if **any** value is
  allowed; matching is case-sensitive. Unset/empty = no restriction, but a
  boot warning is logged. Public providers (Google/Yahoo/Microsoft) issue
  id_tokens to *any* registered app, so on a shared/production instance this
  allowlist is the control that stops a token minted for another app (or
  another OBP environment) from authenticating here.
- **User mapping** (`getOrCreateResourceUser`): `iss` → `provider`,
  `sub` → `providerId`; `given_name`/`email` populate the new user.
- **Consumer mapping** (`getOrCreateConsumer`): keyed on `<sub, azp>` — found
  → reused, not found → a new Consumer is auto-created from
  `azp`/`sub`/`iss`/`name`/`email`. (`consumers_enabled_by_default` controls
  whether it's enabled on creation.)
- **Bearer must be the id_token, not the access token.** Google access tokens
  (`ya29...`) are opaque, not JWTs, so OBP cannot validate them — the client
  must send the **id_token**. (The Explorer BFF already does this.)

## Boot-time warnings (sanity checks)

`OAuth2Login.logConfigWarnings()` (called from Boot) surfaces two
misconfigurations for Google:

- Google **enabled** but `oauth2.google.allowed_audiences` empty → *"id_tokens
  issued to ANY Google OAuth client will be accepted"*.
- Google JWKS present in `oauth2.jwk_set.url` but `google` **not** in
  `oauth2.oidc_provider` → leftover JWKS URL; its tokens will be rejected
  (`OBP-20218`).

Check the startup log after configuring.

## Verify

```bash
# 1. /well-known advertises google
curl -s http://localhost:8080/obp/v5.1.0/well-known | jq '.well_known_uris'

# 2. End-to-end: obtain a Google id_token via the client, then
curl -s http://localhost:8080/obp/v5.1.0/users/current \
  -H "Authorization: Bearer <google-id-token>"
# → 200 with the resolved/auto-created user; not OBP-20214/17/18.
```

## Troubleshooting

| Error | Meaning | Fix |
|---|---|---|
| `OBP-20218` | issuer is a public IdP not enabled | add `google` to `oauth2.oidc_provider` |
| `OBP-20217` | token `aud` not allowed | add the client ID to `oauth2.google.allowed_audiences` |
| `OBP-20208` / not recognised | signature can't be verified | add Google JWKS to `oauth2.jwk_set.url`; confirm the Bearer is the **id_token** (3-part JWT), not `ya29...` |
| `OBP-20203` | OAuth2 disabled | `allow_oauth2_login=true` |

---

**Related:** `../OAUTH2_IDENTITY_PROVIDERS.md` (trust model & security rationale),
`obp-api/src/main/scala/code/api/OAuth2.scala` (`object Google`),
`obp-api/src/main/resources/props/sample.props.template` (OAuth2 section),
API-Explorer-II `docs/GOOGLE_OIDC_ONBOARDING.md` (client/BFF side).
