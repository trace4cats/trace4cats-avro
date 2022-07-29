package trace4cats.avro

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.kernel.SpanCompleter
import trace4cats.model.TraceProcess
import trace4cats.{CompleterConfig, QueuedSpanCompleter}

object AvroSpanCompleter {
  def udp[F[_]: Async](
    process: TraceProcess,
    host: String = agentHostname,
    port: Int = agentPort,
    config: CompleterConfig = CompleterConfig(),
    encoder: Option[CompletedSpanEncoder[F]] = None,
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      AvroSpanExporter.udp[F, Chunk](host, port, encoder = encoder).flatMap(QueuedSpanCompleter[F](process, _, config))
    }

  def tcp[F[_]: Async](
    process: TraceProcess,
    host: String = agentHostname,
    port: Int = agentPort,
    config: CompleterConfig = CompleterConfig(),
    encoder: Option[CompletedSpanEncoder[F]] = None,
  ): Resource[F, SpanCompleter[F]] = {
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      AvroSpanExporter.tcp[F, Chunk](host, port, encoder = encoder).flatMap(QueuedSpanCompleter[F](process, _, config))
    }
  }
}
