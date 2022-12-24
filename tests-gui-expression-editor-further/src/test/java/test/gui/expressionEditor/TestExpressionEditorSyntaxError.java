/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.expressionEditor;

import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

import static org.junit.Assert.*;

public class TestExpressionEditorSyntaxError extends BaseTestExpressionEditorError
{
    public void test1()
    {
        // Check basic:
        testError("1");
    }

    public void test1b()
    {
        // Check basic:
        testError("1+", e(2,2, 4,5, "missing"));
    }

    public void test2()
    {
        testError("foo", e(0,3, 0,3, "unknown"));
    }

    public void test2A()
    {
        testError("foo+1", e(0, 3, "unknown"));
    }

    public void test2B()
    {
        testError("foo+", e(4, 4, 6,7, "missing"));
    }

    public void test2C()
    {
        // Error once we leave the slot:
        // (and error in the blank operand skipped)
        testError("1+/3", e(2,2, 4,5, "missing"));
    }

    public void test2D()
    {
        // Error once we leave the slot:
        testError("foo*1", e(0, 3, "unknown"));
    }

    
    public void test3()
    {
        testError("@iftrue@then3@else5", e(19,19, 24, 25, "endif"));
    }

    public void test3A()
    {
        // Display part will be (backslash for newline: "@if %\    @then %\    @else 0\@endif"
        testError("@if%@then%@else0@endif", e(3,4, 4,5, "%"), e(9,10, 14,15, "%"));
    }

    public void test3B()
    {
        // Type error
        testError("@if3@then4@else5@endif", e(3,4, 4,5, "boolean"));
    }

    public void testEmptyBracket()
    {
        testError("()", e(1, 1, "missing", ")"));
    }

    public void testEmptyUnit()
    {
        testError("1{}", e(2, 2, "missing"));
    }

    public void testUnknownUnit1()
    {
        testError("1{zzz}", e(2, 5, "unknown"));
    }

    public void testUnknownUnit2()
    {
        testError("1{(m/zzz)}", e(5, 8, "unknown"));
    }

    public void testUnknownUnit3()
    {
        testError("type{Optional({zzz})}", e(15, 18, "unknown"));
    }

    public void testUnknownType1()
    {
        testError("type{zzz}", e(5, 8, "unknown"));
    }

    public void testUnknownType2()
    {
        testError("type{Optional(zzz)}", e(14, 17, "unknown"));
    }
    
    public void testBlankNestedLiteral()
    {
        testError("from text to(type{},Initial release date)", e(18, 18, 18,19, "empty"));
        //####
    }

    public void testUnclosedUnitBracket()
    {
        testError("1{(}", e(3, 3, "missing", ")", "end"));
    }
    
    public void testEmptyIf()
    {
        testError("@iftrue@then@else1@endif",
            e(12,12, 17,18, "missing", "@else"));
    }

    public void testEmptyIf2()
    {
        testError("@iftrue@then@else@endif",
                e(12,12, 17,18, "missing", "@else"),
                e(17,17, 27,28, "missing", "@endif"));
    }

    public void testPartialIf()
    {
        testError("@if(true>false)",
                e(15,15, 18,19, "missing", "@then"));
    }

    public void testPartialIf2()
    {
        testError("@if(ACC1>ACC1)",
            e(14,14, 17,18, "missing", "@then"));
    }

    public void testMissingOperator1()
    {
        testError("1ACC1",
                e(1,1, "missing operator"));
    }

    public void testMissingOperator2()
    {
        testError("@iftrue@then0@else1@endif@iftrue@then0@else1@endif",
                e(25,25, 36,37, "missing operator"));
    }

    public void testMissingOperator3()
    {
        testError("ACC1[1]",
                e(4,4, "missing operator"));
    }

    public void testMissingOperator3b()
    {
        testError("ACC1[1]+ACC1[2]",
                e(4,4, 4,5, "missing operator"), e(12,12, 15,16, "missing operator"));
    }
    
    public void testSpaceAfterIdent1()
    {
        // Should disappear when OK pressed, and not leave a runtime error:
        testError("ACC1 ");
        TFXUtil.fx_(() -> dumpScreenshot());
        assertEquals(ImmutableSet.of(), TFXUtil.fx(() -> lookup(".table-data-cell").match(c -> TFXUtil.fx(() -> FXUtility.hasPseudoclass(c, "has-error"))).queryAll()));
    }
    
    public void testEmptyTypeLiteral()
    {
        testError("\u0000type{", e(5,5, 5, 6, "empty"));
    }

    public void testEmptyUnitLiteral()
    {
        testError("\u0000type{Number{", e(12,12, 12, 13, ""));
    }
    
    public void testInvalidUnitLiteral()
    {
        testError("convert unit({m/s},100{yard}/2{s})",
            e(13, 14, "{"),
            e(17, 18, 20, 22,  "}"),
            e(14, 14, 14, 15, "op"),
            e(17, 17, 20, 21, "op"));
    }
    
    // Not sure we can check body without checking condition successfully.
    public void testUnknownIdents()
    {
        testError("@ifopt=~Is(x)@thenx@elsesubstitution@endif", 
            e(3, 6, 4, 7, "unknown"),
            e(24, 36, 31, 43, "unknown")
        );
    }
}
