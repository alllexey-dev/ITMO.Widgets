import SnapshotTesting
import SwiftUI
import UIKit
import XCTest
@testable import ITMOWidgets

@MainActor
final class AvatarVoteSnapshotTests: XCTestCase {
    func testAvatarPhases() {
        // A deliberately wide synthetic photo checks aspect-fill and circular clipping without networking.
        let photo = UIGraphicsImageRenderer(size: CGSize(width: 120, height: 60)).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 120, height: 60))
            UIColor.systemOrange.setFill()
            context.fill(CGRect(x: 45, y: 0, width: 30, height: 60))
        }
        let avatars = HStack(spacing: ItmoSpacing.content) {
            ItmoAvatar(initials: "ИК")
            ItmoAvatarContent(initials: "ИК", phase: .empty)
            ItmoAvatarContent(initials: "ИК", phase: .failure(URLError(.notConnectedToInternet)))
            ItmoAvatarContent(initials: "ИК", phase: .success(Image(uiImage: photo)))
            ItmoAvatarContent(initials: "ИК", phase: .success(Image(uiImage: photo)), size: 22)
        }
        .padding(ItmoSpacing.screenMargin)
        assertAppearances(of: avatars, named: "avatars", appearances: [.light, .dark])
    }

    func testVotes() {
        let votes = VStack(spacing: ItmoSpacing.content) {
            pill(vote: 0)
            pill(vote: 1)
            pill(vote: -1)
            pill(vote: 0, enabled: false)
        }
        .padding(ItmoSpacing.screenMargin)
        assertAppearances(of: votes, named: "votes", appearances: [.light, .dark])
    }

    private func pill(vote: Int, enabled: Bool = true) -> some View {
        ItmoVotePill(
            score: 12, myVote: vote, upLabel: "Полезная ссылка", downLabel: "Бесполезная ссылка",
            enabled: enabled, onUp: {}, onDown: {}
        )
    }
}
