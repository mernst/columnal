package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.function.list.Single;
import styled.StyledShowable;
import utility.FXPlatformFunction;
import utility.Utility;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 19/02/2017.
 */
public abstract class Consecutive<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT>
{
    protected final EEDisplayNodeParent parent;
    
    @SuppressWarnings("initialization") // Because of loading
    public Consecutive(OperandOps<EXPRESSION, SEMANTIC_PARENT> operations, @UnknownInitialization(Object.class) EEDisplayNodeParent parent, @Nullable String prefixText, @Nullable String suffixText, String style, @Nullable Stream<SingleLoader<EXPRESSION, SEMANTIC_PARENT>> content)
    {
        super(operations, prefixText, suffixText, style);
        this.parent = parent;
        if (content != null)
        {
            atomicEdit.set(true);
            children.addAll(content.map(f -> f.load(this)).collect(Collectors.toList()));
            if (children.isEmpty())
                children.add(makeBlankChild());
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
        else
        {
            atomicEdit.set(true);
            children.add(makeBlankChild());
            atomicEdit.set(false);
        }
    }

    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) Consecutive<EXPRESSION, SEMANTIC_PARENT> this)
    {
        if (parent != null)
            parent.changed(this);
    }

    @Override
    protected void parentFocusRightOfThis(Focus side, boolean becauseOfTab)
    {
        parent.focusRightOf(this, side, becauseOfTab);
    }

    @Override
    protected void parentFocusLeftOfThis()
    {
        parent.focusLeftOf(this);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.<String>of(style));
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return parent.getEditor();
    }
}
