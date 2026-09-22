import SwiftUI

@main
struct LittleFarmApp: App {
    private let accountPlatform = NativeAccountPlatform()

    var body: some Scene {
        WindowGroup {
            ComposeView(accountPlatform: accountPlatform)
                .ignoresSafeArea()
                .onOpenURL { accountPlatform.handle(url: $0) }
        }
    }
}
