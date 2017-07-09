package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import records.gui.stf.StructuredTextField.Suggestion;
import utility.Either;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class BoolComponent extends TerminalComponent<Boolean>
{
    public BoolComponent(ImmutableList<Component<?>> parents, @Nullable Boolean initial)
    {
        super(parents);
        items.add(new Item(getItemParents(), initial == null ? "" : Boolean.toString(initial), ItemVariant.EDITABLE_BOOLEAN, ""));
    }

    @Override
    public Either<List<ErrorFix>, Boolean> endEdit(StructuredTextField<?> field)
    {
        String val = getItem(ItemVariant.EDITABLE_BOOLEAN).trim().toLowerCase();
        if (val.equals("true"))
        {
            items.set(0, items.get(0).replaceContent("true"));
            return Either.right(true);
        }
        else if (val.equals("false"))
        {
            items.set(0, items.get(0).replaceContent("false"));
            return Either.right(false);
        }
        else
            return Either.left(Collections.emptyList());
    }

    @Override
    public List<Suggestion> getSuggestions()
    {
        return Arrays.asList(new Suggestion(items.get(0), "true"), new Suggestion(items.get(0), "false"));
    }
}
