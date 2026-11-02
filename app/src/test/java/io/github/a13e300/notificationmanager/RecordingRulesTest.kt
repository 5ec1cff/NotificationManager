package io.github.a13e300.notificationmanager

import org.junit.Assert.*
import org.junit.Test

class RecordingRulesTest {
    @Test fun defaultsKeepAllAndRulesCombine() {
        assertTrue(RecordingRules().accepts("a.b", "", true))
        val rules = RecordingRules(excludedPackages = " a.b \n\nc.d", requiredText = "Hello", excludedText = "SPAM", includeOngoing = false)
        assertFalse(rules.accepts("a.b", "hello", false))
        assertFalse(rules.accepts("x.y", "hello spam", false))
        assertFalse(rules.accepts("x.y", "hello", true))
        assertFalse(rules.accepts("x.y", "other", false))
        assertTrue(rules.accepts("x.y", "HELLO world", false))
        assertFalse(rules.copy(enabled = false).accepts("x.y", "hello", false))
    }
}
