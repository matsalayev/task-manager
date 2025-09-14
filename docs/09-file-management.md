# File Management Backend Implementation

## Hozirgi Holat

### ✅ Mavjud Funksiyalar:
- Basic S3 integration for file uploads
- Project document storage
- Telegram bot file handling

### ❌ Qo'shilishi Kerak:
- Complete file management API
- File versioning system
- Document categorization
- File sharing and permissions
- File preview generation

## Implementation Tasks

### 1. File Domain Models
**Priority: 🟡 Medium**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/files/`

```scala
case class FileDocument(
  id: FileDocumentId,
  name: String,
  originalName: String,
  filePath: String,
  fileSize: Long,
  mimeType: String,
  checksum: String, // SHA-256
  uploadedBy: UserId,
  projectId: Option[ProjectId],
  taskId: Option[TaskId],
  category: FileCategory,
  version: Int,
  parentFileId: Option[FileDocumentId], // For versioning
  isPublic: Boolean,
  downloadCount: Int,
  uploadedAt: Instant,
  updatedAt: Instant
)

sealed trait FileCategory
object FileCategory {
  case object Document extends FileCategory
  case object Image extends FileCategory
  case object Video extends FileCategory
  case object Archive extends FileCategory
  case object Code extends FileCategory
  case object Other extends FileCategory
}

case class FilePermission(
  fileId: FileDocumentId,
  userId: UserId,
  permission: Permission,
  grantedBy: UserId,
  grantedAt: Instant
)

sealed trait Permission
object Permission {
  case object Read extends Permission
  case object Write extends Permission
  case object Delete extends Permission
  case object Share extends Permission
}

case class FileShare(
  id: FileShareId,
  fileId: FileDocumentId,
  sharedBy: UserId,
  shareToken: String,
  expiresAt: Option[Instant],
  downloadLimit: Option[Int],
  downloadCount: Int,
  isActive: Boolean,
  createdAt: Instant
)
```

### 2. Database Schema
```sql
CREATE TABLE file_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    checksum VARCHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    category VARCHAR(50) DEFAULT 'Other',
    version INTEGER DEFAULT 1,
    parent_file_id UUID REFERENCES file_documents(id),
    is_public BOOLEAN DEFAULT false,
    download_count INTEGER DEFAULT 0,
    uploaded_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    INDEX idx_files_project(project_id),
    INDEX idx_files_task(task_id),
    INDEX idx_files_uploader(uploaded_by),
    INDEX idx_files_checksum(checksum)
);

CREATE TABLE file_permissions (
    file_id UUID NOT NULL REFERENCES file_documents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(20) NOT NULL,
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (file_id, user_id, permission)
);

CREATE TABLE file_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL REFERENCES file_documents(id) ON DELETE CASCADE,
    shared_by UUID NOT NULL REFERENCES users(id),
    share_token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ,
    download_limit INTEGER,
    download_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

## Estimated Time: 1-2 hafta