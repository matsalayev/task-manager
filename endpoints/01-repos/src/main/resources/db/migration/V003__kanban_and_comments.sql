-- Add new columns to tasks table for Kanban and enhanced functionality
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS priority VARCHAR(20);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS position INTEGER DEFAULT 0;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS estimated_hours INTEGER;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id UUID REFERENCES tasks(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP WITH TIME ZONE;

-- Add constraints for valid enum values
ALTER TABLE tasks ADD CONSTRAINT IF NOT EXISTS valid_priority
    CHECK (priority IS NULL OR priority IN ('Low', 'Medium', 'High', 'Critical'));

-- Create index for better performance on Kanban queries
CREATE INDEX IF NOT EXISTS idx_tasks_project_status_position ON tasks(project_id, status, position);
CREATE INDEX IF NOT EXISTS idx_tasks_parent ON tasks(parent_task_id) WHERE parent_task_id IS NOT NULL;

-- Task comments table
CREATE TABLE IF NOT EXISTS task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    parent_comment_id UUID REFERENCES task_comments(id) ON DELETE CASCADE,
    is_edited BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Task attachments table
CREATE TABLE IF NOT EXISTS task_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Task dependencies table
CREATE TABLE IF NOT EXISTS task_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dependent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    dependency_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    dependency_type VARCHAR(20) NOT NULL DEFAULT 'FinishToStart',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Ensure a task cannot depend on itself
    CONSTRAINT no_self_dependency CHECK (dependent_task_id != dependency_task_id),

    -- Unique constraint to prevent duplicate dependencies
    UNIQUE(dependent_task_id, dependency_task_id)
);

-- Task watchers table (for notifications)
CREATE TABLE IF NOT EXISTS task_watchers (
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (task_id, user_id)
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_task_comments_task ON task_comments(task_id, created_at);
CREATE INDEX IF NOT EXISTS idx_task_comments_author ON task_comments(author_id);
CREATE INDEX IF NOT EXISTS idx_task_comments_parent ON task_comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_task_attachments_task ON task_attachments(task_id, uploaded_at);
CREATE INDEX IF NOT EXISTS idx_task_attachments_uploader ON task_attachments(uploaded_by);

CREATE INDEX IF NOT EXISTS idx_task_dependencies_dependent ON task_dependencies(dependent_task_id);
CREATE INDEX IF NOT EXISTS idx_task_dependencies_dependency ON task_dependencies(dependency_task_id);

CREATE INDEX IF NOT EXISTS idx_task_watchers_user ON task_watchers(user_id);

-- Add constraints for valid enum values
ALTER TABLE task_dependencies ADD CONSTRAINT IF NOT EXISTS valid_dependency_type
    CHECK (dependency_type IN ('FinishToStart', 'StartToStart', 'FinishToFinish', 'StartToFinish'));

-- Trigger to update task updated_at when comments are added
CREATE OR REPLACE FUNCTION update_task_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE tasks SET updated_at = NOW() WHERE id = NEW.task_id;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS task_comments_update_task ON task_comments;
CREATE TRIGGER task_comments_update_task
    AFTER INSERT ON task_comments
    FOR EACH ROW
    EXECUTE FUNCTION update_task_updated_at();

-- Trigger to update comment updated_at
CREATE OR REPLACE FUNCTION update_comment_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    NEW.is_edited = true;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS task_comments_updated_at ON task_comments;
CREATE TRIGGER task_comments_updated_at
    BEFORE UPDATE ON task_comments
    FOR EACH ROW
    EXECUTE FUNCTION update_comment_updated_at();