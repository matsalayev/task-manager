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
  link VARCHAR NULL,
  -- Kanban and enhanced task fields
  priority VARCHAR(20) CHECK (priority IS NULL OR priority IN ('Low', 'Medium', 'High', 'Critical')),
  position INTEGER DEFAULT 0,
  estimated_hours INTEGER,
  parent_task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
  finished_at TIMESTAMP WITH TIME ZONE
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

-- Task Comments Table
CREATE TABLE task_comments (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES people(id),
  content TEXT NOT NULL,
  parent_comment_id UUID REFERENCES task_comments(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  is_edited BOOLEAN DEFAULT FALSE
);

-- Task Attachments Table
CREATE TABLE task_attachments (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  file_name VARCHAR NOT NULL,
  file_path VARCHAR NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR,
  uploaded_by UUID NOT NULL REFERENCES people(id),
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Enhanced Time Tracking Tables
CREATE TABLE enhanced_work_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id),
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time TIMESTAMP WITH TIME ZONE,
  work_mode VARCHAR(20) NOT NULL CHECK (work_mode IN ('Office', 'Remote', 'Hybrid')),
  is_running BOOLEAN DEFAULT TRUE,
  total_minutes INTEGER DEFAULT 0,
  break_minutes INTEGER DEFAULT 0,
  productive_minutes INTEGER DEFAULT 0,
  description TEXT,
  location VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE time_entries (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id),
  task_id UUID REFERENCES tasks(id),
  work_session_id UUID REFERENCES enhanced_work_sessions(id),
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time TIMESTAMP WITH TIME ZONE,
  duration INTEGER,
  description TEXT,
  is_running BOOLEAN DEFAULT FALSE,
  is_break BOOLEAN DEFAULT FALSE,
  break_reason VARCHAR(50) CHECK (break_reason IS NULL OR break_reason IN ('Lunch', 'Coffee', 'Meeting', 'Personal', 'Toilet', 'Other')),
  is_manual BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE activity_logs (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id),
  activity_type VARCHAR(50) NOT NULL CHECK (activity_type IN ('TaskStart', 'TaskPause', 'TaskResume', 'TaskComplete', 'BreakStart', 'BreakEnd', 'SessionStart', 'SessionEnd')),
  entity_id UUID,
  entity_type VARCHAR(50),
  description TEXT,
  metadata JSONB,
  timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Task Dependencies and Subtasks Tables
CREATE TABLE task_dependencies (
  id UUID PRIMARY KEY,
  dependent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  dependency_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  dependency_type VARCHAR(20) NOT NULL CHECK (dependency_type IN ('FinishToStart', 'StartToStart', 'FinishToFinish', 'StartToFinish')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE(dependent_task_id, dependency_task_id),
  CHECK (dependent_task_id != dependency_task_id)
);

CREATE TABLE task_subtasks (
  id UUID PRIMARY KEY,
  parent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  child_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  order_index INTEGER NOT NULL DEFAULT 0,
  created_by UUID NOT NULL REFERENCES people(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  UNIQUE(parent_task_id, child_task_id),
  CHECK (parent_task_id != child_task_id)
);

-- Indexes for better performance
CREATE INDEX idx_tasks_project_status_position ON tasks(project_id, status, position);
CREATE INDEX idx_tasks_parent ON tasks(parent_task_id) WHERE parent_task_id IS NOT NULL;
CREATE INDEX idx_task_comments_task ON task_comments(task_id);
CREATE INDEX idx_task_comments_parent ON task_comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_task_attachments_task ON task_attachments(task_id);
CREATE INDEX idx_enhanced_work_sessions_user ON enhanced_work_sessions(user_id);
CREATE INDEX idx_time_entries_user ON time_entries(user_id);
CREATE INDEX idx_time_entries_task ON time_entries(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX idx_time_entries_session ON time_entries(work_session_id) WHERE work_session_id IS NOT NULL;
CREATE INDEX idx_activity_logs_user ON activity_logs(user_id);
CREATE INDEX idx_task_dependencies_dependent ON task_dependencies(dependent_task_id);
CREATE INDEX idx_task_dependencies_dependency ON task_dependencies(dependency_task_id);
CREATE INDEX idx_task_subtasks_parent ON task_subtasks(parent_task_id);
CREATE INDEX idx_task_subtasks_child ON task_subtasks(child_task_id);

-- Triggers for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_task_comments_updated_at BEFORE UPDATE ON task_comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_time_entries_updated_at BEFORE UPDATE ON time_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();