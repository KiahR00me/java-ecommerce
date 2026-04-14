insert into categories (id, name, description)
values
    (1001, 'Electronics', 'Laptops, phones, and accessories'),
    (1002, 'Home Office', 'Remote work essentials'),
    (1003, 'Fitness', 'Home workout gear')
on conflict (id) do nothing;

insert into products (id, name, description, image_url, price, stock_quantity, active, category_id)
values
    (2001, 'Mechanical Keyboard Pro', 'Hot-swappable keyboard for developers', 'https://images.unsplash.com/photo-1511467687858-23d96c32e4ae?auto=format&fit=crop&w=1200&q=80', 129.00, 120, true, 1001),
    (2002, 'UltraWide 34 Monitor', '3440x1440 monitor for productivity', 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=1200&q=80', 649.00, 35, true, 1002),
    (2003, 'Adjustable Standing Desk', 'Electric standing desk with memory presets', 'https://images.unsplash.com/photo-1598300042247-d088f8ab3a91?auto=format&fit=crop&w=1200&q=80', 499.00, 22, true, 1002),
    (2004, 'Smart Jump Rope', 'Bluetooth jump rope with analytics', 'https://images.unsplash.com/photo-1599058918144-1ffabb6ab9a0?auto=format&fit=crop&w=1200&q=80', 89.00, 80, true, 1003),
    (2005, 'Noise-Cancelling Headphones', 'Over-ear ANC headphones for focus', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=1200&q=80', 299.00, 45, true, 1001)
on conflict (id) do nothing;

insert into customers (id, email, full_name)
values
    (3001, 'alex.doe@example.com', 'Alex Doe'),
    (3002, 'sam.lee@example.com', 'Sam Lee'),
    (3003, 'maria.chen@example.com', 'Maria Chen')
on conflict (id) do nothing;

select setval('categories_id_seq', greatest((select max(id) from categories), 1));
select setval('products_id_seq', greatest((select max(id) from products), 1));
select setval('customers_id_seq', greatest((select max(id) from customers), 1));
