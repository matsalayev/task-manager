package tm.domain

case class FileMeta[F[_]](
    bytes: fs2.Stream[F, Byte],
    contentType: Option[String],
    fileName: String,
    fileSize: Long,
  )
