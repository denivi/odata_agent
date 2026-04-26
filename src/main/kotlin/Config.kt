package org.example


object Config {

    // Порт сервера
    const val HTTP_PORT = 8089

    // LLM (Ollama/Koog)
    val BASE_URL_LLM: String = System.getenv("BASE_URL_LLM")
        ?: "http://77.95.56.147:65526"

    //LLM (Open api)
    val BASE_OPEN_API_URL_LLM: String = System.getenv("BASE_OPEN_API_URL_LLM")
        ?: "http://77.95.56.147:65527/v1"

    // Модель LLM
    val MODEL_NAME: String = System.getenv("MODEL_NAME")
        ?: "qwen35-agent-32k"

    val OPEN_API_MODEL_NAME: String = System.getenv("OPEN_API_MODEL_NAME")
        ?: "qwen/qwen3.5-9b"

    val BASE_URL_TOOL_SET: String = System.getenv("BASE_URL_TOOL_SET")
    ?: "http://77.95.56.147:65525/DevelopDaily/hs/agent_smart_api_v1"
}