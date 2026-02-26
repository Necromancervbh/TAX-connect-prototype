package com.example.taxconnect.data.models

class RequestItem(
    val type: Int,
    val data: Any
) {
    var userModel: UserModel? = null
    var isReturning: Boolean = false

    companion object {
        const val TYPE_CONVERSATION = 0
        const val TYPE_BOOKING = 1
    }
}
