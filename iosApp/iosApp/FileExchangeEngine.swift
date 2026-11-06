import Foundation
import UIKit
import UniformTypeIdentifiers
import Shared

/// Мост файлового обмена семестром: share-лист для экспорта,
/// системный выбор документа для импорта. Регистрируется в iOSApp.init
/// (FileExchangeEngineHolder из shared-модуля).
final class FileExchangeEngine: NSObject, FileExchangeEngineProtocol {

    func shareFile(fileName: String, content: String) {
        let url = URL(fileURLWithPath: NSTemporaryDirectory() + fileName)
        do {
            try content.data(using: .utf8)?.write(to: url)
        } catch {
            return
        }
        let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        Self.rootViewController?.present(activity, animated: true)
    }

    func pickJsonFile(onResult: @escaping (String?) -> Void) {
        let types: [UTType] = [
            UTType.json,
            UTType.plainText,
            UTType.data
        ].compactMap { $0 }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = self
        Self.pickCallback = onResult
        Self.rootViewController?.present(picker, animated: true)
    }

    // MARK: - Хранилище колбэка (делегат — отдельный объект,

    /// колбэк живёт здесь: делегат слабый по семантике UIKit)
    static var pickCallback: ((String?) -> Void)?

    private static var rootViewController: UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
        return scene?.keyWindow?.rootViewController
    }
}

extension FileExchangeEngine: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let callback = FileExchangeEngine.pickCallback
        FileExchangeEngine.pickCallback = nil
        guard let url = urls.first, let callback = callback else {
            callback?(nil)
            return
        }
        do {
            let text = try String(contentsOf: url, encoding: .utf8)
            callback(text)
        } catch {
            callback(nil)
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let callback = FileExchangeEngine.pickCallback
        FileExchangeEngine.pickCallback = nil
        callback?(nil)
    }
}
