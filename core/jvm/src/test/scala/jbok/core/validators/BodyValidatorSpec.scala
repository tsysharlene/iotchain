package jbok.core.validators

import cats.effect.IO
import cats.implicits._
import jbok.codec.rlp.RlpEncoded
import jbok.core.models._
import jbok.core.validators.BodyInvalid.BodyTransactionsHashInvalid
import scodec.bits._
import jbok.core.CoreSpec

class BodyValidatorSpec extends CoreSpec {
  val validBlockHeader = BlockHeader(
    parentHash = hex"8345d132564b3660aa5f27c9415310634b50dbc92579c65a0825d9a255227a71",
    beneficiary = hex"df7d7e053933b5cc24372f878c90e62dadad5d42",
    stateRoot = hex"087f96537eba43885ab563227262580b27fc5e6516db79a6fc4d3bcd241dda67",
    transactionsRoot = hex"0x8ae451039a8bf403b899dcd23252d94761ddd23b88c769d9b7996546edc47fac",
    receiptsRoot = hex"8b472d8d4d39bae6a5570c2a42276ed2d6a56ac51a1a356d5b17c5564d01fd5d",
    logsBloom = ByteVector.fromValidHex("0" * 512),
    difficulty = BigInt("14005986920576"),
    number = 3125369,
    gasLimit = 4699996,
    gasUsed = 84000,
    unixTimestamp = 1486131165,
    extra = RlpEncoded.emptyList
  )

  val txs = List[SignedTransaction](
    SignedTransaction(
      Transaction(
        nonce = BigInt("438550"),
        gasPrice = BigInt("20000000000"),
        gasLimit = BigInt("50000"),
        receivingAddress = Address(hex"ee4439beb5c71513b080bbf9393441697a29f478").some,
        value = BigInt("1265230129703017984"),
        payload = ByteVector.empty
      ),
      0x9d.toByte,
      hex"5b496e526a65eac3c4312e683361bfdb873741acd3714c3bf1bcd7f01dd57ccb",
      hex"3a30af5f529c7fc1d43cfed773275290475337c5e499f383afd012edcc8d7299"
    ),
    SignedTransaction(
      Transaction(
        nonce = BigInt("438551"),
        gasPrice = BigInt("20000000000"),
        gasLimit = BigInt("50000"),
        receivingAddress = Address(hex"c68e9954c7422f479e344faace70c692217ea05b").some,
        value = BigInt("656010196207162880"),
        payload = ByteVector.empty
      ),
      0x9d.toByte,
      hex"377e542cd9cd0a4414752a18d0862a5d6ced24ee6dba26b583cd85bc435b0ccf",
      hex"579fee4fd96ecf9a92ec450be3c9a139a687aa3c72c7e43cfac8c1feaf65c4ac"
    ),
    SignedTransaction(
      Transaction(
        nonce = BigInt("438552"),
        gasPrice = BigInt("20000000000"),
        gasLimit = BigInt("50000"),
        receivingAddress = Address(hex"19c5a95eeae4446c5d24363eab4355157e4f828b").some,
        value = BigInt("3725976610361427456"),
        payload = ByteVector.empty
      ),
      0x9d.toByte,
      hex"a70267341ba0b33f7e6f122080aa767d52ba4879776b793c35efec31dc70778d",
      hex"3f66ed7f0197627cbedfe80fd8e525e8bc6c5519aae7955e7493591dcdf1d6d2"
    ),
    SignedTransaction(
      Transaction(
        nonce = BigInt("438553"),
        gasPrice = BigInt("20000000000"),
        gasLimit = BigInt("50000"),
        receivingAddress = Address(hex"3435be928d783b7c48a2c3109cba0d97d680747a").some,
        value = BigInt("108516826677274384"),
        payload = ByteVector.empty
      ),
      0x9d.toByte,
      hex"beb8226bdb90216ca29967871a6663b56bdd7b86cf3788796b52fd1ea3606698",
      hex"2446994156bc1780cb5806e730b171b38307d5de5b9b0d9ad1f9de82e00316b5"
    )
  )

  val validBlockBody = BlockBody(
    transactionList = txs
  )

  val validReceipts = List(
    Receipt(
      postTransactionStateHash = hex"ce0ac687bb90d457b6573d74e4a25ea7c012fee329eb386dbef161c847f9842d",
      cumulativeGasUsed = 21000,
      logsBloomFilter = ByteVector.fromValidHex("0" * 512),
      logs = List[TxLogEntry](),
      txHash = txs(0).hash,
      contractAddress = None,
      gasUsed = 21000,
      status = true,
      blockNumber = 1
    ),
    Receipt(
      postTransactionStateHash = hex"b927d361126302acaa1fa5e93d0b7e349e278231fe2fc2846bfd54f50377f20a",
      cumulativeGasUsed = 42000,
      logsBloomFilter = ByteVector.fromValidHex("0" * 512),
      logs = List[TxLogEntry](),
      txHash = txs(1).hash,
      contractAddress = None,
      gasUsed = 21000,
      status = true,
      blockNumber = 1
    ),
    Receipt(
      postTransactionStateHash = hex"1e913d6bdd412d71292173d7908f8792adcf958b84c89575bc871a1decaee56d",
      cumulativeGasUsed = 63000,
      logsBloomFilter = ByteVector.fromValidHex("0" * 512),
      logs = List[TxLogEntry](),
      txHash = txs(2).hash,
      contractAddress = None,
      gasUsed = 21000,
      status = true,
      blockNumber = 1
    ),
    Receipt(
      postTransactionStateHash = hex"0c6e052bc83482bafaccffc4217adad49f3a9533c69c820966d75ed0154091e6",
      cumulativeGasUsed = 84000,
      logsBloomFilter = ByteVector.fromValidHex("0" * 512),
      logs = List[TxLogEntry](),
      txHash = txs(3).hash,
      contractAddress = None,
      gasUsed = 21000,
      status = true,
      blockNumber = 1
    )
  )

  val block = Block(validBlockHeader, validBlockBody)

  "BodyValidator" should {
    "return a failure if a block body doesn't corresponds to a block header due to wrong tx hash" in {
      BodyValidator
        .validate[IO](Block(validBlockHeader, validBlockBody.copy(transactionList = validBlockBody.transactionList.reverse)))
        .attempt
        .unsafeRunSync() shouldBe Left(BodyTransactionsHashInvalid)
    }
  }
}
