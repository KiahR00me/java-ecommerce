insert into categories (id, name, description)
values
    (1001, 'Electronics', 'Laptops, phones, and accessories'),
    (1002, 'Home Office', 'Remote work essentials'),
    (1003, 'Fitness', 'Home workout gear')
on conflict (id) do nothing;

insert into products (id, name, description, price, stock_quantity, active, category_id)
values
    (2001, 'Mechanical Keyboard Pro', 'Hot-swappable keyboard for developers', 129.00, 120, true, 1001),
    (2002, 'UltraWide 34 Monitor', '3440x1440 monitor for productivity', 649.00, 35, true, 1002),
    (2003, 'Adjustable Standing Desk', 'Electric standing desk with memory presets', 499.00, 22, true, 1002),
    (2004, 'Smart Jump Rope', 'Bluetooth jump rope with analytics', 89.00, 80, true, 1003),
    (2005, 'Noise-Cancelling Headphones', 'Over-ear ANC headphones for focus', 299.00, 45, true, 1001)
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
