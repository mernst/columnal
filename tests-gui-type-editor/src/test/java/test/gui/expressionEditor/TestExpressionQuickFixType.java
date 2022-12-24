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

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

public class TestExpressionQuickFixType extends BaseTestQuickFix
{
    // Test that adding two strings suggests a quick fix to switch to string concatenation
    public void testStringAdditionFix1()
    {
        testFix("\"A\"+\"B\"", "A", "", "\"A\";\"B\"");
    }
    
    public void testStringAdditionFix2()
    {
        testFix("\"A\"+S1+\"C\"", "C", "", "\"A\" ; column\\\\S1 ; \"C\"");
    }

    public void testDateSubtractionFix1()
    {
        testFix("date from ymd(2019{year}, 1{month}, 1{day}) - date{2018-12-31}", "2019", "", "@call function\\\\datetime\\days between(@call function\\\\datetime\\date from ymd(2019{year}, 1{month}, 1{day}), date{2018-12-31})");
    }

    public void testTimeSubtractionFix1()
    {
        testFix("time from hms(12{hour}, 34{minute}, 56{s}) - time{9:00AM}", "56", "", "@call function\\\\datetime\\seconds between(@call function\\\\datetime\\time from hms(12{hour}, 34{minute}, 56{s}), time{9:00AM})");
    }
    
    public void testUnitLiteralFix1()
    {
        testFix("ACC1+6{1}", "6", "", "column\\\\ACC1 + 6{m/s^2}");
    }

    public void testUnitLiteralFix1B()
    {
        testFix("6{1}-ACC1", "6", "", "6{m/s^2} - column\\\\ACC1");
    }

    public void testUnitLiteralFix2() throws UserException, InternalException
    {
        testFix("ACC1>6{1}>ACC3", "6", dotCssClassFor("6{m/s^2}"), "column\\\\ACC1 > 6{m/s^2} > column\\\\ACC3");
    }

    public void testUnitLiteralFix3() throws UserException, InternalException
    {
        testFix("ACC1<>103{m/s}", "103", dotCssClassFor("103{m/s^2}"), "column\\\\ACC1 <> 103{m/s^2}");
    }

    public void testUnitLiteralFix3B() throws UserException, InternalException
    {
        testFix("ACC1=103{1}", "103", dotCssClassFor("103{m/s^2}"), "column\\\\ACC1 = 103{m/s^2}");
    }

    public void testUnitLiteralFix4() throws UserException, InternalException
    {
        testFix("@ifACC1=ACC2=32{1}@then2@else7+6@endif", "32", dotCssClassFor("32{m/s^2}"), "@if (column\\\\ACC1 = column\\\\ACC2 = 32{m/s^2}) @then 2 @else (7 + 6) @endif");
    }

    public void testUnitLiteralFix5() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then5@endmatch", "3", dotCssClassFor("3{m/s^2}"), "@match column\\\\ACC1 @case 3{m/s^2} @then 5 @endmatch");
    }

    public void testUnitLiteralFix6() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "3{1}", dotCssClassFor("3{m/s^2}"), "@match column\\\\ACC1 @case 3{m/s^2} @then 52 @case 12{1} @orcase 14{1} @then 63 @endmatch");
    }

    public void testUnitLiteralFix6B() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "12", dotCssClassFor("12{m/s^2}"), "@match column\\\\ACC1 @case 3{1} @then 52 @case 12{m/s^2} @orcase 14{1} @then 63 @endmatch");
    }

    public void testUnitLiteralFix6C() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "14", dotCssClassFor("14{m/s^2}"), "@match column\\\\ACC1 @case 3{1} @then 52 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    public void testUnitLiteralFix6D() throws UserException, InternalException
    {
        testFix("@matchACC1@case12{1}@orcase14{1}@then63@endmatch", "14", dotCssClassFor("14{m/s^2}"), "@match column\\\\ACC1 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    public void testUnitLiteralFix7() throws UserException, InternalException
    {
        testFix("12{metre/s}", "metre", dotCssClassFor("m"), "12{m/s}");
    }

    public void testUnitLiteralFix8() throws UserException, InternalException
    {
        testFix("type{Number{metre/s}}", "metre", dotCssClassFor("m"), "type{Number{m/s}}");
    }

    public void testUnitLiteralFix9() throws UserException, InternalException
    {
        testFix("type{Number{new/s}}", "new", ".quick-fix-action", "type{Number{new/s}}");
    }
    
    // TODO fix the scoping issue
    public void testAsType1()
    {
        testSimpleFix("minimum([])", "minimum", "@call function\\\\core\\as type(type{@invalidtypeops()},@call function\\\\comparison\\minimum([]))");
    }

    public void testAsType1b()
    {
        testSimpleFix("from text(\"\")", "from", "@call function\\\\conversion\\from text to(type{@invalidtypeops()}, \"\")");
    }

    public void testAsType2()
    {
        testSimpleFix("[]", "[", "@call function\\\\core\\as type(type{@invalidtypeops()},[])");
    }
}
