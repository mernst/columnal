package records.gui.grid;

import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A selection of cells.  This might be, for example:
 * - A selection of one or more rows
 * - A selection of one or more columns
 * - The entire table
 * - A rectangular grid of cells within the table of at least 1x1
 */
@OnThread(Tag.FXPlatform)
public interface CellSelection
{
    // Primary selection means the single cell/row/column being moved around,
    // secondary selection means cells that are also selected but not primary.
    public static enum SelectionStatus { UNSELECTED, SECONDARY_SELECTION, PRIMARY_SELECTION}

    /**
     * Gets a new selection that is the result of pressing home on this one.
     */
    public CellSelection atHome(boolean extendSelection);

    /**
     * Gets a new selection that is the result of pressing end on this one.
     */
    public CellSelection atEnd(boolean extendSelection);
    
    public CellSelection move(boolean extendSelection, int byRows, int byColumns);
    
    public CellPosition positionToEnsureInView();
    
    public RectangleBounds getSelectionDisplayRectangle();
}
