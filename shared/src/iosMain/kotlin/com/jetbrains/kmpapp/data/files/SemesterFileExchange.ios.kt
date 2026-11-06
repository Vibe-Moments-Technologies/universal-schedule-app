package com.jetbrains.kmpapp.data.files

/**
 * iOS-мост: логика в Swift (FileExchangeEngine.swift), здесь только
 * диспетчеризация через зарегистрированный Swift-движок.
 * Движок вешается в iOSApp.init и живёт со сценой.
 */
actual object SemesterFileExchange {

    actual fun shareFile(fileName: String, content: String) {
        val engine = FileExchangeEngineHolder.engine
        if (engine != null) {
            engine.shareFile(fileName, content)
        }
    }

    actual fun pickJsonFile(onResult: (String?) -> Unit) {
        val engine = FileExchangeEngineHolder.engine
        if (engine != null) {
            engine.pickJsonFile(onResult)
        } else {
            onResult(null)
        }
    }
}

/** Swift-движок регистрируется из iOSApp.init. */
object FileExchangeEngineHolder {
    var engine: FileExchangeEngineProtocol? = null
}

/** Контракт Swift-движка (реализация — FileExchangeEngine.swift). */
interface FileExchangeEngineProtocol {
    fun shareFile(fileName: String, content: String)
    fun pickJsonFile(onResult: (String?) -> Unit)
}
