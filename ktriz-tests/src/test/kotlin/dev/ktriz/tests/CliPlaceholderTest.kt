package dev.ktriz.tests

import dev.ktriz.cli.main
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec

class CliPlaceholderTest :
    StringSpec({
        "placeholder main runs without throwing (no args)" {
            shouldNotThrowAny { main(emptyArray()) }
        }

        "placeholder main runs without throwing (with args)" {
            shouldNotThrowAny { main(arrayOf("analyze", "--foo")) }
        }
    })
