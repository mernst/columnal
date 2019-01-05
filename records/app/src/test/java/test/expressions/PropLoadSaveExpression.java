package test.expressions;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.BracketedStatus;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression extends FXApplicationTest
{
    @Property(trials = 200)
    public void testLoadSaveNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        testLoadSave(expression);
    }

    @Property(trials = 200)
    public void testEditNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        TestUtil.fxTest_(() -> {
            testNoOpEdit(expression);
        });
    }
    
    @Test
    public void testInvalids()
    {
        TestUtil.fxTest_(() -> {
            try
            {
                testNoOpEdit("@invalidops(2, @unfinished \"+\")");
                testNoOpEdit("@invalidops(2, @unfinished \"#\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", 2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(-1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("-1");
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public void testNoOpEdit(String src) throws UserException, InternalException
    {
        testNoOpEdit(Expression.parse(null, src, DummyManager.make().getTypeManager()));
    }

    @OnThread(Tag.FXPlatform)
    private void testNoOpEdit(Expression expression)
    {
        Expression edited = new ExpressionEditor(expression, new ReadOnlyObjectWrapper<@Nullable Table>(null), true, new ReadOnlyObjectWrapper<@Nullable DataType>(null), DummyManager.make(), e -> {
        }).save();
        assertEquals(expression, edited);
        assertEquals(expression.save(true, BracketedStatus.MISC, TableAndColumnRenames.EMPTY), edited.save(true, BracketedStatus.MISC, TableAndColumnRenames.EMPTY));
    }

    @Property(trials = 200)
    public void testLoadSaveReal(@From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws InternalException, UserException
    {
        try
        {
            testLoadSave(expressionValue.expression);
        }
        catch (OutOfMemoryError e)
        {
            fail("Out of memory issue with expression: " + expressionValue.expression);
        }
    }

    private void testLoadSave(@From(GenNonsenseExpression.class) Expression expression) throws UserException, InternalException
    {
        String saved = expression.save(true, BracketedStatus.MISC, TableAndColumnRenames.EMPTY);
        // Use same manager to load so that types are preserved:
        Expression reloaded = Expression.parse(null, saved, TestUtil.managerWithTestTypes().getFirst().getTypeManager());
        assertEquals("Saved version: " + saved, expression, reloaded);
        String resaved = reloaded.save(true, BracketedStatus.MISC, TableAndColumnRenames.EMPTY);
        assertEquals(saved, resaved);

    }
}
