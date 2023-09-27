package app.aaps.database.impl.transactions

import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.interfaces.end

class CancelCurrentTemporaryTargetIfAnyTransaction(
    val timestamp: Long
) : Transaction<CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.temporaryTargetDao.getTemporaryTargetActiveAt(timestamp).blockingGet()
        if (current != null) {
            current.end = timestamp
            database.temporaryTargetDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<TemporaryTarget>()
    }
}