package records.gui.grid;

import javafx.geometry.Point2D;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;

/**
 * One rectangular table area within a parent VirtualGrid.  Tracks position,
 * size, adjusts when resized.
 * 
 * Overlays such as line numbers are not included in the logical bounds,
 * but column headers are included.
 * 
 * No two GridArea items may overlap, and VirtualGrid will reposition to make sure
 * this is always true.
 * 
 * Each GridArea is assumed to have a fixed immediately-knowable position and number of columns,
 * but a number of rows which may not be known ahead of time.
 */
@OnThread(Tag.FXPlatform)
public abstract class GridArea
{
    // The top left cell, which is probably a column header.
    private CellPosition topLeft;
    
    private MessageWhenEmpty messageWhenEmpty;
    private @MonotonicNonNull VirtualGrid parent;

    public GridArea(MessageWhenEmpty messageWhenEmpty)
    {
        this.messageWhenEmpty = messageWhenEmpty;
        // Default position:
        this.topLeft = new CellPosition(CellPosition.row(1), CellPosition.col(1));
    }

    public final CellPosition getPosition(@UnknownInitialization(GridArea.class) GridArea this)
    {
        return topLeft;
    }
    
    public void setPosition(CellPosition cellPosition)
    {
        topLeft = cellPosition;
        updateParent();
    }

    protected void updateParent(@UnknownInitialization(GridArea.class) GridArea this)
    {
        if (parent != null)
            parent.positionOrAreaChanged();
    }
    
    // Calls the consumer, iff we have a non-null parent
    protected void withParent(@UnknownInitialization(GridArea.class) GridArea this, FXPlatformConsumer<VirtualGrid> withVirtualGrid)
    {
        if (parent != null)
            withVirtualGrid.consume(parent);
    }    

    public final void addedToGrid(VirtualGrid parent)
    {
        this.parent = parent;
    }

    public final VirtualGrid _test_getParent()
    {
        if (parent == null)
            throw new RuntimeException("GridArea " + this + " has no parent");
        return parent;
    }

    /**
     * Check if more rows are available, up to and including the given row number (but not beyond).
     * If you need to do a calculation off-thread, and find that you do have a new size
     * (i.e. different to the one you return from the method), call the runnable
     * @param checkUpToRowIncl The row to check up to in overall grid position
     * @param updateSizeAndPositions The runnable to call if the size later changes.
     * @return The current known row size.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract void updateKnownRows(int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions);

    public final int getAndUpdateKnownRows(int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
    {
        updateKnownRows(checkUpToRowIncl, updateSizeAndPositions);
        return getCurrentKnownRows();
    }

    public final boolean contains(CellPosition cellPosition)
    {
        return cellPosition.rowIndex >= topLeft.rowIndex && cellPosition.rowIndex < topLeft.rowIndex + getCurrentKnownRows()
            && cellPosition.columnIndex >= topLeft.columnIndex && cellPosition.columnIndex < topLeft.columnIndex + getColumnCount();
    }
    
    // Including any expand arrows, etc:
    public abstract int getColumnCount();
    // Remember -- including all headers:
    public abstract int getCurrentKnownRows();

    public void setMessageWhenEmpty(MessageWhenEmpty messageWhenEmpty)
    {
        this.messageWhenEmpty = messageWhenEmpty;
    }

    // Return true if click has been handled:
    public abstract boolean clicked(Point2D screenPosition, CellPosition cellPosition);
}
