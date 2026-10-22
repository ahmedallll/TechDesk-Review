package com.example.data.model

enum class ActiveContext {
    POS,
    PRODUCTS,
    AUTO,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ActiveContext {
            return when (value?.trim()?.lowercase()) {
                "pos" -> POS
                "products" -> PRODUCTS
                "auto" -> AUTO
                else -> UNKNOWN
            }
        }
    }
}
