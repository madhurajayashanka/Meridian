# 09 — Auth Flow

Registration, login, token refresh, and session lifecycle.

---

## Diagram: Registration Flow

```
Browser                    Spring Boot API              PostgreSQL
   │                             │                           │
   │──mutation register()────────▶                           │
   │  {email, password, name}    │ validate email format     │
   │                             │ validate password ≥8 chars│
   │                             │ check email not taken     │
   │                             │──SELECT users WHERE email─▶
   │                             │◀──(empty)─────────────────│
   │                             │ bcrypt(password, cost=12) │
   │                             │──INSERT users─────────────▶
   │                             │ generateAccessToken (15m) │
   │                             │ generateRefreshToken (7d) │
   │                             │──INSERT refresh_tokens────▶
   │◀──{accessToken,             │  (stores SHA-256 hash)    │
   │    refreshToken, user}──────│                           │
   │                             │                           │
   │ store in localStorage       │                           │
   │ navigate to /dashboard      │                           │
```

---

## Diagram: Login Flow

```
Browser                    Spring Boot API              PostgreSQL
   │                             │                           │
   │──mutation login()───────────▶                           │
   │  {email, password}          │──SELECT user by email─────▶
   │                             │◀──user row────────────────│
   │                             │ account locked?           │
   │                             │  YES → 401                │
   │                             │ bcrypt.verify(password)   │
   │                             │  FAIL → increment         │
   │                             │         failed_attempts   │
   │                             │         → 401             │
   │                             │  PASS → reset attempts    │
   │                             │ generateAccessToken (15m) │
   │                             │ generateRefreshToken (7d) │
   │                             │──INSERT refresh_tokens────▶
   │◀──{accessToken,             │                           │
   │    refreshToken, user}──────│                           │
```

---

## Diagram: Token Refresh Flow

```
Browser                    Spring Boot API              PostgreSQL
   │                             │                           │
   │ (accessToken expires soon)  │                           │
   │──mutation refreshToken()────▶                           │
   │  {refreshToken}             │ validate JWT structure    │
   │                             │ check token type=refresh  │
   │                             │ extract userId            │
   │                             │──SELECT refresh_tokens────▶
   │                             │  WHERE hash=SHA256(token) │
   │                             │  AND expires_at > NOW()   │
   │                             │  AND revoked_at IS NULL   │
   │                             │◀──token row───────────────│
   │                             │ revoke old token          │
   │                             │──UPDATE revoked_at=NOW()──▶
   │                             │ generate new pair         │
   │                             │──INSERT new refresh_token─▶
   │◀──{new accessToken,         │                           │
   │    new refreshToken}────────│                           │
   │                             │                           │
   │ update localStorage         │                           │
```

---

## Diagram: JWT Structure

```
Access Token (RS256, 15 min):
  Header: { alg: "RS256", typ: "JWT" }
  Payload: {
    sub: "user-uuid",
    email: "user@example.com",
    type: "access",
    iat: 1711700000,
    exp: 1711700900   ← 15 min
  }
  Signature: RSA-SHA256(header.payload, privateKey)

Refresh Token (RS256, 7 days):
  Payload: {
    sub: "user-uuid",
    type: "refresh",
    iat: 1711700000,
    exp: 1712304800   ← 7 days
  }
  DB: stores SHA-256(refreshToken) — never the raw token
```

---

## Diagram: Account Lockout

```
Failed login attempt
    │
    ▼
failed_login_attempts++
    │
    ▼
failed_login_attempts >= 5?
    │ YES → locked_until = NOW() + 15 minutes
    │       return 401 "Account is locked"
    │ NO  → return 401 "Invalid credentials"

Successful login:
    → reset failed_login_attempts = 0
    → clear locked_until
```

---

## Diagram: Service-to-Service Auth

```
Spring Boot → FastAPI (internal calls):
  Header: X-Service-Key: <INTERNAL_API_KEY>
  Dev: key not set → skip check (warning logged)
  Prod: key required → 401 if missing or wrong

FastAPI → Spring Boot (webhook callbacks):
  Header: X-Webhook-Signature: sha256=<HMAC-SHA256(body, WEBHOOK_SECRET)>
  Spring: verifies HMAC with constant-time comparison
  Dev: secret not set → skip check (warning logged)
  Prod: secret required → 401 if missing or invalid

Browser → FastAPI SSE:
  Query param: ?token=<accessToken>
  FastAPI: validates RS256 JWT using JWT_PUBLIC_KEY
  Dev: key not set → accept any non-empty token (warning logged)
```
