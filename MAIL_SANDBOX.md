# Local Mail Sandbox (Mailpit)

This project supports a dedicated local mail profile for email verification testing.

## 1) Start Mailpit

```powershell
docker compose up -d mailpit
```

Open Mailpit UI:
- http://localhost:8025

SMTP endpoint for the app:
- host: localhost
- port: 1025

## 2) Run the app with sandbox profile

Set security environment variables first (or copy values from `.env.example`):

```powershell
$env:APP_SECURITY_ADMIN_USERNAME="admin"
$env:APP_SECURITY_ADMIN_PASSWORD="change-me-admin"
$env:APP_SECURITY_CUSTOMER_USERNAME="customer@example.com"
$env:APP_SECURITY_CUSTOMER_PASSWORD="change-me-customer"
$env:APP_SECURITY_JWT_SECRET="replace-with-a-strong-32-char-minimum-secret"
```

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev-mail-sandbox"
```

The profile group `dev-mail-sandbox` expands to:
- `dev-fast`
- `mail-sandbox`

## 3) Trigger verification email flow

1. Login as admin and capture bearer token:
```http
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"<your-admin-password>"}
```

2. Create customer as admin:
```http
POST /api/customers
Authorization: Bearer <accessToken>
```

3. Send verification email as admin:
```http
POST /api/customers/{id}/send-verification
Authorization: Bearer <accessToken>
```
4. Check Mailpit UI and copy token from email body.
5. Verify account:
```http
POST /api/customers/verify
```

## 4) Run the one-command demo script

```powershell
.\scripts\demo-email-verification-flow.ps1 -BaseUrl "http://localhost:8080"
```

Optional parameters:
- `-AdminCredential`
- `-Email`
- `-FullName`

## Notes
- In profiles without `mail-sandbox`, `app.email.enabled=false` and verification tokens are logged instead of sent.
- You can run Postgres and Mailpit together:
```powershell
docker compose up -d postgres mailpit
```
