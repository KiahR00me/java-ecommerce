alter table products
    add column if not exists image_url varchar(1000);

create index if not exists idx_products_category_active on products(category_id, active);
create index if not exists idx_products_name on products(name);