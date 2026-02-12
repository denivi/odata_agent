package org.example.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefClassMetaDataResponse(
    val response: RefClassMetaDataDescription
)

@Serializable
data class NotRefClassMetaDataResponse(
    val response: NotRefClassMetaDataDescription
)

@Serializable
data class RefClassMetaDataDescription(
    val name: String,
    val title: String,
    @SerialName("is_ref") val isRef: Boolean,
    val properties: List<PropertyClass>,
    val tables: List<Table>,
)

@Serializable
data class NotRefClassMetaDataDescription(
    val name: String,
    val title: String,
    @SerialName("is_ref") val isRef: Boolean,
    val dimensions: List<PropertyClass>,
    val attributes: List<PropertyClass>,
    val resources: List<PropertyClass>,
)

@Serializable
data class PropertyClass(
    val property: PropertyClassDescription,
)

@Serializable
data class Table(
    val table: TableProperties,
)

@Serializable
data class TableProperties(
    val name: String,
    val title: String,
    val properties: List<PropertyClass>,
)

@Serializable
data class PropertyClassDescription(
    val name: String,
    val title: String,
    @SerialName("types_description") val typesDescription: TypesDescription,
)

@Serializable
data class TypesDescription(
    val types: List<TypeDescription>,
    val enums: List<String>,
)

@Serializable
data class TypeDescription(
    val type: String,
)
