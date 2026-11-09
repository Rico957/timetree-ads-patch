package app.timetree.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

internal const val PACKAGE_NAME = "works.jubilee.timetree"
internal const val TARGET_VERSION = "15.34.1"

private const val DOMAIN_ADS = "Lworks/jubilee/timetree/domain/ads/model/"

internal const val ADS_CONFIG_MODEL = "${DOMAIN_ADS}AdsConfigModel;"
internal const val ADS_AD_MODEL = "${DOMAIN_ADS}AdsAdModel;"
internal const val ADS_AD_EMPTY = "${DOMAIN_ADS}AdsAdModel\$Empty;"
internal const val ADS_REQUEST_PARAM = "${DOMAIN_ADS}timetree/AdsTimeTreeRequestParam;"

internal const val SPLASH_ADS_ACTIVITY = "Lworks/jubilee/timetree/app/ui/SplashAdsActivity;"

/**
 * TimeTree builds every ad stream inside a synthetic `Function1` lambda that closes over
 * the placement request (`AdsTimeTreeRequestParam`), the ads API client and the downloaded
 * `AdsConfigModel`. The captured request param is the stable anchor: it is a non-obfuscated
 * domain type that appears nowhere else as an instance field of a lambda class.
 *
 * Matching on that field instead of the obfuscated class name (`Lbo;`) keeps the patch
 * working when R8 renames the synthetic class or its methods in the next release.
 */
internal object AdsStreamFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = ADS_REQUEST_PARAM,
        ),
    ),
)

/**
 * `AdsConfigModel` carries the server-driven ad slot switches (enableDaily, enableMonthly,
 * enableCreation, enableActivities, enableEventDetail, ...). The constructor signature is
 * stable because it mirrors the JSON payload, so the parameter list is a strong fingerprint.
 */
internal object AdsConfigConstructorFingerprint : Fingerprint(
    definingClass = ADS_CONFIG_MODEL,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Z",
        "Ljava/lang/String;",
        "Z", "Z", "Z", "Z", "Z", "Z",
        "Ljava/util/List;",
        "Z", "Z", "Z",
        "L", "L", "L", "L", "L",
        "${DOMAIN_ADS}AdsConfigModel\$AdResponse;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
    ),
)

/**
 * The gate method that decides whether the splash ad screen is shown at all. It is the
 * only no-argument `void` method of `SplashAdsActivity` that both reads and writes a
 * boolean field of the activity (the "already handled" flag) and touches a lazy holder.
 */
internal object SplashAdsGateFingerprint : Fingerprint(
    definingClass = SPLASH_ADS_ACTIVITY,
    isStatic = false,
    returnType = "V",
    parameters = listOf(),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions
        instructions != null &&
            instructions.any { it.opcode == Opcode.IGET_BOOLEAN } &&
            instructions.any { it.opcode == Opcode.IPUT_BOOLEAN } &&
            instructions.any { it.opcode == Opcode.IGET_OBJECT }
    },
)
