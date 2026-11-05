// swift-tools-version: 5.9
import PackageDescription

// Пакет существует только ради unit-тестов логики виджета: XCTest иначе
// не запустить. Чистые исходники (модели + движок выбора пары) лежат здесь
// и подключены к таргету расширения по абсолютному пути в pbxproj — один
// и тот же код, без дублирования и без второго источника правды.
let package = Package(
    name: "WidgetKitLogic",
    platforms: [.macOS(.v13), .iOS(.v17)],
    products: [
        .library(name: "WidgetKitLogic", targets: ["WidgetKitLogic"])
    ],
    targets: [
        .target(name: "WidgetKitLogic"),
        .testTarget(
            name: "WidgetKitLogicTests",
            dependencies: ["WidgetKitLogic"]
        )
    ]
)