-- Task subtasks table
CREATE TABLE IF NOT EXISTS task_subtasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    child_task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Ensure a task can't be a subtask of itself
    CONSTRAINT no_self_subtask CHECK (parent_task_id != child_task_id),

    -- Unique constraint to prevent duplicate parent-child relationships
    UNIQUE(parent_task_id, child_task_id)
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_task_subtasks_parent ON task_subtasks(parent_task_id, order_index);
CREATE INDEX IF NOT EXISTS idx_task_subtasks_child ON task_subtasks(child_task_id);

-- Function to prevent circular subtask relationships
CREATE OR REPLACE FUNCTION check_subtask_hierarchy()
RETURNS TRIGGER AS $$
DECLARE
    has_cycle BOOLEAN := FALSE;
    visited_tasks UUID[] := ARRAY[]::UUID[];
    current_task UUID;
    path_tasks UUID[] := ARRAY[]::UUID[];
BEGIN
    -- For INSERT/UPDATE operations
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        current_task := NEW.parent_task_id;
        path_tasks := ARRAY[NEW.child_task_id];

        -- Check if creating this relationship would create a cycle
        WHILE current_task IS NOT NULL LOOP
            -- If we've already visited this task, we have a cycle
            IF current_task = ANY(path_tasks) THEN
                RAISE EXCEPTION 'Creating this subtask relationship would create a circular dependency';
            END IF;

            -- Add current task to visited path
            path_tasks := path_tasks || current_task;

            -- Get the parent of current task
            SELECT parent_task_id INTO current_task
            FROM task_subtasks
            WHERE child_task_id = current_task
            LIMIT 1;
        END LOOP;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Create trigger to check for circular dependencies
DROP TRIGGER IF EXISTS subtask_hierarchy_check ON task_subtasks;
CREATE TRIGGER subtask_hierarchy_check
    BEFORE INSERT OR UPDATE ON task_subtasks
    FOR EACH ROW
    EXECUTE FUNCTION check_subtask_hierarchy();

-- Function to update task completion based on subtasks
CREATE OR REPLACE FUNCTION update_parent_task_progress()
RETURNS TRIGGER AS $$
DECLARE
    parent_id UUID;
    total_subtasks INTEGER;
    completed_subtasks INTEGER;
    completion_percentage INTEGER;
BEGIN
    -- Get the parent task ID
    IF TG_OP = 'DELETE' THEN
        parent_id := OLD.parent_task_id;
    ELSE
        parent_id := NEW.parent_task_id;
    END IF;

    -- Count total and completed subtasks
    SELECT
        COUNT(*),
        COUNT(CASE WHEN t.status IN ('Done', 'Completed') THEN 1 END)
    INTO total_subtasks, completed_subtasks
    FROM task_subtasks ts
    JOIN tasks t ON ts.child_task_id = t.id
    WHERE ts.parent_task_id = parent_id;

    -- Calculate completion percentage
    IF total_subtasks > 0 THEN
        completion_percentage := (completed_subtasks * 100) / total_subtasks;

        -- Auto-complete parent task if all subtasks are done
        IF completed_subtasks = total_subtasks THEN
            UPDATE tasks
            SET status = 'Done'
            WHERE id = parent_id AND status != 'Done';
        END IF;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update parent task progress
DROP TRIGGER IF EXISTS update_parent_progress ON task_subtasks;
CREATE TRIGGER update_parent_progress
    AFTER INSERT OR UPDATE OR DELETE ON task_subtasks
    FOR EACH ROW
    EXECUTE FUNCTION update_parent_task_progress();

-- Also trigger when task status changes
DROP TRIGGER IF EXISTS task_status_change_subtasks ON tasks;
CREATE TRIGGER task_status_change_subtasks
    AFTER UPDATE OF status ON tasks
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION update_parent_task_progress_on_status_change();

-- Function for task status change trigger
CREATE OR REPLACE FUNCTION update_parent_task_progress_on_status_change()
RETURNS TRIGGER AS $$
DECLARE
    parent_record RECORD;
BEGIN
    -- Update progress for all parent tasks of this task
    FOR parent_record IN
        SELECT parent_task_id
        FROM task_subtasks
        WHERE child_task_id = NEW.id
    LOOP
        PERFORM update_parent_task_progress_for_parent(parent_record.parent_task_id);
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Helper function to update specific parent
CREATE OR REPLACE FUNCTION update_parent_task_progress_for_parent(parent_id UUID)
RETURNS VOID AS $$
DECLARE
    total_subtasks INTEGER;
    completed_subtasks INTEGER;
BEGIN
    SELECT
        COUNT(*),
        COUNT(CASE WHEN t.status IN ('Done', 'Completed') THEN 1 END)
    INTO total_subtasks, completed_subtasks
    FROM task_subtasks ts
    JOIN tasks t ON ts.child_task_id = t.id
    WHERE ts.parent_task_id = parent_id;

    -- Auto-complete parent task if all subtasks are done
    IF total_subtasks > 0 AND completed_subtasks = total_subtasks THEN
        UPDATE tasks
        SET status = 'Done'
        WHERE id = parent_id AND status != 'Done';
    END IF;
END;
$$ LANGUAGE plpgsql;