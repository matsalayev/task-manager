CREATE TYPE GENDER AS ENUM (
	'male',
	'female'
);

CREATE TYPE ROLE AS ENUM (
	'admin',
	'manager',
	'staff'
);

CREATE TYPE TASK_STATUS AS ENUM (
	'to_do',
	'in_progress',
	'in_review',
	'testing',
	'done'
);

CREATE TABLE assets (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  s3_key VARCHAR NOT NULL,
  file_name VARCHAR NULL,
  content_type VARCHAR NULL
);

CREATE TABLE people (
	id UUID PRIMARY KEY,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	full_name VARCHAR NOT NULL,
	gender GENDER NOT NULL,
	date_of_birth DATE,
	document_number VARCHAR NULL,
	pinfl_number VARCHAR NULL,
	updated_at TIMESTAMP WITH TIME ZONE NULL,
	deleted_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE locations (
  id UUID PRIMARY KEY,
  name VARCHAR NOT NULL,
  latitude FLOAT NOT NULL,
  longitude FLOAT NOT NULL
);

CREATE TABLE corporations (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  name VARCHAR NOT NULL,
  location_id UUID NOT NULL REFERENCES locations (id),
  asset_id UUID NULL REFERENCES assets (id)
);

CREATE TABLE users (
  id UUID PRIMARY KEY REFERENCES people (id),
  role ROLE NOT NULL,
  phone VARCHAR NOT NULL UNIQUE,
  asset_id UUID NULL REFERENCES assets (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id),
  password VARCHAR NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NULL,
  deleted_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE specialties (
  id UUID PRIMARY KEY,
  name VARCHAR NOT NULL,
  corporate_id UUID NOT NULL REFERENCES corporations (id)
);

CREATE TABLE employees (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  person_id UUID NOT NULL REFERENCES people (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id),
  specialty_id UUID NOT NULL REFERENCES specialties (id),
  asset_id UUID NULL REFERENCES assets (id),
  phone VARCHAR NOT NULL UNIQUE
);

CREATE TABLE projects (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by UUID NOT NULL REFERENCES employees (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id),
  name VARCHAR NOT NULL,
  description VARCHAR NULL
);

CREATE TABLE tags (
  id UUID PRIMARY KEY,
  name VARCHAR NOT NULL,
  color VARCHAR NOT NULL,
  corporate_id UUID NOT NULL REFERENCES corporations (id)
);

CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by UUID NOT NULL REFERENCES employees (id),
  project_id UUID NOT NULL REFERENCES projects (id),
  name VARCHAR NOT NULL,
  description VARCHAR NULL,
  tag_id UUID NULL REFERENCES tags (id),
  asset_id UUID NULL REFERENCES assets (id),
  status TASK_STATUS NOT NULL,
  deadline TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE works (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  employee_id UUID NOT NULL REFERENCES employees (id),
  task_id UUID NOT NULL REFERENCES tasks (id),
  during_minutes BIGINT NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE assignees (
  task_id UUID NOT NULL REFERENCES tasks (id),
  employee_id UUID NOT NULL REFERENCES employees (id),

  UNIQUE (task_id, employee_id)
);

CREATE TABLE telegram_bot_users (
  person_id UUID UNIQUE REFERENCES people (id) NOT NULL,
  chat_id BIGINT UNIQUE NOT NULL
);

CREATE TABLE create_corporate (
  chat_id BIGINT UNIQUE NOT NULL,
  name VARCHAR NULL,
  photo UUID NULL REFERENCES assets (id),
  location UUID NULL REFERENCES locations (id)
);

CREATE TABLE create_user (
  chat_id BIGINT UNIQUE NOT NULL,
  full_name VARCHAR NULL,
  phone VARCHAR NOT NULL UNIQUE,
  photo UUID NULL REFERENCES assets (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id)
);

CREATE TABLE create_employee (
  chat_id BIGINT UNIQUE NOT NULL,
  full_name VARCHAR NULL,
  phone VARCHAR NOT NULL UNIQUE,
  photo UUID NULL REFERENCES assets (id),
  specialty UUID NULL REFERENCES specialties (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id)
)

--
--INSERT INTO people (id, created_at, full_name, gender, date_of_birth)
--VALUES (
--  '370ca333-5f7d-4981-9e25-b7886555c661',
--  now(),
--  'Admin',
--  'male',
--  '2024-01-01'
--);
--
--INSERT INTO people (id, created_at, full_name, gender, date_of_birth)
--VALUES (
--  '521f4e39-95e6-44ea-8406-f5a5cc73144e',
--  now(),
--  'Azizbek Matsalayev',
--  'male',
--  '2003-05-20'
--);
--
--INSERT INTO users (id, role, phone, password)
--VALUES (
--  '370ca333-5f7d-4981-9e25-b7886555c661',
--  'admin',
--  '+998901234567',
--  '$s0$e0801$5JK3Ogs35C2h5htbXQoeEQ==$N7HgNieSnOajn1FuEB7l4PhC6puBSq+e1E8WUaSJcGY='
--);
--
--INSERT INTO locations (id, name, latitude, longitude)
--VALUES (
--  '832a34d2-6d28-4778-85d4-ed18e42b3b01',
--  'Furqat ko`chasi 31',
--  41.552074,
--  60.607359
-- );
--
--INSERT INTO corporations (id, created_at, name, location_id)
--VALUES (
--  '2c0a9567-a36f-48de-9fa4-a233f4be507e',
--  now(),
--  'KV',
--  '832a34d2-6d28-4778-85d4-ed18e42b3b01'
--);
--
--INSERT INTO specialties (id, name)
--VALUES (
--  'e68c531c-332d-4f73-9745-ba2c600b0031',
--  'Backend Developer'
--);
--
--INSERT INTO employees (id, created_at, person_id, corporate_id, specialty_id, phone)
--VALUES (
--  '4add61d1-66f3-4339-8775-67515d86f489',
--  now(),
--  '521f4e39-95e6-44ea-8406-f5a5cc73144e',
--  '2c0a9567-a36f-48de-9fa4-a233f4be507e',
--  'e68c531c-332d-4f73-9745-ba2c600b0031',
--  '+998919991901'
--);