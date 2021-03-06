package jbok.core.config

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.common.log.LogConfig
import jbok.codec.json.implicits._
import jbok.crypto.ssl.SSLConfig
import jbok.persistent.PersistConfig

@ConfiguredJsonCodec
final case class FullConfig(
    rootPath: String,
    genesis: GenesisConfig,
    log: LogConfig,
    history: HistoryConfig,
    keystore: KeyStoreConfig,
    peer: PeerConfig,
    sync: SyncConfig,
    txPool: TxPoolConfig,
    blockPool: BlockPoolConfig,
    mining: MiningConfig,
    persist: PersistConfig,
    ssl: SSLConfig,
    db: DatabaseConfig,
    service: ServiceConfig
)
