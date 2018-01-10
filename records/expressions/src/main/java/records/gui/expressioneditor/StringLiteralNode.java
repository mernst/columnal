package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends EntryNode<Expression, ExpressionNodeParent> implements OperandNode<Expression>
{
    private final AutoComplete autoComplete;

    public StringLiteralNode(String initialValue, ConsecutiveBase<Expression, ExpressionNodeParent> parent)
    {
        super(parent, Expression.class);
        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        Completion currentCompletion = new EndStringCompletion();
        this.autoComplete = new AutoComplete(textField, (s, q) ->
        {
            return Collections.singletonList(currentCompletion);
        }, new SimpleCompletionListener()
        {
            @Override
            public String exactCompletion(String currentText, Completion selectedItem)
            {
                super.exactCompletion(currentText, selectedItem);
                if (currentText.endsWith("\""))
                    return currentText.substring(0, currentText.length() - 1);
                else
                    return currentText;
            }

            @Override
            protected String selected(String currentText, @Nullable Completion c, String rest)
            {
                parent.setOperatorToRight(StringLiteralNode.this, "");
                parent.focusRightOf(StringLiteralNode.this, Focus.LEFT);
                return currentText;
            }

            @Override
            public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
            {
                return currentText;
            }
        }, c -> false);

        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
        textField.setText(initialValue);
        updateNodes();
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(new Label("\u201C"), textField, new Label("\u201D"));
    }

    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        return errorDisplayer.record(this, new records.transformations.expression.StringLiteral(textField.getText()));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public void showError(String error, List<ErrorRecorder.QuickFix> quickFixes)
    {
        // TODO
    }

    @Override
    public void showType(String type)
    {
        // It's obviously a string.  Do we need to show the type?
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    private static class EndStringCompletion extends Completion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, currentText);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return true;
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            if (input.endsWith("\""))
                return CompletionAction.COMPLETE_IMMEDIATELY;

            return CompletionAction.SELECT;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return true;
        }
    }
}
