package jbok.core.peer

import cats.effect._
import cats.implicits._
import fs2._
import jbok.common.log.Logger
import jbok.core.messages.{NewBlock, SignedTransactions, Status}
import jbok.core.models.Block
import jbok.core.peer.PeerSelector.PeerSelector
import jbok.core.queue.Producer
import jbok.network.{Message, Request}

final class PeerManager[F[_]](
    val incoming: IncomingManager[F],
    val outgoing: OutgoingManager[F],
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = Logger[F]

  val inbound: Stream[F, (Peer[F], Message[F])] =
    incoming.inbound.consume
      .merge(outgoing.inbound.consume)
      .evalTap { case (peer, message) => log.d(s"received ${message} from ${peer.uri}") }

  val outbound: Pipe[F, (PeerSelector[F], Message[F]), Unit] =
    _.evalMap { case (selector, message) => distribute(selector, message) }

  val connected: F[List[Peer[F]]] =
    for {
      in  <- incoming.connected.get.map(_.map(kv => (kv._1.uri -> kv._2)))
      out <- outgoing.connected.get.map(_.map(kv => (kv._1.uri -> kv._2)))
    } yield (in ++ out).values.map(_._1).toList

  val seedConnected: F[List[Peer[F]]] =
    for {
      in <- incoming.connected.get
      inSeeds <- incoming.seedConnects
      out <- outgoing.connected.get
      outSeeds <- outgoing.seedConnects
      inPeers = in.filter(peer => inSeeds.contains(peer._1))
      outPeers = out.filter(peer => outSeeds.contains(peer._1))
    } yield (inPeers ++ outPeers).values.map(_._1).toList

  def distribute(selector: PeerSelector[F], message: Message[F]): F[Unit] =
    for {
      peers    <- connected
      selected <- selector.run(peers)
      _        <- selected.traverse(_.queue.enqueue1(message))
      _ <- if(selected.length>0){
        message match {
          case Request(_, SignedTransactions.name, _, _) =>
            for {
              stxs <- message.as[SignedTransactions]
              _ <- selected.traverse(_.markTxs(stxs))
              _ <- log.d(s"distribute ${stxs} to ${selected} end")
            }yield()
          case Request(_, NewBlock.name, _, _) =>
            for {
              newBlock <- message.as[NewBlock]
              blockHeader = newBlock.block.header
              _ <- selected.traverse(_.markBlock(blockHeader.hash,blockHeader.number))
            }yield ()
          case _ => F.unit
        }
      }else F.unit
    } yield ()

  def close(uri: PeerUri): F[Unit] =
    (incoming.close(uri), outgoing.close(uri)).tupled.void

  val stream: Stream[F, Unit] =
    Stream.eval_(log.i(s"starting Core/PeerManager")) ++
      Stream(
        incoming.serve,
        outgoing.connects,
      ).parJoinUnbounded

  val resource: Resource[F, PeerUri] = {
    for {
      peerUri <- incoming.resource
      _       <- outgoing.resource
    } yield peerUri
  }
}
