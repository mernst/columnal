package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.QuickFix;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use BracketedExpression.
 */
public @Interned abstract class ConsecutiveBase<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNodeParent, EEDisplayNode, Locatable, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
{
    protected final OperandOps<EXPRESSION, SEMANTIC_PARENT> operations;

    protected final String style;
    protected final ObservableList<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> children;
    private final @Nullable Node prefixNode;
    private final @Nullable Node suffixNode;
    private @Nullable String prompt = null;

    @SuppressWarnings("initialization")
    public ConsecutiveBase(OperandOps<EXPRESSION, SEMANTIC_PARENT> operations, @Nullable Node prefixNode, @Nullable Node suffixNode, String style)
    {
        this.operations = operations;
        this.style = style;
        children = FXCollections.observableArrayList();

        this.prefixNode = prefixNode;
        this.suffixNode = suffixNode;
        listenToNodeRelevantList(children);
        FXUtility.listen(children, c -> {
            //Utility.logStackTrace("Operands size: " + operands.size());
            if (!atomicEdit.get())
                selfChanged();
        });
        FXUtility.addChangeListenerPlatformNN(atomicEdit, changing -> {
            if (!changing)
                selfChanged();
        });
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        List<Node> childrenNodes = new ArrayList<Node>();
        for (ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> child : children)
        {
            childrenNodes.addAll(child.nodes());
        }
        if (this.prefixNode != null)
            childrenNodes.add(0, this.prefixNode);
        if (this.suffixNode != null)
            childrenNodes.add(this.suffixNode);
        return childrenNodes.stream();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return children.stream().map(c -> c);
    }

    @NonNull
    protected EntryNode<EXPRESSION, SEMANTIC_PARENT> makeBlankChild()
    {
        return operations.makeGeneral(this, null);
    }

    protected abstract void selfChanged();

    @Override
    public void focus(Focus side)
    {
        if (children.isEmpty())
        {
            // Shouldn't happen, but better to ignore call than throw exception:
            Log.logStackTrace("Empty operands!");
            return;
        }
        
        if (side == Focus.LEFT)
            children.get(0).focus(side);
        else
            children.get(children.size() - 1).focus(side);
    }
    
    protected void replaceLoad(EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> oldNode, @NonNull Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> newNode)
    {
        if (newNode.getFirst() == ReplacementTarget.CURRENT)
        {
            replace(oldNode, newNode.getSecond().loadAsConsecutive().load(this, getThisAsSemanticParent()));
        }
        else
        {
            Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> loaded = newNode.getSecond().loadAsConsecutive(hasImplicitRoundBrackets());

            List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> startingOperands = Utility.mapList(loaded.getFirst(), operand -> operand.load(this, getThisAsSemanticParent()));
            List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> startingOperators = Utility.mapList(loaded.getSecond(), operator -> operator.load(this, getThisAsSemanticParent()));
            
            atomicEdit.set(true);
            operands.setAll(startingOperands);
            operators.setAll(startingOperators);
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
    }

    protected abstract boolean hasImplicitRoundBrackets();

    // Replaces the whole operator-expression that operator was part of, with the new expression
    /*
    protected void replaceWholeLoad(EntryNode<EXPRESSION, SEMANTIC_PARENT> oldOperator, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT> e)
    {
        if (children.contains(oldOperator))
        {
            Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> loaded = e.loadAsConsecutive(hasImplicitRoundBrackets());
            List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> startingOperands = Utility.mapList(loaded.getFirst(), operand -> operand.load(this, getThisAsSemanticParent()));
            List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> startingOperators = Utility.mapList(loaded.getSecond(), operator -> operator.load(this, getThisAsSemanticParent()));
            atomicEdit.set(true);
            operands.forEach(EEDisplayNode::cleanup);
            operators.forEach(EEDisplayNode::cleanup);
            operands.setAll(startingOperands);
            operators.setAll(startingOperators);
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
    }
    */

    public void replace(EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> oldNode, @Nullable EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> newNode)
    {
        int index = getOperandIndex(oldNode);
        //System.err.println("Replacing " + oldNode + " with " + newNode + " index " + index);
        if (index != -1)
        {
            //Utility.logStackTrace("Removing " + oldNode + " from " + this);
            if (newNode != null)
                children.set(index, newNode);
            else
                children.remove(index).cleanup();
        }
    }

    // Is this node equal to then given one, or does it contain the given one?
    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child || getAllChildren().stream().anyMatch(n -> n.isOrContains(child));
    }

    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return getAllChildren().stream().flatMap(o -> o._test_getHeaders());
    }

    /**
     * If the operand to the right of rightOf does NOT pass the given test (or the operator between is non-blank),
     * use the supplier to make one and insert it with blank operator between.
     */
    public void ensureOperandToRight(EntryNode<EXPRESSION, SEMANTIC_PARENT> rightOf, Predicate<EntryNode<EXPRESSION, SEMANTIC_PARENT>> isAcceptable, Supplier<EntryNode<EXPRESSION, SEMANTIC_PARENT>> makeNew)
    {
        int index = Utility.indexOfRef(children, rightOf);
        if (index + 1 < children.size() && isAcceptable.test(children.get(index + 1)) && children.get(index).isBlank())
            return; // Nothing to do; already acceptable
        // Must add:
        atomicEdit.set(true);
        children.add(index + 1, makeNew.get());
        atomicEdit.set(false);
    }

    public static enum OperatorOutcome { KEEP, BLANK }
    
    public OperatorOutcome addOperandToRight(@UnknownInitialization EntryNode<EXPRESSION, SEMANTIC_PARENT> rightOf, String operatorEntered, String initialContent, boolean focus)
    {
        // Must add operand and operator
        int index = Utility.indexOfRef(children, rightOf);
        
        if (index + 1 < children.size())
        {
            if (focus)
                children.get(index + 1).focus(Focus.LEFT);
            return OperatorOutcome.KEEP;
        }
        
        if (index != -1)
        {
            atomicEdit.set(true);
            EntryNode<EXPRESSION, SEMANTIC_PARENT> operandNode = operations.makeGeneral(this, getThisAsSemanticParent(), initialContent);
            if (focus)
                operandNode.focusWhenShown();
            children.add(index+1, operandNode);
            atomicEdit.set(false);
            return OperatorOutcome.KEEP;
        }
        // If we can't find it, I guess blank:
        return OperatorOutcome.BLANK;
    }


    public void setOperatorToRight(@UnknownInitialization EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> rightOf, String operator)
    {
        int index = getOperandIndex(rightOf);
        if (index != -1)
        {
            if (index >= children.size() || !children.get(index).fromBlankTo(operator))
            {
                // Add new operator:
                atomicEdit.set(true);
                children.add(index + 1, new OperatorEntry<>(operations.getOperandClass(), operator, true, this));
                atomicEdit.set(false);
            }
        }
    }

    private int getOperandIndex(@UnknownInitialization EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> operand)
    {
        int index = Utility.indexOfRef(children, operand);
        if (index == -1)
            Log.logStackTrace("Asked for index but " + operand + " not a child of parent " + this);
        return index;
    }


    //protected abstract List<Pair<DataType,List<String>>> getSuggestedParentContext() throws UserException, InternalException;

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (!atomicEdit.get())
            selfChanged();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void focusRightOf(@UnknownInitialization EEDisplayNode child, Focus side)
    {
        // Cast is safe because of instanceof, and the knowledge that
        // all our children have EXPRESSION as inner type:
        if (child instanceof EntryNode && Utility.containsRef(children, (EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child))
        {
            int index = getOperandIndex((EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child);
            if (index + 1 < children.size())
                children.get(index + 1).focus(side);
            else
                parentFocusRightOfThis(side);
        }
    }

    protected abstract void parentFocusRightOfThis(Focus side);

    @SuppressWarnings("unchecked")
    @Override
    public void focusLeftOf(@UnknownInitialization EEDisplayNode child)
    {
        if (child instanceof EntryNode && Utility.containsRef(children, (EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child))
        {
            int index = getOperandIndex((EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT>) child);
            if (index > 0)
                children.get(index - 1).focus(Focus.RIGHT);
            else
            {
                // index is zero.  If we are blank then we do go to parent's left
                // If we aren't blank, we make a new blank before us:
                if (children.get(0).isBlank())
                    parentFocusLeftOfThis();
                else
                {
                    addBlankAtLeft();
                }
            }
        }
    }

    private void addBlankAtLeft()
    {
        Log.debug("Adding blank at left");
        atomicEdit.set(true);
        EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> blankOperand = makeBlankChild();
        blankOperand.focusWhenShown();
        children.add(0, blankOperand);
        atomicEdit.set(false);
    }

    protected abstract void parentFocusLeftOfThis();

    public void focusWhenShown()
    {
        children.get(0).focusWhenShown();
    }

    public void prompt(String value)
    {
        prompt = value;
        updatePrompt();
    }

    public @UnknownIfRecorded EXPRESSION saveUnrecorded(ErrorDisplayerRecord errorDisplayers, ErrorAndTypeRecorder onError)
    {
        if (children.isEmpty())
            return operations.makeExpression(errorDisplayers, ImmutableList.of(), ImmutableList.of(), getChildrenBracketedStatus());
        else
            return save(errorDisplayers, onError, children.get(0), children.get(children.size() - 1));
    }

    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.MISC;
    }

    public @UnknownIfRecorded EXPRESSION save(ErrorDisplayerRecord errorDisplayers, ErrorAndTypeRecorder onError, EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> first, EntryNode<@NonNull EXPRESSION, SEMANTIC_PARENT> last)
    {
        int firstIndex = children.indexOf(first);
        int lastIndex = children.indexOf(last);
        BracketedStatus bracketedStatus = BracketedStatus.MISC;
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = children.size() - 1;
        }
        // May be because it was -1, or just those values were passed directly:
        if (firstIndex == 0 && lastIndex == children.size() - 1)
        {
            bracketedStatus = getChildrenBracketedStatus();
        }

        return operations.makeExpression(errorDisplayers, children.subList(firstIndex, lastIndex + 1), bracketedStatus);
    }

    public @Nullable DataType inferType()
    {
        return null; //TODO
    }

    /**
     * Gets all the operands between start and end (inclusive).  Returns the empty list
     * if there any problems (start or end not found, or end before start)
     */
    @Pure
    public List<ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>> getChildrenFromTo(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> allChildren = getAllChildren();
        int a = allChildren.indexOf(start);
        int b = allChildren.indexOf(end);
        if (a == -1 || b == -1 || a > b)
            return Collections.emptyList();
        return allChildren.subList(a, b + 1);
    }

    protected ImmutableList<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> getAllChildren()
    {
        return ImmutableList.copyOf(children);
    }

    public void markSelection(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> from, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> to, boolean selected)
    {
        for (ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> n : getChildrenFromTo(from, to))
        {
            n.setSelected(selected);
        }
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        children.forEach(o -> o.visitLocatable(visitor));
    }

    @SuppressWarnings("unchecked")
    public @Nullable String copyItems(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> all = getAllChildren();
        int startIndex = all.indexOf(start);
        int endIndex = all.indexOf(end);

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        SEMANTIC_PARENT saver = operations.saveToClipboard();
        for (ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> child : all.subList(startIndex, endIndex + 1))
        {
            child.save(saver);
        }
        
        return saver.toString();
    }

    /*
    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.
        atomicEdit.set(true);

        @Nullable Pair<List<OperandNode<EXPRESSION, SEMANTIC_PARENT>>, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>> loaded = loadItems(itemsToInsert);
        if (loaded == null)
            return false;
        List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> newOperands = loaded.getFirst();
        List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> newOperators = loaded.getSecond();

        boolean endsWithOperator;
        if (itemsToInsert.startsOnOperator)
            endsWithOperator = newOperators.size() == newOperands.size() + 1;
        else
            endsWithOperator = newOperators.size() == newOperands.size();

        // If it starts with an operator and you're inserting before an operand, add an extra blank operand

        if (insertBefore instanceof OperandNode)
        {
            int index = operands.indexOf(insertBefore);
            if (index == -1)
                return false;

            // If it starts with an operator and you're inserting before an operand, add an extra blank operand
            if (itemsToInsert.startsOnOperator)
            {
                newOperands.add(0, makeBlankOperand());
            }
            // We are inserting before an operand, so the end is messy if the inserted content
            // didn't end with an operator
            if (!endsWithOperator)
            {
                newOperators.add(makeBlankOperator());
            }

            // We will have an operand first, that one goes at index:
            operands.addAll(index, newOperands);
            // Operator at index follows operand at index:
            operators.addAll(index, newOperators);

        }
        else
        {
            int index = operators.indexOf(insertBefore);
            if (index == -1)
                return false;

            // Inserting before operator, so to match we need an operator first to take its place:
            if (!itemsToInsert.startsOnOperator)
            {
                newOperators.add(0, makeBlankOperator());
            }
            // Inserting before operator, so we need to end with operand:
            if (endsWithOperator)
            {
                newOperands.add(makeBlankOperand());
            }

            // Now we are ok to insert at index for operators, but must adjust for operands:
            operators.addAll(index, newOperators);
            operands.addAll(index + 1, newOperands);
        }

        removeBlanks(operands, operators, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, false, null);

        atomicEdit.set(false);
        return true;
    }
    */

    /*
    private @Nullable List<EntryNode<EXPRESSION, SEMANTIC_PARENT>> loadItems(CopiedItems copiedItems)
    {
        List<EntryNode<EXPRESSION, SEMANTIC_PARENT>> loaded = new ArrayList<>();
        try
        {
            for (int i = 0; i < copiedItems.items.size(); i++)
            {
                String curItem = copiedItems.items.get(i);
                loaded.add(operations.loadOperand(curItem, this));
            }
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
        return loaded;
    }
    */

    public void removeItems(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        atomicEdit.set(true);
        int startIndex;
        int endIndex;
        startIndex = children.indexOf(start);
        endIndex = children.indexOf(end);
        if (startIndex != -1 && endIndex != -1)
        {
            // Important to go backwards so that the indexes don't get screwed up:
            for (int i = endIndex; i >= startIndex; i--)
                children.remove(i).cleanup();
        }
        
        removeBlanks(children, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, true, null);
        atomicEdit.set(false);
    }

    public void setSelected(boolean selected)
    {
        if (prefixNode != null)
            FXUtility.setPseudoclass(prefixNode, "exp-selected", selected);
        if (suffixNode != null)
            FXUtility.setPseudoclass(suffixNode, "exp-selected", selected);
        for (ConsecutiveChild consecutiveChild : getAllChildren())
        {
            consecutiveChild.setSelected(selected);
        }
    }

    /**
     * Focuses a blank slot on the left of the expression, either an existing
     * blank, or a new specially created blank
     */
    public void focusBlankAtLeft()
    {
        if (children.get(0).isBlank())
            children.get(0).focus(Focus.LEFT);
        else
            addBlankAtLeft();
    }

    /**
     * A collection of characters which terminate this item, i.e. which you could press in the child
     * at the last position, and it should complete this ConsecutiveBase and move on.
     *
     * Note that while the returned collection is immutable, this method may return different values at
     * different times, e.g. because we are using the parent's set, which in turn has changed (like when
     * a clause node becomes/unbecomes the last item in a pattern match).
     */
    public abstract ImmutableSet<Character> terminatedByChars();

    public void focusChanged()
    {
        //Log.debug("Removing blanks, focus owner: " + nodes().get(0).getScene().getFocusOwner() + " items: " + nodes().stream().map(Object::toString).collect(Collectors.joining(", ")));
        removeBlanks(children, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, true, atomicEdit);

        // Must also tell remaining children to update (shouldn't interact with above calculation
        // because updating should not make a field return isBlank==true, that should only be returned
        // by unstructured/leaf operands, and only structured/branch operands should respond to this):
        for (ConsecutiveChild consecutiveChild : getAllChildren())
        {
            consecutiveChild.focusChanged();
        }
    }

    /**
     * This method goes through the given list of operands and operators, and modifies them
     * *in place* to remove unnecessary harmless blanks.  It's static to avoid accidentally
     * modifying members.
     *
     * The rules are: any consecutive pair of operator and operand will get removed (it doesn't really
     * matter which ones if there's > 2 consecutive blanks; if there's an odd number you'll always
     * be left with one, and if even, all will be removed anyway).  And finally, a single blank item
     * on the end by itself will get removed.  (A blank pair or more on the end would already have been
     * removed by the first check.)
     *
     * @param operands
     * @param operators
     * @param accountForFocus If they are focused, should they be kept in (true: yes, keep; false: no, remove)
     */
    /*
    static <ITEM> void removeBlanks(List<ITEM> operands, Predicate<ITEM> isBlank, Predicate<ITEM> isFocused, Consumer<ITEM> withRemoved, boolean accountForFocus, @Nullable BooleanProperty atomicEdit)
    {
        // Note on atomicEdit: we set to true if we modify, and set to false once at the end,
        // which will do nothing if we never edited

        List<I> all = ConsecutiveBase.<COMMON, OPERAND, OPERATOR>interleaveOperandsAndOperators(operands, operators);

        // We check for blanks on the second of the pair, as it makes the index checks easier
        // Hence we only need start at 1:
        int index = 1;
        while (index < all.size())
        {
            if (isBlank.test(all.get(index - 1)) && (!accountForFocus || !isFocused.test(all.get(index - 1))) &&
                isBlank.test(all.get(index)) && (!accountForFocus || !isFocused.test(all.get(index))))
            {
                if (atomicEdit != null)
                    atomicEdit.set(true);
                if (all.get(index) instanceof EEDisplayNode)
                    Log.logStackTrace("Removed blank " + all.get(index - 1) + " and " + all.get(index) + " at " + index + " " + accountForFocus);
                // Both are blank, so remove:
                // Important to remove later one first so as to not mess with the indexing:
                all.remove(index);
                if (index - 1 > 0 || all.size() > 1)
                {
                    withRemoved.accept(all.remove(index - 1));
                }
                // Else we don't want to change index, as we want to assess the next
                // pair
            }
            else
                index += 1; // Only if they weren't removed do we advance
        }

        // Remove final operand or operator if blank&unfocused:
        if (!all.isEmpty())
        {
            COMMON last = all.get(all.size() - 1);
            if (isBlank.test(last) && (!accountForFocus || !isFocused.test(last)) && all.size() > 1)
            {
                if (atomicEdit != null)
                    atomicEdit.set(true);
                withRemoved.accept(all.remove(all.size() - 1));
            }
        }

        if (atomicEdit != null)
            atomicEdit.set(false);
    }
    */

    // We deliberately don't directly override OperandNode's isFocused,
    // because then it would be too easily to forget to override it a subclass
    // children, which may have other fields which could be focused
    protected boolean childIsFocused()
    {
        return getAllChildren().stream().anyMatch(c -> c.isFocused());
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
    {
        //Log.logStackTrace("\n\n\n" + this + " showing " + error.toPlain() + " " + quickFixes.size() + " " + quickFixes.stream().map(q -> q.getTitle().toPlain()).collect(Collectors.joining("//")) + "\n\n\n");
        if (operators.isEmpty())
        {
            operands.get(0).addErrorAndFixes(error, quickFixes);
        }
        else
        {
            for (OperatorEntry<EXPRESSION, SEMANTIC_PARENT> operator : operators)
            {
                operator.addErrorAndFixes(error, quickFixes);
            }
        }
    }

    @Override
    public void clearAllErrors()
    {
        children.forEach(op -> op.clearAllErrors());
    }

    @Override
    public boolean isShowingError()
    {
        return children.get(0).isShowingError();
    }

    @Override
    public void showType(String type)
    {
        for (ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> operator : operators)
        {
            operator.showType(type);
        }
    }

    @Override
    public void cleanup()
    {
        children.forEach(EEDisplayNode::cleanup);
    }


    public static final OperandOps<Expression, ExpressionNodeParent> EXPRESSION_OPS = new ExpressionOps();
    public static final OperandOps<UnitExpression, UnitNodeParent> UNIT_OPS = new UnitExpressionOps();
    public static final OperandOps<TypeExpression, TypeParent> TYPE_OPS = new TypeExpressionOps();
}
