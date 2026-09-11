-- =====================================================================
-- HRGenius — development seed data (also runs on real Oracle)
-- BCrypt hashes below are precomputed offline; they are documented
-- development-only credentials and are worthless outside this project.
--   admin@hrgenius.local    / Admin@123
--   hr@hrgenius.local       / Hr@12345
--   manager@hrgenius.local  / Manager@123
--   employee@hrgenius.local / Employee@123
-- =====================================================================

-- Demo users (each hash matches its documented dev password — see comment above)
INSERT INTO USERS (EMAIL, PASSWORD_HASH, FULL_NAME, ROLE, ENABLED) VALUES
  ('admin@hrgenius.local',    '$2a$10$GDKY0xVqGt5k6Fellbaz3OktwSWmDJPqs4tfBBqYZdmKF6M1lV6yK', 'System Administrator', 'ADMIN',    1);
INSERT INTO USERS (EMAIL, PASSWORD_HASH, FULL_NAME, ROLE, ENABLED) VALUES
  ('hr@hrgenius.local',       '$2a$10$ZaYtC.4t8FEVZ7CXI/kvFOG1hy5.p847f/M1s2tHYGUHWqsS5HhA.', 'Priya Sharma',         'HR',       1);
INSERT INTO USERS (EMAIL, PASSWORD_HASH, FULL_NAME, ROLE, ENABLED) VALUES
  ('manager@hrgenius.local',  '$2a$10$KEALPWnWLGGdYnZ.5hlWB.OnZE50gJcrz0OucriJmkibPUDZDnonu', 'Rahul Verma',          'MANAGER',  1);
INSERT INTO USERS (EMAIL, PASSWORD_HASH, FULL_NAME, ROLE, ENABLED) VALUES
  ('employee@hrgenius.local', '$2a$10$rLtrRPjKa7Jkyfu0Qr7WO.SjWmS9DXRa9E.cdN3QRmwx9V5c3TH5C', 'Anita Desai',          'EMPLOYEE', 1);

-- Departments
INSERT INTO DEPARTMENTS (NAME, DESCRIPTION) VALUES
  ('Engineering',      'Product development and platform teams');
INSERT INTO DEPARTMENTS (NAME, DESCRIPTION) VALUES
  ('Human Resources',  'People operations and recruitment');
INSERT INTO DEPARTMENTS (NAME, DESCRIPTION) VALUES
  ('Finance',          'Accounting, payroll and budgeting');
INSERT INTO DEPARTMENTS (NAME, DESCRIPTION) VALUES
  ('Sales',            'Revenue and customer accounts');

-- Designations
INSERT INTO DESIGNATIONS (TITLE, DESCRIPTION, DEPARTMENT_ID) VALUES
  ('Software Engineer',      'Builds and maintains product features',        1);
INSERT INTO DESIGNATIONS (TITLE, DESCRIPTION, DEPARTMENT_ID) VALUES
  ('Senior Software Engineer', 'Leads feature delivery and mentoring',       1);
INSERT INTO DESIGNATIONS (TITLE, DESCRIPTION, DEPARTMENT_ID) VALUES
  ('HR Executive',           'Handles recruitment and onboarding',           2);
INSERT INTO DESIGNATIONS (TITLE, DESCRIPTION, DEPARTMENT_ID) VALUES
  ('Accountant',             'Books, payroll processing, compliance',        3);
INSERT INTO DESIGNATIONS (TITLE, DESCRIPTION, DEPARTMENT_ID) VALUES
  ('Sales Executive',        'Manages client accounts and pipeline',         4);

-- Employees (IDs 1-6)
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP001', 'Rahul', 'Verma', 'manager@hrgenius.local',  '+91-9810011122', DATE '2021-03-15', 'FULL_TIME', 'ACTIVE', 1, 2);
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP002', 'Anita', 'Desai', 'employee@hrgenius.local', '+91-9810033344', DATE '2023-07-10', 'FULL_TIME', 'ACTIVE', 1, 1);
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP003', 'Vikram', 'Singh', 'vikram.singh@hrgenius.local',  '+91-9820055566', DATE '2022-01-03', 'FULL_TIME', 'ACTIVE', 1, 1);
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP004', 'Sneha', 'Patil', 'sneha.patil@hrgenius.local',   '+91-9830077788', DATE '2022-09-19', 'FULL_TIME', 'ACTIVE', 3, 4);
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP005', 'Arjun', 'Mehta', 'arjun.mehta@hrgenius.local',   '+91-9840099900', DATE '2024-02-05', 'INTERN',   'ACTIVE', 4, 5);
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP006', 'Divya', 'Nair',  'divya.nair@hrgenius.local',    '+91-9850022211', DATE '2020-11-23', 'FULL_TIME', 'ACTIVE', 2, 3);

-- Wire relationships now that IDs are known
UPDATE EMPLOYEES SET MANAGER_ID = 1 WHERE ID IN (2, 3, 5);
UPDATE DEPARTMENTS SET MANAGER_ID = 1 WHERE ID = 1;
UPDATE DEPARTMENTS SET MANAGER_ID = 4 WHERE ID = 3;
UPDATE DEPARTMENTS SET MANAGER_ID = 6 WHERE ID = 2;

-- Leave types
INSERT INTO LEAVE_TYPES (NAME, DESCRIPTION, YEARLY_LIMIT) VALUES
  ('CASUAL_LEAVE',  'Short personal leave', 12);
INSERT INTO LEAVE_TYPES (NAME, DESCRIPTION, YEARLY_LIMIT) VALUES
  ('SICK_LEAVE',    'Medical leave with certificate', 10);
INSERT INTO LEAVE_TYPES (NAME, DESCRIPTION, YEARLY_LIMIT) VALUES
  ('EARNED_LEAVE',  'Accrued paid leave', 15);

-- Open jobs (recruitment pipeline gets built in Phase 5)
INSERT INTO JOBS (TITLE, DESCRIPTION, DEPARTMENT_ID, LOCATION, EMPLOYMENT_TYPE, SALARY_RANGE, STATUS, POSTED_DATE)
VALUES ('Backend Developer', 'Spring Boot microservices and Oracle SQL', 1, 'Pune', 'FULL_TIME', '8-14 LPA', 'OPEN', CURRENT_DATE);
INSERT INTO JOBS (TITLE, DESCRIPTION, DEPARTMENT_ID, LOCATION, EMPLOYMENT_TYPE, SALARY_RANGE, STATUS, POSTED_DATE)
VALUES ('HR Generalist', 'Recruitment, onboarding and employee engagement', 2, 'Remote', 'FULL_TIME', '5-8 LPA', 'OPEN', CURRENT_DATE);

-- A first notification for the admin
INSERT INTO NOTIFICATIONS (USER_ID, TITLE, MESSAGE, TYPE, READ_FLAG)
VALUES (1, 'Welcome to HRGenius', 'The workspace is ready. Start by exploring the dashboard.', 'SYSTEM', 0);
