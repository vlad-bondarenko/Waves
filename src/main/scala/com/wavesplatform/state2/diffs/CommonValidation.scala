package com.wavesplatform.state2.diffs

import com.wavesplatform.settings.FunctionalitySettings
import com.wavesplatform.state2.EqByteArray
import com.wavesplatform.state2.reader.StateReader
import scorex.account.{Account, Alias}
import scorex.transaction.ValidationError.TransactionValidationError
import scorex.transaction._
import scorex.transaction.assets.exchange.ExchangeTransaction
import scorex.transaction.assets.{BurnTransaction, IssueTransaction, ReissueTransaction, TransferTransaction}
import scorex.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}

import scala.util.{Left, Right}
import scala.concurrent.duration._

object CommonValidation {

  def disallowDuplicateIds[T <: Transaction](state: StateReader, settings: FunctionalitySettings, tx: T): Either[ValidationError, T] = tx match {
    case ptx: PaymentTransaction if ptx.timestamp < settings.requirePaymentUniqueId => Right(tx)
    case _: Transaction => if (state.transactionInfo(EqByteArray(tx.id)).isEmpty) Right(tx)
    else Left(TransactionValidationError(tx, "(except for some cases of PaymentTransaction) cannot be duplicated"))
  }

  def disallowBeforeActivationTime[T <: Transaction](state: StateReader, settings: FunctionalitySettings, tx: T): Either[ValidationError, T] = tx match {
    case tx: BurnTransaction if tx.timestamp <= settings.allowBurnTransactionAfterTimestamp =>
      Left(TransactionValidationError(tx, s"must not appear before time=${settings.allowBurnTransactionAfterTimestamp}"))
    case tx: LeaseTransaction if tx.timestamp <= settings.allowLeaseTransactionAfterTimestamp =>
      Left(TransactionValidationError(tx, s"must not appear before time=${settings.allowLeaseTransactionAfterTimestamp}"))
    case tx: LeaseCancelTransaction if tx.timestamp <= settings.allowLeaseTransactionAfterTimestamp =>
      Left(TransactionValidationError(tx, s"must not appear before time=${settings.allowLeaseTransactionAfterTimestamp}"))
    case tx: ExchangeTransaction if tx.timestamp <= settings.allowExchangeTransactionAfterTimestamp =>
      Left(TransactionValidationError(tx, s"must not appear before time=${settings.allowExchangeTransactionAfterTimestamp}"))
    case tx: CreateAliasTransaction if tx.timestamp <= settings.allowCreateAliasTransactionAfterTimestamp =>
      Left(TransactionValidationError(tx, s"must not appear before time=${settings.allowCreateAliasTransactionAfterTimestamp}"))
    case _: BurnTransaction => Right(tx)
    case _: PaymentTransaction => Right(tx)
    case _: GenesisTransaction => Right(tx)
    case _: TransferTransaction => Right(tx)
    case _: IssueTransaction => Right(tx)
    case _: ReissueTransaction => Right(tx)
    case _: ExchangeTransaction => Right(tx)
    case _: LeaseTransaction => Right(tx)
    case _: LeaseCancelTransaction => Right(tx)
    case _: CreateAliasTransaction => Right(tx)
    case x => Left(TransactionValidationError(x, "Unknown transaction must be explicitly registered within ActivatedValidator"))
  }

  def disallowTxFromFuture[T <: Transaction](state: StateReader, settings: FunctionalitySettings, time: Long, tx: T): Either[ValidationError, T] = {
    val allowTransactionsFromFutureByTimestamp = tx.timestamp < settings.allowTransactionsFromFutureUntil
    if (allowTransactionsFromFutureByTimestamp) {
      Right(tx)
    } else {
      if ((tx.timestamp - time).millis <= SimpleTransactionModule.MaxTimeTransactionOverBlockDiff)
        Right(tx)
      else Left(TransactionValidationError(tx, s"Transaction is from far future. BlockTime: $time"))
    }
  }
}

