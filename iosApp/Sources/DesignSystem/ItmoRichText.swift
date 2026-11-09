import Shared
import SwiftUI

/// HTML from ITMO as the shared `SafeHtml` let it through (`RichText`): paragraphs and list items with their markers
/// hanging before the text, styles and `http(s)` links. Selectable; links open through the environment's `openURL`.
struct ItmoRichText: View {
    let text: RichText
    var spacing: CGFloat = ItmoSpacing.content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            ForEach(Array(text.blocks.enumerated()), id: \.offset) { _, block in
                HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.related) {
                    if let marker = block.marker {
                        Text(verbatim: marker).foregroundStyle(.secondary).frame(minWidth: 20, alignment: .leading)
                    }
                    Text(Self.attributed(block))
                }
                .font(.itmo(.bodyLarge))
            }
        }
        .textSelection(.enabled)
        .itmoTint()
    }

    /// The block's runs with their styles; a link is an `http(s)` address the shared parser let through.
    static func attributed(_ block: RichTextBlock) -> AttributedString {
        block.spans.reduce(into: AttributedString()) { result, span in
            var part = AttributedString(span.text)
            var intent: InlinePresentationIntent = []
            if span.bold { intent.insert(.stronglyEmphasized) }
            if span.italic { intent.insert(.emphasized) }
            part.inlinePresentationIntent = intent
            if span.underline { part.underlineStyle = .single }
            if let link = span.link, let url = URL(string: link) { part.link = url }
            result += part
        }
    }
}
