-- Dashboard Analytics Views and Tables
-- File: V002__analytics_views.sql

-- User Goals Table
CREATE TABLE user_goals (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  daily_hours_goal DECIMAL(4,2) NOT NULL DEFAULT 8.0,
  weekly_hours_goal DECIMAL(5,2) NOT NULL DEFAULT 40.0,
  monthly_tasks_goal INTEGER NOT NULL DEFAULT 20,
  productivity_goal DECIMAL(5,2) NOT NULL DEFAULT 80.0,
  streak_goal INTEGER NOT NULL DEFAULT 5,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  UNIQUE(user_id)
);

-- Productivity Insights Table
CREATE TABLE productivity_insights (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  category VARCHAR(50) NOT NULL CHECK (category IN ('Productivity', 'TimeManagement', 'WorkLifeBalance', 'Goals', 'Team')),
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  actionable BOOLEAN NOT NULL DEFAULT FALSE,
  priority VARCHAR(20) NOT NULL CHECK (priority IN ('Low', 'Medium', 'High')),
  metadata JSONB DEFAULT '{}',
  valid_until TIMESTAMP WITH TIME ZONE,
  is_read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Dashboard Notifications Table
CREATE TABLE dashboard_notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES people(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN ('TaskDeadline', 'ProjectUpdate', 'GoalAchievement', 'ProductivityAlert', 'TeamUpdate')),
  priority VARCHAR(20) NOT NULL CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
  is_read BOOLEAN DEFAULT FALSE,
  action_url VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  read_at TIMESTAMP WITH TIME ZONE
);

-- Daily Productivity Materialized View
CREATE MATERIALIZED VIEW daily_productivity_stats AS
SELECT
    te.user_id,
    DATE(te.start_time) as report_date,
    -- Productive time calculations
    SUM(CASE WHEN NOT te.is_break AND te.duration IS NOT NULL THEN te.duration ELSE 0 END) as productive_minutes,
    SUM(CASE WHEN te.is_break AND te.duration IS NOT NULL THEN te.duration ELSE 0 END) as break_minutes,
    -- Session calculations
    COUNT(CASE WHEN NOT te.is_break THEN 1 END) as productive_sessions,
    COUNT(CASE WHEN te.is_break THEN 1 END) as break_sessions,
    -- Task work calculations
    COUNT(DISTINCT te.task_id) FILTER (WHERE te.task_id IS NOT NULL) as tasks_worked,
    -- Time range
    MIN(te.start_time) as first_activity,
    MAX(COALESCE(te.end_time, te.start_time + INTERVAL '1 minute' * COALESCE(te.duration, 0))) as last_activity,
    -- Average session length
    AVG(CASE WHEN NOT te.is_break AND te.duration > 0 THEN te.duration END) as avg_session_length,
    -- Work mode from session
    (SELECT ews.work_mode
     FROM enhanced_work_sessions ews
     WHERE ews.user_id = te.user_id
       AND DATE(ews.start_time) = DATE(te.start_time)
     LIMIT 1) as work_mode
FROM time_entries te
WHERE te.start_time >= CURRENT_DATE - INTERVAL '90 days'
  AND te.duration IS NOT NULL
GROUP BY te.user_id, DATE(te.start_time);

-- Weekly Productivity Aggregation View
CREATE MATERIALIZED VIEW weekly_productivity_stats AS
SELECT
    user_id,
    DATE_TRUNC('week', report_date) as week_start,
    -- Hours calculations
    SUM(productive_minutes) / 60.0 as total_productive_hours,
    SUM(break_minutes) / 60.0 as total_break_hours,
    (SUM(productive_minutes) + SUM(break_minutes)) / 60.0 as total_hours,
    -- Work days
    COUNT(DISTINCT report_date) as work_days,
    -- Averages
    AVG(productive_minutes) / 60.0 as avg_daily_productive_hours,
    AVG(productive_minutes + break_minutes) / 60.0 as avg_daily_total_hours,
    -- Tasks
    SUM(tasks_worked) as total_tasks_worked,
    AVG(tasks_worked) as avg_daily_tasks,
    -- Efficiency
    CASE
      WHEN SUM(productive_minutes + break_minutes) > 0
      THEN SUM(productive_minutes)::DECIMAL / SUM(productive_minutes + break_minutes) * 100
      ELSE 0
    END as efficiency_percentage,
    -- Session metrics
    AVG(avg_session_length) as avg_session_length,
    SUM(productive_sessions) as total_sessions
FROM daily_productivity_stats
WHERE report_date >= CURRENT_DATE - INTERVAL '12 weeks'
GROUP BY user_id, DATE_TRUNC('week', report_date);

-- Monthly Productivity Aggregation View
CREATE MATERIALIZED VIEW monthly_productivity_stats AS
SELECT
    user_id,
    DATE_TRUNC('month', report_date) as month_start,
    -- Hours calculations
    SUM(productive_minutes) / 60.0 as total_productive_hours,
    SUM(break_minutes) / 60.0 as total_break_hours,
    (SUM(productive_minutes) + SUM(break_minutes)) / 60.0 as total_hours,
    -- Work days
    COUNT(DISTINCT report_date) as work_days,
    -- Efficiency and productivity
    CASE
      WHEN SUM(productive_minutes + break_minutes) > 0
      THEN SUM(productive_minutes)::DECIMAL / SUM(productive_minutes + break_minutes) * 100
      ELSE 0
    END as efficiency_percentage,
    -- Tasks
    SUM(tasks_worked) as total_tasks_worked,
    AVG(tasks_worked) as avg_daily_tasks,
    -- Goals achievement (calculated later in service)
    0 as goals_achieved, -- placeholder
    0 as total_goals     -- placeholder
FROM daily_productivity_stats
WHERE report_date >= CURRENT_DATE - INTERVAL '12 months'
GROUP BY user_id, DATE_TRUNC('month', report_date);

-- User Productivity Ranking View
CREATE VIEW user_productivity_ranking AS
SELECT
    u.id as user_id,
    p.full_name,
    u.role,
    u.corporate_id,
    -- Today stats
    COALESCE(dps.productive_minutes, 0) as today_productive_minutes,
    COALESCE(dps.tasks_worked, 0) as today_tasks,
    COALESCE(dps.break_minutes, 0) as today_break_minutes,
    -- Week stats
    COALESCE(wps.total_productive_hours, 0) as week_productive_hours,
    COALESCE(wps.total_tasks_worked, 0) as week_tasks,
    COALESCE(wps.efficiency_percentage, 0) as week_efficiency,
    -- Rankings
    RANK() OVER (
      PARTITION BY u.corporate_id
      ORDER BY COALESCE(dps.productive_minutes, 0) DESC
    ) as daily_rank,
    RANK() OVER (
      PARTITION BY u.corporate_id
      ORDER BY COALESCE(wps.total_productive_hours, 0) DESC
    ) as weekly_rank,
    -- Current status
    CASE
      WHEN EXISTS (
        SELECT 1 FROM time_entries te
        WHERE te.user_id = u.id
          AND te.is_running = TRUE
          AND te.start_time >= CURRENT_DATE
      ) THEN 'Working'
      WHEN EXISTS (
        SELECT 1 FROM time_entries te
        WHERE te.user_id = u.id
          AND te.is_running = TRUE
          AND te.is_break = TRUE
          AND te.start_time >= CURRENT_DATE
      ) THEN 'OnBreak'
      ELSE 'Offline'
    END as current_status
FROM users u
JOIN people p ON u.id = p.id
LEFT JOIN daily_productivity_stats dps ON u.id = dps.user_id AND dps.report_date = CURRENT_DATE
LEFT JOIN weekly_productivity_stats wps ON u.id = wps.user_id AND wps.week_start = DATE_TRUNC('week', CURRENT_DATE)
WHERE u.deleted_at IS NULL AND p.deleted_at IS NULL;

-- Team Productivity Overview View
CREATE VIEW team_productivity_overview AS
SELECT
    pm.project_id,
    p.name as project_name,
    p.corporate_id,
    COUNT(DISTINCT pm.user_id) as team_size,
    -- Today metrics
    SUM(COALESCE(dps.productive_minutes, 0)) / 60.0 as today_team_hours,
    AVG(COALESCE(dps.productive_minutes, 0)) / 60.0 as today_avg_member_hours,
    COUNT(DISTINCT CASE WHEN dps.productive_minutes > 0 THEN pm.user_id END) as active_today,
    -- Week metrics
    SUM(COALESCE(wps.total_productive_hours, 0)) as week_team_hours,
    AVG(COALESCE(wps.total_productive_hours, 0)) as week_avg_member_hours,
    AVG(COALESCE(wps.efficiency_percentage, 0)) as week_avg_efficiency,
    -- Task completion
    (SELECT COUNT(*) FROM tasks t WHERE t.project_id = pm.project_id AND t.status = 'done') as completed_tasks,
    (SELECT COUNT(*) FROM tasks t WHERE t.project_id = pm.project_id AND t.status = 'in_progress') as in_progress_tasks,
    (SELECT COUNT(*) FROM tasks t WHERE t.project_id = pm.project_id) as total_tasks
FROM (
  -- Get project members from task assignees
  SELECT DISTINCT t.project_id, a.user_id
  FROM tasks t
  JOIN assignees a ON t.id = a.task_id
  WHERE t.status != 'done' OR t.finished_at >= CURRENT_DATE - INTERVAL '30 days'
) pm
JOIN projects p ON pm.project_id = p.id
LEFT JOIN daily_productivity_stats dps ON pm.user_id = dps.user_id AND dps.report_date = CURRENT_DATE
LEFT JOIN weekly_productivity_stats wps ON pm.user_id = wps.user_id AND wps.week_start = DATE_TRUNC('week', CURRENT_DATE)
GROUP BY pm.project_id, p.name, p.corporate_id;

-- Hourly Productivity Pattern View
CREATE VIEW hourly_productivity_patterns AS
SELECT
    user_id,
    EXTRACT(HOUR FROM start_time) as hour_of_day,
    EXTRACT(DOW FROM start_time) as day_of_week, -- 0=Sunday, 1=Monday, etc.
    COUNT(*) as session_count,
    AVG(duration) as avg_duration_minutes,
    SUM(duration) as total_duration_minutes,
    AVG(CASE WHEN NOT is_break THEN duration END) as avg_productive_duration
FROM time_entries
WHERE start_time >= CURRENT_DATE - INTERVAL '30 days'
  AND duration IS NOT NULL
GROUP BY user_id, EXTRACT(HOUR FROM start_time), EXTRACT(DOW FROM start_time);

-- Task Completion Performance View
CREATE VIEW task_completion_performance AS
SELECT
    t.id as task_id,
    t.name as task_name,
    t.project_id,
    t.status,
    t.created_at,
    t.finished_at,
    t.deadline,
    t.estimated_hours,
    -- Time tracking metrics
    COALESCE(SUM(te.duration), 0) / 60.0 as actual_hours_spent,
    COUNT(DISTINCT te.user_id) as users_worked,
    COUNT(te.id) as time_entries_count,
    -- Performance calculations
    CASE
      WHEN t.estimated_hours IS NOT NULL AND t.estimated_hours > 0
      THEN (COALESCE(SUM(te.duration), 0) / 60.0) / t.estimated_hours * 100
      ELSE NULL
    END as time_accuracy_percentage,
    CASE
      WHEN t.deadline IS NOT NULL AND t.finished_at IS NOT NULL
      THEN EXTRACT(DAYS FROM (t.finished_at - t.deadline))
      ELSE NULL
    END as deadline_variance_days,
    -- Recent activity
    MAX(te.start_time) as last_worked_on
FROM tasks t
LEFT JOIN time_entries te ON t.id = te.task_id AND NOT te.is_break
GROUP BY t.id, t.name, t.project_id, t.status, t.created_at, t.finished_at, t.deadline, t.estimated_hours;

-- Performance indexes for analytics
CREATE INDEX idx_daily_productivity_user_date ON daily_productivity_stats(user_id, report_date);
CREATE INDEX idx_weekly_productivity_user_week ON weekly_productivity_stats(user_id, week_start);
CREATE INDEX idx_monthly_productivity_user_month ON monthly_productivity_stats(user_id, month_start);
CREATE INDEX idx_user_goals_user ON user_goals(user_id);
CREATE INDEX idx_productivity_insights_user ON productivity_insights(user_id, created_at DESC);
CREATE INDEX idx_dashboard_notifications_user ON dashboard_notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_time_entries_user_date ON time_entries(user_id, start_time DESC) WHERE duration IS NOT NULL;
CREATE INDEX idx_time_entries_running ON time_entries(user_id, is_running) WHERE is_running = TRUE;

-- Function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_analytics_views()
RETURNS void AS $$
BEGIN
    -- Refresh in dependency order
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_productivity_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY weekly_productivity_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_productivity_stats;

    -- Update user goals progress (this could be done in application logic)
    -- UPDATE user_goals SET updated_at = NOW() WHERE updated_at < NOW() - INTERVAL '1 day';

    -- Log refresh
    INSERT INTO activity_logs (id, user_id, activity_type, entity_type, description, timestamp)
    SELECT
      gen_random_uuid(),
      '00000000-0000-0000-0000-000000000000'::UUID, -- System user
      'SystemMaintenance'::VARCHAR,
      'AnalyticsViews'::VARCHAR,
      'Analytics views refreshed',
      NOW();
END;
$$ LANGUAGE plpgsql;

-- Trigger to update updated_at on user_goals
CREATE TRIGGER update_user_goals_updated_at
    BEFORE UPDATE ON user_goals
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger to update updated_at on productivity_insights
CREATE TRIGGER update_productivity_insights_updated_at
    BEFORE UPDATE ON productivity_insights
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create unique indexes for materialized views to enable CONCURRENTLY refresh
CREATE UNIQUE INDEX idx_daily_productivity_unique ON daily_productivity_stats(user_id, report_date);
CREATE UNIQUE INDEX idx_weekly_productivity_unique ON weekly_productivity_stats(user_id, week_start);
CREATE UNIQUE INDEX idx_monthly_productivity_unique ON monthly_productivity_stats(user_id, month_start);

-- Initial population of materialized views
REFRESH MATERIALIZED VIEW daily_productivity_stats;
REFRESH MATERIALIZED VIEW weekly_productivity_stats;
REFRESH MATERIALIZED VIEW monthly_productivity_stats;

-- Schedule daily refresh at 1 AM (if pg_cron is available)
-- SELECT cron.schedule('refresh-analytics-views', '0 1 * * *', 'SELECT refresh_analytics_views();');