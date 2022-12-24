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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.GenRandom;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.PopupTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TestExpressionEditorDelete extends FXApplicationTest
    implements ClickTableLocationTrait, EnterExpressionTrait, ClickOnTableHeaderTrait, PopupTrait
{
    @SuppressWarnings("nullness")
    private MainWindowActions mainWindowActions;
    private final CellPosition targetPos = new CellPosition(CellPosition.row(2), CellPosition.col(2));

    public void setupWindow() throws Exception
    {
        mainWindowActions = TAppUtil.openDataAsTable(windowToUse,null, new EditableRecordSet(ImmutableList.of(), () -> 0));

    }
    
    public void testDeleteAfterOperand(Random r) throws Exception
    {
        testBackspace("true&false", 10, 1, "true & fals", r);
    }

    public void testDeleteAfterInvalidOperator(Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(true, @unfinished \"&\")", 4, 1, "true", r);
    }

    public void testDeleteAfterSpareKeyword(Random r) throws Exception
    {
        testDeleteBackspace("@invalidops(1, @unfinished \"+\", 2, @invalidops(@unfinished \"^aif\", @invalidops ()))", 3, 3, "1+2", r);
    }

    public void testDeleteAfterInfixOperator(Random r) throws Exception
    {
        testDeleteBackspace("1+2", 1, 1, "12", r);
    }
    
    public void testDeleteAfterInfixOperator2(Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 1, 1, "ab <= c", r);
    }
    
    public void testDeleteAfterInfixOperator2b(Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 3, 2, "a < bc", r, 1);
    }

    public void testDeleteAfterInfixOperator2c(Random r) throws Exception
    {
        testDeleteBackspace("a<b<=c", 3, 1, "@invalidops(a, @unfinished \"<\", b, @unfinished \"=\", c)", r, -1);
    }
    
    public void testDeleteAfterInfixOperator3(Random r) throws Exception
    {
        testDeleteBackspace("\"a\";b", 3, 1, "@invalidops(\"a\", b)", r);
    }
    
    public void testRetypeInfix(Random r) throws Exception
    {
        testBackspaceRetype("1+2", 2, 1, "+", r);
    }

    public void testRetypeInfix2(Random r) throws Exception
    {
        testBackspaceRetype("1<=2", 3, 2, "<=", r);
    }

    public void testRetypeLeadingOperand(Random r) throws Exception
    {
        testBackspaceRetype("1+2", 1, 1, "1", r);
    }

    public void testRetypeLeadingOperand2(Random r) throws Exception
    {
        testBackspaceRetype("1234 + 5678", 4, 4, "1234", r);
    }

    public void testRetypeTrailingOperand(Random r) throws Exception
    {
        testBackspaceRetype("1+2", 3, 1, "2", r);
    }

    public void testRetypeTrailingOperand2(Random r) throws Exception
    {
        testBackspaceRetype("123+456", 7, 3,"456", r);
    }
    
    public void testRetypeListTypeContent(Random r) throws Exception
    {
        testBackspaceRetype("type{[Text]}", 10, 4, "Text", r);
    }

    public void testRetypeParameter(Random r) throws Exception
    {
        testBackspaceRetype("@call function\\\\number\\sum([2])", 7, 3, "[2]", r);
    }

    public void testRetypeParameter2(Random r) throws Exception
    {
        testBackspaceRetype("@call function\\\\number\\sum([])", 6, 2, "[]", r);
    }

    public void testRetypeParameter3(Random r) throws Exception
    {
        testBackspaceRetype("@call function\\\\core\\convert unit(foo*unit{m}, 1{cm})", 15, 2, "fo", r);
    }

    public void testRetypeWordInIdent(Random r) throws Exception
    {
        testBackspaceRetype("the quick brown fox", 9, 5, "quick", r);
    }
    
    
    
    // TODO more retype tests

    public void testPasteSeveral(Random r) throws Exception
    {
        testPaste("12", 1, "+3+4-", "1+3+4-2", r);
    }

    private void testPaste(String original, int caretPos, String paste, String expected, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = TFunctionUtil.parseExpression(original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);

        TFXUtil.fx_(() -> {
            expressionEditor._test_positionCaret(caretPos);
            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, paste));
        });
        push(KeyCode.SHORTCUT, KeyCode.V);

        Expression after = (Expression) TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(TFunctionUtil.parseExpression(expected, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }
    
    private void testBackspaceRetype(String original, int deleteBefore, int deleteCount, String retype, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression originalExp = TFunctionUtil.parseExpression(original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);

        TFXUtil.fx_(() -> expressionEditor._test_positionCaret(deleteBefore));
        sleep(200);

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        if (r.nextBoolean())
            write(retype);
        else
        {
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, retype)));
            push(KeyCode.SHORTCUT, KeyCode.V);
        }

        Expression after = (Expression) TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(originalExp, after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testDeleteBackspace(String original, int deleteAfterPos, int deleteCount, String expectedStr, Random r, int... cutCount) throws Exception
    {
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testBackspace(original, deleteAfterPos + deleteCount, deleteCount, expectedStr, r);
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOn(".id-tableDisplay-menu-delete");
        TFXUtil.sleep(300);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        testDelete(original, deleteAfterPos, deleteCount, expectedStr, r);
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOn(".id-tableDisplay-menu-delete");
        TFXUtil.sleep(300);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        if (cutCount.length == 0 || cutCount[0] > 0)
            testCut(original, deleteAfterPos, cutCount.length > 0 ? cutCount[0] : deleteCount, expectedStr, r);
    }

    private void testBackspace(String original, int deleteBefore, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = TFunctionUtil.parseExpression(expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        Expression originalExp = TFunctionUtil.parseExpression(original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(originalExp, r);
        
        assertEquals(originalExp, TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false)));

        TFXUtil.fx_(() -> expressionEditor._test_positionCaret(deleteBefore));

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        
        TFXUtil.sleep(1000);

        Expression after = (Expression) TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);
        
        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testDelete(String original, int deleteAfter, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = TFunctionUtil.parseExpression(expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(TFunctionUtil.parseExpression(original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), r);

        TFXUtil.fx_(() -> expressionEditor._test_positionCaret(deleteAfter));

        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.DELETE);
        }

        Expression after = (Expression) TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);

        moveAndDismissPopupsAtPos(point(".cancel-button"));
        clickOn();
    }

    private void testCut(String original, int deleteAfter, int deleteCount, String expectedStr, Random r) throws Exception
    {
        DummyManager dummyManager = new DummyManager();
        Expression expectedExp = TFunctionUtil.parseExpression(expectedStr, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        EditorDisplay expressionEditor = enter(TFunctionUtil.parseExpression(original, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager())), r);

        TFXUtil.fx_(() -> expressionEditor._test_positionCaret(deleteAfter));

        press(KeyCode.SHIFT);
        for (int i = 0; i < deleteCount; i++)
        {
            push(KeyCode.RIGHT);
        }
        release(KeyCode.SHIFT);
        
        if (r.nextBoolean())
            push(KeyCode.DELETE);
        else
        {
            // Test copy does same as cut:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, "EMPTY")));
            push(KeyCode.SHORTCUT, KeyCode.C);
            String copied = TFXUtil.<String>fx(() -> Clipboard.getSystemClipboard().getString());
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, "EMPTY")));
            push(KeyCode.SHORTCUT, KeyCode.X);
            assertEquals(copied, TFXUtil.<String>fx(() -> Clipboard.getSystemClipboard().getString()));
        }
        

        Expression after = (Expression) TFXUtil.fx(() -> expressionEditor._test_getEditor().save(false));

        assertEquals(expectedExp, after);

        clickOn(".cancel-button");
    }
    
    private EditorDisplay enter(Expression expression, Random r) throws Exception
    {
        Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
        push(KeyCode.SHORTCUT, KeyCode.HOME);
        for (int i = 0; i < 2; i++)
            clickOnItemInBounds(fromNode(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        clickOn(".id-new-transform");
        clickOn(".id-transform-calculate");
        write("Table1");
        push(KeyCode.ENTER);
        TFXUtil.sleep(200);
        write("DestCol");
        // Focus expression editor:
        push(KeyCode.TAB);
        
        enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);

        return waitForOne(".editor-display");
    }
}
