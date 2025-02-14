package tm.support.skunk.syntax

import cats.effect._
import cats.implicits._
import eu.timepit.refined.types.numeric.PosInt
import org.typelevel.log4cats.Logger
import skunk._
import skunk.codec.all._
import skunk.implicits._
import tm.syntax.refined.commonSyntaxAutoUnwrapV

trait SkunkSyntax {
  implicit def skunkSyntaxCommandOps[A](cmd: Command[A]): CommandOps[A] =
    new CommandOps(cmd)
  implicit def skunkSyntaxQueryVoidOps[B](query: Query[Void, B]): QueryVoidOps[B] =
    new QueryVoidOps(query)
  implicit def skunkSyntaxQueryOps[A, B](query: Query[A, B]): QueryOps[A, B] =
    new QueryOps(query)
  implicit def skunkSyntaxFragmentOps(af: AppliedFragment): FragmentOps =
    new FragmentOps(af)

  implicit def skunkSyntaxConnectionOps[F[_]](
      postgres: Resource[F, Session[F]]
    ): ConnectionOps[F] = new ConnectionOps[F](postgres)
}

final class ConnectionOps[F[_]](
    postgres: Resource[F, Session[F]]
  ) {
  def checkPostgresConnection(
      implicit
      F: MonadCancel[F, Throwable],
      logger: Logger[F],
    ): F[Unit] =
    postgres.use { session =>
      session.unique(sql"select version();".query(text)).flatMap { v =>
        logger.info(s"Connected to Postgres $v")
      }
    }
}
final class QueryOps[A, B](query: Query[A, B]) {
  def queryM[F[_], G[_]](
      action: PreparedQuery[F, A, B] => F[G[B]]
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[G[B]] =
    session.use {
      _.prepare(query).flatMap(action)
    }

  def query[F[_]](
      action: PreparedQuery[F, A, B] => F[B]
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[B] =
    session.use {
      _.prepare(query).flatMap(action)
    }

  def queryUnique[F[_]](
      args: A
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[B] =
    query { prepQuery: PreparedQuery[F, A, B] =>
      prepQuery.unique(args)
    }

  def queryList[F[_]: fs2.Compiler.Target](
      args: A
    )(implicit
      session: Resource[F, Session[F]]
    ): F[List[B]] =
    queryM { prepQuery: PreparedQuery[F, A, B] =>
      prepQuery.stream(args, 1024).compile.toList
    }

  def queryStream[F[_]](
      args: A
    )(implicit
      sessionRes: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): fs2.Stream[F, B] =
    for {
      session <- fs2.Stream.resource(sessionRes)
      stream <- session.stream(query)(args, 128)
    } yield stream

  def queryOption[F[_]](
      args: A
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[Option[B]] =
    queryM { prepQuery: PreparedQuery[F, A, B] =>
      prepQuery.option(args)
    }
}
final class QueryVoidOps[B](query: Query[Void, B]) {
  def all[F[_]](
      implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[List[B]] =
    session.use {
      _.execute(query)
    }

  def queryStream[F[_]](
      query: Query[Void, B]
    )(implicit
      sessionRes: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): fs2.Stream[F, B] =
    for {
      session <- fs2.Stream.resource(sessionRes)
      stream <- session.stream(query)(Void, 128)
    } yield stream
}

final class CommandOps[A](cmd: Command[A]) {
  def action[F[_], B](
      action: PreparedCommand[F, A] => F[B]
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[B] =
    session.use {
      _.prepare(cmd).flatMap(action)
    }

  def execute[F[_]](
      args: A
    )(implicit
      session: Resource[F, Session[F]],
      ev: MonadCancel[F, Throwable],
    ): F[Unit] =
    action[F, Unit] {
      _.execute(args).void
    }
}

final class FragmentOps(af: AppliedFragment) {
  def order(by: String): AppliedFragment = {
    val fragment: Fragment[Void] = sql" ORDER BY #$by"
    af |+| fragment(Void)
  }

  def paginate(lim: Int, index: Int): AppliedFragment = {
    val offset = (index - 1) * lim
    val filter: Fragment[Int *: Int *: EmptyTuple] = sql" LIMIT $int4 OFFSET $int4 "
    af |+| filter(lim *: offset *: EmptyTuple)
  }

  def paginateOpt(maybeLim: Option[PosInt], maybeIndex: Option[PosInt]): AppliedFragment =
    (maybeLim, maybeIndex)
      .mapN {
        case lim ~ index =>
          val offset = (index - 1) * lim
          val filter: Fragment[Int *: Int *: EmptyTuple] = sql" LIMIT $int4 OFFSET $int4 "
          af |+| filter(lim.value *: offset *: EmptyTuple)
      }
      .getOrElse(af)

  /** Returns `WHERE (f1) AND (f2) AND ... (fn)` for defined `f`, if any, otherwise the empty fragment. */

  def whereAndOpt(fs: Option[AppliedFragment]*): AppliedFragment = {
    val filters =
      if (fs.flatten.isEmpty)
        AppliedFragment.empty
      else
        fs.flatten.foldSmash(void" WHERE ", void" AND ", AppliedFragment.empty)
    af |+| filters
  }

  def whereAndOpt(fs: List[Option[AppliedFragment]]): AppliedFragment =
    whereAndOpt(fs: _*)

  def andOpt(fs: List[Option[AppliedFragment]]): AppliedFragment =
    andOpt(fs: _*)

  def andOpt(fs: Option[AppliedFragment]*): AppliedFragment = {
    val filters =
      if (fs.flatten.isEmpty)
        AppliedFragment.empty
      else
        fs.flatten.foldSmash(void" AND ", void" AND ", AppliedFragment.empty)
    af |+| filters
  }
}
