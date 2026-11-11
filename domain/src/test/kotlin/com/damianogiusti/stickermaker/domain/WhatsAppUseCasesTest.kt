package com.damianogiusti.stickermaker.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.test.runTest
import com.damianogiusti.stickermaker.domain.error.PrepareAddToWhatsAppError
import com.damianogiusti.stickermaker.domain.error.WhitelistError
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppPlan
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppRequest
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppResult
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.model.WhatsAppApp
import com.damianogiusti.stickermaker.domain.repository.WhatsAppInstallations
import com.damianogiusti.stickermaker.domain.repository.WhatsAppWhitelist
import com.damianogiusti.stickermaker.domain.usecase.CheckPackInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.GetPacksInWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.PrepareAddToWhatsAppUseCase
import com.damianogiusti.stickermaker.domain.usecase.ValidateStickerPackUseCase
import com.damianogiusti.stickermaker.domain.validation.StickerPackValidator
import com.damianogiusti.stickermaker.domain.validation.ValidationIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeInstallations(var apps: List<WhatsAppApp> = emptyList()) : WhatsAppInstallations {
    override suspend fun installedApps() = apps
}

private class FakeWhitelist(
    var answers: Map<WhatsAppApp, Either<WhitelistError, Boolean>> = emptyMap(),
) : WhatsAppWhitelist {
    val asked = mutableListOf<Pair<WhatsAppApp, StickerPackId>>()
    override suspend fun isWhitelisted(app: WhatsAppApp, authority: StickerAuthority, packId: StickerPackId):
        Either<WhitelistError, Boolean> {
        asked += app to packId
        return answers[app] ?: false.right()
    }
}

class WhatsAppUseCasesTest {
    private val authority = StickerAuthority("com.example.stickercontentprovider")
    private val id = StickerPackId("7")
    private val readyPack = aPack(id = "7", name = "Cats", stickers = List(3) { aSticker(it) })
    private val repository = FakeStickerPackRepository(listOf(readyPack))
    private val inspector = FakeStickerFileInspector(
        mapOf("tray.png" to validTrayFacts()) + (0..2).associate { "$it.webp" to validStickerFacts() },
    )
    private val installations = FakeInstallations()
    private val whitelist = FakeWhitelist()
    private val request = AddToWhatsAppRequest(id, authority, "Cats")

    private fun prepare() = PrepareAddToWhatsAppUseCase(
        repository,
        ValidateStickerPackUseCase(repository, inspector, StickerPackValidator()),
        installations,
        authority,
    )

    private fun check() = CheckPackInWhatsAppUseCase(installations, whitelist, authority)

    @Test
    fun `prepare launches directly when one app is installed`() = runTest {
        installations.apps = listOf(WhatsAppApp.BUSINESS)
        assertEquals(AddToWhatsAppPlan.Launch(request, WhatsAppApp.BUSINESS).right(), prepare()(id))
    }

    @Test
    fun `prepare asks the user when both apps are installed`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP, WhatsAppApp.BUSINESS)
        val plan = prepare()(id).getOrNull()
        assertEquals(AddToWhatsAppPlan.ChooseApp(request, listOf(WhatsAppApp.WHATSAPP, WhatsAppApp.BUSINESS)), plan)
    }

    @Test
    fun `prepare reports no WhatsApp when none is installed`() = runTest {
        assertEquals(AddToWhatsAppPlan.NoWhatsAppInstalled.right(), prepare()(id))
    }

    @Test
    fun `prepare refuses a pack that is not valid anymore`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        repository.packs.value = listOf(aPack(id = "7", stickers = listOf(aSticker(0, emojis = emptyList()))))

        val error = prepare()(id).leftOrNull()

        val notReady = assertIs<PrepareAddToWhatsAppError.NotReady>(error)
        assertEquals(true, notReady.issues.any { it is ValidationIssue.MissingEmoji })
    }

    @Test
    fun `check is false when WhatsApp is not installed and nothing is asked`() = runTest {
        assertEquals(false.right(), check()(id))
        assertEquals(emptyList(), whitelist.asked)
    }

    @Test
    fun `check is true when any installed app has the pack`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP, WhatsAppApp.BUSINESS)
        whitelist.answers = mapOf(
            WhatsAppApp.WHATSAPP to WhitelistError.Unavailable("com.whatsapp", "denied").left(),
            WhatsAppApp.BUSINESS to true.right(),
        )
        assertEquals(true.right(), check()(id))
    }

    @Test
    fun `check fails when an app failed and none said yes`() = runTest {
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        val failure = WhitelistError.Unavailable("com.whatsapp", "denied")
        whitelist.answers = mapOf(WhatsAppApp.WHATSAPP to failure.left())
        assertEquals(failure.left(), check()(id))
    }

    @Test
    fun `packs in WhatsApp lists only the whitelisted ids`() = runTest {
        repository.packs.value = listOf(aPack(id = "1"), aPack(id = "2"))
        installations.apps = listOf(WhatsAppApp.WHATSAPP)
        val useCase = object : WhatsAppWhitelist {
            override suspend fun isWhitelisted(app: WhatsAppApp, authority: StickerAuthority, packId: StickerPackId) =
                (packId.value == "2").right()
        }
        val result = GetPacksInWhatsAppUseCase(repository, CheckPackInWhatsAppUseCase(installations, useCase, authority))()
        assertEquals(setOf(StickerPackId("2")).right(), result)
    }

    @Test
    fun `result parsing follows the WhatsApp extras`() {
        fun parse(keys: Set<String>, error: String? = null, cancelled: Boolean = false) =
            AddToWhatsAppResult.fromResult(cancelled, keys, error)

        assertEquals(AddToWhatsAppResult.Added, parse(setOf("add_successful")))
        assertEquals(AddToWhatsAppResult.AlreadyAdded, parse(setOf("already_added")))
        assertEquals(AddToWhatsAppResult.Added, parse(setOf("add_successful", "validation_error"), "ignored"))
        assertEquals(AddToWhatsAppResult.Rejected("Tray too big"), parse(setOf("validation_error"), "Tray too big"))
        assertEquals(AddToWhatsAppResult.Rejected(null), parse(setOf("other")))
    }

    @Test
    fun `no extras means cancelled only when the result code says cancelled`() {
        assertEquals(AddToWhatsAppResult.Cancelled, AddToWhatsAppResult.fromResult(true, emptySet(), null))
        assertEquals(AddToWhatsAppResult.Unconfirmed, AddToWhatsAppResult.fromResult(false, emptySet(), null))
    }

    @Test
    fun `extras win over a cancelled result code`() {
        assertEquals(
            AddToWhatsAppResult.Rejected("Tray too big"),
            AddToWhatsAppResult.fromResult(true, setOf("validation_error"), "Tray too big"),
        )
    }
}
