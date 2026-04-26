package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimilarMetaDataResponse(
    val response: List<MetaDataClass>
)

@Serializable
data class MetaDataClass(
    @SerialName("class_meta_data")val classMetaData: MetaDataItem
)

@Serializable
data class MetaDataItem(
    val id: String,
    val name: String = "",
    val title: String = "",
    val type: String = "",
)