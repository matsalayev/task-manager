-- Comprehensive Database Schema
-- Consolidated from V001, V002, V003 with proper types

-- Basic ENUMs
CREATE TYPE GENDER AS ENUM ('male', 'female');
CREATE TYPE ROLE AS ENUM ('director', 'manager', 'employee');
CREATE TYPE TASK_STATUS AS ENUM ('rejected', 'to_do', 'in_progress', 'in_review', 'testing', 'done');

-- Notification ENUMs
CREATE TYPE notification_type AS ENUM (
    'TaskAssigned', 'TaskDue', 'TaskCompleted', 'TaskOverdue', 'ProjectUpdate', 'ProjectDeadline',
    'TimeReminder', 'BreakReminder', 'DailyGoalReached', 'WeeklyGoalReached', 'SystemAlert',
    'TeamUpdate', 'MentionInComment', 'WorkSessionStarted', 'ProductivityInsight'
);
CREATE TYPE entity_type AS ENUM ('Task', 'Project', 'User', 'Team', 'Comment', 'TimeEntry', 'Goal');
CREATE TYPE notification_priority AS ENUM ('Low', 'Normal', 'High', 'Critical');
CREATE TYPE delivery_method AS ENUM ('InApp', 'Email', 'SMS', 'Push', 'Telegram', 'WebSocket');
CREATE TYPE delivery_status AS ENUM ('Pending', 'Sent', 'Delivered', 'Failed', 'Retrying');
CREATE TYPE condition_operator AS ENUM ('Equals', 'NotEquals', 'Contains', 'GreaterThan', 'LessThan', 'In');

-- Core Tables
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
  priority VARCHAR CHECK (priority IS NULL OR priority IN ('Low', 'Medium', 'High', 'Critical')),
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

-- Task Management Tables
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

-- Time Tracking Tables
CREATE TABLE enhanced_work_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id),
  start_time TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time TIMESTAMP WITH TIME ZONE,
  work_mode VARCHAR NOT NULL CHECK (work_mode IN ('Office', 'Remote', 'Hybrid')),
  is_running BOOLEAN DEFAULT TRUE,
  total_minutes INTEGER DEFAULT 0,
  break_minutes INTEGER DEFAULT 0,
  productive_minutes INTEGER DEFAULT 0,
  description TEXT,
  location VARCHAR,
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
  break_reason VARCHAR CHECK (break_reason IS NULL OR break_reason IN ('Lunch', 'Coffee', 'Meeting', 'Personal', 'Toilet', 'Other')),
  is_manual BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE activity_logs (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id),
  activity_type VARCHAR NOT NULL CHECK (activity_type IN ('TaskStart', 'TaskPause', 'TaskResume', 'TaskComplete', 'BreakStart', 'BreakEnd', 'SessionStart', 'SessionEnd')),
  entity_id UUID,
  entity_type VARCHAR,
  description TEXT,
  metadata JSONB,
  timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Task Dependencies
CREATE TABLE task_dependencies (
  id UUID PRIMARY KEY,
  dependent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  dependency_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  dependency_type VARCHAR NOT NULL CHECK (dependency_type IN ('FinishToStart', 'StartToStart', 'FinishToFinish', 'StartToFinish')),
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

-- Analytics Tables
CREATE TABLE user_goals (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  daily_hours_goal NUMERIC NOT NULL DEFAULT 8.0,
  weekly_hours_goal NUMERIC NOT NULL DEFAULT 40.0,
  monthly_tasks_goal INTEGER NOT NULL DEFAULT 20,
  productivity_goal NUMERIC NOT NULL DEFAULT 80.0,
  streak_goal INTEGER NOT NULL DEFAULT 5,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  UNIQUE(user_id)
);

CREATE TABLE productivity_insights (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  category VARCHAR NOT NULL CHECK (category IN ('Productivity', 'TimeManagement', 'WorkLifeBalance', 'Goals', 'Team')),
  title VARCHAR NOT NULL,
  description TEXT NOT NULL,
  actionable BOOLEAN NOT NULL DEFAULT FALSE,
  priority VARCHAR NOT NULL CHECK (priority IN ('Low', 'Medium', 'High')),
  metadata JSONB DEFAULT '{}',
  valid_until TIMESTAMP WITH TIME ZONE,
  is_read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE dashboard_notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  title VARCHAR NOT NULL,
  message TEXT NOT NULL,
  notification_type VARCHAR NOT NULL CHECK (notification_type IN ('TaskDeadline', 'ProjectUpdate', 'GoalAchievement', 'ProductivityAlert', 'TeamUpdate')),
  priority VARCHAR NOT NULL CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
  is_read BOOLEAN DEFAULT FALSE,
  action_url VARCHAR,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  read_at TIMESTAMP WITH TIME ZONE
);

-- Notifications System Tables
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    title VARCHAR NOT NULL,
    content TEXT NOT NULL,
    notification_type notification_type NOT NULL,
    related_entity_id VARCHAR,
    related_entity_type entity_type,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    priority notification_priority NOT NULL DEFAULT 'Normal',
    delivery_methods delivery_method[] NOT NULL DEFAULT ARRAY['InApp'::delivery_method],
    metadata JSONB DEFAULT '{}',
    scheduled_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    action_url VARCHAR,
    action_label VARCHAR,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    push_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    sms_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    telegram_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    task_assignments BOOLEAN NOT NULL DEFAULT TRUE,
    task_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    project_updates BOOLEAN NOT NULL DEFAULT TRUE,
    team_updates BOOLEAN NOT NULL DEFAULT TRUE,
    daily_digest BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_report BOOLEAN NOT NULL DEFAULT FALSE,
    productivity_insights BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR DEFAULT 'UTC',
    quiet_hours_weekends_only BOOLEAN DEFAULT FALSE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    timezone VARCHAR NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR NOT NULL UNIQUE,
    notification_type notification_type NOT NULL,
    title_template VARCHAR NOT NULL,
    content_template TEXT NOT NULL,
    supported_delivery_methods delivery_method[] NOT NULL,
    default_priority notification_priority NOT NULL DEFAULT 'Normal',
    variables JSONB DEFAULT '[]',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    notification_type notification_type NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions JSONB DEFAULT '[]',
    delivery_methods delivery_method[] NOT NULL,
    priority notification_priority NOT NULL,
    cooldown_minutes INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    delivery_method delivery_method NOT NULL,
    status delivery_status NOT NULL DEFAULT 'Pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    entity_type entity_type NOT NULL,
    entity_id UUID NOT NULL,
    notification_types notification_type[] NOT NULL,
    delivery_methods delivery_method[] NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, entity_type, entity_id)
);

-- Analytics Materialized Views
CREATE MATERIALIZED VIEW daily_productivity_stats AS
SELECT
    te.user_id,
    DATE(te.start_time) as report_date,
    SUM(CASE WHEN NOT te.is_break AND te.duration IS NOT NULL THEN te.duration ELSE 0 END) as productive_minutes,
    SUM(CASE WHEN te.is_break AND te.duration IS NOT NULL THEN te.duration ELSE 0 END) as break_minutes,
    COUNT(CASE WHEN NOT te.is_break THEN 1 END) as productive_sessions,
    COUNT(CASE WHEN te.is_break THEN 1 END) as break_sessions,
    COUNT(DISTINCT te.task_id) FILTER (WHERE te.task_id IS NOT NULL) as tasks_worked,
    MIN(te.start_time) as first_activity,
    MAX(COALESCE(te.end_time, te.start_time + INTERVAL '1 minute' * COALESCE(te.duration, 0))) as last_activity,
    AVG(CASE WHEN NOT te.is_break AND te.duration > 0 THEN te.duration END) as avg_session_length,
    'Standard' as work_mode
FROM time_entries te
WHERE te.start_time >= CURRENT_DATE - INTERVAL '90 days'
  AND te.duration IS NOT NULL
GROUP BY te.user_id, DATE(te.start_time);

CREATE MATERIALIZED VIEW weekly_productivity_stats AS
SELECT
    user_id,
    DATE_TRUNC('week', report_date) as week_start,
    SUM(productive_minutes) / 60.0 as total_productive_hours,
    SUM(break_minutes) / 60.0 as total_break_hours,
    (SUM(productive_minutes) + SUM(break_minutes)) / 60.0 as total_hours,
    COUNT(DISTINCT report_date) as work_days,
    AVG(productive_minutes) / 60.0 as avg_daily_productive_hours,
    AVG(productive_minutes + break_minutes) / 60.0 as avg_daily_total_hours,
    SUM(tasks_worked) as total_tasks_worked,
    AVG(tasks_worked) as avg_daily_tasks,
    CASE
      WHEN SUM(productive_minutes + break_minutes) > 0
      THEN SUM(productive_minutes)::NUMERIC / SUM(productive_minutes + break_minutes) * 100
      ELSE 0
    END as efficiency_percentage,
    AVG(avg_session_length) as avg_session_length,
    SUM(productive_sessions) as total_sessions
FROM daily_productivity_stats
WHERE report_date >= CURRENT_DATE - INTERVAL '12 weeks'
GROUP BY user_id, DATE_TRUNC('week', report_date);

CREATE MATERIALIZED VIEW monthly_productivity_stats AS
SELECT
    user_id,
    DATE_TRUNC('month', report_date) as month_start,
    SUM(productive_minutes) / 60.0 as total_productive_hours,
    SUM(break_minutes) / 60.0 as total_break_hours,
    (SUM(productive_minutes) + SUM(break_minutes)) / 60.0 as total_hours,
    COUNT(DISTINCT report_date) as work_days,
    CASE
      WHEN SUM(productive_minutes + break_minutes) > 0
      THEN SUM(productive_minutes)::NUMERIC / SUM(productive_minutes + break_minutes) * 100
      ELSE 0
    END as efficiency_percentage,
    SUM(tasks_worked) as total_tasks_worked,
    AVG(tasks_worked) as avg_daily_tasks,
    0 as goals_achieved,
    0 as total_goals
FROM daily_productivity_stats
WHERE report_date >= CURRENT_DATE - INTERVAL '12 months'
GROUP BY user_id, DATE_TRUNC('month', report_date);

-- Views
CREATE VIEW user_productivity_ranking AS
SELECT
    u.id as user_id,
    p.full_name,
    u.role,
    u.corporate_id,
    COALESCE(dps.productive_minutes, 0) as today_productive_minutes,
    COALESCE(dps.tasks_worked, 0) as today_tasks,
    COALESCE(dps.break_minutes, 0) as today_break_minutes,
    COALESCE(wps.total_productive_hours, 0) as week_productive_hours,
    COALESCE(wps.total_tasks_worked, 0) as week_tasks,
    COALESCE(wps.efficiency_percentage, 0) as week_efficiency,
    RANK() OVER (
      PARTITION BY u.corporate_id
      ORDER BY COALESCE(dps.productive_minutes, 0) DESC
    ) as daily_rank,
    RANK() OVER (
      PARTITION BY u.corporate_id
      ORDER BY COALESCE(wps.total_productive_hours, 0) DESC
    ) as weekly_rank,
    CASE
      WHEN EXISTS (
        SELECT 1 FROM time_entries te
        WHERE te.user_id = u.id AND te.is_running = TRUE AND te.start_time >= CURRENT_DATE
      ) THEN 'Working'
      WHEN EXISTS (
        SELECT 1 FROM time_entries te
        WHERE te.user_id = u.id AND te.is_running = TRUE AND te.is_break = TRUE AND te.start_time >= CURRENT_DATE
      ) THEN 'OnBreak'
      ELSE 'Offline'
    END as current_status
FROM users u
JOIN people p ON u.id = p.id
LEFT JOIN daily_productivity_stats dps ON u.id = dps.user_id AND dps.report_date = CURRENT_DATE
LEFT JOIN weekly_productivity_stats wps ON u.id = wps.user_id AND wps.week_start = DATE_TRUNC('week', CURRENT_DATE)
WHERE u.deleted_at IS NULL AND p.deleted_at IS NULL;

-- All Indexes
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

-- Analytics Indexes
CREATE INDEX idx_daily_productivity_user_date ON daily_productivity_stats(user_id, report_date);
CREATE INDEX idx_weekly_productivity_user_week ON weekly_productivity_stats(user_id, week_start);
CREATE INDEX idx_monthly_productivity_user_month ON monthly_productivity_stats(user_id, month_start);
CREATE INDEX idx_user_goals_user ON user_goals(user_id);
CREATE INDEX idx_productivity_insights_user ON productivity_insights(user_id, created_at DESC);
CREATE INDEX idx_dashboard_notifications_user ON dashboard_notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_time_entries_user_date ON time_entries(user_id, start_time DESC) WHERE duration IS NOT NULL;
CREATE INDEX idx_time_entries_running ON time_entries(user_id, is_running) WHERE is_running = TRUE;

-- Notification Indexes
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, created_at DESC) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_scheduled ON notifications(scheduled_at) WHERE scheduled_at IS NOT NULL AND sent_at IS NULL;
CREATE INDEX idx_notifications_type ON notifications(notification_type);
CREATE INDEX idx_notifications_priority ON notifications(priority);
CREATE INDEX idx_notifications_entity ON notifications(related_entity_type, related_entity_id) WHERE related_entity_id IS NOT NULL;
CREATE INDEX idx_notifications_expires ON notifications(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_notification_settings_user ON notification_settings(user_id);
CREATE INDEX idx_notification_templates_type ON notification_templates(notification_type) WHERE is_active = TRUE;
CREATE INDEX idx_notification_rules_user_type ON notification_rules(user_id, notification_type) WHERE enabled = TRUE;
CREATE INDEX idx_delivery_log_notification ON notification_delivery_log(notification_id, status);
CREATE INDEX idx_delivery_log_status_pending ON notification_delivery_log(status, created_at) WHERE status = 'Pending';
CREATE INDEX idx_delivery_log_failed_retry ON notification_delivery_log(status, attempts, created_at) WHERE status = 'Failed';
CREATE INDEX idx_subscriptions_user_entity ON notification_subscriptions(user_id, entity_type, entity_id) WHERE enabled = TRUE;
CREATE INDEX idx_subscriptions_entity ON notification_subscriptions(entity_type, entity_id) WHERE enabled = TRUE;

-- Unique indexes for materialized views
CREATE UNIQUE INDEX idx_daily_productivity_unique ON daily_productivity_stats(user_id, report_date);
CREATE UNIQUE INDEX idx_weekly_productivity_unique ON weekly_productivity_stats(user_id, week_start);
CREATE UNIQUE INDEX idx_monthly_productivity_unique ON monthly_productivity_stats(user_id, month_start);

-- Functions and Triggers
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Update triggers
CREATE TRIGGER update_task_comments_updated_at BEFORE UPDATE ON task_comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_time_entries_updated_at BEFORE UPDATE ON time_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_goals_updated_at BEFORE UPDATE ON user_goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_notifications_updated_at BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_notification_settings_updated_at BEFORE UPDATE ON notification_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_notification_templates_updated_at BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_notification_rules_updated_at BEFORE UPDATE ON notification_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_delivery_log_updated_at BEFORE UPDATE ON notification_delivery_log
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON notification_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Utility Functions
CREATE OR REPLACE FUNCTION refresh_analytics_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_productivity_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY weekly_productivity_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_productivity_stats;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_default_notification_settings()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO notification_settings (user_id, created_at, updated_at)
    VALUES (NEW.id, NOW(), NOW())
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER create_user_notification_settings
    AFTER INSERT ON people
    FOR EACH ROW
    EXECUTE FUNCTION create_default_notification_settings();

CREATE OR REPLACE FUNCTION cleanup_expired_notifications()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM notifications
    WHERE expires_at IS NOT NULL AND expires_at < NOW() AND is_read = TRUE;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_notification_stats(p_user_id UUID)
RETURNS TABLE (
    total_count BIGINT,
    unread_count BIGINT,
    today_count BIGINT,
    week_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) as total_count,
        COUNT(*) FILTER (WHERE is_read = FALSE) as unread_count,
        COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today_count,
        COUNT(*) FILTER (WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE)) as week_count
    FROM notifications
    WHERE user_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

-- Initial materialized view population
REFRESH MATERIALIZED VIEW daily_productivity_stats;
REFRESH MATERIALIZED VIEW weekly_productivity_stats;
REFRESH MATERIALIZED VIEW monthly_productivity_stats;

-- Default notification templates
INSERT INTO notification_templates (name, notification_type, title_template, content_template, supported_delivery_methods, default_priority, variables) VALUES
('task_assigned', 'TaskAssigned', 'New Task Assigned: {{task_name}}', 'You have been assigned a new task "{{task_name}}" in project {{project_name}}. Due date: {{due_date}}', ARRAY['InApp'::delivery_method, 'Email'::delivery_method, 'Push'::delivery_method, 'Telegram'::delivery_method], 'Normal', '[{"name": "task_name", "description": "Name of the task", "required": true}, {"name": "project_name", "description": "Name of the project", "required": true}, {"name": "due_date", "description": "Due date of the task", "required": false}]'),
('task_due_soon', 'TaskDue', 'Task Due Tomorrow: {{task_name}}', 'Your task "{{task_name}}" is due tomorrow. Please make sure to complete it on time.', ARRAY['InApp'::delivery_method, 'Email'::delivery_method, 'Push'::delivery_method], 'High', '[{"name": "task_name", "description": "Name of the task", "required": true}]'),
('task_overdue', 'TaskOverdue', 'Overdue Task: {{task_name}}', 'Your task "{{task_name}}" is now overdue. Please complete it as soon as possible.', ARRAY['InApp'::delivery_method, 'Email'::delivery_method, 'Push'::delivery_method], 'Critical', '[{"name": "task_name", "description": "Name of the task", "required": true}]'),
('project_update', 'ProjectUpdate', 'Project Update: {{project_name}}', 'There has been an update in project "{{project_name}}": {{update_description}}', ARRAY['InApp'::delivery_method, 'Email'::delivery_method], 'Normal', '[{"name": "project_name", "description": "Name of the project", "required": true}, {"name": "update_description", "description": "Description of the update", "required": true}]'),
('daily_goal_reached', 'DailyGoalReached', 'Daily Goal Achieved! 🎉', 'Congratulations! You have reached your daily work goal of {{goal_hours}} hours. Keep up the great work!', ARRAY['InApp'::delivery_method, 'Push'::delivery_method], 'Normal', '[{"name": "goal_hours", "description": "Daily goal in hours", "required": true}]'),
('productivity_insight', 'ProductivityInsight', 'Productivity Insight', '{{insight_message}}', ARRAY['InApp'::delivery_method], 'Low', '[{"name": "insight_message", "description": "The productivity insight message", "required": true}]');