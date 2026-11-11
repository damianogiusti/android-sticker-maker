package com.damianogiusti.stickermaker.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import kotlinx.coroutines.flow.first
import com.damianogiusti.stickermaker.domain.error.PrepareAddToWhatsAppError
import com.damianogiusti.stickermaker.domain.error.StickerPackError
import com.damianogiusti.stickermaker.domain.error.WhitelistError
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppPlan
import com.damianogiusti.stickermaker.domain.model.AddToWhatsAppRequest
import com.damianogiusti.stickermaker.domain.model.StickerAuthority
import com.damianogiusti.stickermaker.domain.model.StickerPack
import com.damianogiusti.stickermaker.domain.model.StickerPackId
import com.damianogiusti.stickermaker.domain.repository.StickerPackRepository
import com.damianogiusti.stickermaker.domain.repository.WhatsAppInstallations
import com.damianogiusti.stickermaker.domain.repository.WhatsAppWhitelist

/** Re-validates the pack, then decides how to reach WhatsApp. */
class PrepareAddToWhatsAppUseCase(
    private val repository: StickerPackRepository,
    private val validatePack: ValidateStickerPackUseCase,
    private val installations: WhatsAppInstallations,
    private val authority: StickerAuthority,
) {
    suspend operator fun invoke(id: StickerPackId): Either<PrepareAddToWhatsAppError, AddToWhatsAppPlan> = either {
        val issues = validatePack(id).mapLeft(PrepareAddToWhatsAppError::Validation).bind()
        ensure(issues.isEmpty()) { PrepareAddToWhatsAppError.NotReady(issues) }
        val pack = repository.getPack(id).mapLeft(PrepareAddToWhatsAppError::Pack).bind()
        val request = AddToWhatsAppRequest(pack.id, authority, pack.name)
        val apps = installations.installedApps()
        when (apps.size) {
            0 -> AddToWhatsAppPlan.NoWhatsAppInstalled
            1 -> AddToWhatsAppPlan.Launch(request, apps.single())
            else -> AddToWhatsAppPlan.ChooseApp(request, apps)
        }
    }
}

/**
 * True when at least one installed WhatsApp app has the pack. Fails only when no app could be
 * asked successfully and at least one query failed.
 */
class CheckPackInWhatsAppUseCase(
    private val installations: WhatsAppInstallations,
    private val whitelist: WhatsAppWhitelist,
    private val authority: StickerAuthority,
) {
    suspend operator fun invoke(id: StickerPackId): Either<WhitelistError, Boolean> {
        val answers = installations.installedApps().map { whitelist.isWhitelisted(it, authority, id) }
        if (answers.any { answer -> answer.fold(ifLeft = { false }, ifRight = { isWhitelisted -> isWhitelisted }) }) return true.right()
        val failure = answers.firstNotNullOfOrNull { it.leftOrNull() }
        return failure?.left() ?: false.right()
    }
}

/** Ids of the packs WhatsApp already has. Fails if any check failed, so callers can keep stale data. */
class GetPacksInWhatsAppUseCase(
    private val repository: StickerPackRepository,
    private val checkPack: CheckPackInWhatsAppUseCase,
) {
    suspend operator fun invoke(): Either<GetPacksInWhatsAppError, Set<StickerPackId>> = either {
        val packs = repository.observePacks().first().mapLeft(GetPacksInWhatsAppError::Packs).bind()
        buildSet {
            for (pack in packs) {
                if (checkPack(pack.id).mapLeft(GetPacksInWhatsAppError::Whitelist).bind()) add(pack.id)
            }
        }
    }
}

sealed interface GetPacksInWhatsAppError {
    data class Packs(val error: StickerPackError) : GetPacksInWhatsAppError
    data class Whitelist(val error: WhitelistError) : GetPacksInWhatsAppError
}

/** One-shot reads for the framework entry point (ContentProvider), which cannot observe. */
class ListStickerPacksUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(): Either<StickerPackError, List<StickerPack>> =
        repository.observePacks().first()
}

class GetStickerPackUseCase(private val repository: StickerPackRepository) {
    suspend operator fun invoke(id: StickerPackId): Either<StickerPackError, StickerPack> = repository.getPack(id)
}
