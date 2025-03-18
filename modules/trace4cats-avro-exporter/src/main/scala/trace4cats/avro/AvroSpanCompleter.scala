/*
 * Copyright (c) 2021-2025 Trace4Cats <https://github.com/trace4cats>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package trace4cats.avro

import cats.effect.kernel.Async
import cats.effect.kernel.Resource

import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.CompleterConfig
import trace4cats.QueuedSpanCompleter
import trace4cats.kernel.SpanCompleter
import trace4cats.model.TraceProcess

object AvroSpanCompleter {

  def udp[F[_]: Async](
      process: TraceProcess,
      host: String = agentHostname,
      port: Int = agentPort,
      config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      AvroSpanExporter.udp[F, Chunk](host, port).flatMap(QueuedSpanCompleter[F](process, _, config))
    }

  def tcp[F[_]: Async](
      process: TraceProcess,
      host: String = agentHostname,
      port: Int = agentPort,
      config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] = {
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      AvroSpanExporter.tcp[F, Chunk](host, port).flatMap(QueuedSpanCompleter[F](process, _, config))
    }
  }

}
