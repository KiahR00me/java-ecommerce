alter table customers
    add column if not exists email_verified boolean not null default false;

alter table customers
    add column if not exists email_verification_token varchar(120);

alter table customers
    add column if not exists email_verification_sent_at timestamptz;

create unique index if not exists ux_customers_email_verification_token
    on customers (email_verification_token)
    where email_verification_token is not null;

-- Keep the existing demo account usable for Stage 1 test flows.
update customers
set email_verified = true,
    email_verification_token = null,
    email_verification_sent_at = null
where lower(email) = 'customer@example.com';
