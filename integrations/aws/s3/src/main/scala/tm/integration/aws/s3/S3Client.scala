package tm.integration.aws.s3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.ZonedDateTime
import java.util.Date

import scala.jdk.CollectionConverters._

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxFlatMapOps
import cats.implicits.catsSyntaxOptionId
import com.amazonaws.ClientConfiguration
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.model._
import fs2._

import tm.syntax.refined._

trait S3Client[F[_]] {
  private[this] val defaultChunkSize = 5 * 1024 * 1024

  def listFiles: Stream[F, String]

  def listBuckets: Stream[F, Bucket]

  def downloadObject(key: String): Stream[F, Byte]

  def deleteObject(key: String): Stream[F, Unit]

  def putObject(key: String): Pipe[F, Byte, Unit]

  def uploadFileMultipart(key: String, chunkSize: Int = defaultChunkSize): Pipe[F, Byte, String]

  def generatePresignedUrl(key: String, publicRead: Boolean = false): F[URL]

  def generateUrl(key: String): F[URL]
}

object S3Client {
  def resource[F[_]: Async](awsConfig: AWSConfig): Resource[F, S3Client[F]] =
    for {
      s3 <- Resource.eval(make[F](awsConfig))
    } yield new S3ClientImpl[F](awsConfig, s3)

  def make[F[_]](
      awsConfig: AWSConfig
    )(implicit
      F: Sync[F]
    ): F[AmazonS3] =
    F.delay {
      val clientConfiguration = new ClientConfiguration()
      clientConfiguration.setSignerOverride("AWSS3V4SignerType")

      AmazonS3ClientBuilder
        .standard()
        .withEndpointConfiguration(
          new AwsClientBuilder.EndpointConfiguration(
            awsConfig.serviceEndpoint,
            awsConfig.signingRegion,
          )
        )
        .withPathStyleAccessEnabled(true)
        .withCredentials(
          new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(awsConfig.accessKey.value, awsConfig.secretKey.value)
          )
        )
        .withClientConfiguration(clientConfiguration)
        .build()
    }

  private class S3ClientImpl[F[_]: Async] private[s3] (
      awsConfig: AWSConfig,
      s3: AmazonS3,
    )(implicit
      F: Sync[F]
    ) extends S3Client[F] {
    private def expireTime(): Date =
      Date.from(ZonedDateTime.now().plusDays(1).toInstant)

    /** Uploads a file in a single request. Suitable for small files.
      *
      * For big files, consider using [[uploadFileMultipart]] instead.
      */

    override def putObject(key: String): Pipe[F, Byte, Unit] =
      (s: Stream[F, Byte]) =>
        for {
          is <- s.through(io.toInputStream)
          byteArrayOutputStream = new ByteArrayOutputStream()
          _ = is.transferTo(byteArrayOutputStream)
          byteArray = byteArrayOutputStream.toByteArray
          contentLength = byteArray.length.toLong
          inputStream = new ByteArrayInputStream(byteArray)
          metadata = new ObjectMetadata()
          _ = metadata.setContentLength(contentLength)
          _ = s3.putObject {
            val uploadRequest =
              new PutObjectRequest(
                awsConfig.bucketName,
                key,
                inputStream,
                metadata,
              ).withCannedAcl(CannedAccessControlList.Private)
            uploadRequest
          }
        } yield ()

    /** <p>Uploads a file in multiple parts of the specified <b color="yellow">partSize</b> per request. Suitable for
      * big files.</p>
      *
      * It does so in constant memory. So at a given time, only the number of bytes indicated by @partSize will be
      * loaded in memory.
      *
      * For small files, consider using [[putObject]] instead.
      *
      * @param chunkSize
      *   the part size indicated in MBs. It must be at least <b color="green">5</b>, as required by AWS.
      */

    override def uploadFileMultipart(
        key: String,
        chunkSize: Int,
      ): Pipe[F, Byte, String] = {

      val initiateMultipartUpload: F[String] =
        F.delay(
          s3
            .initiateMultipartUpload(
              new InitiateMultipartUploadRequest(awsConfig.bucketName, key)
            )
            .getUploadId
        )

      def uploadPart(uploadId: String): Pipe[F, (Chunk[Byte], Int), PartETag] =
        _.flatMap {
          case (c, i) =>
            for {
              is <- fs2.Stream.chunk(c).through(io.toInputStream)
              partReq = s3.uploadPart {
                val uploadPartRequest = new UploadPartRequest()
                uploadPartRequest.withBucketName(awsConfig.bucketName)
                uploadPartRequest.withKey(key)
                uploadPartRequest.withUploadId(uploadId)
                uploadPartRequest.withPartNumber(i)
                uploadPartRequest.setPartSize(c.size.toLong)
                uploadPartRequest.withInputStream(is)
                uploadPartRequest
              }
            } yield partReq.getPartETag
        }

      def completeUpload(uploadId: String): Pipe[F, List[PartETag], String] =
        _.evalMap { tags =>
          s3.completeMultipartUpload(
            new CompleteMultipartUploadRequest(awsConfig.bucketName, key, uploadId, tags.asJava)
          ).getETag
            .pure[F]
        }

      def cancelUpload(uploadId: String) =
        F.delay(
          s3
            .abortMultipartUpload(
              new AbortMultipartUploadRequest(awsConfig.bucketName, key, uploadId)
            )
        )

      in =>
        fs2
          .Stream
          .eval(initiateMultipartUpload)
          .flatMap { uploadId =>
            in.chunkMin(chunkSize)
              .zip(fs2.Stream.iterate(1)(_ + 1))
              .through(uploadPart(uploadId))
              .fold[List[PartETag]](List.empty)(_ :+ _)
              .through(completeUpload(uploadId))
              .handleErrorWith(ex =>
                fs2.Stream.eval(cancelUpload(uploadId) >> F.raiseError[String](ex))
              )
          }
    }

    /** <b color='green'>Download a file in a single request. Suitable for small files.</b>
      */

    override def downloadObject(key: String): Stream[F, Byte] =
      io.readInputStream(
        Sync[F].delay(
          s3.getObject(awsConfig.bucketName, key).getObjectContent
        ),
        chunkSize = 1024 * 1024,
      )

    /** <b color="green">Delete a file in a single request.</b>
      */

    override def deleteObject(key: String): Stream[F, Unit] =
      Stream.eval(
        F.delay(s3.deleteObject(awsConfig.bucketName, key))
      )

    override def listFiles: Stream[F, String] =
      Pagination.offsetUnfoldChunkEval[F, String, String] { maybeMarker =>
        val request = new ListObjectsRequest().withBucketName(awsConfig.bucketName)
        maybeMarker.foreach(request.setMarker)

        val res = s3.listObjects(request)
        val resultChunk =
          Chunk.seq(res.getObjectSummaries.asScala).map(_.getKey)
        val maybeNextMarker = Option(res.getNextMarker)

        F.delay((resultChunk, maybeNextMarker))
      }

    override def listBuckets: Stream[F, Bucket] =
      Stream.fromIterator(s3.listBuckets().asScala.iterator, 1024)

    override def generatePresignedUrl(key: String, publicRead: Boolean = false): F[URL] =
      F.delay {
        val acl = if (publicRead) CannedAccessControlList.PublicRead.some else None
        val presignedUrlRequest = new GeneratePresignedUrlRequest(awsConfig.bucketName, key)
          .withMethod(HttpMethod.GET)
          .withExpiration(expireTime())
        acl
          .map(_.toString)
          .foreach(presignedUrlRequest.addRequestParameter(Headers.S3_CANNED_ACL, _))

        s3.generatePresignedUrl(presignedUrlRequest)
      }

    override def generateUrl(key: String): F[URL] =
      F.delay {
        s3.getUrl(awsConfig.bucketName, key)
      }
  }
}
