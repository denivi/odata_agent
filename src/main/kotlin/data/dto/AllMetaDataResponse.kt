package org.example.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MetaDataItem(
    val id: String,
    val name: String = "",
    val title: String = ""
)

@Serializable
data class ListMetaDataClasses(
    val type: String,
    val classes: List<MetaDataItem>
)

@Serializable
data class AllMetaDataResponse(
    val response: ListMetaDataClasses
){
}
