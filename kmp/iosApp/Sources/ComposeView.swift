import Shared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    let accountPlatform: NativeAccountPlatform
    let notificationPlatform: NativeHarvestNotifications

    func makeUIViewController(context: Context) -> UIViewController {
        Shared.MainViewControllerKt.MainViewController(accountPlatform: accountPlatform, notificationPlatform: notificationPlatform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
