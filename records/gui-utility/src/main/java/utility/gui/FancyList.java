package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * A ListView which allows deletion of selected items using a little cross to the right (or pressing backspace/delete), which is
 * animated by sliding out the items.
 */
public abstract class FancyList<T>
{
    private final VBox children = new VBox();
    private final ArrayList<Cell> cells = new ArrayList<>();
    private final BitSet selection = new BitSet(); 
    private boolean hoverOverSelection = false;
    private final boolean allowReordering;
    private final boolean allowDeleting;
    private final ScrollPane scrollPane = new ScrollPaneFill(children);
    protected final @Nullable Button addButton;

    public FancyList(ImmutableList<T> initialItems, boolean allowDeleting, boolean allowReordering, boolean allowInsertion)
    {
        this.allowDeleting = allowDeleting;
        this.allowReordering = allowReordering;
        children.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)
            {
                Utility.later(this).deleteCells(Utility.later(this).getSelectedCells());
            }
        });
        for (T initialItem : initialItems)
        {
            cells.add(new Cell(initialItem));
        }
        addButton = GUI.button("add", () -> {
            cells.add(new Cell(null));
            updateChildren();
        });
        updateChildren();
    }

    private ImmutableList<Cell> getSelectedCells()
    {
        ImmutableList.Builder<Cell> builder = ImmutableList.builder();
        for (int i = 0; i < cells.size(); i++)
        {
            if (selection.get(i))
                builder.add(cells.get(i));
        }
        return builder.build();
    }

    // For overriding in subclasses:
    @OnThread(Tag.FXPlatform)
    protected abstract Pair<Node, ObjectExpression<T>> makeCellContent(@Nullable T initialContent);

    private void deleteCells(List<Cell> selectedCells)
    {
        animateOutToRight(selectedCells, () -> {
            cells.removeAll(selectedCells);
            updateChildren();
        });
    }

    private void animateOutToRight(List<Cell> cells, FXPlatformRunnable after)
    {
        SimpleDoubleProperty amount = new SimpleDoubleProperty(0);
        for (Cell cell : cells)
        {
            cell.translateXProperty().bind(amount);
        }
        
        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
                Utility.mapList(cells, c -> new KeyValue(amount, c.getWidth())).toArray(new KeyValue[0])));
        
        t.setOnFinished(e -> after.run());
        t.play();

    }

    @RequiresNonNull({"children", "cells"})
    private void updateChildren(@UnknownInitialization(Object.class) FancyList<T> this)
    {
        ArrayList<Node> nodes = new ArrayList<>(this.cells);
        if (addButton != null)
            nodes.add(addButton);
        children.getChildren().setAll(nodes);
    }

    public ImmutableList<T> getItems()
    {
        return cells.stream().map(c -> c.value.get()).collect(ImmutableList.toImmutableList());
    }

    public Region getNode()
    {
        return scrollPane;
    }

    /**
     * Gets the nearest gap before/after a cell to the given scene X/Y position.  The first component
     * of the pair is the cell above (may be blank if at top of list), the second component is the
     * one below (ditto if at bottom).  Both parts may be blank if list is empty.
     * @return
     */
    /*
    @OnThread(Tag.FXPlatform)
    public Pair<@Nullable DeletableListCell, @Nullable DeletableListCell> getNearestGap(double sceneX, double sceneY)
    {
        // Y is in scene coords.  We set initial a pixel outside so that any items within bounds will "win" against them:
        Pair<Double, @Nullable DeletableListCell> nearestAbove = new Pair<>(localToScene(0.0, -1.0).getY(), null);
        Pair<Double, @Nullable DeletableListCell> nearestBelow = new Pair<>(localToScene(0.0, getHeight() + 1.0).getY(), null);

        for (WeakReference<DeletableListCell> ref : allCells)
        {
            @Nullable DeletableListCell cell = ref.get();
            if (cell != null && !cell.isEmpty())
            {
                Bounds sceneBounds = cell.localToScene(cell.getBoundsInLocal());
                if (Math.abs(sceneBounds.getMaxY() - sceneY) < Math.abs(nearestAbove.getFirst() - sceneY))
                {
                    nearestAbove = new Pair<>(sceneBounds.getMaxY(), cell);
                }

                if (Math.abs(sceneBounds.getMinY() - sceneY) < Math.abs(nearestBelow.getFirst() - sceneY))
                {
                    nearestBelow = new Pair<>(sceneBounds.getMinY(), cell);
                }
            }
        }

        // If nearest below is above nearest above, we picked both from last cell in the list; only return the nearest above:
        if (nearestBelow.getFirst() < nearestAbove.getFirst())
            return new Pair<>(nearestAbove.getSecond(), null);
        else
            return new Pair<>(nearestAbove.getSecond(), nearestBelow.getSecond());
    }*/

    protected class Cell extends BorderPane
    {
        protected final SmallDeleteButton deleteButton;
        protected final Node content;
        private final ObjectExpression<T> value;
        
        public Cell(@Nullable T initialContent)
        {
            getStyleClass().add("fancy-list-cell");
            deleteButton = new SmallDeleteButton();
            deleteButton.setOnAction(() -> {
                if (isSelected(this))
                {
                    // Delete all in selection
                    deleteCells(getSelectedCells());
                }
                else
                {
                    // Just delete this one
                    deleteCells(ImmutableList.of(Utility.later(this)));
                }
            });
            deleteButton.setOnHover(entered -> {
                if (isSelected(this))
                {
                    hoverOverSelection = entered;
                    // Set hover state on all (including us):
                    for (Cell selectedCell : getSelectedCells())
                    {
                        selectedCell.updateHoverState(hoverOverSelection);
                    }

                }
                // If not selected, nothing to do
            });
            //deleteButton.visibleProperty().bind(deletable);
            Pair<Node, ObjectExpression<T>> pair = makeCellContent(initialContent);
            this.content = pair.getFirst();
            this.value = pair.getSecond();
            setCenter(this.content);
            if (allowDeleting)
                setRight(deleteButton);
        }

        @OnThread(Tag.FXPlatform)
        private void updateHoverState(boolean hovering)
        {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("my_hover_sel"), hovering);
        }
    }

    private boolean isSelected(@UnknownInitialization Cell cell)
    {
        int index = Utility.indexOfRef(cells, cell);
        return index < 0 ? false : selection.get(index);
    }
}
