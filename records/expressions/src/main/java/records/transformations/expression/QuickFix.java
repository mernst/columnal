package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.Scene;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.Utility;
import utility.TranslationUtility;

/**
 * A quick fix for an error.  Has a title to display, and a thunk to run
 * to get a replacement expression for the error source.
 * 
 * This works on a tree basis, not text-based.
 */
public final class QuickFix<EXPRESSION extends StyledShowable>
{
    private final StyledString title;
    // Identified by reference, not by contents/hashCode.
    private final @Recorded EXPRESSION replacementTarget;
    private final Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> actionOrMakeReplacement;
    private final FXPlatformSupplier<ImmutableList<String>> cssClasses;

    public QuickFix(@LocalizableKey String titleKey, @Recorded EXPRESSION replacementTarget, QuickFixReplace<@NonNull @UnknownIfRecorded EXPRESSION> makeReplacement)
    {
        this(StyledString.s(TranslationUtility.getString(titleKey)),
            ImmutableList.of(),
            replacementTarget, makeReplacement);
    }

    public QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, QuickFixReplace<@UnknownIfRecorded EXPRESSION> makeReplacement)
    {
        this(title, cssClasses, replacementTarget, Either.right(makeReplacement));
    }

    public QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, QuickFixAction action)
    {
        this(title, Utility.prependToList("quick-fix-action", cssClasses), replacementTarget, Either.left(action));
    }

    private QuickFix(StyledString title, ImmutableList<String> cssClasses, @Recorded EXPRESSION replacementTarget, Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> actionOrMakeReplacement)
    {
        this.title = title;
        this.cssClasses = new FXPlatformSupplier<ImmutableList<String>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ImmutableList<String> get()
            {
                try
                {
                    return Utility.<String>concatI(cssClasses, actionOrMakeReplacement.<ImmutableList<String>>eitherInt(a -> ImmutableList.<String>of(), r -> ImmutableList.<String>of(ExpressionUtil.makeCssClass(r.makeReplacement()))));
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return cssClasses;
                }
            }
        };
        this.replacementTarget = replacementTarget;
        this.actionOrMakeReplacement = actionOrMakeReplacement;
    }

    @OnThread(Tag.Any)
    public StyledString getTitle()
    {
        return title;
    }
    
    @OnThread(Tag.FXPlatform)
    // Gets the replacement, or action to perform
    public Either<QuickFixAction, QuickFixReplace<@UnknownIfRecorded EXPRESSION>> getActionOrReplacement()
    {
        return actionOrMakeReplacement;
    }

    public @Recorded EXPRESSION getReplacementTarget()
    {
        return replacementTarget;
    }

    @OnThread(Tag.FXPlatform)
    public ImmutableList<String> getCssClasses()
    {
        return cssClasses.get();
    }
    
    public static interface QuickFixAction
    {
        // Will only be called once.
        @OnThread(Tag.FXPlatform)
        public void doAction(FixHelper fixHelper, Scene editorScene);
    }
    
    public static interface QuickFixReplace<EXPRESSION>
    {
        // Note -- may be called multiple times
        public EXPRESSION makeReplacement() throws InternalException;
    }
}
