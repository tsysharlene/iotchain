package jbok.network.transport

import java.net.InetSocketAddress

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2.io.udp
import fs2.io.udp.{AsynchronousSocketGroup, Packet}
import fs2.{Pipe, _}
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

case class UdpTransport[F[_], A](
    bind: InetSocketAddress,
    timeout: Option[FiniteDuration] = None
)(
    implicit F: ConcurrentEffect[F],
    C: Codec[A],
    EC: ExecutionContext
) {
  private[this] val log = org.log4s.getLogger

  implicit val AG = AsynchronousSocketGroup()

  private def decodeChunk(chunk: Chunk[Byte]): F[A] =
    F.delay(Codec[A].decode(BitVector(chunk.toArray)).require.value)

  private def encodeChunk(a: A): F[Chunk[Byte]] =
    F.delay(Chunk.array(Codec[A].encode(a).require.toByteArray))

  def serve(pipe: Pipe[F, (InetSocketAddress, A), (InetSocketAddress, A)]): Stream[F, Unit] =
    udp
      .open[F](bind, reuseAddress = true)
      .flatMap { socket =>
        log.info(s"udp transport bound at ${bind}")
        socket
          .reads(timeout)
          .evalMap(p =>
            decodeChunk(p.bytes).map(a => {
              log.info(s"received msg from ${p.remote}")
              p.remote -> a
            }))
          .through(pipe)
          .evalMap {
            case (remote, a) =>
              encodeChunk(a).map(chunk => Packet(remote, chunk))
          }
          .to(socket.writes(timeout))
      }
      .handleErrorWith(e => Stream.eval(F.delay(log.warn(e)(s"udp transport error"))))
      .onFinalize(F.delay(log.info(s"udp transport serving terminated")))

  def send(remote: InetSocketAddress, a: A, timeout: Option[FiniteDuration] = None): F[Unit] = {
    log.info(s"sending msg to ${remote}")
    val s = for {
      socket <- udp.open[F](bind, reuseAddress = true)
      chunk  <- Stream.eval(encodeChunk(a))
      packet = Packet(remote, chunk)
      _ <- Stream.eval(socket.write(packet, timeout))
    } yield ()
    s.compile.drain
  }
}