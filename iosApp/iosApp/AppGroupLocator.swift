import Foundation

/// Определение App Group в рантайме: чем бы приложение ни переподписали
/// (GBox, esign, Sideloadly, дистрибутив Xcode), профиль лежит в бандле в
/// embedded.mobileprovision — читаем его и берём группу оттуда. Так работает
/// большинство приложений, не жёстко зафиксированных на одном профиле.
enum AppGroupLocator {
    /// Группа по умолчанию — должна совпадать с группой, разрешённой
    /// профилем переподписи (GBox): система даёт контейнер только для
    /// групп из профиля. Взята из сиделоад-профиля (group.c2fefdd0db2e255e.1);
    /// для подписи командой проекта приоритетнее группы из самой подписи
    /// и embedded.mobileprovision (см. availableSuiteName).
    static let fallback = "group.c2fefdd0db2e255e.1"

    /// Группа из профиля, которым подписали этот бинарник. nil — профиля нет.
    static func fromProfile() -> String? {
        // Бандл приложения: в самом приложении — Bundle.main; в виджете
        // Bundle.main — это *.appex внутри PlugIns, приложение на два уровня выше.
        let appBundleURL: URL
        if Bundle.main.bundlePath.hasSuffix(".appex") {
            appBundleURL = Bundle.main.bundleURL
                .deletingLastPathComponent()  // PlugIns
                .deletingLastPathComponent()  // *.app
        } else {
            appBundleURL = Bundle.main.bundleURL
        }
        guard let profileURL = Bundle(url: appBundleURL)?
            .url(forResource: "embedded", withExtension: "mobileprovision") else { return nil }
        guard let data = try? Data(contentsOf: profileURL) else { return nil }

        // Профиль — CMS-конверт с бинарным мусором вокруг; XML-плIST внутри.
        // ISO Latin-1 декодирует любые байты без отказа.
        guard let text = String(data: data, encoding: .isoLatin1),
              let start = text.range(of: "<?xml"),
              let end = text.range(of: "</plist>", range: start.lowerBound..<text.endIndex) else { return nil }
        let xml = String(text[start.lowerBound..<end.upperBound])

        guard let plist = try? PropertyListSerialization.propertyList(
                  from: Data(xml.utf8), options: [], format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String],
              let group = groups.first(where: { !$0.isEmpty }) else { return nil }
        return group
    }

    /// Первый контейнер, который реально доступен этому процессу.
    /// Порядок источников: подпись бинарника (истина), профиль в бандле,
    /// fallback для дистрибутивной подписи.
    static func availableSuiteName() -> String? {
        let candidates = (signatureGroups().first.map { [$0] } ?? [])
            + [fromProfile(), fallback].compactMap({ $0 })
        for group in candidates {
            if FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: group) != nil {
                return group
            }
        }
        return nil
    }

    /// Все группы из профиля (для диагностики в меню отладки).
    static func profileGroups() -> [String] {
        let appBundleURL = Bundle.main.bundleURL
        guard let profileURL = Bundle(url: appBundleURL)?
            .url(forResource: "embedded", withExtension: "mobileprovision"),
            let data = try? Data(contentsOf: profileURL),
            let text = String(data: data, encoding: .isoLatin1),
            let start = text.range(of: "<?xml"),
            let end = text.range(of: "</plist>", range: start.lowerBound..<text.endIndex),
            let plist = try? PropertyListSerialization.propertyList(
                from: Data(String(text[start.lowerBound..<end.upperBound]).utf8),
                options: [], format: nil) as? [String: Any],
            let entitlements = plist["Entitlements"] as? [String: Any],
            let groups = entitlements["com.apple.security.application-groups"] as? [String] else {
            return []
        }
        return groups
    }

    /// Группы из СОБСТВЕННОЙ подписи бинарника — то, что реально вшил
    /// инструмент переподписи. Истина в последней инстанции: контейнер
    /// существует только если группа есть здесь.
    ///
    /// Xcode 16.4 не экспортирует Code Signing C API в Swift-модуль Security,
    /// поэтому вызываем через dlopen/dlsym — работает независимо от module map.
    static func signatureGroups() -> [String] {
        // RTLD_DEFAULT = ((void*)-1) — макрос, в Swift не экспортируется.
        let rtldDefault = UnsafeMutableRawPointer(bitPattern: -1)
        guard let createSym = dlsym(rtldDefault, "SecStaticCodeCreateWithPath") else { return [] }

        // SecStaticCodeCreateWithPath(CFURL, SecCSFlags, SecStaticCode*) -> OSStatus
        typealias CreateFn = @convention(c) (CFURL, UInt32, UnsafeMutablePointer<CFTypeRef?>) -> Int32
        let createFn = unsafeBitCast(createSym, to: CreateFn.self)

        var staticCode: CFTypeRef?
        // kSecCSDefaultFlags = 0
        guard createFn(Bundle.main.bundleURL as CFURL, 0, &staticCode) == 0,
              let code = staticCode else { return [] }

        // SecCodeCopySigningInformation(SecCode, SecCSFlags, CFDictionary*) -> OSStatus
        typealias CopyInfoFn = @convention(c) (CFTypeRef, UInt32, UnsafeMutablePointer<CFDictionary?>) -> Int32
        guard let copyInfoSym = dlsym(rtldDefault, "SecCodeCopySigningInformation") else { return [] }
        let copyInfoFn = unsafeBitCast(copyInfoSym, to: CopyInfoFn.self)

        var info: CFDictionary?
        // kSecCSSigningInformation = 2
        guard copyInfoFn(code, 2, &info) == 0,
              let dict = info as? [String: Any] else { return [] }

        // kSecCodeInfoEntitlementsDict = "entitlements"
        guard let entitlements = dict["entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String] else { return [] }
        return groups
    }

    /// Диагностика для меню отладки: что этот процесс видит.
    static func debugInfo() -> String {
        var lines: [String] = []
        lines.append("bundleId: \(Bundle.main.bundleIdentifier ?? "?")")
        let sigGroups = signatureGroups()
        lines.append("групп в подписи: \(sigGroups.count)")
        for g in sigGroups {
            let alive = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: g) != nil
            lines.append("  \(g) — контейнер: \(alive ? "есть" : "нет")")
        }
        if sigGroups.isEmpty {
            lines.append("  (в подписи нет App Group — инструмент переподписи их не вшил)")
        }
        let profileGroups = profileGroups()
        lines.append("групп в профиле: \(profileGroups.count)")
        if profileGroups.isEmpty {
            lines.append("  (embedded.mobileprovision не найден или без групп)")
        }
        // Корень бандла: что реально лежит рядом с бинарником.
        let root = (try? FileManager.default.contentsOfDirectory(
            atPath: Bundle.main.bundlePath)) ?? []
        let interesting = root.filter {
            $0.hasSuffix(".mobileprovision") || $0 == "_CodeSignature" || $0 == "PlugIns"
        }
        lines.append("в бандле: \(interesting.isEmpty ? "пусто (нет профиля/подписи/расширений)" : interesting.sorted().joined(separator: ", "))")
        if let suite = availableSuiteName() {
            lines.append("выбранная группа: \(suite)")
        } else {
            lines.append("выбранная группа: нет доступного контейнера")
        }
        return lines.joined(separator: "\n")
    }
}
