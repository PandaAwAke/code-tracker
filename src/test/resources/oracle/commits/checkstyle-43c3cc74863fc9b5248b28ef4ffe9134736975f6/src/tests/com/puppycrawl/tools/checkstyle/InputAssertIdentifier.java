////////////////////////////////////////////////////////////////////////////////
// Test case file for checkstyle.
// Created: 2001
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle;

/**
 * Test case for assert handling fallback.
 * This file uses "assert" is as an identifier, so the default grammar
 * fails and the JDK 1.3 grammar has to be used.
 * @author Lars K�hne
 **/
class InputAssertIdentifier
{
    /** test method **/
    void assert()
    {
        int assert = 1;
        int a = assert;
    }
}
