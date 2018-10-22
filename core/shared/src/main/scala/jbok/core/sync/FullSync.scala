package jbok.core.sync
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import fs2._
import jbok.core.config.Configs.SyncConfig
import jbok.core.ledger.BlockExecutor
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{PeerId, PeerManager}
import jbok.core.pool.TxPool

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * FullSync should download the block headers, the block bodies
  * then import them into the local chain (validate and execute)
  */
case class FullSync[F[_]](
    config: SyncConfig,
    peerManager: PeerManager[F],
    executor: BlockExecutor[F],
    txPool: TxPool[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F], EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  def stream: Stream[F, Unit] =
    Stream.awakeEvery[F](config.checkForNewBlockInterval).evalMap { _ =>
      for {
        bestPeerOpt <- getBestPeer
        _ <- bestPeerOpt match {
          case Some(peerId) =>
            log.info(s"about to sync with ${peerId}")
            requestPeer(peerId)
          case None =>
            log.info(s"no peer for syncing now, retry in ${config.checkForNewBlockInterval}")
            F.unit
        }
      } yield ()
    }

  def start: F[Unit] =
    F.start(stream.compile.drain).void

  private def getBestPeer: F[Option[PeerId]] =
    for {
      peers <- peerManager.handshakedPeers.map(_.values.toList)
      best = Try(peers.maxBy(_.peerInfo.status.bestNumber).peerId).toOption
    } yield best

  private def requestPeer(peerId: PeerId): F[Unit] =
    for {
      current <- executor.history.getBestBlockNumber
      requestNumber = current + 1
      _             = log.info(s"request ${config.blockHeadersPerRequest} headers at most, starting from ${requestNumber}")
      response <- peerManager.sendRequest(
        peerId,
        GetBlockHeaders(Left(requestNumber), config.blockHeadersPerRequest, 0, false),
        Some(config.peerResponseTimeout)
      )
      _ <- response match {
        case Some(BlockHeaders(headers, _)) =>
          if (headers.isEmpty) {
            F.delay(log.info(s"got empty headers from ${peerId}, retry in ${config.checkForNewBlockInterval}"))
          } else {
            log.info(s"got ${headers.length} headers from ${peerId}")
            handleBlockHeaders(peerId, current, headers)
          }
        case _ =>
          log.info(s"got headers timeout from ${peerId}, retry in ${config.checkForNewBlockInterval}")
          F.unit
      }
    } yield ()

  private def handleBlockHeaders(peerId: PeerId, currentNumber: BigInt, headers: List[BlockHeader]): F[Unit] =
    if (!checkHeaders(headers) || headers.last.number < currentNumber) {
      log.warn(s"got invalid block headers from ${peerId}")
      F.unit
    } else {
      log.info(s"received headers: ${headers.mkString("\n")}")

      require(headers.head.number == 1)

      val parentIsKnown = executor.history
        .getBlockHeaderByHash(headers.head.parentHash)
        .map(_.isDefined)

      F.ifM(parentIsKnown)(
        ifTrue = {
          // find blocks with same numbers in the current chain, removing any common prefix
          for {
            blocks <- headers.map(_.number).traverse(executor.history.getBlockByNumber)
            (oldBranch, _) = blocks.flatten
              .zip(headers)
              .dropWhile {
                case (oldBlock, header) =>
                  oldBlock.header == header
              }
              .unzip
            newHeaders              = headers.dropWhile(h => oldBranch.headOption.exists(_.header.number > h.number))
            currentBranchDifficulty = oldBranch.map(_.header.difficulty).sum
            newBranchDifficulty     = newHeaders.map(_.difficulty).sum
            _ <- if (currentBranchDifficulty < newBranchDifficulty) {
              handleBetterBranch(peerId, oldBranch, headers)
            } else {
              log.warn(s"no chain switch, ignore")
              F.unit
            }
          } yield ()
        },
        ifFalse = {
          log.warn(s"the parent of the headers is unknown")
          F.unit
        }
      )
    }

  private def handleBetterBranch(peerId: PeerId, oldBranch: List[Block], headers: List[BlockHeader]): F[Unit] = {
    val transactionsToAdd = oldBranch.flatMap(_.body.transactionList)
    for {
      _ <- txPool.addTransactions(transactionsToAdd)
      hashes = headers.take(config.blockBodiesPerRequest).map(_.hash)
      _      = log.info(s"request ${hashes.length} bodies, starting from ${headers.head.number}")
      response <- peerManager.sendRequest(peerId, GetBlockBodies(hashes), Some(config.peerResponseTimeout))
      _ <- response match {
        case Some(BlockBodies(bodies, _)) =>
          if (bodies.isEmpty) {
            F.delay(log.info(s"got empty bodies from ${peerId}, retry in ${config.checkForNewBlockInterval}"))
          } else {
            log.info(s"got ${bodies.length} bodies from ${peerId}")
            handleBlockBodies(peerId, headers, bodies)
          }
        case _ =>
          log.info(s"got bodies timeout from ${peerId}, retry in ${config.checkForNewBlockInterval}")
          F.unit
      }
    } yield ()
  }

  private def checkHeaders(headers: List[BlockHeader]): Boolean =
    if (headers.length > 1)
      headers.zip(headers.tail).forall {
        case (parent, child) =>
          parent.hash == child.parentHash && parent.number + 1 == child.number
      } else
      headers.nonEmpty

  private def handleBlockBodies(peerId: PeerId, headers: List[BlockHeader], bodies: List[BlockBody]): F[Unit] = {
    val blocks = headers.zip(bodies).map { case (header, body) => Block(header, body) }
    for {
      imported <- executor.importBlocks(blocks)
      _ = log.info(s"successfully synced to ${imported.head.tag}")
    } yield ()
  }

}
