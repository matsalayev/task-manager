-- Enhanced work sessions table
CREATE TABLE IF NOT EXISTS enhanced_work_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    work_mode VARCHAR(20) NOT NULL DEFAULT 'Remote',
    is_running BOOLEAN DEFAULT false,
    total_minutes INTEGER DEFAULT 0,
    break_minutes INTEGER DEFAULT 0,
    productive_minutes INTEGER DEFAULT 0,
    description TEXT,
    location VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT valid_session_time CHECK (end_time IS NULL OR start_time <= end_time),
    CONSTRAINT valid_minutes CHECK (total_minutes >= 0 AND break_minutes >= 0 AND productive_minutes >= 0)
);

-- Enhanced time entries table
CREATE TABLE IF NOT EXISTS time_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    work_session_id UUID REFERENCES enhanced_work_sessions(id) ON DELETE SET NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_minutes INTEGER,
    description TEXT NOT NULL,
    is_running BOOLEAN DEFAULT false,
    is_break BOOLEAN DEFAULT false,
    break_reason VARCHAR(20),
    is_manual BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT valid_entry_time CHECK (end_time IS NULL OR start_time <= end_time),
    CONSTRAINT break_reason_required CHECK (is_break = false OR break_reason IS NOT NULL)
);

-- Daily time reports (aggregated daily stats)
CREATE TABLE IF NOT EXISTS daily_time_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    total_worked_minutes INTEGER NOT NULL DEFAULT 0,
    productive_minutes INTEGER NOT NULL DEFAULT 0,
    break_minutes INTEGER NOT NULL DEFAULT 0,
    tasks_worked INTEGER NOT NULL DEFAULT 0,
    work_mode VARCHAR(20),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    overtime_minutes INTEGER DEFAULT 0,
    is_holiday BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    UNIQUE(user_id, report_date)
);

-- Activity logs (detailed user activity tracking)
CREATE TABLE IF NOT EXISTS activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Time targets (user goals and limits)
CREATE TABLE IF NOT EXISTS time_targets (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    daily_target_minutes INTEGER DEFAULT 480, -- 8 hours
    weekly_target_minutes INTEGER DEFAULT 2400, -- 40 hours
    monthly_target_minutes INTEGER DEFAULT 10400, -- ~173 hours (4.3 weeks)
    max_overtime_minutes INTEGER DEFAULT 120, -- 2 hours
    required_break_minutes INTEGER DEFAULT 30, -- 30 min break per day
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_enhanced_work_sessions_user_date ON enhanced_work_sessions(user_id, DATE(start_time));
CREATE INDEX IF NOT EXISTS idx_enhanced_work_sessions_running ON enhanced_work_sessions(user_id, is_running) WHERE is_running = true;

CREATE INDEX IF NOT EXISTS idx_time_entries_user_date ON time_entries(user_id, DATE(start_time));
CREATE INDEX IF NOT EXISTS idx_time_entries_task ON time_entries(task_id) WHERE task_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_time_entries_running ON time_entries(user_id, is_running) WHERE is_running = true;
CREATE INDEX IF NOT EXISTS idx_time_entries_break ON time_entries(user_id, is_break) WHERE is_break = true;

CREATE INDEX IF NOT EXISTS idx_daily_reports_user_date ON daily_time_reports(user_id, report_date);
CREATE INDEX IF NOT EXISTS idx_activity_logs_user_time ON activity_logs(user_id, timestamp);

-- Constraints to prevent multiple running sessions/timers
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_running_session_per_user
ON enhanced_work_sessions(user_id) WHERE is_running = true;

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_running_timer_per_user
ON time_entries(user_id) WHERE is_running = true AND is_break = false;

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_running_break_per_user
ON time_entries(user_id) WHERE is_running = true AND is_break = true;

-- Add constraints for valid enum values
ALTER TABLE enhanced_work_sessions ADD CONSTRAINT IF NOT EXISTS valid_work_mode
    CHECK (work_mode IN ('Office', 'Remote', 'Hybrid'));

ALTER TABLE time_entries ADD CONSTRAINT IF NOT EXISTS valid_break_reason
    CHECK (break_reason IS NULL OR break_reason IN ('Lunch', 'Coffee', 'Meeting', 'Personal', 'Toilet', 'Other'));

ALTER TABLE activity_logs ADD CONSTRAINT IF NOT EXISTS valid_activity_type
    CHECK (activity_type IN ('SessionStart', 'SessionEnd', 'BreakStart', 'BreakEnd', 'TaskStart', 'TaskEnd', 'ModeChange'));

-- Trigger to update daily reports when time entries change
CREATE OR REPLACE FUNCTION update_daily_time_report()
RETURNS TRIGGER AS $$
DECLARE
    report_date DATE;
    user_id_val UUID;
BEGIN
    -- Get the date and user_id from the time entry
    IF TG_OP = 'DELETE' THEN
        report_date := DATE(OLD.start_time);
        user_id_val := OLD.user_id;
    ELSE
        report_date := DATE(NEW.start_time);
        user_id_val := NEW.user_id;
    END IF;

    -- Update the daily report
    INSERT INTO daily_time_reports (user_id, report_date, total_worked_minutes, productive_minutes, break_minutes, tasks_worked)
    SELECT
        user_id_val,
        report_date,
        COALESCE(SUM(CASE WHEN NOT is_break AND duration_minutes IS NOT NULL THEN duration_minutes ELSE 0 END), 0) as total_worked,
        COALESCE(SUM(CASE WHEN NOT is_break AND duration_minutes IS NOT NULL THEN duration_minutes ELSE 0 END), 0) as productive,
        COALESCE(SUM(CASE WHEN is_break AND duration_minutes IS NOT NULL THEN duration_minutes ELSE 0 END), 0) as break_time,
        COUNT(DISTINCT CASE WHEN NOT is_break AND task_id IS NOT NULL THEN task_id END) as tasks_count
    FROM time_entries
    WHERE user_id = user_id_val AND DATE(start_time) = report_date AND duration_minutes IS NOT NULL
    ON CONFLICT (user_id, report_date)
    DO UPDATE SET
        total_worked_minutes = EXCLUDED.total_worked_minutes,
        productive_minutes = EXCLUDED.productive_minutes,
        break_minutes = EXCLUDED.break_minutes,
        tasks_worked = EXCLUDED.tasks_worked,
        updated_at = NOW();

    RETURN COALESCE(NEW, OLD);
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS time_entries_daily_report ON time_entries;
CREATE TRIGGER time_entries_daily_report
    AFTER INSERT OR UPDATE OR DELETE ON time_entries
    FOR EACH ROW
    EXECUTE FUNCTION update_daily_time_report();

-- Update trigger for time entries
CREATE OR REPLACE FUNCTION update_time_entry_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS time_entries_updated_at ON time_entries;
CREATE TRIGGER time_entries_updated_at
    BEFORE UPDATE ON time_entries
    FOR EACH ROW
    EXECUTE FUNCTION update_time_entry_updated_at();