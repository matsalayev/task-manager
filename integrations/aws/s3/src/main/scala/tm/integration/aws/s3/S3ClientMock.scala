package tm.integration.aws.s3

import java.net.URL

import cats.Applicative
import com.amazonaws.services.s3.model.Bucket
import fs2.Pipe

class S3ClientMock[F[_]: Applicative] extends S3Client[F] {
  override def listFiles: fs2.Stream[F, String] =
    fs2.Stream.empty
  override def listBuckets: fs2.Stream[F, Bucket] =
    fs2.Stream.empty
  override def downloadObject(key: String): fs2.Stream[F, Byte] =
    fs2.Stream.empty
  override def deleteObject(key: String): fs2.Stream[F, Unit] =
    fs2.Stream.empty
  override def putObject(key: String): Pipe[F, Byte, Unit] =
    _ => fs2.Stream.empty
  override def uploadFileMultipart(key: String, chunkSize: Int): Pipe[F, Byte, String] =
    _ => fs2.Stream.empty
  override def generatePresignedUrl(key: String, publicRead: Boolean): F[URL] = ???

  override def generateUrl(key: String): F[URL] = ???
}
