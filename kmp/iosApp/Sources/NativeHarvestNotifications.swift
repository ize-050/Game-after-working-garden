import Foundation
import Shared
import UserNotifications

/// On-device reminders only: no APNs registration, token, remote messages, or backend.
final class NativeHarvestNotifications: NSObject, HarvestNotificationPlatform {
    private let center = UNUserNotificationCenter.current()
    private let identifier = "littlefarm.harvest.aggregate.v1"
    private let fingerprintKey = "littlefarm.harvest.schedule.v1"
    private var generation: UInt64 = 0

    func request(operation: String, payload: String, completion: @escaping (String) -> Void) {
        DispatchQueue.main.async {
            let reply: ([String: Any]) -> Void = { value in
                let data = try? JSONSerialization.data(withJSONObject: value)
                let json = data.flatMap { String(data: $0, encoding: .utf8) } ?? "{\"ok\":false}"
                DispatchQueue.main.async { completion(json) }
            }
            switch operation {
            case "permission":
                self.readPermission(reply)
            case "requestPermission":
                // Only an explicit settings toggle reaches this operation.
                self.center.requestAuthorization(options: [.alert, .sound]) { _, error in
                    if error != nil { reply(["ok": false, "permission": "UNAVAILABLE"]) }
                    else { self.readPermission(reply) }
                }
            case "cancel":
                self.generation &+= 1
                self.center.removePendingNotificationRequests(withIdentifiers: [self.identifier])
                self.center.removeDeliveredNotifications(withIdentifiers: [self.identifier])
                UserDefaults.standard.removeObject(forKey: self.fingerprintKey)
                reply(["ok": true])
            case "schedule":
                self.schedule(payload, reply: reply)
            default:
                reply(["ok": false])
            }
        }
    }

    private func readPermission(_ reply: @escaping ([String: Any]) -> Void) {
        center.getNotificationSettings { settings in
            let value: String
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral: value = "AUTHORIZED"
            case .denied: value = "DENIED"
            case .notDetermined: value = "UNKNOWN"
            @unknown default: value = "UNAVAILABLE"
            }
            reply(["ok": true, "permission": value])
        }
    }

    private func schedule(_ payload: String, reply: @escaping ([String: Any]) -> Void) {
        guard payload.utf8.count < 1000,
              let data = payload.data(using: .utf8),
              let args = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let fireAt = (args["fireAtMillis"] as? NSNumber)?.doubleValue,
              let count = (args["cropCount"] as? NSNumber)?.intValue,
              fireAt.isFinite, count > 0, count <= 1000 else {
            reply(["ok": false]); return
        }
        generation &+= 1
        let version = generation
        center.getNotificationSettings { settings in
            DispatchQueue.main.async {
                guard version == self.generation else { reply(["ok": false]); return }
                guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus) else {
                    reply(["ok": false]); return
                }
                let seconds = fireAt / 1000 - Date().timeIntervalSince1970
                guard seconds > 0 else { reply(["ok": false]); return }
                let content = UNMutableNotificationContent()
                content.title = "ผักในสวนพร้อมเก็บแล้ว 🌱"
                content.body = "ผักที่รอไว้ \(count) แปลงพร้อมแล้ว แวะกลับมาเมื่อสะดวกนะ ไม่มีผักเหี่ยว"
                content.sound = .default
                content.threadIdentifier = self.identifier
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, seconds), repeats: false)
                let request = UNNotificationRequest(identifier: self.identifier, content: content, trigger: trigger)
                // Reusing one identifier replaces an earlier request, including after app relaunch.
                self.center.add(request) { error in
                    DispatchQueue.main.async {
                        guard version == self.generation else { reply(["ok": false]); return }
                        if error == nil {
                            // Avoid Double -> Int64 traps for syntactically valid extreme saved timestamps.
                            UserDefaults.standard.set("\(fireAt):\(count)", forKey: self.fingerprintKey)
                        }
                        reply(["ok": error == nil])
                    }
                }
            }
        }
    }
}
