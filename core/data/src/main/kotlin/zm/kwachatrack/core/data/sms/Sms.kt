package zm.kwachatrack.core.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.annotation.CallSuper
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.repository.SmsInbox
import zm.kwachatrack.core.domain.repository.TransactionParser
import zm.kwachatrack.core.domain.usecase.ImportSmsUseCase
import javax.inject.Inject

/** Reads the SMS inbox through the system content provider. Requires READ_SMS. */
class ContentResolverSmsInbox
    @Inject
    constructor(
        private val context: Context,
    ) : SmsInbox {
        override suspend fun read(senderFilter: (String) -> Boolean): List<RawSms> =
            withContext(Dispatchers.IO) {
                val result = ArrayList<RawSms>()
                context.contentResolver
                    .query(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                        null,
                        null,
                        "${Telephony.Sms.DATE} ASC",
                    )?.use { c ->
                        val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                        val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                        val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                        while (c.moveToNext()) {
                            val sender = c.getString(address) ?: continue
                            // Only provider messages are ever copied out of the inbox.
                            if (!senderFilter(sender)) continue
                            result += RawSms(sender, c.getString(body).orEmpty(), c.getLong(date))
                        }
                    }
                result
            }
    }

/**
 * Hilt injects a BroadcastReceiver inside onReceive, so subclasses must call super.onReceive.
 * BroadcastReceiver.onReceive is abstract, which Kotlin won't let us call; this base class gives it a body.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}

/**
 * Receives new SMS. Multipart messages are joined, non-provider senders are dropped immediately,
 * and the parsing work is handed to WorkManager so it survives the receiver's short lifetime.
 */
@AndroidEntryPoint
class SmsReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var parser: TransactionParser

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        parts
            .filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }
            .forEach { (sender, messages) ->
                if (!parser.isProviderSender(sender)) return@forEach
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                val time = messages.minOf { it.timestampMillis }
                ImportSmsWorker.enqueue(context, RawSms(sender, body, time))
            }
    }
}

@HiltWorker
class ImportSmsWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val importSms: ImportSmsUseCase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
            val body = inputData.getString(KEY_BODY) ?: return Result.failure()
            val time = inputData.getLong(KEY_TIME, System.currentTimeMillis())
            return try {
                importSms.importOne(RawSms(sender, body, time))
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        companion object {
            private const val KEY_SENDER = "sender"
            private const val KEY_BODY = "body"
            private const val KEY_TIME = "time"

            fun enqueue(
                context: Context,
                sms: RawSms,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<ImportSmsWorker>()
                        .setInputData(workDataOf(KEY_SENDER to sms.sender, KEY_BODY to sms.body, KEY_TIME to sms.receivedAt))
                        .build()
                // Unique per message, so a duplicate broadcast does not double the work.
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork("sms-${sms.sender}-${sms.receivedAt}-${sms.body.hashCode()}", ExistingWorkPolicy.KEEP, request)
            }
        }
    }
