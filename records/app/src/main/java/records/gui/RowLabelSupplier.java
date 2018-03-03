package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.DataItemPosition;
import records.gui.RowLabelSupplier.LabelPane;
import records.gui.RowLabelSupplier.Visible;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGridSupplierIndividual;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;

import java.util.Arrays;
import java.util.Collection;

public class RowLabelSupplier extends VirtualGridSupplierIndividual<LabelPane, Visible>
{
    public RowLabelSupplier()
    {
        super(ViewOrder.FLOATING, Arrays.asList(Visible.values()));
    }

    @Override
    protected LabelPane makeNewItem()
    {
        return new LabelPane();
    }

    @OnThread(Tag.FX)
    @Override
    protected void adjustStyle(LabelPane item, Visible style, boolean on)
    {
        item.setVisible(on);
    }

    @OnThread(Tag.FXPlatform)
    public void addTable(TableDisplay tableDisplay)
    {
        addGrid(tableDisplay, new GridCellInfo<LabelPane, Visible>()
        {
            private final SimpleObjectProperty<ImmutableList<Visible>> visible = new SimpleObjectProperty<>(ImmutableList.of()); 
            
            @Override
            public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
            {
                @AbsColIndex int columnForRowLabels = tableDisplay.getPosition().columnIndex - CellPosition.col(1);
                @AbsRowIndex int topRowLabel = tableDisplay.getDataDisplayTopLeftIncl().from(tableDisplay.getPosition()).rowIndex;
                @AbsRowIndex int bottomRowLabel = tableDisplay.getDataDisplayBottomRightIncl().from(tableDisplay.getPosition()).rowIndex;
                return GridAreaCellPosition.offsetInside(new RectangleBounds(new CellPosition(topRowLabel, columnForRowLabels), new CellPosition(bottomRowLabel, columnForRowLabels)), cellPosition);
            }

            @Override
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable LabelPane> getCell)
            {
                @Nullable LabelPane labelPane = getCell.apply(cellPosition.from(tableDisplay.getPosition()));
                if (labelPane != null)
                    labelPane.setRow(tableDisplay, tableDisplay.getRowIndexWithinTable(cellPosition.rowIndex));
            }

            @Override
            public ObjectExpression<? extends Collection<Visible>> styleForAllCells()
            {
                return visible;
            }

            @Override
            public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, LabelPane cell)
            {
                return cell.isTableRow(tableDisplay, tableDisplay.getRowIndexWithinTable(cellPosition.rowIndex));
            }
        });
    }


    public static enum Visible { VISIBLE }
    
    @OnThread(Tag.FXPlatform)
    public class LabelPane extends BorderPane
    {
        // Zero based row
        private @TableDataRowIndex int row;
        private final Label label = new Label();
        private @MonotonicNonNull TableDisplay tableDisplay;

        public LabelPane()
        {
            setRight(label);
        }
        
        public boolean isTableRow(TableDisplay tableDisplay, @TableDataRowIndex int row)
        {
            return this.tableDisplay == tableDisplay && this.row == row;
        }
        
        public void setRow(TableDisplay tableDisplay, @TableDataRowIndex int row)
        {
            this.tableDisplay = tableDisplay;
            this.row = row;
            // User rows begin with 1:
            label.setText(Integer.toString(row + 1));
        }
    }
}
