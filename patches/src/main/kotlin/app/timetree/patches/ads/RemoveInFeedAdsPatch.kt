package app.timetree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.timetree.patches.shared.ACC_STATIC
import app.timetree.patches.shared.ADS_AD_EMPTY
import app.timetree.patches.shared.AdsStreamFingerprint
import app.timetree.patches.shared.PACKAGE_NAME
import app.timetree.patches.shared.RX_OBSERVABLE
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Short-circuits the ad pipeline at its source.
 *
 * `AdsStreamFingerprint` matches the `Function1.invoke(Object): Object` lambda that TimeTree
 * uses to build the `Observable<AdsAdModel>` for every ad placement (home calendar headline,
 * activities headline, event creation, event detail, ...).
 *
 * The original body loads the downloaded ad config, branches on the placement type and then
 * emits either a real ad or a "premium upsell" pseudo ad. Replacing the body with
 * `Observable.just(AdsAdModel.Empty)` hands the UI layer the model it already knows how to
 * render as "there is no ad", so no banner, no card and no placeholder is ever drawn.
 *
 * Every reference used by the injected code is taken from the target method itself:
 *
 * - the `AdsAdModel.Empty` singleton field is discovered from that class, because R8 renames
 *   the field (`a0` in 15.34.1) and only the class name is stable, and
 * - the `Observable.just(Object)` reference is copied verbatim out of the original body,
 *   because R8 renames `just` to `f` *and* narrows its return type to `ObservableJust`.
 *   Writing `Observable;->f(Ljava/lang/Object;)Lio/reactivex/Observable;` by hand would
 *   produce a method id that no class defines, and ART resolves calls on the exact
 *   name+descriptor pair, so the app would die with NoSuchMethodError at runtime.
 */
@Suppress("unused")
val removeInFeedAdsPatch = bytecodePatch(
    name = "Remove in-app ads",
    description = "Emits AdsAdModel.Empty for every ad placement, so calendar banners, " +
        "in-feed ad cards and premium-upsell ad slots are never rendered. " +
        "Anchored on unobfuscated domain types and reuses the app's own method ids, " +
        "so it survives minor app updates.",
) {
    compatibleWith(
        Compatibility(
            packageName = PACKAGE_NAME,
            name = "TimeTree",
            description = "Shared calendar app. Patch targets the ad stream of the free build.",
            apkFileType = ApkFileType.APK,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    execute {
        val emptyClass = classDefBy(ADS_AD_EMPTY)
        val singleton = emptyClass.fields.firstOrNull {
            it.type == ADS_AD_EMPTY && (it.accessFlags and ACC_STATIC) != 0
        } ?: throw IllegalStateException(
            "AdsAdModel.Empty no longer exposes a static instance field",
        )

        val originalInstructions = AdsStreamFingerprint.originalMethod.implementation
            ?.instructions
            ?: throw IllegalStateException("ad stream lambda has no body")

        val justReference = originalInstructions
            .filterIsInstance<ReferenceInstruction>()
            .map { it.reference }
            .filterIsInstance<MethodReference>()
            .firstOrNull { reference ->
                reference.definingClass == RX_OBSERVABLE &&
                    reference.name != "<init>" &&
                    reference.parameterTypes.size == 1 &&
                    reference.parameterTypes[0] == "Ljava/lang/Object;"
            }
            ?: throw IllegalStateException(
                "the ad stream lambda no longer calls a static Observable factory",
            )

        // Rebuild the exact descriptor the app itself uses, return type included.
        val justCall = buildString {
            append(justReference.definingClass)
            append("->")
            append(justReference.name)
            append("(")
            justReference.parameterTypes.forEach(::append)
            append(")")
            append(justReference.returnType)
        }

        AdsStreamFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, $ADS_AD_EMPTY->${singleton.name}:$ADS_AD_EMPTY
                invoke-static {v0}, $justCall
                move-result-object v0
                return-object v0
            """,
        )
    }
}
