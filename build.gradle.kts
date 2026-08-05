plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    // Kotlin 2.x is not optional. Any dependency compiled against Kotlin 2.x
    // metadata - OkHttp 5 among them - is unreadable to a 1.9 compiler, and the
    // Flutter plugin templates a merchant would consume this from are themselves
    // Kotlin 2.x. Staying on 1.9.21 makes the SDK uncombinable with both.
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    // From Kotlin 2.0 the Compose compiler ships with Kotlin itself and is
    // applied as a plugin instead of pinned via composeOptions.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
