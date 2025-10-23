package org.example


object Config {

    // Порт сервера
    const val HTTP_PORT = 8089

    // LLM (Ollama/Koog)
    val BASE_URL_LLM: String = System.getenv("BASE_URL_LLM")
        ?: "http://192.168.0.100:11434"

    // Модель LLM
    val MODEL_NAME: String = System.getenv("MODEL_NAME")
        ?: "qwen3:8b"
}