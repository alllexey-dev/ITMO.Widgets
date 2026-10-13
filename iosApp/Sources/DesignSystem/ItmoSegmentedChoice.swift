import SwiftUI

/// The one pattern for two to four options and for in-screen tabs (a subject's scores, lessons and materials): the native
/// segmented picker. Filters are chips, longer choices a list or a sheet. `enabled: false` shows a choice that cannot
/// change now. The labels are the options' titles; `label` names the whole choice for VoiceOver.
struct ItmoSegmentedChoice<Option: Hashable>: View {
    let label: String
    let options: [Option]
    @Binding var selection: Option
    var enabled = true
    let title: (Option) -> String

    var body: some View {
        Picker(selection: $selection) {
            ForEach(options, id: \.self) { option in
                Text(verbatim: title(option)).tag(option)
            }
        } label: {
            Text(verbatim: label)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .disabled(!enabled)
        .itmoTint()
    }
}
