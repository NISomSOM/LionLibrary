package com.singam.lionlibrary.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.DataError

sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val arguments: List<Any> = emptyList()) :
        UiText

    data class Dynamic(val text: String) : UiText

    fun resolve(context: Context): String =
        when (this) {
            is Resource -> context.getString(id, *arguments.toTypedArray())
            is Dynamic -> text
        }
}

fun DataError.toUiText(): UiText =
    UiText.Resource(
        when (this) {
            DataError.UNAUTHORIZED -> R.string.error_api_key
            DataError.OFFLINE -> R.string.error_offline
            DataError.SERVER -> R.string.error_server
            DataError.NOT_FOUND -> R.string.error_not_found
            DataError.PERMISSION -> R.string.error_permission
            DataError.STORAGE -> R.string.error_storage
            DataError.UNKNOWN -> R.string.error_unknown
        }
    )
