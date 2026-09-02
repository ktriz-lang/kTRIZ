// bogus-parameter.ktriz.kts
//
// Deliberately invalid: EngineeringParameter.STRUCTURAL_INTEGRITY does not exist (kTRIZ has
// no such parameter among its 39 classical engineering parameters). Proves the Kotlin
// compiler rejects a hallucinated parameter before anything runs -- KtrizScriptHelloWorldTest
// pins the exact line/column of the resulting diagnostic below; if you edit this file, re-run
// that test and update the pinned line number, don't guess.
val bogus = contradiction(
    improving = EngineeringParameter.WEIGHT_OF_MOVING_OBJECT,
    worsening = EngineeringParameter.STRUCTURAL_INTEGRITY,
)
