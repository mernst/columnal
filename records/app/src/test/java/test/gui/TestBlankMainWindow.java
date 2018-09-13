package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.robot.Motion;
import org.testfx.service.query.PointQuery;
import org.testfx.service.query.impl.NodeQueryImpl;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.ConcreteDataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.expression.type.TypeExpression;
import test.TestUtil;
import test.gen.GenDataType;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestBlankMainWindow extends ApplicationTest implements ComboUtilTrait, ScrollToTrait, ClickTableLocationTrait, EnterTypeTrait, EnterStructuredValueTrait
{
    @SuppressWarnings("nullness")
    private @NonNull Stage mainWindow;
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull MainWindowActions mainWindowActions;

    @Override
    public void start(Stage stage) throws Exception
    {
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        mainWindowActions = MainWindow.show(stage, dest, null);
        mainWindow = stage;
    }

    @After
    @OnThread(Tag.Any)
    public void hide()
    {
        Platform.runLater(() -> {
            // Take a copy to avoid concurrent modification:
            new ArrayList<>(MainWindow._test_getViews().values()).forEach(Stage::hide);
        });
    }

    // Both a test, and used as utility method.
    @Test
    @OnThread(Tag.Any)
    public void testStartState()
    {
        assertTrue(TestUtil.fx(() -> mainWindow.isShowing()));
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().size()));
        assertTrue(TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty()));
    }

    @Test
    public void testNewClick()
    {
        testStartState();
        clickOn("#id-menu-project").clickOn(".id-menu-project-new");
        assertEquals(2, MainWindow._test_getViews().size());
        assertTrue(MainWindow._test_getViews().values().stream().allMatch(Stage::isShowing));
    }

    @Test
    public void testCloseMenu()
    {
        testStartState();
        clickOn("#id-menu-project").clickOn(".id-menu-project-close");
        assertTrue(MainWindow._test_getViews().isEmpty());
    }

    @Test
    @OnThread(Tag.Any)
    public void testNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(1));
        makeNewDataEntryTable(targetPos);

        TestUtil.sleep(1000);
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(1, (int)TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
    }

    @OnThread(Tag.Any)
    private void makeNewDataEntryTable(CellPosition targetPos)
    {
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        // Only need to click once as already selected by keyboard:
        clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        clickOn(".id-new-data");
        push(KeyCode.TAB);
        write("Text", 1);
        clickOn(".ok-button");
    }

    @Test
    @OnThread(Tag.Any)
    public void testUndoNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        clickOn("#id-menu-data").moveBy(5, 0).clickOn(".id-menu-data-new", Motion.VERTICAL_FIRST);
        TestUtil.sleep(1000);
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
        TestUtil.sleep(1000);
        assertEquals(0, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propAddColumnToEntryTable(@When(seed=-8875669618956742531L) @From(GenDataType.class) GenDataType.DataTypeAndManager dataTypeAndManager) throws UserException, InternalException, Exception
    {
        TestUtil.printSeedOnFail(() -> {
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(dataTypeAndManager.typeManager);
            addNewTableWithColumn(dataTypeAndManager.dataType, null);
        });
    }

    @OnThread(Tag.Any)
    private void addNewTableWithColumn(DataType dataType, @Nullable @Value Object value) throws InternalException, UserException
    {
        testNewEntryTable();
        Node expandRight = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-right"))).<Node>query();
        assertNotNull(expandRight);
        // Won't happen, assertion will fail:
        if (expandRight == null) return;
        clickOn(expandRight);
        String newColName = "Column " + Math.abs(new Random().nextInt());
        write(newColName);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(dataType), new Random(1));
        // Dismiss popups:
        push(KeyCode.ESCAPE);
        if (value != null)
        {
            clickOn(".default-value");
            enterStructuredValue(dataType, value, new Random(1));
        }
        clickOn(".ok-button");
        WaitForAsyncUtils.waitForFxEvents();
        try
        {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) { }
        assertEquals(2, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
        assertEquals(newColName, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getName().getRaw()));
        assertEquals(dataType, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getType()));
    }

    private void clickOnSub(Node root, String subQuery)
    {
        assertTrue(subQuery.startsWith("."));
        @Nullable Node sub = new NodeQueryImpl().from(root).lookup(subQuery).<Node>query();
        assertNotNull(subQuery, sub);
        if (sub != null)
            clickOn(sub);
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propDefaultValue(@When(seed=8547045604407256801L) @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException, Exception
    {
        TestUtil.printSeedOnFail(() -> {
            @Value Object initialVal = typeAndValueGen.makeValue();
            addNewTableWithColumn(typeAndValueGen.getType(), initialVal);
            List<@Value Object> values = new ArrayList<>();
            for (int i = 0; i < 3; i++)
            {
                addNewRow();
                values.add(initialVal);
                // Now test for equality:
                @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
                DataTypeValue column = recordSet.getColumns().get(1).getType();
                assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
                for (int j = 0; j < values.size(); j++)
                {
                    int jFinal = j;
                    TestUtil.assertValueEqual("Index " + j, values.get(j), TestUtil.<@Value Object>sim(() -> column.getCollapsed(jFinal)));
                }
            }
        });
    }

    @Property(trials = 10)
    @OnThread(Tag.Any)
    public void testEnterColumn(@From(GenTypeAndValueGen.class) @When(seed=-746430439083107785L) TypeAndValueGen typeAndValueGen) throws InternalException, UserException, Exception
    {
        propAddColumnToEntryTable(new DataTypeAndManager(typeAndValueGen.getTypeManager(), typeAndValueGen.getType()));
        // Now set the values
        List<@Value Object> values = new ArrayList<>();
        for (int i = 0; i < 10;i ++)
        {
            addNewRow();
            @Value Object value = typeAndValueGen.makeValue();
            values.add(value);

            try
            {
                Thread.sleep(400);
            }
            catch (InterruptedException e)
            {

            }
            /*
            clickOn(new Predicate<Node>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public boolean test(Node n)
                {
                    return n.getStyleClass().contains("stable-view-row-cell") && actuallyVisible(n);
                }
            });
            try
            {
                Thread.sleep(400);
            }
            catch (InterruptedException e)
            {

            }
            push(KeyCode.END);
            */
            setValue(typeAndValueGen.getType(), value);
        }
        // Now test for equality:
        @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
        DataTypeValue column = recordSet.getColumns().get(1).getType();
        assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
        for (int i = 0; i < values.size(); i++)
        {
            int iFinal = i;
            TestUtil.assertValueEqual("Index " + i, values.get(i), TestUtil.<@Value Object>sim(() -> column.getCollapsed(iFinal)));
        }
    }

    @OnThread(Tag.Any)
    private void setValue(DataType dataType, @Value Object value) throws UserException, InternalException
    {
        Node row = lookup(new Predicate<Node>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public boolean test(Node node)
            {
                // Don't click on the last row which has the append button:
                return node.getStyleClass().contains("virt-grid-cell") && node.lookup(".stable-view-row-append-button") == null;
            }

            ;
        }).<Node>query();
        if (row != null)
        {
            targetWindow(row);
            clickOn(row);
        }
        //TODO check colour of focused cell (either check background, or take snapshot)
        Node prevFocused = TestUtil.fx(() -> targetWindow().getScene().getFocusOwner());
        // Go to last row:
        push(KeyCode.END);
        // Enter to start editing:
        push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        Node focused = TestUtil.fx(() -> targetWindow().getScene().getFocusOwner());
        assertNotNull(focused);
        write(TestUtil.sim(() -> DataTypeUtility.valueToString(dataType, value, null)));
        // Enter to finish editing:
        push(KeyCode.ENTER);
    }

    @OnThread(Tag.Any)
    private void addNewRow()
    {
        Node expandDown = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-down"))).<Node>query();
        assertNotNull(expandDown);
        // Won't happen, assertion will fail:
        if (expandDown == null) return;
        clickOn(expandDown);
        WaitForAsyncUtils.waitForFxEvents();
    }
/*
    @OnThread(Tag.Any)
    private boolean actuallyVisible(String query)
    {
        Node original = lookup(query).<Node>query();
        if (original == null)
            return false;
        return TestUtil.fx(() -> {
            return actuallyVisible(original);
        });
    }

    @NonNull
    @OnThread(Tag.FXPlatform)
    private Boolean actuallyVisible(Node original)
    {
        Bounds b = original.getBoundsInLocal();
        for (Node n = original, parent = original.getParent(); n != null && parent != null; n = parent, parent = parent.getParent())
        {
            b = n.localToParent(b);
            //System.err.println("Bounds in parent: " + b.getMinY() + "->"  + b.getMaxY());
            //System.err.println("  Parent bounds: " + parent.getBoundsInLocal().getMinY() + "->" + parent.getBoundsInLocal().getMaxY());
            if (!parent.getBoundsInLocal().contains(getCentre(b)))
                return false;
        }
        // If we get to the top and all is well, it is visible
        return true;
    }*/

    private static Point2D getCentre(Bounds b)
    {
        return new Point2D(b.getMinX(), b.getMinY()).midpoint(b.getMaxX(), b.getMaxY());
    }
}
