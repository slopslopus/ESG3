INSERT INTO employees (full_name, department, role) VALUES
    ('Ivanov Ivan Ivanovich', 'Security', 'SECURITY'),
    ('Petrova Anna Sergeevna', 'HR', 'HR'),
    ('Sidorov Petr Nikolaevich', 'IT', 'IT'),
    ('Kuznetsova Olga Pavlovna', 'Finance', 'FINANCE'),
    ('Smirnov Alexey Dmitrievich', 'Sales', 'SALES');

INSERT INTO visitors (full_name, document_type) VALUES
    ('Fedorov Fedor Fedorovich', 'PASSPORT'),
    ('Mikhailova Maria Ivanovna', 'PASSPORT'),
    ('Novikov Nikolay Petrovich', 'DRIVER_LICENSE'),
    ('Egorova Elena Andreevna', 'PASSPORT'),
    ('Pavlov Pavel Pavlovich', 'MILITARY_ID');

INSERT INTO visit_requests (visitor_id, host_id, purpose, visit_date, time_from, time_to, status, pass_number, arrived_at, left_at) VALUES
    (1, 1, 'Contract signing', CURRENT_DATE - 4, '10:00', '11:00', 'COMPLETED', 'PASS-001', CURRENT_DATE - 4 + TIME '10:02', CURRENT_DATE - 4 + TIME '10:55'),
    (2, 2, 'Job interview', CURRENT_DATE - 3, '14:00', '15:00', 'COMPLETED', 'PASS-002', CURRENT_DATE - 3 + TIME '13:58', CURRENT_DATE - 3 + TIME '15:05'),
    (3, 3, 'Server maintenance', CURRENT_DATE - 2, '09:00', '12:00', 'COMPLETED', 'PASS-003', CURRENT_DATE - 2 + TIME '09:00', CURRENT_DATE - 2 + TIME '11:50');

INSERT INTO visit_requests (visitor_id, host_id, purpose, visit_date, time_from, time_to, status, pass_number, arrived_at) VALUES
    (4, 4, 'Audit meeting', CURRENT_DATE, '11:00', '12:30', 'ISSUED', 'PASS-004', CURRENT_DATE + TIME '10:58'),
    (5, 5, 'Product demo', CURRENT_DATE, '15:00', '16:00', 'ISSUED', 'PASS-005', CURRENT_DATE + TIME '14:55');

INSERT INTO visit_requests (visitor_id, host_id, purpose, visit_date, time_from, time_to, status) VALUES
    (1, 2, 'HR documents', CURRENT_DATE + 3, '10:00', '10:30', 'SUBMITTED'),
    (2, 3, 'Software presentation', CURRENT_DATE + 4, '12:00', '13:00', 'SUBMITTED'),
    (3, 4, 'Budget approval', CURRENT_DATE + 5, '09:30', '10:30', 'SUBMITTED'),
    (4, 5, 'Partnership talks', CURRENT_DATE + 6, '14:00', '15:30', 'SUBMITTED'),
    (5, 1, 'Delivery of equipment', CURRENT_DATE + 7, '09:00', '10:00', 'SUBMITTED');

INSERT INTO visit_requests (visitor_id, host_id, purpose, visit_date, time_from, time_to, status) VALUES
    (1, 3, 'Cancelled meeting', CURRENT_DATE + 1, '16:00', '17:00', 'CANCELLED'),
    (2, 4, 'Rescheduled visit', CURRENT_DATE + 2, '11:00', '12:00', 'CANCELLED');
