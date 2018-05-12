package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.OperandOps;
import records.typeExp.TypeConcretisationError;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunctionInt;
import utility.Pair;
import utility.gui.TranslationUtility;

import java.util.List;
import java.util.function.Consumer;

/**
 * A listener that records where errors have occurred when checking an expression.
 * 
 * Note: it is valid to record an error and/or quick fixes multiple times
 * for the same expression.  In this case, the error and list of quick fixes
 * should be concatenated to form the final outcome.
 */
public interface ErrorAndTypeRecorder
{
    /**
     * Checks the given error-or-type.  If error, that error is recorded 
     * (with no available quick fixes) and null is returned.
     * If type, it is returned directly.
     * @param src
     * @param errorOrType
     * @return
     */
    public default @Nullable TypeExp recordError(Expression src, Either<StyledString, TypeExp> errorOrType)
    {
        return errorOrType.<@Nullable TypeExp>either(err -> {
            recordError(src, err);
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message
     */
    public default <T> @Nullable T recordLeftError(UnitManager unitManager, Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<@Nullable T>either(err -> {
            @Nullable DataType fix = err.getSuggestedTypeFix();
            recordError(src, err.getErrorText());
            recordQuickFixes(src, ExpressionEditorUtil.quickFixesForTypeError(unitManager, src, fix));
            return null;
        }, val -> val);
    }

    /**
     * Records the type for a particular expression.
     */
    public default @Recorded @Nullable TypeExp recordType(Expression expression, @Nullable TypeExp typeExp)
    {
        if (typeExp != null)
            return recordTypeNN(expression, typeExp);
        else
            return null;
    }

    /**
     * A curried version of the two-arg function of the same name.
     */
    @Pure
    public default Consumer<StyledString> recordErrorCurried(Expression src)
    {
        return errMsg -> recordError(src, errMsg);
    }

    /**
     * Records an error source and error message, and a list of possible quick fixes
     */
    // TODO make the String @Localized
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error);
    
    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> fixes);

    public default @Recorded @Nullable TypeExp recordTypeAndError(Expression expression, Either<StyledString, TypeExp> typeOrError)
    {
        return recordType(expression, recordError(expression, typeOrError));
    }

    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp);

    /**
     * A quick fix for an error.  Has a title to display, and a thunk to run
     * to get a replacement expression for the error source.
     */
    public final static class QuickFix<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
    {
        private final StyledString title;
        private final FXPlatformFunctionInt<QuickFixParams, Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>> fixedReplacement;
        private final ImmutableList<String> cssClasses;

        public QuickFix(@LocalizableKey String titleKey, ReplacementTarget replacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT> replacement)
        {
            this(StyledString.concat(
                    StyledString.s(TranslationUtility.getString(titleKey)),
                    StyledString.s(": "),
                    replacement.toStyledString()),
                ImmutableList.of(OperandOps.makeCssClass(replacement)),
                p -> new Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>(replacementTarget, replacement));
        }
        
        public QuickFix(StyledString title, ImmutableList<String> cssClasses, FXPlatformFunctionInt<QuickFixParams, Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>>> fixedReplacement)
        {
            this.title = title;
            this.cssClasses = cssClasses;
            this.fixedReplacement = fixedReplacement;
        }

        public StyledString getTitle()
        {
            return title;
        }
        
        @OnThread(Tag.FXPlatform)
        public Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> getFixedVersion(@Nullable Window parentWindow, TableManager tableManager) throws InternalException
        {
            return fixedReplacement.apply(new QuickFixParams(parentWindow, tableManager));
        }

        public ImmutableList<String> getCssClasses()
        {
            return cssClasses;
        }

        public static enum ReplacementTarget
        {
            CURRENT, PARENT;
        }
        
        public static final class QuickFixParams
        {
            public final @Nullable Window parentWindow;
            public final TableManager tableManager;

            public QuickFixParams(@Nullable Window parentWindow, TableManager tableManager)
            {
                this.parentWindow = parentWindow;
                this.tableManager = tableManager;
            }
        }
    }
}
