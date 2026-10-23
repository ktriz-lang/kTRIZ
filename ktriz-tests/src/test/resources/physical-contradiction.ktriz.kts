// physical-contradiction.ktriz.kts
//
// Builds a physical contradiction with no explicit imports -- exercises the
// "dev.ktriz.physical.*" default import (physicalContradiction { }, SeparationPrinciple).
val pc =
    physicalContradiction {
        element("Landing gear")
        property("Presence")
        state("present", because = "needed for landing")
        oppositeState("absent", because = "causes drag in flight")
        separation(SeparationPrinciple.IN_TIME)
    }

println("Element: ${pc.element.name}")
println("Property: ${pc.property}")
println("State: ${pc.state}")
println("Opposite state: ${pc.oppositeState}")
println("Separation: ${pc.separation!!.labelEn} / ${pc.separation!!.labelDe}")
