# 0039 Display My ITMO person contacts

**Decision (2026-11-10).** Amend [0009](0009-person-profile.md): show the contact values and their type labels returned by My ITMO in the person card. The existing on-device MyItmoApi request supplies them; they are never sent to Backend and do not depend on the custom-services opt-in. My ITMO controls which contacts it returns. Search results still discard their contact fields.

Blank values are omitted and duplicate label/value/destination rows are collapsed. Recognized email, telephone and social handles open in the system; valid HTTPS links open externally. Unknown or unsafe values remain readable without an action. No contacts section appears for an empty answer. Gender and exchange status remain excluded.
