CREATE TYPE LANGUAGE AS ENUM (
	'uz',
	'ru',
	'en'
);

CREATE TYPE GENDER AS ENUM (
	'male',
	'female'
);

CREATE TYPE WORK_STATE AS ENUM (
	'in_state',
	'out_of_state'
);

CREATE TYPE WORK_TYPE AS ENUM (
	'full_time',
	'hourly'
);

CREATE TYPE WORK_STATUS AS ENUM (
	'active',
	'passive'
);

CREATE TYPE ROLE AS ENUM (
	'admin',
	'manager',
	'teacher',
	'staff'
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

CREATE TABLE users (
  id UUID PRIMARY KEY REFERENCES people(id),
  role ROLE NOT NULL,
  phone VARCHAR NOT NULL UNIQUE,
  password VARCHAR NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NULL,
  deleted_at TIMESTAMP WITH TIME ZONE NULL
);

INSERT INTO people (id, created_at, full_name, gender, date_of_birth)
VALUES (
  '370ca333-5f7d-4981-9e25-b7886555c661',
  now(),
  'Admin',
  'male',
  '2024-01-01'
);

INSERT INTO users (id, role, phone, password)
VALUES (
  '370ca333-5f7d-4981-9e25-b7886555c661',
  'admin',
  '+998901234567',
  '$s0$e0801$5JK3Ogs35C2h5htbXQoeEQ==$N7HgNieSnOajn1FuEB7l4PhC6puBSq+e1E8WUaSJcGY='
);

CREATE TABLE IF NOT EXISTS telegram_bot_users
(
    people_id UUID UNIQUE REFERENCES people (id) NOT NULL,
    chat_id BIGINT UNIQUE NOT NULL
);