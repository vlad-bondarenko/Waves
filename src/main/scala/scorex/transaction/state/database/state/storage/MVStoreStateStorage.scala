package scorex.transaction.state.database.state.storage

import org.h2.mvstore.{MVMap, MVStore}
import scorex.account.Account
import scorex.crypto.encode.Base58
import scorex.transaction._
import scorex.transaction.state.database.state._
import scorex.utils.LogMVMapBuilder

import scala.collection.JavaConverters._


trait MVStoreStateStorage extends StateStorageI {
  val db: MVStore

  // ============= Account Changes
  private lazy val AccountChanges = "AccountChanges"
  private lazy val accountChanges: MVMap[String, Row] =
    db.openMap(AccountChanges, new LogMVMapBuilder[String, Row].valueType(RowDataType))

  private def acKey(address: AddressString, height: Int): String = height + "-" + address

  def getAccountChanges(key: AddressString, height: Int): Option[Row] = Option(accountChanges.get(acKey(key, height)))

  def putAccountChanges(key: AddressString, height: Int, data: Row): Unit = accountChanges.put(acKey(key, height), data)

  def removeAccountChanges(key: AddressString, height: Int): Row = accountChanges.remove(acKey(key, height))


  // ============= Last States
  private lazy val LastStates = "lastStates"
  private lazy val lastStates: MVMap[AddressString, Int] = db.openMap(LastStates, new LogMVMapBuilder[AddressString, Int])

  def getLastStates(a: AddressString): Option[Int] = Option(lastStates.get(a))

  def putLastStates(a: AddressString, s: Int): Unit = lastStates.put(a, s)

  def lastStatesKeys: List[AddressString] = lastStates.keySet().asScala.toList


  // ============= Last States
  private lazy val AccountAssets = "accountAssets"
  private lazy val accountAssetsMap: MVMap[String, Set[String]] = db.openMap(AccountAssets,
    new LogMVMapBuilder[String, Set[String]])

  def updateAccountAssets(address: AddressString, assetId: Option[AssetId]): Unit = {
    if (assetId.isDefined) {
      val asset = Base58.encode(assetId.get)
      val assets = Option(accountAssetsMap.get(address)).getOrElse(Set.empty[String])
      accountAssetsMap.put(address, assets + asset)
    }
  }

  def getAccountAssets(address: AddressString): Set[String] =
    Option(accountAssetsMap.get(address)).getOrElse(Set.empty[String])


  // ============= transactions
  private lazy val AllTxs = "IssueTxs"
  private lazy val IncludedTx = "includedTx"

  /**
    * Transaction ID -> serialized transaction
    */
  private lazy val transactionsMap: MVMap[Array[Byte], Array[Byte]] = db.openMap(AllTxs, new LogMVMapBuilder[Array[Byte], Array[Byte]])
  /**
    * Transaction ID -> Block height
    */
  private lazy val includedTx: MVMap[Array[Byte], Int] = db.openMap(IncludedTx, new LogMVMapBuilder[Array[Byte], Int])

  override def getTransactionBytes(id: Array[Byte]): Option[Array[Byte]] = Option(transactionsMap.get(id))

  override def putTransaction(tx: Transaction, height: Int): Unit = {
    includedTx.put(tx.id, height)
    transactionsMap.put(tx.id, tx.bytes)
  }

  override def removeTransaction(id: Array[Byte]): Unit = {
    includedTx.remove(id)
    transactionsMap.remove(id)
  }

  override def included(id: Array[Byte], heightOpt: Option[Int]): Option[Int] =
    Option(includedTx.get(id)).filter(_ < heightOpt.getOrElse(Int.MaxValue))


  // ============= state height
  private lazy val HeightKey = "height"
  private lazy val heightMap: MVMap[String, Int] = db.openMap(HeightKey, new LogMVMapBuilder[String, Int])

  def stateHeight: Int = Option(heightMap.get(HeightKey)).getOrElse(0)

  def setStateHeight(height: Int): Unit = heightMap.put(HeightKey, height)

  def assetDistribution(assetId: Array[Byte]): Map[String, Long] = {
    val encodedAssetId = Base58.encode(assetId)

    accountAssetsMap.entrySet().asScala
      .filter(e => e.getValue.contains(encodedAssetId))
      .map(e => {
        val assetAcc: AssetAcc = AssetAcc(Account.fromString(e.getKey).right.get, Some(assetId))
        val key = assetAcc.key
        val balance = getAccountChanges(key, getLastStates(key).get).get.state.balance
        (e.getKey, balance)
      }).toMap
  }

}
