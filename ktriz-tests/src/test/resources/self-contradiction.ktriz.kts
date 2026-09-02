// self-contradiction.ktriz.kts
//
// Compiles cleanly; throws IllegalArgumentException at runtime from Contradiction's init
// block (improving == worsening). Demonstrates kTRIZ's second TDG enforcement level:
// well-formedness via `require`, since the type system alone cannot express "these two
// enum values must differ".
contradiction(
    improving = EngineeringParameter.STRENGTH,
    worsening = EngineeringParameter.STRENGTH,
)
