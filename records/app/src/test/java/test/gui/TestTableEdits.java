package test.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.units.qual.UnitsBottom;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.transformations.Sort;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenTableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.Workers;
import utility.Workers.Priority;

import java.awt.Toolkit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestTableEdits extends ApplicationTest implements ClickTableLocationTrait
{
    @SuppressWarnings("nullness")
    private @NonNull Stage mainWindow;

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull VirtualGrid virtualGrid;

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull TableManager tableManager;
    
    private final CellPosition originalTableTopLeft = new CellPosition(CellPosition.row(1), CellPosition.col(2));
    private final CellPosition transformTopLeft = new CellPosition(CellPosition.row(3), CellPosition.col(6));
    private int originalColumns = 2;
    @OnThread(Tag.Any)
    private final CompletableFuture<Optional<Exception>> finish = new CompletableFuture<>();
    

    @Override
    public void start(Stage stage) throws Exception
    {
        mainWindow = stage;
        TableManager dummyManager = new DummyManager();
        Workers.onWorkerThread("Making tables", Priority.FETCH, () -> {
            try
            {
                ExFunction<RecordSet, ? extends EditableColumn> a = rs -> new MemoryBooleanColumn(rs, new ColumnId("A"), ImmutableList.of(true, false, false), false);
                ExFunction<RecordSet, ? extends EditableColumn> b = rs -> new MemoryNumericColumn(rs, new ColumnId("B"), NumberInfo.DEFAULT, ImmutableList.of(5, 4, 3), 6);
                @SuppressWarnings({"units", "keyfor"})
                @KeyForBottom @UnitsBottom ImmutableList<ExFunction<RecordSet, ? extends EditableColumn>> columns = ImmutableList.of(a, b);
                ImmediateDataSource src = new ImmediateDataSource(dummyManager, new EditableRecordSet(columns, () -> 3));
                src.loadPosition(originalTableTopLeft);
                dummyManager.record(src);
                Sort sort = new Sort(dummyManager, new TableId("Sorted"), src.getId(), ImmutableList.of(new ColumnId("B")));
                sort.loadPosition(transformTopLeft);
                dummyManager.record(sort);
                Pair<TableManager, VirtualGrid> details = TestUtil.openDataAsTable(stage, dummyManager).get();
                this.tableManager = details.getFirst();
                virtualGrid = details.getSecond();
                finish.complete(Optional.empty());
            }
            catch (Exception e)
            {
                Log.log(e);
                finish.complete(Optional.of(e));
            }
            Platform.runLater(() -> com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, finish));
        });
        com.sun.javafx.tk.Toolkit.getToolkit().enterNestedEventLoop(finish);
        //assertNull(finish.get(5, TimeUnit.SECONDS).orElse(null));
    }

    @Property(trials = 3)
    public void testRenameTable(@From(GenTableId.class) TableId newTableId) throws Exception
    {
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(0, originalColumns));
        clickOnItemInBounds(lookup(".table-display-table-title"), virtualGrid, rectangleBounds);
        deleteAll();
        write(newTableId.getRaw());
        // Different ways of exiting:

        // Click on right-hand end of table header:
        Bounds headerBox = virtualGrid._test_getRectangleBoundsScreen(rectangleBounds);
        clickOn(new Point2D(headerBox.getMaxX() - 2, headerBox.getMinY() + 2));

        assertEquals(ImmutableSet.of("Sorted", newTableId.getRaw()), tableManager.getAllTables().stream().map(t -> t.getId().getRaw()).sorted().collect(Collectors.toSet()));
    }
         

    private void deleteAll()
    {
        press(KeyCode.END);
        press(KeyCode.SHIFT, KeyCode.HOME);
        press(KeyCode.DELETE);
    }
}
