import SwiftUI

@main
struct LittleFarmApp: App {
    private let accountPlatform = NativeAccountPlatform()
    private let notificationPlatform = NativeHarvestNotifications()

    var body: some Scene {
        WindowGroup {
            ComposeView(accountPlatform: accountPlatform, notificationPlatform: notificationPlatform)
                .ignoresSafeArea()
                .onOpenURL { accountPlatform.handle(url: $0) }
        }
    }
}
