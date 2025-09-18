-- Notifications System Tables
-- File: V003__notifications_system.sql

-- Notification Types Enum
CREATE TYPE notification_type AS ENUM (
    'TaskAssigned',
    'TaskDue',
    'TaskCompleted',
    'TaskOverdue',
    'ProjectUpdate',
    'ProjectDeadline',
    'TimeReminder',
    'BreakReminder',
    'DailyGoalReached',
    'WeeklyGoalReached',
    'SystemAlert',
    'TeamUpdate',
    'MentionInComment',
    'WorkSessionStarted',
    'ProductivityInsight'
);

-- Entity Types Enum
CREATE TYPE entity_type AS ENUM (
    'Task',
    'Project',
    'User',
    'Team',
    'Comment',
    'TimeEntry',
    'Goal'
);

-- Notification Priority Enum
CREATE TYPE notification_priority AS ENUM (
    'Low',
    'Normal',
    'High',
    'Critical'
);

-- Delivery Method Enum
CREATE TYPE delivery_method AS ENUM (
    'InApp',
    'Email',
    'SMS',
    'Push',
    'Telegram',
    'WebSocket'
);

-- Delivery Status Enum
CREATE TYPE delivery_status AS ENUM (
    'Pending',
    'Sent',
    'Delivered',
    'Failed',
    'Retrying'
);

-- Condition Operator Enum
CREATE TYPE condition_operator AS ENUM (
    'Equals',
    'NotEquals',
    'Contains',
    'GreaterThan',
    'LessThan',
    'In'
);

-- Main Notifications Table
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    notification_type notification_type NOT NULL,
    related_entity_id VARCHAR(255),
    related_entity_type entity_type,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    priority notification_priority NOT NULL DEFAULT 'Normal',
    delivery_methods delivery_method[] NOT NULL DEFAULT ARRAY['InApp'],
    metadata JSONB DEFAULT '{}',
    scheduled_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    action_url VARCHAR(500),
    action_label VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Notification Settings Table
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
    quiet_hours_timezone VARCHAR(50) DEFAULT 'UTC',
    quiet_hours_weekends_only BOOLEAN DEFAULT FALSE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);

-- Notification Templates Table
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    notification_type notification_type NOT NULL,
    title_template VARCHAR(500) NOT NULL,
    content_template TEXT NOT NULL,
    supported_delivery_methods delivery_method[] NOT NULL,
    default_priority notification_priority NOT NULL DEFAULT 'Normal',
    variables JSONB DEFAULT '[]', -- Array of TemplateVariable objects
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Notification Rules Table
CREATE TABLE notification_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    notification_type notification_type NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions JSONB DEFAULT '[]', -- Array of RuleCondition objects
    delivery_methods delivery_method[] NOT NULL,
    priority notification_priority NOT NULL,
    cooldown_minutes INTEGER, -- Prevent spam
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Notification Delivery Log Table
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

-- Notification Subscriptions Table (for team/project notifications)
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

-- Performance Indexes
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

-- Functions and Triggers

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
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

-- Function to automatically create default notification settings for new users
CREATE OR REPLACE FUNCTION create_default_notification_settings()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO notification_settings (user_id, created_at, updated_at)
    VALUES (NEW.id, NOW(), NOW())
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to create default settings when a new user is created
CREATE TRIGGER create_user_notification_settings
    AFTER INSERT ON people
    FOR EACH ROW
    EXECUTE FUNCTION create_default_notification_settings();

-- Function to clean up expired notifications
CREATE OR REPLACE FUNCTION cleanup_expired_notifications()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM notifications
    WHERE expires_at IS NOT NULL
      AND expires_at < NOW()
      AND is_read = TRUE;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to get notification statistics for a user
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

-- Default notification templates
INSERT INTO notification_templates (name, notification_type, title_template, content_template, supported_delivery_methods, default_priority, variables) VALUES
('task_assigned', 'TaskAssigned', 'New Task Assigned: {{task_name}}', 'You have been assigned a new task "{{task_name}}" in project {{project_name}}. Due date: {{due_date}}', ARRAY['InApp', 'Email', 'Push', 'Telegram'], 'Normal', '[{"name": "task_name", "description": "Name of the task", "required": true}, {"name": "project_name", "description": "Name of the project", "required": true}, {"name": "due_date", "description": "Due date of the task", "required": false}]'),

('task_due_soon', 'TaskDue', 'Task Due Tomorrow: {{task_name}}', 'Your task "{{task_name}}" is due tomorrow. Please make sure to complete it on time.', ARRAY['InApp', 'Email', 'Push'], 'High', '[{"name": "task_name", "description": "Name of the task", "required": true}]'),

('task_overdue', 'TaskOverdue', 'Overdue Task: {{task_name}}', 'Your task "{{task_name}}" is now overdue. Please complete it as soon as possible.', ARRAY['InApp', 'Email', 'Push'], 'Critical', '[{"name": "task_name", "description": "Name of the task", "required": true}]'),

('project_update', 'ProjectUpdate', 'Project Update: {{project_name}}', 'There has been an update in project "{{project_name}}": {{update_description}}', ARRAY['InApp', 'Email'], 'Normal', '[{"name": "project_name", "description": "Name of the project", "required": true}, {"name": "update_description", "description": "Description of the update", "required": true}]'),

('daily_goal_reached', 'DailyGoalReached', 'Daily Goal Achieved! 🎉', 'Congratulations! You have reached your daily work goal of {{goal_hours}} hours. Keep up the great work!', ARRAY['InApp', 'Push'], 'Normal', '[{"name": "goal_hours", "description": "Daily goal in hours", "required": true}]'),

('productivity_insight', 'ProductivityInsight', 'Productivity Insight', '{{insight_message}}', ARRAY['InApp'], 'Low', '[{"name": "insight_message", "description": "The productivity insight message", "required": true}]');

-- Schedule periodic cleanup (if pg_cron is available)
-- SELECT cron.schedule('cleanup-expired-notifications', '0 2 * * *', 'SELECT cleanup_expired_notifications();');