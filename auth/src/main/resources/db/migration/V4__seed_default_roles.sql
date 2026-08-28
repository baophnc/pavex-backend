INSERT INTO auth.role (
    id,
    code,
    name,
    description,
    is_system
)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'CUSTOMER',
    'Customer',
    'Default Pavex customer account',
    TRUE
)
ON CONFLICT (code) DO NOTHING;
