package app.timetree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.timetree.patches.shared.AdsConfigConstructorFingerprint
import app.timetree.patches.shared.PACKAGE_NAME

/**
 * Turns every server-driven ad slot in `AdsConfigModel` off.
 *
 * The downloaded ads config carries one boolean per placement
 * (`optout`, `enableDaily`, `enableCreation`, `enableActivities`,
 * `enableEventDetail`, and one unnamed extra flag). Feeding `false` into those
 * constructor parameters means the app itself concludes that no slot is
 * enabled, so list items for ads are never created and no empty placeholder is
 * left behind in the calendar / activities feed.
 *
 * This is the second layer of the patch. It keeps the UI cheap and consistent
 * even if a future app version changes the render pipeline but keeps feeding
 * from `AdsConfigModel`.
 */
@Suppress("unused")
val disableAdSlotFlagsPatch = bytecodePatch(
    name = "Disable ad slot flags",
    description = "Forces every server-driven ad slot switch in AdsConfigModel to false, " +
        "so no ad slot is created at all. Fingerprints are validated against TimeTree 15.34.1.",
) {
    compatibleWith(
        Compatibility(
            packageName = PACKAGE_NAME,
            name = "TimeTree",
            description = "Shared calendar app. Patch targets the downloadable ads config.",
            apkFileType = ApkFileType.APK,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    execute {
        AdsConfigConstructorFingerprint.method.addInstructions(
            0,
            """
                const/4 p4, 0x0
                const/4 p5, 0x0
                const/4 p6, 0x0
                const/4 p7, 0x0
                const/4 p8, 0x0
                const/4 p9, 0x0
            """,
        )
    }
}
