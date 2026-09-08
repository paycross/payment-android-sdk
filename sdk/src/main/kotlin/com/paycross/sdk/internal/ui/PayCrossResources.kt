package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.paycross.sdk.internal.util.LocaleResolution
import com.paycross.sdk.internal.util.UiText
import java.util.Locale

/**
 * The resources the sheet reads its own strings from, or null to read the
 * activity's.
 *
 * The session's locale arrives with the payload, long after the activity has
 * attached its base context, and recreating the activity to apply it would throw
 * away a half-filled card form. So the language reaches the sheet as a
 * composition local instead. Not by overriding [LocalContext]: a Compose
 * `Dialog` re-provides that from its own window, which would leave the cancel and
 * remove-card dialogs in the old language, and the 3-D Secure WebView needs the
 * real Activity anyway. A local of our own crosses into a dialog's
 * sub-composition and nothing else reads it.
 */
internal val LocalPayCrossResources = compositionLocalOf<Resources?> { null }

/**
 * [id] in the language the sheet resolved, falling back to the activity's own
 * resources before anything has been provided.
 *
 * Every SDK string site goes through this rather than through Compose's
 * `stringResource`, which reads the activity's resources and so would miss a
 * session or merchant locale entirely. The one deliberate exception is Material's
 * borrowed `default_error_message`, which is not ours and ships in far more
 * languages than this SDK does.
 */
@Composable
@ReadOnlyComposable
internal fun pcStringResource(@StringRes id: Int): String =
    (LocalPayCrossResources.current ?: activityResources()).getString(id)

@Composable
@ReadOnlyComposable
internal fun pcStringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    (LocalPayCrossResources.current ?: activityResources()).getString(id, *formatArgs)

/** [text] as a sentence: SDK copy translated, a server's own message untouched. */
@Composable
@ReadOnlyComposable
internal fun pcStringResource(text: UiText): String = when (text) {
    is UiText.Raw -> text.text
    // The no-argument overload rather than an empty spread: the formatting one
    // runs the value through String.format, which would throw on a stray percent
    // in a merchant's own override of a key that takes no arguments.
    is UiText.Resource -> when {
        text.args.isEmpty() -> pcStringResource(text.id)
        else -> pcStringResource(text.id, *text.args.toTypedArray())
    }
}

/**
 * The locale the amount is formatted with, or null to use the device's.
 *
 * Separate from [LocalPayCrossResources] on purpose: the strings are clamped to
 * the two languages the SDK ships, while a number can be formatted for any
 * locale on the platform. A German shopper reads English words over a German
 * amount rather than losing their own grouping to a language gap.
 */
internal val LocalPayCrossFormattingLocale = compositionLocalOf<Locale?> { null }

/** The locale the amount is formatted with. */
internal val pcFormattingLocale: Locale
    @Composable
    @ReadOnlyComposable
    get() = LocalPayCrossFormattingLocale.current
        ?: LocalConfiguration.current.locales[0]

/**
 * The language tag the sheet resolved, for text that does not live in
 * `values-*`: the merchant's own field-group labels, placeholders, option names
 * and validation messages, which the session carries in every language the
 * backend has them in.
 *
 * The same answer as [LocalPayCrossResources], carried separately only because a
 * map lookup needs the tag as a string while `getString` needs the Resources.
 * Nothing re-resolves the language off this one — both are provided together
 * from a single [LocaleResolution] call, so the merchant's words and the SDK's
 * cannot end up in different languages.
 */
internal val LocalPayCrossLanguage = compositionLocalOf { LocaleResolution.DEFAULT.language }

/**
 * Runs [content] with the sheet's language and the amount's locale resolved from
 * [merchantLocale], [sessionLocale] and the shopper's own list of preferences.
 *
 * The whole ladder lives here rather than in the activity so that it can be
 * exercised without one: a test renders this with a session locale and reads the
 * words back, which is the wiring rather than the mechanism. They are provided
 * together because they come from one set of candidates and must never disagree
 * about which session they describe.
 */
@Composable
internal fun PayCrossLocalization(
    sessionLocale: String?,
    merchantLocale: String?,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val deviceLocales = LocalConfiguration.current.locales
    val locales = remember(merchantLocale, sessionLocale, deviceLocales) {
        LocaleResolution.sheetLocales(
            override = merchantLocale,
            session = sessionLocale,
            device = deviceLocales.toList()
        )
    }
    val resources = remember(context, locales.strings) {
        context.localizedResources(locales.strings)
    }

    CompositionLocalProvider(
        LocalPayCrossResources provides resources,
        LocalPayCrossFormattingLocale provides locales.amount,
        LocalPayCrossLanguage provides locales.strings.language,
        content = content
    )
}

/** The shopper's languages in their order of preference. */
private fun LocaleList.toList(): List<Locale> = (0 until size()).map { get(it) }

/**
 * [this] re-read in [locale]. Built from the context rather than from a bare
 * Resources so the density, font scale and night bits the sheet is already
 * running under all carry over — only the language changes.
 */
internal fun Context.localizedResources(locale: Locale): Resources {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration).resources
}

// Read through LocalConfiguration the way Compose's own stringResource does, so
// a configuration change invalidates the call rather than leaving stale text.
@Composable
@ReadOnlyComposable
private fun activityResources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}
