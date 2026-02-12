package org.example.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TypesMetaDataResponse(
    val response: MetaDataTypes
)

@Serializable
data class MetaDataTypes(
val types: List<String>,
)