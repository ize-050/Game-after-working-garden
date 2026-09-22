import Shared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    let accountPlatform: NativeAccountPlatform

    func makeUIViewController(context: Context) -> UIViewController {
        Shared.MainViewControllerKt.MainViewController(accountPlatform: accountPlatform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
