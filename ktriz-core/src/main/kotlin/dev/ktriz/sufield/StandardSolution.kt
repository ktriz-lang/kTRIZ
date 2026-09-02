package dev.ktriz.sufield

/**
 * The 76 individual classical TRIZ standard solutions for Su-Field analysis (Altshuller).
 *
 * [code] is the classical dot-numbered code (e.g. `"1.1.1"`, `"5.5.3"`) used throughout the
 * literature; [standardClass] is the owning [StandardSolutionClass] (1..5); [labelEn] is a
 * short English "what to do" statement for the solution, transcribed from the source below.
 * There is deliberately no `labelDe` -- English-only in V1, because no verified
 * German-language transcription of the 76-item list exists (unlike the independently-authored
 * German labels backing [dev.ktriz.core.EngineeringParameter], [dev.ktriz.core.InventivePrinciple],
 * [FieldType] and [StandardSolutionClass]). There is also deliberately no separate
 * `description` field distinct from [labelEn]: unlike [StandardSolutionClass] (a terse
 * [StandardSolutionClass.labelEn] plus a separately-composed [StandardSolutionClass.description]),
 * the source's own short statement for each of the 76 items already *is* the short gloss, so a
 * second field would only duplicate it or force an arbitrary split.
 *
 * `enum` and not a `sealed` hierarchy, for the same reason as [StandardSolutionClass]/
 * [FieldType]: a closed set, fully known at compile time, with no instance-specific structure
 * beyond code + owning class + label.
 *
 * ## Source and its limits
 *
 * Transcribed from a 5-part TRIZ Journal article series, "The Seventy-Six Standard Solutions,
 * with Examples" (Terninko, Domb & Miller, February-July 2000; Classes 2-5 retrieved via
 * Internet Archive Wayback Machine captures of the original `triz-journal.com`, since only
 * Class 1 survives on the site's current domain). Full sourcing record, encoding notes and
 * caveats: `docs/standard-solutions-provenance.adoc`. Two caveats worth surfacing here rather
 * than only in the provenance doc:
 *
 * - This is a **single authorial voice**, not a majority-reconciled compilation like
 *   [dev.ktriz.core.ContradictionMatrix]'s four-source reconciliation -- the authors themselves
 *   call this their own "interpretation" of Altshuller-era primary sources, not a verbatim
 *   Altshuller translation.
 * - **`5.1.1`'s numbering anomaly.** The source nests nine further sub-items (`5.1.1.1`-
 *   `5.1.1.9`) under `5.1.1` -- the only fourth-level numbering in the entire 76-item series.
 *   [INDIRECT_SUBSTANCE_INTRODUCTION] (`5.1.1`) folds those nine into one [labelEn] as the
 *   source's own enumeration of techniques *within* that one solution, keeping the class at 17
 *   and the grand total at 76 (matching every one of the source's own preface tables, and the
 *   independently-sourced 13/23/6/17/17 split already backing [StandardSolutionClass]). Its
 *   [labelEn] is therefore the one item in this enum that is a semicolon-joined compression of
 *   the source's own nine opening clauses, not a verbatim single sentence like the other 75 --
 *   see `docs/standard-solutions-provenance.adoc`'s "Known anomaly" section for the full
 *   reasoning.
 *
 * ## No `SuField`/`SuFieldQuality`-to-`StandardSolution` bridge -- deliberately not modeled
 *
 * Echoing and extending [StandardSolutionClass]'s own KDoc: kTRIZ provides no
 * `suggestStandardSolution(suField: SuField): StandardSolution` (or `List<StandardSolution>`)
 * function, and no narrower [StandardSolutionClass]-to-`StandardSolution`-subset convenience
 * beyond plain `StandardSolution.entries.filter { it.standardClass == someClass }`. At 5-class
 * granularity the correspondence to [SuFieldQuality] is already "loose" and undocumented as a
 * 1:1 mapping ([StandardSolutionClass]'s own KDoc); at this 76-item granularity the source
 * itself resolves which specific solution applies to a situation through ARIZ-style
 * worked-example pattern matching and human judgment, not a formula over `SuField`/
 * `SuFieldQuality` state. A type-system-enforced bridge here would present uncertain domain
 * knowledge as false precision -- exactly the failure mode Typed Domain Grounding exists to
 * prevent. A caller who wants to reason about which solution applies does so explicitly, in
 * their own code, aware that it is a heuristic, not a rule.
 */
enum class StandardSolution(
    val code: String,
    val standardClass: StandardSolutionClass,
    val labelEn: String,
) {
    COMPLETE_INCOMPLETE_MODEL(
        code = "1.1.1",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "Complete an incomplete model. If there is only an object S1, add a second object S2" +
                " and an interaction (field) F.",
    ),
    INTERNAL_ADDITIVE(
        code = "1.1.2",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "The system cannot be changed but a permanent or temporary additive is acceptable." +
                " Incorporate an internal additive in either S1 or S2.",
    ),
    EXTERNAL_ADDITIVE(
        code = "1.1.3",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "As in 1.1.2, but use a permanent or temporary external additive S3 to change either" +
                " S1 or S2.",
    ),
    ENVIRONMENTAL_RESOURCE_ADDITIVE(
        code = "1.1.4",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "As in 1.1.2, but use a resource from the environment as the additive, either" +
                " internally or externally.",
    ),
    MODIFY_ENVIRONMENT(
        code = "1.1.5",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn = "As in 1.1.2, but modify or change the environment of the system.",
    ),
    CONTROL_BY_SURPLUS_AND_REMOVAL(
        code = "1.1.6",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "Precise control of small amounts is difficult to achieve. Control small quantities" +
                " by applying and removing a surplus.",
    ),
    LINK_TO_ANOTHER_ELEMENT_FOR_FIELD_MAGNITUDE(
        code = "1.1.7",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "If a moderate field can be applied which is insufficient for the desired effect, and" +
                " a greater field will damage the system, the larger magnitude field can be applied to" +
                " another element which can be linked to the original. Likewise, a substance that" +
                " cannot take the full action directly but can achieve the desired effect through" +
                " linkage to another substance can be used.",
    ),
    SELECTIVE_PROTECTION_AND_ENHANCEMENT(
        code = "1.1.8",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "A pattern of large/strong and small/weak effects is required. The locations" +
                " requiring the smaller effects can be protected by a substance S3. The locations" +
                " requiring the large effects can be enhanced by a substance S3.",
    ),
    REMOVE_HARMFUL_EFFECT_VIA_THIRD_SUBSTANCE(
        code = "1.2.1",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "Useful and harmful effects exist in the current design. It is not necessary for S1" +
                " and S2 to be in direct contact. Remove the harmful effect by introducing S3.",
    ),
    REMOVE_HARMFUL_EFFECT_BY_MODIFYING_EXISTING_SUBSTANCES(
        code = "1.2.2",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "Similar to 1.2.1, but new substances cannot be added. Remove the harmful effect by" +
                " modifying S1 or S2. This solution includes adding \"nothing\" — voids, hollows," +
                " vacuum, air, bubbles, foam, etc., or adding a field that acts like an additional" +
                " substance.",
    ),
    ABSORB_HARMFUL_FIELD(
        code = "1.2.3",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "The harmful action is caused by a field. Introduce an element S3 to absorb the" +
                " harmful effects.",
    ),
    NEUTRALIZE_HARMFUL_EFFECT_WITH_SECOND_FIELD(
        code = "1.2.4",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "Useful and harmful effects exist in a system in which the elements S1 and S2 must be" +
                " in contact. Counteract the harmful effect of F1 by having F2 neutralize the harmful" +
                " effect or gain an additional useful effect.",
    ),
    REMOVE_HARMFUL_MAGNETIC_EFFECT(
        code = "1.2.5",
        standardClass = StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION,
        labelEn =
            "A harmful effect may exist because of magnetic properties of an element in a system." +
                " The effect can be removed by heating the magnetic substance above its Curie point," +
                " or by introducing an opposite magnetic field.",
    ),
    CHAIN_SU_FIELD_MODEL(
        code = "2.1.1",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Chain Su-Field Model: Convert the single model to a chained model by having S2 with" +
                " F1 applied to S3 which in turn applies F2 to S1. The sequence of two models can be" +
                " independently controlled.",
    ),
    DOUBLE_SU_FIELD_MODEL(
        code = "2.1.2",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Double Su-Field Model: A poorly controlled system needs to be improved but you may" +
                " not change the elements of the existing system. A second field can be applied to S2.",
    ),
    USE_MORE_CONTROLLABLE_FIELD(
        code = "2.2.1",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Replace or add to the poorly controlled field with a more easily controlled field." +
                " Going from a gravitational field to a mechanical field provides more control, as" +
                " does going from mechanical means to electrical or mechanical to magnetic. This is" +
                " one of the patterns of evolution of systems progressing from objects in physical" +
                " contact to actions done by fields.",
    ),
    TRANSITION_TO_MICRO_LEVEL_SUBSTANCE(
        code = "2.2.2",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Change S2 from a macro level to a micro level, i.e., instead of a rock consider" +
                " particles. This standard is actually the pattern of evolution from a macro- to" +
                " micro-level.",
    ),
    USE_POROUS_OR_CAPILLARY_SUBSTANCE(
        code = "2.2.3",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Change S2 to a porous or capillary material that will allow gas or liquid to pass" +
                " through.",
    ),
    INCREASE_SYSTEM_FLEXIBILITY(
        code = "2.2.4",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Make the system more flexible or adaptable; becoming more dynamic is another pattern" +
                " of evolution. The common transition is from a solid to a hinged system to continuous" +
                " flexible systems.",
    ),
    STRUCTURE_THE_FIELD(
        code = "2.2.5",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Change an uncontrolled field to a field with predetermined patterns that may be" +
                " permanent or temporary.",
    ),
    STRUCTURE_THE_SUBSTANCE(
        code = "2.2.6",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Change a uniform substance or uncontrolled substance to a non-uniform substance with" +
                " a predetermined spatial structure that may be permanent or temporary.",
    ),
    MATCH_OR_MISMATCH_FREQUENCY(
        code = "2.3.1",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Matching or mismatching the frequency of F and S1 or S2.",
    ),
    MATCH_RHYTHMS_OF_TWO_FIELDS(
        code = "2.3.2",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Matching the rhythms of F1 and F2.",
    ),
    RUN_INCOMPATIBLE_ACTIONS_IN_TURNS(
        code = "2.3.3",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Two incompatible or independent actions can be accomplished by running each during" +
                " the down time of the other.",
    ),
    ADD_FERROMAGNETIC_MATERIAL_AND_FIELD(
        code = "2.4.1",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Add ferromagnetic material and/or a magnetic field to the system.",
    ),
    COMBINE_CONTROLLABLE_FIELD_WITH_FERROMAGNETIC_FIELD(
        code = "2.4.2",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Combine 2.2.1 (going to more controlled fields) and 2.4.1 (using ferromagnetic" +
                " materials and magnetic fields).",
    ),
    USE_MAGNETIC_LIQUID(
        code = "2.4.3",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Use a magnetic liquid. Magnetic liquids are a special case of 2.4.2. Magnetic" +
                " liquids are colloidal ferromagnetic particles suspended in kerosene, silicone or" +
                " water.",
    ),
    USE_CAPILLARY_STRUCTURE_WITH_MAGNETIC_PARTICLES(
        code = "2.4.4",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Use capillary structures that contain magnetic particles or liquid.",
    ),
    ADD_MAGNETIC_ADDITIVE(
        code = "2.4.5",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Use additives (such as a coating) to give a non-magnetic object magnetic properties." +
                " May be temporary or permanent.",
    ),
    INTRODUCE_FERROMAGNETIC_MATERIAL_INTO_ENVIRONMENT(
        code = "2.4.6",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Introduce ferromagnetic materials into the environment, if it is not possible to" +
                " make the object magnetic.",
    ),
    USE_NATURAL_MAGNETIC_PHENOMENA(
        code = "2.4.7",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Use natural phenomena (such as alignment of objects with the field, or loss of" +
                " ferromagnetism above the Curie point).",
    ),
    USE_DYNAMIC_MAGNETIC_FIELD(
        code = "2.4.8",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Use a dynamic, variable, or self-adjusting magnetic field.",
    ),
    STRUCTURE_MATERIAL_WITH_FERROMAGNETIC_PARTICLES(
        code = "2.4.9",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Modify the structure of a material by introducing ferromagnetic particles, then" +
                " apply a magnetic field to move the particles. More generally, the transition from an" +
                " unstructured system to a structured one, or vice versa, depending on the situation.",
    ),
    MATCH_RHYTHMS_IN_FE_FIELD_MODELS(
        code = "2.4.10",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Matching the rhythms in the Fe-field models. In macro-systems, this is the use of" +
                " mechanical vibration to enhance the motion of ferromagnetic particles. At the" +
                " molecular and atomic levels, material composition can be identified by the spectrum" +
                " of the resonance frequency of electrons in response to changing frequencies of a" +
                " magnetic field.",
    ),
    USE_ELECTRIC_CURRENT_FOR_MAGNETIC_FIELD(
        code = "2.4.11",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn = "Use electric current to create magnetic fields, instead of using magnetic particles.",
    ),
    RHEOLOGICAL_LIQUID_VISCOSITY_CONTROL(
        code = "2.4.12",
        standardClass = StandardSolutionClass.DEVELOPMENT,
        labelEn =
            "Rheological liquids have viscosity controlled by an electric field. They can be used" +
                " in combination with any of the methods here. They can mimic liquid/solid phase" +
                " transitions.",
    ),
    CREATE_BI_OR_POLY_SYSTEM(
        code = "3.1.1",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "System Transition 1a: Creating the Bi- and Poly-Systems.",
    ),
    IMPROVE_LINKS_IN_BI_OR_POLY_SYSTEM(
        code = "3.1.2",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "Improving Links in the Bi- and Poly-Systems.",
    ),
    INCREASE_DIFFERENCES_BETWEEN_ELEMENTS(
        code = "3.1.3",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "System Transition 1b: Increasing the Differences Between Elements.",
    ),
    SIMPLIFY_BI_OR_POLY_SYSTEM(
        code = "3.1.4",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "Simplification of the Bi- and Poly-Systems.",
    ),
    OPPOSITE_FEATURES_OF_WHOLE_AND_PARTS(
        code = "3.1.5",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "System Transition 1c: Opposite Features of the Whole and Parts.",
    ),
    TRANSITION_TO_MICRO_LEVEL(
        code = "3.2.1",
        standardClass = StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL,
        labelEn = "System Transition 2: Transition to the Micro-Level.",
    ),
    ELIMINATE_NEED_FOR_MEASUREMENT(
        code = "4.1.1",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Modify the system instead of detecting or measuring so there is no longer a need for" +
                " measurement.",
    ),
    MEASURE_A_COPY_OR_IMAGE(
        code = "4.1.2",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn = "Measure a copy or an image, if 4.1.1 can't be used.",
    ),
    USE_TWO_DETECTIONS_INSTEAD_OF_CONTINUOUS_MEASUREMENT(
        code = "4.1.3",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Use 2 detections instead of continuous measurement, if 4.1.1 or 4.1.2 cannot be" +
                " used. For example, make a ring having the outer tolerance limits of a machined" +
                " part, and a solid having its diameter equal to the inner tolerance limit. The" +
                " part is the right diameter when it fits through the ring (one detection) and" +
                " the solid fits through it (second detection.)",
    ),
    CREATE_SU_FIELD_SYSTEM_WITH_FIELD_OUTPUT(
        code = "4.2.1",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If an incomplete Su-field system cannot be detected or measured, a single or double" +
                " Su-field system with a field as an output is created. If the existing field is" +
                " inadequate, change or enhance the field without interfering with the original" +
                " system. The new or enhanced field should have an easily detectable parameter that" +
                " correlates to the parameter we need to know.",
    ),
    MEASURE_AN_INTRODUCED_ADDITIVE(
        code = "4.2.2",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Measure an introduced additive. Introduce an additive that reacts to a change in the" +
                " original system, then measure the changes in the additive.",
    ),
    MEASURE_EFFECT_ON_EXTERNAL_ENVIRONMENT_FIELD(
        code = "4.2.3",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If nothing can be added to the system, then detect or measure the system's effect on" +
                " a field created by additive(s) placed in the external environment.",
    ),
    MEASURE_EFFECT_ON_CREATED_ENVIRONMENT_ADDITIVES(
        code = "4.2.4",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If additives cannot be introduced into the environment of the system as in 4.2.3," +
                " then create them by decomposing or changing the state of something that is already" +
                " in the environment, and measure the effect of the system on these created additives.",
    ),
    APPLY_NATURAL_PHENOMENA(
        code = "4.3.1",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Apply natural phenomena. Use scientific effects that are known to occur in the" +
                " system, and determine the state of the system by observing changes in the effects.",
    ),
    MEASURE_RESONANT_FREQUENCY(
        code = "4.3.2",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If changes in a system cannot be determined directly or by passing a field, measure" +
                " the excited resonant frequency of the system or an element in order to measure" +
                " changes.",
    ),
    MEASURE_RESONANT_FREQUENCY_OF_JOINED_OBJECT(
        code = "4.3.3",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If 4.3.2 is not possible, measure the resonant frequency of the object joined to" +
                " another of known properties.",
    ),
    USE_FERROMAGNETIC_SUBSTANCE_AND_MAGNETIC_FIELD_FOR_MEASUREMENT(
        code = "4.4.1",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Add or make use of a ferromagnetic substance and a magnetic field in a system (by" +
                " means of permanent magnets or loops of electric current) to facilitate measurement.",
    ),
    ADD_MAGNETIC_PARTICLES_FOR_MEASUREMENT(
        code = "4.4.2",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Add magnetic particles to a system or change a substance to ferromagnetic particles" +
                " to facilitate measurement by detection of the resulting magnetic field.",
    ),
    CONSTRUCT_COMPLEX_SYSTEM_WITH_FERROMAGNETIC_ADDITIVES(
        code = "4.4.3",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "If ferromagnetic particles cannot be added directly to the system or a substance" +
                " cannot be replaced with ferromagnetic particles, construct a complex system, by" +
                " putting ferromagnetic additives into the substance.",
    ),
    ADD_FERROMAGNETIC_PARTICLES_TO_ENVIRONMENT(
        code = "4.4.4",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Add ferromagnetic particles to the environment, if they cannot be added to the" +
                " system.",
    ),
    MEASURE_MAGNETIC_NATURAL_PHENOMENA(
        code = "4.4.5",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Measure the effects of natural phenomena associated with magnetism such as the Curie" +
                " point, hysteresis, quenching of superconductivity, the Hall effect, etc.",
    ),
    TRANSITION_TO_BI_OR_POLY_MEASUREMENT_SYSTEMS(
        code = "4.5.1",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Transition to bi- and poly-systems. If a single measurement system does not give" +
                " sufficient accuracy, use two or more measuring systems, or make multiple" +
                " measurements.",
    ),
    MEASURE_DERIVATIVES_INSTEAD_OF_DIRECT_PHENOMENON(
        code = "4.5.2",
        standardClass = StandardSolutionClass.MEASUREMENT_AND_DETECTION,
        labelEn =
            "Instead of a direct measurement of a phenomenon, measure the first and second" +
                " derivatives in time or in space. For example, measure velocity and acceleration" +
                " instead of measuring position. Measure the rate of frequency change of a sound" +
                " (Doppler shift) to determine the velocity of the source.",
    ),
    INDIRECT_SUBSTANCE_INTRODUCTION(
        code = "5.1.1",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn =
            "Indirect ways [of introducing a substance]: use nothing (add air, vacuum, bubbles," +
                " foam, voids, hollows, clearances, capillaries, pores, holes, etc.); use a field" +
                " instead of a substance; use an external additive instead of an internal one; use a" +
                " small amount of a very active additive; concentrate the additive at a specific" +
                " location; introduce the additive temporarily; use a copy or model of the object in" +
                " which additives can be used, instead of the original object; introduce a chemical" +
                " compound which reacts, yielding the desired elements or compounds, where introducing" +
                " the desired material would be harmful; or obtain the required additive by" +
                " decomposition of either the environment or the object itself.",
    ),
    DIVIDE_ELEMENTS_INTO_SMALLER_UNITS(
        code = "5.1.2",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Divide the elements into smaller units.",
    ),
    SELF_ELIMINATING_ADDITIVE(
        code = "5.1.3",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "The additive eliminates itself after use.",
    ),
    USE_NOTHING_WHEN_QUANTITY_IS_LIMITED(
        code = "5.1.4",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Use nothing if circumstances do not permit the use of large quantities of material.",
    ),
    USE_ONE_FIELD_TO_CREATE_ANOTHER(
        code = "5.2.1",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Use one field to cause the creation of another field.",
    ),
    USE_FIELDS_PRESENT_IN_ENVIRONMENT(
        code = "5.2.2",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Use fields that are present in the environment.",
    ),
    USE_SUBSTANCES_THAT_ARE_FIELD_SOURCES(
        code = "5.2.3",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Use substances that are the sources of fields.",
    ),
    PHASE_SUBSTITUTION(
        code = "5.3.1",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Phase Transition 1: Substituting the Phases.",
    ),
    DUAL_PHASE_STATE(
        code = "5.3.2",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Phase Transition 2: Dual Phase State.",
    ),
    PHASE_CHANGE_ACCOMPANYING_PHENOMENA(
        code = "5.3.3",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Phase Transition 3: Utilizing the Accompanying Phenomena of the Phase Change.",
    ),
    TRANSITION_TO_TWO_PHASE_STATE(
        code = "5.3.4",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Phase Transition 4: Transition to the Two-Phase State.",
    ),
    INTERACTION_OF_PHASES(
        code = "5.3.5",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn =
            "Interaction of the Phases. Increase the effectiveness of the system by inducing an" +
                " interaction between the elements of the system, or the phases of the system.",
    ),
    SELF_CONTROLLED_TRANSITIONS(
        code = "5.4.1",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn =
            "Self-controlled Transitions. If an object must be in several different states, it" +
                " should transition from one state to the other by itself.",
    ),
    STRENGTHEN_OUTPUT_NEAR_PHASE_TRANSITION(
        code = "5.4.2",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn =
            "Strengthening the output field when there is a weak input field. Generally this is" +
                " done by working near a phase transition point.",
    ),
    OBTAIN_PARTICLES_BY_DECOMPOSITION(
        code = "5.5.1",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Obtaining the Substance Particles (Ions, Atoms, Molecules, etc.) by Decomposition.",
    ),
    OBTAIN_PARTICLES_BY_JOINING(
        code = "5.5.2",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn = "Obtaining the substance particles by joining.",
    ),
    APPLY_DECOMPOSITION_AND_JOINING_AT_NEXT_STRUCTURAL_LEVEL(
        code = "5.5.3",
        standardClass = StandardSolutionClass.APPLICATION_STRATEGIES,
        labelEn =
            "Applying the Standard Solutions 5.5.1 and 5.5.2. If a substance of a high structural" +
                " level has to be decomposed, and it cannot be decomposed, start with the substance of" +
                " the next highest level. Likewise, if a substance must be formed from materials of a" +
                " low structural level, and it cannot be, then start with the next higher level of" +
                " structure. In the antenna problem (5.4.1) the gas molecules are ionized, not the" +
                " whole antenna, to create the path for the lightning, and the ions and electrons" +
                " are recombined to restore neutrality.",
    ),
    ;

    companion object {
        private val byCode = entries.associateBy(StandardSolution::code)

        /** @throws IllegalStateException if [code] is not one of the 76 classical codes. */
        fun ofCode(code: String): StandardSolution = byCode[code] ?: error("No standard solution with code=$code")
    }
}
