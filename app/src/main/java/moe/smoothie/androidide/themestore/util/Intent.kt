package moe.smoothie.androidide.themestore.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import java.io.Serializable

fun <T: Serializable> Intent.getSerializableExtraApiDependent(
    name: String,
    clazz: Class<T>
): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getSerializableExtra(name, clazz)
    } else {
        @Suppress("DEPRECATION")
        this.getSerializableExtra(name) as T?
    }
}

inline fun <reified T : Parcelable> Intent.getParcelableExtraApiDependent(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
}
