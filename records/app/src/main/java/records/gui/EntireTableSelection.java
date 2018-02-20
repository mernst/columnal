package records.gui;

import records.data.CellPosition;
import records.gui.grid.CellSelection;

public class EntireTableSelection implements CellSelection
{
    private final DataDisplay selected;

    public EntireTableSelection(DataDisplay selected)
    {
        this.selected = selected;
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return this;
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        return this;
    }

    @Override
    public CellSelection move(boolean extendSelection, int byRows, int byColumns)
    {
        // TODO allow moving out
        return this;
    }

    @Override
    public CellPosition positionToEnsureInView()
    {
        return selected.getPosition();
    }
}
