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

package test.gui.transformation;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.transformations.HideColumns;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestHideColumns extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, ListUtilTrait
{
    public void testHideColumns(GenImmediateData.ImmediateData_Mgr original,
            Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, original.mgr).get();
        TFXUtil.sleep(5000);
        Table src = mainWindowActions._test_getTableManager().getAllTables().get(0);
        RecordSet srcRS = src.getData();

        CellPosition targetPos = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TFXUtil.sleep(100);
        clickOn(".id-new-transform");
        TFXUtil.sleep(100);
        scrollTo(".id-transform-hideColumns");
        clickOn(".id-transform-hideColumns");
        TFXUtil.sleep(100);
        write(src.getId().getRaw());
        push(KeyCode.ENTER);
        TFXUtil.sleep(100);

        ArrayList<ColumnId> toHide = new ArrayList<>();
        ImmutableList<ColumnId> originalColumnIds = srcRS.getColumnIds();
        ArrayList<ColumnId> columnIdsLeft = new ArrayList<>(originalColumnIds);
        int numToHide = r.nextInt(Math.min(4, columnIdsLeft.size()));
        for (int i = 0; i < numToHide; i++)
        {
            toHide.add(columnIdsLeft.remove(r.nextInt(columnIdsLeft.size())));
        }

        for (ColumnId columnId : toHide)
        {
            selectGivenListViewItem(waitForOne(".shown-columns-list-view"), c -> c.equals(columnId));
            clickOn(".add-button");
        }
        clickOn(".ok-button");
        sleep(500);

        HideColumns hide = (HideColumns)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof HideColumns).findFirst().orElseThrow(() -> new AssertionError("No HideColumns found"));
        
        assertEquals(new HashSet<>(toHide), new HashSet<>(hide.getHiddenColumns()));

        checkActualVisibleColumns(mainWindowActions, columnIdsLeft, hide);

        if (toHide.isEmpty())
            return;

        // Try editing to unhide a column and check it refreshes:
        clickOn(".edit-hide-columns");
        ColumnId unHide = toHide.remove(r.nextInt(toHide.size()));
        selectGivenListViewItem(waitForOne(".hidden-columns-list-view"), c -> c.equals(unHide));
        clickOn(".add-button");
        clickOn(".ok-button");
        sleep(500);
        hide = (HideColumns)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof HideColumns).findFirst().orElseThrow(() -> new AssertionError("No HideColumns found"));

        assertEquals(new HashSet<>(toHide), new HashSet<>(hide.getHiddenColumns()));
        columnIdsLeft = new ArrayList<>(originalColumnIds);
        columnIdsLeft.removeAll(toHide);
        checkActualVisibleColumns(mainWindowActions, columnIdsLeft, hide);
    }

    private void checkActualVisibleColumns(MainWindowActions mainWindowActions, ArrayList<ColumnId> columnIdsLeft, HideColumns hide)
    {
        if (columnIdsLeft.isEmpty())
        {
            fail("TODO check for no-columns error");
        }
        else
        {
            for (int i = 0; i < columnIdsLeft.size(); i++)
            {
                @SuppressWarnings("nullness")
                CellPosition pos = TFXUtil.fx(() -> hide.getDisplay().getMostRecentPosition()).offsetByRowCols(1, i);
                ColumnId columnId = columnIdsLeft.get(i);
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), pos);
                withItemInBounds(".table-display-column-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(pos, pos), (n, p) -> assertEquals(columnId.getRaw(), ((Label)n).getText()));
                
            }
        }
    }
}
