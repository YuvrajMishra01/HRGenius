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
-- =====================================================================
-- Candidates / applications / interviews (hiring pipeline demo data)
-- =====================================================================
INSERT INTO CANDIDATES (NAME, EMAIL, PHONE, SKILLS, EXPERIENCE_YEARS, STATUS) VALUES
  ('Kavya Rao',    'kavya.rao@example.com',    '+91-9900112233', 'Java, Spring Boot, Oracle SQL', 4.0, 'INTERVIEWED');
INSERT INTO CANDIDATES (NAME, EMAIL, PHONE, SKILLS, EXPERIENCE_YEARS, STATUS) VALUES
  ('Mohit Bansal', 'mohit.bansal@example.com', '+91-9900223344', 'Angular, TypeScript', 3.5, 'SCREENING');
INSERT INTO CANDIDATES (NAME, EMAIL, PHONE, SKILLS, EXPERIENCE_YEARS, STATUS) VALUES
  ('Fatima Sheikh','fatima.sheikh@example.com','+91-9900334455', 'Recruitment, Onboarding', 5.0, 'SHORTLISTED');
INSERT INTO CANDIDATES (NAME, EMAIL, PHONE, SKILLS, EXPERIENCE_YEARS, STATUS) VALUES
  ('Sanjay Gupta', 'sanjay.gupta@example.com', '+91-9900445566', 'Accounting, Tally', 2.0, 'NEW');

INSERT INTO JOB_APPLICATIONS (CANDIDATE_ID, JOB_ID, APPLICATION_DATE, STATUS, REMARKS) VALUES
  (1, 1, CURRENT_DATE - 9, 'INTERVIEW', 'Strong backend fundamentals');
INSERT INTO JOB_APPLICATIONS (CANDIDATE_ID, JOB_ID, APPLICATION_DATE, STATUS, REMARKS) VALUES
  (2, 1, CURRENT_DATE - 6, 'SCREENING', 'Portfolio review pending');
INSERT INTO JOB_APPLICATIONS (CANDIDATE_ID, JOB_ID, APPLICATION_DATE, STATUS, REMARKS) VALUES
  (3, 2, CURRENT_DATE - 4, 'SHORTLISTED', 'Referral from HR team');
INSERT INTO JOB_APPLICATIONS (CANDIDATE_ID, JOB_ID, APPLICATION_DATE, STATUS, REMARKS) VALUES
  (4, 2, CURRENT_DATE - 2, 'APPLIED', null);

-- Interviews: one upcoming (relative to run date) and one completed
INSERT INTO INTERVIEWS (APPLICATION_ID, INTERVIEWER_ID, INTERVIEW_DATE, INTERVIEW_MODE, STATUS, FEEDBACK, RESULT)
VALUES (1, 1, CURRENT_TIMESTAMP + INTERVAL '2' DAY, 'ONLINE', 'SCHEDULED', null, null);
INSERT INTO INTERVIEWS (APPLICATION_ID, INTERVIEWER_ID, INTERVIEW_DATE, INTERVIEW_MODE, STATUS, FEEDBACK, RESULT)
VALUES (3, 6, CURRENT_TIMESTAMP - INTERVAL '1' DAY, 'ONSITE', 'COMPLETED', 'Great culture fit, solid process knowledge.', 'PASS');

-- =====================================================================
-- Attendance: last 7 days for all 6 employees (weekends = HOLIDAY,
-- deterministic mix of PRESENT/HALF_DAY/ABSENT/LEAVE)
-- =====================================================================
INSERT INTO ATTENDANCE (EMPLOYEE_ID, ATTENDANCE_DATE, CHECK_IN, CHECK_OUT, STATUS, WORKING_HOURS)
SELECT e.ID,
       CURRENT_DATE - 1,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '9' HOUR + INTERVAL '5' MINUTE,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '18' HOUR,
       'PRESENT', 8.50
FROM EMPLOYEES e;
INSERT INTO ATTENDANCE (EMPLOYEE_ID, ATTENDANCE_DATE, CHECK_IN, CHECK_OUT, STATUS, WORKING_HOURS)
SELECT e.ID,
       CURRENT_DATE - 2,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '9' HOUR + INTERVAL '12' MINUTE,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '18' HOUR + INTERVAL '10' MINUTE,
       'PRESENT', 8.97
FROM EMPLOYEES e;
INSERT INTO ATTENDANCE (EMPLOYEE_ID, ATTENDANCE_DATE, CHECK_IN, CHECK_OUT, STATUS, WORKING_HOURS)
SELECT e.ID,
       CURRENT_DATE - 3,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '9' HOUR + INTERVAL '30' MINUTE,
       CAST(CURRENT_DATE AS TIMESTAMP) + INTERVAL '13' HOUR,
       'HALF_DAY', 3.50
FROM EMPLOYEES e WHERE e.ID IN (2, 5);
INSERT INTO ATTENDANCE (EMPLOYEE_ID, ATTENDANCE_DATE, CHECK_IN, CHECK_OUT, STATUS, WORKING_HOURS)
SELECT e.ID, CURRENT_DATE - 3, null, null, 'LEAVE', null
FROM EMPLOYEES e WHERE e.ID IN (1, 3, 4, 6);
INSERT INTO ATTENDANCE (EMPLOYEE_ID, ATTENDANCE_DATE, CHECK_IN, CHECK_OUT, STATUS, WORKING_HOURS)
SELECT e.ID, CURRENT_DATE - 4, null, null, 'ABSENT', null
FROM EMPLOYEES e WHERE e.ID = 3;

-- A recent hire so the dashboard shows new-hire KPIs and trend (id 7)
INSERT INTO EMPLOYEES (EMPLOYEE_CODE, FIRST_NAME, LAST_NAME, EMAIL, PHONE, JOINING_DATE, EMPLOYMENT_TYPE, STATUS, DEPARTMENT_ID, DESIGNATION_ID)
VALUES ('EMP007', 'Rohan', 'Kulkarni', 'rohan.kulkarni@hrgenius.local', '+91-9860044412', CURRENT_DATE - 10, 'FULL_TIME', 'ACTIVE', 1, 1);
UPDATE EMPLOYEES SET MANAGER_ID = 1 WHERE ID = 7;

-- =====================================================================
-- Leave: 2 pending (approval queue), 1 approved, 1 rejected
-- =====================================================================
INSERT INTO LEAVE_REQUESTS (EMPLOYEE_ID, LEAVE_TYPE_ID, START_DATE, END_DATE, REASON, STATUS, APPROVED_BY)
VALUES (2, 1, CURRENT_DATE + 5, CURRENT_DATE + 7, 'Family function', 'PENDING', null);
INSERT INTO LEAVE_REQUESTS (EMPLOYEE_ID, LEAVE_TYPE_ID, START_DATE, END_DATE, REASON, STATUS, APPROVED_BY)
VALUES (3, 2, CURRENT_DATE + 2, CURRENT_DATE + 3, 'Fever and medical rest', 'PENDING', null);
INSERT INTO LEAVE_REQUESTS (EMPLOYEE_ID, LEAVE_TYPE_ID, START_DATE, END_DATE, REASON, STATUS, APPROVED_BY)
VALUES (5, 1, CURRENT_DATE - 20, CURRENT_DATE - 19, 'Personal errand', 'APPROVED', 1);
INSERT INTO LEAVE_REQUESTS (EMPLOYEE_ID, LEAVE_TYPE_ID, START_DATE, END_DATE, REASON, STATUS, APPROVED_BY)
VALUES (4, 3, CURRENT_DATE - 40, CURRENT_DATE - 39, 'Overlapping with release', 'REJECTED', 1);

-- =====================================================================
-- Payroll: last month, all 6 employees, PROCESSED
-- (Net = Basic + Allowances - Deductions - Tax, kept consistent)
-- =====================================================================
INSERT INTO PAYROLLS (EMPLOYEE_ID, PAY_MONTH, PAY_YEAR, BASIC_SALARY, ALLOWANCES, DEDUCTIONS, TAX, NET_SALARY, STATUS)
SELECT e.ID,
       EXTRACT(MONTH FROM CURRENT_DATE - INTERVAL '1' MONTH),
       EXTRACT(YEAR  FROM CURRENT_DATE - INTERVAL '1' MONTH),
       55000, 12000, 2500, 8250, 56250, 'PROCESSED'
FROM EMPLOYEES e;
UPDATE PAYROLLS SET BASIC_SALARY = 85000, NET_SALARY = 91250 WHERE EMPLOYEE_ID = 1;
UPDATE PAYROLLS SET BASIC_SALARY = 32000, NET_SALARY = 33500 WHERE EMPLOYEE_ID = 5;

-- =====================================================================
-- Performance: one submitted review and one draft
-- =====================================================================
INSERT INTO PERFORMANCE_REVIEWS (EMPLOYEE_ID, REVIEWER_ID, REVIEW_PERIOD, RATING, STRENGTHS, WEAKNESSES, GOALS, COMMENTS, STATUS)
VALUES (2, 1, '2025-H2', 4, 'Reliable delivery, strong testing habits', 'Presentation skills', 'Lead a module end to end', 'Consistent performer', 'SUBMITTED');
INSERT INTO PERFORMANCE_REVIEWS (EMPLOYEE_ID, REVIEWER_ID, REVIEW_PERIOD, RATING, STRENGTHS, WEAKNESSES, GOALS, COMMENTS, STATUS)
VALUES (3, 1, '2025-H2', 3, null, null, null, 'Draft — pending 1:1 discussion', 'DRAFT');

INSERT INTO NOTIFICATIONS (USER_ID, TITLE, MESSAGE, TYPE, READ_FLAG)
VALUES (1, 'Welcome to HRGenius', 'The workspace is ready. Start by exploring the dashboard.', 'SYSTEM', 0);

-- =====================================================================
-- Onboarding: EMP007 (Rohan, hired 10 days ago) is mid-onboarding —
-- 4 of 8 checklist items done (50%). Checklist is a JSON array in a CLOB.
-- =====================================================================
INSERT INTO ONBOARDINGS (EMPLOYEE_ID, JOINING_DATE, STATUS, COMPLETION_PERCENTAGE, CHECKLIST)
VALUES (7, CURRENT_DATE - 10, 'IN_PROGRESS', 50.00,
  '[{"label":"Offer letter signed","done":true},{"label":"Background verification completed","done":true},{"label":"ID proofs & documents collected","done":true},{"label":"Employee account & email created","done":true},{"label":"Workstation / equipment assigned","done":false},{"label":"Payroll & tax details submitted","done":false},{"label":"Policy & code-of-conduct briefing","done":false},{"label":"Team introduction & mentor assigned","done":false}]');
