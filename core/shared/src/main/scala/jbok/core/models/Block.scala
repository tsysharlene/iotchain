package jbok.core.models

case class Block(header: BlockHeader, body: BlockBody) {
  lazy val tag: String = s"Block(${header.number})#${header.stateRoot.toHex.take(7)}"
}
