package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by neil on 17/12/2016.
 */
@OnThread(Tag.FXPlatform)
public class AutoComplete extends PopupControl
{
    private final TextField textField;
    private final ExFunction<String, List<Completion>> calculateCompletions;
    private final ListView<Completion> completions;

    /**
     *
     * @param textField
     * @param calculateCompletions
     * @param onSelect The completion to enact, and any characters left-over which
     *                 should be carried into the next slot.
     * @param inNextAlphabet The alphabet of a slot is the set of characters *usually*
     *                       featured.  E.g. for operators it's any characters that
     *                       feature in an operator. For general entry it's the inverse
     *                       of operators.  This predicate checks if the character is in
     *                       the alphabet of the following slot.  If it is, and there's
     *                       no available completions with this character then we pick
     *                       the top one and move to next slot.
     */
    @SuppressWarnings("initialization")
    public AutoComplete(TextField textField, ExFunction<String, List<Completion>> calculateCompletions, FXPlatformBiConsumer<Completion, String> onSelect, Predicate<Character> inNextAlphabet)
    {
        this.textField = textField;
        this.completions = new ListView<>();
        this.calculateCompletions = calculateCompletions;

        completions.setCellFactory(lv -> {
            return new CompleteCell();
        });
        completions.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY)
            {
                onSelect.consume(completions.getSelectionModel().getSelectedItem(), "");
            }
        });

        setSkin(new Skin<AutoComplete>()
        {
            @Override
            @OnThread(Tag.FX)
            public AutoComplete getSkinnable()
            {
                return AutoComplete.this;
            }

            @Override
            @OnThread(Tag.FX)
            public Node getNode()
            {
                return completions;
            }

            @Override
            @OnThread(Tag.FX)
            public void dispose()
            {
            }
        });

        Utility.addChangeListenerPlatformNN(textField.focusedProperty(), focused -> {
            if (focused)
            {
                Pair<Double, Double> pos = calculatePosition();
                updateCompletions(calculateCompletions, textField.getText());
                show(textField, pos.getFirst(), pos.getSecond());
            }
            else
                hide();
        });

        // TODO listen to scene's position in window, and window's position
        Utility.addChangeListenerPlatformNN(textField.localToSceneTransformProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.layoutXProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.layoutYProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.heightProperty(), t -> updatePosition());

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text -> {
            text = text.trim();
            updatePosition(); // Just in case
            List<Completion> available = updateCompletions(calculateCompletions, text);
            // If they type an operator or non-operator char, and there is
            // no completion containing such a char, finish with current and move
            // to next (e.g. user types "true&"; as long as there's no current completion
            // involving "&", take it as an operator and move to next slot (which
            // may also complete if that's the only operator featuring that char)
            // while selecting the best (top) selection for current, or leave as error if none
            if (text.length() >= 2 && inNextAlphabet.test(text.charAt(text.length() - 1)))
            {
                char last = text.charAt(text.length() - 1);
                String withoutLast = text.substring(0, text.length() - 1);
                List<Completion> completionsWithoutLast = null;
                try
                {
                    completionsWithoutLast = calculateCompletions.apply(withoutLast);

                    if (withoutLast != null && !available.stream().anyMatch(c -> c.getDisplay(withoutLast).getSecond().contains("" + last)))
                    {
                        // No completions feature the character and it is in the following alphabet, so
                        // complete the top one and move character to next slot
                        onSelect.consume(completionsWithoutLast.get(0), "" + last);
                        return;
                    }
                }
                catch (UserException | InternalException e)
                {
                    Utility.log(e);
                }
            }
            for (Completion completion : available)
            {
                if (completion.completesOnExactly(text))
                {
                    onSelect.consume(completion, "");
                    hide();
                    break;
                }
                else if (completion.getDisplay(text).getSecond().equals(text))
                {
                    // Select it, at least:
                    completions.getSelectionModel().select(completion);
                }
            }

            if (!text.isEmpty() && !completions.getItems().isEmpty() && completions.getSelectionModel().isEmpty())
            {
                completions.getSelectionModel().select(0);
            }

        });
        setHideOnEscape(true);
        setAutoFix(true);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE)
            {
                hide();
                e.consume();
            }
            if ((e.getCode() == KeyCode.UP || e.getCode() == KeyCode.PAGE_UP) && completions.getSelectionModel().getSelectedIndex() <= 0)
            {
                completions.getSelectionModel().clearSelection();
                e.consume();
            }
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB)
            {
                Completion selectedItem = completions.getSelectionModel().getSelectedItem();
                if (selectedItem != null)
                {
                    e.consume();
                    onSelect.consume(selectedItem, "");
                    hide();
                }
            }
        });
    }

    private List<Completion> updateCompletions(ExFunction<String, List<Completion>> calculateCompletions, String text)
    {
        try
        {
            this.completions.getItems().setAll(calculateCompletions.apply(text));
        }
        catch (InternalException | UserException e)
        {
            Utility.log(e);
            this.completions.getItems().clear();
        }
        return this.completions.getItems();
    }

    @OnThread(Tag.FXPlatform)
    private @Nullable Pair<Double, Double> calculatePosition()
    {
        @Nullable Point2D textToScene = textField.localToScene(0, textField.getHeight());
        if (textToScene == null || textField.getScene() == null || textField.getScene().getWindow() == null)
            return null;
        return new Pair<>(
            textToScene.getX() + textField.getScene().getX() + textField.getScene().getWindow().getX(),
            textToScene.getY() + textField.getScene().getY() + textField.getScene().getWindow().getY()
        );
    }

    private void updatePosition()
    {
        if (isShowing())
        {
            @Nullable Pair<Double, Double> pos = calculatePosition();
            if (pos != null)
            {
                setAnchorX(pos.getFirst());
                setAnchorY(pos.getSecond());
            }
        }
    }

    public abstract static class Completion
    {
        abstract Pair<@Nullable Node, String> getDisplay(String currentText);
        abstract boolean shouldShow(String input);
        public boolean completesOnExactly(String input)
        {
            return false;
        }
    }



    public static class KeyShortcutCompletion extends Completion
    {
        private final Character[] shortcuts;
        private final String title;

        public KeyShortcutCompletion(String title, Character... shortcuts)
        {
            this.shortcuts = shortcuts;
            this.title = title;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay(String currentText)
        {
            return new Pair<>(new Label(" " + shortcuts[0] + " "), title);
        }

        @Override
        boolean shouldShow(String input)
        {
            return input.isEmpty() || Arrays.stream(shortcuts).anyMatch(c -> input.equals(c.toString()));
        }

        @Override
        public boolean completesOnExactly(String input)
        {
            for (Character shortcut : shortcuts)
            {
                if (input.equals(shortcut.toString()))
                    return true;
            }
            return false;
        }
    }

    private class CompleteCell extends ListCell<Completion>
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(Completion item, boolean empty)
        {
            if (empty)
            {
                setGraphic(null);
                setText("");
            }
            else
            {
                Pair<@Nullable Node, String> p = item.getDisplay(textField.getText());
                setGraphic(p.getFirst());
                setText(p.getSecond());
            }
            super.updateItem(item, empty);
        }
    }
}
