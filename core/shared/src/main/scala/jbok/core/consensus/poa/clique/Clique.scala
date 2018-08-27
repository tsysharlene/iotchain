package jbok.core.consensus.poa.clique

import jbok.core.consensus.{ChainReader, Engine}
import jbok.core.models._
import scodec.bits._
import cats.effect.Sync
import Clique._
import cats.data.OptionT
import jbok.persistent.{KeyValueDB, KeyValueStore, LruMap}
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.signature.CryptoSignature
import jbok.crypto.signature.ecdsa.SecP256k1

import scala.concurrent.duration._

class SnapshotStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, String](ByteVector("clique".getBytes()), db)

class Clique[F[_]](
    val config: CliqueConfig,
    val chain: ChainReader[F],
    val store: SnapshotStore[F],
    val recents: LruMap[ByteVector, Snapshot],
    val proposals: Map[Address, Boolean] // Current list of proposals we are pushing
)(implicit F: Sync[F])
    extends Engine[F](chain) {
  private[this] val log = org.log4s.getLogger

  override def author(header: BlockHeader): F[Address] =
    F.delay(ecrecover(header))

  override def verifyHeader(header: BlockHeader, seal: Boolean): F[Unit] =
    F.unit

  override def verifyHeaders(headers: List[BlockHeader], seals: List[Boolean]): F[Unit] =
    F.unit

  override def verifyUncles(block: Block): F[Unit] =
    F.unit

  override def verifySeal(header: BlockHeader): F[Unit] =
    F.unit

  override def prepare(header: BlockHeader): F[Unit] = {
    // If the block isn't a checkpoint, cast a random vote (good enough for now)
    val number = header.number
    F.unit
  }

  override def finalize(
      header: BlockHeader,
      txs: List[SignedTransaction],
      uncles: List[BlockHeader],
      receipts: List[Receipt]
  ): F[Block] = ???

  override def seal(block: Block): F[Block] = ???

  override def calcDifficulty(time: Long, parent: BlockHeader): F[BigInt] = ???

  /////////////////////////
  /////////////////////////

  private[jbok] def readSnapshot(number: BigInt, hash: ByteVector): OptionT[F, Snapshot] = {
    // try to read snapshot from cache or db
    log.info(s"try to read snapshot @ number ${number} from cache")
    OptionT
      .fromOption[F](recents.get(hash)) // If an in-memory snapshot was found, use that
      .orElseF(
        if (number % checkpointInterval == 0) {
          // If an on-disk checkpoint snapshot can be found, use that
          log.info(s"try to read snapshot @ number ${number} from db")
          Snapshot.loadSnapshot[F](store, hash)
        } else {
          log.info(s"snapshot @ number ${number} not found in cache and db")
          F.pure(None)
        }
      )
  }

  private[jbok] def genesisSnapshot: F[Snapshot] = {
    log.info(s"making a genesis snapshot")
    for {
      genesis <- chain.getHeaderByNumber(0)
      n = (genesis.extraData.length - extraVanity - extraSeal).toInt / 20
      signers: Set[Address] = (0 until n)
        .map(i => Address(genesis.extraData.slice(i * 20 + extraVanity, i * 20 + extraVanity + 20)))
        .toSet
      snap = Snapshot(config, 0, genesis.hash, signers)
      _ <- Snapshot.storeSnapshot[F](snap, store)
      _ = log.info(s"stored genesis with ${signers.size} signers")
    } yield snap
  }

  private[jbok] def snapshot(number: BigInt,
                             hash: ByteVector,
                             parents: List[BlockHeader],
                             headers: List[BlockHeader] = Nil): F[Snapshot] = {
    val snap = readSnapshot(number, hash)
      .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        log.info(s"applying ${headers.length} headers")
        val newSnap = Snapshot.applyHeaders(s, headers)
        recents.put(newSnap.hash, newSnap)
        // If we've generated a new checkpoint snapshot, save to disk
        if (newSnap.number % checkpointInterval == 0 && headers.nonEmpty) {
          Snapshot.storeSnapshot[F](newSnap, store).map(_ => newSnap)
        } else {
          F.pure(newSnap)
        }
      case None => // No snapshot for this header, gather the header and move backward(recur)
        for {
          (h, p) <- if (parents.nonEmpty) {
            // If we have explicit parents, pick from there (enforced)
            F.pure((parents.last, parents.slice(0, parents.length - 1)))
          } else {
            // No explicit parents (or no more left), reach out to the database
            chain.getHeader(hash, number).map(header => header -> parents)
          }
          snap <- snapshot(number - 1, h.parentHash, p, h :: headers)
        } yield snap
    }
  }
}

object Clique {
  val extraVanity = 32 // Fixed number of extra-data prefix bytes reserved for signer vanity
  val extraSeal   = 65 // Fixed number of extra-data suffix bytes reserved for signer seal
  val uncleHash   = RlpCodec.encode(()).require.bytes.kec256 // Always Keccak256(RLP([])) as uncles are meaningless outside of PoW.
  val diffInTurn  = BigInt(2) // Block difficulty for in-turn signatures
  val diffNoTurn  = BigInt(1) // Block difficulty for out-of-turn signatures

  val checkpointInterval = 1024 // Number of blocks after which to save the vote snapshot to the database
  val inMemorySnapshots  = 128  // Number of recent vote snapshots to keep in memory
  val inMemorySignatures = 1024 // Number of recent blocks to keep in memory

  val wiggleTime = 500.millis // Random delay (per signer) to allow concurrent signers

  val nonceAuthVote = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.

  def apply[F[_]: Sync](config: CliqueConfig, chain: ChainReader[F], db: KeyValueDB[F]): Clique[F] =
    new Clique[F](config,
                  chain,
                  new SnapshotStore[F](db),
                  new LruMap[ByteVector, Snapshot](inMemorySnapshots),
                  Map.empty)

  def sigHash(header: BlockHeader): ByteVector = {
    val bytes = RlpCodec.encode(header.copy(extraData = header.extraData.dropRight(65))).require.bytes
    bytes.kec256
  }

  def ecrecover(header: BlockHeader): Address = {
    // Retrieve the signature from the header extra-data
    val signature = header.extraData.takeRight(65)
    val hash      = sigHash(header)
    val sig       = CryptoSignature(signature.toArray)
    val public    = SecP256k1.recoverPublic(hash.toArray, sig, None).get
    Address(public.bytes.kec256)
  }
}
