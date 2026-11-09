package app.timetree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.timetree.patches.shared.ADS_AD_EMPTY
import app.timetree.patches.shared.AdsStreamFingerprint
import app.timetree.patches.shared.PACKAGE_NAME

/**
 * Short-circuits the ad pipeline at its source.
 *
 * `AdsStreamFingerprint` matches the `Function1.invoke(Object): Object` lambda that
 * TimeTree uses to build the `Observable<AdsAdModel>` for every ad placement
 * (home calendar headline, activities headline, event creation, event detail, ...).
 *
 * The original body loads the downloaded ad config, branches on the placement type and
 * then either emits a real ad or a "premium upsell" pseudo ad. Replacing the body with
 * `Observable.just(AdsAdModel.Empty)` means the UI layer receives the model it already
 * knows how to render as "there is no ad", so no banner, no card, no placeholder and no
 * reservation of vertical space happens.
 */
@Suppress("unused")
val removeInFeedAdsPatch = bytecodePatch(
    name = "Remove in-app ads",
    description = "Emits AdsAdModel.Empty for every ad placement, so calendar banners, " +
        "in-feed ad cards and premium-upsell ad slots are never rendered. Fingerprints are validated against TimeTree 15.34.1.",
) {
    compatibleWith(
        Compatibility(
            packageName = PACKAGE_NAME,
            name = "TimeTree",
            description = "Shared calendar app. Patch targets the ad pipeline of the " +
                "free (non-Premium) build.",
            apkFileType = ApkFileType.APK,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    execute {
        AdsStreamFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, $ADS_AD_EMPTY->a0:$ADS_AD_EMPTY
                invoke-static {v0}, Lio/reactivex/Observable;->f(Ljava/lang/Object;)Lio/reactivex/Observable;
                move-result-object v0
                return-object v0
            """,
        )
    }
}
