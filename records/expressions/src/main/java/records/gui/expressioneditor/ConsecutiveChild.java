package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionInfoDisplay.CaretSide;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Stream;

/**
 * A child of a ConsecutiveBase item.  Has methods for selection, dragging and focusing.
 */
public interface ConsecutiveChild<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends EEDisplayNode, Locatable, ErrorDisplayer<EXPRESSION, SAVER>
{
    @Pure
    public ConsecutiveBase<EXPRESSION, SAVER> getParent();

    // Delete from the end, backspace pressed ahead of us.  Return true if handled, false if not.
    boolean deleteLast();
    
    // Delete from the beginning, delete pressed before us.  Return true if handled, false if not.
    boolean deleteFirst();

    /**
     * Sets the selected status of the child.  If focus is true,
     * focus the top header.
     * @param selected Selected or not?
     * @param focus Focus the top header?
     * @param onFocusLost If we focus the top header, run this on next focus loss.
     */
    void setSelected(boolean selected, boolean focus, @Nullable FXPlatformRunnable onFocusLost);

    /**
     * Is focus still with node that would be focused if setSelected(true, true) was called?
     */
    @Pure
    boolean isSelectionFocused();

    // Ideally this would be protected access:
    @SuppressWarnings("unchecked")
    public static <US extends StyledShowable, TARGET extends StyledShowable> @Nullable Pair<ConsecutiveChild<? extends TARGET, ?>, Double> closestDropSingle(ConsecutiveChild<US, ?> us, Class<US> ourClass, Node node, Point2D loc, Class<TARGET> forType)
    {
        if (forType.isAssignableFrom(ourClass))
            return new Pair<ConsecutiveChild<? extends TARGET, ?>, Double>((ConsecutiveChild)us, FXUtility.distanceToLeft(node, loc));
        else
            return null;
    }
    
    void setHoverDropLeft(boolean on);

    default boolean isBlank(@UnknownInitialization(Object.class) ConsecutiveChild<EXPRESSION, SAVER> this) { return false; }

    void focusChanged();

    @OnThread(Tag.FXPlatform)
    Stream<Pair<Label, Boolean>> _test_getHeaders();

    public void save(SAVER saver);

    void unmaskErrors();

    boolean isFocusPending();
    
    // Execute any pending focus request.
    void flushFocusRequest();

    boolean opensBracket(BracketBalanceType bracketBalanceType);

    boolean closesBracket(BracketBalanceType bracketBalanceType);

    default void removeNestedBlanks() {};
    
    void setPrompt(@Localized String prompt);

    void bindDisable(BooleanExpression disabledProperty);

    default public ImmutableList<ErrorInfo> getErrorsForAdjacentSide(CaretSide sideOfThisNode)
    {
        ImmutableList<ConsecutiveChild<EXPRESSION, SAVER>> children = getParent().getAllChildren();

        int index = Utility.indexOfRef(children, this);
        
        if (sideOfThisNode == CaretSide.LEFT && index > 0)
        {
            return children.get(index - 1).getErrors();
        }
        else if (sideOfThisNode == CaretSide.RIGHT && index < children.size() - 1)
        {
            return children.get(index + 1).getErrors();
        }
        
        return ImmutableList.of();
    }

    public ImmutableList<ErrorInfo> getErrors();

    // If possible, merge the content from right into this one and return true.
    // If not possible, return false.  Merging happens when an operator or keyword
    // is deleted, which leaves two consecutive plain fieldss
    default boolean mergeFromRight(ConsecutiveChild<EXPRESSION, SAVER> right)
    {
        return false;
    }

    default boolean isImplicitlyBracketed()
    {
        return false;
    }
}
