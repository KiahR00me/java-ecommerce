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

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev-mail-sandbox"
```

The profile group `dev-mail-sandbox` expands to:
- `dev-fast`
- `mail-sandbox`

## 3) Trigger verification email flow

1. Create customer as admin:
```http
POST /api/customers
Authorization: Basic admin/admin123
```
2. Send verification email as admin:
```http
POST /api/customers/{id}/send-verification
Authorization: Basic admin/admin123
```
3. Check Mailpit UI and copy token from email body.
4. Verify account:
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
