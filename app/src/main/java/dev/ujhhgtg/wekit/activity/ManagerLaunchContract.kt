package dev.ujhhgtg.wekit.activity

import android.app.Activity
import android.content.Intent
import dev.ujhhgtg.wekit.constants.PackageNames

private const val REQUEST_OPEN_LSPOSED_MANAGER = 0x574B

object ManagerLaunchContract {
    const val ACTION_OPEN_LSPOSED_MANAGER =
        "${PackageNames.MODULE}.action.OPEN_LSPOSED_MANAGER"
    const val EXTRA_ERROR = "manager_launch_error"
}

@Suppress("DEPRECATION")
fun openLsposedManager(activity: Activity) {
    val intent = Intent().apply {
        setClassName(PackageNames.MODULE, "${PackageNames.MODULE}.activity.MainActivity")
        action = ManagerLaunchContract.ACTION_OPEN_LSPOSED_MANAGER
    }
    activity.startActivityForResult(intent, REQUEST_OPEN_LSPOSED_MANAGER)
}
