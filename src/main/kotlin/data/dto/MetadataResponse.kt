package org.example.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MetadataResponse(val name: String, val url: String)
