package records.gui.grid;

import annotation.help.qual.UnknownIfHelp;
import annotation.qual.UnknownIfValue;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.userindex.qual.UnknownIfUserIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.effect.Effect;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.dataflow.qual.Pure;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier.ContainerChildren;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.stable.ScrollBindable;
import records.gui.stable.ScrollGroup;
import records.gui.stable.ScrollGroup.ScrollLock;
import records.gui.stable.ScrollGroup.Token;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * 
 * Scrolling:
 * 
 * A scroll position is held as an item index and offset.  The offset is always negative, and between zero and
 * negative height of the item.  For example, our rows are 24 pixels tall at the moment.  When you are viewing the
 * very top of the file, that is item #0 at offset 0.0  If you scroll down one pixel, that is item #0 at offset -1.0
 * Another 20 pixels and it is #0 at -21.0.  However, another five pixels will show #1 at -2.0  (since -24.0 will
 * be the whole item, and 2 pixels left over at the end).
 * 
 * When you scroll, the scroll-by amount is how many pixels to move the offset.  So negative scroll-by moves the
 * items up, which as far as the user is concerned, moves down the document.  It's a bit more like the macOS model
 * of thinking about it (even though I find that confusion): we are moving the *document*, not the viewport.
 * So remember:
 *   - Negative scroll-by: offset goes more negative.  Item index goes up as the document moves up.
 *     Viewport slides down document, in effect.
 *   - Positive scroll-by: offset goes more positive.  Item index goes down as the document moves down.
 *     Viewport slides up document, in effect.
 * 
 */

@OnThread(Tag.FXPlatform)
public final class VirtualGrid implements ScrollBindable
{
    private static final double MAX_EXTRA_X_PIXELS = 800;
    private static final double MAX_EXTRA_Y_PIXELS = 800;
    // How many columns beyond last table to show in the grid
    // (logical, not to do with rendering/scrolling)
    private final int columnsToRight;
    // Ditto for rows
    private final int rowsToBottom;
    private final List<VirtualGridSupplier<? extends Node>> nodeSuppliers = new ArrayList<>();
    private final List<GridArea> gridAreas = new ArrayList<>();
    
    private final Container container;
    private static final int MAX_EXTRA_ROW_COLS = 12;
    private final ScrollBar hBar;
    private final ScrollBar vBar;
    private final ScrollGroup scrollGroup;
    private final ArrayList<SelectionListener> selectionListeners = new ArrayList<>();
    private final BorderPane paneWithScrollBars;
    // Used as a sort of lock on updating the scroll bars to prevent re-entrant updates:
    private boolean settingScrollBarVal = false;
    
    // The visible portion of VirtualGrid has two aspects.  There is the logical position,
    // which is in theory where the top-left of the viewport currently resides.  This may not correspond
    // to the current screen position temporarily if there is smooth scrolling going on, but it is where
    // the top-left will be if you did a sleep for several seconds.
    // The other aspect is the rendering bounds.  We render (render here meaning that
    // we layout cells) a region that may extend above/below/left/right of the current visible portion
    // of the viewport.  This is to allow the next scroll (or window resize) to be carried out without
    // requiring a re-layout.  A layout is only performed if we reach the extents of the most recent
    // render, or if we reach a new row in the table.
    
    // Outer rectangle is render extents, inner rectangle is logical extents.  If you scroll a little,
    // just slide logical position without redoing render extents: 
    // +-------------------------------------+  ^
    // |Render                               |  | extraRenderYPixelsBefore
    // |                                     |  v
    // |     +-------------------+           |
    // |     |Logical            |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     |                   |           |
    // |     +-------------------+           |
    // |                                     | ^
    // |                                     | | extraRenderYPixelsAtfer
    // +-------------------------------------+ v
        
    // The indexes of the top/left of the render extents:
    private @AbsColIndex int firstRenderColumnIndex = CellPosition.col(0);
    private @AbsRowIndex int firstRenderRowIndex = CellPosition.row(0);
    // Offset of first cell being rendered.  Always zero or negative:
    private double firstRenderColumnOffset = 0.0;
    private double firstRenderRowOffset = 0.0;
    
    // How many pixels are currently rendered before/after logical extents.  The before amount
    // is always a valid amount that could be rendered before, but the after amount may actually
    // extend beyond what is currently visible.
    // Both amounts are always positive.
    private double extraRenderXPixelsBefore = 0;
    private double extraRenderXPixelsAfter = MAX_EXTRA_X_PIXELS;
    private double extraRenderYPixelsBefore = 0;
    private double extraRenderYPixelsAfter = MAX_EXTRA_Y_PIXELS;
    
    // Where is our theoretical scroll position?   We don't use this for any rendering,
    // only for knowing where to scroll to on next scroll (because we may need to render
    // extra portions outside the logical scroll position).
    private @AbsColIndex int logicalScrollColumnIndex = CellPosition.col(0);
    private @AbsRowIndex int logicalScrollRowIndex = CellPosition.row(0);
    // What is the offset of first item?  Always between negative width of current item and zero.  Never positive.
    private double logicalScrollColumnOffset = 0.0;
    private double logicalScrollRowOffset = 0.0;
    
    private final ObjectProperty<@AbsRowIndex Integer> currentKnownLastRowIncl = new SimpleObjectProperty<>();
    private final ObjectProperty<@AbsColIndex Integer> currentColumns = new SimpleObjectProperty<>();

    // Package visible to let sidebars access it
    static final double rowHeight = 24;
    static final double defaultColumnWidth = 100;

    private final Map<@AbsColIndex Integer, Double> customisedColumnWidths = new HashMap<>();

    // null means the grid doesn't have focus:
    private final ObjectProperty<@Nullable CellSelection> selection = new SimpleObjectProperty<>(null);

    private final BooleanProperty atLeftProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atRightProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atTopProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty atBottomProperty = new SimpleBooleanProperty(false);
    
    // A sort of mutex to stop re-entrance to the updateSizeAndPositions() method:
    private boolean updatingSizeAndPositions = false;
    
    private final VirtualGridSupplierFloating supplierFloating = new VirtualGridSupplierFloating();
    private @Nullable GridAreaHighlight highlightedGridArea;
    private final StackPane stackPane;
    private final Pane activeOverlayPane;

    public static interface CreateTable
    {
        public void createTable(CellPosition cellPosition, Point2D suitablePoint, VirtualGrid virtualGrid);
    }
    
    @OnThread(Tag.FXPlatform)
    public VirtualGrid(@Nullable CreateTable createTable, int columnsToRight, int rowsToBottom, String... styleClasses)
    {
        this.columnsToRight = columnsToRight;
        this.rowsToBottom = rowsToBottom;
        currentKnownLastRowIncl.set(CellPosition.row(rowsToBottom));
        //FXUtility.addChangeListenerPlatformNN(currentKnownLastRowIncl, r -> {
        //    Log.debug("Current rows: " + r);
        //});
        currentColumns.set(CellPosition.col(columnsToRight));
        if (createTable != null)
            nodeSuppliers.add(new CreateTableButtonSupplier(createTable));
        nodeSuppliers.add(supplierFloating);
        this.hBar = new ScrollBar();
        hBar.setOrientation(Orientation.HORIZONTAL);
        hBar.setMin(0.0);
        hBar.setMax(1.0);
        hBar.setValue(0.0);
        hBar.valueProperty().addListener(new ChangeListener<Number>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Number> prop, Number oldScrollBarVal, Number newScrollBarVal)
            {
                if (!settingScrollBarVal)
                {
                    double delta = FXUtility.mouse(VirtualGrid.this).getMaxScrollX() * (newScrollBarVal.doubleValue() - oldScrollBarVal.doubleValue());
                    FXUtility.mouse(VirtualGrid.this).scrollGroup.requestScrollBy(-delta, 0);
                }
            }
        });
        this.vBar = new ScrollBar();
        vBar.setOrientation(Orientation.VERTICAL);
        vBar.setMin(0.0);
        vBar.setMax(1.0);
        vBar.setValue(0.0);
        vBar.valueProperty().addListener(new ChangeListener<Number>()
        {
            @Override
            @SuppressWarnings("nullness")
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Number> prop, Number oldScrollBarVal, Number newScrollBarVal)
            {
                if (!settingScrollBarVal)
                {
                    double delta = FXUtility.mouse(VirtualGrid.this).getMaxScrollY() * (newScrollBarVal.doubleValue() - oldScrollBarVal.doubleValue());
                    FXUtility.mouse(VirtualGrid.this).scrollGroup.requestScrollBy(0, -delta);
                }
            }
        });
        this.container = new Container();
        this.container.getStyleClass().addAll(styleClasses);
        this.activeOverlayPane = new Pane() {
            @Override
            protected void layoutChildren()
            {
            }

            @Override
            public void requestFocus()
            {
            }
        };
        this.activeOverlayPane.setMouseTransparent(true);
        this.activeOverlayPane.translateXProperty().bind(container.translateXProperty());
        this.activeOverlayPane.translateYProperty().bind(container.translateYProperty());
        this.stackPane = new StackPane(container, activeOverlayPane) {
            @Override
            public void requestFocus()
            {
            }
        };
        this.paneWithScrollBars = new BorderPane(stackPane, null, vBar, hBar, null);
        
        this.paneWithScrollBars.getStylesheets().add(FXUtility.getStylesheet("virtual-grid.css"));
        scrollGroup = new ScrollGroup(FXUtility.mouse(this)::scrollClampX, FXUtility.mouse(this)::scrollClampY);
/*

                scrollLayoutXBy, MAX_EXTRA_ROW_COLS, targetX -> {
            // Count column widths in that direction until we reach target:
            double curX;
            int startCol;
            if (targetX < 0)
            {
                // If it's negative, we're scrolling left, and we need to show extra
                // rows to the right until they scroll out of view.
                double w = 0;
                for (startCol = firstRenderColumnIndex; startCol < currentColumns.get(); startCol++)
                {
                    w += getColumnWidth(startCol);
                    if (w >= container.getWidth())
                    {
                        break;
                    }
                }
                curX = w + firstRenderColumnOffset - container.getWidth();
                int col;
                for (col = startCol + 1; curX < -targetX; col++)
                {
                    if (col >= currentColumns.get())
                        return currentColumns.get() - startCol;
                    curX += getColumnWidth(col);
                }
                // Will be 0 or positive:
                return col - startCol;
            }
            else
            {
                // Opposite: scrolling right, need extra rows left until scroll out of view:
                startCol = firstRenderColumnIndex;
                int col;
                curX = firstRenderColumnOffset;
                for (col = startCol - 1; curX > -targetX; col--)
                {
                    if (col < 0)
                        return -startCol;
                    curX -= getColumnWidth(col);
                }
                // Will be 0 or negative:
                return col - startCol;
            }
        }
                , FXUtility.mouse(this)::scrollLayoutYBy
                , y -> (int)(Math.signum(-y) * Math.ceil(Math.abs(y) / rowHeight))); */
        scrollGroup.add(FXUtility.mouse(this), ScrollLock.BOTH);
        container.translateXProperty().bind(scrollGroup.translateXProperty());
        container.translateYProperty().bind(scrollGroup.translateYProperty());
        container.addEventFilter(ScrollEvent.ANY, scrollEvent -> {
            scrollGroup.requestScroll(scrollEvent);
            scrollEvent.consume();
        });


        @Initialized @NonNull ObjectProperty<@Nullable CellSelection> selectionFinal = this.selection;
        RectangleOverlayItem selectionRectangleOverlayItem = new RectangleOverlayItem(ViewOrder.OVERLAY_ACTIVE)
        {
            private final BooleanExpression hasSelection = selectionFinal.isNotNull();
            
            @Override
            protected Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
            {
                CellSelection selection = selectionFinal.get();
                if (selection == null)
                    return Optional.empty();
                else
                    return Optional.of(Either.right(selection.getSelectionDisplayRectangle()));
            }

            @Override
            protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
            {
                r.getStyleClass().add("virt-grid-selection-overlay");
                r.visibleProperty().bind(hasSelection);
            }
        };
        supplierFloating.addItem(selectionRectangleOverlayItem);
        
        selection.addListener(new ChangeListener<@Nullable CellSelection>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends @Nullable CellSelection> prop, @Nullable CellSelection oldVal, @Nullable CellSelection s)
            {
                if (s != null)
                {
                    FXUtility.mouse(VirtualGrid.this).smoothScrollToEnsureVisible(s.positionToEnsureInView());
                }
                List<FXPlatformConsumer<VisibleBounds>> updaters = new ArrayList<>();
                for (Iterator<SelectionListener> iterator = FXUtility.mouse(VirtualGrid.this).selectionListeners.iterator(); iterator.hasNext(); )
                {
                    SelectionListener selectionListener = iterator.next();
                    @OnThread(Tag.FXPlatform) Pair<ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> outcome = selectionListener.selectionChanged(oldVal, s);
                    if (outcome.getFirst() == ListenerOutcome.REMOVE)
                        iterator.remove();
                    if (outcome.getSecond() != null)
                        updaters.add(outcome.getSecond());
                }
                VisibleBounds bounds = FXUtility.mouse(VirtualGrid.this).container.redoLayout();
                for (FXPlatformConsumer<VisibleBounds> updater : updaters)
                {
                    updater.consume(bounds);
                }
            }
        });

        //FXUtility.onceNotNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));
    }

    private double scrollClampX(double idealScrollBy)
    {
        if (idealScrollBy > 0)
        {
            // Scrolling the document rightwards, so that the view is moving towards the left of the document
            // Scrolling the offset positive, which means index numbers come down
            
            // We can scroll to the left edge of the current item, for sure.
            // Note that maxScroll will be positive.
            double maxScroll = -logicalScrollColumnOffset;
            // Can we also scroll into any other items?
            for (int index = logicalScrollColumnIndex - 1; index >= 0; index--)
            {
                maxScroll += getColumnWidth(index);
                // Short-circuit: if we already showed we can scroll as far as we want to, stop:
                if (maxScroll >= idealScrollBy)
                    return idealScrollBy;
            }
            // Math.min gets us the smaller positive number:
            return Math.min(maxScroll, idealScrollBy);
        }
        else if (idealScrollBy < 0)
        {
            // Scrolling the document leftwards, so the view is moving towards the right of the document.
            // Scrolling the offset negative, so index numbers go oup.
            
            // We measure distance to the very right hand edge of all columns, then subtract
            // our own width to work out where the left edge can go:
            
            // The right edge is at least scrolling to right of current item:
            double distToRightEdge = getColumnWidth(logicalScrollColumnIndex) + logicalScrollColumnOffset;

            @AbsColIndex int curColumns = currentColumns.get();
            double paneWidth = container.getWidth();
            for (int index = logicalScrollColumnIndex + 1; index < curColumns; index++)
            {
                distToRightEdge += getColumnWidth(index);
                // Short-circuit if we already know we can scroll far enough:
                if (distToRightEdge - paneWidth > -idealScrollBy)
                    return idealScrollBy;
            }
            // Math.max gets us the negative number closest to zero:
            return Math.max(-(distToRightEdge - paneWidth), idealScrollBy);
        }
        return idealScrollBy;
    }

    /**
     * What's the most we can scroll by in the given direction?  If less than param, return
     * the clamped value.
     */
    private double scrollClampY(double idealScrollBy)
    {
        if (idealScrollBy > 0)
        {
            // Scrolling the document downwards, so that the view is moving towards the top of the document
            // Scrolling the offset positive, which means index numbers come down
            
            // Furthest we could scroll is all the way to the top:
            double maxScroll = logicalScrollRowIndex * rowHeight - logicalScrollRowOffset;
            //Log.debug("Row #" + logicalScrollRowIndex  + " at " + logicalScrollRowOffset + " Max: " + maxScroll);
            
            // Math.min gets us the smallest positive number:
            return Math.min(maxScroll, idealScrollBy);
        }
        else if (idealScrollBy < 0)
        {
            // The furthest we scroll is until the last row rests at the bottom of the window:
            double lastScrollPos = (currentKnownLastRowIncl.get() + 1) * rowHeight - container.getHeight();
            double maxScroll = -(lastScrollPos - (logicalScrollRowIndex * rowHeight - logicalScrollRowOffset));
            // Don't start scrolling backwards, though.  (Shouldn't, but sanity check):
            if (maxScroll > 0)
                maxScroll = 0;
            // We are both negative, so Math.max gets us the least-negative item:
            return Math.max(maxScroll, idealScrollBy);
        }
        // Must be zero; no need to clamp:
        return idealScrollBy;
    }

    private @Nullable CellPosition getCellPositionAt(double x, double y)
    {
        @AbsColIndex int colIndex;
        x -= logicalScrollColumnOffset;
        for (colIndex = logicalScrollColumnIndex; colIndex < currentColumns.get(); colIndex++)
        {
            x -= getColumnWidth(colIndex);
            if (x < 0.0)
            {
                break;
            }
        }
        if (x > 0.0)
            return null;
        y -= logicalScrollRowOffset;
        @SuppressWarnings("units")
        @AbsRowIndex int rowIndex = (int) Math.floor(y / rowHeight) + logicalScrollRowIndex;
        if (rowIndex >= getLastSelectableRowGlobal())
            return null;
        return new CellPosition(rowIndex, colIndex);
    }

    @Override
    public boolean scrollXLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter)
    {
        // We basically do two scrolls.  One to scroll from existing logical position by the given number of pixels,
        // and store that in logical position.  Then a second to scroll from new logical position by
        // the extra number of pixels before, to get the first render.  Finally, we calculate how many
        // items we need to render in the pane overall.
        int curCol = logicalScrollColumnIndex;
        double curOffset = logicalScrollColumnOffset;
        @AbsColIndex int totalColumns = currentColumns.get();
        
        for (int miniScroll = 0; miniScroll < 2; miniScroll++)
        {
            double remainingScroll = miniScroll == 0 ? scrollBy : (extraPixelsToShowBefore = scrollClampX(extraPixelsToShowBefore));

            // Remember here how the scrolling offsets work.  If you have column 5 with content ABCDE, and you
            // have a curOffset of minus 2, then we render like this:
            //   |
            // AB CDE
            //   |
            // If you move to a curOffset of minus 4 then you get:
            //     |
            // ABCD E
            //     |
            // That is, making curOffset more negative scrolls *right*.  So if your remainingScroll is negative
            // (meaning a request to scroll left), you should actually make curOffset more positive
            
            // Can we just do the scroll within the given column?
            double startColumnWidth = getColumnWidth(curCol);
            if (remainingScroll == 0.0 || (-startColumnWidth < curOffset + remainingScroll && curOffset + remainingScroll <= 0))
            {
                curOffset += remainingScroll;
            }
            else
            {
                // The first thing we do is scroll in the appropriate direction so that we are at the left edge of a column,
                // because that makes the loop afterwards a hell of a lot simpler:
                if (remainingScroll > 0)
                {
                    // We are scrolling the offset positive, and items are decreasing in index.
                    // We must go to the right edge by putting the negative offset to zero, and absorbing that from remainingScroll:
                    remainingScroll += curOffset;
                    // Now we are already on to the next column in terms of inspecting the widths:
                    curCol -= 1;
                    while (curCol >= 0 && curCol < totalColumns)
                    {
                        double curColumnWidth = getColumnWidth(curCol);
                        remainingScroll -= curColumnWidth;
                        // Is our stopping position in this column?
                        if (remainingScroll <= 0)
                        {
                            break;
                        }
                        curCol -= 1;
                    }
                    // Don't scroll beyond end:
                    if (curCol < 0)
                    {
                        curCol = 0;
                        remainingScroll = 0;
                    }
                }
                else
                {
                    // We are scrolling the offset negative, and items are increasing in index;

                    // First get to the left edge by moving the remainder of the column width: 
                    remainingScroll += startColumnWidth + curOffset;
                    // That already involves moving to the next item:
                    curCol += 1;
                    // Now we head through the columns until we've used up all our scroll.
                    // We'll know we're finished when the remainingScroll is between -curColumnWidth, and zero
                    while (curCol >= 0 && curCol < totalColumns)
                    {
                        double curColumnWidth = getColumnWidth(curCol);
                        if (-curColumnWidth <= remainingScroll && remainingScroll <= 0)
                        {
                            break;
                        }
                        remainingScroll += curColumnWidth;
                        curCol += 1;
                    }
                }
                curOffset = remainingScroll;
            }
            
            if (miniScroll == 0)
            {
                logicalScrollColumnIndex = CellPosition.col(curCol);
                logicalScrollColumnOffset = curOffset;

                // Can we do the rest via a simple layout alteration?
                if (-this.extraRenderXPixelsBefore <= scrollBy - extraPixelsToShowBefore
                    && scrollBy + extraPixelsToShowAfter <= this.extraRenderXPixelsAfter)
                {
                    // Just move everything by that amount without doing a full layout:
                    for (Node node : container.getChildrenUnmodifiable())
                    {
                        node.setLayoutX(node.getLayoutX() + scrollBy);
                    }
                    for (Node node : activeOverlayPane.getChildren())
                    {
                        node.setLayoutX(node.getLayoutX() + scrollBy);
                    }

                    this.extraRenderXPixelsBefore -= scrollBy;
                    this.extraRenderXPixelsAfter += scrollBy;
                    
                    updateHBar();

                    return false;
                }
                else
                {
                    // We didn't have enough extra pixels or we are laying out anyway, so go up to max:
                    extraPixelsToShowAfter = MAX_EXTRA_X_PIXELS;
                    extraPixelsToShowBefore = MAX_EXTRA_X_PIXELS;
                }
            }
            else
            {
                firstRenderColumnOffset = curOffset;
                firstRenderColumnIndex = CellPosition.col(curCol);
            }
        }
        
        this.extraRenderXPixelsBefore = extraPixelsToShowBefore;
        this.extraRenderXPixelsAfter = extraPixelsToShowAfter;
        
        updateHBar();

        boolean atLeft = firstRenderColumnIndex == 0 && firstRenderColumnOffset >= -5;
        //FXUtility.setPseudoclass(glass, "left-shadow", !atLeft);

        return true;
    }
    
    @Override
    public boolean scrollYLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter)
    {
        // First scroll to the right logical position:
        class ScrollResult
        {
            private @AbsRowIndex int row;
            private double offset;
            
            // Slight abuse of a constructor, but it's only local.  Calculate new scroll result:
            ScrollResult(@AbsRowIndex int existingIndex, double existingOffset, double scrollBy)
            {
                if (0 >= existingOffset + scrollBy && existingOffset + scrollBy >= -rowHeight)
                {
                    // Can do it without moving row even:
                    row = existingIndex;
                    offset = existingOffset + scrollBy;
                }
                else
                {
                    if (scrollBy <= 0.0)
                    {
                        // We will need to scroll by rowHeight - (-existingOffset) to get to the next row boundary,
                        // then the rest of scrollBy.  newRows is the positive version:
                        int newRows = 1 + (int) Math.floor((-scrollBy - (rowHeight + existingOffset)) / rowHeight);
                        row = existingIndex + CellPosition.row(newRows);
                        offset = existingOffset + (scrollBy + newRows * rowHeight);
                    }
                    else
                    {
                        // We need to scroll by -existingOffset to get to next row boundary, then rest of scrollBy
                        int newRows = 1 + (int) Math.floor((scrollBy + existingOffset) / rowHeight);
                        row = existingIndex - CellPosition.row(newRows);
                        offset = existingOffset + (scrollBy - newRows * rowHeight);
                        
                        // Clamp:
                        if (row < 0)
                        {
                            row = CellPosition.row(0);
                            offset = 0;
                        }
                    }
                }

                // Can be small positive number due to floating point error, so just mask that:
                offset = Math.min(0.0, offset);
            }
        }

        if (-this.extraRenderYPixelsBefore <= -scrollBy - extraPixelsToShowBefore
            && -scrollBy + extraPixelsToShowAfter <= this.extraRenderYPixelsAfter)
        {
            //Log.debug("Slide scroll; wanted to scroll by " + scrollBy + " (+" + extraPixelsToShowAfter + ") and pixels after was " + this.extraRenderYPixelsAfter);
            // Just move everything by that amount without doing a full layout:
            for (Node node : container.getChildrenUnmodifiable())
            {
                node.setLayoutY(node.getLayoutY() + scrollBy);
            }
            for (Node node : activeOverlayPane.getChildren())
            {
                node.setLayoutY(node.getLayoutY() + scrollBy);
            }

            this.extraRenderYPixelsBefore -= scrollBy;
            this.extraRenderYPixelsAfter += scrollBy;
            
            ScrollResult logicalPos = new ScrollResult(logicalScrollRowIndex, logicalScrollRowOffset, scrollBy);
            logicalScrollRowIndex = logicalPos.row;
            logicalScrollRowOffset = logicalPos.offset;
            
            updateVBar();
            
            return false;
        }
        // We didn't have enough extra pixels or we are laying out anyway, so go up to max:
        extraPixelsToShowAfter = MAX_EXTRA_Y_PIXELS;
        extraPixelsToShowBefore = MAX_EXTRA_Y_PIXELS;
        
        ScrollResult logicalPos = new ScrollResult(logicalScrollRowIndex, logicalScrollRowOffset, scrollBy);
        int oldRowIndex = logicalScrollRowIndex;
        logicalScrollRowIndex = logicalPos.row;
        logicalScrollRowOffset = logicalPos.offset;
        extraPixelsToShowBefore = scrollClampY(extraPixelsToShowBefore);
        ScrollResult renderPos = new ScrollResult(logicalScrollRowIndex, logicalScrollRowOffset, extraPixelsToShowBefore);
        firstRenderRowIndex = renderPos.row;
        firstRenderRowOffset = renderPos.offset;
        this.extraRenderYPixelsBefore = extraPixelsToShowBefore;
        this.extraRenderYPixelsAfter = extraPixelsToShowAfter;
        
        updateVBar();
        
        // May need to adjust our visible row count if a new row is potentially visible:
        if (logicalScrollRowIndex + ((container.getHeight() + extraRenderYPixelsAfter) / rowHeight) > currentKnownLastRowIncl.get())
        {
            // This will call redoLayout so we won't need to call it again:
            //Log.debug("Potentially adjusting row count");
            updateSizeAndPositions();
            return false;
        }
        else
        {
            // No need to update rows, but do need to redo layout:
            return true;
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) void redoLayoutAfterScroll()
    {
        container.redoLayout();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void updateClip()
    {
        if (container.clip != null)
        {
            container.clip.setX(-container.getTranslateX());
            container.clip.setY(-container.getTranslateY());
            container.clip.setWidth(container.getWidth());
            container.clip.setHeight(container.getHeight());
            //scrollGroup.updateClip();
        }
    }

    /**
     * Adds the column widths for any column index C where startColIndexIncl <= C < endColIndexExcl
     * If startColIndexIncl >= endColIndexExcl, zero will be returned.
     */
    private double sumColumnWidths(@AbsColIndex int startColIndexIncl, @AbsColIndex int endColIndexExcl)
    {
        double total = 0;
        for (int i = startColIndexIncl; i < endColIndexExcl; i++)
        {
            total += getColumnWidth(i);
        }
        return total;
    }

    private double getColumnWidth(@UnknownInitialization(Object.class) VirtualGrid this, int columnIndex)
    {
        return customisedColumnWidths.getOrDefault(columnIndex, defaultColumnWidth);
    }

    private @AbsRowIndex int getLastSelectableRowGlobal()
    {
        return CellPosition.row(currentKnownLastRowIncl.get());
    }

    private double getMaxScrollX()
    {
        return Math.max(0, sumColumnWidths(CellPosition.col(0), currentColumns.get())  - container.getWidth());
    }

    private double getMaxScrollY()
    {
        return Math.max(0, (getLastSelectableRowGlobal() - 1) * rowHeight - container.getHeight());
    }

    private void updateVBar()
    {
        settingScrollBarVal = true;
        double maxScrollY = getMaxScrollY();
        double currentScrollY = getCurrentScrollY(null);
        vBar.setValue(maxScrollY < 1.0 ? 0.0 : (currentScrollY / maxScrollY));
        vBar.setVisibleAmount(maxScrollY < 1.0 ? 1.0 : (container.getHeight() / (maxScrollY + container.getHeight())));
        vBar.setMax(maxScrollY < 1.0 ? 0.0 : 1.0);
        atTopProperty.set(currentScrollY < 1.0);
        atBottomProperty.set(currentScrollY >= maxScrollY - 1.0);

        settingScrollBarVal = false;
    }

    private void updateHBar()
    {
        settingScrollBarVal = true;
        double maxScrollX = getMaxScrollX();
        double currentScrollX = getCurrentScrollX(null);
        hBar.setValue(maxScrollX < 1.0 ? 0.0 : (currentScrollX / maxScrollX));
        hBar.setVisibleAmount(maxScrollX < 1.0 ? 1.0 : (container.getWidth() / (maxScrollX + container.getWidth())));
        hBar.setMax(maxScrollX < 1.0 ? 0.0 : 1.0);
        atLeftProperty.set(currentScrollX < 1.0);
        atRightProperty.set(currentScrollX >= maxScrollX - 1.0);
        settingScrollBarVal = false;
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollY(@Nullable Pair<@AbsRowIndex Integer, Double> pos)
    {
        return (pos == null ? logicalScrollRowIndex : pos.getFirst()) * rowHeight - (pos == null ? logicalScrollRowOffset : pos.getSecond());
    }

    // If param is non-null, overrides our own data
    private double getCurrentScrollX(@Nullable Pair<@AbsColIndex Integer, Double> pos)
    {
        return sumColumnWidths(CellPosition.col(0), pos == null ? logicalScrollColumnIndex : pos.getFirst()) - (pos == null ? logicalScrollColumnOffset : pos.getSecond());
    }

    public void select(@Nullable CellSelection cellSelection)
    {
        /*
        visibleCells.forEach((visPos, visCell) -> {
            SelectionStatus status = cellSelection == null ? SelectionStatus.UNSELECTED : cellSelection.selectionStatus(visPos);
            FXUtility.setPseudoclass(visCell, "primary-selected-cell", status == SelectionStatus.PRIMARY_SELECTION);
            FXUtility.setPseudoclass(visCell, "secondary-selected-cell", status == SelectionStatus.SECONDARY_SELECTION);
        });
        */
        
        selection.set(cellSelection);
        if (cellSelection != null)
            container.requestFocus();
    }

    public boolean selectionIncludes(@UnknownInitialization(GridArea.class) GridArea gridArea)
    {
        @Nullable CellSelection curSel = selection.get();
        return curSel != null ? curSel.includes(gridArea) : false;
    }


    // Selects cell so that you can navigate around with keyboard
    public void findAndSelect(@Nullable Either<CellPosition, CellSelection> target)
    {
        select(target == null ? null : target.<@Nullable CellSelection>either(
            p -> {
                // See if the position is in a grid area:
                for (GridArea gridArea : gridAreas)
                {
                    if (gridArea.contains(p))
                    {
                        return gridArea.getSelectionForSingleCell(p);
                    }
                }
                return new EmptyCellSelection(p);
            },
            s -> s
        ));
    }

    public Region getNode()
    {
        return paneWithScrollBars;
    }

    public void positionOrAreaChanged()
    {
        // This calls container.redoLayout():
        updateSizeAndPositions();
    }

    public int _test_getFirstLogicalVisibleRowIncl()
    {
        // This is first row with middle visible, so check for that:
        if (firstRenderRowOffset < -rowHeight / 2.0)
            return firstRenderRowIndex + 1;
        else
            return firstRenderRowIndex;
    }

    public int _test_getLastLogicalVisibleRowExcl()
    {
        int numVisible = (int)Math.round((double)container.getHeight() / rowHeight);
        return firstRenderRowIndex + numVisible;
    }


    public void addNodeSupplier(VirtualGridSupplier<?> cellSupplier)
    {
        // Keep the floating supplier last in the list:
        nodeSuppliers.add(nodeSuppliers.size() - 1, cellSupplier);
        container.redoLayout();
    }

    public ScrollGroup getScrollGroup()
    {
        return scrollGroup;
    }

    public void onNextSelectionChange(FXPlatformConsumer<@Nullable CellSelection> onChange)
    {
        selectionListeners.add((old, s) -> {
            onChange.consume(s);
            return new Pair<>(ListenerOutcome.REMOVE, null);
        });
    }

    public Bounds _test_getRectangleBoundsScreen(RectangleBounds rectangleBounds)
    {
        return container.localToScreen(getRectangleBoundsInContainer(rectangleBounds));
    }

    /**
     * Gets the bounds of the rectangle relative to the grid's current viewport.
     * May be off-screen.
     * @param rectangleBounds
     * @return
     */
    private BoundingBox getRectangleBoundsInContainer(RectangleBounds rectangleBounds)
    {
        double adjustX = getCurrentScrollX(null);
        double adjustY = getCurrentScrollY(null);
        // TODO could be cleverer here to avoid summing column widths:
        double x = sumColumnWidths(CellPosition.col(0), rectangleBounds.topLeftIncl.columnIndex);
        double y = rectangleBounds.topLeftIncl.rowIndex * rowHeight;
        return new BoundingBox(
            x - adjustX, 
            y - adjustY, 
            sumColumnWidths(rectangleBounds.topLeftIncl.columnIndex, rectangleBounds.bottomRightIncl.columnIndex + CellPosition.col(1)),
            rowHeight * (rectangleBounds.bottomRightIncl.rowIndex + 1 - rectangleBounds.topLeftIncl.rowIndex)
        );
    }

    public final @Pure VirtualGridSupplierFloating getFloatingSupplier()
    {
        return supplierFloating;
    }

    public final void addSelectionListener(SelectionListener selectionListener)
    {
        selectionListeners.add(selectionListener);
    }

    public final void removeSelectionListener(SelectionListener selectionListener)
    {
        selectionListeners.remove(selectionListener);
    }

    public double _test_getScrollXPos()
    {
        return getCurrentScrollX(null);
    }

    public double _test_getScrollYPos()
    {
        return getCurrentScrollY(null);
    }
    
    // List of pairs
    // Index 0 is its (negative) limit, index 1 is actual item.
    // It should be the case that r[i][0] <= r[i][1] <= 0
    public double[][] _test_getOffsetsAndLimits()
    {
        return new double[][] {
            new double[] {-getColumnWidth(firstRenderColumnIndex), firstRenderColumnOffset},
            new double[] {-rowHeight, firstRenderRowOffset},
            new double[] {-getColumnWidth(logicalScrollColumnIndex), logicalScrollColumnOffset},
            new double[] {-rowHeight, logicalScrollRowOffset}
        };
    }

    // List of pairs
    // Index 0 is actual item, index 1 is limit (excl)
    // It should be the case that r[i][0] <= r[i][1]
    // As an added item, r[0][0] should be <= r[2][0] and r[1][0] <= r[3][0]
    public int[][] _test_getIndexesAndLimits()
    {
        return new int[][] {
            new int[] {firstRenderColumnIndex, currentColumns.get()},
            new int[] {firstRenderRowIndex, currentKnownLastRowIncl.get()},
            new int[] {logicalScrollColumnIndex, currentColumns.get()},
            new int[] {logicalScrollRowIndex, currentKnownLastRowIncl.get()}
        };
    }
    

    public void _test_setColumnWidth(int columnIndex, double width)
    {
        customisedColumnWidths.put(CellPosition.col(columnIndex), width);
    }

    public Optional<CellSelection> _test_getSelection()
    {
        return Optional.ofNullable(selection.get());
    }

    public void stopHighlightingGridArea()
    {
        if (highlightedGridArea != null)
        {
            supplierFloating.removeItem(highlightedGridArea);
            highlightedGridArea = null;
            container.redoLayout();
        }
    }
    
    public @Nullable GridArea highlightGridAreaAtScreenPos(Point2D screenPos, Predicate<GridArea> validPick, FXPlatformConsumer<@Nullable Cursor> setCursor)
    {
        if (highlightedGridArea == null)
            highlightedGridArea = supplierFloating.addItem(new GridAreaHighlight());
        return highlightedGridArea.highlightAtScreenPos(screenPos, validPick, setCursor);
    }

    @OnThread(Tag.FXPlatform)
    private class Container extends Region implements ContainerChildren
    {
        private final Rectangle clip;
        private List<ViewOrder> viewOrders = new ArrayList<>();

        public Container()
        {
            getStyleClass().add("virt-grid");
            // Need this for when JavaFX looks for a default focus target:
            setFocusTraversable(true);

            clip = new Rectangle();
            //setClip(clip);

            FXUtility.addChangeListenerPlatformNN(widthProperty(), w -> {updateHBar(); redoLayout();});
            FXUtility.addChangeListenerPlatformNN(heightProperty(), h -> {updateVBar(); redoLayout();});

            /* 
             * The logic for mouse events is as follows.
             * 
             * - If the cell is being edited already, we let add mouse events pass through unfiltered
             *   to the underlying component.
             * - Else if the cell is currently a single cell selection, a single click
             *   will be passed to the cell with an instruction to start editing at that location
             *   (e.g. by setting the caret location).
             * - Else if the cell is not currently a single cell selection, how many clicks?
             *   - Double click: start editing at given point
             *   - Single click: become a single cell selection
             * 
             * To do the first two steps we talk to the node suppliers as they know which cell is at whic
             * location.  For the third one we find the relevant grid area.
             */
            
            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp @UnknownUnits MouseEvent> clickHandler = mouseEvent -> {

                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());
                Point2D screenPos = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
                
                if (cellPosition != null)
                {
                    @NonNull CellPosition cellPositionFinal = cellPosition;
                    boolean clickable = nodeSuppliers.stream().anyMatch(g -> {
                        ItemState itemState = g.getItemState(cellPositionFinal, screenPos);
                        return itemState != null && itemState != ItemState.NOT_CLICKABLE;
                    });
                    if (clickable)
                        return; // Don't capture the events
                    
                    // Not editing, is the cell currently part of a single cell selection:
                    @Nullable CellSelection curSel = VirtualGrid.this.selection.get();
                    boolean selectedByItself = curSel != null && curSel.isExactly(cellPosition);
                    
                    if (selectedByItself || mouseEvent.getClickCount() == 2)
                    {
                        for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
                        {
                            nodeSupplier.startEditing(screenPos, cellPositionFinal);
                        }
                    }
                    else
                    {
                        boolean foundInGrid = false;
                        // Become a single cell selection:
                        for (GridArea gridArea : gridAreas)
                        {
                            @Nullable CellSelection singleCellSelection = gridArea.getSelectionForSingleCell(cellPosition);
                            if (singleCellSelection != null)
                            {
                                select(singleCellSelection);
                                foundInGrid = true;
                                break;
                            }
                        }

                        if (!foundInGrid)
                        {
                            // Belongs to no-one; we must handle it:
                            select(new EmptyCellSelection(cellPosition));
                            FXUtility.mouse(this).requestFocus();
                        }
                    }
                    
                    mouseEvent.consume();
                    redoLayout();
                }
            };
            addEventFilter(MouseEvent.MOUSE_CLICKED, clickHandler);

            EventHandler<? super @UnknownIfRecorded @UnknownKeyFor @UnknownIfValue @UnknownIfUserIndex @UnknownIfHelp @UnknownUnits MouseEvent> capture = mouseEvent -> {
                @Nullable CellPosition cellPosition = getCellPositionAt(mouseEvent.getX(), mouseEvent.getY());

                if (cellPosition != null)
                {
                    // We want to capture the events to prevent clicks reaching the underlying cell,
                    // if the cell is not currently editing
                    @NonNull CellPosition cellPositionFinal = cellPosition;
                    Point2D screenPos = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
                    boolean clickable = nodeSuppliers.stream().anyMatch(g -> {
                        ItemState itemState = g.getItemState(cellPositionFinal, screenPos);
                        return itemState != null && itemState != ItemState.NOT_CLICKABLE;
                    });
                    // If it's the end of a drag, don't steal the event because we
                    // are looking at drag end location, not drag start, so our calculations
                    // are invalid:
                    if (!clickable && (mouseEvent.getEventType() != MouseEvent.MOUSE_RELEASED || mouseEvent.isStillSincePress()))
                        mouseEvent.consume();
                }
            };
            addEventFilter(MouseEvent.MOUSE_PRESSED, capture);
            addEventFilter(MouseEvent.MOUSE_RELEASED, capture);
            //addEventFilter(MouseEvent.MOUSE_DRAGGED, capture);
            addEventFilter(MouseEvent.DRAG_DETECTED, capture);
            
            FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                if (!focused)
                {
                    select(null);
                    redoLayout();
                }
            });

            Nodes.addInputMap(FXUtility.keyboard(this), InputMap.sequence(
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.HOME, KeyCombination.CONTROL_DOWN), e -> {
                        for (GridArea gridArea : gridAreas)
                        {
                            @Nullable CellSelection possibleSel = gridArea.getSelectionForSingleCell(CellPosition.ORIGIN);
                            if (possibleSel != null)
                                select(possibleSel);
                        }
                        select(new EmptyCellSelection(CellPosition.ORIGIN));
                        e.consume();
                    }),
                    bindS(KeyCode.HOME, (shift, c) -> c.home(shift)),
                    bindS(KeyCode.END, (shift, c) -> c.end(shift)),
                    bindS(KeyCode.UP, (shift, c) -> c.move(shift, -1, 0)),
                    bindS(KeyCode.DOWN, (shift, c) -> c.move(shift, 1, 0)),
                    bindS(KeyCode.LEFT, (shift, c) -> c.move(shift, 0, -1)),
                    bindS(KeyCode.RIGHT, (shift, c) -> c.move(shift, 0, 1)),
                    bindS(KeyCode.PAGE_UP, (shift, c) -> c.move(shift, -((int)Math.floor(c.getHeight() / rowHeight) - 1), 0)),
                    bindS(KeyCode.PAGE_DOWN, (shift, c) -> c.move(shift, (int)Math.floor(c.getHeight() / rowHeight) - 1, 0)),
                    InputMap.<Event, KeyEvent>consume(EventPattern.<Event, KeyEvent>anyOf(EventPattern.keyPressed(KeyCode.F11), EventPattern.keyPressed(KeyCode.C, KeyCombination.SHORTCUT_DOWN)), e -> {
                        @Nullable CellSelection focusedCellPosition = selection.get();
                        if (focusedCellPosition != null)
                            focusedCellPosition.doCopy();
                        e.consume();
                    }),
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
                        @Nullable CellSelection sel = selection.get();
                        if (sel != null)
                        {
                            activateCell(sel.getActivateTarget());
                        }
                        e.consume();
                    }),
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.SPACE), e -> {
                        @Nullable CellSelection sel = selection.get();
                        if (sel != null)
                        {
                            activateCell(sel.getActivateTarget());
                        }
                        e.consume();
                    })
            ));
        }

        @Override
        @OnThread(Tag.FX)
        protected void layoutChildren()
        {
            // Note: it is important to not call super.layoutChildren() here, because Parent will
            // resize its children to their preferred size, which we do not want.
            
            // We do nothing directly here because redoLayout will have done it.  We don't call redoLayout during
            // the actual layout pass, because redoLayout adds and removes children, and JavaFX goes a bit loopy
            // if you do this during its layout pass.
        }

        /**
         * Like bind, but binds with and without shift held, passing a boolean for shift state to the lambda
         * @param keyCode
         * @param action
         * @return
         */
        private InputMap<KeyEvent> bindS(@UnknownInitialization(Region.class) Container this, KeyCode keyCode, FXPlatformBiConsumer<Boolean, Container> action)
        {
            return InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(keyCode, KeyCombination.SHIFT_ANY), e -> {
                action.consume(e.isShiftDown(), FXUtility.keyboard(this));
                e.consume();
            });
        }

        private void move(boolean extendSelection, int rows, int columns)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                findAndSelect(focusedCellPos.move(extendSelection, rows, columns));
            }
        }

        private void home(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atHome(extendSelection));
            }
        }

        private void end(boolean extendSelection)
        {
            @Nullable CellSelection focusedCellPos = selection.get();
            if (focusedCellPos != null)
            {
                select(focusedCellPos.atEnd(extendSelection));
            }
        }

        // Note: the returned bounds are only valid until the next scroll or redoLayout
        // Use immediately and then forget them!
        private VisibleBounds redoLayout(@UnknownInitialization(Region.class) Container this)
        {
            VisibleBounds bounds = getVisibleBoundDetails();

            for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
            {
                nodeSupplier.layoutItems(FXUtility.mouse(this), bounds);
            }
            //Log.debug("Children: " + getChildren().size());
            
            updateClip();
            requestLayout();
            activeOverlayPane.requestLayout();
            return bounds;
        }

        private VisibleBounds getVisibleBoundDetails(@UnknownInitialization(Region.class) Container this)
        {
            // We are starting at firstRenderRowIndex, so already taken into account extraRenderYPixelsBefore to find starting pos:
            int newNumVisibleRows = Math.min(currentKnownLastRowIncl.get() - firstRenderRowIndex + 1, (int)Math.ceil((extraRenderYPixelsBefore + getHeight() + extraRenderYPixelsAfter) / rowHeight));
            int newNumVisibleCols = 0;
            // We are starting at firstRenderColumnIndex, so already taken into account extraRenderXPixelsBefore to find starting pos:
            double x = firstRenderColumnOffset;
            for (int column = firstRenderColumnIndex; x < extraRenderXPixelsBefore + getWidth() + extraRenderXPixelsAfter && column < currentColumns.get(); column++)
            {
                newNumVisibleCols += 1;
                x += getColumnWidth(column);
            }
            final int renderRowCount = newNumVisibleRows;
            final int renderColumnCount = newNumVisibleCols;            

            //Log.debug("Rows: " + firstDisplayRow + " to " + (lastDisplayRowExcl - 1) + " incl offset by: " + firstRenderRowOffset);

            // This includes extra rows needed for smooth scrolling:
            return new VisibleBounds(firstRenderRowIndex, firstRenderRowIndex + CellPosition.row(renderRowCount - 1), firstRenderColumnIndex, firstRenderColumnIndex + CellPosition.col(renderColumnCount - 1))
            {
                @Override
                public double getXCoord(@AbsColIndex int itemIndex)
                {
                    return -extraRenderXPixelsBefore + firstRenderColumnOffset + (firstColumnIncl <= itemIndex ? sumColumnWidths(firstColumnIncl, itemIndex) : -sumColumnWidths(itemIndex, firstColumnIncl));
                }

                @Override
                public double getYCoord(@AbsRowIndex int itemIndex)
                {
                    return -extraRenderYPixelsBefore + firstRenderRowOffset + rowHeight * (itemIndex - firstRowIncl);
                }

                // I'm not sure this can ever return empty any more, after the clamping logic got added on 30/3/2018
                @Override
                @OnThread(Tag.FXPlatform)
                public Optional<CellPosition> getNearestTopLeftToScreenPos(Point2D screenPos, HPos horizBias, VPos verticalBias)
                {
                    Point2D localCoord = container.screenToLocal(screenPos);
                    double x = firstRenderColumnOffset;
                    // Math.round will find us the nearest row:
                    double rowFloat = (localCoord.getY() - (firstRenderRowOffset)) / rowHeight;
                    // We add a little tolerance so that if you go a few pixels beyond border, it doesn't
                    // feel like a hard snap.
                    switch (verticalBias)
                    {
                        case TOP:
                            rowFloat = Math.floor(rowFloat + 0.2);
                            break;
                        case CENTER:
                            rowFloat = Math.round(rowFloat);
                            break;
                        case BOTTOM:
                            rowFloat = Math.ceil(rowFloat - 0.2);
                            break;
                    }
                    rowFloat = Utility.clampIncl(0, rowFloat, this.lastRowIncl - this.firstRowIncl + 1);
                    @AbsRowIndex int row = CellPosition.row((int)rowFloat) + this.firstRowIncl;
                    
                    if (firstRowIncl <= row && row <= lastRowIncl)
                    {
                        if (localCoord.getX() < x)
                            return Optional.of(new CellPosition(row, firstRenderColumnIndex));
                        
                        for (@AbsColIndex int i = firstRenderColumnIndex; i <= lastColumnIncl; i++)
                        {
                            double nextX = x + getColumnWidth(i);
                            if (x <= localCoord.getX() && localCoord.getX() < nextX)
                            {
                                @AbsColIndex int column = i;
                                switch (horizBias)
                                {
                                    case LEFT:
                                        column = i;
                                        break;
                                    case CENTER:
                                        double colFloat = Math.abs(localCoord.getX() - x) / Math.abs(nextX - x);
                                        column = colFloat < 0.5 ? i : i + CellPosition.col(1);
                                        break;
                                    case RIGHT:
                                        column = i + CellPosition.col(1);
                                        break;
                                }
                                return Optional.of(new CellPosition(row, column));
                            }
                            x = nextX;
                        }

                        return Optional.of(new CellPosition(row, lastColumnIncl));
                    }
                    return Optional.empty();
                }

                @Override
                public Point2D screenToLayout(Point2D screenPos)
                {
                    Point2D local = container.screenToLocal(screenPos);
                    return local;
                }
            };
        }

        @Override
        public Pair<DoubleExpression, DoubleExpression> add(Node node, ViewOrder viewOrder)
        {
            if (viewOrder == ViewOrder.OVERLAY_ACTIVE)
            {
                activeOverlayPane.getChildren().add(node);
            }
            else
            {
                // Need to insert at right place:
                // Children are kept sorted by view order:
                int insertionIndex = 0;
                while (insertionIndex < viewOrders.size() && viewOrders.get(insertionIndex).ordinal() < viewOrder.ordinal())
                    insertionIndex += 1;
                getChildren().add(insertionIndex, node);
                viewOrders.add(insertionIndex, viewOrder);
            }
            return new Pair<>(translateXProperty(), translateYProperty());
        }

        @Override
        public void remove(Node node)
        {
            if (!activeOverlayPane.getChildren().remove(node))
            {
                int index = getChildren().indexOf(node);
                getChildren().remove(index);
                viewOrders.remove(index);
            }
        }
    }

    private void updateSizeAndPositions()
    {
        if (updatingSizeAndPositions)
            return;
        updatingSizeAndPositions = true;
        
        // Three things to do:
        //   - Get each grid area to update its known size (which may involve calling us back)
        //   - Check for overlaps between tables, and reshuffle if needed
        //   - Update our known overall grid size

        // It's okay to ask row sizes beforehand because reshuffling only moves sideways.
        // If in future we also reshuffle down, this code needs rewriting:
        @SuppressWarnings("units")
        @AbsRowIndex int maxExtra = MAX_EXTRA_ROW_COLS;
        @AbsRowIndex int currentLastVisibleRow = CellPosition.row((int)(container.getHeight() / rowHeight)) + logicalScrollRowIndex;
        List<@AbsRowIndex Integer> lastRows = Utility.<GridArea, @AbsRowIndex Integer>mapList(gridAreas, gridArea -> gridArea.getAndUpdateBottomRow(currentLastVisibleRow + maxExtra, this::updateSizeAndPositions));
                
        // The plan to fix overlaps: we go from the left-most column across to
        // the right-most, keeping track of which tables exist in this column.
        // If for any given column, we find an overlap, we pick one table
        // to remain in this column, then punt any overlappers to the right
        // until they no longer overlap.  Then we continue this process,
        // making sure to take account that tables may have moved, and thus the
        // furthest column may also have changed.
        
        // Pairs each grid area with its integer priority.  Starts in order of table left-most index:
        ArrayList<Pair<Long, GridArea>> gridAreas = new ArrayList<>(
                Streams.mapWithIndex(this.gridAreas.stream()
                        .sorted(Comparator.<GridArea, Integer>comparing(t -> t.getPosition().columnIndex).thenComparing(t -> t.getSortKey())),
                        (x, i) -> new Pair<> (i, x))
            .collect(Collectors.toList()));

        // Any currently open grid areas.  These will not overlap with each other vertically:
        ArrayList<Pair<Long, GridArea>> openGridAreas = new ArrayList<>();
        
        // Note: gridAreas.size() may change during the loop!  But nothing at position
        // before i will be modified or looked at.
        gridAreaLoop: for (int i = 0; i < gridAreas.size(); i++)
        {
            Pair<Long, GridArea> cur = gridAreas.get(i);
            
            // Check for overlap with open grid areas:
            for (Pair<Long, GridArea> openGridArea : openGridAreas)
            {
                if (overlap(openGridArea.getSecond(), cur.getSecond()))
                {
                    // Shunt us sideways so that we don't overlap, and add to right point in list
                    // We may overlap more tables, but that is fine, we will get shunted again
                    // next time round if needed
                    CellPosition curPos = cur.getSecond().getPosition();
                    curPos = new CellPosition(curPos.rowIndex, openGridArea.getSecond().getBottomRightIncl().columnIndex + CellPosition.col(1));
                    cur.getSecond().setPosition(curPos);
                    
                    // Now need to add us to the gridAreas list at correct place.  We don't
                    // worry about removing ourselves as it's more hassle than it's worth.
                    
                    // The right place is the end, or the first time that we compare less
                    // when comparing left-hand edge coordinates (and secondarily, priority)
                    for (int j = i + 1; j < gridAreas.size(); j++)
                    {
                        int lhs = gridAreas.get(j).getSecond().getPosition().columnIndex;
                        if (curPos.columnIndex < lhs
                                 || (curPos.columnIndex == lhs && cur.getFirst() < gridAreas.get(j).getFirst()))
                        {
                            gridAreas.add(j, cur);
                            continue gridAreaLoop;
                        }
                    }
                    
                    gridAreas.add(cur);
                    continue gridAreaLoop;
                }
            }
            // Close any grid areas that we have gone to the right of:
            openGridAreas.removeIf(p -> p.getSecond().getBottomRightIncl().columnIndex <= cur.getSecond().getPosition().columnIndex);
            
            // Add ourselves to the open areas:
            openGridAreas.add(cur);
        }
        
        currentColumns.set( 
                CellPosition.col(gridAreas.stream()
                        .mapToInt(g -> g.getSecond().getBottomRightIncl().columnIndex + 1)
                        .max()
                    .orElse(0)
                    + columnsToRight));
        if (!currentKnownLastRowIncl.isBound())
        {
            currentKnownLastRowIncl.set(lastRows.stream().max(Comparator.comparingInt(x -> x)).<@AbsRowIndex Integer>orElse(CellPosition.row(0)) + CellPosition.row(rowsToBottom));
        }
        VisibleBounds bounds = container.redoLayout();
        updatingSizeAndPositions = false;
        
        for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
        {
             nodeSupplier.sizesOrPositionsChanged(bounds);
        }
        updateHBar();
        updateVBar();
    }

    public void bindVisibleRowsTo(VirtualGrid srcGrid)
    {
        currentKnownLastRowIncl.bind(srcGrid.currentKnownLastRowIncl);
        FXUtility.addChangeListenerPlatformNN(currentKnownLastRowIncl, newNum -> {
            container.redoLayout();
        });
    }

    private boolean overlap(GridArea a, GridArea b)
    {
        int aLeftIncl = a.getPosition().columnIndex;
        int aRightIncl = a.getBottomRightIncl().columnIndex;
        int aTopIncl = a.getPosition().rowIndex;
        int aBottomIncl = a.getBottomRightIncl().rowIndex;
        
        int bLeftIncl = b.getPosition().columnIndex;
        int bRightIncl = b.getBottomRightIncl().columnIndex;
        int bTopIncl = b.getPosition().rowIndex;
        int bBottomIncl = b.getBottomRightIncl().rowIndex;
        boolean distinctHoriz = aLeftIncl > bRightIncl || bLeftIncl > aRightIncl;
        boolean distinctVert = aTopIncl > bBottomIncl || bTopIncl > aBottomIncl;
        boolean overlap = !(distinctHoriz || distinctVert);
        /*
        if (overlap)
        {
            Log.logStackTrace("Found overlap between " + a + " " + a.getPosition() + "-(" + aRightIncl + ", " + aBottomIncl + ")"
                + " and " + b + " " + b.getPosition() + "-(" + bRightIncl + ", " + bBottomIncl + ")");
        }
        */
        return overlap;
    }

    private void activateCell(CellPosition cellPosition)
    {
        for (VirtualGridSupplier<? extends Node> nodeSupplier : nodeSuppliers)
        {
            nodeSupplier.startEditing(null, cellPosition);
        }
    }

    /**
     * Gets position of the cell relative to the origin of the complete grid.
     */
    private BoundingBox getPixelPosition(CellPosition target)
    {
        double minX = sumColumnWidths(CellPosition.col(0), target.columnIndex);
        double minY = rowHeight * target.rowIndex;
        return new BoundingBox(minX, minY, getColumnWidth(target.columnIndex), rowHeight);
    }

    private void smoothScrollToEnsureVisible(CellPosition target)
    {
        Point2D currentXY = new Point2D(getCurrentScrollX(null), getCurrentScrollY(null));
        Bounds targetBounds = getPixelPosition(target);
        double deltaX = 0.0;
        double deltaY = 0.0;
        if (targetBounds.getMinX() < currentXY.getX())
        {
            // Off screen to left, scroll til it's visible plus a margin:
            deltaX = targetBounds.getMinX() - currentXY.getX() - 20;
        }
        else if (targetBounds.getMaxX() > currentXY.getX() + container.getWidth())
        {
            // Off screen to right:
            deltaX = targetBounds.getMaxX() - (currentXY.getX() + container.getWidth()) + 20;
        }

        if (targetBounds.getMinY() < currentXY.getY())
        {
            // Off screen above, scroll til it's visible plus a margin:
            deltaY = targetBounds.getMinY() - currentXY.getY() - 20;
        }
        else if (targetBounds.getMaxY() > currentXY.getY() + container.getHeight())
        {
            // Off screen below:
            deltaY = targetBounds.getMaxY() - (currentXY.getY() + container.getHeight()) + 20;
        }

        if (deltaX != 0.0 || deltaY != 0.0)
            scrollGroup.requestScrollBy(-deltaX, -deltaY);
    }
    
    public void addGridAreas(Collection<GridArea> gridAreas)
    {
        this.gridAreas.addAll(gridAreas);
        for (GridArea gridArea : gridAreas)
        {
            gridArea.addedToGrid(this);
        }
        updateSizeAndPositions();
    }

    public void removeGridArea(GridArea gridArea)
    {
        gridAreas.remove(gridArea);
        updateSizeAndPositions();
    }

    public VisibleBounds getVisibleBounds()
    {
        return container.getVisibleBoundDetails();
    }

    public void setEffectOnNonOverlays(@Nullable Effect effect)
    {
        container.setEffect(effect);
    }

    /**
     * For the given gridArea, find the graphical bounds (in relative coords to that grid area)
     * of all the other grid areas which touch it.
     */
    public ImmutableList<BoundingBox> getTouchingRectangles(@UnknownInitialization(GridArea.class) GridArea gridArea)
    {
        // Make a rectangle one bigger in each dimension, and see if that touches:
        RectangleBounds overSize = new RectangleBounds(
            gridArea.getPosition().offsetByRowCols(-1, -1),
            gridArea.getBottomRightIncl().offsetByRowCols(1, 1)
        );
        // Could be a bit cleverer here with some of the logic:
        BoundingBox targetTopLeft = getRectangleBoundsInContainer(new RectangleBounds(gridArea.getPosition(), gridArea.getPosition()));
        return gridAreas.stream()
            .filter(g -> g != gridArea)
            .map(g -> new RectangleBounds(g.getPosition(), g.getBottomRightIncl()))
            .filter(r -> r.touches(overSize))
            .map(r -> {
                BoundingBox box = getRectangleBoundsInContainer(r);
                return new BoundingBox(
                    box.getMinX() - targetTopLeft.getMinX(),
                    box.getMinY() - targetTopLeft.getMinY(),
                    box.getWidth(),
                    box.getHeight()    
                );
            })
            .collect(ImmutableList.toImmutableList());
    }

    // A really simple class that manages a single button which is shown when an empty location is focused
    private class CreateTableButtonSupplier extends VirtualGridSupplier<Button>
    {
        private @MonotonicNonNull Button button;
        private @Nullable CellPosition buttonPosition;
        // Button position, last mouse position on screen:
        private final CreateTable createTable;

        private CreateTableButtonSupplier(CreateTable createTable)
        {
            this.createTable = createTable;
        }

        @Override
        protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
        {
            if (button == null)
            {
                Point2D[] lastMousePos = new Point2D[1];
                button = GUI.button("create.table", () -> {
                    @Nullable CellSelection curSel = selection.get();
                    if (curSel instanceof EmptyCellSelection)
                    {
                        // Offer to create a table at that location, but we need to ask data or transform, if it's not the first table:
                        createTable.createTable(((EmptyCellSelection)curSel).position, lastMousePos[0], FXUtility.mouse(VirtualGrid.this));
                    }
                }, "create-table-grid-button");
                button.setFocusTraversable(false);
                button.addEventFilter(MouseEvent.ANY, e -> {
                    lastMousePos[0] = new Point2D(e.getScreenX(), e.getScreenY());
                });
                containerChildren.add(button, ViewOrder.STANDARD_CELLS);
            }

            @Nullable CellSelection curSel = selection.get();
            if (curSel != null && curSel instanceof EmptyCellSelection)
            {
                button.setVisible(true);
                CellPosition pos = ((EmptyCellSelection) curSel).position;
                buttonPosition = pos;
                double x = visibleBounds.getXCoord(pos.columnIndex);
                double y = visibleBounds.getYCoord(pos.rowIndex);
                button.resizeRelocate(
                    x,
                    y,
                    Math.max(button.minWidth(100.0), visibleBounds.getXCoordAfter(pos.columnIndex) - x),
                    visibleBounds.getYCoordAfter(pos.rowIndex) - y
                );
            }
            else
            {
                button.setVisible(false);
                buttonPosition = null;
            }
        }

        @Override
        protected @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return cellPosition.equals(buttonPosition) ? ItemState.DIRECTLY_CLICKABLE : null;
        }
    }
    
    private class EmptyCellSelection implements CellSelection
    {
        private final CellPosition position;

        public EmptyCellSelection(CellPosition position)
        {
            this.position = position;
        }

        @Override
        public void doCopy()
        {
            // Can't copy an empty cell
        }

        @Override
        public CellPosition getActivateTarget()
        {
            return position;
        }

        @Override
        public CellSelection atHome(boolean extendSelection)
        {
            // Should we make home do anything if on empty spot?
            return this;
        }

        @Override
        public CellSelection atEnd(boolean extendSelection)
        {
            // Should we make end do anything if on empty spot?
            return this;
        }
        
        @Override
        public Either<CellPosition, CellSelection> move(boolean extendSelection, int _byRows, int _byColumns)
        {
            @AbsRowIndex int byRows = CellPosition.row(_byRows);
            @AbsColIndex int byColumns = CellPosition.col(_byColumns);
            CellPosition newPos = new CellPosition(
                Utility.minRow(currentKnownLastRowIncl.get(), Utility.maxRow(position.rowIndex + byRows, CellPosition.row(0))), 
                Utility.minCol(currentColumns.get() - CellPosition.col(1), Utility.maxCol(position.columnIndex + byColumns, CellPosition.col(0)))
            );
            if (newPos.equals(position))
                return Either.right(this); // Not moving
            // Go through each grid area and see if it contains the position:
            for (GridArea gridArea : gridAreas)
            {
                if (gridArea.contains(newPos))
                {
                    return Either.left(newPos);
                }
            }
            return Either.right(new EmptyCellSelection(newPos));
        }

        @Override
        public CellPosition positionToEnsureInView()
        {
            return position;
        }

        @Override
        public RectangleBounds getSelectionDisplayRectangle()
        {
            return new RectangleBounds(position, position);
        }

        @Override
        public boolean isExactly(CellPosition cellPosition)
        {
            return position.equals(cellPosition);
        }

        @Override
        public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
        {
            // Empty cell overlaps no grid area:
            return false;
        }
    }
    
    public static enum ListenerOutcome { KEEP, REMOVE }
    
    @OnThread(Tag.FXPlatform)
    public static interface SelectionListener
    {
        /**
         * Returns whether to keep listener, and an optional updated to be run
         * once the bounds have been calculated
         * @param oldSelection
         * @param newSelection
         * @return
         */
        @OnThread(Tag.FXPlatform)
        public Pair<ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection);
    }
    
    @OnThread(Tag.FXPlatform)
    private class GridAreaHighlight extends RectangleOverlayItem
    {
        private @Nullable GridArea picked;
        
        private GridAreaHighlight()
        {
            super(ViewOrder.OVERLAY_ACTIVE);
        }

        @Override
        public Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
        {
            if (picked == null)
                return Optional.empty();
            else
            {
                return visibleBounds.clampVisible(new RectangleBounds(picked.getPosition(), picked.getBottomRightIncl())).map(b -> Either.left(getRectangleBoundsInContainer(b)));
            }
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.getStyleClass().add("pick-table-overlay");
        }

        public @Nullable GridArea highlightAtScreenPos(Point2D screenPos, Predicate<GridArea> validPick, FXPlatformConsumer<@Nullable Cursor> setCursor)
        {
            Point2D localPos = container.screenToLocal(screenPos);
            @Nullable CellPosition cellAtScreenPos = getCellPositionAt(localPos.getX(), localPos.getY());
            @Nullable GridArea oldPicked = picked;
            picked = gridAreas.stream().filter(validPick).filter(g -> cellAtScreenPos != null && g.contains(cellAtScreenPos)).findFirst().orElse(null);
            if (picked != oldPicked)
            {
                container.redoLayout();
            }
            setCursor.consume(picked != null ? Cursor.HAND : null);
            return picked;
        }
    }
}
