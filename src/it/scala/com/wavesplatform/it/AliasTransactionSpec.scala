package com.wavesplatform.it

import org.scalatest.{FreeSpec, Matchers}
import scorex.transaction.TransactionParser.TransactionType

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

class AliasTransactionSpec(allNodes: Seq[Node]) extends FreeSpec with Matchers {
  "Able to send money to an alias" in {
    val alias = "TEST_ALIAS"
    val asset = "WAVES"
    val Seq(creator, sender) = Random.shuffle(allNodes).take(2)
    val feeSettings = creator.settings.feesSettings.fees

    val createAliasFee = feeSettings(TransactionType.CreateAliasTransaction.id).find(_.asset == asset).get.fee
    val transferFee = feeSettings(TransactionType.TransferTransaction.id).find(_.asset == asset).get.fee

    val transferResult = for {
      fb <- creator.lastBlock
      t <- creator.createAlias(creator.address, alias, createAliasFee)
      b <- creator.findBlock(_.transactions.exists(_.id == t.id), fb.height)
      _ <- sender.findBlock(_.transactions.exists(_.id == t.id), b.height)
      t <- sender.transfer(sender.address, s"alias:${sender.settings.blockchainSettings.addressSchemeCharacter}:$alias", 1000000, transferFee)
    } yield t

    Await.result(transferResult, 1.minute)
  }

  "Able to issue an alias and send money to an alias" in {

  }

  "unable to send to non-issued alias" in {

  }

  "unable to lease to non-issued alias" in {

  }

  "unable to create the same alias in the next block" in {

  }

  "able to recreate alias after rollback" in pending
}
