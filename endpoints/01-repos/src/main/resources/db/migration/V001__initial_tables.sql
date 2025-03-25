CREATE TYPE GENDER AS ENUM (
	'male',
	'female'
);

CREATE TYPE ROLE AS ENUM (
	'director',
	'manager',
	'employee'
);

CREATE TYPE TASK_STATUS AS ENUM (
    'rejected',
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
	date_of_birth DATE NULL,
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
  asset_id UUID NULL REFERENCES assets (id),

  UNIQUE(name, location_id)
);

CREATE TABLE users (
  id UUID PRIMARY KEY REFERENCES people (id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  role ROLE NOT NULL,
  phone VARCHAR NOT NULL UNIQUE,
  asset_id UUID NULL REFERENCES assets (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id),
  password VARCHAR NULL,
  updated_at TIMESTAMP WITH TIME ZONE NULL,
  deleted_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE projects (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by UUID NOT NULL REFERENCES users (id),
  corporate_id UUID NOT NULL REFERENCES corporations (id),
  name VARCHAR NOT NULL,
  description VARCHAR NULL
);

CREATE TABLE tags (
  id UUID PRIMARY KEY,
  name VARCHAR NOT NULL,
  color VARCHAR NULL,
  corporate_id UUID NOT NULL REFERENCES corporations (id)
);

CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by UUID NOT NULL REFERENCES users (id),
  project_id UUID NOT NULL REFERENCES projects (id),
  name VARCHAR NOT NULL,
  description VARCHAR NULL,
  tag_id UUID NULL REFERENCES tags (id),
  asset_id UUID NULL REFERENCES assets (id),
  status TASK_STATUS NOT NULL,
  deadline TIMESTAMP WITH TIME ZONE NULL,
  link VARCHAR NULL
);

CREATE TABLE works (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  user_id UUID NOT NULL REFERENCES users (id),
  task_id UUID NOT NULL REFERENCES tasks (id),
  task_status TASK_STATUS NOT NULL,
  during_minutes BIGINT NOT NULL DEFAULT 0,
  finished_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE TABLE assignees (
  task_id UUID NOT NULL REFERENCES tasks (id),
  user_id UUID NOT NULL REFERENCES users (id),

  UNIQUE (task_id, user_id)
);

CREATE TABLE telegram_bot_users (
  person_id UUID UNIQUE REFERENCES people (id) NOT NULL,
  chat_id BIGINT UNIQUE NOT NULL
);