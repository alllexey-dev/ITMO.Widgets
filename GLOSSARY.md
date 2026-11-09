# ITMO.Widgets

The language of a student's university life as the app shows it: study, campus services and what the student owes
ITMO.

## Language

### Dormitory

**Этап общежития**:
Where a student is in getting and keeping a dormitory place: `В очереди`, `Место предоставлено`, `Дата заселения
выбрана`, `Заселён`.
_Avoid_: dormitory status, statusId

**Место предоставлено**:
The stage at which ITMO has offered a place and the student must pick a settlement date within a short window or lose
it.
_Avoid_: place assigned, stage 2

**Документы для заселения**:
The medical documents ITMO must confirm before a student can settle in.
_Avoid_: medical certificates, справки

### Payments

**Договор**:
An agreement under which the student pays ITMO: a **Договор найма** for a dormitory place or a **Договор об
обучении** for paid study.
_Avoid_: contract (in UI text), agreement

**Платёж**:
One line of a Договор's schedule: an amount and the date it must be paid by.
_Avoid_: installment, invoice

**Долг**:
A Договор with a negative balance or at least one overdue unpaid Платёж.
_Avoid_: задолженность, недоплата, arrears
