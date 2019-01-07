package records.gui.flex;

import com.google.common.collect.ImmutableList;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.richtext.model.TextOps;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.Collection;

@OnThread(Tag.FXPlatform)
public class FlexibleTextField extends StyleClassedTextArea
{
    private @MonotonicNonNull EditorKit<?> editorKit;
    
    public FlexibleTextField()
    {
        super(false);
        getStyleClass().add("flexible-text-field");
        setPrefHeight(FXUtility.measureNotoSansHeight());

        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            FlexibleTextField usFocused = FXUtility.focused(this);
            if (usFocused.editorKit == null)
                return;
            //usFocused.updateAutoComplete(getSelection());
            if (focused)
            {
                usFocused.focusGained();
            }
            else
            {
                usFocused.focusLost();
            }
        });

        Nodes.addInputMap(FXUtility.keyboard(this), InputMap.sequence(
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.TAB), (KeyEvent e) -> {
                FXUtility.keyboard(this).tabPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), (KeyEvent e) -> {
                FXUtility.keyboard(this).enterPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ESCAPE), (KeyEvent e) -> {
                FXUtility.keyboard(this).escapePressed();
                e.consume();
            })
        ));

        Nodes.addFallbackInputMap(FXUtility.mouse(this), InputMap.consume(MouseEvent.ANY));
    }

    public <T> void resetContent(EditorKit<T> editorKit)
    {
        if (this.editorKit != null)
        {
            this.editorKit.setField(null);
        }

        this.editorKit = editorKit;
        this.editorKit.setField(this);
        replace(editorKit.getLatestDocument(isFocused()));

        setEditable(this.editorKit.isEditable());
    }

    public @Nullable EditorKit<?> getEditorKit()
    {
        return editorKit;
    }

    void tabPressed()
    {
        /*
        if (autoComplete != null && autoComplete.isShowing())
        {
            autoComplete.fireSelected();
        }
        else*/ if (editorKit != null)
        {
            editorKit.relinquishFocus();
        }
    }

    void escapePressed()
    {
        /*if (autoComplete != null)
        {
            autoComplete.hide();
            autoComplete = null;
        }
        else*/ if (editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    void enterPressed()
    {
        /*if (autoComplete != null)
        {
            autoComplete.fireSelected();
        }
        */
        boolean atEnd = getCaretPosition() == getLength();
        if (atEnd && editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    @RequiresNonNull("editorKit")
    private void focusGained()
    {
        editorKit.focusChanged(getText(), true);
    }

    @RequiresNonNull("editorKit")
    private void focusLost()
    {
        // Deselect when focus is lost:
        deselect();
        editorKit.focusChanged(getText(), false);
    }
    
    public static ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc(ImmutableList<StyledText<Collection<String>>> segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> overall = ReadOnlyStyledDocument.fromString("", ImmutableList.<String>of(), ImmutableList.<String>of(), StyledText.<Collection<String>>textOps());
        for (StyledText<Collection<String>> segment : segments)
        {
            ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> latest = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromString(segment.getText(), ImmutableList.<String>of(), segment.getStyle(), StyledText.<Collection<String>>textOps());
            overall = overall.concat(latest);
        }
        return overall;
        
    }
}
