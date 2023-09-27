package app.aaps.database.impl.transactions

import app.aaps.database.entities.TemporaryBasal

class UpdateNsIdTemporaryBasalTransaction(private val temporaryBasals: List<TemporaryBasal>) : Transaction<UpdateNsIdTemporaryBasalTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (temporaryBasal in temporaryBasals) {
            val current = database.temporaryBasalDao.findById(temporaryBasal.id)
            if (current != null && current.interfaceIDs.nightscoutId != temporaryBasal.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = temporaryBasal.interfaceIDs.nightscoutId
                database.temporaryBasalDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<TemporaryBasal>()
    }
}