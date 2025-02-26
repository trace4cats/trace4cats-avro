package trace4cats.avro

import cats.data.NonEmptyList
import cats.free.FreeApplicative
import cats.syntax.apply._
import cats.syntax.either._
import cats.{ApplicativeThrow, Eval}
import org.apache.avro.Schema
import trace4cats.model._
import vulcan.{AvroError, Codec}

object AvroInstances {
  private val modelNs = "trace4cats.model"

  implicit val spanIdCodec: Codec[SpanId] =
    Codec.bytes.imapError(SpanId(_).toRight(AvroError("Invalid Span ID")))(_.value)

  implicit val traceIdCodec: Codec[TraceId] =
    Codec.bytes.imapError(TraceId(_).toRight(AvroError("Invalid Trace ID")))(_.value)

  implicit val traceStateKeyCodec: Codec[TraceState.Key] =
    Codec.string.imapError(TraceState.Key(_).toRight(AvroError("Invalid trace state key")))(_.k)

  implicit val traceStateValueCodec: Codec[TraceState.Value] =
    Codec.string.imapError(TraceState.Value(_).toRight(AvroError("Invalid trace state value")))(_.v)

  implicit val traceStateCodec: Codec[TraceState] = Codec
    .map[TraceState.Value]
    .imap(_.flatMap { case (k, v) => TraceState.Key(k).map(_ -> v) })(_.map { case (k, v) => k.k -> v })
    .imapError[TraceState](TraceState(_).toRight(AvroError("Invalid trace state size")))(_.values)

  implicit val traceFlagsCodec: Codec[TraceFlags] =
    Codec.boolean.imap(sampled => TraceFlags(SampleDecision.fromBoolean(sampled)))(_.sampled.toBoolean)

  implicit val parentCodec: Codec[Parent] =
    Codec.record[Parent](name = "Parent", namespace = modelNs) { field =>
      (field("spanId", _.spanId), field("isRemote", _.isRemote)).mapN(Parent.apply)
    }

  implicit val spanContextCodec: Codec[SpanContext] =
    Codec.record[SpanContext](name = "SpanContext", namespace = modelNs) { field =>
      (
        field("traceId", _.traceId),
        field("spanId", _.spanId),
        field("parent", _.parent),
        field("traceFlags", _.traceFlags),
        field("traceState", _.traceState),
        field("isRemote", _.isRemote)
      ).mapN(SpanContext.apply)
    }

  implicit def evalCodec[A: Codec]: Codec[Eval[A]] =
    Codec[A].imap(Eval.later(_))(_.value)

  implicit val traceValueCodec: Codec[AttributeValue] = {
    import AttributeValue._
    val ns = s"$modelNs.AttributeValue"

    implicit val boolValueCodec: Codec[BooleanValue] = Codec.boolean.imap(BooleanValue(_))(_.value.value)
    implicit val doubleValueCodec: Codec[DoubleValue] = Codec.double.imap(DoubleValue(_))(_.value.value)
    implicit val longValueCodec: Codec[LongValue] = Codec.long.imap(LongValue(_))(_.value.value)
    implicit val stringValueCodec: Codec[StringValue] = Codec.string.imap(StringValue(_))(_.value.value)

    implicit val boolListCodec: Codec[BooleanList] =
      Codec.record[BooleanList](name = "BooleanList", namespace = ns) { field =>
        field("value", _.value).map(BooleanList.apply)
      }
    implicit val doubleListCodec: Codec[DoubleList] =
      Codec.record[DoubleList](name = "DoubleList", namespace = ns) { field =>
        field("value", _.value).map(DoubleList.apply)
      }
    implicit val longListCodec: Codec[LongList] =
      Codec.record[LongList](name = "LongList", namespace = ns) { field =>
        field("value", _.value).map(LongList.apply)
      }
    implicit val stringListCodec: Codec[StringList] =
      Codec.record[StringList](name = "StringList", namespace = ns) { field =>
        field("value", _.value).map(StringList.apply)
      }

    Codec.union(alt =>
      NonEmptyList
        .of(
          // alphabetical order
          alt[BooleanList],
          alt[BooleanValue],
          alt[DoubleList],
          alt[DoubleValue],
          alt[LongList],
          alt[LongValue],
          alt[StringList],
          alt[StringValue]
        )
        .reduce
    )
  }

  implicit val attributesCodec: Codec[Map[String, AttributeValue]] = Codec.map[AttributeValue]

  implicit val spanKindCodec: Codec[SpanKind] = {
    import SpanKind._
    val ns = s"$modelNs.SpanKind"

    implicit val serverCodec: Codec[Server.type] =
      Codec.record[Server.type](name = "Server", namespace = ns)(_ => FreeApplicative.pure(Server))
    implicit val clientCodec: Codec[Client.type] =
      Codec.record[Client.type](name = "Client", namespace = ns)(_ => FreeApplicative.pure(Client))
    implicit val producerCodec: Codec[Producer.type] =
      Codec.record[Producer.type](name = "Producer", namespace = ns)(_ => FreeApplicative.pure(Producer))
    implicit val consumerCodec: Codec[Consumer.type] =
      Codec.record[Consumer.type](name = "Consumer", namespace = ns)(_ => FreeApplicative.pure(Consumer))
    implicit val internalCodec: Codec[Internal.type] =
      Codec.record[Internal.type](name = "Internal", namespace = ns)(_ => FreeApplicative.pure(Internal))

    Codec.union(alt =>
      NonEmptyList
        .of(
          // alphabetical order
          alt[Client.type],
          alt[Consumer.type],
          alt[Internal.type],
          alt[Producer.type],
          alt[Server.type]
        )
        .reduce
    )
  }

  implicit val linkCodec: Codec[Link] = Codec.record[Link](name = "Link", namespace = modelNs) { field =>
    (field("traceId", _.traceId), field("spanId", _.spanId)).mapN(Link.apply)
  }

  implicit val linksCodec: Codec[NonEmptyList[Link]] = Codec.nonEmptyList[Link]

  implicit val metaTraceCodec: Codec[MetaTrace] =
    Codec.record[MetaTrace](name = "MetaTrace", namespace = modelNs) { field =>
      (field("traceId", _.traceId), field("spanId", _.spanId)).mapN(MetaTrace.apply)
    }

  implicit val spanStatusCodec: Codec[SpanStatus] = {
    import SpanStatus._
    val ns = s"$modelNs.SpanStatus"

    implicit val okCodec: Codec[Ok.type] =
      Codec.record[Ok.type](name = "Ok", namespace = ns)(_ => FreeApplicative.pure(Ok))
    implicit val cancelledCodec: Codec[Cancelled.type] =
      Codec.record[Cancelled.type](name = "Cancelled", namespace = ns)(_ => FreeApplicative.pure(Cancelled))
    implicit val unknownCodec: Codec[Unknown.type] =
      Codec.record[Unknown.type](name = "Unknown", namespace = ns)(_ => FreeApplicative.pure(Unknown))
    implicit val invalidArgumentCodec: Codec[InvalidArgument.type] =
      Codec.record[InvalidArgument.type](name = "InvalidArgument", namespace = ns)(_ =>
        FreeApplicative.pure(InvalidArgument)
      )
    implicit val deadlineExceededCodec: Codec[DeadlineExceeded.type] =
      Codec.record[DeadlineExceeded.type](name = "DeadlineExceeded", namespace = ns)(_ =>
        FreeApplicative.pure(DeadlineExceeded)
      )
    implicit val notFoundCodec: Codec[NotFound.type] =
      Codec.record[NotFound.type](name = "NotFound", namespace = ns)(_ => FreeApplicative.pure(NotFound))
    implicit val alreadyExistsCodec: Codec[AlreadyExists.type] =
      Codec.record[AlreadyExists.type](name = "AlreadyExists", namespace = ns)(_ => FreeApplicative.pure(AlreadyExists))
    implicit val permissionDeniedCodec: Codec[PermissionDenied.type] =
      Codec.record[PermissionDenied.type](name = "PermissionDenied", namespace = ns)(_ =>
        FreeApplicative.pure(PermissionDenied)
      )
    implicit val resourceExhaustedCodec: Codec[ResourceExhausted.type] =
      Codec.record[ResourceExhausted.type](name = "ResourceExhausted", namespace = ns)(_ =>
        FreeApplicative.pure(ResourceExhausted)
      )
    implicit val failedPreconditionCodec: Codec[FailedPrecondition.type] =
      Codec.record[FailedPrecondition.type](name = "FailedPrecondition", namespace = ns)(_ =>
        FreeApplicative.pure(FailedPrecondition)
      )
    implicit val abortedCodec: Codec[Aborted.type] =
      Codec.record[Aborted.type](name = "Aborted", namespace = ns)(_ => FreeApplicative.pure(Aborted))
    implicit val outOfRangeCodec: Codec[OutOfRange.type] =
      Codec.record[OutOfRange.type](name = "OutOfRange", namespace = ns)(_ => FreeApplicative.pure(OutOfRange))
    implicit val unimplementedCodec: Codec[Unimplemented.type] =
      Codec.record[Unimplemented.type](name = "Unimplemented", namespace = ns)(_ => FreeApplicative.pure(Unimplemented))
    implicit val internalCodec: Codec[Internal] = Codec.record[Internal](name = "Internal", namespace = ns)(field =>
      field("message", _.message).map(Internal.apply)
    )
    implicit val unavailableCodec: Codec[Unavailable.type] =
      Codec.record[Unavailable.type](name = "Unavailable", namespace = ns)(_ => FreeApplicative.pure(Unavailable))
    implicit val dataLossCodec: Codec[DataLoss.type] =
      Codec.record[DataLoss.type](name = "DataLoss", namespace = ns)(_ => FreeApplicative.pure(DataLoss))
    implicit val unauthenticatedCodec: Codec[Unauthenticated.type] =
      Codec.record[Unauthenticated.type](name = "Unauthenticated", namespace = ns)(_ =>
        FreeApplicative.pure(Unauthenticated)
      )

    Codec.union(alt =>
      NonEmptyList
        .of(
          // alphabetical order
          alt[Aborted.type],
          alt[AlreadyExists.type],
          alt[Cancelled.type],
          alt[DataLoss.type],
          alt[DeadlineExceeded.type],
          alt[FailedPrecondition.type],
          alt[Internal],
          alt[InvalidArgument.type],
          alt[NotFound.type],
          alt[Ok.type],
          alt[OutOfRange.type],
          alt[PermissionDenied.type],
          alt[ResourceExhausted.type],
          alt[Unauthenticated.type],
          alt[Unavailable.type],
          alt[Unimplemented.type],
          alt[Unknown.type]
        )
        .reduce
    )
  }

  implicit val completedSpanCodec: Codec[CompletedSpan] =
    Codec.record[CompletedSpan](name = "CompletedSpan", namespace = modelNs) { field =>
      (
        field("context", _.context),
        field("name", _.name),
        field("serviceName", _.serviceName),
        field("kind", _.kind),
        field("start", _.start),
        field("end", _.end),
        field("attributes", _.attributes),
        field("status", _.status),
        field("links", _.links),
        field("metaTrace", _.metaTrace),
      ).mapN(CompletedSpan.apply)
    }

  implicit val processCodec: Codec[TraceProcess] =
    Codec.record[TraceProcess](name = "TraceProcess", namespace = modelNs) { field =>
      (
        field("serviceName", _.serviceName),
        field("attributes", _.attributes, default = Some(Map.empty[String, AttributeValue]))
      ).mapN(TraceProcess.apply)
    }

  def completedSpanSchema[F[_]: ApplicativeThrow]: F[Schema] =
    ApplicativeThrow[F].fromEither(completedSpanCodec.schema.leftMap(_.throwable))
}
