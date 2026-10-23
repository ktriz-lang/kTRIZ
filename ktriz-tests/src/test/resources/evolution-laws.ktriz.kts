// evolution-laws.ktriz.kts
//
// Lists the eight classical laws of technical systems evolution with no explicit imports --
// exercises the "dev.ktriz.evolution.*" default import (EvolutionLaw, EvolutionLawGroup).
EvolutionLaw.entries.forEach { println("${it.id} ${it.group} ${it.labelEn}") }
