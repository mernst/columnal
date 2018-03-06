package test.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.animation.AnimationTimer;
import javafx.geometry.BoundingBox;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import styled.StyledString;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(JUnitQuickcheck.class)
public class TestVirtualGridScroll extends ApplicationTest
{
    private final double ORIGINAL_SCROLL = 5001.0;
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @SuppressWarnings("nullness")
    private Label node;

    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    @Override
    public void start(Stage stage) throws Exception
    {
        virtualGrid = new VirtualGrid((c, p) -> {});
        stage.setScene(new Scene(new BorderPane(virtualGrid.getNode())));
        stage.setWidth(800);
        stage.setHeight(600);
        stage.show();
        
        // Add a huge table which will be roughly 10,000 pixels square:
        // That's about 400 rows, and 100 columns
        virtualGrid.addGridAreas(ImmutableList.of(new GridArea(new MessageWhenEmpty(StyledString.s("")))
        {
            @Override
            protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
            {
            }

            @Override
            protected CellPosition recalculateBottomRightIncl()
            {
                return new CellPosition(CellPosition.row(400), CellPosition.col(100));
            }

            @Override
            public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
            {
                return null;
            }

            @Override
            public String getSortKey()
            {
                return "";
            }
        }));
        
        virtualGrid._test_setColumnWidth(48, 37.0);
        virtualGrid._test_setColumnWidth(50, 106.0);
        virtualGrid._test_setColumnWidth(51, 52.0);
        virtualGrid._test_setColumnWidth(53, 1.0);
        
        node = new Label("X");
        // Add node in middle of screen when viewing top left:        
        virtualGrid.getFloatingSupplier().addItem(new FloatingItem()
        {
            @Override
            public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
            {
                @AbsColIndex Integer xCell = Utility.boxCol(CellPosition.col(5));
                @AbsRowIndex Integer yCell = Utility.boxRow(CellPosition.row(10));
                double x = columnBounds.getItemCoord(xCell);
                double y = rowBounds.getItemCoord(yCell);
                double width = columnBounds.getItemCoordAfter(xCell) - x;
                double height = rowBounds.getItemCoordAfter(yCell) - y;
                return Optional.of(new BoundingBox(x, y, width, height));
            }

            @Override
            public Pair<ViewOrder, Node> makeCell()
            {
                return new Pair<>(ViewOrder.STANDARD, node);
            }
        });
    }

    @OnThread(Tag.Any)
    public static class ScrollAmounts
    {
        final double[] amounts;

        public ScrollAmounts(double[] amounts)
        {
            this.amounts = amounts;
        }
    }
    
    @OnThread(Tag.Any)
    public static class GenScrollAmounts extends Generator<ScrollAmounts>
    {
        public GenScrollAmounts()
        {
            super(ScrollAmounts.class);
        }

        @Override
        public ScrollAmounts generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            int length = 6;
            double[] amounts = new double[length]; 
            for (int i = 0; i < length; i++)
            {
                amounts[i] = sourceOfRandomness.nextInt(10) == 0 ? 0.0 : sourceOfRandomness.nextDouble(-600.0, 600.0);
            }
            return new ScrollAmounts(amounts);
        }
    }
    
    @Property(trials = 5)
    public void clampTest(@When(seed=1L) @From(GenScrollAmounts.class) ScrollAmounts scrollAmounts)
    {
        // We started at top and left, so what we do is scroll out by half the amount, then attempt to scroll back the full amount, then repeat

        for (double amount : scrollAmounts.amounts)
        {
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-Math.abs(amount) / 2.0, 0.0);
            });
            assertEquals(Math.abs(amount) / 2.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollXPos()), 0.1);
            checkOffsetsNegative();
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(Math.abs(amount), 0.0);
            });
            assertEquals(0.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollXPos()), 0.01);
            checkOffsetsNegative();
        }

        for (double amount : scrollAmounts.amounts)
        {
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, -Math.abs(amount) / 2.0);
            });
            assertEquals(Math.abs(amount) / 2.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.1);
            checkOffsetsNegative();
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, Math.abs(amount));
            });
            assertEquals(0.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.01);
            checkOffsetsNegative();


            // Now scroll away and then exactly back:
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, -Math.abs(amount));
            });
            assertEquals(Math.abs(amount), (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.1);
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, Math.abs(amount));
            });
            assertEquals(0.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.01);
        }
        
        // Now we need to go to the very bottom/right and try in the other direction:
        TestUtil.fx_(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(-Double.MAX_VALUE, -Double.MAX_VALUE);
        });
        
        // We should really calculate these ourselves, rather than trusting the code we are testing...
        double endX = TestUtil.<Double>fx(() -> virtualGrid._test_getScrollXPos());
        double endY = TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos());

        for (double amount : scrollAmounts.amounts)
        {
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(Math.abs(amount) / 2.0, 0.0);
            });
            assertEquals(endX - Math.abs(amount) / 2.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollXPos()), 0.1);
            checkOffsetsNegative();
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-Math.abs(amount), 0.0);
            });
            assertEquals(endX, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollXPos()), 0.01);
            checkOffsetsNegative();
        }

        for (double amount : scrollAmounts.amounts)
        {
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, Math.abs(amount) / 2.0);
            });
            assertEquals(endY - Math.abs(amount) / 2.0, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.1);
            checkOffsetsNegative();
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(0.0, -Math.abs(amount));
            });
            assertEquals(endY, (double)TestUtil.<Double>fx(() -> virtualGrid._test_getScrollYPos()), 0.01);
            checkOffsetsNegative();
        }
    }
    
    @SuppressWarnings("deprecation")
    @Property(trials = 5)
    public void scrollXYBy(@From(GenScrollAmounts.class) ScrollAmounts scrollX, @From(GenScrollAmounts.class) ScrollAmounts scrollY)
    {
        // Scroll to the middle to begin with so we don't get clamped:
        virtualGrid.getScrollGroup().requestScrollBy(-ORIGINAL_SCROLL, -ORIGINAL_SCROLL);
        TestUtil.sleep(300);
        
        // Check window has sized properly:
        assertThat(TestUtil.fx(() -> virtualGrid.getNode().getHeight()), Matchers.greaterThanOrEqualTo(500.0));
        assertThat(TestUtil.fx(() -> virtualGrid.getNode().getWidth()), Matchers.greaterThanOrEqualTo(700.0));

        checkOffsetsNegative();
      
        // We check with and without delay to try with/without smooth scrolling and jumping:
        for (int delay : new int[]{0, (int) (virtualGrid.getScrollGroup()._test_getScrollTimeNanos() / 1_000_000L)})
        {
            assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
            // Test that scroll and scroll back works:
            for (double amount : scrollX.amounts)
            {
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(amount, 0.0));
                TestUtil.sleep(delay);
                assertThat("Scrolling " + amount, TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL - amount, 0.1));
                checkOffsetsNegative();
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-amount, 0.0));
                TestUtil.sleep(delay);
                assertThat("Scrolling " + (-amount), TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
                
                checkOffsetsNegative();
            }

            assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollYPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
            // Test that scroll and scroll back works:
            for (double amount : scrollY.amounts)
            {
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(amount, 0.0));
                TestUtil.sleep(delay);
                assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollXPos()), Matchers.closeTo(ORIGINAL_SCROLL - amount, 0.1));
                checkOffsetsNegative();
                TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-amount, 0.0));
                TestUtil.sleep(delay);
                assertThat(TestUtil.fx(() -> virtualGrid._test_getScrollYPos()), Matchers.closeTo(ORIGINAL_SCROLL, 0.1));
                checkOffsetsNegative();
            }
        }
        
        

    }

    @SuppressWarnings("deprecation")
    private void checkOffsetsNegative()
    {
        double[][] offsetsAndLimits = virtualGrid._test_getOffsetsAndLimits();
        for (int i = 0; i < offsetsAndLimits.length; i++)
        {
            assertThat("Offset index " + i, offsetsAndLimits[i][1], Matchers.greaterThanOrEqualTo(offsetsAndLimits[i][0]));
            assertThat("Offset index " + i, offsetsAndLimits[i][1], Matchers.lessThanOrEqualTo(0.0));
        }

        int[][] indexesAndLimits = virtualGrid._test_getIndexesAndLimits();
        for (int i = 0; i < indexesAndLimits.length; i++)
        {
            assertThat("Index index " + i, indexesAndLimits[i][0], Matchers.greaterThanOrEqualTo(0));
            assertThat("Index index " + i, indexesAndLimits[i][0], Matchers.lessThan(indexesAndLimits[i][1]));            
        }
    }

    @Test
    public void testSmoothScrollMonotonic()
    {
        // Easier to spot issues with a slower scroll:
        virtualGrid.getScrollGroup()._test_setScrollTimeNanos(1_000_000_000L);

        // Down twice, back up to the top twice, each separate:
        testMonotonicScroll(true);
        testMonotonicScroll(true);
        testMonotonicScroll(false);
        testMonotonicScroll(false);

        // Up to nothing, then down, each separate:
        testMonotonicScroll(false);
        testMonotonicScroll(true);
        
        // We are one from top.  Now scroll thrice up in quick succession:
        testMonotonicScroll(false, false, false);
    }

    @SuppressWarnings("deprecation")
    private void testMonotonicScroll(boolean... scrollDocumentUp)
    {
        final @Nullable AnimationTimer[] timer = new AnimationTimer[1];
        try
        {
            moveTo(node);
            final ArrayList<Double> yCoords = new ArrayList<>();
            TestUtil.fx_(() -> {
                timer[0] = new AnimationTimer()
                {
                    @Override
                    public void handle(long now)
                    {
                        yCoords.add(node.localToScreen(node.getBoundsInLocal()).getMinY());
                    }
                };
                timer[0].start();
            });
            for (boolean up : scrollDocumentUp)
            {
                scroll(3, up == SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN);
            }
            TestUtil.sleep(1200);
            TestUtil.fx_(() -> { if (timer[0] != null) timer[0].stop();});
            Function<Double, Matcher<Double>> strictlyAfter;
            Function<Double, Matcher<Double>> afterOrSame;
            if (scrollDocumentUp[0])
            {
                strictlyAfter = Matchers::greaterThan;
                afterOrSame = Matchers::greaterThanOrEqualTo;
            }
            else
            {
                strictlyAfter = Matchers::lessThan;
                afterOrSame = Matchers::lessThanOrEqualTo;
            }
            
            // Check that the node moved:
            //assertThat(yCoords.get(0), strictlyAfter.apply(yCoords.get(yCoords.size() - 1)));
            // Check that list is sorted:
            for (int i = 0; i < yCoords.size() - 1; i++)
            {
                assertThat(yCoords.get(i), afterOrSame.apply(yCoords.get(i + 1)));
            }
        }
        finally
        {
            TestUtil.fx_(() -> { if (timer[0] != null) timer[0].stop();});
        }
    }
}
