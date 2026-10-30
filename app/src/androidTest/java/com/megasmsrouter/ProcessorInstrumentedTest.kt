package com.megasmsrouter

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.megasmsrouter.background.DeliveryScheduler
import com.megasmsrouter.data.AppDatabase
import com.megasmsrouter.data.repository.toEntity
import com.megasmsrouter.domain.model.ApiDestination
import com.megasmsrouter.domain.model.IncomingSms
import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.security.SecretCipher
import com.megasmsrouter.sms.SmsMessageProcessor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Needs an emulator/device. Not executed in the CI workflow. */
@RunWith(AndroidJUnit4::class)
class ProcessorInstrumentedTest {
    private lateinit var db: AppDatabase
    private val queued = mutableListOf<String>()
    private var forwarding = true
    private val fakeCipher = object : SecretCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(encrypted: String) = encrypted
    }
    private lateinit var processor: SmsMessageProcessor

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        processor = SmsMessageProcessor(db, fakeCipher, { forwarding }, object : DeliveryScheduler {
            override fun enqueue(pendingId: String) { queued.add(pendingId) }
        })
        runBlocking {
            val d = ApiDestination(id = "d1", name = "dest", url = "https://example.com/x")
            db.destinationDao().upsert(d.toEntity(fakeCipher))
            val r = RoutingRule(id = "r1", name = "rule", senders = listOf("Bank"), destinationIds = listOf("d1"))
            db.ruleDao().save(r.toEntity(), r.destinationIds)
        }
    }

    @After fun tearDown() { db.close() }

    @Test fun matchingMessageIsQueuedOnceAndDuplicateIgnored() = runBlocking {
        val sms = IncomingSms("Bank", "Your code 123456", 1000L)
        assertEquals(1, processor.process(sms))
        assertEquals(0, processor.process(sms)) // duplicate
        assertEquals(1, queued.size)
        assertEquals(1, db.pendingDao().count())
    }

    @Test fun nonMatchingMessageLeavesNoTrace() = runBlocking {
        assertEquals(0, processor.process(IncomingSms("Stranger", "code 123456", 1L)))
        assertEquals(0, db.pendingDao().count())
    }

    @Test fun pausedForwardingQueuesNothing() = runBlocking {
        forwarding = false
        assertEquals(0, processor.process(IncomingSms("Bank", "code 123456", 2L)))
    }

    @Test fun deletingDestinationRemovesRuleLink() = runBlocking {
        db.destinationDao().delete("d1")
        assertTrue(db.ruleDao().getDestinationIds("r1").isEmpty())
        assertEquals(0, processor.process(IncomingSms("Bank", "code 123456", 3L)))
    }

    @Test fun configurationPersistsAcrossReads() = runBlocking {
        assertEquals("dest", db.destinationDao().getById("d1")?.name)
        assertEquals(listOf("d1"), db.ruleDao().getDestinationIds("r1"))
    }
}
