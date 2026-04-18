-- Payment tracking fields for order-payment API integration.
alter table customer_orders add column if not exists payment_intent_id varchar(255);
alter table customer_orders add column if not exists payment_method varchar(60);
alter table customer_orders add column if not exists payment_status varchar(40) not null default 'UNPAID';
alter table customer_orders add column if not exists paid_at timestamptz;

create index if not exists idx_orders_payment_intent
    on customer_orders(payment_intent_id)
    where payment_intent_id is not null;

create index if not exists idx_orders_customer_status
    on customer_orders(customer_id, status);

-- Product audit timestamps for sync with product-sourcing APIs.
alter table products add column if not exists created_at timestamptz not null default now();
alter table products add column if not exists updated_at timestamptz not null default now();
