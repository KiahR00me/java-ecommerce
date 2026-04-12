create table if not exists categories (
    id bigserial primary key,
    name varchar(120) not null unique,
    description varchar(500)
);

create table if not exists products (
    id bigserial primary key,
    name varchar(160) not null,
    description varchar(1000),
    price numeric(12,2) not null,
    stock_quantity integer not null,
    active boolean not null default true,
    category_id bigint not null references categories(id)
);

create table if not exists customers (
    id bigserial primary key,
    email varchar(160) not null unique,
    full_name varchar(160) not null
);

create table if not exists carts (
    id bigserial primary key,
    customer_id bigint not null unique references customers(id),
    updated_at timestamptz not null
);

create table if not exists cart_items (
    id bigserial primary key,
    cart_id bigint not null references carts(id) on delete cascade,
    product_id bigint not null references products(id),
    quantity integer not null
);

create table if not exists customer_orders (
    id bigserial primary key,
    customer_id bigint not null references customers(id),
    total_amount numeric(12,2) not null,
    status varchar(40) not null,
    created_at timestamptz not null
);

create table if not exists order_items (
    id bigserial primary key,
    order_id bigint not null references customer_orders(id) on delete cascade,
    product_id bigint not null references products(id),
    quantity integer not null,
    unit_price numeric(12,2) not null
);
