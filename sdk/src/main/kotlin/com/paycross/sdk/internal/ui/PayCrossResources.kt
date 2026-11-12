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

// WHY: a Compose Dialog re-provides LocalContext from its own window, so the language cannot ride on it.
internal val LocalPayCrossResources = compositionLocalOf<Resources?> { null }

// WHY: Compose's stringResource reads the activity's locale, so SDK strings must not use it.
@Composable
@ReadOnlyComposable
internal fun pcStringResource(@StringRes id: Int): String =
    (LocalPayCrossResources.current ?: activityResources()).getString(id)

@Composable
@ReadOnlyComposable
internal fun pcStringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    (LocalPayCrossResources.current ?: activityResources()).getString(id, *formatArgs)

@Composable
@ReadOnlyComposable
internal fun pcStringResource(text: UiText): String = when (text) {
    is UiText.Raw -> text.text
    // WHY: a key with no arguments skips String.format, which throws on a stray percent sign.
    is UiText.Resource -> when {
        text.args.isEmpty() -> pcStringResource(text.id)
        else -> pcStringResource(text.id, *text.args.toTypedArray())
    }
}

// WHY: amounts keep the device's grouping, while strings are limited to the languages the SDK ships.
internal val LocalPayCrossFormattingLocale = compositionLocalOf<Locale?> { null }

internal val pcFormattingLocale: Locale
    @Composable
    @ReadOnlyComposable
    get() = LocalPayCrossFormattingLocale.current
        ?: LocalConfiguration.current.locales[0]

internal val LocalPayCrossLanguage = compositionLocalOf { LocaleResolution.DEFAULT.language }

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

private fun LocaleList.toList(): List<Locale> = (0 until size()).map { get(it) }

// WHY: built from the context, so density, font scale and night mode carry over.
internal fun Context.localizedResources(locale: Locale): Resources {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration).resources
}

// WHY: reads LocalConfiguration, so a configuration change recomposes the caller.
@Composable
@ReadOnlyComposable
private fun activityResources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}
