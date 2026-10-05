plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 has built-in Kotlin support (KGP applied transitively) - no separate
    // org.jetbrains.kotlin.android plugin. Only the Compose compiler plugin is still explicit.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
