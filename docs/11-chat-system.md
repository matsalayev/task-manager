# Chat System Backend Implementation

## UI Requirements Analysis

### Qo'shilish Kerak:
- Real-time team messaging
- Project-based chat rooms
- Direct messages
- File sharing in chat
- Chat history and search

## Implementation Tasks

### 1. Chat Domain Models
**Priority: 🟢 Low**
**Fayl**: `endpoints/00-domain/src/main/scala/tm/domain/chat/`

```scala
case class ChatRoom(
  id: ChatRoomId,
  name: String,
  description: Option[String],
  roomType: ChatRoomType,
  projectId: Option[ProjectId],
  createdBy: UserId,
  isActive: Boolean,
  createdAt: Instant
)

sealed trait ChatRoomType
object ChatRoomType {
  case object Direct extends ChatRoomType
  case object Group extends ChatRoomType
  case object Project extends ChatRoomType
  case object Team extends ChatRoomType
}

case class ChatMessage(
  id: ChatMessageId,
  roomId: ChatRoomId,
  senderId: UserId,
  content: String,
  messageType: MessageType,
  attachments: List[MessageAttachment],
  replyToId: Option[ChatMessageId],
  isEdited: Boolean,
  sentAt: Instant,
  editedAt: Option[Instant]
)

sealed trait MessageType
object MessageType {
  case object Text extends MessageType
  case object File extends MessageType
  case object System extends MessageType
}

case class MessageAttachment(
  id: MessageAttachmentId,
  messageId: ChatMessageId,
  fileName: String,
  filePath: String,
  fileSize: Long,
  mimeType: String
)
```

### 2. WebSocket Support
```scala
// Real-time chat via WebSocket
object ChatWebSocket {
  def routes[F[_]: Async](
    chatService: ChatService[F],
    authMiddleware: AuthMiddleware[F]
  ): HttpRoutes[F] = {
    // WebSocket endpoints for real-time chat
  }
}
```

## Estimated Time: 2-3 hafta